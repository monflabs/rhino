/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger;

import java.util.concurrent.CompletionStage;

/**
 * The client side of one CDP connection, from a UI's protocol layer own point of view: send
 * outbound text, be told about inbound text and the close. Two implementations exist - one over
 * {@code java.net.http.WebSocket} (a real socket), one in-process (no socket at all) - the protocol
 * layer above has no idea which one it is talking to.
 *
 * <p>Deliberately living in this module rather than in the debugger UI: it is the one shared type
 * an in-process transport (which must live here, alongside {@link CdpTransport} and the session)
 * can hand back to a UI client without this module ever depending on the UI - the dependency runs
 * the other way.
 */
public interface CdpClientChannel {

    /**
     * Registers where inbound frames and the close arrive. Called exactly once, before any {@link
     * #sendText(String)}.
     *
     * @param listener the listener
     */
    void listen(ChannelListener listener);

    /**
     * Sends one complete text message.
     *
     * @param text the message
     * @return a stage completing once the send succeeds or fails
     */
    CompletionStage<?> sendText(String text);

    /** Requests that the channel close. Idempotent; safe even if the peer is already gone. */
    void requestClose();

    /**
     * Where inbound frames and the close land. Callbacks may arrive on any thread - the caller of
     * {@link #listen(ChannelListener)} is responsible for any marshaling it needs.
     */
    interface ChannelListener {

        /**
         * One inbound frame, possibly a fragment - {@code last} says whether more is coming for the
         * same logical message.
         *
         * @param data the frame's text
         * @param last whether this completes the message
         */
        void onText(CharSequence data, boolean last);

        /**
         * The channel closed - cleanly, on error, or because it never opened. Fires exactly once.
         *
         * @param reason a short description
         */
        void onClosed(String reason);
    }
}
