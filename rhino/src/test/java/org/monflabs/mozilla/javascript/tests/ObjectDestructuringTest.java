/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Object patterns and the names of pattern defaults (plan 1.3c), under VERSION_ECMASCRIPT;
 * VERSION_ES6 keeps the stock patterns.
 */
class ObjectDestructuringTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void nullAndUndefinedCannotBeDestructured() {
        Utils.assertGatedBehavior(
                "ok,TypeError,ok,ok",
                "TypeError,TypeError,TypeError,TypeError",
                "var r = []; function t(f) { try { f(); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t(function () { var {} = null; });"
                        + " t(function () { let {a} = undefined; });"
                        + " t(function () { (function ({}) {})(); });"
                        + " t(function () { (function ({} = null) {})(); });"
                        + " r.join()");
    }

    @Test
    void anonymousFunctionDefaultsAreNamedAfterTheirBinding() {
        Utils.assertGatedBehavior(
                ",,,,named,|,||",
                "fn,ar,gn,cv,named,|f,a|g|pq",
                "var [fn = function () {}, ar = () => 1, gn = function* () {}, cv = (function () {}),"
                        + "  xn = function named() {}, cm = (0, function () {})] = [];"
                        + " var r = [[fn.name, ar.name, gn.name, cv.name, xn.name, cm.name].join()];"
                        + " var { f = function () {}, x: a = () => 1 } = {}; r.push([f.name, a.name].join());"
                        + " var g; [g = function () {}] = []; r.push(g.name);"
                        + " r.push((function ([p = function () {}], { q = () => 2 }) { return p.name + q.name; })"
                        + "   ([], {}));"
                        + " r.join('|')");
    }

    @Test
    void theInferredNameIsNotABinding() {
        assertBoth(
                "number", "var [f = function () { return typeof f; }] = []; var o = f; f = 1; o()");
    }

    @Test
    void targetsAreEvaluatedBeforeTheirValue() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "ok",
                "try { eval('var o = {}; [o.q = 5] = [];'); 'ok' } catch (e) { e.name }");
        Utils.assertWithAllModes_ECMASCRIPT(
                "key,next,return|key,get,9|k1,get a,k2,get b|5",
                "var r = [];"
                        + " var log = []; var it = { [Symbol.iterator]() { return {"
                        + "   next() { log.push('next'); return {done: false, value: 1}; },"
                        + "   return() { log.push('return'); return {}; } }; } };"
                        + " var o = {}; [o[(log.push('key'), 'p')]] = it; r.push(log.join());"
                        + " log = []; var src = { get a() { log.push('get'); return undefined; } };"
                        + " ({ a: o[(log.push('key'), 'x')] = 9 } = src); r.push(log.join() + ',' + o.x);"
                        + " log = []; var x, y; src = { get a() { log.push('get a'); return 1; },"
                        + "   get b() { log.push('get b'); return 2; } };"
                        + " ({ [(log.push('k1'), 'a')]: x, [(log.push('k2'), 'b')]: y } = src);"
                        + " r.push(log.join());"
                        + " [o.q = 5] = []; r.push(o.q);"
                        + " r.join('|')");
    }

    @Test
    void forHeads() {
        Utils.assertGatedBehavior(
                "1,3,undefined",
                "1,3,undefined",
                "'use strict'; var r = [];"
                        + " for (let [{ x }] = [{ x: 1 }]; r.length < 1; ) r.push(x);"
                        + " for (let { w: [c, d] } = { w: [1, 2] }; r.length < 2; ) r.push(c + d);"
                        + " r.push(typeof x);"
                        + " r.join()");
        // an assignment pattern with initializers as the target of for-of
        Utils.assertGatedBehavior(
                "SyntaxError",
                "ok",
                "try { eval('var a, b; for ({ a = 1, b } of [{ b: 3 }]) ;'); 'ok' }"
                        + " catch (e) { e.name }");
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,4,3,undefined",
                "'use strict'; var r = [];"
                        + " for (let [{ x }] = [{ x: 1 }]; r.length < 1; ) r.push(x);"
                        + " var a, b; for ({ a = 1, b } of [{ b: 3 }]) r.push(a + b);"
                        + " for (let { w: [c, d] } = { w: [1, 2] }; r.length < 3; ) r.push(c + d);"
                        + " r.push(typeof x);"
                        + " r.join()");
    }

    @Test
    void forConstHeads() {
        // the interpreter of stock Rhino rejects a const loop head
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,3",
                "function f() { for (const x = 1; ; ) { return x; } }"
                        + " var r = []; for (const a = 1, b = 2; r.length < 1; ) { r.push(a + b); }"
                        + " [f(), r[0]].join()");
    }
}
