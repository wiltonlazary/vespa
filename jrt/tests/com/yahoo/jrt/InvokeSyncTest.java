// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.jrt.tool.RpcInvoker;
import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class InvokeSyncTest {

    Supervisor server;
    Acceptor   acceptor;
    Supervisor client;
    Target     target;
    SimpleRequestAccessFilter filter;
    
    @Before
    public void setUp() throws ListenFailedException {
        server   = new Supervisor(new Transport());
        client   = new Supervisor(new Transport());
        acceptor = server.listen(new Spec(0));
        target   = client.connect(new Spec("localhost", acceptor.port()));
        filter = new SimpleRequestAccessFilter();
        server.addMethod(new Method("concat", "ss", "s", this::rpc_concat)
                         .methodDesc("Concatenate 2 strings")
                         .paramDesc(0, "str1", "a string")
                         .paramDesc(1, "str2", "another string")
                         .returnDesc(0, "ret", "str1 followed by str2")
                          .requestAccessFilter(filter));
        server.addMethod(new Method("alltypes", "bhilfds", "s", this::rpc_alltypes)
                          .methodDesc("Method taking all types of params"));
    }

    @After
    public void tearDown() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        target.close();
        acceptor.shutdown().join();
        client.transport().shutdown().join();
        server.transport().shutdown().join();
    }

    private void rpc_concat(Request req) {
        req.returnValues().add(new StringValue(req.parameters()
                                               .get(0).asString() +
                                               req.parameters()
                                               .get(1).asString()));
    }

    private void rpc_alltypes(Request req) {
        req.returnValues().add(new StringValue("This was alltypes. The string param was: "+req.parameters().get(6).asString()));
    }
    
    @org.junit.Test
    public void testSync() {
        Request req = new Request("concat");
        req.parameters().add(new StringValue("abc"));
        req.parameters().add(new StringValue("def"));

        target.invokeSync(req, Duration.ofSeconds(5));

        assertTrue(!req.isError());
        assertEquals(1, req.returnValues().size());
        assertEquals("abcdef", req.returnValues().get(0).asString());
    }

    @org.junit.Test
    public void testRpcInvoker() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        RpcInvoker.main(new String[] {"-h", "localhost:"+acceptor.port(), "concat", "s:foo", "s:bar"});
        baos.flush();
        assertEquals(baos.toString(), "foobar\n");
        baos.reset();
        System.setOut(new PrintStream(baos));
        RpcInvoker.main(new String[] {"-h", "localhost:"+acceptor.port(), "alltypes", "b:1", "h:2", "i:3", "l:4", "f:5.0", "d:6.0", "s:baz"});
        baos.flush();
        assertEquals(baos.toString(), "This was alltypes. The string param was: baz\n");
    }

    @org.junit.Test
    public void testFilterIsInvoked() {
        Request req = new Request("concat");
        req.parameters().add(new StringValue("abc"));
        req.parameters().add(new StringValue("def"));
        assertFalse(filter.invoked);
        target.invokeSync(req, Duration.ofSeconds(10));
        assertFalse(req.isError());
        assertEquals("abcdef", req.returnValues().get(0).asString());
        assertTrue(filter.invoked);
    }


}
