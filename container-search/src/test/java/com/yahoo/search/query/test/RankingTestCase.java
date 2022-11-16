// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.test;

import com.yahoo.search.Query;
import com.yahoo.search.query.Sorting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Arne Bergene Fossaa
 */
public class RankingTestCase {

    private static final double delta = 0.00000001;

    /** Tests setting rank feature values */
    @Test
    void testRankFeatures() {
        // Check initializing from query
        Query query = new Query("?query=test&ranking.features.query(name)=0.1&ranking.features.fieldMatch(foo)=0.2");
        assertEquals(0.1, query.getRanking().getFeatures().getDouble("query(name)").getAsDouble(), delta);
        assertEquals(0.2, query.getRanking().getFeatures().getDouble("fieldMatch(foo)").getAsDouble(), delta);
        assertEquals("{\"query(name)\":0.1,\"fieldMatch(foo)\":0.2}", query.getRanking().getFeatures().toString());

        // Test cloning
        Query clone = query.clone();
        assertEquals(0.1, query.getRanking().getFeatures().getDouble("query(name)").getAsDouble(), delta);
        assertEquals(0.2, query.getRanking().getFeatures().getDouble("fieldMatch(foo)").getAsDouble(), delta);

        // Check programmatic setting + that the clone really has a separate object
        assertNotNull(query.getRanking().getFeatures());
        clone.properties().set("ranking.features.query(name)", "0.3");
        assertEquals(0.3, clone.getRanking().getFeatures().getDouble("query(name)").getAsDouble(), delta);
        assertEquals(0.1, query.getRanking().getFeatures().getDouble("query(name)").getAsDouble(), delta);

        // Check getting
        assertEquals(0.3, clone.properties().getDouble("ranking.features.query(name)"), 0.0000001);

        // Check map access
        assertEquals(2, query.getRanking().getFeatures().asMap().size());
        assertEquals(0.2, query.getRanking().getFeatures().asMap().get("fieldMatch(foo)"));
        query.getRanking().getFeatures().asMap().put("fieldMatch(foo)", 0.3);
        assertEquals(0.3, query.getRanking().getFeatures().getDouble("fieldMatch(foo)").getAsDouble(), delta);
    }

    // This test is order dependent. Fix this!!
    @Test
    void test_setting_rank_feature_values() {
        // Check initializing from query
        Query query = new Query("?query=test&ranking.properties.foo=bar1&ranking.properties.foo2=bar2&ranking.properties.other=10");
        assertEquals("bar1", query.getRanking().getProperties().get("foo").get(0));
        assertEquals("bar2", query.getRanking().getProperties().get("foo2").get(0));
        assertEquals("10", query.getRanking().getProperties().get("other").get(0));
        assertEquals("{\"other\":[10],\"foo\":[bar1],\"foo2\":[bar2]}", query.getRanking().getProperties().toString());

        // Test cloning
        Query clone = query.clone();
        assertNotNull(query.getRanking().getProperties());
        assertEquals("bar1", clone.getRanking().getProperties().get("foo").get(0));
        assertEquals("bar2", clone.getRanking().getProperties().get("foo2").get(0));
        assertEquals("10", clone.getRanking().getProperties().get("other").get(0));

        // Check programmatic setting mean addition
        clone.properties().set("ranking.properties.other", "12");
        assertEquals("[10, 12]", clone.getRanking().getProperties().get("other").toString());
        assertEquals("[10]",     query.getRanking().getProperties().get("other").toString());

        // Check map access
        assertEquals(3, query.getRanking().getProperties().asMap().size());
        assertEquals("bar1", query.getRanking().getProperties().asMap().get("foo").get(0));
    }

    /** Test setting sorting to null does not cause an exception. */
    @Test
    void testResetSorting() {
        Query q = new Query();
        q.getRanking().setSorting((Sorting) null);
        q.getRanking().setSorting((String) null);
    }

    @Test
    void testStructuredRankProperty() {
        Query query = new Query("?query=abc&rankproperty.distanceToPath(gps_position).path=(0,0,10,0,10,5,20,5)");
        assertEquals("(0,0,10,0,10,5,20,5)", query.getRanking().getProperties().get("distanceToPath(gps_position).path").get(0));
    }

}
