/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A function with an activation object (because of a closure, a catch clause, ...) keeps in
 * registers the parameters and variables that no nested function refers to: the others must stay
 * visible to the nested functions.
 */
class RegisterVarsTest {

    private static void assertAllVersions(Object expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void closuresSeeTheirVariables() {
        assertAllVersions(
                "3,13,4",
                "function f(a, b) {"
                        + "  var local = a + b, captured = 10;"
                        + "  var get = function () { return captured + local2; };"
                        + "  var local2 = 3;"
                        + "  var arrow = () => a * 2;"
                        + "  return [local, get(), arrow() + local2 - 1];"
                        + "}"
                        + "f(1, 2).join()");
    }

    @Test
    void catchClauseKeepsVariablesInRegisters() {
        assertAllVersions(
                "45,boom,2",
                "function f(n) {"
                        + "  var s = 0, last;"
                        + "  for (var i = 0; i < n; i++) { try { s += i; } catch (e) { s = -1; } }"
                        + "  try { throw 'boom'; } catch (e) { last = e; }"
                        + "  try { throw 1; } catch (i) { i++; var k = i; }"
                        + "  try { throw 5; } catch (s) { s++; }"
                        + "  return [s, last, k];"
                        + "}"
                        + "f(10).join()");
    }

    @Test
    void typeofParametersAndVariables() {
        assertAllVersions(
                "number,undefined,string,undefined",
                "function f(a, b) {"
                        + "  var c = 'x', d;"
                        + "  try {} catch (e) {}"
                        + "  return [typeof a, typeof b, typeof c, typeof d];"
                        + "}"
                        + "f(1).join()");
    }

    @Test
    void restAndDefaultParameters() {
        Utils.assertWithAllModes_ES6(
                "1,2,0|1,3,2|7",
                "function f(a, b = a + 1, ...r) {"
                        + "  try {} catch (e) {}"
                        + "  return [a, b, r.length].join();"
                        + "}"
                        + "function g(x, ...r) { var h = () => r.length; return x + h(); }"
                        + "f(1) + '|' + f(1, 3, 4, 5) + '|' + g(5, 0, 0)");
    }

    @Test
    void duplicateParameters() {
        assertAllVersions(
                "2,",
                "function f(a, a) { try {} catch (e) {} return a; }" + "[f(1, 2), f(1)].join()");
    }

    @Test
    void functionDeclarationNamedLikeAVariable() {
        assertAllVersions(
                "function,1,function",
                "function f() {"
                        + "  var r = [typeof g];"
                        + "  var g = 1;"
                        + "  r.push(g);"
                        + "  function g() {}"
                        + "  var h; function h() {}"
                        + "  r.push(typeof h);"
                        + "  try {} catch (e) {}"
                        + "  return r.join();"
                        + "}"
                        + "f()");
    }

    @Test
    void blockFunctionNamedLikeAVariable() {
        assertAllVersions(
                "function",
                "function f() {"
                        + "  var b;"
                        + "  { function b() {} }"
                        + "  try {} catch (e) {}"
                        + "  return typeof b;"
                        + "}"
                        + "f()");
    }

    @Test
    void namesInNestedConstructs() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "k,5,3,4,8",
                "function f() {"
                        + "  var key = 'k', v = 5, d = 3, m = 4, fi = 8;"
                        + "  var o = function () { return { [key]: v }; };"
                        + "  var p = function (x = d) { return x; };"
                        + "  class C { meth() { return m; } }"
                        + "  var obj = o();"
                        + "  return [Object.keys(obj)[0], obj.k, p(), new C().meth(), (() => fi)()].join();"
                        + "}"
                        + "f()");
    }

    @Test
    void classFieldInitializer() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "9",
                "function f() { var z = '9'; class C { field = z; } return new C().field; } f()");
    }

    @Test
    void closuresInLoops() {
        assertAllVersions(
                "0,1,2|3",
                "function f() {"
                        + "  var fs = [], last;"
                        + "  for (var i = 0; i < 3; i++) { fs.push((function (j) { return function () { return j; }; })(i)); }"
                        + "  last = i;"
                        + "  return fs.map(function (g) { return g(); }).join() + '|' + last;"
                        + "}"
                        + "f()");
    }

    @Test
    void evalWithAndArgumentsKeepTheActivation() {
        assertAllVersions(
                "3|4|7|2,9",
                "function e() { var x = 1; var g = function () { return eval('x + 2'); }; return g(); }"
                        + "function w() { var y = 1; var o = { y: 4 }; var g = function () { with (o) { return y; } }; return g(); }"
                        + "function n() { var x = 1; var g = function () { return eval('x = 7'); }; g(); return x; }"
                        + "function a(p, q) { var g = function () {}; arguments[1] = 9; return [arguments.length, q].join(); }"
                        + "[e(), w(), n(), a(1, 2)].join('|')");
    }

    @Test
    void deleteVariable() {
        assertAllVersions(
                "false,1",
                "function f() { var v = 1; var g = function () {}; return [delete v, v].join(); } f()");
    }

    @Test
    void generatorsKeepTheActivation() {
        Utils.assertWithAllModes_ES6(
                "1,2,3",
                "function* gen() { var a = 1; var g = () => a; yield g(); a++; yield a; var b = 3; yield b; }"
                        + "[...gen()].join()");
    }

    @Test
    void codeEvaluatedInTheScopeAtRuntime() {
        // a legacy Script object runs in the scope of its caller: only the variables that a nested
        // function refers to are still in the activation object
        Utils.assertGatedBehavior(
                "5,2",
                "5,1",
                "function f() {"
                        + "  var c = 1, w = 1;"
                        + "  var g = function () { return c; };"
                        + "  Script('c = 5; w = 2')();"
                        + "  return [g(), w].join();"
                        + "}"
                        + "f()");
    }

    @Test
    void capturedVariablesReadAndWrittenByTheFunction() {
        assertAllVersions(
                "13,5,6,3|1,2,3",
                "function f(p, ...rest) {"
                        + "  var t = 0, n = 0;"
                        + "  var add = function (v) { t += v; n++; };"
                        + "  for (var i = 0; i < 3; i++) { add(i); t = t + 1; }"
                        + "  t += 7; n++;"
                        + "  var r = [t, ++n, n++ + 1, p + rest.length];"
                        + "  var get = function () { return [p, rest.length, rest[1]]; };"
                        + "  return r.join() + '|' + get().join();"
                        + "}"
                        + "f(1, 'a', 3)");
    }

    @Test
    void capturedVariableAfterTheActivationGrows() {
        // enough function declarations to promote the slot map of the activation
        StringBuilder declarations = new StringBuilder();
        for (int i = 0; i < 1600; i++) {
            declarations.append("function g").append(i).append("() { return v; }");
        }
        assertAllVersions(
                "3,3",
                "function f() {"
                        + "  var v = 1;"
                        + declarations
                        + "  v = v + 2;"
                        + "  return [v, g1599()].join();"
                        + "}"
                        + "f()");
    }

    @Test
    void nestedFunctionsReadAndWriteCapturedVariables() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "100,15,115,200,-85,2,100",
                "function f() {"
                        + "  var n = 0, total = 10;"
                        + "  function inc() { n = n + 1; return n; }"
                        + "  var add = (k) => { total = total + k; return total; };"
                        + "  var deep = function () { return function () { return () => n + total; }; };"
                        + "  var o = { m() { return n * 2; } };"
                        + "  class C { get v() { return total - n; } }"
                        + "  function* g() { yield n; n = 100; yield n; }"
                        + "  inc(); inc(); add(5);"
                        + "  var it = g(), a = it.next().value, b = it.next().value;"
                        + "  return [n, total, deep()()(), o.m(), new C().v, a, b].join();"
                        + "}"
                        + "f()");
    }

    @Test
    void nestedFunctionsSeeTheActivationOfTheirCall() {
        assertAllVersions(
                "10,2|6",
                "function mk(v) {"
                        + "  var x = v;"
                        + "  return [() => x, (y) => { x = y; }];"
                        + "}"
                        + "var p = mk(1), q = mk(2); p[1](10);"
                        + "function f(n) {"
                        + "  var x = n;"
                        + "  return function () { return n > 0 ? x + f(n - 1)() : x; };"
                        + "}"
                        + "[p[0](), q[0]()].join() + '|' + f(3)()");
    }

    @Test
    void nestedFunctionsKeepTheActivationsOfTheirCall() {
        assertAllVersions(
                "12,21,14,23|27,14|15,9|1,3,6",
                "function outer(a) {"
                        + "  var x = a;"
                        + "  function mid(b) {"
                        + "    var y = b;"
                        + "    return function (n, odd) {"
                        + "      var r = 0;"
                        + "      for (var i = 0; i < n; i++) {"
                        + "        if (odd && i % 2) { r += y; } else { let k = x; r += k; }"
                        + "      }"
                        + "      x = x + 1; y = y + 1;"
                        + "      return r;"
                        + "    };"
                        + "  }"
                        + "  return mid;"
                        + "}"
                        + "var p = outer(1)(10), q = outer(2)(20), r = [];"
                        + "for (var i = 0; i < 2; i++) { r.push(p(2, true) + 1, q(2, true) - 1); }"
                        + "function t() {"
                        + "  var v = 1, w = 0;"
                        + "  var h = function () {"
                        + "    try { if (v > 0) throw v; } catch (e) { w = w + e + v; }"
                        + "    finally { v = v * 2; }"
                        + "    return v;"
                        + "  };"
                        + "  return [h() + h() + h() + w - 1, w].join();"
                        + "}"
                        + "function u() {"
                        + "  var s = 0;"
                        + "  function add(k) { if (k > 0) { add(k - 1); } s = s + k; return s; }"
                        + "  var arrow = () => add(4) + s;"
                        + "  return [arrow() - 5, s - 1].join();"
                        + "}"
                        + "function g() {"
                        + "  var c = 0;"
                        + "  return function () { c = c + 1; return c; };"
                        + "}"
                        + "var c1 = g(), c2 = g(), c3 = g();"
                        + "c2(); c3(); c3();"
                        + "[r.join(), t(), u(),"
                        + " [c1(), c2() + 1, c3() + 3].join()].join('|')");
    }

    @Test
    void nestedFunctionsShadowingCapturedVariables() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "2,5,1|function,1|2,5|6,6",
                "function f() {"
                        + "  var x = 1;"
                        + "  var a = function (x) { return x; };"
                        + "  var b = function () { let x = 5; { return x; } };"
                        + "  return [a(2), b(), x].join();"
                        + "}"
                        + "function g() {"
                        + "  var C = 1;"
                        + "  var h = function () { return typeof class C { static m() { return C; } }.m(); };"
                        + "  return [h(), (() => C)()].join();"
                        + "}"
                        + "function k() {"
                        + "  var arguments = 5;"
                        + "  var h = function () { return arguments.length; };"
                        + "  return [h(1, 2), (() => arguments)()].join();"
                        + "}"
                        + "function s() {"
                        + "  'use strict';"
                        + "  var x = 1;"
                        + "  var h = function () {"
                        + "    try {} catch (e) {}"
                        + "    let y = 2;"
                        + "    { let z = 3; x = x + y + z; }"
                        + "    return x;"
                        + "  };"
                        + "  return [h(), x].join();"
                        + "}"
                        + "[f(), g(), k(), s()].join('|')");
    }

    @Test
    void functionWithManyVariablesCompiles() {
        StringBuilder script = new StringBuilder("function f() { var n = 0;");
        for (int i = 0; i < 1200; i++) {
            script.append("var v").append(i).append(" = ").append(i).append("; n++;");
        }
        script.append("var get = function () { return v1199; };")
                .append("return n + ',' + get(); }")
                .append("f()");
        assertAllVersions("1200,1199", script.toString());
    }
}
