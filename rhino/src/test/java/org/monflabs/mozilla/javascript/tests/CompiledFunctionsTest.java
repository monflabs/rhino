/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ErrorReporter;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Compiled functions behave as the interpreter: a direct call of a function with a rest parameter,
 * a BigInt compared with a number, the __proto__ of an object literal, a strict comparison of a
 * parameter of a direct call with a number, and a yield in the arguments of a direct call.
 */
class CompiledFunctionsTest {

    @Test
    void directCallOfAFunctionWithARestParameter() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,1,xy,0,2",
                "function rest(...a) { return a; }"
                        + "function two(x, ...a) { return a.length; }"
                        + "function outer() {"
                        + "  var r = rest('xy');"
                        + "  return [Array.isArray(r), r.length, r[0], two(1), two(1, 2, 3)].join();"
                        + "}"
                        + "outer()");
    }

    @Test
    void bigIntComparedWithANumber() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true false true true true true true | true true true true true true false | b,a",
                "function lt(a, b) { return a < b; }"
                        + "function lt2(a) { return a < 2; }"
                        + "function gt2(a) { return 2 < a; }"
                        + "function local() { var x = 1n; return [x < 2, 2 > x, x <= 1, 1 >= x,"
                        + "  x > 0.5, 0.5 < x, x < NaN].join(' '); }"
                        + "function order() { var log = [];"
                        + "  var a = { valueOf() { log.push('a'); return 1; } };"
                        + "  a < (log.push('b'), 2); return log.join(); }"
                        + "function outer() {"
                        + "  return [[lt(1n, 2), lt(2, 1n), lt(1n, 2n), lt('1', 2n), lt2(1n), gt2(3n),"
                        + "    lt2('1')].join(' '), local(), order()].join(' | ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void protoOfAnObjectLiteralInAFunction() {
        String script =
                "function f() {"
                        + "  var o = {__proto__: 1}; var p = {__proto__: 2, a: 3}; var q; q = {__proto__: 4.5};"
                        + "  return [String(Object.getOwnPropertyDescriptor(o, '__proto__')),"
                        + "    Object.keys(p).join(), Object.getPrototypeOf(q) === Object.prototype].join();"
                        + "}"
                        + "f()";
        Utils.assertWithAllModes_ES6("undefined,a,true", script);
        Utils.assertWithAllModes_ECMASCRIPT("undefined,a,true", script);
    }

    @Test
    void strictComparisonOfADirectCallParameterWithANumber() {
        String script =
                "function f(n) { if (n === 0) return 0; return 1 + f(n - 1); }"
                        + "function g(n) { if (n !== 0) return 1 + g(n - 1); return 0; }"
                        + "function h(n) { var zero = n === 0; return zero ? 0 : 1 + h(n - 1); }"
                        + "function k(n) { return n === 1.5 ? 'x' : n === NaN ? 'nan' : 'y'; }"
                        + "[f(10), g(10), h(10), k(1.5), k(NaN), k(2)].join()";
        Utils.assertWithAllModes_ECMASCRIPT("10,10,10,x,y,y", script);
    }

    @Test
    void yieldInTheArgumentsOfADirectCall() {
        String script =
                "function log(x) { return 'v' + x; }"
                        + "function* g() { var a = log(yield 1); var b = log(yield 2); return a + b; }"
                        + "async function m() { return log(await 3) + log(await 4); }"
                        + "var it = g(); it.next(); it.next('a');"
                        + "it.next('b').value";
        Utils.assertWithAllModes_ECMASCRIPT("vavb", script);
        // compiled to classes, without falling back to the interpreter
        List<String> warnings = new ArrayList<>();
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInterpretedMode(false);
            cx.setErrorReporter(
                    new ErrorReporter() {
                        @Override
                        public void warning(
                                String message, String name, int line, String src, int offset) {
                            warnings.add(message);
                        }

                        @Override
                        public void error(
                                String message, String name, int line, String src, int offset) {
                            throw runtimeError(message, name, line, src, offset);
                        }

                        @Override
                        public EvaluatorException runtimeError(
                                String message, String name, int line, String src, int offset) {
                            return new EvaluatorException(message, name, line, src, offset);
                        }
                    });
            TopLevel scope = cx.initStandardObjects();
            Script compiled = cx.compileString(script, "test", 1, null);
            assertEquals("vavb", compiled.exec(cx, scope, scope.getGlobalThis()));
        }
        assertEquals(List.of(), warnings);
    }
}
