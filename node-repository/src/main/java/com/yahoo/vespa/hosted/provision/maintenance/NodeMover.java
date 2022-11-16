// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodesAndHosts;
import com.yahoo.vespa.hosted.provision.provisioning.HostCapacity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Base class for maintainers that move nodes.
 *
 * @author mpolden
 */
public abstract class NodeMover<MOVE> extends NodeRepositoryMaintainer {

    static final Duration waitTimeAfterPreviousDeployment = Duration.ofMinutes(10);

    private final Deployer deployer;
    private final MOVE emptyMove;
    private final Random random;

    public NodeMover(Deployer deployer, NodeRepository nodeRepository, Duration interval, Metric metric, MOVE emptyMove) {
        super(nodeRepository, interval, metric);
        this.deployer = deployer;
        this.emptyMove = emptyMove;
        this.random = new Random(nodeRepository.clock().millis());
    }

    /** Returns a suggested move for given node */
    protected abstract MOVE suggestedMove(Node node, Node fromHost, Node toHost, NodesAndHosts<? extends NodeList> allNodes);

    private static class HostWithResources {
        private final Node node;
        private final NodeResources hostResources;
        HostWithResources(Node node, NodeResources hostResources) {
            this.node = node;
            this.hostResources = hostResources;
        }
        boolean hasCapacity(NodeResources requested) {
            return hostResources.satisfies(requested);
        }

    }

    /** Find the best possible move */
    protected final MOVE findBestMove(NodesAndHosts<? extends NodeList> allNodes) {
        HostCapacity capacity = new HostCapacity(allNodes, nodeRepository().resourcesCalculator());
        MOVE bestMove = emptyMove;
        // Shuffle nodes to not get stuck if the chosen move is consistently discarded. Node moves happen through
        // a soft request to retire (preferToRetire), which node allocation can disregard
        NodeList activeNodes = allNodes.nodes().nodeType(NodeType.tenant)
                                       .state(Node.State.active)
                                       .shuffle(random);
        Set<Node> spares = capacity.findSpareHosts(allNodes.nodes().asList(), nodeRepository().spareCount());
        List<HostWithResources> hostResources = new ArrayList<>();
        allNodes.nodes().matching(nodeRepository().nodes()::canAllocateTenantNodeTo).forEach(host -> hostResources.add(new HostWithResources(host, capacity.availableCapacityOf(host))));
        for (Node node : activeNodes) {
            if (node.parentHostname().isEmpty()) continue;
            ApplicationId applicationId = node.allocation().get().owner();
            if (applicationId.instance().isTester()) continue;
            if (deployedRecently(applicationId)) continue;
            for (HostWithResources toHost : hostResources) {
                if (toHost.node.hostname().equals(node.parentHostname().get())) continue;
                if (toHost.node.reservedTo().isPresent() &&
                    !toHost.node.reservedTo().get().equals(applicationId.tenant())) continue; // Reserved to a different tenant
                if (spares.contains(toHost.node)) continue; // Do not offer spares as a valid move as they are reserved for replacement of failed nodes
                if ( ! toHost.hasCapacity(node.resources())) continue;

                MOVE suggestedMove = suggestedMove(node, allNodes.parentOf(node).get(), toHost.node, allNodes);
                bestMove = bestMoveOf(bestMove, suggestedMove);
            }
        }
        return bestMove;
    }

    /** Returns the best move of given moves */
    protected abstract MOVE bestMoveOf(MOVE a, MOVE b);

    private boolean deployedRecently(ApplicationId application) {
        Instant now = nodeRepository().clock().instant();
        return deployer.lastDeployTime(application)
                       .map(lastDeployTime -> lastDeployTime.isAfter(now.minus(waitTimeAfterPreviousDeployment)))
                       // We only know last deploy time for applications that were deployed on this config server,
                       // the rest will be deployed on another config server
                       .orElse(true);
    }

    /** Returns true if no active nodes are retiring or about to be retired */
    static boolean zoneIsStable(NodeList allNodes) {
        return allNodes.state(Node.State.active).stream()
                       .noneMatch(node -> node.allocation().get().membership().retired() ||
                                          node.status().wantToRetire() ||
                                          node.status().preferToRetire());
    }

}
