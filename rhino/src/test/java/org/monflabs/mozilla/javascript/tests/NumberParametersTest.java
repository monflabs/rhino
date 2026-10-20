/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The parameters of a nested function declaration that only direct calls passing numbers can call
 * are doubles in compiled code, and a direct call does not compute the this of a function that does
 * not read it.
 */
class NumberParametersTest {

    private static void assertAllVersions(String expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void numberArguments() {
        assertAllVersions(
                "3.3088 12 5,7 -1 NaN 0.5",
                "function outer() {"
                        + "  function A(i, j) { return 1 / ((i + j) * (i + j + 1) / 2 + i + 1); }"
                        + "  function sum(n) {"
                        + "    var s = 0;"
                        + "    for (var i = 0; i < n; i++) { for (var j = 0; j < n; j++) s += A(i, j); }"
                        + "    return s.toFixed(4);"
                        + "  }"
                        + "  function inc(k) { k++; k += 2; return k; }"
                        + "  function pair(a, b) { return [a + b, a * b + 1].join(); }"
                        + "  function neg(z) { z = -z; return z; }"
                        + "  function nan(x) { return x + 1; }"
                        + "  function half(h) { return h / 2; }"
                        + "  var two = 2, three = 3;"
                        + "  return [sum(4), inc(9), pair(two, three), neg(1), nan(0 / 0 * 1), half(1)]"
                        + "    .join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void argumentsOfAnyType() {
        assertAllVersions(
                "30 ab 12 1x 11 3 x1 function 5",
                "function outer() {"
                        + "  function add(a, b) { return a + b; }"
                        + "  function str(a) { a = a + 'x'; return a; }"
                        + "  function asValue(a) { return a + 1; }"
                        + "  function typed(a) { return a + 1; }"
                        + "  function fewer(a, b) { return a + (b === undefined ? 'u' : b); }"
                        + "  var f = asValue, n = 1;"
                        + "  var r = [add(n, 2) + '' + add(1, 1) - 2 + 0, add('a', 'b'), add(1, '2'),"
                        + "    str(1), f('1'), typed(2), typeof typed('x') === 'string' ? 'x1' : '?',"
                        + "    typeof asValue];"
                        + "  r.push(fewer(5, ''));"
                        + "  return r.join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void recursionAndCallsFromClosures() {
        assertAllVersions(
                "55 8 6",
                "function outer() {"
                        + "  function fib(n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }"
                        + "  function sq(x) { return x * x; }"
                        + "  var inner = function () { var k = 2; return sq(k) + sq(k); };"
                        + "  var arrow = (m) => sq(m) - 3;"
                        + "  return [fib(10), inner(), arrow(3)].join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void recursiveFunctions() {
        assertAllVersions(
                "55 true,false 7 3 1x21 1.5 7 x2 1111 4",
                "function outer() {"
                        + "  function fib(n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }"
                        + "  function even(n) { return n == 0 ? true : odd(n - 1); }"
                        + "  function odd(n) { return n == 0 ? false : even(n - 1); }"
                        + "  function tak(x, y, z) {"
                        + "    return y >= x ? z : tak(tak(x - 1, y, z), tak(y - 1, z, x), tak(z - 1, x, y));"
                        + "  }"
                        + "  function down(n) { if (n > 3) { n = n - 1; return down(n); } return n; }"
                        + "  function str(n, s) { return n == 0 ? s : str(n - 1, s + n); }"
                        + "  function half(n) { return n < 2 ? n : half(n / 2); }"
                        + "  function big(n) { return n > 5 ? n : big(n + 2); }"
                        + "  function mixed(n) { return n === 'x' ? n + 2 : mixed(n + 'x'); }"
                        + "  function rep(n, k) { return k == 0 ? n : rep(n + n, k - 1); }"
                        + "  function rep2(n, k) { return k == 0 ? n : rep2(n + n, k - 1); }"
                        + "  return [fib(10), [even(4), odd(4)], tak(18, 12, 6), down(9), str(2, '1x'),"
                        + "    half(3), big(1), mixed(''), rep(1 + '', 2), rep2(-(-1), 2)].join(' ');"
                        + "}"
                        + "outer()");
    }

    @Test
    void comparisonsOfNumbersAsArguments() {
        assertAllVersions(
                "true,false,boolean|true,boolean",
                "function f(x) { return x; }"
                        + "function g(a, b) { var i = 1, j = 2; return [f(i < j), f(a > b), typeof f(i <= j)]; }"
                        + "function outer() {"
                        + "  function h(x) { return x; }"
                        + "  var i = 1, j = 2;"
                        + "  return [h(i < j), typeof h(i >= j)];"
                        + "}"
                        + "g(1, 2).join() + '|' + outer().join()");
    }

    @Test
    void thisOfDirectCalls() {
        assertAllVersions(
                "true true true true 3",
                "function outer() {"
                        + "  function sloppy() { return this === globalThis; }"
                        + "  function strict() { 'use strict'; return this === undefined; }"
                        + "  function arrow() { return (() => this)() === globalThis; }"
                        + "  function viaEval() { return eval('this') === globalThis; }"
                        + "  function none(x) { return x + 1; }"
                        + "  return [sloppy(), strict(), arrow(), viaEval(), none(2)].join(' ');"
                        + "}"
                        + "outer()");
    }
}
