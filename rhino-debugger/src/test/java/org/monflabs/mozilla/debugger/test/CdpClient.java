/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.monflabs.mozilla.debugger.json.Json;

/**
 * A CDP client over the JDK's WebSocket: sends requests, matches responses by id, queues events.
 */
final class CdpClient implements AutoCloseable {
    static final long TIMEOUT = 20;

    private final WebSocket socket;
    private final BlockingQueue<Map<String, Object>> responses = new LinkedBlockingQueue<>();
    private final BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();
    private final CompletableFuture<String> closed = new CompletableFuture<>();
    private long nextId = 1;

    CdpClient(final String url) throws Exception {
        socket =
                HttpClient.newHttpClient()
                        .newWebSocketBuilder()
                        .buildAsync(
                                URI.create(url),
                                new WebSocket.Listener() {
                                    @Override
                                    @SuppressWarnings("unchecked")
                                    public CompletionStage<?> onText(
                                            final WebSocket ws,
                                            final CharSequence data,
                                            final boolean last) {
                                        partial.append(data);
                                        if (last) {
                                            final Map<String, Object> message =
                                                    (Map<String, Object>)
                                                            Json.parse(partial.toString());
                                            partial.setLength(0);
                                            if (message.containsKey("id")) {
                                                responses.add(message);
                                            } else {
                                                events.add(message);
                                            }
                                        }
                                        ws.request(1);
                                        return null;
                                    }

                                    @Override
                                    public CompletionStage<?> onClose(
                                            final WebSocket ws,
                                            final int statusCode,
                                            final String reason) {
                                        closed.complete(statusCode + ":" + reason);
                                        return null;
                                    }
                                })
                        .get(TIMEOUT, TimeUnit.SECONDS);
    }

    /** Sends a request and returns its result, or throws with the error's message. */
    Map<String, Object> call(final String method, final Object... params) throws Exception {
        final long id = nextId++;
        socket.sendText(
                        Json.write(
                                Json.object(
                                        "id", id, "method", method, "params", Json.object(params))),
                        true)
                .get(TIMEOUT, TimeUnit.SECONDS);
        while (true) {
            final Map<String, Object> response = responses.poll(TIMEOUT, TimeUnit.SECONDS);
            if (response == null) {
                throw new AssertionError("no response to " + method + " within " + TIMEOUT + "s");
            }
            if (((Number) response.get("id")).longValue() != id) {
                continue;
            }
            if (response.containsKey("error")) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> error = (Map<String, Object>) response.get("error");
                throw new CdpFailure(
                        ((Number) error.get("code")).intValue(),
                        String.valueOf(error.get("message")));
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> result = (Map<String, Object>) response.get("result");
            return result;
        }
    }

    /** The next event of a given method, skipping others. */
    Map<String, Object> event(final String method) throws Exception {
        while (true) {
            final Map<String, Object> event = events.poll(TIMEOUT, TimeUnit.SECONDS);
            if (event == null) {
                throw new AssertionError("no " + method + " event within " + TIMEOUT + "s");
            }
            if (method.equals(event.get("method"))) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> params = (Map<String, Object>) event.get("params");
                return params;
            }
        }
    }

    boolean hasEvents() {
        return !events.isEmpty();
    }

    /**
     * Sends a request without waiting for its response - for a command whose own response may not
     * arrive because it ends the run (a resume past the last statement closes the connection).
     */
    void send(final String method, final Object... params) throws Exception {
        final long id = nextId++;
        socket.sendText(
                        Json.write(
                                Json.object(
                                        "id", id, "method", method, "params", Json.object(params))),
                        true)
                .get(TIMEOUT, TimeUnit.SECONDS);
    }

    /** Waits for the server to close the connection and returns "code:reason". */
    String awaitClose() throws Exception {
        return closed.get(TIMEOUT, TimeUnit.SECONDS);
    }

    void sendRaw(final String text) throws Exception {
        socket.sendText(text, true).get(TIMEOUT, TimeUnit.SECONDS);
    }

    Map<String, Object> rawResponse() throws Exception {
        return responses.poll(TIMEOUT, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(TIMEOUT, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            // the server may already be gone
        }
    }

    static final class CdpFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final int code;

        CdpFailure(final int code, final String message) {
            super(message);
            this.code = code;
        }
    }
}
