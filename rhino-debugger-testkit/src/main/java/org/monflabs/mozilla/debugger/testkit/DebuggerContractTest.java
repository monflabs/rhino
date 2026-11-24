/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.monflabs.mozilla.api.debugger.BreakpointRequest;
import org.monflabs.mozilla.api.debugger.DebugListener;
import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.api.debugger.PauseOnExceptions;
import org.monflabs.mozilla.api.debugger.PausedEvent;

/**
 * The contract of the neutral debugger API ({@code org.monflabs.mozilla.api.debugger}), for any
 * engine that implements it: Rhino's own debugger, or the debugger of another runtime. Extend it,
 * implement {@link #createFixture()}, and the tests run against your debugger.
 *
 * <p>Line numbers are zero-based. A script run under the name {@code x.js} is known to the debugger
 * by that name, which is what a breakpoint by url matches.
 */
@Timeout(30)
public abstract class DebuggerContractTest {

    /** What a debugger under test offers the contract: the debugger and a way to run a script. */
    public interface Fixture extends AutoCloseable {
        /** The debugger attached to the engine, before any script has run. */
        Debugger debugger();

        /**
         * Runs a script to completion on the calling thread, in a fresh global, under that name.
         *
         * @param source the script text
         * @param name the script's name, as the debugger reports it
         * @return the completion value
         */
        Object run(String source, String name);

        @Override
        void close();
    }

    /**
     * Creates the engine and its debugger, one per test.
     *
     * @return the fixture
     */
    protected abstract Fixture createFixture();

    private Fixture fixture;
    private Debugger debugger;
    private final List<String> log = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        fixture = createFixture();
        debugger = fixture.debugger();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Object run(String source, String name) {
        return fixture.run(source, name);
    }

    private void onPause(Consumer<PausedEvent> handler) {
        debugger.addListener(
                new DebugListener() {
                    @Override
                    public void paused(PausedEvent event) {
                        log.add(
                                "paused:"
                                        + event.reason()
                                        + ":"
                                        + event.frames().get(0).location().line());
                        handler.accept(event);
                    }
                });
    }

    @Test
    public void breakpointPausesAndReadsLocals() {
        List<Object> seen = new ArrayList<>();
        onPause(
                e -> {
                    try {
                        seen.add(e.frames().get(0).evaluate("x"));
                    } catch (Exception ex) {
                        seen.add(ex);
                    }
                    e.resume();
                });
        debugger.setBreakpoint(BreakpointRequest.at("t.js", 2));
        Object result = run("function f(a) {\n  var x = a + 1;\n  return x;\n}\nf(4);", "t.js");
        assertEquals(5, ((Number) result).intValue());
        assertEquals(List.of("paused:BREAKPOINT:2"), log);
        assertEquals(1, seen.size());
        assertEquals(5, ((Number) seen.get(0)).intValue(), seen.toString());
    }

    @Test
    public void conditionalBreakpoint() {
        onPause(PausedEvent::resume);
        debugger.setBreakpoint(new BreakpointRequest("c.js", null, null, 2, -1, "i == 2"));
        run("var s = 0;\nfor (var i = 0; i < 4; i++) {\n  s += i;\n}", "c.js");
        assertEquals(1, log.size());
    }

    @Test
    public void stepOverAndInto() {
        onPause(
                e -> {
                    if (log.size() < 3) {
                        e.stepInto();
                    } else {
                        e.resume();
                    }
                });
        debugger.pauseOnStart();
        run("var a = 1;\nvar b = 2;\nvar c = 3;\nvar d = 4;", "s.js");
        assertEquals("paused:START:0", log.get(0));
        assertEquals("paused:STEP:1", log.get(1));
        assertEquals("paused:STEP:2", log.get(2));
    }

    @Test
    public void pauseOnUncaughtException() {
        onPause(PausedEvent::resume);
        debugger.setPauseOnExceptions(PauseOnExceptions.UNCAUGHT);
        try {
            run("function f() { throw new Error('boom'); }\nf();", "e.js");
        } catch (RuntimeException expected) {
            // thrown to the caller
        }
        assertTrue(log.stream().anyMatch(s -> s.startsWith("paused:EXCEPTION")), log.toString());
        assertEquals(1, debugger.scripts().size());
    }

    @Test
    public void terminateStopsTheScript() {
        onPause(PausedEvent::terminate);
        debugger.setBreakpoint(BreakpointRequest.at("k.js", 0));
        boolean terminated = false;
        try {
            run("var a = 1;", "k.js");
        } catch (Throwable t) {
            terminated = t instanceof org.monflabs.mozilla.api.debugger.ScriptTerminated;
        }
        assertTrue(terminated);
    }

    @Test
    public void scriptsAreListedAndBreakpointsCanBeInactive() {
        debugger.setBreakpoint(BreakpointRequest.at("l.js", 0));
        debugger.setBreakpointsActive(false);
        run("var a = 1;", "l.js");
        assertFalse(debugger.scripts().isEmpty());
        assertNotNull(debugger.scripts().get(0).source());
        assertTrue(log.isEmpty());
    }

    @Test
    public void traceListenerSeesStatementsAndContextEvaluates() throws Exception {
        List<Integer> lines = new CopyOnWriteArrayList<>();
        debugger.addTraceListener(
                new org.monflabs.mozilla.api.debugger.TraceListener() {
                    @Override
                    public void statementReached(
                            org.monflabs.mozilla.api.debugger.DebugScript script,
                            int line,
                            int column,
                            int depth) {
                        lines.add(line);
                    }
                });
        run("var a = 1;\nvar b = a + 1;", "tr.js");
        assertEquals(List.of(0, 1), lines);
        var context = debugger.executionContexts().get(0);
        assertEquals(2, ((Number) debugger.evaluate(context, "b")).intValue());
        debugger.clearScripts();
        assertTrue(debugger.scripts().isEmpty());
    }

    @Test
    public void scopesOfAPausedFunction() {
        List<String> types = new ArrayList<>();
        onPause(
                e -> {
                    e.frames().get(0).scopes().forEach(sc -> types.add(sc.type().name()));
                    e.resume();
                });
        debugger.setBreakpoint(BreakpointRequest.at("sc.js", 1));
        run("function f(a) {\n  return a;\n}\nf(1);", "sc.js");
        assertTrue(types.contains("GLOBAL"), types.toString());
    }
}
