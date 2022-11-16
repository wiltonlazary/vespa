// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vespa.clustercontroller.core.*;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.SlobrokListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;

public class ClusterControllerMock implements RemoteClusterControllerTaskScheduler {
    public RemoteClusterControllerTask.Context context = new RemoteClusterControllerTask.Context();

    private final int fleetControllerIndex;
    Integer fleetControllerMaster;
    private final StringBuilder events = new StringBuilder();

    ClusterControllerMock(ContentCluster cluster, ClusterState state,
                          ClusterStateBundle publishedClusterStateBundle,
                          int fcIndex, Integer fcMaster) {
        this.fleetControllerIndex = fcIndex;
        this.fleetControllerMaster = fcMaster;
        context.cluster = cluster;
        context.currentConsolidatedState = state;
        context.publishedClusterStateBundle = publishedClusterStateBundle;
        context.masterInfo = new MasterInterface() {
            @Override
            public boolean isMaster() {
                return (fleetControllerMaster != null &&
                        fleetControllerMaster == fleetControllerIndex);
            }

            @Override
            public boolean inMasterMoratorium() {
                return false;
            }

            @Override
            public Integer getMaster() {
                return fleetControllerMaster;
            }
        };
        context.nodeListener = new NodeListener() {

            @Override
            public void handleNewNodeState(NodeInfo currentInfo, NodeState newState) {
                events.append("newNodeState(").append(currentInfo.getNode()).append(": ").append(newState).append('\n');
            }

            @Override
            public void handleNewWantedNodeState(NodeInfo node, NodeState newState) {
                events.append("newWantedNodeState(").append(node.getNode()).append(": ").append(newState).append('\n');
            }

            @Override
            public void handleRemovedNode(Node node) {
                events.append("handleRemovedNode(").append(node).append(")\n");
            }

            @Override
            public void handleUpdatedHostInfo(NodeInfo node, HostInfo newHostInfo) {
                events.append("updatedHostInfo(").append(node.getNode()).append(": ")
                        .append(newHostInfo).append(")\n");
            }

        };
        context.slobrokListener = new SlobrokListener() {

            @Override
            public void handleNewNode(NodeInfo node) {
                events.append("newNode(").append(node.getNode()).append(")\n");
            }

            @Override
            public void handleMissingNode(NodeInfo node) {
                events.append("newMissingNode(").append(node.getNode()).append('\n');
            }

            @Override
            public void handleNewRpcAddress(NodeInfo node) {
                events.append("newRpcAddress(").append(node.getNode()).append('\n');
            }

            @Override
            public void handleReturnedRpcAddress(NodeInfo node) {
                events.append("returnedRpcAddress(").append(node.getNode()).append(")\n");
            }

        };
    }

    @Override
    public void schedule(RemoteClusterControllerTask task) {
        task.doRemoteFleetControllerTask(context);
        task.notifyCompleted();
    }

}
