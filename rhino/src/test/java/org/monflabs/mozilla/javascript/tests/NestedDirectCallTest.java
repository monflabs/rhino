/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A call to a function declared in an enclosing function compiles to a direct call, with a regular
 * call when the name no longer holds that function.
 */
class NestedDirectCallTest {

    @Test
    void callsTheFunctionTheNameHolds() {
        String expected = "0.7118 -2.2251 inner,var,outer 55";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "function outer(n) {"
                        + "  function A(i, j) { return 1 / ((i + j) * (i + j + 1) / 2 + i + 1); }"
                        + "  function sum(n) {"
                        + "    var s = 0; for (var i = 0; i < n; i++) s += A(i, i + 1); return s; }"
                        + "  var r = sum(n);"
                        + "  if (n > 5) { A = function (i, j) { return -1; }; r += sum(3); }"
                        + "  return r.toFixed(4); }"
                        + "function shadow() {"
                        + "  function f() { return 'outer'; }"
                        + "  function inner() { function f() { return 'inner'; } return f(); }"
                        + "  function inner2() { var f = function () { return 'var'; }; return f(); }"
                        + "  return [inner(), inner2(), f()].join(); }"
                        + "function fib(n) {"
                        + "  function f(k) { return k < 2 ? k : f(k - 1) + f(k - 2); } return f(n); }"
                        + "[outer(4), outer(10), shadow(), fib(10)].join(' ')");
    }

    @Test
    void closureOfTheSameFunctionKeepsItsScope() {
        String expected = "1 2 1";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "function outer(k, other) {"
                        + "  function g() { return k; }"
                        + "  if (other) g = other;"
                        + "  function call() { return g(); }"
                        + "  return [call, g]; }"
                        + "var a = outer(1), b = outer(2), c = outer(2, a[1]);"
                        + "[a[0](), b[0](), c[0]()].join(' ')");
    }

    @Test
    void thisNewAndGenerators() {
        String expected = "true true 3 1";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "function o() {"
                        + "  function t() { return this === globalThis; }"
                        + "  function s() { 'use strict'; return this === undefined; }"
                        + "  function P(x) { this.x = x; }"
                        + "  function* g() { yield 1; }"
                        + "  return [t(), s(), new P(3).x, g().next().value].join(' '); }"
                        + "o()");
    }

    @Test
    void asyncFunctions() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "object 2",
                "function o() {"
                        + "  async function a(x) { return x; }"
                        + "  function b(x) { return a(x) instanceof Promise ? x : -1; }"
                        + "  return typeof a(1) + ' ' + b(2); }"
                        + "o()");
    }
}
