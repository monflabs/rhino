/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The long tail of test262 (plan M13). */
class ConformanceResidualsTest {

    @Test
    void decodeUriNoncharacters() {
        Utils.assertGatedBehavior(
                "fffd,fffd",
                "fffe,ffff",
                "[decodeURI('%EF%BF%BE'), decodeURIComponent('%EF%BF%BF')]"
                        + ".map(s => s.charCodeAt(0).toString(16)).join()");
    }

    @Test
    void revokeIsNotAConstructor() {
        Utils.assertGatedBehavior(
                "true",
                "false",
                "String(Proxy.revocable({}, {}).revoke.hasOwnProperty('prototype'))");
    }

    @Test
    void log2OfPowersOfTwo() {
        Utils.assertGatedBehavior(
                "2.9999999999999996,-1074",
                "3,-1074",
                "[Math.log2(8), Math.log2(Number.MIN_VALUE)].join()");
    }

    @Test
    void finalSigmaPastSupplementaryCaseIgnorable() {
        Utils.assertGatedBehavior(
                "σ,ς,σ",
                "ς,ς,σ",
                "['a\\ud834\\ude42\\u03a3', 'a\\u03a3', '\\u03a3'].map(s => s.toLowerCase().slice(-1))"
                        + ".join()");
    }

    @Test
    void classEarlyErrors() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,ok,ok",
                "['class C { * constructor() {} }', 'class C { async * constructor() {} }',"
                        + " 'class C { static * prototype() {} }', 'class C { st\\\\u0061tic m() {} }',"
                        + " 'class C { st\\\\u0061tic() {} }', 'class C { * [\"constructor\"]() {} }']"
                        + ".map(src => { try { eval(src); return 'ok'; } catch (e) { return e.name; } })"
                        + ".join()");
    }

    @Test
    void noArity() {
        Utils.assertGatedBehavior(
                "2|length,name,arity,prototype",
                "undefined|length,name,prototype",
                "var f = function (a, b) {};\n"
                        + "f.arity + '|' + Object.getOwnPropertyNames(f).join()");
    }

    @Test
    void yieldInArrowParameters() {
        Utils.assertGatedBehavior(
                "ok",
                "SyntaxError",
                "try { eval('function *g() { (x = yield) => {}; }'); 'ok' } catch (e) { e.name }");
    }

    @Test
    void parameterClosuresDoNotSeeBodyVars() {
        Utils.assertGatedBehavior(
                "inside|inside|1,1,1|inside,dz",
                "outside|inside|1,1,1|inside,dz",
                "var x = 'outside', probeParams, probeBody;\n"
                        + "function f(_ = probeParams = function () { return x; }) {\n"
                        + "  var x = 'inside'; probeBody = function () { return x; };\n"
                        + "}\n"
                        + "f();\n"
                        + "function g(a = () => x) { var x = 1;"
                        + " return [x, (function () { return x; })(), (() => x)()]; }\n"
                        + "function h(a = () => x) { var x = 'inside', o = { x: x };"
                        + " var { x: z } = { x: 'dz' }; return [o.x, z]; }\n"
                        + "[probeParams(), probeBody(), g(), h()].join('|')");
    }

    @Test
    void primitiveThis() {
        Utils.assertGatedBehavior(
                "object|object|object|object|object|TestString",
                "boolean|boolean|string|bigint|string|TestString",
                "'use strict';\n"
                        + "Boolean.prototype.f = function () { return typeof this; };\n"
                        + "Boolean.prototype.toString = function () { return typeof this; };\n"
                        + "var matcher = { [Symbol.match](s) { return typeof s; } };\n"
                        + "Object.defineProperty(BigInt.prototype, 'toJSON',"
                        + " { get() { var t = typeof this; return () => t; }, configurable: true });\n"
                        + "var flat = [1].flatMap(function () { return [typeof this]; }, 's');\n"
                        + "var r = [true.f(), true.toLocaleString(), ''.match(matcher),"
                        + " JSON.parse(JSON.stringify(1n)), flat[0],"
                        + " [1].flatMap(function () { return [this]; }, 'TestString')[0]];\n"
                        + "r.join('|')");
    }

    @Test
    void dataPropertyAfterAccessorInLiteral() {
        Utils.assertGatedBehavior(
                "TypeError",
                "1",
                "'use strict'; try { String(({ get foo() { return 0; }, foo: 1 }).foo) }"
                        + " catch (e) { e.name }");
    }

    @Test
    void classHeritageIsStrict() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError|undefined",
                "var D = class extends function () { arguments.callee; } {};\n"
                        + "var F = class extends function () { return this; } {};\n"
                        + "var r; try { new D(); r = 'none'; } catch (e) { r = e.name; }\n"
                        + "r + '|' + typeof Object.getPrototypeOf(F).call(undefined)");
    }
}
