/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ErrorReporter;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.ScriptCompileSpec;
import org.monflabs.mozilla.javascript.TopLevel;

/**
 * The class file limits of compiled mode: the 64K limit applies to the bytecode of a method, not to
 * its tables, and a script beyond a limit runs in the interpreter with a warning.
 */
class ClassFileLimitsTest {

    private static final class RecordingReporter implements ErrorReporter {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void warning(
                String message, String sourceName, int line, String lineSource, int lineOffset) {
            warnings.add(message);
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

    /** A function of one {@code if} per line: about 25 bytes of bytecode per statement. */
    private static String branches(int count) {
        return body("function f(x) {\n", count) + "f(1000);";
    }

    private static String body(String header, int count) {
        StringBuilder sb = new StringBuilder(header).append("  var s = 0;\n");
        for (int i = 0; i < count; i++) {
            sb.append("  if (x > ").append(i).append(") s++;\n");
        }
        return sb.append("  return s;\n}\n").toString();
    }

    private static List<String> run(int version, String source, int expected) {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(false);
            cx.setLanguageVersion(version);
            TopLevel scope = cx.initStandardObjects();
            RecordingReporter reporter = new RecordingReporter();
            Script script =
                    cx.compileScript(
                            ScriptCompileSpec.fromSource(source)
                                    .sourceName("big.js")
                                    .lineno(1)
                                    .compilationErrorReporter(reporter));
            assertEquals(expected, ((Number) script.exec(cx, scope, scope)).intValue());
            return reporter.warnings;
        }
    }

    /** 50 KB of bytecode, whose line number and stack map tables take the attribute over 64 KB. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void tablesDoNotCountInTheLimit(int version) {
        List<String> warnings = run(version, branches(2000), 1000);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void scriptBeyondALimitIsReported(int version) {
        // a function too large is split into smaller methods, but not a generator
        List<String> warnings =
                run(version, body("function* f(x) {\n", 3000) + "f(1000).next().value;", 1000);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("class file limit"), warnings.get(0));
    }

    /**
     * More invokedynamic sites than HotSpot can number in a class (65,535): a site got the linkage
     * of another one, or the JVM crashed. The script is split, then spread over several classes.
     */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void tooManyCallSitesAreSpreadOverClasses(int version) {
        StringBuilder sb = new StringBuilder("var x = 0;\n");
        for (int i = 0; i < 70000; i++) {
            sb.append("x++;\n");
        }
        List<String> warnings = run(version, sb.append("x;").toString(), 70000);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    /** More constants than a constant pool can hold (65,535 entries, a string takes two). */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void tooManyConstantsAreSpreadOverClasses(int version) {
        StringBuilder sb = new StringBuilder("var n = 0;\n");
        for (int i = 0; i < 40000; i++) {
            sb.append("n += 'k").append(i).append("'.length;\n");
        }
        // 'k0' to 'k9' are 2 long, 'k10' to 'k99' 3...
        List<String> warnings = run(version, sb.append("n;").toString(), 228890);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
