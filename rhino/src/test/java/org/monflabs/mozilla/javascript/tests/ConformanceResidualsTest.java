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
                "undefined|length,name,arguments,caller,prototype",
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

    @Test
    void restrictedFunctionProperties() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|true|2|undefined|length,name,arguments,caller,prototype|TypeError|length,name",
                "var d = Object.getOwnPropertyDescriptor(Function.prototype, 'caller');\n"
                        + "var tte = (function () { 'use strict';"
                        + " return Object.getOwnPropertyDescriptor(arguments, 'callee').get; })();\n"
                        + "function sloppy() { return sloppy.arguments.length; }\n"
                        + "function strict() { 'use strict'; }\n"
                        + "var r = [d.get === d.set && d.get === tte,"
                        + " Object.getOwnPropertyDescriptor(Function.prototype, 'arguments').get === tte,"
                        + " sloppy(1, 2), String(sloppy.caller), Object.getOwnPropertyNames(sloppy).join()];\n"
                        + "try { strict.caller; r.push('none'); } catch (e) { r.push(e.name); }\n"
                        + "r.push(Object.getOwnPropertyNames(() => 1).join());\n"
                        + "r.join('|')");
    }

    @Test
    void parametersDoNotSeeBodyLexicals() {
        Utils.assertGatedBehavior(
                "NaN|undefined",
                "out1|object",
                "var x = 'out', r = [], args;\n"
                        + "function f(a = x) { let x = 1; return a + x; }\n"
                        + "function g(y = args = arguments) { let arguments; }\n"
                        + "try { r.push(f()); } catch (e) { r.push(e.name); }\n"
                        + "try { g(); r.push(typeof args); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }

    @Test
    void arrayBufferPrototypeBeforeAllocation() {
        Utils.assertGatedBehavior(
                "RangeError",
                "Dummy",
                "var nt = function () {}.bind(null);\n"
                        + "Object.defineProperty(nt, 'prototype', { get() { throw 'Dummy'; } });\n"
                        + "try { Reflect.construct(ArrayBuffer, [7 * Math.pow(1024, 5)], nt); 'none' }"
                        + " catch (e) { typeof e === 'string' ? e : e.name }");
    }

    @Test
    void generatorReturnClosesDestructuringIterators() {
        Utils.assertGatedBehavior(
                "0|1",
                "1|TypeError",
                "var closed = 0;\n"
                        + "var iterable = { [Symbol.iterator]() { return {"
                        + " next() { return { value: undefined, done: false }; },"
                        + " return() { closed++; return null; } }; } };\n"
                        + "function* g() { var a; [a = yield] = iterable; }\n"
                        + "var it = g(); it.next();\n"
                        + "var r; try { it.return(); r = 'none'; } catch (e) { r = e.name; }\n"
                        + "closed + '|' + (r === 'none' ? 1 : r)");
    }

    @Test
    void switchDiscriminantAndCatchParameterSeeOutside() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "outside|inside|outside|inside|1|clean",
                "let x = 'outside'; var r = [], pd, ps, pp, pb;\n"
                        + "switch (pd = function () { return x; }, null) {"
                        + " case ps = function () { return x; }, null: let x = 'inside'; }\n"
                        + "try { throw []; } catch ([_ = pp = function () { return x; }]) {"
                        + " pb = function () { return x; }; let x = 'inside'; }\n"
                        + "let y = 1; switch (y) { case 1: r.push(1); let y = 2; }\n"
                        + "try { switch (y) { case 1: y; let y; } } catch (e) {"
                        + " r.push(e.message.indexOf('$') < 0 ? 'clean' : e.message); }\n"
                        + "[pd(), ps(), pp(), pb()].concat(r).join('|')");
    }
}
