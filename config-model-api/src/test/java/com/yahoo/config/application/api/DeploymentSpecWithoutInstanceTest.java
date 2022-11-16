// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import org.junit.Test;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.application.api.Notifications.Role.author;
import static com.yahoo.config.application.api.Notifications.When.failing;
import static com.yahoo.config.application.api.Notifications.When.failingCommit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class DeploymentSpecWithoutInstanceTest {

    @Test
    public void testSpec() {
        String specXml = "<deployment version='1.0'>" +
                         "   <test/>" +
                         "</deployment>";

        StringReader r = new StringReader(specXml);
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(specXml, spec.xmlForm());
        assertEquals(1, spec.steps().size());
        assertFalse(spec.majorVersion().isPresent());
        assertTrue(spec.steps().get(0).concerns(Environment.test));
        assertTrue(spec.requireInstance("default").concerns(Environment.test, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.test, Optional.of(RegionName.from("region1")))); // test steps specify no region
        assertFalse(spec.requireInstance("default").concerns(Environment.staging, Optional.empty()));
        assertFalse(spec.requireInstance("default").concerns(Environment.prod, Optional.empty()));
        assertFalse(spec.requireInstance("default").globalServiceId().isPresent());
    }

    @Test
    public void testSpecPinningMajorVersion() {
        String specXml = "<deployment version='1.0' major-version='6'>" +
                         "   <test/>" +
                         "</deployment>";

        StringReader r = new StringReader(specXml);
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(specXml, spec.xmlForm());
        assertEquals(1, spec.steps().size());
        assertTrue(spec.majorVersion().isPresent());
        assertEquals(6, (int)spec.majorVersion().get());
    }

    @Test
    public void stagingSpec() {
        StringReader r = new StringReader(
        "<deployment version='1.0'>" +
        "   <staging/>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(1, spec.steps().size());
        assertEquals(1, spec.requireInstance("default").steps().size());
        assertTrue(spec.requireInstance("default").steps().get(0).concerns(Environment.staging));
        assertFalse(spec.requireInstance("default").concerns(Environment.test, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.staging, Optional.empty()));
        assertFalse(spec.requireInstance("default").concerns(Environment.prod, Optional.empty()));
        assertFalse(spec.requireInstance("default").globalServiceId().isPresent());
    }

    @Test
    public void minimalProductionSpec() {
        StringReader r = new StringReader(
        "<deployment version='1.0'>" +
        "   <prod>" +
        "      <region active='false'>us-east1</region>" +
        "      <region active='true'>us-west1</region>" +
        "   </prod>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(1, spec.steps().size());
        assertEquals(2, spec.requireInstance("default").steps().size());

        assertTrue(spec.requireInstance("default").steps().get(0).concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertFalse(((DeploymentSpec.DeclaredZone)spec.requireInstance("default").steps().get(0)).active());

        assertTrue(spec.requireInstance("default").steps().get(1).concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.DeclaredZone)spec.requireInstance("default").steps().get(1)).active());

        assertFalse(spec.requireInstance("default").concerns(Environment.test, Optional.empty()));
        assertFalse(spec.requireInstance("default").concerns(Environment.staging, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertTrue(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertFalse(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("no-such-region"))));
        assertFalse(spec.requireInstance("default").globalServiceId().isPresent());
        
        assertEquals(DeploymentSpec.UpgradePolicy.defaultPolicy, spec.requireInstance("default").upgradePolicy());
        assertEquals(DeploymentSpec.UpgradeRollout.separate, spec.requireInstance("default").upgradeRollout());
    }

    @Test
    public void maximalProductionSpec() {
        StringReader r = new StringReader(
        "<deployment version='1.0'>" +
        "   <test/>" +
        "   <staging/>" +
        "   <prod>" +
        "      <region active='false'>us-east1</region>" +
        "      <delay hours='3' minutes='30'/>" +
        "      <region active='true'>us-west1</region>" +
        "   </prod>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(5, spec.requireInstance("default").steps().size());
        assertEquals(4, spec.requireInstance("default").zones().size());

        assertTrue(spec.requireInstance("default").steps().get(0).concerns(Environment.test));

        assertTrue(spec.requireInstance("default").steps().get(1).concerns(Environment.staging));

        assertTrue(spec.requireInstance("default").steps().get(2).concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertFalse(((DeploymentSpec.DeclaredZone)spec.requireInstance("default").steps().get(2)).active());

        assertTrue(spec.requireInstance("default").steps().get(3) instanceof DeploymentSpec.Delay);
        assertEquals(3 * 60 * 60 + 30 * 60, spec.requireInstance("default").steps().get(3).delay().getSeconds());

        assertTrue(spec.requireInstance("default").steps().get(4).concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.DeclaredZone)spec.requireInstance("default").steps().get(4)).active());

        assertTrue(spec.requireInstance("default").concerns(Environment.test, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.test, Optional.of(RegionName.from("region1")))); // test steps specify no region
        assertTrue(spec.requireInstance("default").concerns(Environment.staging, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertTrue(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertFalse(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("no-such-region"))));
        assertFalse(spec.requireInstance("default").globalServiceId().isPresent());
    }

    @Test
    public void productionTests() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <test/>" +
                "   <staging/>" +
                "   <prod>" +
                "      <region active='false'>us-east-1</region>" +
                "      <region active='true'>us-west-1</region>" +
                "      <delay hours='1' />" +
                "      <test>us-west-1</test>" +
                "      <test>us-east-1</test>" +
                "   </prod>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        List<DeploymentSpec.Step> instanceSteps = spec.steps().get(0).steps();
        assertEquals(7, instanceSteps.size());
        assertEquals("test", instanceSteps.get(0).toString());
        assertEquals("staging", instanceSteps.get(1).toString());
        assertEquals("prod.us-east-1", instanceSteps.get(2).toString());
        assertEquals("prod.us-west-1", instanceSteps.get(3).toString());
        assertEquals("delay PT1H", instanceSteps.get(4).toString());
        assertEquals("tests for prod.us-west-1", instanceSteps.get(5).toString());
        assertEquals("tests for prod.us-east-1", instanceSteps.get(6).toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateProductionTests() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <prod>" +
                "      <region active='true'>us-east1</region>" +
                "      <test>us-east1</test>" +
                "      <test>us-east1</test>" +
                "   </prod>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void productionTestBeforeDeployment() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <prod>" +
                "      <test>us-east1</test>" +
                "      <region active='true'>us-east1</region>" +
                "   </prod>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void productionTestInParallelWithDeployment() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <prod>" +
                "      <parallel>" +
                "         <region active='true'>us-east1</region>" +
                "         <test>us-east1</test>" +
                "      </parallel>" +
                "   </prod>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void productionSpecWithGlobalServiceId() {
        StringReader r = new StringReader(
            "<deployment version='1.0'>" +
            "    <prod global-service-id='query'>" +
            "        <region active='true'>us-east-1</region>" +
            "        <region active='true'>us-west-1</region>" +
            "    </prod>" +
            "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(spec.requireInstance("default").globalServiceId(), Optional.of("query"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void globalServiceIdInTest() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "    <test global-service-id='query' />" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected=IllegalArgumentException.class)
    public void globalServiceIdInStaging() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "    <staging global-service-id='query' />" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void productionSpecWithGlobalServiceIdBeforeStaging() {
        StringReader r = new StringReader(
            "<deployment>" +
            "  <test/>" +
            "  <prod global-service-id='qrs'>" +
            "    <region active='true'>us-west-1</region>" +
            "    <region active='true'>us-central-1</region>" +
            "    <region active='true'>us-east-3</region>" +
            "  </prod>" +
            "  <staging/>" +
            "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("qrs", spec.requireInstance("default").globalServiceId().get());
    }

    @Test
    public void productionSpecWithUpgradeRollout() {
        StringReader r = new StringReader(
                "<deployment>" +
                "  <upgrade rollout='leading'/>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("leading", spec.requireInstance("default").upgradeRollout().toString());
    }

    @Test
    public void productionSpecWithUpgradePolicy() {
        StringReader r = new StringReader(
                "<deployment>" +
                "  <upgrade policy='canary'/>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("canary", spec.requireInstance("default").upgradePolicy().toString());
    }

    @Test
    public void maxDelayExceeded() {
        try {
            StringReader r = new StringReader(
                    "<deployment>" +
                    "  <upgrade policy='canary'/>" +
                    "  <prod>" +
                    "    <region active='true'>us-west-1</region>" +
                    "    <delay hours='47'/>" +
                    "    <region active='true'>us-central-1</region>" +
                    "    <delay minutes='59' seconds='61'/>" +
                    "    <region active='true'>us-east-3</region>" +
                    "  </prod>" +
                    "</deployment>"
            );
            DeploymentSpec.fromXml(r);
            fail("Expected exception due to exceeding the max total delay");
        }
        catch (IllegalArgumentException e) {
            // success
            assertEquals("The total delay specified is PT48H1S but max 48 hours is allowed", e.getMessage());
        }
    }

    @Test
    public void testEmpty() {
        assertEquals(DeploymentSpec.empty, DeploymentSpec.fromXml("<deployment version='1.0'>\n</deployment>"));
        assertEquals(0, DeploymentSpec.empty.steps().size());
        assertTrue(DeploymentSpec.empty.athenzDomain().isEmpty());
        assertTrue(DeploymentSpec.empty.athenzService().isEmpty());
        assertEquals("<deployment version='1.0'/>", DeploymentSpec.empty.xmlForm());
    }

    @Test
    public void testOnlyAthenzServiceDefined() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);

        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals(List.of(), spec.instances());
    }

    @Test
    public void productionSpecWithParallelDeployments() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                        "  <prod>    \n" +
                        "    <region active='true'>us-west-1</region>\n" +
                        "    <parallel>\n" +
                        "      <region active='true'>us-central-1</region>\n" +
                        "      <region active='true'>us-east-3</region>\n" +
                        "    </parallel>\n" +
                        "  </prod>\n" +
                        "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        DeploymentSpec.ParallelSteps parallelSteps = ((DeploymentSpec.ParallelSteps) spec.requireInstance("default").steps().get(1));
        assertEquals(2, parallelSteps.zones().size());
        assertEquals(RegionName.from("us-central-1"), parallelSteps.zones().get(0).region().get());
        assertEquals(RegionName.from("us-east-3"), parallelSteps.zones().get(1).region().get());
    }

    @Test
    public void testNestedParallelAndSteps() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>" +
                "   <staging />" +
                "   <prod>" +
                "      <parallel>" +
                "         <region active='true'>us-west-1</region>" +
                "         <steps>" +
                "            <region active='true'>us-east-3</region>" +
                "            <delay hours='2' />" +
                "            <region active='true'>eu-west-1</region>" +
                "            <delay hours='2' />" +
                "         </steps>" +
                "         <steps>" +
                "            <delay hours='3' />" +
                "            <region active='true'>aws-us-east-1a</region>" +
                "            <parallel>" +
                "               <region active='true' athenz-service='no-service'>ap-northeast-1</region>" +
                "               <region active='true'>ap-southeast-2</region>" +
                "               <test>aws-us-east-1a</test>" +
                "            </parallel>" +
                "         </steps>" +
                "         <delay hours='3' minutes='30' />" +
                "      </parallel>" +
                "      <region active='true'>us-north-7</region>" +
                "   </prod>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        List<DeploymentSpec.Step> steps = spec.steps();
        assertEquals(1, steps.size());
        assertEquals("instance 'default'", steps.get(0).toString());
        assertEquals(Duration.ofHours(4), steps.get(0).delay());

        List<DeploymentSpec.Step> instanceSteps = steps.get(0).steps();
        assertEquals(3, instanceSteps.size());
        assertEquals("staging", instanceSteps.get(0).toString());
        assertEquals("4 parallel steps", instanceSteps.get(1).toString());
        assertEquals("prod.us-north-7", instanceSteps.get(2).toString());

        List<DeploymentSpec.Step> parallelSteps = instanceSteps.get(1).steps();
        assertEquals(4, parallelSteps.size());
        assertEquals("prod.us-west-1", parallelSteps.get(0).toString());
        assertEquals("4 steps", parallelSteps.get(1).toString());
        assertEquals("3 steps", parallelSteps.get(2).toString());
        assertEquals("delay PT3H30M", parallelSteps.get(3).toString());

        List<DeploymentSpec.Step> firstSerialSteps = parallelSteps.get(1).steps();
        assertEquals(4, firstSerialSteps.size());
        assertEquals("prod.us-east-3", firstSerialSteps.get(0).toString());
        assertEquals("delay PT2H", firstSerialSteps.get(1).toString());
        assertEquals("prod.eu-west-1", firstSerialSteps.get(2).toString());
        assertEquals("delay PT2H", firstSerialSteps.get(3).toString());

        List<DeploymentSpec.Step> secondSerialSteps = parallelSteps.get(2).steps();
        assertEquals(3, secondSerialSteps.size());
        assertEquals("delay PT3H", secondSerialSteps.get(0).toString());
        assertEquals("prod.aws-us-east-1a", secondSerialSteps.get(1).toString());
        assertEquals("3 parallel steps", secondSerialSteps.get(2).toString());

        List<DeploymentSpec.Step> innerParallelSteps = secondSerialSteps.get(2).steps();
        assertEquals(3, innerParallelSteps.size());
        assertEquals("prod.ap-northeast-1", innerParallelSteps.get(0).toString());
        assertEquals("no-service", spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("ap-northeast-1")).get().value());
        assertEquals("prod.ap-southeast-2", innerParallelSteps.get(1).toString());
        assertEquals("service", spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("ap-southeast-2")).get().value());
        assertEquals("tests for prod.aws-us-east-1a", innerParallelSteps.get(2).toString());
    }

    @Test
    public void productionSpecWithDuplicateRegions() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                        "  <prod>\n" +
                        "    <region active='true'>us-west-1</region>\n" +
                        "    <parallel>\n" +
                        "      <region active='true'>us-west-1</region>\n" +
                        "      <region active='true'>us-central-1</region>\n" +
                        "      <region active='true'>us-east-3</region>\n" +
                        "    </parallel>\n" +
                        "  </prod>\n" +
                        "</deployment>"
        );
        try {
            DeploymentSpec.fromXml(r);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("prod.us-west-1 is listed twice in deployment.xml", e.getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIllegallyOrderedDeploymentSpec1() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <block-change days='sat' hours='10' time-zone='CET'/>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "  <block-change days='mon,tue' hours='15-16'/>\n" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIllegallyOrderedDeploymentSpec2() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <block-change days='sat' hours='10' time-zone='CET'/>\n" +
                "  <test/>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void deploymentSpecWithChangeBlocker() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <block-change revision='false' days='mon,tue' hours='15-16'/>\n" +
                "  <block-change days='sat' hours='10' time-zone='CET'/>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(2, spec.requireInstance("default").changeBlocker().size());
        assertTrue(spec.requireInstance("default").changeBlocker().get(0).blocksVersions());
        assertFalse(spec.requireInstance("default").changeBlocker().get(0).blocksRevisions());
        assertEquals(ZoneId.of("UTC"), spec.requireInstance("default").changeBlocker().get(0).window().zone());

        assertTrue(spec.requireInstance("default").changeBlocker().get(1).blocksVersions());
        assertTrue(spec.requireInstance("default").changeBlocker().get(1).blocksRevisions());
        assertEquals(ZoneId.of("CET"), spec.requireInstance("default").changeBlocker().get(1).window().zone());

        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T14:15:30.00Z")));
        assertFalse(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T15:15:30.00Z")));
        assertFalse(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T16:15:30.00Z")));
        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T17:15:30.00Z")));

        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-23T09:15:30.00Z")));
        assertFalse(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-23T08:15:30.00Z"))); // 10 in CET
        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-23T10:15:30.00Z")));
    }

    @Test
    public void athenz_config_is_read_from_deployment() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(spec.athenzDomain().get().value(), "domain");
        assertEquals(spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("us-west-1")).get().value(), "service");
    }

    @Test
    public void athenz_config_is_propagated_through_parallel_zones() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>" +
                "   <prod athenz-service='prod-service'>" +
                "      <region active='true'>us-central-1</region>" +
                "      <parallel>" +
                "         <region active='true'>us-west-1</region>" +
                "         <region active='true'>us-east-3</region>" +
                "      </parallel>" +
                "   </prod>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals("service", spec.athenzService().get().value());
        assertEquals("prod-service", spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("us-central-1"))
                                         .get().value());
        assertEquals("prod-service", spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("us-west-1"))
                                         .get().value());
        assertEquals("prod-service", spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("us-east-3"))
                                         .get().value());
    }

    @Test
    public void athenz_service_is_overridden_from_environment() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>\n" +
                "  <test />\n" +
                "  <staging athenz-service='staging-service' />\n" +
                "  <prod athenz-service='prod-service'>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("service", spec.athenzService().get().value());
        assertEquals(spec.athenzDomain().get().value(), "domain");
        assertEquals(spec.requireInstance("default").athenzService(Environment.test, RegionName.from("us-east-1")).get().value(), "service");
        assertEquals(spec.requireInstance("default").athenzService(Environment.staging, RegionName.from("us-north-1")).get().value(), "staging-service");
        assertEquals(spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("us-west-1")).get().value(), "prod-service");
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_fails_when_athenz_service_is_not_defined() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain'>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_fails_when_athenz_service_is_configured_but_not_athenz_domain() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <prod athenz-service='service'>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void emptySpecs() {
        assertEquals(DeploymentSpec.empty, DeploymentSpec.fromXml("<deployment>\n" +
                                                                  "</deployment>"));
        assertEquals(DeploymentSpec.empty, DeploymentSpec.fromXml("<deployment />"));
        assertEquals(DeploymentSpec.empty, DeploymentSpec.fromXml("<deployment version=\"1.0\" />"));

        assertNotEquals(DeploymentSpec.empty, DeploymentSpec.fromXml("<deployment version=\"1.0\" athenz-domain=\"domain\" athenz-service=\"service\"/>"));
        assertNotEquals(DeploymentSpec.empty, DeploymentSpec.fromXml("<deployment athenz-domain=\"domain\" athenz-service=\"service\">\n" +
                                                                     "</deployment>"));
    }

    @Test
    public void emptyNotifications() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>\n" +
                                                     "  <notifications />" +
                                                     "</deployment>");
        assertEquals(Notifications.none(), spec.requireInstance("default").notifications());
    }

    @Test
    public void someNotifications() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>\n" +
                                                     "  <notifications when=\"failing\">\n" +
                                                     "    <email role=\"author\"/>\n" +
                                                     "    <email address=\"john@dev\" when=\"failing-commit\"/>\n" +
                                                     "    <email address=\"jane@dev\"/>\n" +
                                                     "  </notifications>\n" +
                                                     "</deployment>");
        assertEquals(ImmutableSet.of(author), spec.requireInstance("default").notifications().emailRolesFor(failing));
        assertEquals(ImmutableSet.of(author), spec.requireInstance("default").notifications().emailRolesFor(failingCommit));
        assertEquals(ImmutableSet.of("john@dev", "jane@dev"), spec.requireInstance("default").notifications().emailAddressesFor(failingCommit));
        assertEquals(ImmutableSet.of("jane@dev"), spec.requireInstance("default").notifications().emailAddressesFor(failing));
    }

    @Test
    public void customTesterFlavor() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>\n" +
                                                     "  <test tester-flavor=\"d-1-4-20\" />\n" +
                                                     "  <staging />\n" +
                                                     "  <prod tester-flavor=\"d-2-8-50\">\n" +
                                                     "    <region active=\"false\">us-north-7</region>\n" +
                                                     "  </prod>\n" +
                                                     "</deployment>");
        assertEquals(Optional.of("d-1-4-20"), spec.requireInstance("default").steps().get(0).zones().get(0).testerFlavor());
        assertEquals(Optional.empty(), spec.requireInstance("default").steps().get(1).zones().get(0).testerFlavor());
        assertEquals(Optional.of("d-2-8-50"), spec.requireInstance("default").steps().get(2).zones().get(0).testerFlavor());
    }

    @Test
    public void emptyEndpoints() {
        var spec = DeploymentSpec.fromXml("<deployment><endpoints/></deployment>");
        assertEquals(Collections.emptyList(), spec.requireInstance("default").endpoints());
    }

    @Test
    public void someEndpoints() {
        var spec = DeploymentSpec.fromXml("" +
                "<deployment>" +
                "  <prod>" +
                "    <region active=\"true\">us-east</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint id=\"foo\" container-id=\"bar\">" +
                "      <region>us-east</region>" +
                "    </endpoint>" +
                "    <endpoint id=\"nalle\" container-id=\"frosk\" />" +
                "    <endpoint container-id=\"quux\" />" +
                "  </endpoints>" +
                "</deployment>");

        assertEquals(
                List.of("foo", "nalle", "default"),
                spec.requireInstance("default").endpoints().stream().map(Endpoint::endpointId).collect(Collectors.toList())
        );

        assertEquals(
                List.of("bar", "frosk", "quux"),
                spec.requireInstance("default").endpoints().stream().map(Endpoint::containerId).collect(Collectors.toList())
        );

        assertEquals(List.of(RegionName.from("us-east")), spec.requireInstance("default").endpoints().get(0).regions());
    }

    @Test
    public void endpointDefaultRegions() {
        var spec = DeploymentSpec.fromXml("" +
                "<deployment>" +
                "  <prod>" +
                "    <region active=\"true\">us-east</region>" +
                "    <region active=\"true\">us-west</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint id=\"foo\" container-id=\"bar\">" +
                "      <region>us-east</region>" +
                "    </endpoint>" +
                "    <endpoint id=\"nalle\" container-id=\"frosk\" />" +
                "    <endpoint container-id=\"quux\" />" +
                "  </endpoints>" +
                "</deployment>");

        assertEquals(Set.of("us-east"), endpointRegions("foo", spec));
        assertEquals(Set.of("us-east", "us-west"), endpointRegions("nalle", spec));
        assertEquals(Set.of("us-east", "us-west"), endpointRegions("default", spec));
    }

    @Test
    public void productionSpecWithCloudAccount() {
        StringReader r = new StringReader(
                "<deployment version='1.0' cloud-account='012345678912'>" +
                "    <prod>" +
                "        <region cloud-account='219876543210'>us-east-1</region>" +
                "        <region>us-west-1</region>" +
                "    </prod>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        DeploymentInstanceSpec instance = spec.requireInstance("default");
        assertEquals(Optional.of(CloudAccount.from("012345678912")), spec.cloudAccount());
        assertEquals(Optional.of(CloudAccount.from("219876543210")), instance.cloudAccount(Environment.prod, Optional.of(RegionName.from("us-east-1"))));
        assertEquals(Optional.of(CloudAccount.from("012345678912")), instance.cloudAccount(Environment.prod, Optional.of(RegionName.from("us-west-1"))));
        assertEquals(Optional.of(CloudAccount.from("012345678912")), instance.cloudAccount(Environment.staging, Optional.empty()));

        r = new StringReader(
                "<deployment version='1.0'>" +
                "    <prod>" +
                "        <region cloud-account='219876543210'>us-east-1</region>" +
                "        <region>us-west-1</region>" +
                "    </prod>" +
                "</deployment>"
        );
        spec = DeploymentSpec.fromXml(r);
        assertEquals(Optional.empty(), spec.cloudAccount());
        assertEquals(Optional.of(CloudAccount.from("219876543210")), spec.requireInstance("default").cloudAccount(Environment.prod, Optional.of(RegionName.from("us-east-1"))));
        assertEquals(Optional.empty(), spec.requireInstance("default").cloudAccount(Environment.prod, Optional.of(RegionName.from("us-west-1"))));
    }

    private static Set<String> endpointRegions(String endpointId, DeploymentSpec spec) {
        return spec.requireInstance("default").endpoints().stream()
                .filter(endpoint -> endpoint.endpointId().equals(endpointId))
                .flatMap(endpoint -> endpoint.regions().stream())
                .map(RegionName::value)
                .collect(Collectors.toSet());
    }

}
