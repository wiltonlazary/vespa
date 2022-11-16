// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.flags.FlagRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.cores.Cores;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.configserver.state.State;

/**
 * The available (and implemented) APIs of the config server
 *
 * @author freva
 */
public interface ConfigServerClients {
    /** Get handle to /nodes/v2/ REST API */
    NodeRepository nodeRepository();

    /** Get handle to /orchestrator/v1/ REST API */
    Orchestrator orchestrator();

    /** Get handle to the /state/v1 REST API */
    State state();

    /** Get handle to the /flags/v1 REST API */
    FlagRepository flagRepository();

    /** Get handle to the /cores/v1 REST API */
    Cores cores();

    void stop();
}
