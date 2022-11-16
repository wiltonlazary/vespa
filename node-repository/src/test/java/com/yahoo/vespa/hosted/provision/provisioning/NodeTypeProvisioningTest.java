// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.maintenance.RetiredExpirer;
import com.yahoo.vespa.hosted.provision.maintenance.TestMetric;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests provisioning by node type instead of by count and flavor
 * 
 * @author bratseth
 */
public class NodeTypeProvisioningTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();

    private final ApplicationId application = ProvisioningTester.applicationId(); // application using proxy nodes
    private final Capacity capacity = Capacity.fromRequiredNodeType(NodeType.proxy);
    private final ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();

    @Before
    public void setup() {
        tester.makeReadyNodes( 1, "small", NodeType.proxy);
        tester.makeReadyNodes( 3, "small", NodeType.host);
        tester.makeReadyNodes( 5, "small", NodeType.tenant);
        tester.makeReadyNodes(10, "large", NodeType.proxy);
        tester.makeReadyNodes(20, "large", NodeType.host);
        tester.makeReadyNodes(40, "large", NodeType.tenant);
    }

    @Test
    public void proxy_deployment() {
        { // Deploy
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals("Reserved all proxies", 11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        { // Redeploy with no changes
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());
        }

        { // Add 2 ready proxies then redeploy
            tester.makeReadyNodes(2, "small", NodeType.proxy);
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(13, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(13, nodes.size());
        }

        { // Remove 3 proxies then redeploy
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            tester.nodeRepository().nodes().fail(nodes.asList().get(0).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().nodes().fail(nodes.asList().get(1).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().nodes().fail(nodes.asList().get(5).hostname(), Agent.system, "Failing to unit test");
            
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(10, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(10, nodes.size());
        }
    }

    @Test
    public void retire_proxy() {
        MockDeployer deployer = new MockDeployer(tester.provisioner(),
                                                 tester.clock(),
                                                 Collections.singletonMap(application,
                                                                          new MockDeployer.ApplicationContext(application,
                                                                                                              clusterSpec,
                                                                                                              capacity)));
        RetiredExpirer retiredExpirer =  new RetiredExpirer(tester.nodeRepository(),
                                                            deployer,
                                                            new TestMetric(),
                                                            Duration.ofDays(30),
                                                            Duration.ofMinutes(10));

        { // Deploy
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals("Reserved all proxies", 11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        Node nodeToRetire = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy).asList().get(5);
        { // Pick out a node and retire it
            tester.nodeRepository().nodes().write(nodeToRetire.withWantToRetire(true, Agent.system, tester.clock().instant()), () -> {});

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            assertTrue(tester.nodeRepository().nodes().node(nodeToRetire.hostname())
                    .flatMap(Node::allocation)
                    .map(allocation -> allocation.membership().retired())
                    .orElseThrow(RuntimeException::new));
        }

        { // Redeploying while the node is still retiring has no effect
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that the node is still marked as retired
            assertTrue(tester.nodeRepository().nodes().node(nodeToRetire.hostname())
                    .flatMap(Node::allocation)
                    .map(allocation -> allocation.membership().retired())
                    .orElseThrow(RuntimeException::new));
        }

        {
            tester.advanceTime(Duration.ofMinutes(11));
            retiredExpirer.run();

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(10, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(10, nodes.size());

            // Verify that the node is now inactive
            assertEquals(Node.State.dirty, tester.nodeRepository().nodes().node(nodeToRetire.hostname())
                    .orElseThrow(RuntimeException::new).state());
        }
    }

    @Test
    public void retire_multiple_proxy_simultaneously() {
        MockDeployer deployer = new MockDeployer(tester.provisioner(),
                                                 tester.clock(),
                                                 Collections.singletonMap(application,
                                                                          new MockDeployer.ApplicationContext(application, clusterSpec, capacity)));
        RetiredExpirer retiredExpirer =  new RetiredExpirer(tester.nodeRepository(),
                                                            deployer,
                                                            new TestMetric(),
                                                            Duration.ofDays(30),
                                                            Duration.ofMinutes(10));
        final int numNodesToRetire = 5;

        { // Deploy
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals("Reserved all proxies", 11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        List<Node> nodesToRetire = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy).asList()
                .subList(3, 3 + numNodesToRetire);
        {
            nodesToRetire.forEach(nodeToRetire ->
                    tester.nodeRepository().nodes().write(nodeToRetire.withWantToRetire(true, Agent.system, tester.clock().instant()), () -> {}));

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            List<Node> nodesCurrentlyRetiring = nodes.stream()
                    .filter(node -> node.allocation().get().membership().retired())
                    .collect(Collectors.toList());
            assertEquals(5, nodesCurrentlyRetiring.size());

            // The retiring nodes should be the nodes we marked for retirement
            assertTrue(Set.copyOf(nodesToRetire).containsAll(nodesCurrentlyRetiring));
        }

        { // Redeploying while the nodes are still retiring has no effect
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            List<Node> nodesCurrentlyRetiring = nodes.stream()
                    .filter(node -> node.allocation().get().membership().retired())
                    .collect(Collectors.toList());
            assertEquals(5, nodesCurrentlyRetiring.size());
        }

        {
            // Let all retired nodes expire
            tester.advanceTime(Duration.ofMinutes(11));
            retiredExpirer.run();

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(6, hosts.size());
            tester.activate(application, new HashSet<>(hosts));

            // All currently active proxy nodes are not marked with wantToRetire or as retired
            long numRetiredActiveProxyNodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy).stream()
                    .filter(node -> !node.status().wantToRetire())
                    .filter(node -> !node.allocation().get().membership().retired())
                    .count();
            assertEquals(6, numRetiredActiveProxyNodes);

            // All the nodes that were marked with wantToRetire earlier are now dirty
            assertEquals(nodesToRetire.stream().map(Node::hostname).collect(Collectors.toSet()),
                    tester.nodeRepository().nodes().list(Node.State.dirty).stream().map(Node::hostname).collect(Collectors.toSet()));
        }
    }

    private List<HostSpec> deployProxies(ApplicationId application, ProvisioningTester tester) {
        return tester.prepare(application, clusterSpec, capacity);
    }

}
