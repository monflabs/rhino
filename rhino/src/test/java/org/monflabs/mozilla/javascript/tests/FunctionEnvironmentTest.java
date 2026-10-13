/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Environments of functions and eval code (plan 1.2g). */
class FunctionEnvironmentTest {

    private static final String PRELUDE =
            "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }";

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, PRELUDE + script + " r.join('|')");
        Utils.assertWithAllModes_ECMASCRIPT(expected, PRELUDE + script + " r.join('|')");
    }

    @Test
    void functionExpressionNameIsImmutable() {
        assertBoth(
                "function|TypeError|function|function|TypeError|function|number",
                "t(function () { var f = function g() { g = 1; return typeof g; }; return f(); });"
                        + " t(function () { var f = function g() { 'use strict'; g = 1; }; return f(); });"
                        + " t(function () { var f = function g() { (() => { g = 1; })(); return typeof g; };"
                        + " return f(); });"
                        + " t(function () { var f = function g() { eval('g = 1'); return typeof g; }; return f(); });"
                        + " t(function () { var f = function g() { 'use strict'; eval('g = 1'); }; return f(); });"
                        + " t(function () { var f = function g() { return function () { g = null; }; };"
                        + " var h = f(); h(); return typeof f; });"
                        + " t(function () { var f = function g() { var g = 1; return typeof g; }; return f(); });");
    }

    @Test
    void evalInParametersAndNewTarget() {
        Utils.assertGatedBehavior(
                "undefined|ok|true,false|SyntaxError",
                "SyntaxError|ok|true,false|SyntaxError",
                PRELUDE
                        + "t(function () { var f = (p = eval('var x = 1'), x) => {}; f(); });"
                        + " t(function () { var f = (p = eval('var arguments = 1')) => {}; f(); return 'ok'; });"
                        + " t(function () { var nt; function F() { nt = eval('new.target'); }"
                        + " new F(); var a = nt === F; F(); return [a, nt === F].join(); });"
                        + " t(function () { return (0, eval)('new.target'); });"
                        + " r.join('|')");
    }

    @Test
    void destructuredParametersGiveUnmappedArguments() {
        String script =
                PRELUDE
                        + "t(function () { function f(a, [b]) { arguments[0] = 2; return a; } return f(1, []); });"
                        + " t(function () { function f(a, b) { arguments[0] = 2; return a; } return f(1, []); });"
                        + " r.join('|')";
        Utils.assertGatedBehavior("2|2", "1|2", script);
    }

    @Test
    void redefiningANonConfigurablePropertyKeepsItsEnumerability() {
        assertBoth(
                "2,x",
                "var o = Object.defineProperty({}, 'x', { value: 1, writable: true, enumerable: true });"
                        + " Object.defineProperty(o, 'x', { value: 2 }); r.push([o.x, Object.keys(o)].join());");
    }

    @Test
    void lexicalDeclarationsOfEvalCode() {
        Utils.assertGatedBehavior(
                "number,number,1,3",
                "undefined,ReferenceError,1,3",
                PRELUDE
                        + " eval('let a = 1; var v = 1;'); r.push(typeof a);"
                        + " t(function () { return typeof eval('typeof c; let c = 2; c'); });"
                        + " r.push(v); r.push(eval('let d = 3; d'));"
                        + " r.join()");
    }

    @Test
    void evalVarConflictsWithTheFunctionLexicals() {
        Utils.assertGatedBehavior(
                "ok",
                "SyntaxError",
                PRELUDE + " t(function () { let x; eval('var x'); return 'ok'; }); r.join()");
    }
}
