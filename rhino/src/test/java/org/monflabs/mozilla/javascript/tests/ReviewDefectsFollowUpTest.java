/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Defects found while fixing those of the review of the engine. */
class ReviewDefectsFollowUpTest {

    private static final String ATTEMPT =
            "var r = [];\n"
                    + "function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }\n";

    @Test
    void initializersOfAForHeadSeeEarlierBindings() {
        Utils.assertGatedBehavior(
                "ReferenceError|ReferenceError|ReferenceError|NaN,NaN|ReferenceError",
                "0,0|1,2,2|0,5,5|5,6|ReferenceError",
                ATTEMPT
                        + "t(function () { var s = [];"
                        + " for (let i = 0, n = i; i < 1; i++) s.push(i, n); return s; });\n"
                        + "t(function () { var s = [];"
                        + " for (let i = 1, n = i + 1, m = n * i; i < 2; i++) s.push(i, n, m);"
                        + " return s; });\n"
                        + "t(function () { var s = [];"
                        + " for (let [i, n] = [0, 5], m = n; i < 1; i++) s.push(i, n, m);"
                        + " return s; });\n"
                        + "t(function () { var s = [];"
                        + " for (let [i, n] = [0, 5], m = n; i < 2; i++) s.push(() => i + m);"
                        + " return s.map(f => f()); });\n"
                        + "t(function () { for (let i = n, n = 1; ;) return 'no'; });\n"
                        + "r.join('|')");
    }

    @Test
    void functionExpressionNameInWith() {
        Utils.assertGatedBehavior(
                "5,function|2,function|6,function|false,number",
                "5,function|2,function|6,function|false,function",
                ATTEMPT
                        + "t(function () { return (function g() { var o = {g: 1};"
                        + " with (o) { g = 5 } return o.g + ',' + typeof g; })(); });\n"
                        + "t(function () { return (function g() { var o = {g: 1};"
                        + " with (o) { g++ } return o.g + ',' + typeof g; })(); });\n"
                        + "t(function () { return (function g() { var o = {g: 1};"
                        + " with (o) { g += 5 } return o.g + ',' + typeof g; })(); });\n"
                        + "t(function () { return (function g() { var o = {};"
                        + " with (o) { g = 5; g++; g += 1 } return ('g' in o) + ',' + typeof g;"
                        + " })(); });\n"
                        + "r.join('|')");
    }

    @Test
    void varOfEvalCodeHidesTheFunctionExpressionName() {
        Utils.assertGatedBehavior(
                "number|function|3|7|number",
                "number|undefined|3|7|function",
                ATTEMPT
                        + "t(function () { return (function g() { eval('var g = 1');"
                        + " return typeof g; })(); });\n"
                        + "t(function () { return (function g() { eval('var g');"
                        + " return typeof g; })(); });\n"
                        + "t(function () { return (function g() { eval('var g'); g = 2; g++;"
                        + " return g; })(); });\n"
                        + "t(function () { return (function g() { eval('function g() { return 7 }');"
                        + " return g(); })(); });\n"
                        + "t(function () { return (function g() { eval('1'); g = 2; g++;"
                        + " return typeof g; })(); });\n"
                        + "r.join('|')");
    }

    @Test
    void strictWriteThatWouldExtendAnArray() {
        Utils.assertGatedBehavior(
                "1|1|1,2,3|true|2",
                "TypeError|TypeError|TypeError|false|2",
                ATTEMPT
                        + "t(function () { 'use strict'; var a = Object.preventExtensions([1]);"
                        + " a[1] = 2; return a.length; });\n"
                        + "t(function () { 'use strict'; var a = Object.preventExtensions([1]);"
                        + " a[100000] = 2; return a.length; });\n"
                        + "t(function () { 'use strict'; var a = Object.preventExtensions([1,,3]);"
                        + " a[1] = 2; return a; });\n"
                        + "t(function () { var a = Object.preventExtensions([1,,3]);"
                        + " a[1] = 2; return 1 in a; });\n"
                        + "t(function () { 'use strict'; var a = Object.preventExtensions([1]);"
                        + " a[0] = 2; return a; });\n"
                        + "r.join('|')");
    }

    @Test
    void reservedNamesOfAFunctionWhoseBodyIsStrict() {
        Utils.assertGatedBehavior(
                "ok|ok|ok|ok|ok|ok",
                "SyntaxError|SyntaxError|SyntaxError|SyntaxError|SyntaxError|ok",
                "var r = [];\n"
                        + "function p(src) { try { eval(src); r.push('ok'); }"
                        + " catch (e) { r.push(e.name); } }\n"
                        + "p('function static() { \"use strict\" }');\n"
                        + "p('function implements() { \"use strict\" }');\n"
                        + "p('function f(static) { \"use strict\" }');\n"
                        + "p('(function package() { \"use strict\" })');\n"
                        + "p('(public) => { \"use strict\" }');\n"
                        + "p('({ static() { \"use strict\" }, yield(a) { \"use strict\" } })');\n"
                        + "r.join('|')");
    }

    @Test
    void sloppyFunctionsKeepTheNamesReservedInStrictCode() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ok|ok|ok",
                "var r = [];\n"
                        + "function p(src) { try { eval(src); r.push('ok'); }"
                        + " catch (e) { r.push(e.name); } }\n"
                        + "p('function static() {} function f(yield, package) {}');\n"
                        + "p('function f({a: public}) {}');\n"
                        + "p('function f(a) { \"use strict\"; return {static: 1, yield: 2}; }');\n"
                        + "r.join('|')");
    }

    @Test
    void setTrapForwardingANewPropertyToReflectSet() {
        Utils.assertGatedBehavior(
                "1,2,3,4|4|x,y",
                "1,2,3,4|4|x,y",
                "var fwd = { set(t, k, v, r) { return Reflect.set(t, k, v, r); } };\n"
                        + "var t0 = [1, 2, 3], p = new Proxy(t0, fwd);\n"
                        + "Array.prototype.push.call(p, 4);\n"
                        + "var o = new Proxy({}, fwd); o.x = 1; o.y = 2;\n"
                        + "[t0.join(), p.length, Object.keys(o).join()].join('|')");
    }

    @Test
    void prototypeOfAGeneratorMethod() {
        Utils.assertGatedBehavior(
                "object|false",
                "object|true",
                "var g = ({ *g() {} }).g;\n"
                        + "[typeof g.prototype, g.hasOwnProperty('prototype')].join('|')");
    }
}
