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
 * A yield or an await in the arguments of a call ({@code r.push(await x)}): compiled code, not the
 * fallback to the interpreter.
 */
class CallWithYieldArgumentTest {

    private static final String SCRIPT =
            "var o = { f(a, b) { return a + b; } }, r = [];"
                    + "function* gen() { r.push(yield 1); return o.f(yield, o.f(1, yield)); }"
                    + "async function af(p) { r.push(Math.max(1, await p), o?.f(await p, 1)); }"
                    + "var g = gen(); g.next(); g.next(3); g.next(10); r.push(g.next(5).value);"
                    + "af(Promise.resolve(7));";

    @Test
    void yieldAndAwaitInTheArguments() {
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
                                            .sourceName("call.js")
                                            .lineno(1)
                                            .compilationErrorReporter(reporter));
                    script.exec(cx, scope, scope);
                    cx.processMicrotasks();
                    assertTrue(warnings.isEmpty(), warnings.toString());
                    assertEquals(
                            "3,16,7,8", cx.evaluateString(scope, "r.join()", "check.js", 1, null));
                    return null;
                });
    }
}
