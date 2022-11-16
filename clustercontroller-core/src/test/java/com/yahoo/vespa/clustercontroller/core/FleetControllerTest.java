// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.log.LogSetup;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.database.ZooKeeperDatabaseFactory;
import com.yahoo.vespa.clustercontroller.core.rpc.RPCCommunicator;
import com.yahoo.vespa.clustercontroller.core.rpc.RpcServer;
import com.yahoo.vespa.clustercontroller.core.rpc.SlobrokClient;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitCondition;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitTask;
import com.yahoo.vespa.clustercontroller.core.testutils.Waiter;
import com.yahoo.vespa.clustercontroller.utils.util.NoMetricReporter;
import org.junit.jupiter.api.AfterEach;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.vdslib.state.NodeType.DISTRIBUTOR;
import static com.yahoo.vdslib.state.NodeType.STORAGE;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Håkon Humberset
 */
public abstract class FleetControllerTest implements Waiter {

    private static final Logger log = Logger.getLogger(FleetControllerTest.class.getName());
    private static final int DEFAULT_NODE_COUNT = 10;

    private final Duration timeout = Duration.ofSeconds(30);
    protected final FakeTimer timer = new FakeTimer();

    protected Slobrok slobrok;
    protected FleetControllerOptions options;
    ZooKeeperTestServer zooKeeperServer;
    protected List<FleetController> fleetControllers = new ArrayList<>();
    protected List<DummyVdsNode> nodes = new ArrayList<>();
    private String testName;

    private final Waiter waiter = new Waiter.Impl(new DataRetriever() {
        @Override
        public Object getMonitor() { return timer; }
        @Override
        public FleetController getFleetController() { return fleetController(); }
        @Override
        public List<DummyVdsNode> getDummyNodes() { return nodes; }
        @Override
        public Duration getTimeout() { return timeout; }
    });

    static {
        LogSetup.initVespaLogging("fleetcontroller");
    }

    protected void startingTest(String name) {
        System.err.println("STARTING TEST: " + name);
        testName = name;
    }

    static protected FleetControllerOptions.Builder defaultOptions(String clusterName) {
        return defaultOptions(clusterName, DEFAULT_NODE_COUNT);
    }

    static protected FleetControllerOptions.Builder defaultOptions(String clusterName, int nodeCount) {
        return defaultOptions(clusterName, IntStream.range(0, nodeCount)
                                                    .mapToObj(i -> new ConfiguredNode(i, false))
                                                    .collect(Collectors.toSet()));
    }

    static protected FleetControllerOptions.Builder defaultOptions(String clusterName, Collection<ConfiguredNode> nodes) {
        var builder = new FleetControllerOptions.Builder(clusterName, nodes);
        builder.enableTwoPhaseClusterStateActivation(true); // Enable by default, tests can explicitly disable.
        return builder;
    }

    void setUpSystem(FleetControllerOptions.Builder builder) throws Exception {
        log.log(Level.FINE, "Setting up system");
        slobrok = new Slobrok();
        if (builder.zooKeeperServerAddress() != null) {
            zooKeeperServer = new ZooKeeperTestServer();
            // Need to set zookeeper address again, as port number is not known until ZooKeeperTestServer has been created
            builder.setZooKeeperServerAddress(zooKeeperServer.getAddress());
            log.log(Level.FINE, "Set up new zookeeper server at " + zooKeeperServer.getAddress());
        }
        builder.setSlobrokConnectionSpecs(getSlobrokConnectionSpecs(slobrok));
        this.options = builder.build();
    }

    FleetController createFleetController(boolean useFakeTimer, FleetControllerOptions options) throws Exception {
        var context = new TestFleetControllerContext(options);
        Timer timer = useFakeTimer ? this.timer : new RealTimer();
        var metricUpdater = new MetricUpdater(new NoMetricReporter(), options.fleetControllerIndex(), options.clusterName());
        var log = new EventLog(timer, metricUpdater);
        var cluster = new ContentCluster(options.clusterName(), options.nodes(), options.storageDistribution());
        var stateGatherer = new NodeStateGatherer(timer, timer, log);
        var communicator = new RPCCommunicator(
                RPCCommunicator.createRealSupervisor(),
                timer,
                options.fleetControllerIndex(),
                options.nodeStateRequestTimeoutMS(),
                options.nodeStateRequestTimeoutEarliestPercentage(),
                options.nodeStateRequestTimeoutLatestPercentage(),
                options.nodeStateRequestRoundTripTimeMaxSeconds());
        var lookUp = new SlobrokClient(context, timer, new String[0]);
        var rpcServer = new RpcServer(timer, timer, options.clusterName(), options.fleetControllerIndex(), options.slobrokBackOffPolicy());
        var database = new DatabaseHandler(context, new ZooKeeperDatabaseFactory(context), timer, options.zooKeeperServerAddress(), timer);

        // Setting this <1000 ms causes ECONNREFUSED on socket trying to connect to ZK server, in ZooKeeper,
        // after creating a new ZooKeeper (session).  This causes ~10s extra time to connect after connection loss.
        // Reasons unknown.  Larger values like the default 10_000 causes that much additional running time for some tests.
        database.setMinimumWaitBetweenFailedConnectionAttempts(2_000);

        var stateGenerator = new StateChangeHandler(context, timer, log);
        var stateBroadcaster = new SystemStateBroadcaster(context, timer, timer);
        var masterElectionHandler = new MasterElectionHandler(context, options.fleetControllerIndex(), options.fleetControllerCount(), timer, timer);

        var status = new StatusHandler.ContainerStatusPageServer();
        var controller = new FleetController(context, timer, log, cluster, stateGatherer, communicator, status, rpcServer, lookUp,
                                             database, stateGenerator, stateBroadcaster, masterElectionHandler, metricUpdater, options);
        controller.start();
        return controller;
    }

    protected FleetControllerOptions setUpFleetController(boolean useFakeTimer, FleetControllerOptions.Builder builder) throws Exception {
        if (slobrok == null) setUpSystem(builder);
        options = builder.build();
        startFleetController(useFakeTimer);
        return options;
    }

    void stopFleetController() {
        fleetControllers.forEach(f -> {
            try {
                f.shutdown();
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        fleetControllers.clear();
    }

    void startFleetController(boolean useFakeTimer) throws Exception {
        if ( ! fleetControllers.isEmpty()) throw new IllegalStateException("already started fleetcontroller, not starting another");

        fleetControllers.add(createFleetController(useFakeTimer, options));
    }

    protected void setUpVdsNodes(boolean useFakeTimer) throws Exception {
        setUpVdsNodes(useFakeTimer, false);
    }

    protected void setUpVdsNodes(boolean useFakeTimer, boolean startDisconnected) throws Exception {
        setUpVdsNodes(useFakeTimer, startDisconnected, DEFAULT_NODE_COUNT);
    }

    protected void setUpVdsNodes(boolean useFakeTimer, boolean startDisconnected, int nodeCount) throws Exception {
        TreeSet<Integer> nodeIndexes = new TreeSet<>();
        for (int i = 0; i < nodeCount; ++i)
            nodeIndexes.add(this.nodes.size()/2 + i); // divide by 2 because there are 2 nodes (storage and distributor) per index
        setUpVdsNodes(useFakeTimer, startDisconnected, nodeIndexes);
    }

    protected void setUpVdsNodes(boolean useFakeTimer, boolean startDisconnected, Set<Integer> nodeIndexes) throws Exception {
        for (int nodeIndex : nodeIndexes) {
            nodes.add(createNode(useFakeTimer, startDisconnected, DISTRIBUTOR, nodeIndex));
            nodes.add(createNode(useFakeTimer, startDisconnected, STORAGE, nodeIndex));
        }
    }

    private DummyVdsNode createNode(boolean useFakeTimer, boolean startDisconnected,
                                    NodeType nodeType, int nodeIndex) throws Exception {
        String[] connectionSpecs = getSlobrokConnectionSpecs(slobrok);
        DummyVdsNode node = new DummyVdsNode(useFakeTimer ? timer : new RealTimer(), connectionSpecs,
                                             options.clusterName(), nodeType, nodeIndex);
        if ( ! startDisconnected)
            node.connect();
        return node;
    }

    // TODO: Replace all usages of the above setUp methods with this one, and remove the nodes field

    /**
     * Creates dummy vds nodes for the list of configured nodes and returns them.
     * As two dummy nodes are created for each configured node - one distributor and one storage node -
     * the returned list is twice as large as configuredNodes.
     */
    protected List<DummyVdsNode> setUpVdsNodes(boolean useFakeTimer, boolean startDisconnected, List<ConfiguredNode> configuredNodes) throws Exception {
        nodes = new ArrayList<>();
        for (ConfiguredNode configuredNode : configuredNodes) {
            nodes.add(createNode(useFakeTimer, startDisconnected, DISTRIBUTOR, configuredNode.index()));
            nodes.add(createNode(useFakeTimer, startDisconnected, STORAGE, configuredNode.index()));
        }
        return nodes;
    }

    static Set<Integer> asIntSet(Integer... idx) {
        return new HashSet<>(Arrays.asList(idx));
    }

    static Set<ConfiguredNode> asConfiguredNodes(Set<Integer> indices) {
        return indices.stream().map(idx -> new ConfiguredNode(idx, false)).collect(Collectors.toSet());
    }

    void waitForStateExcludingNodeSubset(String expectedState, Set<Integer> excludedNodes) throws Exception {
        // Due to the implementation details of the test base, this.waitForState() will always
        // wait until all nodes added in the test have received the latest cluster state. Since we
        // want to entirely ignore node #6, it won't get a cluster state at all and the test will
        // fail unless otherwise handled. We thus use a custom waiter which filters out nodes with
        // the sneaky index (storage and distributors with same index are treated as different nodes
        // in this context).
        Waiter subsetWaiter = new Waiter.Impl(new DataRetriever() {
            @Override
            public Object getMonitor() { return timer; }
            @Override
            public FleetController getFleetController() { return fleetController(); }
            @Override
            public List<DummyVdsNode> getDummyNodes() {
                return nodes.stream()
                        .filter(n -> !excludedNodes.contains(n.getNode().getIndex()))
                        .collect(Collectors.toList());
            }
            @Override
            public Duration getTimeout() { return timeout; }
        });
        subsetWaiter.waitForState(expectedState);
    }

    @AfterEach
    public void tearDown() {
        if (testName != null) {
            //log.log(Level.INFO, "STOPPING TEST " + testName);
            System.err.println("STOPPING TEST " + testName);
            testName = null;
        }
        fleetControllers.forEach(f -> {
            try {
                f.shutdown();
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        fleetControllers.clear();
        if (nodes != null) for (DummyVdsNode node : nodes) {
            node.shutdown();
            nodes = null;
        }
        if (slobrok != null) {
            slobrok.stop();
            slobrok = null;
        }
    }

    public ClusterState waitForStableSystem() throws Exception { return waiter.waitForStableSystem(); }
    public ClusterState waitForStableSystem(int nodeCount) throws Exception { return waiter.waitForStableSystem(nodeCount); }
    public ClusterState waitForState(String state) throws Exception { return waiter.waitForState(state); }
    public ClusterState waitForStateInAllSpaces(String state) throws Exception { return waiter.waitForStateInAllSpaces(state); }
    public ClusterState waitForStateInSpace(String space, String state) throws Exception { return waiter.waitForStateInSpace(space, state); }
    public ClusterState waitForState(String state, Duration timeout) throws Exception { return waiter.waitForState(state, timeout); }
    public ClusterState waitForInitProgressPassed(Node n, double progress) { return waiter.waitForInitProgressPassed(n, progress); }
    public ClusterState waitForClusterStateIncludingNodesWithMinUsedBits(int bitcount, int nodecount) { return waiter.waitForClusterStateIncludingNodesWithMinUsedBits(bitcount, nodecount); }

    public void wait(WaitCondition condition, WaitTask task, Duration timeout) {
        waiter.wait(condition, task, timeout);
    }

    void waitForCompleteCycle() {
        fleetController().waitForCompleteCycle(timeout);
    }

    public static Set<ConfiguredNode> toNodes(Integer ... indexes) {
        return Arrays.stream(indexes)
                .map(i -> new ConfiguredNode(i, false))
                .collect(Collectors.toSet());
    }

    void setWantedState(DummyVdsNode node, State state, String reason, Supervisor supervisor) {
        NodeState ns = new NodeState(node.getType(), state);
        if (reason != null) ns.setDescription(reason);
        Target connection = supervisor.connect(new Spec("localhost", fleetController().getRpcPort()));
        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue(node.getSlobrokName()));
        req.parameters().add(new StringValue(ns.serialize()));
        connection.invokeSync(req, timeout());
        if (req.isError()) {
            fail("Failed to invoke setNodeState(): " + req.errorCode() + ": " + req.errorMessage());
        }
        if (!req.checkReturnTypes("s")) {
            fail("Failed to invoke setNodeState(): Invalid return types.");
        }
    }

    protected FleetController fleetController() {return fleetControllers.get(0);}

    static String[] getSlobrokConnectionSpecs(Slobrok slobrok) {
        String[] connectionSpecs = new String[1];
        connectionSpecs[0] = "tcp/localhost:" + slobrok.port();
        return connectionSpecs;
    }

    Duration timeout() { return timeout; }

}
