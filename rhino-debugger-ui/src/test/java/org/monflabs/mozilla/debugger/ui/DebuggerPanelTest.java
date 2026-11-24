/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui;

import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertEquals;
import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertNotNull;
import static org.monflabs.mozilla.debugger.ui.test.TestNgAsserts.assertTrue;

import java.awt.Font;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.api.debugger.InspectOptions;
import org.monflabs.mozilla.debugger.CdpServer;
import org.monflabs.mozilla.debugger.ui.model.DebugSession;
import org.monflabs.mozilla.debugger.ui.test.TestEngine;

/**
 * The panel headless (no JFrame, as {@code java.awt.headless=true} requires): it composes, attaches
 * to a real server, reflects a paused run, and cleans up.
 */
@SuppressWarnings({"deprecation", "javadoc"})
public class DebuggerPanelTest {
    protected static final long TIMEOUT = 20;

    protected TestEngine engine;
    protected AutoCloseable server;
    protected ExecutorService worker;
    protected DebuggerPanel panel;

    @BeforeEach
    public void setUp() throws Exception {
        engine = new TestEngine();
        openServer();
        worker = Executors.newSingleThreadExecutor();
        onEdt(() -> panel = new DebuggerPanel(new Font(Font.MONOSPACED, Font.PLAIN, 12), false));
    }

    /** Starts the transport under test. Overridden for the in-process one. */
    protected void openServer() throws Exception {
        server = CdpServer.open(engine.debugger(), InspectOptions.parse("127.0.0.1:0", false));
    }

    /** Points the panel at the transport under test. */
    protected void attachPanel() throws Exception {
        onEdt(() -> panel.attach(((CdpServer.Handle) server).webSocketUrl()));
    }

    @AfterEach
    public void tearDown() throws Exception {
        onEdt(() -> panel.close());
        server.close();
        worker.shutdownNow();
        engine.debugger().close();
    }

    protected static void onEdt(final Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }

    /** A value read from the EDT. */
    protected static <T> T onEdtGet(final java.util.concurrent.Callable<T> c) throws Exception {
        final AtomicReference<T> ref = new AtomicReference<>();
        final AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        ref.set(c.call());
                    } catch (final Exception e) {
                        err.set(e);
                    }
                });
        if (err.get() != null) {
            throw err.get();
        }
        return ref.get();
    }

    protected void waitUntil(final EdtCondition condition) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
        while (System.nanoTime() < deadline) {
            if (onEdtGet(condition::holds)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("condition not met within " + TIMEOUT + "s");
    }

    protected interface EdtCondition {
        boolean holds();
    }

    @Test
    public void thePanelComposes() throws Exception {
        assertNotNull(panel);
        assertTrue(onEdtGet(() -> panel.getComponentCount() > 0));
    }

    @Test
    public void attachRunAndPauseReflectsInTheStack() throws Exception {
        final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
        onEdt(() -> panel.onConnectionChange((state, detail) -> connState.set(state)));
        attachPanel();
        waitUntil(() -> connState.get() == DebuggerPanel.ConnectionState.CONNECTED);

        // set a breakpoint by url and run a script reporting that url
        final String url = "file:///work/panel.js";
        onEdt(() -> panel.session().toggleBreakpoint(url, 1));
        final CompletableFuture<Object> done = new CompletableFuture<>();
        engine.put(TestEngine.FILENAME, url);
        worker.submit(
                () -> {
                    try {
                        done.complete(
                                engine.eval(
                                        "var a = 1;\na = a + 1;\na;\n//# sourceURL=" + url + "\n"));
                    } catch (final Throwable t) {
                        done.completeExceptionally(t);
                    }
                });

        waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
        assertEquals(
                onEdtGet(() -> panel.session().pauseState().frames().get(0).line()),
                Integer.valueOf(1));

        onEdt(() -> panel.session().resume());
        done.get(TIMEOUT, TimeUnit.SECONDS);
    }

    @Test
    public void detachWhilePausedReleasesTheScript() throws Exception {
        final AtomicReference<DebuggerPanel.ConnectionState> connState = new AtomicReference<>();
        onEdt(() -> panel.onConnectionChange((state, detail) -> connState.set(state)));
        attachPanel();
        waitUntil(() -> connState.get() == DebuggerPanel.ConnectionState.CONNECTED);

        final String url = "file:///work/hang.js";
        onEdt(() -> panel.session().toggleBreakpoint(url, 0));
        final CompletableFuture<Object> done = new CompletableFuture<>();
        engine.put(TestEngine.FILENAME, url);
        worker.submit(
                () -> {
                    try {
                        done.complete(engine.eval("var a = 1;\na;\n//# sourceURL=" + url + "\n"));
                    } catch (final Throwable t) {
                        done.completeExceptionally(t);
                    }
                });
        waitUntil(() -> panel.session().state() == DebugSession.State.PAUSED);
        onEdt(() -> panel.detach());
        done.get(TIMEOUT, TimeUnit.SECONDS); // released only if detach resumed
    }
}
