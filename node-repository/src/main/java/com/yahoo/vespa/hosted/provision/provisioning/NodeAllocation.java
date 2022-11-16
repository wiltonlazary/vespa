// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.net.HostName;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodesAndHosts;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Used to manage a list of nodes during the node reservation process
 * in order to fulfill the nodespec.
 * 
 * @author bratseth
 */
class NodeAllocation {

    private static final Logger LOG = Logger.getLogger(NodeAllocation.class.getName());

    /** List of all nodes in node-repository */
    private final NodesAndHosts<? extends NodeList> allNodesAndHosts;

    /** The application this list is for */
    private final ApplicationId application;

    /** The cluster this list is for */
    private final ClusterSpec cluster;

    /** The requested nodes of this list */
    private final NodeSpec requestedNodes;

    /** The node candidates this has accepted so far, keyed on hostname */
    private final Map<String, NodeCandidate> nodes = new LinkedHashMap<>();

    /** The number of already allocated nodes accepted and not retired */
    private int accepted = 0;

    /** The number of already allocated nodes accepted and not retired and not needing resize */
    private int acceptedWithoutResizingRetired = 0;

    /** The number of nodes rejected because of clashing parentHostname */
    private int rejectedDueToClashingParentHost = 0;

    /** The number of nodes rejected due to exclusivity constraints */
    private int rejectedDueToExclusivity = 0;

    private int rejectedDueToInsufficientRealResources = 0;

    /** The number of nodes that just now was changed to retired */
    private int wasRetiredJustNow = 0;

    /** The node indexes to verify uniqueness of each members index */
    private final Set<Integer> indexes = new HashSet<>();

    /** The next membership index to assign to a new node */
    private final Supplier<Integer> nextIndex;

    private final NodeRepository nodeRepository;
    private final NodeResourceLimits nodeResourceLimits;
    private final Optional<String> requiredHostFlavor;

    NodeAllocation(NodesAndHosts<? extends NodeList> allNodesAndHosts, ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes,
                   Supplier<Integer> nextIndex, NodeRepository nodeRepository) {
        this.allNodesAndHosts = allNodesAndHosts;
        this.application = application;
        this.cluster = cluster;
        this.requestedNodes = requestedNodes;
        this.nextIndex = nextIndex;
        this.nodeRepository = nodeRepository;
        this.nodeResourceLimits = new NodeResourceLimits(nodeRepository);
        this.requiredHostFlavor = Optional.of(PermanentFlags.HOST_FLAVOR.bindTo(nodeRepository.flagSource())
                        .with(FetchVector.Dimension.APPLICATION_ID, application.serializedForm())
                        .with(FetchVector.Dimension.CLUSTER_TYPE, cluster.type().name())
                        .value())
                .filter(s -> !s.isBlank());
    }

    /**
     * Offer some nodes to this. The nodes may have an allocation to a different application or cluster,
     * an allocation to this cluster, or no current allocation (in which case one is assigned).
     *
     * Note that if unallocated nodes are offered before allocated nodes, this will unnecessarily
     * reject allocated nodes due to index duplicates.
     *
     * @param candidates the nodes which are potentially on offer. These may belong to a different application etc.
     */
    void offer(List<NodeCandidate> candidates) {
        for (NodeCandidate candidate : candidates) {
            if (candidate.allocation().isPresent()) {
                Allocation allocation = candidate.allocation().get();
                ClusterMembership membership = allocation.membership();
                if ( ! allocation.owner().equals(application)) continue; // wrong application
                if ( ! membership.cluster().satisfies(cluster)) continue; // wrong cluster id/type
                if ((! candidate.isSurplus || saturated()) && ! membership.cluster().group().equals(cluster.group())) continue; // wrong group, and we can't or have no reason to change it
                if ( candidate.state() == Node.State.active && allocation.removable()) continue; // don't accept; causes removal
                if ( candidate.state() == Node.State.active && candidate.wantToFail()) continue; // don't accept; causes failing
                if ( indexes.contains(membership.index())) continue; // duplicate index (just to be sure)
                if ( requiredHostFlavor.isPresent() && ! candidate.parent.map(node -> node.flavor().name()).equals(requiredHostFlavor)) continue;
                if ( candidate.parent.isPresent() && ! candidate.parent.get().cloudAccount().equals(requestedNodes.cloudAccount())) continue; // wrong account

                boolean resizeable = requestedNodes.considerRetiring() && candidate.isResizable;
                boolean acceptToRetire = acceptToRetire(candidate);

                if ((! saturated() && hasCompatibleFlavor(candidate) && requestedNodes.acceptable(candidate)) || acceptToRetire) {
                    candidate = candidate.withNode();
                    if (candidate.isValid()) {
                        acceptNode(candidate, shouldRetire(candidate, candidates), resizeable);
                    }
                }
            }
            else if (! saturated() && hasCompatibleFlavor(candidate)) {
                if (! nodeResourceLimits.isWithinRealLimits(candidate, cluster)) {
                    ++rejectedDueToInsufficientRealResources;
                    continue;
                }
                if ( violatesParentHostPolicy(candidate)) {
                    ++rejectedDueToClashingParentHost;
                    continue;
                }
                if ( violatesExclusivity(candidate)) {
                    ++rejectedDueToExclusivity;
                    continue;
                }
                if (candidate.wantToRetire()) {
                    continue;
                }
                candidate = candidate.allocate(application,
                                               ClusterMembership.from(cluster, nextIndex.get()),
                                               requestedNodes.resources().orElse(candidate.resources()),
                                               nodeRepository.clock().instant());
                if (candidate.isValid()) {
                    acceptNode(candidate, Retirement.none, false);
                }
            }
        }
    }

    /** Returns the cause of retirement for given candidate */
    private Retirement shouldRetire(NodeCandidate candidate, List<NodeCandidate> candidates) {
        if ( ! requestedNodes.considerRetiring()) {
            boolean alreadyRetired = candidate.allocation().map(a -> a.membership().retired()).orElse(false);
            return alreadyRetired ? Retirement.alreadyRetired : Retirement.none;
        }
        if ( ! nodeResourceLimits.isWithinRealLimits(candidate, cluster)) return Retirement.outsideRealLimits;
        if (violatesParentHostPolicy(candidate)) return Retirement.violatesParentHostPolicy;
        if ( ! hasCompatibleFlavor(candidate)) return Retirement.incompatibleFlavor;
        if (candidate.wantToRetire()) return Retirement.hardRequest;
        if (candidate.preferToRetire() && candidate.replaceableBy(candidates)) return Retirement.softRequest;
        if (violatesExclusivity(candidate)) return Retirement.violatesExclusivity;
        return Retirement.none;
    }

    private boolean violatesParentHostPolicy(NodeCandidate candidate) {
        return checkForClashingParentHost() && offeredNodeHasParentHostnameAlreadyAccepted(candidate);
    }

    private boolean checkForClashingParentHost() {
        return nodeRepository.zone().system() == SystemName.main &&
               nodeRepository.zone().environment().isProduction() &&
               ! application.instance().isTester();
    }

    private boolean offeredNodeHasParentHostnameAlreadyAccepted(NodeCandidate candidate) {
        for (NodeCandidate acceptedNode : nodes.values()) {
            if (acceptedNode.parentHostname().isPresent() && candidate.parentHostname().isPresent() &&
                    acceptedNode.parentHostname().get().equals(candidate.parentHostname().get())) {
                return true;
            }
        }
        return false;
    }

    private boolean violatesExclusivity(NodeCandidate candidate) {
        if (candidate.parentHostname().isEmpty()) return false;

        // In nodes which does not allow host sharing, exclusivity is violated if...
        if ( ! nodeRepository.zone().cloud().allowHostSharing()) {
            // TODO: Write this in a way that is simple to read
            // If either the parent is dedicated to a cluster type different from this cluster
            return  ! candidate.parent.flatMap(Node::exclusiveToClusterType).map(cluster.type()::equals).orElse(true) ||
                    // or this cluster is requiring exclusivity, but the host is exclusive to a different owner
                    (requestedNodes.isExclusive() && !candidate.parent.flatMap(Node::exclusiveToApplicationId).map(application::equals).orElse(false));
        }

        // In zones with shared hosts we require that if either of the nodes on the host requires exclusivity,
        // then all the nodes on the host must have the same owner
        for (Node nodeOnHost : allNodesAndHosts.childrenOf(candidate.parentHostname().get())) {
            if (nodeOnHost.allocation().isEmpty()) continue;
            if (requestedNodes.isExclusive() || nodeOnHost.allocation().get().membership().cluster().isExclusive()) {
                if ( ! nodeOnHost.allocation().get().owner().equals(application)) return true;
            }
        }
        return false;
    }

    /**
     * Returns whether this node should be accepted into the cluster even if it is not currently desired
     * (already enough nodes, or wrong flavor).
     * Such nodes will be marked retired during finalization of the list of accepted nodes.
     * The conditions for this are:
     *
     * This is a stateful node. These must always be retired before being removed to allow the cluster to
     * migrate away data.
     *
     * This is a container node and it is not desired due to having the wrong flavor. In this case this
     * will (normally) obtain for all the current nodes in the cluster and so retiring before removing must
     * be used to avoid removing all the current nodes at once, before the newly allocated replacements are
     * initialized. (In the other case, where a container node is not desired because we have enough nodes we
     * do want to remove it immediately to get immediate feedback on how the size reduction works out.)
     */
    private boolean acceptToRetire(NodeCandidate candidate) {
        if (candidate.state() != Node.State.active) return false;
        if (! candidate.allocation().get().membership().cluster().group().equals(cluster.group())) return false;
        if (candidate.allocation().get().membership().retired()) return true; // don't second-guess if already retired
        if (! requestedNodes.considerRetiring()) return false;

        return cluster.isStateful() ||
               (cluster.type() == ClusterSpec.Type.container && !hasCompatibleFlavor(candidate));
    }

    private boolean hasCompatibleFlavor(NodeCandidate candidate) {
        return requestedNodes.isCompatible(candidate.flavor(), nodeRepository.flavors()) || candidate.isResizable;
    }

    private Node acceptNode(NodeCandidate candidate, Retirement retirement, boolean resizeable) {
        Node node = candidate.toNode();

        if (node.allocation().isPresent()) // Record the currently requested resources
            node = node.with(node.allocation().get().withRequestedResources(requestedNodes.resources().orElse(node.resources())));

        if (retirement == Retirement.none) {
            accepted++;

            // We want to allocate new nodes rather than unretiring with resize, so count without those
            // for the purpose of deciding when to stop accepting nodes (saturation)
            if (node.allocation().isEmpty()
                || ! ( requestedNodes.needsResize(node) && node.allocation().get().membership().retired()))
                acceptedWithoutResizingRetired++;

            if (resizeable && ! ( node.allocation().isPresent() && node.allocation().get().membership().retired()))
                node = resize(node);

            if (node.state() != Node.State.active) // reactivated node - wipe state that deactivated it
                node = node.unretire().removable(false);
        } else {
            LOG.info("Retiring " + node + " because " + retirement.description());
            ++wasRetiredJustNow;
            node = node.retire(nodeRepository.clock().instant());
        }
        if ( ! node.allocation().get().membership().cluster().equals(cluster)) {
            // group may be different
            node = setCluster(cluster, node);
        }
        candidate = candidate.withNode(node);
        indexes.add(node.allocation().get().membership().index());
        nodes.put(node.hostname(), candidate);
        return node;
    }

    private Node resize(Node node) {
        NodeResources hostResources = allNodesAndHosts.parentOf(node).get().flavor().resources();
        return node.with(new Flavor(requestedNodes.resources().get()
                                                  .with(hostResources.diskSpeed())
                                                  .with(hostResources.storageType())
                                                  .with(hostResources.architecture())),
                Agent.application, nodeRepository.clock().instant());
    }

    private Node setCluster(ClusterSpec cluster, Node node) {
        ClusterMembership membership = node.allocation().get().membership().with(cluster);
        return node.with(node.allocation().get().with(membership));
    }

    /** Returns true if no more nodes are needed in this list */
    private boolean saturated() {
        return requestedNodes.saturatedBy(acceptedWithoutResizingRetired);
    }

    /** Returns true if the content of this list is sufficient to meet the request */
    boolean fulfilled() {
        return requestedNodes.fulfilledBy(accepted());
    }

    /** Returns true if this allocation was already fulfilled and resulted in no new changes */
    public boolean fulfilledAndNoChanges() {
        return fulfilled() && reservableNodes().isEmpty() && newNodes().isEmpty();
    }

    /**
     * Returns {@link HostDeficit} describing the host deficit for the given {@link NodeSpec}.
     *
     * @return empty if the requested spec is already fulfilled. Otherwise returns {@link HostDeficit} containing the
     * flavor and host count required to cover the deficit.
     */
    Optional<HostDeficit> hostDeficit() {
        if (nodeType().isHost()) {
            return Optional.empty(); // Hosts are provisioned as required by the child application
        }
        return Optional.of(new HostDeficit(requestedNodes.resources().orElseGet(NodeResources::unspecified),
                                           requestedNodes.fulfilledDeficitCount(accepted())))
                       .filter(hostDeficit -> hostDeficit.count() > 0);
    }

    /** Returns the indices to use when provisioning hosts for this */
    List<Integer> provisionIndices(int count) {
        if (count < 1) throw new IllegalArgumentException("Count must be positive");
        NodeType hostType = requestedNodes.type().hostType();

        // Tenant hosts have a continuously increasing index
        if (hostType == NodeType.host) return nodeRepository.database().readProvisionIndices(count);

        // Infrastructure hosts have fixed indices, starting at 1
        Set<Integer> currentIndices = allNodesAndHosts.nodes().nodeType(hostType)
                                              .hostnames()
                                              .stream()
                                              // TODO(mpolden): Use cluster index instead of parsing hostname, once all
                                              //                config servers have been replaced once and have switched
                                              //                to compact indices
                                              .map(NodeAllocation::parseIndex)
                                              .collect(Collectors.toSet());
        List<Integer> indices = new ArrayList<>(count);
        for (int i = 1; indices.size() < count; i++) {
            if (!currentIndices.contains(i)) {
                indices.add(i);
            }
        }
        // Ignore our own index as we should never try to provision ourself. This can happen in the following scenario:
        // - cfg1 has been deprovisioned
        // - cfg2 has triggered provisioning of a new cfg1
        // - cfg1 is starting and redeploys its infrastructure application during bootstrap. A deficit is detected
        //   (itself, because cfg1 is only added to the repository *after* it is up)
        // - cfg1 tries to provision a new host for itself
        Integer myIndex = parseIndex(HostName.getLocalhost());
        indices.remove(myIndex);
        return indices;
    }

    /** The node type this is allocating */
    NodeType nodeType() {
        return requestedNodes.type();
    }

    /**
     * Make the number of <i>non-retired</i> nodes in the list equal to the requested number
     * of nodes, and retire the rest of the list. Only retire currently active nodes.
     * Prefer to retire nodes of the wrong flavor.
     * Make as few changes to the retired set as possible.
     *
     * @return the final list of nodes
     */
    List<Node> finalNodes() {
        int wantToRetireCount = (int) nodes.values().stream().filter(NodeCandidate::wantToRetire).count();
        int currentRetiredCount = (int) nodes.values().stream().filter(node -> node.allocation().get().membership().retired()).count();
        int deltaRetiredCount = requestedNodes.idealRetiredCount(nodes.size(), wantToRetireCount, currentRetiredCount);

        if (deltaRetiredCount > 0) { // retire until deltaRetiredCount is 0
            for (NodeCandidate candidate : byRetiringPriority(nodes.values())) {
                if ( ! candidate.allocation().get().membership().retired() && candidate.state() == Node.State.active) {
                    candidate = candidate.withNode();
                    candidate = candidate.withNode(candidate.toNode().retire(Agent.application, nodeRepository.clock().instant()));
                    nodes.put(candidate.toNode().hostname(), candidate);
                    if (--deltaRetiredCount == 0) break;
                }
            }
        }
        else if (deltaRetiredCount < 0) { // unretire until deltaRetiredCount is 0
            for (NodeCandidate candidate : byUnretiringPriority(nodes.values())) {
                if ( candidate.allocation().get().membership().retired() && hasCompatibleFlavor(candidate) ) {
                    candidate = candidate.withNode();
                    if (candidate.isResizable)
                        candidate = candidate.withNode(resize(candidate.toNode()));
                    candidate = candidate.withNode(candidate.toNode().unretire());
                    nodes.put(candidate.toNode().hostname(), candidate);
                    if (++deltaRetiredCount == 0) break;
                }
            }
        }
        
        for (NodeCandidate candidate : nodes.values()) {
            // Set whether the node is exclusive
            candidate = candidate.withNode();
            Allocation allocation = candidate.allocation().get();
            candidate = candidate.withNode(candidate.toNode().with(allocation.with(allocation.membership()
                                 .with(allocation.membership().cluster().exclusive(requestedNodes.isExclusive())))));
            nodes.put(candidate.toNode().hostname(), candidate);
        }

        return nodes.values().stream().map(n -> n.toNode()).collect(Collectors.toList());
    }

    List<Node> reservableNodes() {
        // Include already reserved nodes to extend reservation period and to potentially update their cluster spec.
        EnumSet<Node.State> reservableStates = EnumSet.of(Node.State.inactive, Node.State.ready, Node.State.reserved);
        return nodesFilter(n -> ! n.isNew && reservableStates.contains(n.state()));
    }

    List<Node> newNodes() {
        return nodesFilter(n -> n.isNew);
    }

    private List<Node> nodesFilter(Predicate<NodeCandidate> predicate) {
        return nodes.values().stream()
                .filter(predicate)
                .map(n -> n.toNode())
                .collect(Collectors.toList());
    }

    /** Returns the number of nodes accepted this far */
    private int accepted() {
        if (nodeType() == NodeType.tenant) return accepted;
        // Infrastructure nodes are always allocated by type. Count all nodes as accepted so that we never exceed
        // the wanted number of nodes for the type.
        return allNodesAndHosts.nodes().nodeType(nodeType()).size();
    }

    /** Prefer to retire nodes we want the least */
    private List<NodeCandidate> byRetiringPriority(Collection<NodeCandidate> candidates) {
        return candidates.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    /** Prefer to unretire nodes we don't want to retire, and otherwise those with lower index */
    private List<NodeCandidate> byUnretiringPriority(Collection<NodeCandidate> candidates) {
        return candidates.stream()
                         .sorted(Comparator.comparing(NodeCandidate::wantToRetire)
                                           .thenComparing(n -> n.allocation().get().membership().index()))
                         .collect(Collectors.toList());
    }

    public String allocationFailureDetails() {
        List<String> reasons = new ArrayList<>();
        if (rejectedDueToExclusivity > 0)
            reasons.add("host exclusivity constraints");
        if (rejectedDueToClashingParentHost > 0)
            reasons.add("insufficient nodes available on separate physical hosts");
        if (wasRetiredJustNow > 0)
            reasons.add("retirement of allocated nodes");
        if (rejectedDueToInsufficientRealResources > 0)
            reasons.add("insufficient real resources on hosts");

        if (reasons.isEmpty()) return "";
        return ": Not enough suitable nodes available due to " + String.join(", ", reasons);
    }

    private static Integer parseIndex(String hostname) {
        // Node index is the first number appearing in the hostname, before the first dot
        try {
            return Integer.parseInt(hostname.replaceFirst("^\\D+(\\d+)\\..*", "$1"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Could not parse index from hostname '" + hostname + "'", e);
        }
    }

    /** Possible causes of node retirement */
    private enum Retirement {

        alreadyRetired("node is already retired"),
        outsideRealLimits("node real resources is outside limits"),
        violatesParentHostPolicy("node violates parent host policy"),
        incompatibleFlavor("node flavor is incompatible"),
        hardRequest("node is requested to retire"),
        softRequest("node is requested to retire (soft)"),
        violatesExclusivity("node violates host exclusivity"),
        none("");

        private final String description;

        Retirement(String description) {
            this.description = description;
        }

        /** Human readable description of this cause */
        public String description() {
            return description;
        }

    }

    /** A host deficit, the number of missing hosts, for a deployment */
    static class HostDeficit {

        private final NodeResources resources;
        private final int count;

        private HostDeficit(NodeResources resources, int count) {
            this.resources = resources;
            this.count = count;
        }

        NodeResources resources() {
            return resources;
        }

        int count() {
            return count;
        }

    }

}
