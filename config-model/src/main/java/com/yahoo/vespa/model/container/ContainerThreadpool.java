// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Component definition for a {@link java.util.concurrent.Executor} using {@link ContainerThreadPool}.
 *
 * @author bjorncs
 */
public class ContainerThreadpool extends SimpleComponent implements ContainerThreadpoolConfig.Producer {

    private final String name;
    private final UserOptions userOptions;

    public ContainerThreadpool(String name) { this(name, null); }

    public ContainerThreadpool(String name, UserOptions userOptions) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "threadpool@" + name,
                        ContainerThreadPool.class.getName(),
                        null)));
        this.name = name;
        this.userOptions = userOptions;
    }

    @Override
    public void getConfig(ContainerThreadpoolConfig.Builder builder) {
        builder.name(this.name);
        if (userOptions != null) {
            builder.maxThreads(userOptions.maxThreads);
            builder.minThreads(userOptions.minThreads);
            builder.queueSize(userOptions.queueSize);
        }
    }

    protected Optional<UserOptions> userOptions() { return Optional.ofNullable(userOptions); }
    protected boolean hasUserOptions() { return userOptions().isPresent(); }

    protected static double vcpu(ContainerCluster<?> cluster) {
        List<Double> vcpus = cluster.getContainers().stream()
                .filter(c -> c.getHostResource() != null && c.getHostResource().realResources() != null)
                .map(c -> c.getHostResource().realResources().vcpu())
                .distinct()
                .collect(Collectors.toList());
        // We can only use host resource for calculation if all container nodes in the cluster are homogeneous (in terms of vcpu)
        if (vcpus.size() != 1 || vcpus.get(0) == 0) return 0;
        return vcpus.get(0);
    }

    public static class UserOptions {
        private final int maxThreads;
        private final int minThreads;
        private final int queueSize;

        private UserOptions(int maxThreads, int minThreads, int queueSize) {
            this.maxThreads = maxThreads;
            this.minThreads = minThreads;
            this.queueSize = queueSize;
        }

        public static Optional<UserOptions> fromXml(Element xml) {
            Element element = XML.getChild(xml, "threadpool");
            if (element == null) return Optional.empty();
            return Optional.of(new UserOptions(
                    intOption(element, "max-threads"),
                    intOption(element, "min-threads"),
                    intOption(element, "queue-size")));
        }

        private static int intOption(Element element, String name) {
            return Integer.parseInt(XML.getChild(element, name).getTextContent());
        }
    }
}
