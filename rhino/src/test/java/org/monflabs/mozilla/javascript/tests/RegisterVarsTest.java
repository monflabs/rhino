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
}
