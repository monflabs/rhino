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
 * A function too large for a class file is split into smaller methods (Splitter) and runs compiled,
 * with the same results as unsplit code: returns, breaks and continues across the methods, try
 * statements, closures, this, arguments and stack traces.
 */
class MethodSplittingTest {

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

    /** Statements on one line each, about 2 KB of bytecode per 100. */
    private static String filler(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("if (n > ").append(i).append(") t++;\n");
        }
        return sb.toString();
    }

    private static Object run(int version, String source, boolean interpreted) {
        try (Context cx = Context.enter()) {
            cx.setInterpretedMode(interpreted);
            cx.setLanguageVersion(version);
            TopLevel scope = cx.initStandardObjects();
            RecordingReporter reporter = new RecordingReporter();
            Script script =
                    cx.compileScript(
                            ScriptCompileSpec.fromSource(source)
                                    .sourceName("split.js")
                                    .lineno(1)
                                    .compilationErrorReporter(reporter));
            Object result = Context.toString(script.exec(cx, scope, scope));
            assertTrue(reporter.warnings.isEmpty(), reporter.warnings.toString());
            return result;
        }
    }

    /** Runs a script compiled and interpreted: same result, and no fallback when compiled. */
    private static void assertRuns(int version, String expected, String source) {
        assertEquals(expected, run(version, source, true));
        assertEquals(expected, run(version, source, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void functionBeyondTheMethodLimit(int version) {
        assertRuns(
                version,
                "3000",
                "function f(n) { var t = 0;\n" + filler(3000) + "return t; }\nf(5000);");
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void loopBeyondTheJumpLimit(int version) {
        assertRuns(
                version,
                "3000",
                "function f(n) { var t = 0;\n"
                        + "for (var k = 0; k < 2; k++) {\n"
                        + filler(1500)
                        + "}\nreturn t; }\nf(5000);");
    }

    /** A single statement too large, split from the innermost else part out. */
    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void elseIfChain(int version) {
        StringBuilder sb = new StringBuilder("function f(n) { var r = 'none';\n");
        for (int i = 0; i < 2000; i++) {
            sb.append(i == 0 ? "" : "else ").append("if (n == ").append(i).append(") {\n");
            sb.append("  r = 'v").append(i).append("'; if (n == 1500) return r + '!';\n}\n");
        }
        sb.append("return r; }\n[f(3), f(1500), f(1999), f(5000)].join();");
        assertRuns(version, "v3,v1500!,v1999,none", sb.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void returnsAndJumpsAcrossTheChunks(int version) {
        String source =
                "function f(n, stop) { var t = 0, log = [];\n"
                        + filler(1000)
                        + "outer: for (var i = 0; i < 4; i++) {\n"
                        + "  for (var j = 0; j < 4; j++) {\n"
                        + filler(300)
                        + "    if (j == 1) continue;\n"
                        + "    if (j == 3) continue outer;\n"
                        + "    if (i == 2) break outer;\n"
                        + "    log.push(i + '' + j);\n"
                        + "  }\n"
                        + "}\n"
                        + "if (stop) { log.push('stop'); return log.join(); }\n"
                        + filler(1000)
                        + "try {\n"
                        + filler(500)
                        + "  return log.join() + ':' + t;\n"
                        + "} finally { log.push('finally'); }\n"
                        + "}\n"
                        + "f(5000, true) + ' ' + f(5000, false);";
        assertRuns(version, "00,02,10,12,stop 00,02,10,12:5200", source);
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void bindingsAndThisAcrossTheChunks(int version) {
        String source =
                "function f(n) { var t = 0;\n"
                        + "var self = this, fs = [];\n"
                        + filler(1500)
                        + "var count = 0; function inc() { return ++count; }\n"
                        + "fs.push(function () { return t; });\n"
                        + filler(1500)
                        + "inc(); inc();\n"
                        + "return [self === this, this.v, arguments.length, arguments[1],"
                        + " fs[0](), inc(), typeof g].join(); }\n"
                        + "f.call({ v: 7 }, 5000, 'b');";
        assertRuns(version, "true,7,2,b,3000,3,undefined", source);
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ECMASCRIPT})
    void lexicalBindingsAcrossTheChunks(int version) {
        String source =
                "function f(n) { let t = 0; const k = 2;\n"
                        + filler(1500)
                        + "let late;\n"
                        + "var fs = []; for (let i = 0; i < 3; i++) fs.push(() => i * k);\n"
                        + filler(1500)
                        + "var tdz; try { before; } catch (e) { tdz = e.name; } let before = 1;\n"
                        + "return [t, fs.map(g => g()).join(''), late, tdz].join(); }\n"
                        + "f(5000);";
        assertRuns(version, "3000,024,,ReferenceError", source);
    }

    @ParameterizedTest
    @ValueSource(ints = {Context.VERSION_ES6, Context.VERSION_ECMASCRIPT})
    void stackTraceShowsTheFunction(int version) {
        String source =
                "function f(n) { var t = 0;\n"
                        + filler(3000)
                        + "try { null.x; } catch (e) { return e.stack.split('\\n')[0].trim(); } }\n"
                        + "f(5000);";
        assertRuns(version, "at split.js:3002 (f)", source);
    }
}
