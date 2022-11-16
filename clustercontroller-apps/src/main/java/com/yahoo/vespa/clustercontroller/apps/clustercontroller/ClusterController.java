// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.clustercontroller.apputil.communication.http.JDiscMetricWrapper;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTaskScheduler;
import com.yahoo.vespa.clustercontroller.core.restapiv2.ClusterControllerStateRestAPI;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Wrapper around fleet controller state to be able to use it in container.
 */
public class ClusterController extends AbstractComponent
                               implements ClusterControllerStateRestAPI.FleetControllerResolver,
                                          StatusHandler.ClusterStatusPageServerSet {

    private static final Logger log = Logger.getLogger(ClusterController.class.getName());
    private final JDiscMetricWrapper metricWrapper;
    private final Map<String, FleetController> controllers = new TreeMap<>();
    private final Map<String, StatusHandler.ContainerStatusPageServer> status = new TreeMap<>();
    private final AtomicInteger referents = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    /**
     * Dependency injection constructor for controller. A {@link VespaZooKeeperServer} argument is required
     * for all its users, to ensure that zookeeper has started before we start polling it, but
     * should not be injected here, as that causes recreation of the cluster controller, and old and new
     * will run master election, etc., concurrently, which breaks everything.
     */
    @Inject
    public ClusterController() {
        metricWrapper = new JDiscMetricWrapper(null);
    }

    public void setOptions(FleetControllerOptions options, Metric metricImpl) throws Exception {
        referents.incrementAndGet();
        metricWrapper.updateMetricImplementation(metricImpl);
        verifyThatZooKeeperWorks(options);
        synchronized (controllers) {
            FleetController controller = controllers.get(options.clusterName());
            if (controller == null) {
                StatusHandler.ContainerStatusPageServer statusPageServer = new StatusHandler.ContainerStatusPageServer();
                controller = FleetController.create(options, statusPageServer, metricWrapper);
                controllers.put(options.clusterName(), controller);
                status.put(options.clusterName(), statusPageServer);
            } else {
                controller.updateOptions(options);
            }
        }
    }

    @Override
    public void deconstruct() {
        shutdown();
    }

    /**
     * Since we hack around injecting a running ZK here by providing one through the configurer instead,
     * we must also let the last configurer shut down this controller, to ensure this is shut down
     * before the ZK server it had injected from the configurers.
     */
    void countdown() {
        if (referents.decrementAndGet() == 0)
            shutdown();
    }

    void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            synchronized (controllers) {
                for (FleetController controller : controllers.values()) {
                    try {
                        shutdownController(controller);
                    }
                    catch (Exception e) {
                        log.warning("Failed to shut down fleet controller: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public Map<String, RemoteClusterControllerTaskScheduler> getFleetControllers() {
        synchronized (controllers) {
            return new LinkedHashMap<>(controllers);
        }
    }

    FleetController getController(String name) { return controllers.get(name); }

    @Override
    public StatusHandler.ContainerStatusPageServer get(String cluster) {
        return status.get(cluster);
    }

    @Override
    public Map<String, StatusHandler.ContainerStatusPageServer> getAll() {
        return status;
    }

    void shutdownController(FleetController controller) throws Exception {
        controller.shutdown();
    }

    /**
     * Block until we are connected to zookeeper server
     */
    private void verifyThatZooKeeperWorks(FleetControllerOptions options) throws Exception {
        if (options.zooKeeperServerAddress() != null && !"".equals(options.zooKeeperServerAddress())) {
            try (Curator curator = Curator.create(options.zooKeeperServerAddress())) {
                if ( ! curator.framework().blockUntilConnected(600, TimeUnit.SECONDS))
                    com.yahoo.protect.Process.logAndDie("Failed to connect to ZK, dying and restarting container");
            }
        }
    }

}
