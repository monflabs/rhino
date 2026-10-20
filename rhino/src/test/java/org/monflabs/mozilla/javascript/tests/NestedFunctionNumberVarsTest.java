/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Nested functions are optimized like top-level ones: their variables that only hold numbers live
 * in double registers in compiled code.
 */
class NestedFunctionNumberVarsTest {

    private static void assertAllVersions(String expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void numberVariables() {
        assertAllVersions(
                "3.3088 45 3 7,8,9 -Infinity 0.5 x 2 number 3",
                "function outer() {"
                        + "  function A(i, j) { return 1 / ((i + j) * (i + j + 1) / 2 + i + 1); }"
                        + "  function sum(n) {"
                        + "    var s = 0, t = 0;"
                        + "    for (var i = 0; i < n; i++) { for (var j = 0; j < n; j++) s += A(i, j); }"
                        + "    for (var k = 0; k < 10; ++k) t += k;"
                        + "    return [s.toFixed(4), t];"
                        + "  }"
                        + "  function idx(a) {"
                        + "    var r = [], o = {1: 'x'};"
                        + "    for (var i = 0; i < a.length; i++) r[i] = a[i] + 1;"
                        + "    var h = 3, z = -0;"
                        + "    return [h, r.join(), 1 / z, h / 6, o[1], h - 1, typeof h];"
                        + "  }"
                        + "  var n = 0; function g() { var m = 1; m = m + 2; n = m; }"
                        + "  g();"
                        + "  return sum(4).concat(idx([6, 7, 8]), n).join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void variablesThatDoNotOnlyHoldNumbers() {
        assertAllVersions(
                "NaN 01 2 a1 3ab 10 true 1,2",
                "function outer() {"
                        + "  function f() {"
                        + "    var u; u++;"
                        + "    var s = 0; s = s + '1';"
                        + "    var t = '1'; t++;"
                        + "    var v = 1; if (t > 1) v = 'a' + v;"
                        + "    return [u, s, t, v];"
                        + "  }"
                        + "  function add(a, b) { return a + b; }"
                        + "  function cmp(x) { var y = x; return y == 10; }"
                        + "  var r = f();"
                        + "  r.push(add(1, 2) + add('a', 'b'), add(4, 6), cmp('10'), add([1], [',2']));"
                        + "  return r.join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void bigInts() {
        assertAllVersions(
                "1024 bigint 6 TypeError",
                "function outer() {"
                        + "  function pow() { var b = 1n; for (var i = 0; i < 10; i++) b = b * 2n; return b; }"
                        + "  function mul(x, y) { return x * y; }"
                        + "  var r = [pow(), typeof pow(), mul(2n, 3n)];"
                        + "  try { mul(2n, 3); } catch (e) { r.push(e.name); }"
                        + "  return r.join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void bigIntParametersOfTopLevelFunctions() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "6 -1 TypeError",
                "function mul(x, y) { return x * y; }"
                        + "function sub(x, y) { return x - (y + 0n); }"
                        + "function half(x) { return x / 2; }"
                        + "var r = [mul(2n, 3n), sub(2n, 3n)];"
                        + "try { half(2n); } catch (e) { r.push(e.name); }"
                        + "r.join(' ')");
    }
}
