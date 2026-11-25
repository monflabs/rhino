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
import java.util.concurrent.TimeUnit;
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

    /** A front end that stops the first run at its first pause, and resumes the others. */
    @Test
    void aRunAfterAStoppedOneIsNotStopped() throws Exception {
        List<String> pauses = new CopyOnWriteArrayList<>();
        List<Boolean> interrupts = new CopyOnWriteArrayList<>();
        runner.debugger()
                .addListener(
                        new DebugListener() {
                            @Override
                            public void paused(PausedEvent event) {
                                pauses.add(event.reason().name());
                                interrupts.add(Thread.currentThread().isInterrupted());
                                if (pauses.size() == 1) {
                                    runner.stop();
                                    // terminated, not interrupted
                                    interrupts.add(Thread.currentThread().isInterrupted());
                                } else {
                                    event.resume();
                                }
                            }
                        });
        String source = "print('run');\n";
        for (int i = 0; i < 3; i++) {
            runner.debugger().pauseOnStart();
            Recorder recorder = new Recorder();
            runner.debug(sample(source), source, ScriptRunner.Settings.DEFAULT, recorder, recorder);
            ScriptRunner.Result result = recorder.await(30, "debug run " + i);
            assertNull(result.failure());
            assertEquals(i == 0, result.terminated());
            assertEquals(i == 0 ? "" : "run\n", recorder.out.toString());
        }
        assertEquals(List.of("START", "START", "START"), pauses);
        assertEquals(List.of(false, false, false, false), interrupts);
    }

    @Test
    void aDebugRunAfterAnInterruptedRun() throws Exception {
        String sleeping = "setTimeout(() => print('late'), 3600000);";
        Recorder stopped = new Recorder();
        runner.run(sample(sleeping), sleeping, ScriptRunner.Settings.DEFAULT, stopped, stopped);
        assertTrue(stopped.started.await(30, TimeUnit.SECONDS));
        Thread.sleep(200);
        runner.stop();
        assertTrue(stopped.await(30, "the stopped run").terminated());
        pausesAtTheFirstStatementThenRuns();
    }
}
