/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.api.debugger.DebuggerFrontend;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.cdp.CdpSession;
import org.monflabs.mozilla.debugger.json.Json;
import org.monflabs.mozilla.debugger.ws.HttpWebSocketServer;

/**
 * The Chrome DevTools Protocol server: what {@code --inspect} starts, and what an embedder can
 * start for a {@link Debugger} of its own. Listens on one port for the discovery documents Chrome
 * and VS Code fetch and for the WebSocket a client then opens, exactly as Node's inspector does, so
 * any client that can attach to Node can attach here.
 *
 * <pre>
 *   ContextFactory factory = new ContextFactory();
 *   try (CdpServer.Handle handle = CdpServer.open(Debugger.of(factory), InspectOptions.parse("9229", false))) {
 *       factory.call(cx -> cx.evaluateString(...));
 *   }
 * </pre>
 */
public final class CdpServer implements DebuggerFrontend {

    /** A running server. */
    public static final class Handle implements AutoCloseable {
        private final HttpWebSocketServer server;
        private final String id;

        private Handle(final HttpWebSocketServer server, final String id) {
            this.server = server;
            this.id = id;
        }

        /**
         * The port the server listens on.
         *
         * @return the port
         */
        public int port() {
            return server.port();
        }

        /**
         * The WebSocket url a client connects to.
         *
         * @return the url
         */
        public String webSocketUrl() {
            return "ws://" + server.host() + ":" + server.port() + "/" + id;
        }

        @Override
        public void close() {
            server.close();
        }
    }

    /** For the service loader. */
    public CdpServer() {}

    @Override
    public String name() {
        return "Chrome DevTools Protocol";
    }

    @Override
    public AutoCloseable start(final Debugger debugger, final InspectOptions options)
            throws IOException {
        return startServer(debugger, options);
    }

    /**
     * Starts a server for a debugger.
     *
     * @param debugger the debugger to expose
     * @param options where to listen, and whether to wait for a client before returning
     * @return the running server
     * @throws IOException if the port cannot be bound
     */
    public static Handle open(final Debugger debugger, final InspectOptions options)
            throws IOException {
        return startServer(debugger, options);
    }

    private static Handle startServer(final Debugger debugger, final InspectOptions options)
            throws IOException {
        final String id = UUID.randomUUID().toString();
        final CountDownLatch waiting = new CountDownLatch(options.waitForDebugger() ? 1 : 0);
        final HttpWebSocketServer[] holder = new HttpWebSocketServer[1];
        final HttpWebSocketServer server =
                new HttpWebSocketServer(
                        options.host(),
                        options.port(),
                        "/" + id,
                        () -> discovery(holder[0], id),
                        () ->
                                Json.write(
                                        Json.object(
                                                "Browser",
                                                "Rhino/"
                                                        + org.monflabs.mozilla.javascript.Context
                                                                .class
                                                                .getPackage()
                                                                .getImplementationVersion(),
                                                "Protocol-Version",
                                                "1.3")),
                        connection ->
                                new CdpSession(debugger, connection, id, waiting::countDown).run());
        holder[0] = server;
        server.start();
        final Handle handle = new Handle(server, id);
        System.err.println("Debugger listening on " + handle.webSocketUrl());
        System.err.println("For help, see: https://nodejs.org/en/docs/inspector");
        if (options.waitForDebugger()) {
            debugger.pauseOnStart();
            try {
                waiting.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Debugger attached.");
        }
        return handle;
    }

    private static String discovery(final HttpWebSocketServer server, final String id) {
        final String address = server.host() + ":" + server.port();
        return Json.write(
                List.of(
                        Json.object(
                                "description",
                                "rhino instance",
                                "devtoolsFrontendUrl",
                                "devtools://devtools/bundled/js_app.html?experiments=true&v8only=true&ws="
                                        + address
                                        + "/"
                                        + id,
                                "devtoolsFrontendUrlCompat",
                                "devtools://devtools/bundled/inspector.html?experiments=true&v8only=true&ws="
                                        + address
                                        + "/"
                                        + id,
                                "faviconUrl",
                                "https://nodejs.org/static/images/favicons/favicon.ico",
                                "id",
                                id,
                                "title",
                                "rhino[" + ProcessHandle.current().pid() + "]",
                                "type",
                                "node",
                                "url",
                                "file://",
                                "webSocketDebuggerUrl",
                                "ws://" + address + "/" + id)));
    }
}
