/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A nested function declaration that only direct calls can call and that always returns a number
 * returns it as a double in compiled code.
 */
class NumberReturnsTest {

    private static void assertAllVersions(String expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void numberReturns() {
        assertAllVersions(
                "55 7 true,false 13 2.5 x3 3 number 1,2 5 -0 NaN",
                "function outer() {"
                        + "  function fib(n) { if (n < 2) return n; return fib(n - 1) + fib(n - 2); }"
                        + "  function tak(x, y, z) {"
                        + "    return y >= x ? z : tak(tak(x - 1, y, z), tak(y - 1, z, x), tak(z - 1, x, y));"
                        + "  }"
                        + "  function even(n) { return n == 0 ? 1 : odd(n - 1); }"
                        + "  function odd(n) { return n == 0 ? 0 : even(n - 1); }"
                        + "  function sq(x) { return x * x; }"
                        + "  function half(x) { return x / 2; }"
                        + "  function three() { return 3; }"
                        + "  function count() { var c = 0; for (var i = 0; i < 5; i++) c++; return c; }"
                        + "  function negZero() { return -0; }"
                        + "  function nan(s) { return +s; }"
                        + "  var r = [fib(10), tak(18, 12, 6), [!!even(4), !!odd(4)], sq(3) + sq(2), half(5),"
                        + "    'x' + three(), three(), typeof three(), [three() - 2, half(4)]];"
                        + "  three();"
                        + "  if (three()) r.push(count());"
                        + "  r.push(1 / negZero() < 0 ? '-0' : '0', nan('x'));"
                        + "  return r.join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void returnsOfAnyType() {
        assertAllVersions(
                "ab 1   2 x 3n 4  5",
                "function outer() {"
                        + "  function cat(a, b) { return a + b; }"
                        + "  function sometimes(n) { if (n > 0) return n; }"
                        + "  function nothing() { return; }"
                        + "  function inTry(n) { try { return n; } finally { n++; } }"
                        + "  function str(n) { return n > 0 ? 'x' : 1; }"
                        + "  function big(n) { return n * n; }"
                        + "  function ret(n) { return n; }"
                        + "  function obj(n) { return n == 4 ? { valueOf: function () { return 4; } } : null; }"
                        + "  function rec(n) { return n == 0 ? 5 : rec(n - 1); }"
                        + "  return [cat('a', 'b'), sometimes(1), sometimes(0), nothing(), inTry(2), str(1),"
                        + "    big(3n) / 3n + 'n', +obj(4), obj(0), rec('3')].join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void callsFromFunctionsNotOptimized() {
        assertAllVersions(
                "6 number 4",
                "function outer() {"
                        + "  function sq(x) { return x * x; }"
                        + "  var v = sq(2);"
                        + "  var f = function () { return v; };"
                        + "  return [sq(2) + f() - 2, typeof sq(1), f()].join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void scriptStack() {
        assertAllVersions(
                "boom 5",
                "function outer() {"
                        + "  function down(n) {"
                        + "    if (n == 0) throw new Error('boom');"
                        + "    return down(n - 1) + 1;"
                        + "  }"
                        + "  try { down(3); } catch (e) {"
                        + "    return e.message + ' ' + e.stack.split('(down)').length;"
                        + "  }"
                        + "}"
                        + "outer()");
    }
}
