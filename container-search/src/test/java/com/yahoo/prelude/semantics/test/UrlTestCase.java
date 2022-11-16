// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

/**
 * Tests working with url indexes
 *
 * @author bratseth
 */
public class UrlTestCase extends RuleBaseAbstractTestCase {

    public UrlTestCase() {
        super("url.sr");
    }

    @Test
    void testFromDefaultToUrlIndex() {
        assertSemantics("fromurl:\"youtube com\"", "youtube.com");
    }


}
