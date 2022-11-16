// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.test;

import com.yahoo.component.ComponentId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class ComponentIdTestCase {

    @Test
    void testFileNameConversion() {
        assertFileNameEquals("a", "a");
        assertFileNameEquals("a-1", "a-1");
        assertFileNameEquals("a-1", "a-1.0.0");
        assertFileNameEquals("a-1.0.0.qualifier", "a-1.0.0.qualifier");
        assertFileNameEquals("a.b-1.0.0.qualifier", "a.b-1.0.0.qualifier");
        assertFileNameEquals("a@space", "a@space");
        assertFileNameEquals("a-1@space", "a-1@space");
        assertFileNameEquals("a-1@space", "a-1.0.0@space");
        assertFileNameEquals("a-1.0.0.qualifier@space", "a-1.0.0.qualifier@space");
        assertFileNameEquals("a.b-1.0.0.qualifier@space", "a.b-1.0.0.qualifier@space");
    }

    /** Takes two id file names as input */
    private void assertFileNameEquals(String expected,String initial) {
        assertEquals(expected,ComponentId.fromFileName(initial).toFileName(),"'" + initial + "' became id '" + ComponentId.fromFileName(initial) + "' which should become '" + expected + "'");
    }

    @Test
    void testCompareWithNameSpace() {
        ComponentId withNS = ComponentId.fromString("foo@ns");
        ComponentId withoutNS = ComponentId.fromString("foo"); // Should be less than withNs

        assertEquals(withNS.compareTo(withoutNS), 1);
        assertEquals(withoutNS.compareTo(withNS), -1);
    }

}
