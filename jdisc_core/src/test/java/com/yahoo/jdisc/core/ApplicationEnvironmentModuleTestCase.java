// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.NonWorkingOsgiFramework;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ApplicationEnvironmentModuleTestCase {

    @Test
    void requireThatBindingsExist() {
        List<Class<?>> expected = new LinkedList<>();
        expected.add(ContainerActivator.class);
        expected.add(ContainerBuilder.class);
        expected.add(CurrentContainer.class);
        expected.add(OsgiFramework.class);
        expected.add(ThreadFactory.class);

        Injector injector = Guice.createInjector();
        for (Map.Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
            expected.add(entry.getKey().getTypeLiteral().getRawType());
        }

        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(), emptyList());
        injector = Guice.createInjector(new ApplicationEnvironmentModule(loader));
        for (Map.Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
            assertNotNull(expected.remove(entry.getKey().getTypeLiteral().getRawType()));
        }
        assertTrue(expected.isEmpty());
    }

    @Test
    void requireThatContainerBuilderCanBeInjected() {
        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(), emptyList());
        assertNotNull(new ApplicationEnvironmentModule(loader).containerBuilder());
        assertNotNull(Guice.createInjector(new ApplicationEnvironmentModule(loader))
                .getInstance(ContainerBuilder.class));
    }
}
