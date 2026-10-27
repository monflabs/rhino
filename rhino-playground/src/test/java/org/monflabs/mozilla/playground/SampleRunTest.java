/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every bundled sample runs on the engine it is bundled with, compiled and interpreted: the library
 * cannot drift from what the engine supports.
 */
class SampleRunTest {
    /** The samples that end in an error on purpose, and what the error says. */
    private static final Map<String, String> EXPECTED_FAILURES =
            Map.of("01 - Getting started/10 - Errors", "undefinedFunction");

    /** The samples that end in an error on purpose when compiled only. */
    private static final Map<String, String> EXPECTED_COMPILED_FAILURES =
            Map.of(
                    "02 - ECMAScript support/01 - ES2015/23 - Proper tail calls",
                    "Maximum call stack size exceeded");

    private static ScriptRunner runner;

    @BeforeAll
    static void start() {
        runner = new ScriptRunner();
    }

    @AfterAll
    static void stop() {
        runner.close();
    }

    @ParameterizedTest(name = "interpreted={0}")
    @ValueSource(booleans = {false, true})
    void everySampleRuns(boolean interpreted) throws IOException, InterruptedException {
        ScriptRunner.Settings settings =
                new ScriptRunner.Settings(ScriptRunner.Version.ES2026, interpreted);
        List<String> failures = new ArrayList<>();
        List<Sample> samples = SampleLibrary.load().samples();
        for (Sample sample : samples) {
            Recorder recorder = new Recorder();
            runner.run(sample, sample.source(), settings, recorder, recorder);
            ScriptRunner.Result result = recorder.await(60, sample.id());
            String expected = EXPECTED_FAILURES.get(sample.id());
            if (expected == null && !interpreted) {
                expected = EXPECTED_COMPILED_FAILURES.get(sample.id());
            }
            if (expected != null) {
                if (result.failure() == null
                        || !ScriptRunner.describe(result.failure()).contains(expected)) {
                    failures.add(
                            sample.id()
                                    + ": expected a failure mentioning "
                                    + expected
                                    + ", got "
                                    + (result.failure() == null
                                            ? "success"
                                            : ScriptRunner.describe(result.failure())));
                }
            } else if (!result.ok()) {
                failures.add(
                        sample.id()
                                + ": "
                                + (result.terminated()
                                        ? "terminated"
                                        : ScriptRunner.describe(result.failure()))
                                + (recorder.err.length() == 0 ? "" : "\nstderr: " + recorder.err));
            }
        }
        assertTrue(
                failures.isEmpty(),
                failures.size()
                        + " of "
                        + samples.size()
                        + " samples failed:\n"
                        + String.join("\n\n", failures));
    }

    @Test
    void helloPrints() throws IOException, InterruptedException {
        Sample hello = SampleLibrary.load().byId("01 - Getting started/01 - Hello");
        Recorder recorder = new Recorder();
        runner.run(hello, hello.source(), ScriptRunner.Settings.DEFAULT, recorder, recorder);
        assertTrue(recorder.await(30, "hello").ok());
        assertTrue(recorder.out.toString().startsWith("Hello, Rhino!\n"), recorder.out.toString());
        assertTrue(recorder.err.toString().contains("error"), recorder.err.toString());
    }

    @Test
    void theVersionIsTheOneChosen() throws InterruptedException {
        Sample sample =
                new Sample("x/y", List.of(), "y", "", null, Map.of())
                        .withSource(
                                "var r; try { eval('class A {}'); r = 'class'; }"
                                        + " catch (e) { r = e.name; } print(r, [].at(-1))");
        for (ScriptRunner.Version version : ScriptRunner.Version.values()) {
            Recorder recorder = new Recorder();
            runner.run(
                    sample,
                    sample.source(),
                    new ScriptRunner.Settings(version, false),
                    recorder,
                    recorder);
            assertTrue(recorder.await(30, version.label()).ok(), recorder.err.toString());
            assertEquals(
                    version == ScriptRunner.Version.ES2026
                            ? "class undefined\n"
                            : "SyntaxError undefined\n",
                    recorder.out.toString());
        }
    }

    @Test
    void importsResolveToTheSampleFiles() throws InterruptedException {
        Sample sample =
                new Sample(
                        "x/y",
                        List.of(),
                        "y",
                        "import { n } from './n.js';\nprint(n * 2);\n",
                        null,
                        Map.of("n.js", "export const n = 21;"));
        Recorder recorder = new Recorder();
        runner.run(sample, sample.source(), ScriptRunner.Settings.DEFAULT, recorder, recorder);
        ScriptRunner.Result result = recorder.await(30, "the module");
        assertTrue(result.ok(), () -> ScriptRunner.describe(result.failure()));
        assertEquals("42\n", recorder.out.toString());
    }

    @Test
    void timersAndPromisesRunBeforeTheEnd() throws InterruptedException {
        Sample sample =
                new Sample("x/y", List.of(), "y", "", null, Map.of())
                        .withSource(
                                "setTimeout(() => print('timer'), 10);"
                                        + " Promise.resolve().then(() => print('job'));"
                                        + " print('script');");
        Recorder recorder = new Recorder();
        runner.run(sample, sample.source(), ScriptRunner.Settings.DEFAULT, recorder, recorder);
        assertTrue(recorder.await(30, "the script").ok(), recorder.err.toString());
        assertEquals("script\njob\ntimer\n", recorder.out.toString());
    }
}
