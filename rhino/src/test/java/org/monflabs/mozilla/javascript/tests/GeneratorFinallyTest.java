/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ErrorReporter;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.ScriptCompileSpec;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A return out of nested try statements of a generator or an async function, such as a for-of loop
 * whose head is a destructuring pattern: compiled code, not the fallback to the interpreter.
 */
class GeneratorFinallyTest {

    private static final String SCRIPT =
            "var log = [];"
                    + "function it(n) { var i = 0; return { [Symbol.iterator]() { return this; },"
                    + "  next() { return { value: [i, i * 10], done: i++ >= n }; },"
                    + "  return() { log.push('closed'); return {}; } }; }"
                    + "function* gen() { for (var [a, b] of it(3)) { if (a == 1) return b; } }"
                    + "async function af() { for (var [a] of it(3)) { if (a == 2) return a; } }"
                    + "async function* ag() { for await (var [a, b] of it(3)) { if (a == 1) { yield b; return; } } }"
                    + "function* nested() { try { try { for (var [x] of it(2)) { return x; } }"
                    + "  finally { log.push('inner'); } } finally { log.push('outer'); } }"
                    + "var r = [gen().next().value, nested().next().value];"
                    + "af().then(function (v) { r.push(v); });"
                    + "var g = ag(); g.next().then(function (s) { r.push(s.value); return g.next(); })"
                    + "  .then(function (s) { r.push(s.done); });";

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

    @Test
    void returnThroughNestedFinallyBlocks() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    RecordingReporter reporter = new RecordingReporter();
                    Script script =
                            cx.compileScript(
                                    ScriptCompileSpec.fromSource(SCRIPT)
                                            .sourceName("gen.js")
                                            .lineno(1)
                                            .compilationErrorReporter(reporter));
                    script.exec(cx, scope, scope);
                    cx.processMicrotasks();
                    assertTrue(reporter.warnings.isEmpty(), reporter.warnings.toString());
                    assertEquals(
                            "10,0,2,10,true | closed,closed,inner,outer,closed,closed",
                            cx.evaluateString(
                                    scope, "r.join() + ' | ' + log.join()", "check.js", 1, null));
                    return null;
                });
    }
}
