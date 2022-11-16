// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Expires instances in zones that have configured expiration using TimeToLive.
 * 
 * @author mortent
 * @author bratseth
 */
public class DeploymentExpirer extends ControllerMaintainer {

    public DeploymentExpirer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        int attempts = 0;
        int failures = 0;
        for (Application application : controller().applications().readable()) {
            for (Instance instance : application.instances().values())
                for (Deployment deployment : instance.deployments().values()) {
                    if (!isExpired(deployment, instance.id())) continue;

                    try {
                        log.log(Level.INFO, "Expiring deployment of " + instance.id() + " in " + deployment.zone());
                        attempts++;
                        controller().applications().deactivate(instance.id(), deployment.zone());
                    } catch (Exception e) {
                        failures++;
                        log.log(Level.WARNING, "Could not expire " + deployment + " of " + instance +
                                               ": " + Exceptions.toMessageString(e) + ". Retrying in " +
                                               interval());
                    }
                }
        }
        return asSuccessFactor(attempts, failures);
    }

    /** Returns whether given deployment has expired according to its TTL */
    private boolean isExpired(Deployment deployment, ApplicationId instance) {
        if (deployment.zone().environment().isProduction()) return false; // Never expire production deployments

        Optional<Duration> ttl = controller().zoneRegistry().getDeploymentTimeToLive(deployment.zone());
        if (ttl.isEmpty()) return false;

        return controller().jobController().lastDeploymentStart(instance, deployment)
                           .plus(ttl.get()).isBefore(controller().clock().instant());
    }

}
