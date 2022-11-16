// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;


import com.yahoo.config.provision.ApplicationId;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.model.ApplicationApiFactory;
import com.yahoo.vespa.orchestrator.model.ClusterApi;
import com.yahoo.vespa.orchestrator.model.StorageNode;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author oyving
 * @author bakksjo
 */
public class HostedVespaPolicyTest {

    private final ClusterControllerClientFactory clientFactory = mock(ClusterControllerClientFactory.class);
    private final ClusterControllerClient client = mock(ClusterControllerClient.class);
    private final ManualClock clock = new ManualClock();
    private final ApplicationApiFactory applicationApiFactory = new ApplicationApiFactory(3, 5, clock);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

    @Before
    public void setUp() {
        when(clientFactory.createClient(any(), any())).thenReturn(client);
    }

    @Test
    public void testGrantSuspension() throws HostStateChangeDeniedException {
        final HostedVespaClusterPolicy clusterPolicy = mock(HostedVespaClusterPolicy.class);
        when(clusterPolicy.verifyGroupGoingDownIsFine(any())).thenReturn(SuspensionReasons.nothingNoteworthy());
        final HostedVespaPolicy policy = new HostedVespaPolicy(clusterPolicy, clientFactory, applicationApiFactory, flagSource);
        final ApplicationApi applicationApi = mock(ApplicationApi.class);
        when(applicationApi.applicationId()).thenReturn(ApplicationId.fromSerializedForm("tenant:app:default"));

        ClusterApi clusterApi1 = mock(ClusterApi.class);
        ClusterApi clusterApi2 = mock(ClusterApi.class);
        ClusterApi clusterApi3 = mock(ClusterApi.class);
        List<ClusterApi> clusterApis = Arrays.asList(clusterApi1, clusterApi2, clusterApi3);
        when(applicationApi.getClusters()).thenReturn(clusterApis);

        StorageNode storageNode1 = mock(StorageNode.class);
        HostName hostName1 = new HostName("storage-1");
        when(storageNode1.hostName()).thenReturn(hostName1);

        HostName hostName2 = new HostName("host-2");

        StorageNode storageNode3 = mock(StorageNode.class);
        HostName hostName3 = new HostName("storage-3");
        when(storageNode1.hostName()).thenReturn(hostName3);

        List<StorageNode> upStorageNodes = Arrays.asList(storageNode1, storageNode3);
        when(applicationApi.getNoRemarksStorageNodesInGroupInClusterOrder()).thenReturn(upStorageNodes);
        // setHostState

        List<HostName> noRemarksHostNames = Arrays.asList(hostName1, hostName2, hostName3);
        when(applicationApi.getNodesInGroupWithStatus(HostStatus.NO_REMARKS)).thenReturn(noRemarksHostNames);

        InOrder order = inOrder(applicationApi, clusterPolicy, storageNode1, storageNode3);

        OrchestratorContext context = mock(OrchestratorContext.class);
        policy.grantSuspensionRequest(context, applicationApi);

        order.verify(applicationApi).getClusters();
        order.verify(clusterPolicy).verifyGroupGoingDownIsFine(clusterApi1);
        order.verify(clusterPolicy).verifyGroupGoingDownIsFine(clusterApi2);
        order.verify(clusterPolicy).verifyGroupGoingDownIsFine(clusterApi3);

        order.verify(applicationApi).getNoRemarksStorageNodesInGroupInClusterOrder();
        order.verify(storageNode1).setStorageNodeState(context, ClusterControllerNodeState.MAINTENANCE);
        order.verify(storageNode3).setStorageNodeState(context, ClusterControllerNodeState.MAINTENANCE);

        order.verify(applicationApi).getNodesInGroupWithStatus(HostStatus.NO_REMARKS);
        order.verify(applicationApi).setHostState(context, hostName1, HostStatus.ALLOWED_TO_BE_DOWN);
        order.verify(applicationApi).setHostState(context, hostName2, HostStatus.ALLOWED_TO_BE_DOWN);
        order.verify(applicationApi).setHostState(context, hostName3, HostStatus.ALLOWED_TO_BE_DOWN);

        order.verifyNoMoreInteractions();
    }

    @Test
    public void testAcquirePermissionToRemove() throws OrchestrationException {
        final HostedVespaClusterPolicy clusterPolicy = mock(HostedVespaClusterPolicy.class);
        final HostedVespaPolicy policy = new HostedVespaPolicy(clusterPolicy, clientFactory, applicationApiFactory, flagSource);
        final ApplicationApi applicationApi = mock(ApplicationApi.class);
        when(applicationApi.applicationId()).thenReturn(ApplicationId.fromSerializedForm("tenant:app:default"));

        ClusterApi clusterApi1 = mock(ClusterApi.class);
        ClusterApi clusterApi2 = mock(ClusterApi.class);
        ClusterApi clusterApi3 = mock(ClusterApi.class);
        List<ClusterApi> clusterApis = Arrays.asList(clusterApi1, clusterApi2, clusterApi3);
        when(applicationApi.getClusters()).thenReturn(clusterApis);

        StorageNode storageNode1 = mock(StorageNode.class);
        HostName hostName1 = new HostName("storage-1");
        when(storageNode1.hostName()).thenReturn(hostName1);

        HostName hostName2 = new HostName("host-2");

        StorageNode storageNode3 = mock(StorageNode.class);
        HostName hostName3 = new HostName("storage-3");
        when(storageNode1.hostName()).thenReturn(hostName3);

        List<StorageNode> upStorageNodes = Arrays.asList(storageNode1, storageNode3);
        when(applicationApi.getStorageNodesInGroupInClusterOrder()).thenReturn(upStorageNodes);

        List<HostName> noRemarksHostNames = Arrays.asList(hostName1, hostName2, hostName3);
        when(applicationApi.getNodesInGroupWith(any())).thenReturn(noRemarksHostNames);

        InOrder order = inOrder(applicationApi, clusterPolicy, storageNode1, storageNode3);

        OrchestratorContext context = mock(OrchestratorContext.class);
        OrchestratorContext probeContext = mock(OrchestratorContext.class);
        when(context.createSubcontextForSingleAppOp(true)).thenReturn(probeContext);
        policy.acquirePermissionToRemove(context, applicationApi);

        order.verify(applicationApi).getClusters();
        order.verify(clusterPolicy).verifyGroupGoingDownPermanentlyIsFine(clusterApi1);
        order.verify(clusterPolicy).verifyGroupGoingDownPermanentlyIsFine(clusterApi2);
        order.verify(clusterPolicy).verifyGroupGoingDownPermanentlyIsFine(clusterApi3);

        order.verify(applicationApi).getStorageNodesInGroupInClusterOrder();
        order.verify(storageNode1).setStorageNodeState(probeContext, ClusterControllerNodeState.DOWN);
        order.verify(storageNode3).setStorageNodeState(probeContext, ClusterControllerNodeState.DOWN);

        order.verify(applicationApi).getNodesInGroupWith(any());
        order.verify(applicationApi).setHostState(context, hostName1, HostStatus.PERMANENTLY_DOWN);
        order.verify(applicationApi).setHostState(context, hostName2, HostStatus.PERMANENTLY_DOWN);
        order.verify(applicationApi).setHostState(context, hostName3, HostStatus.PERMANENTLY_DOWN);

        order.verifyNoMoreInteractions();
    }

    @Test
    public void testAcquirePermissionToRemoveConfigServer() throws OrchestrationException {
        final HostedVespaClusterPolicy clusterPolicy = mock(HostedVespaClusterPolicy.class);
        final HostedVespaPolicy policy = new HostedVespaPolicy(clusterPolicy, clientFactory, applicationApiFactory, flagSource);
        final ApplicationApi applicationApi = mock(ApplicationApi.class);
        when(applicationApi.applicationId()).thenReturn(ApplicationId.fromSerializedForm("tenant:app:default"));

        ClusterApi clusterApi1 = mock(ClusterApi.class);
        ClusterApi clusterApi2 = mock(ClusterApi.class);
        ClusterApi clusterApi3 = mock(ClusterApi.class);
        List<ClusterApi> clusterApis = Arrays.asList(clusterApi1, clusterApi2, clusterApi3);
        when(applicationApi.getClusters()).thenReturn(clusterApis);

        StorageNode storageNode1 = mock(StorageNode.class);
        HostName hostName1 = new HostName("storage-1");
        when(storageNode1.hostName()).thenReturn(hostName1);

        HostName hostName2 = new HostName("host-2");

        StorageNode storageNode3 = mock(StorageNode.class);
        HostName hostName3 = new HostName("storage-3");
        when(storageNode1.hostName()).thenReturn(hostName3);

        List<StorageNode> upStorageNodes = Arrays.asList(storageNode1, storageNode3);
        when(applicationApi.getStorageNodesInGroupInClusterOrder()).thenReturn(upStorageNodes);

        List<HostName> noRemarksHostNames = Arrays.asList(hostName1, hostName2, hostName3);
        when(applicationApi.getNodesInGroupWith(any())).thenReturn(noRemarksHostNames);

        InOrder order = inOrder(applicationApi, clusterPolicy, storageNode1, storageNode3);

        OrchestratorContext context = mock(OrchestratorContext.class);
        OrchestratorContext probeContext = mock(OrchestratorContext.class);
        when(context.createSubcontextForSingleAppOp(true)).thenReturn(probeContext);
        policy.acquirePermissionToRemove(context, applicationApi);

        order.verify(applicationApi).getClusters();
        order.verify(clusterPolicy).verifyGroupGoingDownPermanentlyIsFine(clusterApi1);
        order.verify(clusterPolicy).verifyGroupGoingDownPermanentlyIsFine(clusterApi2);
        order.verify(clusterPolicy).verifyGroupGoingDownPermanentlyIsFine(clusterApi3);

        order.verify(applicationApi).getStorageNodesInGroupInClusterOrder();
        order.verify(storageNode1).setStorageNodeState(probeContext, ClusterControllerNodeState.DOWN);
        order.verify(storageNode3).setStorageNodeState(probeContext, ClusterControllerNodeState.DOWN);

        order.verify(applicationApi).getNodesInGroupWith(any());
        order.verify(applicationApi).setHostState(context, hostName1, HostStatus.PERMANENTLY_DOWN);
        order.verify(applicationApi).setHostState(context, hostName2, HostStatus.PERMANENTLY_DOWN);
        order.verify(applicationApi).setHostState(context, hostName3, HostStatus.PERMANENTLY_DOWN);

        order.verifyNoMoreInteractions();
    }
}
