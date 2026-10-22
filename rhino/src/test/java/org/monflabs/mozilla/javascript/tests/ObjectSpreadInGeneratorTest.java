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
 * A yield or an await in the source of a spread in an object literal ({@code {...yield}}): compiled
 * code, not the fallback to the interpreter.
 */
class ObjectSpreadInGeneratorTest {

    private static final String SCRIPT =
            "function* gen() { return { ...yield, y: 1, ...yield yield }; }"
                    + "function* nested() { return { a: { ...(yield 1) }, ...[yield 2] }; }"
                    + "async function af(p) { return { b: 2, ...await p }; }"
                    + "var g = gen(); g.next(); g.next({ x: 42 }); g.next({ x: 'lol' });"
                    + "var o = g.next({ y: 39 }).value, r = [o.x, o.y, Object.keys(o).length];"
                    + "var n = nested(); n.next(); n.next({ c: 3 });"
                    + "o = n.next(4).value; r.push(o.a.c, o[0]);"
                    + "af(Promise.resolve({ b: 5 })).then(function (v) { r.push(v.b); });";

    @Test
    void yieldAndAwaitInASpread() {
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
                                            .sourceName("spread.js")
                                            .lineno(1)
                                            .compilationErrorReporter(reporter));
                    script.exec(cx, scope, scope);
                    cx.processMicrotasks();
                    assertTrue(warnings.isEmpty(), warnings.toString());
                    assertEquals(
                            "42,39,2,3,4,5",
                            cx.evaluateString(scope, "r.join()", "check.js", 1, null));
                    return null;
                });
    }
}
