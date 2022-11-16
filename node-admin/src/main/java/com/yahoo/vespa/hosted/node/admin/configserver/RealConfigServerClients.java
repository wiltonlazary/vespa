// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.flags.FlagRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.Cores;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.CoresImpl;
import com.yahoo.vespa.hosted.node.admin.configserver.flags.RealFlagRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.RealNodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.configserver.state.State;
import com.yahoo.vespa.hosted.node.admin.configserver.state.StateImpl;

/**
 * {@link ConfigServerClients} using the default implementation for the various clients,
 * and backed by a {@link ConfigServerApi}.
 *
 * @author freva
 */
public class RealConfigServerClients implements ConfigServerClients {
    private final ConfigServerApi configServerApi;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final State state;
    private final RealFlagRepository flagRepository;
    private final Cores cores;

    /**
     * @param configServerApi the backend API to use - will be closed at {@link #stop()}.
     */
    public RealConfigServerClients(ConfigServerApi configServerApi) {
        this.configServerApi = configServerApi;
        nodeRepository = new RealNodeRepository(configServerApi);
        orchestrator = new OrchestratorImpl(configServerApi);
        state = new StateImpl(configServerApi);
        flagRepository = new RealFlagRepository(configServerApi);
        cores = new CoresImpl(configServerApi);
    }

    @Override
    public NodeRepository nodeRepository() {
        return nodeRepository;
    }

    @Override
    public Orchestrator orchestrator() {
        return orchestrator;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public FlagRepository flagRepository() {
        return flagRepository;
    }

    @Override
    public Cores cores() {
        return cores;
    }

    @Override
    public void stop() {
        configServerApi.close();
    }
}
