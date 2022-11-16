// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class ContentClusterRemovalValidatorTest {

    private final ValidationTester tester = new ValidationTester(5);

    @Test
    void testContentRemovalValidation() {
        VespaModel previous = tester.deploy(null, getServices("contentClusterId"), Environment.prod, null).getFirst();
        try {
            tester.deploy(previous, getServices("newContentClusterId"), Environment.prod, null);
            fail("Expected exception due to content cluster id change");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("content-cluster-removal: Content cluster 'contentClusterId' is removed. This will cause loss of all data in this cluster. " +
                    ValidationOverrides.toAllowMessage(ValidationId.contentClusterRemoval),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void testOverridingContentRemovalValidation() {
        VespaModel previous = tester.deploy(null, getServices("contentClusterId"), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices("newContentClusterId"), Environment.prod, removalOverride); // Allowed due to override
    }

    private static String getServices(String contentClusterId) {
        return "<services version='1.0'>" +
               "  <content id='" + contentClusterId + "' version='1.0'>" +
               "    <redundancy>1</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='1'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String removalOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>content-cluster-removal</allow>\n" +
            "</validation-overrides>\n";

}
