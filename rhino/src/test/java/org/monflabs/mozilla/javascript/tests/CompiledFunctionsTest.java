/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Compiled functions behave as the interpreter: a direct call of a function with a rest parameter,
 * a BigInt compared with a number, and the __proto__ of an object literal.
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
}
