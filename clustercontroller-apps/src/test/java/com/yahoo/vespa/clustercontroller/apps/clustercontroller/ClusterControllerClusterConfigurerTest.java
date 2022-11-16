// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ClusterControllerClusterConfigurerTest {

    @Test
    void testSimple() throws Exception {
        ClusterController controller = new ClusterController();
        StorDistributionConfig.Builder distributionConfig = new StorDistributionConfig.Builder();
        StorDistributionConfig.Group.Builder group = new StorDistributionConfig.Group.Builder();
        group.index("0").name("foo");
        StorDistributionConfig.Group.Nodes.Builder node = new StorDistributionConfig.Group.Nodes.Builder();
        node.index(0);
        group.nodes.add(node);
        distributionConfig.group.add(group);
        FleetcontrollerConfig.Builder fleetcontrollerConfig = new FleetcontrollerConfig.Builder();
        fleetcontrollerConfig
                .cluster_name("storage")
                .index(0)
                .zookeeper_server("zoo")
                .min_node_ratio_per_group(0.123)
                .enable_cluster_feed_block(true)
                .cluster_feed_block_limit("foo", 0.5)
                .cluster_feed_block_limit("bar", 0.7)
                .cluster_feed_block_noise_level(0.05);
        SlobroksConfig.Builder slobroksConfig = new SlobroksConfig.Builder();
        SlobroksConfig.Slobrok.Builder slobrok = new SlobroksConfig.Slobrok.Builder();
        slobrok.connectionspec("foo");
        slobroksConfig.slobrok.add(slobrok);
        ZookeepersConfig.Builder zookeepersConfig = new ZookeepersConfig.Builder();
        zookeepersConfig.zookeeperserverlist("foo");
        Metric metric = new Metric() {
            @Override
            public void set(String s, Number number, Context context) {
            }
            @Override
            public void add(String s, Number number, Context context) {
            }
            @Override
            public Context createContext(Map<String, ?> stringMap) {
                return null;
            }
        };
        // Used in standalone mode to get config without a cluster controller instance
        ClusterControllerClusterConfigurer configurer = new ClusterControllerClusterConfigurer(
                null,
                new StorDistributionConfig(distributionConfig),
                new FleetcontrollerConfig(fleetcontrollerConfig),
                new SlobroksConfig(slobroksConfig),
                new ZookeepersConfig(zookeepersConfig),
                metric,
                null
        );
        assertNotNull(configurer.getOptions());
        assertEquals(0.123, configurer.getOptions().minNodeRatioPerGroup(), 0.01);
        assertTrue(configurer.getOptions().clusterFeedBlockEnabled());
        assertEquals(0.5, configurer.getOptions().clusterFeedBlockLimit().get("foo"), 0.01);
        assertEquals(0.7, configurer.getOptions().clusterFeedBlockLimit().get("bar"), 0.01);
        assertEquals(0.05, configurer.getOptions().clusterFeedBlockNoiseLevel(), 0.001);

        try {
            zookeepersConfig.zookeeperserverlist("");
            new ClusterControllerClusterConfigurer(
                    controller,
                    new StorDistributionConfig(distributionConfig),
                    new FleetcontrollerConfig(fleetcontrollerConfig),
                    new SlobroksConfig(slobroksConfig),
                    new ZookeepersConfig(zookeepersConfig),
                    metric,
                    null
            );
            fail("Should not get here");
        } catch (Exception e) {
            assertEquals("zookeeper server address must be set, was ''", e.getMessage());
        }
    }

}
