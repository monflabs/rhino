/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.monflabs.mozilla.javascript.CompilationResult;
import org.monflabs.mozilla.javascript.CompilerEnvirons;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ErrorReporter;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.JSScript;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.ScriptCompileSpec;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.ast.ScriptNode;
import org.monflabs.mozilla.javascript.optimizer.Codegen;

/**
 * A script with generators that the class compiler fails on runs in the interpreter, and the
 * compilation reports a warning that says so; with {@code rhino.failOnCompileFallback}, which the
 * test suites set, the compilation fails instead.
 */
class CompilerFallbackTest {

    /** A class compiler with a bug. */
    private static final class FailingCodegen extends Codegen {
        @Override
        public CompilationResult<JSScript> compileScript(
                CompilerEnvirons compilerEnv, ScriptNode tree, String rawSource) {
            throw new IllegalStateException("broken");
        }
    }

    /** A class compiler that generates a class the JVM rejects. */
    private static final class UnverifiableCodegen extends Codegen {
        @Override
        public Script createScriptObject(
                CompilationResult<JSScript> compiled, Object staticSecurityDomain) {
            throw new VerifyError("broken");
        }
    }

    private static final class RecordingReporter implements ErrorReporter {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void warning(
                String message, String sourceName, int line, String lineSource, int lineOffset) {
            warnings.add(sourceName + ":" + line + ": " + message);
        }

        @Override
        public void error(
                String message, String sourceName, int line, String lineSource, int lineOffset) {
            throw new AssertionError(message);
        }

        @Override
        public EvaluatorException runtimeError(
                String message, String sourceName, int line, String lineSource, int lineOffset) {
            return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
        }
    }

    private static Script compile(Context cx, boolean verifyError, ErrorReporter reporter) {
        return cx.compileScript(
                ScriptCompileSpec.fromSource(
                                "function* g() { yield 1; yield 2; }"
                                        + " var s = 0; for (var v of g()) s += v; s")
                        .sourceName("gen.js")
                        .lineno(1)
                        .compiler(verifyError ? new UnverifiableCodegen() : new FailingCodegen())
                        .compilationErrorReporter(reporter));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fallbackToTheInterpreterIsReported(boolean verifyError) {
        String failOnFallback = System.setProperty("rhino.failOnCompileFallback", "false");
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(false);
            TopLevel scope = cx.initStandardObjects();
            RecordingReporter reporter = new RecordingReporter();
            Script script = compile(cx, verifyError, reporter);
            assertEquals(3, ((Number) script.exec(cx, scope, scope)).intValue());
            assertEquals(1, reporter.warnings.size());
            String warning = reporter.warnings.get(0);
            assertTrue(warning.startsWith("gen.js:1: "), warning);
            assertTrue(warning.contains("broken") && warning.contains("interpreter"), warning);
        } finally {
            if (failOnFallback == null) {
                System.clearProperty("rhino.failOnCompileFallback");
            } else {
                System.setProperty("rhino.failOnCompileFallback", failOnFallback);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fallbackFailsInTheTests(boolean verifyError) {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(false);
            RecordingReporter reporter = new RecordingReporter();
            IllegalStateException e =
                    assertThrows(
                            IllegalStateException.class, () -> compile(cx, verifyError, reporter));
            assertTrue(e.getMessage().startsWith("gen.js:1: "), e.getMessage());
            assertTrue(reporter.warnings.isEmpty(), reporter.warnings.toString());
        }
    }
}
