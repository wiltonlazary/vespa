// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.di.IntConfig;
import com.yahoo.config.test.TestConfig;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.SimpleComponent;
import com.yahoo.container.di.componentgraph.core.ComponentNode.ComponentConstructorException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class ContainerTest extends ContainerTestBase {

    @Test
    void components_can_be_created_from_config() {
        writeBootstrapConfigs();
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");

        Container container = newContainer(dirConfigSource);

        ComponentTakingConfig component = createComponentTakingConfig(getNewComponentGraph(container));
        assertEquals("myString", component.config.stringVal());

        container.shutdownConfigRetriever();
    }

    @Test
    void components_are_reconfigured_after_config_update_without_bootstrap_configs() {
        writeBootstrapConfigs();
        dirConfigSource.writeConfig("test", "stringVal \"original\"");

        Container container = newContainer(dirConfigSource);
        ComponentGraph componentGraph = getNewComponentGraph(container);
        ComponentTakingConfig component = createComponentTakingConfig(componentGraph);

        assertEquals("original", component.config.stringVal());

        // Reconfigure
        dirConfigSource.writeConfig("test", "stringVal \"reconfigured\"");
        container.reloadConfig(2);

        ComponentGraph newComponentGraph = getNewComponentGraph(container, componentGraph);
        ComponentTakingConfig component2 = createComponentTakingConfig(newComponentGraph);
        assertEquals("reconfigured", component2.config.stringVal());

        container.shutdownConfigRetriever();
        container.shutdown(newComponentGraph);
    }

    @Test
    void graph_is_updated_after_bootstrap_update() {
        dirConfigSource.writeConfig("test", "stringVal \"original\"");
        writeBootstrapConfigs("id1");

        Container container = newContainer(dirConfigSource);

        ComponentGraph graph = getNewComponentGraph(container);
        ComponentTakingConfig component = createComponentTakingConfig(graph);
        assertEquals("id1", component.getId().toString());

        writeBootstrapConfigs(
                new ComponentEntry("id1", ComponentTakingConfig.class),
                new ComponentEntry("id2", ComponentTakingConfig.class));

        container.reloadConfig(2);
        ComponentGraph newGraph = getNewComponentGraph(container, graph);

        assertNotNull(ComponentGraph.getNode(newGraph, "id1"));
        assertNotNull(ComponentGraph.getNode(newGraph, "id2"));

        container.shutdownConfigRetriever();
        container.shutdown(newGraph);
    }

    @Test
    void bundle_from_previous_generation_is_uninstalled_when_not_used_in_the_new_generation() {
        ComponentEntry component1 = new ComponentEntry("component1", SimpleComponent.class);
        ComponentEntry component2 = new ComponentEntry("component2", SimpleComponent.class);

        writeBootstrapConfigsWithBundles(List.of("bundle-1"), List.of(component1));
        Container container = newContainer(dirConfigSource);
        ComponentGraph graph = getNewComponentGraph(container);

        // bundle-1 is installed
        assertEquals(1, osgi.getBundles().length);
        assertEquals("bundle-1", osgi.getBundles()[0].getSymbolicName());

        writeBootstrapConfigsWithBundles(List.of("bundle-2"), List.of(component2));
        container.reloadConfig(2);
        ComponentGraph newGraph = getNewComponentGraph(container, graph);

        // bundle-2 is installed, bundle-1 has been uninstalled
        assertEquals(1, osgi.getBundles().length);
        assertEquals("bundle-2", osgi.getBundles()[0].getSymbolicName());

        container.shutdownConfigRetriever();
        container.shutdown(newGraph);
    }

    @Test
    void component_is_deconstructed_when_not_reused() {
        writeBootstrapConfigs("id1", DestructableComponent.class);

        Container container = newContainer(dirConfigSource);

        ComponentGraph oldGraph = getNewComponentGraph(container);
        DestructableComponent componentToDestruct = oldGraph.getInstance(DestructableComponent.class);

        writeBootstrapConfigs("id2", DestructableComponent.class);
        container.reloadConfig(2);
        ComponentGraph newGraph = getNewComponentGraph(container, oldGraph);
        assertTrue(componentToDestruct.deconstructed);

        container.shutdownConfigRetriever();
        container.shutdown(newGraph);
    }

    @Disabled("because logAndDie is impossible(?) to verify programmatically")
    @Test
    void manually_verify_what_happens_when_first_graph_contains_component_that_throws_exception_in_ctor() {
        writeBootstrapConfigs("thrower", ComponentThrowingExceptionInConstructor.class);
        Container container = newContainer(dirConfigSource);
        try {
            getNewComponentGraph(container);
            fail("Expected to log and die.");
        } catch (Throwable t) {
            fail("Expected to log and die");
        }

        container.shutdownConfigRetriever();
    }

    // Failure in component construction phase
    @Test
    void previous_graph_is_retained_when_new_graph_contains_component_that_throws_exception_in_ctor() {
        ComponentEntry simpleComponentEntry = new ComponentEntry("simpleComponent", SimpleComponent.class);

        writeBootstrapConfigs(simpleComponentEntry);
        Container container = newContainer(dirConfigSource);
        ComponentGraph currentGraph = getNewComponentGraph(container);

        SimpleComponent simpleComponent = currentGraph.getInstance(SimpleComponent.class);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionInConstructor.class);
        container.reloadConfig(2);
        assertNewComponentGraphFails(container, currentGraph, ComponentConstructorException.class);
        assertEquals(1, currentGraph.generation());

        // Also verify that next reconfig is successful
        ComponentEntry componentTakingConfigEntry = new ComponentEntry("componentTakingConfig", ComponentTakingConfig.class);
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");
        writeBootstrapConfigs(simpleComponentEntry, componentTakingConfigEntry);
        container.reloadConfig(3);
        currentGraph = getNewComponentGraph(container, currentGraph);

        assertEquals(3, currentGraph.generation());
        assertSame(simpleComponent, currentGraph.getInstance(SimpleComponent.class));
        assertNotNull(currentGraph.getInstance(ComponentTakingConfig.class));

        container.shutdownConfigRetriever();
        container.shutdown(currentGraph);
    }

    @Test
    void bundle_from_generation_that_fails_in_component_construction_is_uninstalled() {
        ComponentEntry simpleComponentEntry = new ComponentEntry("simpleComponent", SimpleComponent.class);
        ComponentEntry throwingComponentEntry = new ComponentEntry("throwingComponent", ComponentThrowingExceptionInConstructor.class);

        writeBootstrapConfigsWithBundles(List.of("bundle-1"), List.of(simpleComponentEntry));
        Container container = newContainer(dirConfigSource);
        ComponentGraph currentGraph = getNewComponentGraph(container);

        // bundle-1 is installed
        assertEquals(1, osgi.getBundles().length);
        assertEquals("bundle-1", osgi.getBundles()[0].getSymbolicName());

        writeBootstrapConfigsWithBundles(List.of("bundle-2"), List.of(throwingComponentEntry));
        container.reloadConfig(2);
        assertNewComponentGraphFails(container, currentGraph, ComponentConstructorException.class);
        assertEquals(1, currentGraph.generation());

        // bundle-1 is kept, bundle-2 has been uninstalled
        assertEquals(1, osgi.getBundles().length);
        assertEquals("bundle-1", osgi.getBundles()[0].getSymbolicName());

        container.shutdownConfigRetriever();
        container.shutdown(currentGraph);
    }

    // Failure in graph creation phase
    @Test
    void previous_graph_is_retained_when_new_graph_throws_exception_for_missing_config() {
        ComponentEntry simpleComponentEntry = new ComponentEntry("simpleComponent", SimpleComponent.class);

        writeBootstrapConfigs(simpleComponentEntry);
        Container container = newContainer(dirConfigSource);
        ComponentGraph currentGraph = getNewComponentGraph(container);

        currentGraph.getInstance(SimpleComponent.class);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionForMissingConfig.class);
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");
        container.reloadConfig(2);
        assertNewComponentGraphFails(container, currentGraph, IllegalArgumentException.class);
        assertEquals(1, currentGraph.generation());

        container.shutdownConfigRetriever();
        container.shutdown(currentGraph);
    }

    @Test
    void bundle_from_generation_that_throws_in_graph_creation_phase_is_uninstalled() {
        ComponentEntry simpleComponent = new ComponentEntry("simpleComponent", SimpleComponent.class);
        ComponentEntry configThrower = new ComponentEntry("configThrower", ComponentThrowingExceptionForMissingConfig.class);

        writeBootstrapConfigsWithBundles(List.of("bundle-1"), List.of(simpleComponent));
        Container container = newContainer(dirConfigSource);
        ComponentGraph currentGraph = getNewComponentGraph(container);

        // bundle-1 is installed
        assertEquals(1, osgi.getBundles().length);
        assertEquals("bundle-1", osgi.getBundles()[0].getSymbolicName());

        writeBootstrapConfigsWithBundles(List.of("bundle-2"), List.of(configThrower));
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");
        container.reloadConfig(2);

        assertNewComponentGraphFails(container, currentGraph, IllegalArgumentException.class);
        assertEquals(1, currentGraph.generation());

        // bundle-1 is kept, bundle-2 has been uninstalled
        assertEquals(1, osgi.getBundles().length);
        assertEquals("bundle-1", osgi.getBundles()[0].getSymbolicName());

        container.shutdownConfigRetriever();
        container.shutdown(currentGraph);
    }

    private void assertNewComponentGraphFails(Container container, ComponentGraph currentGraph, Class<? extends RuntimeException> exception) {
        try {
            getNewComponentGraph(container, currentGraph);
            fail("Expected exception");
        } catch (Exception e) {
            assertEquals(exception, e.getClass());
        }
    }

    @Test
    void getNewComponentGraph_hangs_waiting_for_valid_config_after_invalid_config() throws Exception {
        dirConfigSource.writeConfig("test", "stringVal \"original\"");
        writeBootstrapConfigs("myId", ComponentTakingConfig.class);

        Container container = newContainer(dirConfigSource);
        final ComponentGraph currentGraph = getNewComponentGraph(container);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionForMissingConfig.class);
        container.reloadConfig(2);

        assertThrows(IllegalArgumentException.class,
                     () -> getNewComponentGraph(container, currentGraph));

        ExecutorService exec = Executors.newFixedThreadPool(1);
        dirConfigSource.clearCheckedConfigs();
        Future<ComponentGraph> newGraph = exec.submit(() -> getNewComponentGraph(container, currentGraph));
        dirConfigSource.awaitConfigChecked(10_000);
        try {
            newGraph.get(1, TimeUnit.SECONDS);
            fail("Expected waiting for new config.");
        } catch (TimeoutException ignored) {
            // expect to time out
        }

        writeBootstrapConfigs("myId2", ComponentTakingConfig.class);
        container.reloadConfig(3);

        assertNotNull(newGraph.get(10, TimeUnit.SECONDS));

        container.shutdownConfigRetriever();
        container.shutdown(newGraph.get());
    }

    @Test
    void providers_are_destroyed() {
        writeBootstrapConfigs("id1", DestructableProvider.class);

        ComponentDeconstructor deconstructor = (generation, components, bundles) -> {
            components.forEach(component -> {
                if (component instanceof AbstractComponent) {
                    ((AbstractComponent) component).deconstruct();
                } else if (component instanceof Provider) {
                    ((Provider<?>) component).deconstruct();
                }
            });
            if (!bundles.isEmpty()) throw new IllegalArgumentException("This test should not use bundles");
        };

        Container container = newContainer(dirConfigSource, deconstructor);

        ComponentGraph oldGraph = getNewComponentGraph(container);
        DestructableEntity destructableEntity = oldGraph.getInstance(DestructableEntity.class);

        writeBootstrapConfigs("id2", DestructableProvider.class);
        container.reloadConfig(2);
        ComponentGraph graph = getNewComponentGraph(container, oldGraph);

        assertTrue(destructableEntity.deconstructed);

        container.shutdownConfigRetriever();
        container.shutdown(graph);
    }

    @Test
    void providers_are_invoked_only_when_needed() {
        writeBootstrapConfigs("id1", FailOnGetProvider.class);

        Container container = newContainer(dirConfigSource);

        ComponentGraph oldGraph = getNewComponentGraph(container);
        container.shutdown(oldGraph);
    }

    static class DestructableEntity {
        private boolean deconstructed = false;
    }

    public static class DestructableProvider implements Provider<DestructableEntity> {
        DestructableEntity instance = new DestructableEntity();

        public DestructableEntity get() {
            return instance;
        }

        public void deconstruct() {
            assertFalse(instance.deconstructed);
            instance.deconstructed = true;
        }
    }

    public static class FailOnGetProvider implements Provider<Integer> {

        public Integer get() {
            fail("Should never be called.");
            return null;
        }

        public void deconstruct() {
        }

    }

    public static class ComponentTakingConfig extends AbstractComponent {
        private final TestConfig config;

        public ComponentTakingConfig(TestConfig config) {
            assertNotNull(config);
            this.config = config;
        }
    }

    public static class ComponentThrowingExceptionInConstructor {
        public ComponentThrowingExceptionInConstructor() {
            throw new RuntimeException("This component fails upon construction.");
        }
    }

    public static class ComponentThrowingExceptionForMissingConfig extends AbstractComponent {
        public ComponentThrowingExceptionForMissingConfig(IntConfig intConfig) {
            fail("This component should never be created. Only used for tests where 'int' config is missing.");
        }
    }

    public static class DestructableComponent extends AbstractComponent {
        private boolean deconstructed = false;

        @Override
        public void deconstruct() {
            deconstructed = true;
        }
    }

    private ComponentTakingConfig createComponentTakingConfig(ComponentGraph componentGraph) {
        return componentGraph.getInstance(ComponentTakingConfig.class);
    }

}
