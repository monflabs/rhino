/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The server side of one CDP connection, from the CDP session's own point of view: read and deliver
 * inbound messages until the peer goes away, send outbound ones, close. Two implementations exist -
 * {@code WebSocketConnection} (a real socket) and an in-process one (no socket at all, for when the
 * engine and a debugger client run in the same JVM) - {@code CdpSession} itself has no idea which
 * one it is talking to.
 */
public interface CdpTransport {

    /**
     * Delivers each inbound text message to {@code onMessage} until the peer disconnects. Blocks
     * the calling thread for the connection's whole lifetime - callers run this on a dedicated
     * thread.
     *
     * @param onMessage where each inbound message goes
     * @throws IOException if the connection breaks
     */
    void run(Consumer<String> onMessage) throws IOException;

    /**
     * Sends one complete text message. Safe to call from any thread.
     *
     * @param text the message
     * @throws IOException if it cannot be sent
     */
    void send(String text) throws IOException;

    /**
     * Closes the connection. Idempotent.
     *
     * @param code a close status code
     * @param reason a short reason
     */
    void close(int code, String reason);
}
