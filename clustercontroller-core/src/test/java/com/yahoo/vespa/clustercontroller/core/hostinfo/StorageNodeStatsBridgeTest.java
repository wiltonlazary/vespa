// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.yahoo.vespa.clustercontroller.core.ContentNodeStats;
import com.yahoo.vespa.clustercontroller.core.ContentClusterStats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author hakonhall
 */
public class StorageNodeStatsBridgeTest {

    private static String getJsonString() throws IOException {
        Path path = Paths.get("../protocols/getnodestate/host_info.json");
        byte[] encoded;
        encoded = Files.readAllBytes(path);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    @Test
    void testContentNodeStats() throws IOException {
        String data = getJsonString();
        HostInfo hostInfo = HostInfo.createHostInfo(data);

        ContentClusterStats clusterStats = StorageNodeStatsBridge.generate(hostInfo.getDistributor());
        Iterator<ContentNodeStats> itr = clusterStats.iterator();
        { // content node 0
            ContentNodeStats stats = itr.next();
            assertThat(stats.getNodeIndex(), is(0));
            assertThat(stats.getBucketSpaces().size(), is(2));
            assertBucketSpaceStats(11, 3, stats.getBucketSpaces().get("default"));
            assertBucketSpaceStats(13, 5, stats.getBucketSpaces().get("global"));
        }
        { // content node 1
            ContentNodeStats stats = itr.next();
            assertThat(stats.getNodeIndex(), is(1));
            assertThat(stats.getBucketSpaces().size(), is(1));
            assertBucketSpaceStats(0, 0, stats.getBucketSpaces().get("default"));
        }
        assertFalse(itr.hasNext());
    }

    private static void assertBucketSpaceStats(long expBucketsTotal, long expBucketsPending, ContentNodeStats.BucketSpaceStats stats) {
        assertThat(stats.getBucketsTotal(), is(expBucketsTotal));
        assertThat(stats.getBucketsPending(), is(expBucketsPending));
    }
}
