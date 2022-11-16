// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.ManualTimer;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.QueueAdapter;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ThrottlerTestCase {

    Slobrok slobrok;
    TestServer src, dst;

    @BeforeEach
    public void setUp() throws ListenFailedException {
        RoutingTableSpec table = new RoutingTableSpec(SimpleProtocol.NAME);
        table.addHop("dst", "test/dst/session", List.of("test/dst/session"));
        table.addRoute("test", List.of("dst"));
        slobrok = new Slobrok();
        src = new TestServer("test/src", table, slobrok, null);
        dst = new TestServer("test/dst", table, slobrok, null);
    }

    @AfterEach
    public void tearDown() {
        dst.destroy();
        src.destroy();
        slobrok.stop();
    }

    @Test
    void testMaxCount() {
        // Prepare a source session with throttle enabled.
        SourceSessionParams params = new SourceSessionParams().setTimeout(600.0);
        StaticThrottlePolicy policy = new StaticThrottlePolicy();
        policy.setMaxPendingCount(10);
        params.setThrottlePolicy(policy);

        Receptor src_rr = new Receptor();
        SourceSession src_s = src.mb.createSourceSession(src_rr, params);

        // Prepare a destination session to acknowledge messages.
        QueueAdapter dst_q = new QueueAdapter();
        DestinationSession dst_s = dst.mb.createDestinationSession("session", true, dst_q);
        src.waitSlobrok("test/dst/session", 1);

        // Send until throttler rejects a message.
        for (int i = 0; i < policy.getMaxPendingCount(); i++) {
            assertTrue(src_s.send(new SimpleMessage("msg"), "test").isAccepted());
        }
        assertFalse(src_s.send(new SimpleMessage("msg"), "test").isAccepted());

        // Acknowledge one message at a time, then attempt to send two more.
        for (int i = 0; i < 10; i++) {
            assertTrue(dst_q.waitSize(policy.getMaxPendingCount(), 60));
            dst_s.acknowledge((Message) dst_q.dequeue());

            assertNotNull(src_rr.getReply(60));
            assertTrue(src_s.send(new SimpleMessage("msg"), "test").isAccepted());
            assertFalse(src_s.send(new SimpleMessage("msg"), "test").isAccepted());
        }

        assertTrue(dst_q.waitSize(policy.getMaxPendingCount(), 60));
        while (!dst_q.isEmpty()) {
            dst_s.acknowledge((Message) dst_q.dequeue());
        }

        src_s.close();
        dst_s.destroy();
    }

    @Test
    void testMaxSize() {
        // Prepare a source session with throttle enabled.
        SourceSessionParams params = new SourceSessionParams().setTimeout(600.0);
        StaticThrottlePolicy policy = new StaticThrottlePolicy();
        policy.setMaxPendingCount(1000);
        policy.setMaxPendingSize(2);
        params.setThrottlePolicy(policy);

        Receptor src_rr = new Receptor();
        SourceSession src_s = src.mb.createSourceSession(src_rr, params);

        // Prepare a destination session to acknowledge messages.
        QueueAdapter dst_q = new QueueAdapter();
        DestinationSession dst_s = dst.mb.createDestinationSession("session", true, dst_q);
        src.waitSlobrok("test/dst/session", 1);

        assertTrue(src_s.send(new SimpleMessage("1"), "test").isAccepted());
        assertTrue(dst_q.waitSize(1, 60));
        assertTrue(src_s.send(new SimpleMessage("12"), "test").isAccepted());
        assertTrue(dst_q.waitSize(2, 60));

        assertFalse(src_s.send(new SimpleMessage("1"), "test").isAccepted());
        dst_s.acknowledge((Message) dst_q.dequeue());
        assertNotNull(src_rr.getReply(60));

        assertFalse(src_s.send(new SimpleMessage("1"), "test").isAccepted());
        dst_s.acknowledge((Message) dst_q.dequeue());
        assertNotNull(src_rr.getReply(60));

        assertTrue(src_s.send(new SimpleMessage("12"), "test").isAccepted());
        assertTrue(dst_q.waitSize(1, 60));
        assertFalse(src_s.send(new SimpleMessage("1"), "test").isAccepted());
        dst_s.acknowledge((Message) dst_q.dequeue());
        assertNotNull(src_rr.getReply(60));

        // Close sessions.
        src_s.close();
        dst_s.destroy();
    }

    @Test
    void testDynamicWindowSize() {
        ManualTimer timer = new ManualTimer();
        DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer);

        policy.setWindowSizeIncrement(5)
                .setResizeRate(1);

        double windowSize = getWindowSize(policy, timer, 100);
        assertTrue(windowSize >= 90 && windowSize <= 105);

        windowSize = getWindowSize(policy, timer, 200);
        assertTrue(windowSize >= 180 && windowSize <= 205);

        windowSize = getWindowSize(policy, timer, 50);
        assertTrue(windowSize >= 45 && windowSize <= 55);

        windowSize = getWindowSize(policy, timer, 500);
        assertTrue(windowSize >= 450 && windowSize <= 505);

        windowSize = getWindowSize(policy, timer, 100);
        assertTrue(windowSize >= 90 && windowSize <= 115);
    }

    @Test
    void testIdleTimePeriod() {
        ManualTimer timer = new ManualTimer();
        DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer);

        policy.setWindowSizeIncrement(5)
                .setMinWindowSize(1)
                .setResizeRate(1);

        double windowSize = getWindowSize(policy, timer, 100);
        assertTrue(windowSize >= 90 && windowSize <= 110);

        Message msg = new SimpleMessage("foo");
        timer.advance(30 * 1000);
        assertTrue(policy.canSend(msg, 0));
        assertTrue(windowSize >= 90 && windowSize <= 110);

        timer.advance(60 * 1000 + 1);
        assertTrue(policy.canSend(msg, 50));
        assertEquals(55, policy.getMaxPendingCount());

        timer.advance(60 * 1000 + 1);
        assertTrue(policy.canSend(msg, 0));
        assertEquals(5, policy.getMaxPendingCount());

    }

    @Test
    void testMinWindowSize() {
        ManualTimer timer = new ManualTimer();
        DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer);

        policy.setWindowSizeIncrement(5)
                .setResizeRate(1)
                .setMinWindowSize(150);

        double windowSize = getWindowSize(policy, timer, 200);
        assertTrue(windowSize >= 150 && windowSize <= 210);
    }

    @Test
    void testMaxWindowSize() {
        ManualTimer timer = new ManualTimer();
        DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer);

        policy.setWindowSizeIncrement(5);
        policy.setResizeRate(1);
        policy.setMaxWindowSize(50);

        double windowSize = getWindowSize(policy, timer, 100);
        assertTrue(windowSize >= 40 && windowSize <= 50);
    }

    private int getWindowSize(DynamicThrottlePolicy policy, ManualTimer timer, int maxPending) {
        Message msg = new SimpleMessage("foo");
        Reply reply = new SimpleReply("bar");
        reply.setContext(1);
        for (int i = 0; i < 999; ++i) {
            int numPending = 0;
            while (policy.canSend(msg, numPending)) {
                policy.processMessage(msg);
                ++numPending;
            }

            long tripTime = (numPending < maxPending) ? 1000L : 1000 + (numPending - maxPending) * 1000L;
            timer.advance(tripTime);

            while (--numPending >= 0) {
                policy.processReply(reply);
            }
        }
        int ret = policy.getMaxPendingCount();
        System.out.println("getWindowSize() = " + ret);
        return ret;
    }

}
