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
 * A direct construction of a function declaration ({@code new C()}) in the operand of a yield or an
 * await: compiled code, not the fallback to the interpreter.
 */
class DirectNewInGeneratorTest {

    private static final String SCRIPT =
            "function C(x) { this.x = x; }"
                    + "function* gen() { var o = yield new C(4); var p = yield new C(o + 1); return p.x; }"
                    + "async function af() { var o = await new C(5); return o.x; }"
                    + "function* loop() { for (var i = 0; i < 3; i++) yield new C(i).x; }"
                    + "var g = gen(), r = [g.next().value.x, g.next(6).value.x, g.next({ x: 9 }).value];"
                    + "for (var v of loop()) r.push(v);"
                    + "af().then(function (v) { r.push(v); });";

    @Test
    void yieldAndAwaitOfADirectConstruction() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    List<String> warnings = new ArrayList<>();
                    ErrorReporter reporter =
                            new ErrorReporter() {
                                @Override
                                public void warning(
                                        String message,
                                        String sourceName,
                                        int line,
                                        String lineSource,
                                        int lineOffset) {
                                    warnings.add(message);
                                }

                                @Override
                                public void error(
                                        String message,
                                        String sourceName,
                                        int line,
                                        String lineSource,
                                        int lineOffset) {
                                    throw new AssertionError(message);
                                }

                                @Override
                                public EvaluatorException runtimeError(
                                        String message,
                                        String sourceName,
                                        int line,
                                        String lineSource,
                                        int lineOffset) {
                                    return new EvaluatorException(
                                            message, sourceName, line, lineSource, lineOffset);
                                }
                            };
                    Script script =
                            cx.compileScript(
                                    ScriptCompileSpec.fromSource(SCRIPT)
                                            .sourceName("new.js")
                                            .lineno(1)
                                            .compilationErrorReporter(reporter));
                    script.exec(cx, scope, scope);
                    cx.processMicrotasks();
                    assertTrue(warnings.isEmpty(), warnings.toString());
                    assertEquals(
                            "4,7,9,0,1,2,5",
                            cx.evaluateString(scope, "r.join()", "check.js", 1, null));
                    return null;
                });
    }
}
