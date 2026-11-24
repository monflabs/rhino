/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.api.debugger.DebugListener;
import org.monflabs.mozilla.api.debugger.PausedEvent;

/** The playground's debug runs: paused at the first statement, resumed, stopped. */
class DebugRunTest {
    private ScriptRunner runner;

    @BeforeEach
    void start() {
        runner = new ScriptRunner();
    }

    @AfterEach
    void stop() {
        runner.close();
    }

    private static Sample sample(String source) {
        return new Sample("debug", List.of(), "Debug", source, null, Map.of());
    }

    @Test
    void pausesAtTheFirstStatementThenRuns() throws Exception {
        List<Integer> lines = new CopyOnWriteArrayList<>();
        runner.debugger()
                .addListener(
                        new DebugListener() {
                            @Override
                            public void paused(PausedEvent event) {
                                lines.add(event.frames().get(0).location().line());
                                event.resume();
                            }
                        });
        runner.debugger().pauseOnStart();
        String source = "var a = 1;\nprint('a is', a + 1);\n";
        Recorder recorder = new Recorder();
        runner.debug(sample(source), source, ScriptRunner.Settings.DEFAULT, recorder, recorder);
        ScriptRunner.Result result = recorder.await(30, "the debug run");
        assertTrue(result.ok(), String.valueOf(result.failure()));
        assertEquals("a is 2\n", recorder.out.toString());
        assertEquals(List.of(0), lines);
    }

    @Test
    void stopEndsAScriptPausedInTheDebugger() throws Exception {
        runner.debugger()
                .addListener(
                        new DebugListener() {
                            @Override
                            public void paused(PausedEvent event) {
                                new Thread(runner::stop).start();
                            }
                        });
        runner.debugger().pauseOnStart();
        String source = "print('never');\n";
        Recorder recorder = new Recorder();
        runner.debug(sample(source), source, ScriptRunner.Settings.DEFAULT, recorder, recorder);
        ScriptRunner.Result result = recorder.await(30, "the stopped debug run");
        assertTrue(result.terminated());
        assertNull(result.failure());
        assertEquals("", recorder.out.toString());
    }
}
