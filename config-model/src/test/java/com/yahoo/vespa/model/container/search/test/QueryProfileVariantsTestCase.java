// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.test;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.Level;

import static helpers.CompareConfigTestHelper.assertSerializedConfigFileEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class QueryProfileVariantsTestCase {

    private final String root = "src/test/java/com/yahoo/vespa/model/container/search/test/";

    @Test
    void testConfigCreation() throws IOException {
        QueryProfileRegistry registry = new QueryProfileXMLReader().read(root + "queryprofilevariants");
        QueryProfiles profiles = new QueryProfiles(registry, new SilentDeployLogger());
        assertSerializedConfigFileEquals(root + "query-profile-variants-configuration.cfg", profiles.getConfig().toString());
    }

    @Test
    void testConfigCreation2() throws IOException {
        QueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/vespa/model/container/search/test/queryprofilevariants2");
        QueryProfiles profiles = new QueryProfiles(registry, new SilentDeployLogger());
        assertSerializedConfigFileEquals(root + "query-profile-variants2-configuration.cfg", profiles.getConfig().toString());
    }

    @Test
    void testConfigCreationNewsBESimple() throws IOException {
        QueryProfileRegistry registry = new QueryProfileXMLReader().read(root + "newsbesimple");
        QueryProfiles profiles = new QueryProfiles(registry, new SilentDeployLogger());
        assertSerializedConfigFileEquals(root + "newsbe-query-profiles-simple.cfg", profiles.getConfig().toString());
    }

    @Test
    void testConfigCreationNewsFESimple() throws IOException {
        QueryProfileRegistry registry = new QueryProfileXMLReader().read(root + "newsfesimple");
        QueryProfiles profiles = new QueryProfiles(registry, new SilentDeployLogger());
        assertSerializedConfigFileEquals(root + "newsfe-query-profiles-simple.cfg", profiles.getConfig().toString());
    }

    @Test
    void testVariantsOfExplicitCompound() throws IOException {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile a1 = new QueryProfile("a1");
        a1.set("b", "a1.b", registry);

        QueryProfile profile = new QueryProfile("test");
        profile.setDimensions(new String[]{"x"});
        profile.set("a", a1, registry);
        profile.set("a.b", "a.b.x1", new String[]{"x1"}, registry);
        profile.set("a.b", "a.b.x2", new String[]{"x2"}, registry);

        registry.register(a1);
        registry.register(profile);

        QueryProfiles profiles = new QueryProfiles(registry, new SilentDeployLogger());
        assertSerializedConfigFileEquals(root + "variants-of-explicit-compound.cfg", profiles.getConfig().toString());
    }

    @Test
    void testVariantsOfExplicitCompoundWithVariantReference() throws IOException {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile a1 = new QueryProfile("a1");
        a1.set("b", "a1.b", registry);

        QueryProfile a2 = new QueryProfile("a2");
        a2.set("b", "a2.b", registry);

        QueryProfile profile = new QueryProfile("test");
        profile.setDimensions(new String[]{"x"});
        profile.set("a", a1, registry);
        profile.set("a", a2, new String[]{"x1"}, registry);
        profile.set("a.b", "a.b.x1", new String[]{"x1"}, registry);
        profile.set("a.b", "a.b.x2", new String[]{"x2"}, registry);

        registry.register(a1);
        registry.register(a2);
        registry.register(profile);

        QueryProfiles profiles = new QueryProfiles(registry, new SilentDeployLogger());
        assertSerializedConfigFileEquals(root + "variants-of-explicit-compound-with-reference.cfg", profiles.getConfig().toString());
    }

    /** For comparison with the above */
    @Test
    void testExplicitReferenceOverride() throws IOException {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile a1 = new QueryProfile("a1");
        a1.set("b", "a1.b", registry);

        QueryProfile profile = new QueryProfile("test");
        profile.set("a", a1, registry);
        profile.set("a.b", "a.b", registry);
        assertEquals("a.b", profile.get("a.b"));

        registry.register(a1);
        registry.register(profile);

        QueryProfiles profiles = new QueryProfiles(registry, new SilentDeployLogger());
        assertSerializedConfigFileEquals(root + "explicit-reference-override.cfg", profiles.getConfig().toString());
    }

    private static class SilentDeployLogger implements DeployLogger {

        @Override
        public void log(Level level, String message) {}

    }

}
