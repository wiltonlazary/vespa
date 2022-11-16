// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging.test;

import com.yahoo.container.logging.LogFormatter;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bob Travis
 */
public class LogFormatterTestCase {

    @Test
    void testIt() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        @SuppressWarnings("deprecation")
        long time = new Date(103, 7, 25, 13, 30, 35).getTime();
        String result = LogFormatter.insertDate("test%Y%m%d%H%M%S%x", time);
        assertEquals("test20030825133035Aug", result);
        result = LogFormatter.insertDate("test%s%T", time);
        assertEquals("test000" + time, result);
    }

}
