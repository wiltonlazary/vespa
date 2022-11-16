// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespastat;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusSyncSession;
import com.yahoo.documentapi.messagebus.protocol.GetBucketListReply;
import com.yahoo.documentapi.messagebus.protocol.StatBucketReply;
import com.yahoo.messagebus.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BucketStatsRetrieverTest {
    private final BucketIdFactory bucketIdFactory = new BucketIdFactory();

    private DocumentAccessFactory mockedFactory;
    private MessageBusDocumentAccess mockedDocumentAccess;
    private MessageBusSyncSession mockedSession;
    private final String bucketSpace = "default";


    @BeforeEach
    public void prepareMessageBusMocks() {
        this.mockedFactory = mock(DocumentAccessFactory.class);
        this.mockedDocumentAccess = mock(MessageBusDocumentAccess.class);
        this.mockedSession = mock(MessageBusSyncSession.class);
        when(mockedFactory.createDocumentAccess()).thenReturn(mockedDocumentAccess);
        when(mockedDocumentAccess.createSyncSession(any())).thenReturn(mockedSession);
    }

    @Test
    void testGetBucketId() throws BucketStatsException {
        BucketStatsRetriever retriever = createRetriever();

        assertEquals("BucketId(0x80000000000004d2)",
                retriever.getBucketIdForType(ClientParameters.SelectionType.USER, "1234").toString());
        assertEquals("BucketId(0x800000003a7455d7)",
                retriever.getBucketIdForType(ClientParameters.SelectionType.GROUP, "mygroup").toString());
        assertEquals("BucketId(0x800000003a7455d7)",
                retriever.getBucketIdForType(ClientParameters.SelectionType.BUCKET, "0x800000003a7455d7").toString());
        assertEquals("BucketId(0xeb018ac5e5732db3)",
                retriever.getBucketIdForType(ClientParameters.SelectionType.DOCUMENT, "id:ns:type::another").toString());
        assertEquals("BucketId(0xeadd5fe811a2012c)",
                retriever.getBucketIdForType(ClientParameters.SelectionType.GID, "0x2c01a21163cb7d0ce85fddd6").toString());
    }

    @Test
    void testRetrieveBucketList() throws BucketStatsException {
        String bucketInfo = "I like turtles!";
        BucketId bucketId = bucketIdFactory.getBucketId(new DocumentId("id:ns:type::another"));

        GetBucketListReply reply = new GetBucketListReply();
        reply.getBuckets().add(new GetBucketListReply.BucketInfo(bucketId, bucketInfo));
        when(mockedSession.syncSend(any())).thenReturn(reply);

        List<GetBucketListReply.BucketInfo> bucketList = createRetriever().retrieveBucketList(bucketId, bucketSpace);

        verify(mockedSession, times(1)).syncSend(any());
        assertEquals(1, bucketList.size());
        assertEquals(bucketInfo, bucketList.get(0).getBucketInformation());
    }

    @Test
    void testRetrieveBucketStats() throws BucketStatsException {
        String docId = "id:ns:type::another";
        String bucketInfo = "I like turtles!";
        BucketId bucketId = bucketIdFactory.getBucketId(new DocumentId(docId));

        StatBucketReply reply = new StatBucketReply();
        reply.setResults(bucketInfo);
        when(mockedSession.syncSend(any())).thenReturn(reply);
        String result = createRetriever().retrieveBucketStats(ClientParameters.SelectionType.DOCUMENT, docId, bucketId, bucketSpace);

        verify(mockedSession, times(1)).syncSend(any());
        assertEquals(bucketInfo, result);
    }

    @Test
    void testShutdownHook() {
        class MockShutdownRegistrar implements BucketStatsRetriever.ShutdownHookRegistrar {
            public Runnable shutdownRunnable;

            @Override
            public void registerShutdownHook(Runnable runnable) {
                shutdownRunnable = runnable;
            }
        }
        MockShutdownRegistrar registrar = new MockShutdownRegistrar();
        new BucketStatsRetriever(mockedFactory, "default", registrar);
        registrar.shutdownRunnable.run();

        verify(mockedSession, times(1)).destroy();
        verify(mockedDocumentAccess, times(1)).shutdown();
    }

    @Test
    void testShouldFailOnReplyError() throws BucketStatsException {
        assertThrows(BucketStatsException.class, () -> {
            GetBucketListReply reply = new GetBucketListReply();
            reply.addError(new Error(0, "errormsg"));
            when(mockedSession.syncSend(any())).thenReturn(reply);

            createRetriever().retrieveBucketList(new BucketId(1), bucketSpace);
        });
    }

    @Test
    void testRoute() throws BucketStatsException {
        String route = "default";
        BucketId bucketId = bucketIdFactory.getBucketId(new DocumentId("id:ns:type::another"));
        GetBucketListReply reply = new GetBucketListReply();
        reply.getBuckets().add(new GetBucketListReply.BucketInfo(bucketId, "I like turtles!"));
        when(mockedSession.syncSend(any())).thenReturn(reply);

        BucketStatsRetriever retriever = new BucketStatsRetriever(mockedFactory, route, t -> {
        });
        retriever.retrieveBucketList(new BucketId(0), bucketSpace);

        // Route is set at session-level, not per message sent.
        verify(mockedSession).setRoute(eq(route));
    }

    private BucketStatsRetriever createRetriever() {
        return new BucketStatsRetriever(mockedFactory, "default", t -> {});
    }

}
