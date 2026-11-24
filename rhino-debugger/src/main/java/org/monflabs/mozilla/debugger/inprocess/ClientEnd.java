/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.inprocess;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.monflabs.mozilla.debugger.CdpClientChannel;

/**
 * The client side of an in-process CDP pipe - see {@link ServerEnd}. Sending hands the message
 * straight to the server's inbox queue; there is nothing to queue in the other direction, since the
 * server delivers directly.
 */
final class ClientEnd implements CdpClientChannel {
    private final AtomicBoolean closed;
    private volatile ChannelListener listener;
    private volatile ServerEnd peer;

    ClientEnd(final AtomicBoolean closed) {
        this.closed = closed;
    }

    void setPeer(final ServerEnd peer) {
        this.peer = peer;
    }

    ChannelListener listener() {
        return listener;
    }

    @Override
    public void listen(final ChannelListener listener) {
        this.listener = listener;
    }

    @Override
    public CompletionStage<?> sendText(final String text) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IOException("connection closed"));
        }
        peer.deliver(text);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void requestClose() {
        peer.close(1000, "connection closed");
    }
}
