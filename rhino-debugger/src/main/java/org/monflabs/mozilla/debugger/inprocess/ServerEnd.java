/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.inprocess;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.monflabs.mozilla.debugger.CdpClientChannel;
import org.monflabs.mozilla.debugger.CdpTransport;

/**
 * The server ({@code CdpSession}) side of an in-process CDP pipe. Inbound (client-to-server)
 * messages go through a queue, since {@link #run} is a blocking pull loop exactly like {@code
 * WebSocketConnection.run}; outbound ones are delivered by calling the peer's listener directly,
 * synchronously, on the sending thread - safe because the UI's connection and session already
 * document that their callbacks may arrive on any thread and are trampolined onto their own
 * executor.
 */
final class ServerEnd implements CdpTransport {
    private static final Object CLOSE = new Object();

    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed;
    private volatile ClientEnd peer;

    ServerEnd(final AtomicBoolean closed) {
        this.closed = closed;
    }

    void setPeer(final ClientEnd peer) {
        this.peer = peer;
    }

    @Override
    public void run(final Consumer<String> onMessage) throws IOException {
        while (true) {
            final Object item;
            try {
                item = inbox.take();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (item == CLOSE) {
                return;
            }
            onMessage.accept((String) item);
        }
    }

    @Override
    public synchronized void send(final String text) throws IOException {
        // one message at a time, as a socket delivers them: the paused script and the session
        // both send
        if (closed.get()) {
            throw new IOException("connection closed");
        }
        final CdpClientChannel.ChannelListener listener = peer.listener();
        if (listener != null) {
            listener.onText(text, true);
        }
    }

    @Override
    public void close(final int code, final String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        inbox.offer(CLOSE);
        final CdpClientChannel.ChannelListener listener = peer.listener();
        if (listener != null) {
            listener.onClosed(reason);
        }
    }

    void deliver(final String text) {
        inbox.offer(text);
    }
}
