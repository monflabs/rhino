/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.debugger.CdpClientChannel;
import org.monflabs.mozilla.debugger.ui.model.DebugSession;

/** The callbacks of a connection that run after the session attached to another one. */
class DebugSessionReattachTest {

    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final DebugSession session = new DebugSession(tasks::add);

    /** A channel whose server answers every request with an empty result. */
    private static final class Channel implements CdpClientChannel {
        ChannelListener listener;
        final List<String> sent = new ArrayList<>();

        @Override
        public void listen(ChannelListener l) {
            listener = l;
        }

        @Override
        public CompletionStage<?> sendText(String text) {
            sent.add(text);
            String id = text.replaceFirst(".*\"id\":\\s*(\\d+).*", "$1");
            listener.onText("{\"id\":" + id + ",\"result\":{}}", true);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void requestClose() {}
    }

    private void pump() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    @Test
    void closeOfThePreviousConnection() {
        session.attach(new Channel());
        pump();
        assertEquals(DebugSession.State.RUNNING, session.state());
        session.detach();
        // the close of the first connection is still queued
        Channel second = new Channel();
        session.attach(second);
        pump();
        assertEquals(DebugSession.State.RUNNING, session.state());
        int before = second.sent.size();
        session.toggleBreakpoint("file:///a.js", 1);
        assertTrue(second.sent.size() > before, "the new connection is in use");
    }

    @Test
    void eventOfThePreviousConnection() {
        Channel first = new Channel();
        session.attach(first);
        pump();
        first.listener.onText(
                "{\"method\":\"Debugger.paused\",\"params\":{\"callFrames\":[]}}", true);
        session.detach();
        session.attach(new Channel());
        pump();
        assertEquals(DebugSession.State.RUNNING, session.state());
    }

    @Test
    void closeOfTheCurrentConnection() {
        Channel channel = new Channel();
        session.attach(channel);
        pump();
        channel.listener.onClosed("gone");
        pump();
        assertEquals(DebugSession.State.DETACHED, session.state());
    }
}
