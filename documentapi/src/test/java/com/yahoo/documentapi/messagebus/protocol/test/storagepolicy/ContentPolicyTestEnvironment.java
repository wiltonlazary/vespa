// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test.storagepolicy;

import com.yahoo.collections.Pair;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolRoutingPolicy;
import com.yahoo.documentapi.messagebus.protocol.SlobrokPolicy;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RoutingPolicyFactory;
import com.yahoo.documentapi.messagebus.protocol.ContentPolicy;
import com.yahoo.documentapi.messagebus.protocol.WrongDistributionReply;
import com.yahoo.documentapi.messagebus.protocol.test.PolicyTestFrame;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.HopSpec;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingNode;
import com.yahoo.text.Utf8Array;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.RandomGen;
import org.junit.After;
import org.junit.Before;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class ContentPolicyTestEnvironment {

    protected ContentPolicyTestFactory policyFactory;
    protected PolicyTestFrame frame;
    private Set<Integer> nodes;
    protected static int[] bucketOneNodePreference = new int[]{ 3, 5, 7, 6, 8, 0, 9, 2, 1, 4 };
    protected boolean debug = false;

    @Before
    public void setUp() throws Exception {
        DocumentTypeManager manager = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(manager, "file:./test/cfg/testdoc.cfg");
        frame = new PolicyTestFrame(manager);
        nodes = new TreeSet<>();
        DocumentProtocol protocol = (DocumentProtocol) frame.getMessageBus().getProtocol((Utf8Array)DocumentProtocol.NAME);
        policyFactory = new ContentPolicyTestFactory(nodes);
        protocol.putRoutingPolicyFactory("storage", policyFactory);
        frame.setMessage(createMessage("id:ns:testdoc:n=1:foo"));
        frame.setHop(new HopSpec("test", "[storage:cluster=foo]"));
    }

    @After
    public void tearDown() {
        frame.destroy();
    }

    protected static Message createMessage(String id) {
        Message msg = new RemoveDocumentMessage(new DocumentId(id));
        msg.getTrace().setLevel(9);
        return msg;
    }

    protected void setClusterNodes(int[] ints) {
        Set<Integer> clusterNodes = new TreeSet<>();
        for (int i=0; i<ints.length; ++i) clusterNodes.add(ints[i]);
        nodes.clear();
        nodes.addAll(clusterNodes);
    }
    private static Pair<String, String> extractClusterAndIndexFromPattern(String pattern) {
        String[] bits = pattern.split("/");
        if (bits.length < 4) throw new IllegalStateException("Invalid pattern '" + pattern + "'. Expected more parts in it.");
        String distributor = bits[3];
        String cluster = bits[1];
        if (cluster.indexOf('.') < 0) throw new IllegalStateException("Expected . in cluster spec '" + cluster + "'.");
        cluster = cluster.substring(cluster.indexOf('.') + 1);
        return new Pair<>(cluster, distributor);
    }

    protected static Pair<String, Integer> getAddress(RoutingNode node) {
        Pair<String, String> pair = extractClusterAndIndexFromPattern(node.getRoute().getHop(0).toString());
        return new Pair<>(pair.getFirst(), Integer.valueOf(pair.getSecond()));
    }

    protected RoutingNode select() {
        List<RoutingNode> result = frame.select(1);
        assertEquals(1, result.size());
        return result.get(0);
    }

    protected void addNode(int index) {
        nodes.add(index);
    }
    protected void removeNode(int second) {
        assertTrue(nodes.remove(second));
    }

    public static class TestHostFetcher extends ContentPolicy.HostFetcher {
        private final String clusterName;
        private final RandomGen randomizer = new RandomGen(1234);
        private final Set<Integer> nodes;
        private Integer avoidPickingAtRandom = null;

        public TestHostFetcher(String clusterName, Set<Integer> nodes) {
            super(60);
            this.clusterName = clusterName;
            this.nodes = nodes;
        }

        public void setAvoidPickingAtRandom(Integer index) { avoidPickingAtRandom = index; }

        @Override
        public String getTargetSpec(Integer distributor, RoutingContext context) {
            try{
                if (distributor == null) {
                    if (nodes.size() == 1) {
                        assertNotSame(avoidPickingAtRandom, nodes.iterator().next());
                        distributor = nodes.iterator().next();
                    } else {
                        Iterator<Integer> it = nodes.iterator();
                        for (int i = 0, n = randomizer.nextInt(nodes.size() - 1); i<n; ++i) it.next();
                        distributor = it.next();
                        if (avoidPickingAtRandom != null && avoidPickingAtRandom.equals(distributor))
                            distributor = it.next();
                    }
                }
                if (nodes.contains(distributor)) {
                    return "storage/cluster." + clusterName + "/distributor/" + distributor;
                } else {
                    return null;
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw new AssertionError(e.getMessage());
            }
        }
    }

    public static class TestWrappingInstabilityChecker implements ContentPolicy.InstabilityChecker {

        public int recordedFailures = 0;
        private final ContentPolicy.InstabilityChecker fwdChecker;

        TestWrappingInstabilityChecker(ContentPolicy.InstabilityChecker fwdChecker) {
            this.fwdChecker = fwdChecker;
        }

        @Override
        public boolean tooManyFailures(int nodeIndex) {
            return fwdChecker.tooManyFailures(nodeIndex);
        }

        @Override
        public void addFailure(Integer calculatedDistributor) {
            ++recordedFailures;
            fwdChecker.addFailure(calculatedDistributor);
        }
    }

    public static class TestParameters extends ContentPolicy.Parameters {
        private final TestHostFetcher hostFetcher;
        private final Distribution distribution;
        public final TestWrappingInstabilityChecker instabilityChecker;

        public TestParameters(String parameters, Set<Integer> nodes) {
            super(SlobrokPolicy.parse(parameters));
            hostFetcher = new TestHostFetcher(getClusterName(), nodes);
            distribution = new Distribution(Distribution.getDefaultDistributionConfig(2, 10));
            instabilityChecker = new TestWrappingInstabilityChecker(new ContentPolicy.PerNodeCountingInstabilityChecker(5));
        }

        @Override
        public ContentPolicy.HostFetcher createHostFetcher(SlobrokPolicy policy, int percent) { return hostFetcher; }

        @Override
        public Distribution createDistribution(SlobrokPolicy policy) { return distribution; }

        @Override
        public ContentPolicy.InstabilityChecker createInstabilityChecker() { return instabilityChecker; }
    }

    public static class ContentPolicyTestFactory implements RoutingPolicyFactory {
        private Set<Integer> nodes;
        private final LinkedList<TestParameters> parameterInstances = new LinkedList<>();
        private Integer avoidPickingAtRandom = null;

        public ContentPolicyTestFactory(Set<Integer> nodes) {
            this.nodes = nodes;
        }
        public DocumentProtocolRoutingPolicy createPolicy(String parameters) {
            parameterInstances.addLast(new TestParameters(parameters, nodes));
            ((TestHostFetcher) parameterInstances.getLast().createHostFetcher(null, 60)).setAvoidPickingAtRandom(avoidPickingAtRandom);
            return new ContentPolicy(parameterInstances.getLast());
        }
        public void avoidPickingAtRandom(Integer distributor) {
            avoidPickingAtRandom = distributor;
            for (TestParameters params : parameterInstances) {
                ((TestHostFetcher) params.createHostFetcher(null, 60)).setAvoidPickingAtRandom(avoidPickingAtRandom);
            }
        }
        public TestParameters getLastParameters() { return parameterInstances.getLast(); }
    }

    private int findPreferredAvailableNodeForTestBucket() {
        for (int i=0; i<10; ++i) {
            if (nodes.contains(bucketOneNodePreference[i])) return bucketOneNodePreference[i];
        }
        throw new IllegalStateException("Found no node available");
    }

    protected void sendToCorrectNode(String cluster, int correctNode) {
        RoutingNode target = select();
        target.handleReply(new EmptyReply());
        Reply reply = frame.getReceptor().getReply(60);
        assertNotNull(reply);
        assertFalse(reply.hasErrors());
        assertEquals(reply.getTrace().toString(), "storage/cluster." + cluster + "/distributor/" + correctNode, target.getRoute().getHop(0).toString());
    }

    protected void replyWrongDistribution(RoutingNode target, String cluster, Integer randomNode, String clusterState) {
        // We want test to send to wrong node when sending to random. If distribution changes so the first random
        // node picked is the same node we should alter test
        if (randomNode != null) {
            assertFalse(randomNode == findPreferredAvailableNodeForTestBucket());
        }
        target.handleReply(new WrongDistributionReply(clusterState));
        Reply reply = frame.getReceptor().getReply(60);
        assertNotNull(reply);
        assertFalse(reply.hasErrors());

        // Verify that we sent to expected node
        if (randomNode != null) {
            assertEquals(reply.getTrace().toString(), "storage/cluster." + cluster + "/distributor/" + randomNode, target.getRoute().getHop(0).toString());
        }
        if (debug) System.err.println("WRONG DISTRIBUTION: " + reply.getTrace());
    }

    protected void replyOk(RoutingNode target) {
        target.handleReply(new EmptyReply());
        Reply reply = frame.getReceptor().getReply(60);
        assertNotNull(reply);
        assertFalse(reply.hasErrors());
        if (debug) System.err.println("OK: " + reply.getTrace());
    }

    protected void replyError(RoutingNode target, com.yahoo.messagebus.Error error) {
        EmptyReply reply = new EmptyReply();
        reply.addError(error);
        target.handleReply(reply);
        assertTrue(reply == frame.getReceptor().getReply(60));
        assertTrue(reply.hasErrors());
        if (debug) System.err.println("ERROR: " + reply.getTrace());
    }

}
