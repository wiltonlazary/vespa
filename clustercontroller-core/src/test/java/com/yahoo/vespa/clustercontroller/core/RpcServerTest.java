// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.rpc.RpcServer;
import com.yahoo.vespa.clustercontroller.core.testutils.LogFormatter;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitCondition;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author humbe
 */
@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
public class RpcServerTest extends FleetControllerTest {

    public static Logger log = Logger.getLogger(RpcServerTest.class.getName());

    private Supervisor supervisor;

    @BeforeEach
    public void setup() {
        supervisor = new Supervisor(new Transport());
    }

    @AfterEach
    public void teardown() {
        supervisor.transport().shutdown().join();
    }

    @Test
    void testRebinding() throws Exception {
        startingTest("RpcServerTest::testRebinding");
        Slobrok slobrok = new Slobrok();
        String[] slobrokConnectionSpecs = getSlobrokConnectionSpecs(slobrok);
        RpcServer server = new RpcServer(timer, new Object(), "mycluster", 0, new BackOff());
        server.setSlobrokConnectionSpecs(slobrokConnectionSpecs, 0);
        int portUsed = server.getPort();
        server.setSlobrokConnectionSpecs(slobrokConnectionSpecs, portUsed);
        server.disconnect();
        server.disconnect();
        server.connect();
        server.connect();
        server.disconnect();
        server.connect();
        server.shutdown();
        slobrok.stop();
    }

    @Test
    void testGetSystemState() throws Exception {
        LogFormatter.initializeLogging();
        startingTest("RpcServerTest::testGetSystemState");
        FleetControllerOptions.Builder options = defaultOptions("mycluster");
        setUpFleetController(true, options);
        setUpVdsNodes(true);
        waitForStableSystem();

        assertTrue(nodes.get(0).isDistributor());
        log.log(Level.INFO, "Disconnecting distributor 0. Waiting for state to reflect change.");
        nodes.get(0).disconnect();
        nodes.get(19).disconnect();
        fleetController().waitForNodesInSlobrok(9, 9, timeout());
        timer.advanceTime(options.nodeStateRequestTimeoutMS() + options.maxSlobrokDisconnectGracePeriod());

        wait(new WaitCondition.StateWait(fleetController(), fleetController().getMonitor()) {
                 @Override
                 public String isConditionMet() {
                     if (currentState == null) {
                         return "No cluster state defined yet";
                     }
                     NodeState distState = currentState.getNodeState(new Node(NodeType.DISTRIBUTOR, 0));
                     if (distState.getState() != State.DOWN) {
                         return "Distributor not detected down yet: " + currentState.toString();
                     }
                     NodeState storState = currentState.getNodeState(new Node(NodeType.STORAGE, 9));
                     if (!storState.getState().oneOf("md")) {
                         return "Storage node not detected down yet: " + currentState.toString();
                     }
                     return null;
                 }
             }, new WaitTask() {
                 @Override
                 public boolean performWaitTask() {
                     return false;
                 }
             },
             timeout());

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("getSystemState");
        connection.invokeSync(req, timeout());
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ss"), req.toString());
        String systemState = req.returnValues().get(1).asString();
        ClusterState retrievedClusterState = new ClusterState(systemState);
        assertEquals(State.DOWN, retrievedClusterState.getNodeState(new Node(NodeType.DISTRIBUTOR, 0)).getState(), systemState);
        assertTrue(retrievedClusterState.getNodeState(new Node(NodeType.STORAGE, 9)).getState().oneOf("md"), systemState);
    }

    private void setWantedNodeState(State newState, NodeType nodeType, int nodeIndex) {
        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Node node = new Node(nodeType, nodeIndex);
        NodeState newNodeState = new NodeState(nodeType, newState);

        Request req = setNodeState("storage/cluster.mycluster/" + node.getType().toString() + "/" + node.getIndex(), newNodeState, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("s"), req.toString());
    }

    @Test
    void testGetNodeState() throws Exception {
        startingTest("RpcServerTest::testGetNodeState");
        Set<ConfiguredNode> configuredNodes = new TreeSet<>();
        for (int i = 0; i < 10; i++)
            configuredNodes.add(new ConfiguredNode(i, false));
        FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes);
        builder.setMinRatioOfStorageNodesUp(0);
        builder.setMaxInitProgressTime(30000);
        builder.setStableStateTimePeriod(60000);
        setUpFleetController(true, builder);
        setUpVdsNodes(true);
        waitForStableSystem();

        setWantedNodeState(State.DOWN, NodeType.DISTRIBUTOR, 2);
        setWantedNodeState(State.RETIRED, NodeType.STORAGE, 2);
        setWantedNodeState(State.MAINTENANCE, NodeType.STORAGE, 7);
        waitForCompleteCycle();
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(0).disconnect();
        nodes.get(3).disconnect();
        nodes.get(5).disconnect();
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:m .2.s:m .7.s:m");
        timer.advanceTime(1000000);
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:d .2.s:d .7.s:m");
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(3).setNodeState(new NodeState(nodes.get(3).getType(), State.INITIALIZING).setInitProgress(0.2f));
        nodes.get(3).connect();
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:i .1.i:0.2 .2.s:d .7.s:m");

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = getNodeState("distributor", 0, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(0).asString()).getState());
        NodeState reported = NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(1).asString());
        assertTrue(reported.getState().oneOf("d-"), req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("distributor",2, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(0).asString()).getState());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(2).asString()).getState());

        req = getNodeState("distributor", 4, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals("", req.returnValues().get(0).asString());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("distributor", 15, connection);
        assertEquals(ErrorCode.METHOD_FAILED, req.errorCode(), req.toString());
        assertEquals("No node distributor.15 exists in cluster mycluster", req.errorMessage());
        assertFalse(req.checkReturnTypes("ssss"), req.toString());

        req = getNodeState("storage", 1, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals("s:i i:0.2", req.returnValues().get(0).asString());
        assertEquals("s:i i:0.2", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("storage", 2, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(0).asString()).getState());
        reported = NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(1).asString());
        assertTrue(reported.getState().oneOf("d-"), req.returnValues().get(1).asString());
        assertEquals(State.RETIRED, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(2).asString()).getState());

        req = getNodeState("storage", 5, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals("", req.returnValues().get(0).asString());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("storage", 7, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.MAINTENANCE, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(0).asString()).getState());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals(State.MAINTENANCE, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(2).asString()).getState());
    }

    @Test
    void testGetNodeStateWithConfiguredRetired() throws Exception {
        startingTest("RpcServerTest::testGetNodeStateWithConfiguredRetired");
        List<ConfiguredNode> configuredNodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            configuredNodes.add(new ConfiguredNode(i, false));
        configuredNodes.add(new ConfiguredNode(4, true)); // Last node is configured retired
        FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                .setMinRatioOfStorageNodesUp(0)
                .setMaxInitProgressTime(30000)
                .setStableStateTimePeriod(60000);
        setUpFleetController(true, builder);
        setUpVdsNodes(true, false, configuredNodes);
        waitForState("version:\\d+ distributor:5 storage:5 .4.s:r");

        setWantedNodeState(State.DOWN, NodeType.DISTRIBUTOR, 2);
        setWantedNodeState(State.RETIRED, NodeType.STORAGE, 2);
        setWantedNodeState(State.MAINTENANCE, NodeType.STORAGE, 3);
        waitForCompleteCycle();
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(0).disconnect();
        nodes.get(3).disconnect();
        nodes.get(5).disconnect();
        waitForState("version:\\d+ distributor:5 .0.s:d .2.s:d storage:5 .1.s:m .2.s:m .3.s:m .4.s:r");
        timer.advanceTime(1000000);
        waitForState("version:\\d+ distributor:5 .0.s:d .2.s:d storage:5 .1.s:d .2.s:d .3.s:m .4.s:r");
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(3).setNodeState(new NodeState(nodes.get(3).getType(), State.INITIALIZING).setInitProgress(0.2f));
        nodes.get(3).connect();
        waitForState("version:\\d+ distributor:5 .0.s:d .2.s:d storage:5 .1.s:i .1.i:0.2 .2.s:d .3.s:m .4.s:r");
    }

    @Test
    void testGetNodeStateWithConfigurationChangeToRetiredWhileNodeDown() throws Exception {
        startingTest("RpcServerTest::testGetNodeStateWithConfigurationChangeToRetiredWhileNodeDown");

        { // Configuration: 5 nodes, all normal
            List<ConfiguredNode> configuredNodes = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            setUpFleetController(true, builder);
            setUpVdsNodes(true, false, configuredNodes);
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // 2 first storage nodes go down (0 and 2 are the corresponding distributors)
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).disconnectImmediately();
            nodes.get(3).disconnectImmediately();
            waitForState("version:\\d+ distributor:5 storage:5 .0.s:m .1.s:m");
        }

        { // Configuration change: Add 2 new nodes and retire the 5 existing ones
            setUpVdsNodes(true, false, 2);
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                    .setSlobrokConnectionSpecs(this.options.slobrokConnectionSpecs())
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:m .1.s:m .2.s:r .3.s:r .4.s:r");
        }

        { // 2 storage nodes down come up, should go to state retired
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).connect();
            nodes.get(3).connect();
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // 2 first storage nodes go down again
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).disconnectImmediately();
            nodes.get(3).disconnectImmediately();
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:m .1.s:m .2.s:r .3.s:r .4.s:r");
        }

        { // Configuration change: Unretire the nodes
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 7; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                    .setSlobrokConnectionSpecs(this.options.slobrokConnectionSpecs())
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:m .1.s:m");
        }

        { // 2 storage nodes down come up, should go to state up
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).connect();
            nodes.get(3).connect();
            waitForState("version:\\d+ distributor:7 storage:7");
        }

    }

    @Test
    void testGetNodeStateWithConfigurationChangeToRetired() throws Exception {
        startingTest("RpcServerTest::testGetNodeStateWithConfigurationChangeToRetired");

        { // Configuration: 5 nodes, all normal
            List<ConfiguredNode> configuredNodes = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            options = builder.build();
            setUpFleetController(true, builder);
            setUpVdsNodes(true, false, configuredNodes);
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // Reconfigure with the same state
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                    .setSlobrokConnectionSpecs(options.slobrokConnectionSpecs())
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // Configuration change: Add 2 new nodes and retire the 5 existing ones
            setUpVdsNodes(true, false, 2);
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                    .setSlobrokConnectionSpecs(options.slobrokConnectionSpecs())
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // Reconfigure with the same state
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            FleetControllerOptions.Builder builder = defaultOptions("mycluster", configuredNodes)
                    .setSlobrokConnectionSpecs(options.slobrokConnectionSpecs())
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // Configuration change: Remove the previously retired nodes
            /*
        TODO: Verify current result: version:23 distributor:7 .0.s:d .1.s:d .2.s:d .3.s:d .4.s:d storage:7 .0.s:m .1.s:m .2.s:m .3.s:m .4.s:m
        TODO: Make this work without stopping/disconnecting (see StateChangeHandler.setNodes
        Set<ConfiguredNode> configuredNodes = new TreeSet<>();
        configuredNodes.add(new ConfiguredNode(5, false));
        configuredNodes.add(new ConfiguredNode(6, false));
        FleetControllerOptions.Builder builder = new FleetControllerOptions.Builder("mycluster", configuredNodes)
        .setSlobrokConnectionSpecs(options.slobrokConnectionSpecs())
        .setMaxInitProgressTimeMs(30000)
        .setStableStateTimePeriod(60000);
        fleetController.updateOptions(options, 0);
        for (int i = 0; i < 5*2; i++) {
            nodes.get(i).disconnectSlobrok();
            nodes.get(i).disconnect();
        }
        waitForState("version:\\d+ distributor:7 storage:7 .0.s:d .1.s:d .2.s:d .3.s:d .4.s:d");
        */
        }
    }

    @Test
    void testSetNodeState() throws Exception {
        startingTest("RpcServerTest::testSetNodeState");
        Set<Integer> nodeIndexes = new TreeSet<>(List.of(4, 6, 9, 10, 14, 16, 21, 22, 23, 25));
        Set<ConfiguredNode> configuredNodes = nodeIndexes.stream().map(i -> new ConfiguredNode(i, false)).collect(Collectors.toSet());
        FleetControllerOptions.Builder options = defaultOptions("mycluster", configuredNodes);
        //options.setStorageDistribution(new Distribution(getDistConfig(nodeIndexes)));
        setUpFleetController(true, options);
        setUpVdsNodes(true, false, nodeIndexes);
        waitForState("version:\\d+ distributor:26 .0.s:d .1.s:d .2.s:d .3.s:d .5.s:d .7.s:d .8.s:d .11.s:d .12.s:d .13.s:d .15.s:d .17.s:d .18.s:d .19.s:d .20.s:d .24.s:d storage:26 .0.s:d .1.s:d .2.s:d .3.s:d .5.s:d .7.s:d .8.s:d .11.s:d .12.s:d .13.s:d .15.s:d .17.s:d .18.s:d .19.s:d .20.s:d .24.s:d");

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = setNodeState("storage/cluster.mycluster/storage/14", "s:r", connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("s"), req.toString());

        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r .*");

        req = setNodeState("storage/cluster.mycluster/storage/16", "s:m", connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("s"), req.toString());

        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r.* .16.s:m .*");
        nodes.get(5 * 2 + 1).disconnect();
        waitForCompleteCycle();
        timer.advanceTime(100000000);
        waitForCompleteCycle();
        assertEquals(State.MAINTENANCE, fleetController().getSystemState().getNodeState(new Node(NodeType.STORAGE, 16)).getState());

        nodes.get(4 * 2 + 1).disconnect();
        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:m.* .16.s:m .*");
        nodes.get(4 * 2 + 1).connect();
        timer.advanceTime(100000000);
        // Might need to pass more actual time while waiting below?
        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r.* .16.s:m .*");
    }

    @Test
    void testSetNodeStateOutOfRange() throws Exception {
        startingTest("RpcServerTest::testSetNodeStateOutOfRange");
        FleetControllerOptions.Builder options = defaultOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(2, 10)));
        setUpFleetController(true, options);
        setUpVdsNodes(true);
        waitForStableSystem();

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = setNodeState("storage/cluster.mycluster/storage/10", "s:m", connection);
        assertEquals(ErrorCode.METHOD_FAILED, req.errorCode(), req.toString());
        assertEquals("Cannot set wanted state of node storage.10. Index does not correspond to a configured node.", req.errorMessage(), req.toString());

        req = setNodeState("storage/cluster.mycluster/distributor/10", "s:m", connection);
        assertEquals(ErrorCode.METHOD_FAILED, req.errorCode(), req.toString());
        assertEquals("Cannot set wanted state of node distributor.10. Index does not correspond to a configured node.", req.errorMessage(), req.toString());

        req = setNodeState("storage/cluster.mycluster/storage/9", "s:m", connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());

        waitForState("version:\\d+ distributor:10 storage:10 .9.s:m");
    }

    @Test
    void testGetMaster() throws Exception {
        startingTest("RpcServerTest::testGetMaster");
        FleetControllerOptions.Builder options = defaultOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(2, 10)));
        setUpFleetController(true, options);
        setUpVdsNodes(true);
        waitForStableSystem();

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("getMaster");
        connection.invokeSync(req, timeout());
        assertEquals(0, req.returnValues().get(0).asInt32(), req.toString());
        assertEquals("All 1 nodes agree that 0 is current master.", req.returnValues().get(1).asString(), req.toString());

        // Note that this feature is tested better in MasterElectionTest.testGetMaster as it has multiple fleetcontrollers
    }

    @Test
    void testGetNodeList() throws Exception {
        startingTest("RpcServerTest::testGetNodeList");
        setUpFleetController(true, defaultOptions("mycluster", 5));
        final int nodeCount = 5;
        setUpVdsNodes(true, false, nodeCount);
        waitForStableSystem();

        assertTrue(nodes.get(0).isDistributor());
        nodes.get(0).disconnect();
        waitForState("version:\\d+ distributor:5 .0.s:d storage:5");

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("getNodeList");
        connection.invokeSync(req, timeout());
        assertEquals(ErrorCode.NONE, req.errorCode(), req.errorMessage());
        assertTrue(req.checkReturnTypes("SS"), req.toString());
        String[] slobrok = req.returnValues().get(0).asStringArray().clone();
        String[] rpc = req.returnValues().get(1).asStringArray().clone();

        assertEquals(2 * nodeCount, slobrok.length);
        assertEquals(2 * nodeCount, rpc.length);

        // Verify that we can connect to all addresses returned.
        for (int i = 0; i < 2 * nodeCount; ++i) {
            if (slobrok[i].equals("storage/cluster.mycluster/distributor/0")) {
                if (i < nodeCount && !"".equals(rpc[i])) {
                    continue;
                }
                assertEquals("", rpc[i], slobrok[i]);
                continue;
            }
            assertNotEquals("", rpc[i]);
            Request req2 = new Request("getnodestate3");
            req2.parameters().add(new StringValue("unknown"));
            Target connection2 = supervisor.connect(new Spec(rpc[i]));
            connection2.invokeSync(req2, timeout());
            assertEquals(ErrorCode.NONE, req.errorCode(), req2.toString());
        }
    }

    private Request setNodeState(String node, NodeState newNodeState, Target connection) {
        return setNodeState(node, newNodeState.serialize(true), connection);
    }

    private Request setNodeState(String node, String newNodeState, Target connection) {
        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue(node));
        req.parameters().add(new StringValue(newNodeState));
        connection.invokeSync(req, timeout());
        return req;
    }

    private Request getNodeState(String nodeType, int nodeIndex, Target connection) {
        Request req = new Request("getNodeState");
        req.parameters().add(new StringValue(nodeType));
        req.parameters().add(new Int32Value(nodeIndex));
        connection.invokeSync(req, timeout());
        return req;
    }

    private static class BackOff implements BackOffPolicy {
        private int counter = 0;
        public void reset() { counter = 0; }
        public double get() { ++counter; return 0.01; }
        public boolean shouldWarn(double v) { return ((counter % 1000) == 10); }
        public boolean shouldInform(double v) { return false; }
    }

}
