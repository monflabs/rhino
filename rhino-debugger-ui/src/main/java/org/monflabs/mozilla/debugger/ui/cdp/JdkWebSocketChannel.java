/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.cdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.monflabs.mozilla.debugger.CdpClientChannel;

/**
 * A {@link CdpClientChannel} over the JDK's {@link WebSocket} - the real, over-the-wire transport
 * {@link CdpConnection#connect(String, CdpConnection.Listener)} uses. A pure passthrough: no
 * send-serialization of its own, since {@code CdpConnection} already chains its own sends before
 * ever calling {@link #sendText(String)}.
 */
final class JdkWebSocketChannel implements CdpClientChannel {

    private volatile WebSocket socket;
    private volatile ChannelListener listener;

    private JdkWebSocketChannel() {}

    /**
     * Dials a WebSocket and wraps it once the handshake completes.
     *
     * @param wsUrl the {@code ws://host:port/...} url
     * @return a future for the connected channel; fails the way the raw {@code HttpClient}/{@code
     *     WebSocket} dial fails (including a {@link java.net.http.WebSocketHandshakeException} for
     *     a non-101 response, e.g. the server's own 403 "busy" refusal)
     */
    static CompletableFuture<JdkWebSocketChannel> dial(final String wsUrl) {
        final JdkWebSocketChannel channel = new JdkWebSocketChannel();
        final WebSocket.Listener wsListener =
                new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(
                            final WebSocket ws, final CharSequence data, final boolean last) {
                        final ChannelListener l = channel.listener;
                        if (l != null) {
                            l.onText(data, last);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(
                            final WebSocket ws, final int statusCode, final String reason) {
                        final ChannelListener l = channel.listener;
                        if (l != null) {
                            l.onClosed(
                                    reason == null || reason.isEmpty()
                                            ? "connection closed"
                                            : reason);
                        }
                        return null;
                    }

                    @Override
                    public void onError(final WebSocket ws, final Throwable error) {
                        final ChannelListener l = channel.listener;
                        if (l != null) {
                            l.onClosed(String.valueOf(error.getMessage()));
                        }
                    }
                };
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), wsListener)
                .thenApply(
                        ws -> {
                            channel.socket = ws;
                            return channel;
                        });
    }

    @Override
    public void listen(final ChannelListener listener) {
        this.listener = listener;
    }

    @Override
    public CompletionStage<?> sendText(final String text) {
        return socket.sendText(text, true);
    }

    @Override
    public void requestClose() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        } catch (final RuntimeException alreadyGone) {
            // the server may already be gone
        }
    }
}
