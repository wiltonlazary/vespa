// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.google.common.collect.Collections2;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.application.provider.SimpleApplicationValidator;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.model.test.TestRoot;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.di.config.PlatformBundlesConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.config.content.reindexing.ReindexingConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for creating cluster controllers under the admin tag.
 */
public class ClusterControllerTestCase extends DomBuilderTest {

    private List<String> sds;

    @BeforeEach
    public void setup() {
        sds = ApplicationPackageUtils.generateSchemas("type1", "type2");
    }

    @Test
    void testSingleCluster() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services>\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"configserver\" />\n" +
                "    <cluster-controllers>\n" +
                "      <cluster-controller hostalias=\"configserver\">" +
                "      </cluster-controller>" +
                "      <cluster-controller hostalias=\"configserver\"/>" +
                "      <cluster-controller hostalias=\"configserver\"/>" +
                "    </cluster-controllers>\n" +
                "  </admin>\n" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>\n" +
                "     <documents>" +
                "       <document type=\"type1\" mode=\"store-only\"/>\n" +
                "     </documents>\n" +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='0' />" +
                "     </group>" +
                "    <tuning>" +
                "      <cluster-controller>\n" +
                "        <init-progress-time>34567s</init-progress-time>" +
                "        <transition-time>4000ms</transition-time>" +
                "        <stable-state-period>1h</stable-state-period>" +
                "      </cluster-controller>" +
                "    </tuning>" +
                "   </content>" +
                "\n" +
                "</services>";


        VespaModel model = createVespaModel(xml);
        assertTrue(model.getService("admin/cluster-controllers/0").isPresent());

        assertTrue(existsHostsWithClusterControllerConfigId(model));
        assertGroupSize(model, "admin/cluster-controllers/0/components/clustercontroller-bar-configurer", 1);
        for (int i = 0; i < 3; ++i) {
            FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
            model.getConfig(builder, "admin/cluster-controllers/" + i + "/components/clustercontroller-bar-configurer");

            FleetcontrollerConfig cfg = new FleetcontrollerConfig(builder);
            assertEquals(i, cfg.index());
            assertEquals(3, cfg.fleet_controller_count());
            assertEquals(34567000, cfg.init_progress_time());
            assertEquals(4000, cfg.storage_transition_time());
            assertEquals(3600000, cfg.stable_state_time_period());
        }

        assertOnlyNecessaryBundles(model);
    }

    private void assertOnlyNecessaryBundles(VespaModel model) {
        PlatformBundlesConfig config = model.getConfig(PlatformBundlesConfig.class, "admin/cluster-controllers");
        Set<String> unnecessaryBundles = PlatformBundles.VESPA_SECURITY_BUNDLES.stream().map(Path::toString).collect(toSet());
        assertTrue(config.bundlePaths().stream()
                           .noneMatch(unnecessaryBundles::contains));
    }

    @Test
    void testSeparateHostsRequired() {
        assertThrows(IllegalArgumentException.class, () -> {
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<services>\n" +
                    "\n" +
                    "  <admin version=\"2.0\">\n" +
                    "    <adminserver hostalias=\"mockhost\" />\n" +
                    "    <cluster-controllers standalone-zookeeper=\"true\">\n" +
                    "      <cluster-controller hostalias=\"mockhost\"/>" +
                    "      <cluster-controller hostalias=\"mockhost\"/>" +
                    "      <cluster-controller hostalias=\"mockhost\"/>" +
                    "    </cluster-controllers>\n" +
                    "  </admin>\n" +
                    "  <content version='1.0' id='bar'>" +
                    "     <redundancy>1</redundancy>\n" +
                    "     <documents>" +
                    "     </documents>\n" +
                    "     <group>" +
                    "       <node hostalias='mockhost' distribution-key='0' />" +
                    "     </group>" +
                    "   </content>" +
                    "\n" +
                    "</services>";
            TestDriver driver = new TestDriver();
            driver.buildModel(xml);
        });
    }

    @Test
    void testSeparateHostsFromConfigServerRequired() {
        assertThrows(IllegalArgumentException.class, () -> {
            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<services>\n" +
                    "\n" +
                    "  <admin version=\"2.0\">\n" +
                    "    <adminserver hostalias=\"mockhost\" />\n" +
                    "    <configservers>\n" +
                    "      <configserver hostalias=\"mockhost\" />" +
                    "    </configservers>" +
                    "    <cluster-controllers standalone-zookeeper=\"true\">\n" +
                    "      <cluster-controller hostalias=\"mockhost\"/>" +
                    "    </cluster-controllers>\n" +
                    "  </admin>\n" +
                    "  <content version='1.0' id='bar'>" +
                    "     <redundancy>1</redundancy>\n" +
                    "     <documents>" +
                    "     </documents>\n" +
                    "     <group>" +
                    "       <node hostalias='mockhost' distribution-key='0' />" +
                    "     </group>" +
                    "   </content>" +
                    "\n" +
                    "</services>";
            TestDriver driver = new TestDriver();
            driver.buildModel(xml);
        });
    }

    @Test
    void testStandaloneZooKeeper() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services>\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"node1\" />\n" +
                "    <cluster-controllers standalone-zookeeper=\"true\">\n" +
                "      <cluster-controller hostalias=\"node2\"/>" +
                "      <cluster-controller hostalias=\"node3\"/>" +
                "      <cluster-controller hostalias=\"node4\"/>" +
                "    </cluster-controllers>\n" +
                "  </admin>\n" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>\n" +
                "     <documents>" +
                "     </documents>\n" +
                "     <group>" +
                "       <node hostalias='node1' distribution-key='0' />" +
                "     </group>" +
                "   </content>" +
                "\n" +
                "</services>";
        String hosts = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<hosts>\n" +
                "  <host name=\"localhost\">\n" +
                "    <alias>node1</alias>\n" +
                "  </host>\n" +
                "  <host name=\"my.host1.com\">\n" +
                "    <alias>node2</alias>\n" +
                "  </host>\n" +
                "  <host name=\"my.host2.com\">\n" +
                "    <alias>node3</alias>\n" +
                "  </host>\n" +
                "  <host name=\"my.host3.com\">\n" +
                "    <alias>node4</alias>\n" +
                "  </host>\n" +
                "</hosts>";
        TestDriver driver = new TestDriver();
        TestRoot root = driver.buildModel(xml, hosts);
        assertZookeepersConfig(root);
        assertZookeeperServerConfig(root, 0);
        assertZookeeperServerConfig(root, 1);
        assertZookeeperServerConfig(root, 2);

        assertCuratorConfig(root);
    }

    private void assertZookeepersConfig(TestRoot root) {
        ZookeepersConfig.Builder builder = new ZookeepersConfig.Builder();
        root.getConfig(builder, "admin/standalone");
        ZookeepersConfig config = new ZookeepersConfig(builder);
        assertEquals(3, config.zookeeperserverlist().split(",").length);
    }

    private void assertZookeeperServerConfig(TestRoot root, int id) {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        root.getConfig(builder, "admin/standalone/cluster-controllers/" + id);
        ZookeeperServerConfig config = new ZookeeperServerConfig(builder);
        assertEquals(3, config.server().size());
        assertEquals(id, config.myid());
        Collection<Integer> serverIds = Collections2.transform(config.server(), ZookeeperServerConfig.Server::id);
        assertTrue(serverIds.contains(id));
    }

    public void assertCuratorConfig(TestRoot root) {
        CuratorConfig.Builder builder = new CuratorConfig.Builder();
        root.getConfig(builder, "admin/standalone/cluster-controllers");
        CuratorConfig config = builder.build();

        assertEquals(3, config.server().size());
        assertEquals("my.host1.com", config.server().get(0).hostname());
        assertEquals(2181, config.server().get(0).port());
        assertFalse(config.zookeeperLocalhostAffinity());
    }

    @Test
    void testUnconfigured() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services>\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"configserver\" />\n" +
                "    <logserver hostalias=\"logserver\" />\n" +
                "    <slobroks>\n" +
                "      <slobrok hostalias=\"configserver\" />\n" +
                "      <slobrok hostalias=\"logserver\" />\n" +
                "    </slobroks>\n" +
                "  </admin>\n" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>\n" +
                "     <documents>" +
                "       <document type=\"type1\" mode=\"store-only\"/>\n" +
                "     </documents>\n" +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='0' />" +
                "     </group>" +
                "    <tuning>" +
                "      <cluster-controller>\n" +
                "        <init-progress-time>34567</init-progress-time>" +
                "      </cluster-controller>" +
                "    </tuning>" +
                "   </content>" +
                "\n" +
                "</services>";

        VespaModel model = createVespaModel(xml);
        assertTrue(model.getService("admin/cluster-controllers/0").isPresent());

        assertTrue(existsHostsWithClusterControllerConfigId(model));
        assertGroupSize(model, "admin/cluster-controllers/0/components/clustercontroller-bar-configurer", 1);
        assertEquals(1, model.getAdmin().getClusterControllers().getContainers().size());

        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        model.getConfig(builder, "admin/cluster-controllers/0/components/clustercontroller-bar-configurer");

        FleetcontrollerConfig cfg = new FleetcontrollerConfig(builder);
        assertEquals(0, cfg.index());
        assertEquals(1, cfg.fleet_controller_count());
        assertEquals(34567000, cfg.init_progress_time());

        Service cc = model.getService("admin/cluster-controllers/0").get();
        assertTrue(cc instanceof ClusterControllerContainer);
        assertEquals("-Dio.netty.allocator.pageSize=4096 -Dio.netty.allocator.maxOrder=5 -Dio.netty.allocator.numHeapArenas=1 -Dio.netty.allocator.numDirectArenas=1", cc.getJvmOptions());
    }

    private boolean existsHostsWithClusterControllerConfigId(VespaModel model) {
        boolean found = false;
        for (HostResource h : model.hostSystem().getHosts()) {
            for (Service s : h.getServices()) {
                if (s.getConfigId().equals("admin/cluster-controllers/0")) {
                    found = true;
                }
            }
        }
        return found;
    }

    @Test
    void testUnconfiguredMultiple() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services>\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"configserver\" />\n" +
                "    <configservers>\n" +
                "      <configserver hostalias=\"node0\"/>" +
                "      <configserver hostalias=\"node1\"/>" +
                "      <configserver hostalias=\"node2\"/>" +
                "    </configservers>\n" +
                "    <slobroks>\n" +
                "      <slobrok hostalias=\"configserver\" />\n" +
                "      <slobrok hostalias=\"logserver\" />\n" +
                "    </slobroks>\n" +
                "  </admin>\n" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>\n" +
                "     <documents>" +
                "       <document type=\"type1\" mode=\"store-only\"/>\n" +
                "     </documents>\n" +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='0' />" +
                "     </group>" +
                "    <tuning>\n" +
                "      <cluster-controller>" +
                "        <init-progress-time>34567</init-progress-time>" +
                "      </cluster-controller>" +
                "    </tuning>" +
                "   </content>" +
                "\n" +
                "</services>";

        VespaModel model = createVespaModel(xml);

        assertEquals(3, model.getAdmin().getClusterControllers().getContainers().size());
        assertGroupSize(model, "admin/cluster-controllers/0/components/clustercontroller-bar-configurer", 1);
        assertGroupSize(model, "admin/cluster-controllers/1/components/clustercontroller-bar-configurer", 1);
        assertGroupSize(model, "admin/cluster-controllers/2/components/clustercontroller-bar-configurer", 1);
    }

    @Test
    void testUnconfiguredNoTuning() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services>\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"configserver\" />\n" +
                "    <logserver hostalias=\"logserver\" />\n" +
                "    <slobroks>\n" +
                "      <slobrok hostalias=\"configserver\" />\n" +
                "      <slobrok hostalias=\"logserver\" />\n" +
                "    </slobroks>\n" +
                "  </admin>\n" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>\n" +
                "     <documents>" +
                "       <document type=\"type1\" mode=\"store-only\"/>\n" +
                "     </documents>\n" +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='0' />" +
                "     </group>" +
                "   </content>" +
                "\n" +
                "</services>";

        VespaModel model = createVespaModel(xml);
        assertTrue(model.getService("admin/cluster-controllers/0").isPresent());

        assertTrue(existsHostsWithClusterControllerConfigId(model));
        assertGroupSize(model, "admin/cluster-controllers/0/components/clustercontroller-bar-configurer", 1);
        assertEquals(1, model.getAdmin().getClusterControllers().getContainers().size());

        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        model.getConfig(builder, "admin/cluster-controllers/0/components/clustercontroller-bar-configurer");

        FleetcontrollerConfig cfg = new FleetcontrollerConfig(builder);
        assertEquals(0, cfg.index());

        QrStartConfig.Builder qrBuilder = new QrStartConfig.Builder();
        model.getConfig(qrBuilder, "admin/cluster-controllers/0/components/clustercontroller-bar-configurer");
        QrStartConfig qrStartConfig = new QrStartConfig(qrBuilder);
        assertEquals(32, qrStartConfig.jvm().minHeapsize());
        assertEquals(128, qrStartConfig.jvm().heapsize());
        assertEquals(0, qrStartConfig.jvm().heapSizeAsPercentageOfPhysicalMemory());
        assertEquals(1, qrStartConfig.jvm().availableProcessors());
        assertFalse(qrStartConfig.jvm().verbosegc());
        assertEquals("-XX:+UseG1GC -XX:MaxTenuringThreshold=15", qrStartConfig.jvm().gcopts());
        assertEquals(512, qrStartConfig.jvm().stacksize());
        assertEquals(0, qrStartConfig.jvm().directMemorySizeCache());
        assertEquals(16, qrStartConfig.jvm().baseMaxDirectMemorySize());

        CuratorConfig.Builder curatorBuilder = new CuratorConfig.Builder();
        model.getConfig(curatorBuilder, "foo");
        CuratorConfig curatorConfig = curatorBuilder.build();
        assertEquals(120, curatorConfig.zookeeperSessionTimeoutSeconds());

        assertReindexingConfigPresent(model);
        assertReindexingConfiguredOnAdminCluster(model);
    }

    @Test
    void testUnconfiguredNoContent() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services>\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"configserver\" />\n" +
                "  </admin>\n" +
                "  <container version=\"1.0\">\n" +
                "    <nodes>" +
                "      <node hostalias=\"node1\"/>\n" +
                "    </nodes>\n" +
                "  </container>\n" +
                "</services>";

        VespaModel model = createVespaModel(xml);
        assertFalse(model.getService("admin/cluster-controllers/0").isPresent());

        assertFalse(existsHostsWithClusterControllerConfigId(model));
        assertNull(model.getAdmin().getClusterControllers());
    }

    @Test
    void testUsingOldStyle() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<services>\n" +
                "\n" +
                "  <admin version=\"2.0\">\n" +
                "    <adminserver hostalias=\"configserver\" />\n" +
                "    <logserver hostalias=\"logserver\" />\n" +
                "    <slobroks>\n" +
                "      <slobrok hostalias=\"configserver\" />\n" +
                "      <slobrok hostalias=\"logserver\" />\n" +
                "    </slobroks>\n" +
                "  </admin>\n" +
                "  <content version='1.0' id='bar'>" +
                "     <redundancy>1</redundancy>\n" +
                "     <documents>" +
                "       <document type=\"type1\" mode=\"store-only\"/>\n" +
                "     </documents>\n" +
                "     <group>" +
                "       <node hostalias='node0' distribution-key='0' />" +
                "     </group>" +
                "    <tuning>\n" +
                "      <cluster-controller>" +
                "        <init-progress-time>34567</init-progress-time>" +
                "      </cluster-controller>" +
                "    </tuning>" +
                "   </content>" +
                "\n" +
                "</services>";

        VespaModel model = createVespaModel(xml);
        assertTrue(model.getService("admin/cluster-controllers/0").isPresent());

        assertTrue(existsHostsWithClusterControllerConfigId(model));
        {
            //StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();
            //try {
            //    model.getConfig(builder, "admin/cluster-controllers/0/components/bar-configurer");
            //    fail("Invalid config id didn't fail.");
            //} catch (UnknownConfigIdException e) {
            //    assertTrue(e.getMessage().matches(".*Invalid config id.*"));
            //}
        }
    }

    private void assertGroupSize(VespaModel model, String configId, int size) {
        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();
        model.getConfig(builder, configId);
        StorDistributionConfig cfg = new StorDistributionConfig(builder);
        assertEquals(size, cfg.group().size());
    }

    private VespaModel createVespaModel(String servicesXml) throws IOException, SAXException {
        return createVespaModel(servicesXml, new DeployState.Builder());
    }

    private VespaModel createVespaModel(String servicesXml, DeployState.Builder deployStateBuilder) throws IOException, SAXException {
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withSchemas(sds)
                .build();
        // Need to create VespaModel to make deploy properties have effect
        DeployLogger logger = new DeployLoggerStub();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployStateBuilder
                .applicationPackage(applicationPackage)
                .reindexing(new DummyReindexing())
                .deployLogger(logger)
                .zone(new Zone(SystemName.cd, Environment.dev, RegionName.from("here")))
                .build());
        SimpleApplicationValidator.checkServices(new StringReader(servicesXml), new Version(7));
        return model;
    }

    private static void assertReindexingConfigPresent(VespaModel model) {
        ReindexingConfig reindexingConfig = model.getConfig(ReindexingConfig.class, "admin/cluster-controllers/0");
        assertTrue(reindexingConfig.enabled());
        assertEquals(1, reindexingConfig.clusters().size());
        String contentClusterId = "bar";
        assertEquals(Instant.EPOCH.toEpochMilli(), reindexingConfig.clusters(contentClusterId).documentTypes("type1").readyAtMillis());
        assertEquals(1.0, reindexingConfig.clusters(contentClusterId).documentTypes("type1").speed(), 0);
    }

    private static void assertReindexingConfiguredOnAdminCluster(VespaModel model) {
        ClusterControllerContainerCluster clusterControllerCluster = model.getAdmin().getClusterControllers();
        assertReindexingMaintainerConfiguredOnClusterController(clusterControllerCluster);
    }

    private static void assertReindexingMaintainerConfiguredOnClusterController(ClusterControllerContainerCluster clusterControllerCluster) {
        ClusterControllerContainer container = clusterControllerCluster.getContainers().get(0);
        Component<?, ?> reindexingMaintainer = container.getComponents().getComponents().stream()
                .filter(component -> component.getComponentId().getName().equals("reindexing-maintainer"))
                .findAny()
                .get();
        assertEquals("ai.vespa.reindexing.ReindexingMaintainer", reindexingMaintainer.getClassId().getName());
    }

    private static class DummyReindexing implements Reindexing, Reindexing.Status {
        @Override public Optional<Status> status(String cluster, String documentType) { return Optional.of(this); }
        @Override public boolean enabled() { return true; }
        @Override public Instant ready() { return Instant.EPOCH; }
        @Override public double speed() { return 1; }
    }
}
