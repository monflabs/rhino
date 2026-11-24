/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.test;

import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertEquals;
import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertNotNull;
import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertTrue;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.CdpServer;
import org.monflabs.mozilla.debugger.ui.cdp.CdpConnection;
import org.monflabs.mozilla.debugger.ui.cdp.CdpException;
import org.monflabs.mozilla.debugger.ui.cdp.Json;

/**
 * The transport against a real engine and CDP server: request/response, protocol errors, events,
 * close, and the one-client-at-a-time refusal.
 */
@SuppressWarnings({"javadoc", "unchecked", "deprecation", "try"})
public class CdpConnectionTest {
    protected static final long TIMEOUT = 20;

    protected TestEngine engine;
    protected AutoCloseable server;

    @BeforeEach
    public void setUp() throws Exception {
        engine = new TestEngine();
        openServer();
    }

    /** Starts the transport under test. Overridden for the in-process one. */
    protected void openServer() throws Exception {
        server = CdpServer.open(engine.debugger(), InspectOptions.parse("127.0.0.1:0", false));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
        engine.debugger().close();
    }

    /** A test listener that queues events and remembers the close reason. */
    protected static final class Events implements CdpConnection.Listener {
        final LinkedBlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        final AtomicReference<String> closed = new AtomicReference<>();

        @Override
        public void onEvent(final String method, final Map<String, Object> params) {
            queue.add(Json.object("method", method, "params", params));
        }

        @Override
        public void onClosed(final String reason) {
            closed.set(reason);
        }

        Map<String, Object> next(final String method) throws InterruptedException {
            while (true) {
                final Map<String, Object> event = queue.poll(TIMEOUT, TimeUnit.SECONDS);
                if (event == null) {
                    throw new AssertionError("no " + method + " event within " + TIMEOUT + "s");
                }
                if (method.equals(event.get("method"))) {
                    return (Map<String, Object>) event.get("params");
                }
            }
        }
    }

    /** Opens a client on the transport under test. Overridden for the in-process one. */
    protected CdpConnection connect(final Events events) throws Exception {
        return CdpConnection.connect(((CdpServer.Handle) server).webSocketUrl(), events)
                .get(TIMEOUT, TimeUnit.SECONDS);
    }

    /** Runs a call expected to fail and returns the CdpException it carried. */
    protected static CdpException failureOf(final Callable call) {
        try {
            call.run();
        } catch (final ExecutionException e) {
            return (CdpException) e.getCause();
        } catch (final Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
        throw new AssertionError("expected a failure");
    }

    protected interface Callable {
        void run() throws Exception;
    }

    @Test
    public void callsRoundTripAndErrorsSurface() throws Exception {
        final Events events = new Events();
        try (CdpConnection connection = connect(events)) {
            connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            connection.call("Debugger.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            // Runtime.enable makes the server announce the context
            assertNotNull(events.next("Runtime.executionContextCreated").get("context"));

            // an unknown method is a protocol error, code -32601
            final CdpException cdp =
                    failureOf(
                            () ->
                                    connection
                                            .call("Nope.doesNotExist", null)
                                            .get(TIMEOUT, TimeUnit.SECONDS));
            assertEquals(cdp.code(), -32601);
            assertTrue(!cdp.isTransport());
        }
    }

    @Test
    public void eventsArriveOnTheListener() throws Exception {
        final Events events = new Events();
        try (CdpConnection connection = connect(events)) {
            connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            connection.call("Debugger.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
            engine.put(TestEngine.FILENAME, "probe.js");
            engine.eval("1 + 1;");
            final Map<String, Object> script = events.next("Debugger.scriptParsed");
            assertTrue(
                    String.valueOf(script.get("url")).endsWith("/probe.js"),
                    String.valueOf(script.get("url")));
        }
    }

    @Test
    public void closeFailsPendingAndTellsTheListener() throws Exception {
        final Events events = new Events();
        final CdpConnection connection = connect(events);
        connection.call("Runtime.enable", null).get(TIMEOUT, TimeUnit.SECONDS);
        connection.close();
        assertNotNull(events.closed.get());
        final CdpException cdp =
                failureOf(
                        () ->
                                connection
                                        .call("Runtime.enable", null)
                                        .get(TIMEOUT, TimeUnit.SECONDS));
        assertEquals(cdp.code(), CdpException.TRANSPORT_CLOSED);
    }

    @Test
    public void aSecondClientIsRefusedAsBusy() throws Exception {
        final Events first = new Events();
        try (CdpConnection ignored = connect(first)) {
            final CdpException cdp =
                    failureOf(
                            () ->
                                    CdpConnection.connect(
                                                    ((CdpServer.Handle) server).webSocketUrl(),
                                                    new Events())
                                            .get(TIMEOUT, TimeUnit.SECONDS));
            assertEquals(
                    cdp.code(), CdpException.TRANSPORT_BUSY, "message was: " + cdp.getMessage());
            assertTrue(cdp.isTransport());
        }
    }
}
