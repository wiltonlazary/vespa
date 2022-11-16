// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;

public interface StorageNode extends Comparable<StorageNode> {
    HostName hostName();
    void setStorageNodeState(OrchestratorContext context, ClusterControllerNodeState wantedState) throws HostStateChangeDeniedException;
    void forceDistributorState(OrchestratorContext context, ClusterControllerNodeState wantedState) throws HostStateChangeDeniedException;
}
