/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Stop ends a script that never returns, whatever it is doing. */
class StopTest {
    private static ScriptRunner runner;

    @BeforeAll
    static void start() {
        runner = new ScriptRunner();
    }

    @AfterAll
    static void stop() {
        runner.close();
    }

    @ParameterizedTest(name = "{0}, interpreted={1}")
    @CsvSource({
        "'for (;;) {}', false",
        "'for (;;) {}', true",
        "'for (;;) { try { for (;;) {} } catch (e) { print(e); } finally { } }', false",
        "'for (;;) { try { for (;;) {} } catch (e) { print(e); } finally { } }', true",
        "'function f() { return f2(); } function f2() { for (;;) {} } f();', false",
        "'setTimeout(() => print(\"late\"), 3600000);', false",
        "'java.lang.Thread.sleep(3600000);', false",
    })
    void stopEndsTheScript(String script, boolean interpreted) throws InterruptedException {
        Sample sample = new Sample("x/y", List.of(), "y", script, null, Map.of());
        Recorder recorder = new Recorder();
        runner.run(
                sample,
                script,
                new ScriptRunner.Settings(ScriptRunner.Version.ES2026, interpreted),
                recorder,
                recorder);
        assertTrue(recorder.started.await(30, TimeUnit.SECONDS));
        Thread.sleep(200);
        runner.stop();
        ScriptRunner.Result result = recorder.await(30, script);
        assertTrue(result.terminated());
        assertNull(result.failure());
        assertTrue(recorder.out.isEmpty(), recorder.out.toString());
    }
}
