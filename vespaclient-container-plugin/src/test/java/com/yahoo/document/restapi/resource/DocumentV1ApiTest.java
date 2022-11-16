// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.document.BucketId;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.restapi.DocumentOperationExecutorConfig;
import com.yahoo.document.restapi.resource.DocumentV1ApiHandler.StorageCluster;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.DocumentIdResponse;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.ResponseHandler;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.SubscriptionParameters;
import com.yahoo.documentapi.SubscriptionSession;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.UpdateResponse;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorDestinationSession;
import com.yahoo.documentapi.VisitorIterator;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorResponse;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.schema.derived.Deriver;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.tensor.Tensor;
import com.yahoo.test.ManualClock;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.yahoo.documentapi.DocumentOperationParameters.parameters;
import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;
import static com.yahoo.jdisc.http.HttpRequest.Method.PATCH;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author jonmv
 */
public class DocumentV1ApiTest {

    final AllClustersBucketSpacesConfig bucketConfig = new AllClustersBucketSpacesConfig.Builder()
            .cluster("content",
                     new AllClustersBucketSpacesConfig.Cluster.Builder()
                             .documentType("music",
                                           new AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder()
                                                   .bucketSpace(FixedBucketSpaces.defaultSpace())))
            .build();
    final ClusterListConfig clusterConfig = new ClusterListConfig.Builder()
            .storage(new ClusterListConfig.Storage.Builder().configid("config-id")
                                                            .name("content"))
            .build();
    final DocumentOperationExecutorConfig executorConfig = new DocumentOperationExecutorConfig.Builder()
            .maxThrottled(2)
            .resendDelayMillis(1 << 30)
            .build();
    final DocumentmanagerConfig docConfig = Deriver.getDocumentManagerConfig("src/test/cfg/music.sd")
                                                   .ignoreundefinedfields(true).build();
    final DocumentTypeManager manager = new DocumentTypeManager(docConfig);
    final Document doc1 = new Document(manager.getDocumentType("music"), "id:space:music::one");
    final Document doc2 = new Document(manager.getDocumentType("music"), "id:space:music:n=1:two");
    final Document doc3 = new Document(manager.getDocumentType("music"), "id:space:music:g=a:three");
    {
        doc1.setFieldValue("artist", "Tom Waits");
        doc1.setFieldValue("embedding", new TensorFieldValue(Tensor.from("tensor(x[3]):[1,2,3]")));
        doc2.setFieldValue("artist", "Asa-Chan & Jun-Ray");
        doc2.setFieldValue("embedding", new TensorFieldValue(Tensor.from("tensor(x[3]):[4,5,6]")));
    }

    final Map<String, StorageCluster> clusters = Map.of("content", new StorageCluster("content",
                                                                                      Map.of("music", "default")));
    ManualClock clock;
    MockDocumentAccess access;
    MockMetric metric;
    MetricReceiver metrics;
    DocumentV1ApiHandler handler;

    @Before
    public void setUp() {
        clock = new ManualClock();
        access = new MockDocumentAccess(docConfig);
        metric = new MockMetric();
        metrics = new MetricReceiver.MockReceiver();
        handler = new DocumentV1ApiHandler(clock, Duration.ofMillis(1), metric, metrics, access, docConfig,
                                           executorConfig, clusterConfig, bucketConfig);
    }

    @After
    public void tearDown() {
        handler.destroy();
    }

    @Test
    public void testResolveCluster() {
        assertEquals("content",
                     DocumentV1ApiHandler.resolveCluster(Optional.empty(), clusters).name());
        assertEquals("content",
                     DocumentV1ApiHandler.resolveCluster(Optional.of("content"), clusters).name());
        try {
            DocumentV1ApiHandler.resolveCluster(Optional.empty(), Map.of());
            fail("Should fail without any clusters");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Your Vespa deployment has no content clusters, so the document API is not enabled", e.getMessage());
        }
        try {
            DocumentV1ApiHandler.resolveCluster(Optional.of("blargh"), clusters);
            fail("Should not find this cluster");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Your Vespa deployment has no content cluster 'blargh', only 'content'", e.getMessage());
        }
        try {
            Map<String, StorageCluster> twoClusters = new TreeMap<>();
            twoClusters.put("one", new StorageCluster("one", Map.of()));
            twoClusters.put("two", new StorageCluster("two", Map.of()));
            DocumentV1ApiHandler.resolveCluster(Optional.empty(), twoClusters);
            fail("More than one cluster and no document type should fail");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Please specify one of the content clusters in your Vespa deployment: 'one', 'two'", e.getMessage());
        }
        StorageCluster cluster = DocumentV1ApiHandler.resolveCluster(Optional.of("content"), clusters);
        assertEquals(FixedBucketSpaces.defaultSpace(),
                     DocumentV1ApiHandler.resolveBucket(cluster, Optional.of("music"), List.of(), Optional.empty()));
        assertEquals(FixedBucketSpaces.globalSpace(),
                     DocumentV1ApiHandler.resolveBucket(cluster, Optional.empty(), List.of(FixedBucketSpaces.globalSpace()), Optional.of("global")));
    }

    @Test
    public void testResponses() {
        RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler);
        List<AckToken> tokens = List.of(new AckToken(null), new AckToken(null), new AckToken(null));
        // GET at non-existent path returns 404 with available paths
        var response = driver.sendRequest("http://localhost/document/v1/not-found");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/not-found\"," +
                       "  \"message\": \"Nothing at '/document/v1/not-found'. Available paths are:\\n" +
                       "/document/v1/\\n" +
                       "/document/v1/{namespace}/{documentType}/docid/\\n" +
                       "/document/v1/{namespace}/{documentType}/group/{group}/\\n" +
                       "/document/v1/{namespace}/{documentType}/number/{number}/\\n" +
                       "/document/v1/{namespace}/{documentType}/docid/{*}\\n" +
                       "/document/v1/{namespace}/{documentType}/group/{group}/{*}\\n" +
                       "/document/v1/{namespace}/{documentType}/number/{number}/{*}\"" +
                       "}", response.readAll());
        assertEquals("application/json; charset=UTF-8", response.getResponse().headers().getFirst("Content-Type"));
        assertEquals(404, response.getStatus());

        // GET at root is a visit. Numeric parameters have an upper bound.
        access.expect(tokens);
        Trace visitorTrace = new Trace(9);
        visitorTrace.trace(7, "Tracy Chapman", false);
        visitorTrace.getRoot().addChild(new TraceNode().setStrict(false)
                                                .addChild("Fast Car")
                                                .addChild("Baby Can I Hold You"));
        access.visitorTrace = visitorTrace;
        access.expect(parameters -> {
            assertEquals("content", parameters.getRoute().toString());
            assertEquals("default", parameters.getBucketSpace());
            assertEquals(1024, parameters.getMaxTotalHits());
            assertEquals(100, ((StaticThrottlePolicy) parameters.getThrottlePolicy()).getMaxPendingCount());
            assertEquals("[id]", parameters.getFieldSet());
            assertEquals("(all the things)", parameters.getDocumentSelection());
            assertEquals(6000, parameters.getSessionTimeoutMs());
            assertEquals(9, parameters.getTraceLevel());
            // Put some documents in the response
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc1)), tokens.get(0));
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc2)), tokens.get(1));
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc3)), tokens.get(2));
            VisitorStatistics statistics = new VisitorStatistics();
            statistics.setBucketsVisited(1);
            statistics.setDocumentsVisited(3);
            parameters.getControlHandler().onVisitorStatistics(statistics);
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.TIMEOUT, "timeout is OK");
        });
        response = driver.sendRequest("http://localhost/document/v1?cluster=content&bucketSpace=default&wantedDocumentCount=1025&concurrency=123" +
                                      "&selection=all%20the%20things&fieldSet=[id]&timeout=6&tracelevel=9");
        assertSameJson("""
                       {
                         "pathId": "/document/v1",
                         "documents": [
                           {
                             "id": "id:space:music::one",
                             "fields": {
                               "artist": "Tom Waits",\s
                               "embedding": { "type": "tensor(x[3])", "values": [1.0,2.0,3.0] }\s
                             }
                           },
                           {
                             "id": "id:space:music:n=1:two",
                             "fields": {
                               "artist": "Asa-Chan & Jun-Ray",\s
                               "embedding": { "type": "tensor(x[3])", "values": [4.0,5.0,6.0] }\s
                             }
                           },
                           {
                            "id": "id:space:music:g=a:three",
                            "fields": {}
                           }
                         ],
                         "documentCount": 3,
                         "trace": [
                           { "message": "Tracy Chapman" },
                           {
                             "fork": [
                               { "message": "Fast Car" },
                               { "message": "Baby Can I Hold You" }
                             ]
                           }
                         ]
                       }""", response.readAll());
        assertEquals(200, response.getStatus());
        access.visitorTrace = null;

        // GET at root is a visit. Streaming mode can be specified with &stream=true
        access.expect(tokens);
        access.expect(parameters -> {
            assertEquals("content", parameters.getRoute().toString());
            assertEquals("default", parameters.getBucketSpace());
            assertEquals(1025, parameters.getMaxTotalHits()); // Not bounded likewise for streamed responses.
            assertEquals(1, ((StaticThrottlePolicy) parameters.getThrottlePolicy()).getMaxPendingCount());
            assertEquals("[id]", parameters.getFieldSet());
            assertEquals("(all the things)", parameters.getDocumentSelection());
            assertEquals(6000, parameters.getTimeoutMs());
            assertEquals(4, parameters.getSlices());
            assertEquals(1, parameters.getSliceId());
            // Put some documents in the response
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc1)), tokens.get(0));
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc2)), tokens.get(1));
            VisitorStatistics statistics = new VisitorStatistics();
            statistics.setBucketsVisited(1);
            statistics.setDocumentsVisited(2);
            parameters.getControlHandler().onVisitorStatistics(statistics);
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.TIMEOUT, "timeout is OK");
            // Extra documents are ignored.
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc3)), tokens.get(2));
        });
        response = driver.sendRequest("http://localhost/document/v1?cluster=content&bucketSpace=default&wantedDocumentCount=1025&concurrency=123" +
                                      "&selection=all%20the%20things&fieldSet=[id]&timeout=6&stream=true&slices=4&sliceId=1");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1\"," +
                       "  \"documents\": [" +
                       "    {" +
                       "      \"id\": \"id:space:music::one\"," +
                       "      \"fields\": {" +
                       "        \"artist\": \"Tom Waits\"," +
                       "        \"embedding\": { \"type\": \"tensor(x[3])\", \"values\": [1.0,2.0,3.0] } " +
                       "      }" +
                       "    }," +
                       "    {" +
                       "      \"id\": \"id:space:music:n=1:two\"," +
                       "      \"fields\": {" +
                       "        \"artist\": \"Asa-Chan & Jun-Ray\"," +
                       "        \"embedding\": { \"type\": \"tensor(x[3])\", \"values\": [4.0,5.0,6.0] } " +
                       "      }" +
                       "    }" +
                       "  ]," +
                       "  \"documentCount\": 2" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // GET with namespace and document type is a restricted visit.
        ProgressToken progress = new ProgressToken();
        VisitorIterator.createFromExplicitBucketSet(Set.of(new BucketId(1), new BucketId(2)), 8, progress)
                       .update(new BucketId(1), new BucketId(1));
        access.expect(parameters -> {
            assertEquals("(music) and (id.namespace=='space')", parameters.getDocumentSelection());
            assertEquals(progress.serializeToString(), parameters.getResumeToken().serializeToString());
            throw new IllegalArgumentException("parse failure");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid?continuation=" + progress.serializeToString());
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid\"," +
                       "  \"message\": \"parse failure\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // GET when a streamed visit returns status code 200 also when errors occur.
        access.expect(parameters -> {
            assertEquals("(music) and (id.namespace=='space')", parameters.getDocumentSelection());
            parameters.getControlHandler().onProgress(progress);
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.FAILURE, "failure?");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid?stream=true");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid\"," +
                       "  \"documents\": []," +
                       //"  \"continuation\": \"" + progress.serializeToString() + "\"," +
                       "  \"message\": \"failure?\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());
        assertNull(response.getResponse().headers().get("X-Vespa-Ignored-Fields"));

        // POST with namespace and document type is a restricted visit with a required destination cluster ("destinationCluster")
        access.expect(parameters -> {
            fail("Not supposed to run");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid", POST);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid\"," +
                       "  \"message\": \"Must specify 'destinationCluster' at '/document/v1/space/music/docid'\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // POST with namespace and document type is a restricted visit with a required destination cluster ("destinationCluster")
        access.expect(parameters -> {
            assertEquals("[Content:cluster=content]", parameters.getRemoteDataHandler());
            assertEquals("[document]", parameters.fieldSet());
            assertEquals(60_000L, parameters.getSessionTimeoutMs());
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.SUCCESS, "We made it!");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid?destinationCluster=content&selection=true&cluster=content&timeout=60", POST);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // PUT with namespace and document type is a restricted visit with a required partial update to apply to visited documents.
        access.expect(tokens.subList(2, 3));
        access.expect(parameters -> {
            assertEquals("(true) and (music) and (id.namespace=='space')", parameters.getDocumentSelection());
            assertEquals("[id]", parameters.fieldSet());
            assertEquals(10_000, parameters.getSessionTimeoutMs());
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc3)), tokens.get(2));
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.TIMEOUT, "Won't care");
        });
        access.session.expect((update, parameters) -> {
            DocumentUpdate expectedUpdate = new DocumentUpdate(doc3.getDataType(), doc3.getId());
            expectedUpdate.addFieldUpdate(FieldUpdate.createAssign(doc3.getField("artist"), new StringFieldValue("Lisa Ekdahl")));
            expectedUpdate.setCondition(new TestAndSetCondition("true"));
            assertEquals(expectedUpdate, update);
            parameters.responseHandler().get().handleResponse(new UpdateResponse(0, false));
            assertEquals(parameters().withRoute("content"), parameters);
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid?selection=true&cluster=content&timeChunk=10", PUT,
                                      "{" +
                                      "  \"fields\": {" +
                                      "    \"artist\": { \"assign\": \"Lisa Ekdahl\" }, " +
                                      "    \"nonexisting\": { \"assign\": \"Ignored\" }" +
                                      "  }" +
                                      "}");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());
        assertEquals("true", response.getResponse().headers().get("X-Vespa-Ignored-Fields").get(0).toString());

        // PUT with namespace, document type and group is also a restricted visit which requires a cluster.
        access.expect(parameters -> {
            fail("Not supposed to run");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/group/troupe?selection=false", PUT);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/group/troupe\"," +
                       "  \"message\": \"Must specify 'cluster' at '/document/v1/space/music/group/troupe'\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // PUT with namespace, document type and group is also a restricted visit which requires a selection.
        access.expect(parameters -> {
            fail("Not supposed to run");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/group/troupe?cluster=content", PUT);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/group/troupe\"," +
                       "  \"message\": \"Must specify 'selection' at '/document/v1/space/music/group/troupe'\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // DELETE with namespace and document type is a restricted visit which deletes visited documents.
        // When visiting fails fatally, a 502 BAD GATEWAY is returned.
        access.expect(tokens.subList(0, 1));
        access.expect(parameters -> {
            assertEquals("(false) and (music) and (id.namespace=='space')", parameters.getDocumentSelection());
            assertEquals("[id]", parameters.fieldSet());
            assertEquals(60_000, parameters.getSessionTimeoutMs());
            parameters.getLocalDataHandler().onMessage(new PutDocumentMessage(new DocumentPut(doc2)), tokens.get(0));
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.ABORTED, "Huzzah?");
        });
        access.session.expect((remove, parameters) -> {
            DocumentRemove expectedRemove = new DocumentRemove(doc2.getId());
            expectedRemove.setCondition(new TestAndSetCondition("false"));
            assertEquals(expectedRemove, remove);
            assertEquals(parameters().withRoute("content"), parameters);
            parameters.responseHandler().get().handleResponse(new DocumentIdResponse(0, doc2.getId(), "boom", Response.Outcome.ERROR));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid?selection=false&cluster=content", DELETE);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid\"," +
                       "  \"message\": \"boom\"" +
                       "}", response.readAll());
        assertEquals(502, response.getStatus());

        // DELETE at the root is also a deletion visit. These also require a selection.
        access.expect(parameters -> {
            fail("Not supposed to run");
        });
        response = driver.sendRequest("http://localhost/document/v1/", DELETE);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/\"," +
                       "  \"message\": \"Must specify 'selection' at '/document/v1/'\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // DELETE at the root is also a deletion visit. These also require a cluster.
        access.expect(parameters -> {
            fail("Not supposed to run");
        });
        response = driver.sendRequest("http://localhost/document/v1/?selection=true", DELETE);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/\"," +
                       "  \"message\": \"Must specify 'cluster' at '/document/v1/'\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // GET with namespace, document type and group is a restricted visit.
        access.expect(parameters -> {
            assertEquals("(music) and (id.namespace=='space') and (id.group=='best\\'')", parameters.getDocumentSelection());
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.FAILURE, "error");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/group/best%27");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/group/best%27\"," +
                       "  \"documents\": []," +
                       "  \"message\": \"error\"" +
                       "}", response.readAll());
        assertEquals(502, response.getStatus());

        // GET with namespace, document type and number is a restricted visit.
        access.expect(parameters -> {
            assertEquals("(music) and (id.namespace=='space') and (id.user==123)", parameters.getDocumentSelection());
            parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.ABORTED, "aborted");
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/123");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/123\"," +
                       "  \"documents\": []" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // GET with full document ID is a document get operation which returns 404 when no document is found
        access.session.expect((id, parameters) -> {
            assertEquals(doc1.getId(), id);
            assertEquals(parameters().withRoute("content").withFieldSet("go"), parameters);
            parameters.responseHandler().get().handleResponse(new DocumentResponse(0, null));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid/one?cluster=content&fieldSet=go&timeout=123");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid/one\"," +
                       "  \"id\": \"id:space:music::one\"" +
                       "}", response.readAll());
        assertEquals(404, response.getStatus());

        // GET with full document ID is a document get operation.
        access.session.expect((id, parameters) -> {
            assertEquals(doc1.getId(), id);
            assertEquals(parameters().withFieldSet("music:[document]"), parameters);
            parameters.responseHandler().get().handleResponse(new DocumentResponse(0, doc1));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid/one?format.tensors=long");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid/one\"," +
                       "  \"id\": \"id:space:music::one\"," +
                       "  \"fields\": {" +
                       "    \"artist\": \"Tom Waits\"," +
                       "    \"embedding\": { \"cells\": [{\"address\":{\"x\":\"0\"},\"value\":1.0},{\"address\":{\"x\":\"1\"},\"value\": 2.0},{\"address\":{\"x\":\"2\"},\"value\": 3.0}]}" +
                       "  }" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // GET with not encoded / in user specified part of document id is perfectly OK ... щ(ಥДಥщ)
        access.session.expect((id, parameters) -> {
            assertEquals(new DocumentId("id:space:music::one/two/three"), id);
            assertEquals(parameters().withFieldSet("music:[document]"), parameters);
            parameters.responseHandler().get().handleResponse(new DocumentResponse(0));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid/one/two/three");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid/one/two/three\"," +
                       "  \"id\": \"id:space:music::one/two/three\"" +
                       "}", response.readAll());
        assertEquals(404, response.getStatus());

        // GET with dryRun=true is an error
        access.session.expect((__, ___) -> {
            fail("Should not cause an actual feed operation");
            return null;
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?dryRun=true");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"message\": \"May not specify 'dryRun' at '/document/v1/space/music/number/1/two'\"\n" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // POST with dryRun=true returns an immediate OK response
        access.session.expect((__, ___) -> {
            fail("Should not cause an actual feed operation");
            return null;
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?dryRun=true", POST,
                                      "NOT JSON, NOT PARSED");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // PUT with dryRun=true returns an immediate OK response
        access.session.expect((__, ___) -> {
            fail("Should not cause an actual feed operation");
            return null;
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?dryRun=true", PUT,
                                      "NOT JSON, NOT PARSED");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // DELETE with dryRun=true returns an immediate OK response
        access.session.expect((__, ___) -> {
            fail("Should not cause an actual feed operation");
            return null;
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?dryRun=true", DELETE,
                                      "NOT JSON, NOT PARSED");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // PUT with a document update payload is a document update operation.
        access.session.expect((update, parameters) -> {
            DocumentUpdate expectedUpdate = new DocumentUpdate(doc3.getDataType(), doc3.getId());
            expectedUpdate.addFieldUpdate(FieldUpdate.createAssign(doc3.getField("artist"), new StringFieldValue("Lisa Ekdahl")));
            expectedUpdate.setCreateIfNonExistent(true);
            assertEquals(expectedUpdate, update);
            assertEquals(parameters(), parameters);
            parameters.responseHandler().get().handleResponse(new UpdateResponse(0, true));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/group/a/three?create=true&timeout=1e1s&dryRun=false", PUT,
                                      "{" +
                                      "  \"fields\": {" +
                                      "    \"artist\": { \"assign\": \"Lisa Ekdahl\" }" +
                                      "  }" +
                                      "}");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/group/a/three\"," +
                       "  \"id\": \"id:space:music:g=a:three\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // POST with a document payload is a document put operation.
        access.session.expect((put, parameters) -> {
            DocumentPut expectedPut = new DocumentPut(doc2);
            expectedPut.setCondition(new TestAndSetCondition("test it"));
            assertEquals(expectedPut, put);
            assertEquals(parameters().withTraceLevel(9), parameters);
            Trace trace = new Trace(9);
            trace.trace(7, "Tracy Chapman", false);
            trace.getRoot().addChild(new TraceNode().setStrict(false)
                                                    .addChild("Fast Car")
                                                    .addChild("Baby Can I Hold You"));
            parameters.responseHandler().get().handleResponse(new DocumentResponse(0, doc2, trace));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?condition=test%20it&tracelevel=9", POST,
                                      "{" +
                                      "  \"fields\": {" +
                                      "    \"artist\": \"Asa-Chan & Jun-Ray\"," +
                                      "    \"embedding\": { \"values\": [4.0,5.0,6.0] } " +
                                      "  }" +
                                      "}");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"," +
                       "  \"trace\": [" +
                       "    {" +
                       "      \"message\": \"Tracy Chapman\"" +
                       "    }," +
                       "    {" +
                       "      \"fork\": [" +
                       "        {" +
                       "          \"message\": \"Fast Car\"" +
                       "        }," +
                       "        {" +
                       "          \"message\": \"Baby Can I Hold You\"" +
                       "        }" +
                       "      ]" +
                       "    }" +
                       "  ]" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // POST with no payload is a 400
        access.session.expect((__, ___) -> { throw new AssertionError("Not supposed to happen"); });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?condition=test%20it", POST, "");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"message\": \"Could not read document, no document?\"" +
                       "}",
                       response.readAll());
        assertEquals(400, response.getStatus());

        // POST with illegal payload is a 400
        access.session.expect((__, ___) -> { throw new AssertionError("Not supposed to happen"); });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?condition=test%20it", POST,
                                      "{" +
                                      "  ┻━┻︵ \\(°□°)/ ︵ ┻━┻" +
                                      "}");
        Inspector responseRoot = SlimeUtils.jsonToSlime(response.readAll()).get();
        assertEquals("/document/v1/space/music/number/1/two", responseRoot.field("pathId").asString());
        assertTrue(responseRoot.field("message").asString().startsWith("Unexpected character ('┻' (code 9531 / 0x253b)): was expecting double-quote to start field name"));
        assertEquals(400, response.getStatus());

        // PUT on a unknown document type is a 400
        access.session.expect((__, ___) -> { throw new AssertionError("Not supposed to happen"); });
        response = driver.sendRequest("http://localhost/document/v1/space/house/group/a/three?create=true", PUT,
                                      "{" +
                                      "  \"fields\": {" +
                                      "    \"artist\": { \"assign\": \"Lisa Ekdahl\" }" +
                                      "  }" +
                                      "}");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/house/group/a/three\"," +
                       "  \"message\": \"Document type house does not exist\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // PUT on document which is not found is a 200
        access.session.expect((update, parameters) -> {
            parameters.responseHandler().get().handleResponse(new UpdateResponse(0, false));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/docid/sonny", PUT,
                                      "{" +
                                      "  \"fields\": {" +
                                      "    \"artist\": { \"assign\": \"The Shivers\" }" +
                                      "  }" +
                                      "}");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid/sonny\"," +
                       "  \"id\": \"id:space:music::sonny\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // DELETE with full document ID is a document remove operation.
        access.session.expect((remove, parameters) -> {
            DocumentRemove expectedRemove = new DocumentRemove(doc2.getId());
            expectedRemove.setCondition(new TestAndSetCondition("false"));
            assertEquals(expectedRemove, remove);
            assertEquals(parameters().withRoute("route"), parameters);
            parameters.responseHandler().get().handleResponse(new DocumentIdResponse(0, doc2.getId()));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?route=route&condition=false", DELETE);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"" +
                       "}", response.readAll());
        assertEquals(200, response.getStatus());

        // GET with empty route is a 400
        access.session.expect((__, ___) -> { throw new AssertionError("Not supposed to happen"); });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?route=", DELETE);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"message\": \"Expected non-empty value for request property 'route'\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // GET with non-existent cluster is a 400
        access.session.expect((__, ___) -> { throw new AssertionError("Not supposed to happen"); });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?cluster=throw-me");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"message\": \"Your Vespa deployment has no content cluster 'throw-me', only 'content'\"" +
                       "}", response.readAll());
        assertEquals(400, response.getStatus());

        // TIMEOUT is a 504
        access.session.expect((id, parameters) -> {
            assertEquals(clock.instant().plusSeconds(1000), parameters.deadline().get());
            parameters.responseHandler().get().handleResponse(new Response(0, "timeout", Response.Outcome.TIMEOUT));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?timeout=1ks");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"," +
                       "  \"message\": \"timeout\"" +
                       "}", response.readAll());
        assertEquals(504, response.getStatus());

        // INSUFFICIENT_STORAGE is a 507
        access.session.expect((id, parameters) -> {
            parameters.responseHandler().get().handleResponse(new Response(0, "disk full", Response.Outcome.INSUFFICIENT_STORAGE));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two", DELETE);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"," +
                       "  \"message\": \"disk full\"" +
                       "}", response.readAll());
        assertEquals(507, response.getStatus());

        // PRECONDITION_FAILED is a 412
        access.session.expect((id, parameters) -> {
            parameters.responseHandler().get().handleResponse(new Response(0, "no dice", Response.Outcome.CONDITION_FAILED));
            return new Result();
        });
        response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two", DELETE);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"id\": \"id:space:music:n=1:two\"," +
                       "  \"message\": \"no dice\"" +
                       "}", response.readAll());
        assertEquals(412, response.getStatus());

        // OPTIONS gets options
        access.session.expect((__, ___) -> { throw new AssertionError("Not supposed to happen"); });
        response = driver.sendRequest("https://localhost/document/v1/space/music/docid/one", OPTIONS);
        assertEquals("", response.readAll());
        assertEquals(204, response.getStatus());
        assertEquals("GET,POST,PUT,DELETE", response.getResponse().headers().getFirst("Allow"));

        // PATCH is not allowed
        access.session.expect((__, ___) -> { throw new AssertionError("Not supposed to happen"); });
        response = driver.sendRequest("https://localhost/document/v1/space/music/docid/one", PATCH);
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/docid/one\"," +
                       "  \"message\": \"'PATCH' not allowed at '/document/v1/space/music/docid/one'. Allowed methods are: GET, POST, PUT, DELETE\"" +
                       "}", response.readAll());
        assertEquals(405, response.getStatus());

        // OVERLOAD is a 429
        access.session.expect((id, parameters) -> new Result(Result.ResultType.TRANSIENT_ERROR, Result.toError(Result.ResultType.TRANSIENT_ERROR)));
        var response1 = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two", POST, "{\"fields\": {}}");
        var response2 = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two", POST, "{\"fields\": {}}");
        var response3 = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two", POST, "{\"fields\": {}}");
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"message\": \"Rejecting execution due to overload: 2 requests already enqueued\"" +
                       "}", response3.readAll());
        assertEquals(429, response3.getStatus());
        access.session.expect((id, parameters) -> new Result(Result.ResultType.FATAL_ERROR, Result.toError(Result.ResultType.FATAL_ERROR)));
        handler.dispatchEnqueued();
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"message\": \"[FATAL_ERROR @ localhost]: FATAL_ERROR\"" +
                       "}", response1.readAll());
        assertEquals(502, response1.getStatus());
        assertSameJson("{" +
                       "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                       "  \"message\": \"[FATAL_ERROR @ localhost]: FATAL_ERROR\"" +
                       "}", response2.readAll());
        assertEquals(502, response2.getStatus());

        // Request response does not arrive before timeout has passed.
        AtomicReference<ResponseHandler> handler = new AtomicReference<>();
        access.session.expect((id, parameters) -> {
            handler.set(parameters.responseHandler().get());
            return new Result();
        });
        try {
            var response4 = driver.sendRequest("http://localhost/document/v1/space/music/docid/one?timeout=1ms");
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/docid/one\"," +
                           "  \"message\": \"Timeout after 1ms\"" +
                           "}", response4.readAll());
            assertEquals(504, response4.getStatus());
        }
        finally {
            if (handler.get() != null)                          // Timeout may have occurred before dispatch, or ...
                handler.get().handleResponse(new Response(0));  // response may eventually arrive, but too late.
        }

        assertEquals(3, metric.metrics().get("httpapi_succeeded").get(Map.of()), 0);
        assertEquals(1, metric.metrics().get("httpapi_condition_not_met").get(Map.of()), 0);
        assertEquals(1, metric.metrics().get("httpapi_not_found").get(Map.of()), 0);
        assertEquals(1, metric.metrics().get("httpapi_failed").get(Map.of()), 0);
        driver.close();
    }

    @Test
    public void testThroughput() throws InterruptedException {
        DocumentOperationExecutorConfig executorConfig = new DocumentOperationExecutorConfig.Builder().build();
        handler = new DocumentV1ApiHandler(clock, Duration.ofMillis(1), metric, metrics, access, docConfig,
                                           executorConfig, clusterConfig, bucketConfig);

        int writers = 4;
        int queueFill = executorConfig.maxThrottled() - writers;
        RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler);
        ScheduledExecutorService writer = Executors.newScheduledThreadPool(writers);
        ScheduledExecutorService reader = Executors.newScheduledThreadPool(1);
        ScheduledExecutorService replier = Executors.newScheduledThreadPool(writers);
        BlockingQueue<RequestHandlerTestDriver.MockResponseHandler> responses = new LinkedBlockingQueue<>();

        Response success = new Response(0, null, Response.Outcome.SUCCESS);
        int docs = 1 << 14;
        assertTrue(docs >= writers);
        AtomicReference<com.yahoo.jdisc.Response> failed = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(docs);
        reader.execute(() -> {
            while ( ! reader.isShutdown()) {
                try {
                    var response = responses.take();
                    response.awaitResponse().readAll();
                    if (response.getStatus() != 200)
                        failed.set(response.getResponse());
                    latch.countDown();
                }
                catch (InterruptedException e) { break; }
            }
        });

        // Fill the handler resend queue.
        long startNanos = System.nanoTime();
        CountDownLatch setup = new CountDownLatch(queueFill);
        access.session.expect((id, parameters) -> {
            setup.countDown();
            return new Result(Result.ResultType.TRANSIENT_ERROR, Result.toError(Result.ResultType.TRANSIENT_ERROR));
        });
        for (int i = 0; i < queueFill; i++) {
            int j = i;
            writer.execute(() -> {
                responses.add(driver.sendRequest("http://localhost/document/v1/ns/music/docid/" + j,
                                                 POST,
                                                 "{ \"fields\": { \"artist\": \"Sigrid\" } }"));
            });
        }
        setup.await();

        // Let "messagebus" start accepting messages.
        access.session.expect((id, parameters) -> {
            replier.schedule(() -> parameters.responseHandler().get().handleResponse(success), 10, TimeUnit.MILLISECONDS);
            return new Result(0);
        });
        // Send the rest of the documents. Rely on resender to empty queue of throttled operations.
        for (int i = queueFill; i < docs; i++) {
            int j = i;
            writer.execute(() -> {
                responses.add(driver.sendRequest("http://localhost/document/v1/ns/music/docid/" + j,
                                                 POST,
                                                 "{ \"fields\": { \"artist\": \"Sigrid\" } }"));
            });
        }
        latch.await();
        System.err.println(docs + " requests in " + (System.nanoTime() - startNanos) * 1e-9 + " seconds");

        assertNull(failed.get());
        driver.close();
    }


    static class MockDocumentAccess extends DocumentAccess {

        private final AtomicReference<Consumer<VisitorParameters>> expectations = new AtomicReference<>();
        private final Set<AckToken> outstanding = new CopyOnWriteArraySet<>();
        private final MockAsyncSession session = new MockAsyncSession();
        private Trace visitorTrace;

        MockDocumentAccess(DocumentmanagerConfig config) {
            super(new DocumentAccessParams().setDocumentmanagerConfig(config));
        }

        @Override
        public SyncSession createSyncSession(SyncParameters parameters) {
            throw new AssertionError("Not used");
        }

        @Override
        public AsyncSession createAsyncSession(AsyncParameters parameters) {
            return session;
        }

        @Override
        public VisitorSession createVisitorSession(VisitorParameters parameters) {
            VisitorSession visitorSession = new VisitorSession() {
                {
                    parameters.getControlHandler().setSession(this);
                    if (parameters.getLocalDataHandler() != null)
                        parameters.getLocalDataHandler().setSession(this);
                }
                @Override public boolean isDone() { return false; }
                @Override public ProgressToken getProgress() { return null; }
                @Override public Trace getTrace() { return visitorTrace; }
                @Override public boolean waitUntilDone(long timeoutMs) { return false; }
                @Override public void ack(AckToken token) { assertTrue(outstanding.remove(token)); }
                @Override public void abort() { }
                @Override public VisitorResponse getNext() { return null; }
                @Override public VisitorResponse getNext(int timeoutMilliseconds) { return null; }
                @Override public void destroy() { assertEquals(Set.of(), outstanding); }
            };
            expectations.get().accept(parameters);
            return visitorSession;
        }

        @Override
        public VisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters parameters) {
            throw new AssertionError("Not used");
        }

        @Override
        public SubscriptionSession createSubscription(SubscriptionParameters parameters) {
            throw new AssertionError("Not used");
        }

        @Override
        public SubscriptionSession openSubscription(SubscriptionParameters parameters) {
            throw new AssertionError("Not used");
        }

        public void expect(Consumer<VisitorParameters> expectations) {
            this.expectations.set(expectations);
        }

        public void expect(Collection<AckToken> tokens) {
            outstanding.addAll(tokens);
        }

    }


    static class MockAsyncSession implements AsyncSession {

        private final AtomicReference<BiFunction<Object, DocumentOperationParameters, Result>> expectations = new AtomicReference<>();

        @Override
        public Result put(Document document) {
            throw new AssertionError("Not used");
        }

        @Override
        public Result put(DocumentPut documentPut, DocumentOperationParameters parameters) {
            return expectations.get().apply(documentPut, parameters);
        }

        @Override
        public Result get(DocumentId id) {
            throw new AssertionError("Not used");
        }

        @Override
        public Result get(DocumentId id, DocumentOperationParameters parameters) {
            return expectations.get().apply(id, parameters);
        }

        @Override
        public Result remove(DocumentId id) {
            throw new AssertionError("Not used");
        }

        @Override
        public Result remove(DocumentRemove remove, DocumentOperationParameters parameters) {
            return expectations.get().apply(remove, parameters);
        }

        @Override
        public Result update(DocumentUpdate update) {
            throw new AssertionError("Not used");
        }

        @Override
        public Result update(DocumentUpdate update, DocumentOperationParameters parameters) {
            return expectations.get().apply(update, parameters);
        }

        @Override
        public double getCurrentWindowSize() {
            throw new AssertionError("Not used");
        }

        public void expect(BiFunction<Object, DocumentOperationParameters, Result> expectations) {
            this.expectations.set(expectations);
        }

        @Override
        public Response getNext() {
            throw new AssertionError("Not used");
        }

        @Override
        public Response getNext(int timeoutMilliseconds) {
            throw new AssertionError("Not used");
        }

        @Override
        public void destroy() { }

    }

    static void assertSameJson(String expected, String actual) {
        ByteArrayOutputStream expectedPretty = new ByteArrayOutputStream();
        ByteArrayOutputStream actualPretty = new ByteArrayOutputStream();
        JsonFormat formatter = new JsonFormat(false);
        try {
            formatter.encode(actualPretty, SlimeUtils.jsonToSlimeOrThrow(actual));
            formatter.encode(expectedPretty, SlimeUtils.jsonToSlimeOrThrow(expected));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(expectedPretty.toString(UTF_8), actualPretty.toString(UTF_8));
    }

}
