/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui;

import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.inprocess.InProcessCdpServer;
import org.monflabs.mozilla.debugger.ui.model.DebugSession;
import org.monflabs.mozilla.debugger.ui.test.TestEngine;

/**
 * {@link DebuggerPanelTest}'s own scenarios, proving the whole stack - the engine's debugger
 * through {@link InProcessCdpServer}, the connection, the session and the panel - works headlessly,
 * in one JVM, with no socket bound anywhere. The last test is exactly the sequence the playground's
 * Debug button uses.
 */
@SuppressWarnings({"deprecation", "javadoc"})
public class InProcessDebuggerPanelTest extends DebuggerPanelTest {

    @Override
    protected void openServer() {
        server = InProcessCdpServer.open(engine.debugger(), InspectOptions.parse("", false));
    }

    @Override
    protected void attachPanel() throws Exception {
        onEdt(() -> panel.attach(((InProcessCdpServer.Handle) server).clientChannel()));
    }

    /**
     * The playground's Debug flow exactly: ask for a pause at the very first statement, attach, and
     * start the script only once the panel is connected. {@code Debugger.enable} does not replay an
     * already-running pause, so a pause requested before any client exists must still reach the
     * client that attaches afterwards.
     */
    @Test
    public void pauseOnStartBeforeAttachIsDeliveredOnceConnected() throws Exception {
        final String url = "file:///work/onstart.js";
        final CompletableFuture<Object> done = new CompletableFuture<>();
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
        engine.debugger().pauseOnStart();
        onEdt(
                () ->
                        panel.onConnectionChange(
                                (state, detail) -> {
                                    connState.set(state);
                                    if (state == DebuggerPanel.ConnectionState.CONNECTED
                                            && started.compareAndSet(false, true)) {
                                        engine.put(TestEngine.FILENAME, url);
                                        worker.submit(
                                                () -> {
                                                    try {
                                                        done.complete(
                                                                engine.eval(
                                                                        "var a = 1;\na = a + 1;\na;\n//# sourceURL="
                                                                                + url
                                                                                + "\n"));
                                                    } catch (final Throwable t) {
                                                        done.completeExceptionally(t);
                                                    }
                                                });
                                    }
                                }));
        attachPanel();

        waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
        assertEquals(
                onEdtGet(() -> panel.session().pauseState().frames().get(0).line()),
                Integer.valueOf(0));

        onEdt(() -> panel.session().resume());
        done.get(TIMEOUT, TimeUnit.SECONDS);
    }
}
