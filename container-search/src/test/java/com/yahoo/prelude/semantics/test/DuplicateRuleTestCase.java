// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class DuplicateRuleTestCase {

    private final String root = "src/test/java/com/yahoo/prelude/semantics/test/rulebases/";

    @Test
    void testDuplicateRuleBaseLoading() throws java.io.IOException, ParseException  {
        if (System.currentTimeMillis() > 0) return; // TODO: Include this test...

        try {
            new RuleImporter(new SimpleLinguistics()).importFile(root + "rules.sr");
            fail("Did not detect duplicate condition names");
        }
        catch (RuleBaseException e) {
            assertEquals("Duplicate condition 'something' in 'duplicaterules.sr'", e.getMessage());
        }
    }

}
