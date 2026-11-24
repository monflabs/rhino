/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.inprocess;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.CdpClientChannel;
import org.monflabs.mozilla.debugger.cdp.CdpSession;

/**
 * A same-JVM, socket-free Chrome DevTools Protocol session: the counterpart to {@link
 * org.monflabs.mozilla.debugger.CdpServer} for when the engine and a debugger client (for instance
 * the Swing debugger panel) run in the very same process, or when no network stack is available at
 * all. Reuses the CDP session's own JSON-RPC dispatch unchanged - only the transport underneath it
 * differs.
 *
 * <pre>
 *   final Debugger debugger = Debugger.of(engine);
 *   final InProcessCdpServer.Handle handle =
 *           InProcessCdpServer.open(debugger, InspectOptions.parse("", false));
 *   // hand handle.clientChannel() to a debugger UI in the same JVM
 * </pre>
 */
public final class InProcessCdpServer {

    /** A running in-process session. */
    public static final class Handle implements AutoCloseable {
        private final ServerEnd serverEnd;
        private final ClientEnd clientEnd;

        private Handle(final ServerEnd serverEnd, final ClientEnd clientEnd) {
            this.serverEnd = serverEnd;
            this.clientEnd = clientEnd;
        }

        /**
         * The client-side channel - hand this to the debugger UI's own {@code
         * CdpClientChannel}-accepting {@code attach} or {@code open}.
         *
         * @return the client channel
         */
        public CdpClientChannel clientChannel() {
            return clientEnd;
        }

        @Override
        public void close() {
            serverEnd.close(1001, "server closing");
        }
    }

    private InProcessCdpServer() {}

    /**
     * Starts an in-process session for a debugger: no socket, no port, no network stack of any kind
     * - the only way to reach it is through the returned {@link Handle}'s own {@link
     * Handle#clientChannel()}.
     *
     * <p>Unlike {@code CdpServer.open}, {@code options.host()} and {@code options.port()} are
     * meaningless here and ignored. {@code options.waitForDebugger()} is deliberately not
     * supported, and is rejected outright: {@code CdpServer.open} can block the caller until a
     * third party discovers the port and connects, but an in-process pipe has no third party - the
     * only possible client is whoever receives the {@link Handle} this method returns, so blocking
     * the caller before returning it would deadlock forever rather than merely freeze a UI thread
     * until an external client shows up.
     *
     * @param debugger the debugger to expose
     * @param options only {@code waitForDebugger()} is consulted, and must be false
     * @return the running session's handle
     */
    public static Handle open(final Debugger debugger, final InspectOptions options) {
        if (options.waitForDebugger()) {
            throw new IllegalArgumentException(
                    "InProcessCdpServer.open() cannot honor waitForDebugger=true: "
                            + "there is no third party to attach before this call returns the only handle a client could ever use");
        }
        final AtomicBoolean closed = new AtomicBoolean();
        final ServerEnd serverEnd = new ServerEnd(closed);
        final ClientEnd clientEnd = new ClientEnd(closed);
        serverEnd.setPeer(clientEnd);
        clientEnd.setPeer(serverEnd);
        final String id = UUID.randomUUID().toString();
        Thread.ofVirtual()
                .name("rhino-debugger-inprocess")
                .start(() -> new CdpSession(debugger, serverEnd, id, () -> {}).run());
        return new Handle(serverEnd, clientEnd);
    }
}
