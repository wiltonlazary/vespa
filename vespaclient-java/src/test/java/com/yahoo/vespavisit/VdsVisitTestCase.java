// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorResponse;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.Trace;
import com.yahoo.vespaclient.ClusterDef;
import com.yahoo.vespaclient.ClusterList;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class VdsVisitTestCase {

    private VdsVisit.ArgumentParser createMockArgumentParser() {
        Options opts = VdsVisit.createOptions();
        return new VdsVisit.ArgumentParser(opts);
    }

    @Test
    void testCommandLineShortOptions() throws Exception {
        // short options testing (for options that do not collide with each other)
        String[] args = new String[]{
                "-d", "foo.remote",
                "-s", "'id.user=1234'",
                "-f", "5678",
                "-t", "9012",
                "-l", "foodoc.bar,foodoc.baz",
                "-m", "6000",
                "-b", "5",
                "-p", "foo-progress.txt",
                "-u", "123456789",
                "-c", "kittens",
                "-r",
                "-v"
        };
        VdsVisit.ArgumentParser parser = createMockArgumentParser();
        VdsVisit.VdsVisitParameters allParams = parser.parse(args);
        assertNotNull(allParams);

        VisitorParameters params = allParams.getVisitorParameters();
        assertNotNull(params);
        assertEquals("foo.remote", params.getRemoteDataHandler());
        assertEquals("'id.user=1234'", params.getDocumentSelection());
        assertEquals(5678, params.getFromTimestamp());
        assertEquals(9012, params.getToTimestamp());
        assertEquals("foodoc.bar,foodoc.baz", params.getFieldSet());
        assertEquals(6000, params.getMaxPending());
        assertEquals(5, params.getMaxBucketsPerVisitor());
        assertEquals("foo-progress.txt", params.getResumeFileName());
        assertEquals(123456789, params.getTimeoutMs());
        assertEquals(7 * 24 * 60 * 60 * 1000, allParams.getFullTimeout());
        assertEquals("kittens", allParams.getCluster());
        assertTrue(allParams.isVerbose());
    }

    /**
     * Test the parameters that could not be used in conjunction with
     * those in the first parameter test.
     * @throws Exception
     */
    @Test
    void testCommandLineShortOptions2() throws Exception {
        // Short options testing (for options that do not collide with each other)
        String[] args = new String[]{
                "-o", "654321",
                "-i"
        };
        VdsVisit.ArgumentParser parser = createMockArgumentParser();
        VdsVisit.VdsVisitParameters allParams = parser.parse(args);
        assertNotNull(allParams);

        VisitorParameters params = allParams.getVisitorParameters();
        assertNotNull(params);
        assertEquals(654321, allParams.getFullTimeout());
        assertEquals(654321, params.getTimeoutMs());
        assertEquals(DocIdOnly.NAME, params.getFieldSet());
    }

    @Test
    void testCommandLineShortOptionsPrintIdsOnly() throws Exception {
        // Short options testing (for options that do not collide with each other)
        String[] args = new String[]{
                "-i"
        };
        VdsVisit.ArgumentParser parser = createMockArgumentParser();
        VdsVisit.VdsVisitParameters allParams = parser.parse(args);
        assertNotNull(allParams);

        VisitorParameters params = allParams.getVisitorParameters();
        assertNotNull(params);
        assertEquals(DocIdOnly.NAME, params.getFieldSet());
        assertTrue(allParams.isPrintIdsOnly());
    }

    @Test
    void testCommandLineLongOptions() throws Exception {
        // short options testing (for options that do not collide with each other)
        String[] args = new String[]{
                "--datahandler", "foo.remote",
                "--selection", "'id.user=1234'",
                "--from", "5678",
                "--to", "9012",
                "--fieldset", "foodoc.bar,foodoc.baz",
                "--maxpending", "6000",
                "--maxbuckets", "5",
                "--progress", "foo-progress.txt",
                "--maxpendingsuperbuckets", "3",
                "--buckettimeout", "123456789",
                "--cluster", "kittens",
                "--visitinconsistentbuckets",
                "--visitlibrary", "fnord",
                "--libraryparam", "asdf", "rargh",
                "--libraryparam", "pinkie", "pie",
                "--processtime", "555",
                "--maxtotalhits", "2002",
                "--tracelevel", "8",
                "--priority", "NORMAL_1",
                "--skipbucketsonfatalerrors",
                "--abortonclusterdown",
                "--visitremoves",
                "--bucketspace", "outerspace"
        };
        VdsVisit.ArgumentParser parser = createMockArgumentParser();
        VdsVisit.VdsVisitParameters allParams = parser.parse(args);
        assertNotNull(allParams);

        VisitorParameters params = allParams.getVisitorParameters();
        assertNotNull(params);

        assertEquals("foo.remote", params.getRemoteDataHandler());
        assertEquals("'id.user=1234'", params.getDocumentSelection());
        assertEquals(5678, params.getFromTimestamp());
        assertEquals(9012, params.getToTimestamp());
        assertEquals("foodoc.bar,foodoc.baz", params.getFieldSet());
        assertEquals(6000, params.getMaxPending());
        assertEquals(5, params.getMaxBucketsPerVisitor());
        assertEquals("foo-progress.txt", params.getResumeFileName());
        assertEquals(123456789, params.getTimeoutMs());
        assertEquals(7 * 24 * 60 * 60 * 1000, allParams.getFullTimeout());
        assertEquals("kittens", allParams.getCluster());

        assertTrue(params.getThrottlePolicy() instanceof StaticThrottlePolicy);
        assertEquals(3, ((StaticThrottlePolicy) params.getThrottlePolicy()).getMaxPendingCount());

        assertTrue(params.visitInconsistentBuckets());
        assertEquals("fnord", params.getVisitorLibrary());
        // TODO: FIXME? multiple library params doesn't work
        assertArrayEquals("rargh".getBytes(), params.getLibraryParameters().get("asdf"));
        //assertTrue(Arrays.equals("pie".getBytes(), params.getLibraryParameters().get("pinkie")));
        assertEquals(555, allParams.getProcessTime());
        assertEquals(2002, params.getMaxTotalHits());
        assertEquals(8, params.getTraceLevel());
        assertEquals(DocumentProtocol.Priority.NORMAL_1, params.getPriority());
        assertTrue(allParams.getAbortOnClusterDown());
        assertTrue(params.visitRemoves());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        VdsVisit.verbosePrintParameters(allParams, printStream);
        printStream.flush();
        String nl = System.getProperty("line.separator"); // the joys of running tests on windows
        assertEquals(
                "Time out visitor after 123456789 ms." + nl +
                        "Visiting documents matching: 'id.user=1234'" + nl +
                        "Visiting bucket space: outerspace" + nl +
                        "Visiting in the inclusive timestamp range 5678 - 9012." + nl +
                        "Visiting field set foodoc.bar,foodoc.baz." + nl +
                        "Visiting inconsistent buckets." + nl +
                        "Including remove entries." + nl +
                        "Tracking progress in file: foo-progress.txt" + nl +
                        "Let visitor have maximum 6000 replies pending on data handlers per storage node visitor." + nl +
                        "Visit maximum 5 buckets per visitor." + nl +
                        "Sending data to data handler at: foo.remote" + nl +
                        "Using visitor library 'fnord'." + nl +
                        "Adding the following library specific parameters:" + nl +
                        "  asdf = rargh" + nl +
                        "Visitor priority NORMAL_1" + nl +
                        "Skip visiting super buckets with fatal errors." + nl,
                outputStream.toString("utf-8"));
    }

    private static String[] emptyArgList() { return new String[]{}; }

    @Test
    void visitor_priority_is_low1_by_default() throws Exception {
        VdsVisit.VdsVisitParameters allParams = createMockArgumentParser().parse(emptyArgList());

        VisitorParameters params = allParams.getVisitorParameters();
        assertEquals(DocumentProtocol.Priority.LOW_1, params.getPriority());
    }

    @Test
    void testBadPriorityValue() throws Exception {
        String[] args = new String[]{
                "--priority", "super_hyper_important"
        };
        VdsVisit.ArgumentParser parser = createMockArgumentParser();
        try {
            parser.parse(args);
            fail("no exception thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown priority name"));
        }
    }

    @Test
    void testCommandLineShortOptionsInvokeHelp() throws Exception {
        // Short options testing (for options that do not collide with each other)
        String[] args = new String[]{
                "-h"
        };
        VdsVisit.ArgumentParser parser = createMockArgumentParser();
        VdsVisit.VdsVisitParameters allParams = parser.parse(args);
        assertNull(allParams);
    }

    @Test
    void testAutoSelectClusterRoute() throws Exception {
        List<ClusterDef> clusterDefs = new ArrayList<>();
        clusterDefs.add(new ClusterDef("storage"));
        ClusterList clusterList = new ClusterList(clusterDefs);

        String route = VdsVisit.resolveClusterRoute(clusterList, null);
        assertEquals("[Content:cluster=storage]", route);
    }

    @Test
    void testBadClusterName() throws Exception {
        List<ClusterDef> clusterDefs = new ArrayList<>();
        clusterDefs.add(new ClusterDef("storage"));
        ClusterList clusterList = new ClusterList(clusterDefs);
        try {
            VdsVisit.resolveClusterRoute(clusterList, "borkbork");
        } catch (IllegalArgumentException e) {
            assertEquals("Your vespa cluster contains the content clusters 'storage', not 'borkbork'. " +
                    "Please select a valid vespa cluster.",
                    e.getMessage());
        }
    }

    @Test
    void testRequireClusterOptionIfMultipleClusters() {
        List<ClusterDef> clusterDefs = new ArrayList<>();
        clusterDefs.add(new ClusterDef("storage"));
        clusterDefs.add(new ClusterDef("storage2"));
        ClusterList clusterList = new ClusterList(clusterDefs);
        try {
            VdsVisit.resolveClusterRoute(clusterList, null);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Please use the -c option to select one of them"));
        }
    }

    @Test
    void testExplicitClusterOptionWithMultipleClusters() {
        List<ClusterDef> clusterDefs = new ArrayList<>();
        clusterDefs.add(new ClusterDef("storage"));
        clusterDefs.add(new ClusterDef("storage2"));
        ClusterList clusterList = new ClusterList(clusterDefs);

        String route = VdsVisit.resolveClusterRoute(clusterList, "storage2");
        assertEquals("[Content:cluster=storage2]", route);
    }

    @Test
    void testFailIfNoContentClustersAvailable() {
        List<ClusterDef> clusterDefs = new ArrayList<>();
        ClusterList clusterList = new ClusterList(clusterDefs);
        try {
            VdsVisit.resolveClusterRoute(clusterList, null);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Your Vespa cluster does not have any content clusters"));
        }
    }

    @Test
    void testStatistics() throws Exception {
        String[] args = new String[]{
                "--statistics", "foo"
        };
        VdsVisit.ArgumentParser parser = createMockArgumentParser();
        VdsVisit.VdsVisitParameters allParams = parser.parse(args);
        assertNotNull(allParams);

        VisitorParameters params = allParams.getVisitorParameters();
        assertNotNull(params);
        assertEquals("foo", allParams.getStatisticsParts());
        assertEquals(DocIdOnly.NAME, params.getFieldSet());
        assertEquals("CountVisitor", params.getVisitorLibrary());
    }

    // TODO: use DummyVisitorSession instead?
    private static class MockVisitorSession implements VisitorSession {
        private VisitorParameters params;

        public MockVisitorSession(VisitorParameters params) {
            this.params = params;
            params.getLocalDataHandler().setSession(this);
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public ProgressToken getProgress() {
            return null;
        }

        @Override
        public Trace getTrace() {
            return null;
        }

        @Override
        public boolean waitUntilDone(long l) throws InterruptedException {
            params.getControlHandler().onDone(VisitorControlHandler.CompletionCode.SUCCESS, "woo!");
            // Return immediately
            return true;
        }

        @Override
        public void ack(AckToken ackToken) {
        }

        @Override
        public void abort() {
        }

        @Override
        public VisitorResponse getNext() {
            return null;
        }

        @Override
        public VisitorResponse getNext(int i) throws InterruptedException {
            return null;
        }

        @Override
        public void destroy() {
        }
    }

    private static class MockVisitorSessionAccessor implements VdsVisit.VisitorSessionAccessor {
        boolean shutdown = false;
        @Override
        public VisitorSession createVisitorSession(VisitorParameters params) throws ParseException {
            return new MockVisitorSession(params);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        public boolean isShutdown() {
            return shutdown;
        }
    }

    private static class MockVisitorSessionAccessorFactory implements VdsVisit.VisitorSessionAccessorFactory {

        private MockVisitorSessionAccessor lastCreatedAccessor = null;

        @Override
        public VdsVisit.VisitorSessionAccessor createVisitorSessionAccessor() {
            lastCreatedAccessor = new MockVisitorSessionAccessor();
            return lastCreatedAccessor;
        }

        public MockVisitorSessionAccessor getLastCreatedAccessor() {
            return lastCreatedAccessor;
        }
    }

    private static class MockShutdownHookRegistrar implements VdsVisit.ShutdownHookRegistrar {
        Thread cleanUpThread;

        @Override
        public void registerShutdownHook(Thread thread) {
            cleanUpThread = thread;
        }

        public Thread getCleanUpThread() {
            return cleanUpThread;
        }
    }

    @Test
    void testVdsVisitRunLogic() {
        MockVisitorSessionAccessorFactory accessorFactory = new MockVisitorSessionAccessorFactory();
        MockShutdownHookRegistrar shutdownHookRegistrar = new MockShutdownHookRegistrar();
        VdsVisit vdsVisit = new VdsVisit(accessorFactory, shutdownHookRegistrar);

        VdsVisit.VdsVisitParameters params = new VdsVisit.VdsVisitParameters();
        VisitorParameters visitorParameters = new VisitorParameters("");
        params.setVisitorParameters(visitorParameters);

        visitorParameters.setResumeFileName("src/test/files/progress.txt");
        vdsVisit.setVdsVisitParameters(params);

        int code = vdsVisit.doRun();
        assertEquals(0, code);

        assertNotNull(shutdownHookRegistrar.getCleanUpThread());
        shutdownHookRegistrar.getCleanUpThread().run();

        assertNotNull(accessorFactory.getLastCreatedAccessor());
        assertTrue(accessorFactory.getLastCreatedAccessor().isShutdown());

        // Ensure progress token stuff was read from file
        ProgressToken progress = visitorParameters.getResumeToken();
        assertNotNull(progress);
        assertEquals(14, progress.getDistributionBitCount());
        assertEquals(3, progress.getPendingBucketCount());
    }

    @Test
    void testVdsVisitRunLogicProgressFileNotYetCreated() {
        MockVisitorSessionAccessorFactory accessorFactory = new MockVisitorSessionAccessorFactory();
        MockShutdownHookRegistrar shutdownHookRegistrar = new MockShutdownHookRegistrar();
        VdsVisit vdsVisit = new VdsVisit(accessorFactory, shutdownHookRegistrar);

        VdsVisit.VdsVisitParameters params = new VdsVisit.VdsVisitParameters();
        VisitorParameters visitorParameters = new VisitorParameters("");
        params.setVisitorParameters(visitorParameters);

        visitorParameters.setResumeFileName("src/test/files/progress-not-existing.txt");
        vdsVisit.setVdsVisitParameters(params);

        // Should not fail with file not found
        int code = vdsVisit.doRun();
        assertEquals(0, code);

        assertNotNull(shutdownHookRegistrar.getCleanUpThread());
        shutdownHookRegistrar.getCleanUpThread().run();

        assertNotNull(accessorFactory.getLastCreatedAccessor());
        assertTrue(accessorFactory.getLastCreatedAccessor().isShutdown());
    }
}
