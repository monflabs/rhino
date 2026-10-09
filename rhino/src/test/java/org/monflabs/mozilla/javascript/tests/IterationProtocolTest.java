/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Spread arguments and for-of IteratorClose (plan 1.4a); the same in both versions. */
class IterationProtocolTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void spreadArguments() {
        assertBoth(
                "1-2-3-4|2|5|7-8",
                "function f() { return Array.prototype.join.call(arguments, '-'); }"
                        + " var o = { m() { return f(...arguments); } };"
                        + " [f(...[1, 2], 3, ...new Set([4])), new Array(...[1, 2]).length,"
                        + "  Math.max(...[1, 5, 3]), o.m(7, 8)].join('|')");
    }

    private static final String ITERABLE =
            "function mk(log, n) { var i = 0; return { [Symbol.iterator]() { return {"
                    + " next() { i++; return {done: i > (n || 3), value: i}; },"
                    + " return() { log.push('r'); return {}; } }; } }; }"
                    + " var r = []; function t(f) { var log = [];"
                    + " try { r.push(f(log) + ':' + log.join('')); }"
                    + " catch (e) { r.push(e.name + ':' + log.join('')); } }";

    @Test
    void forOfClosesItsIteratorWhenLeftEarly() {
        assertBoth(
                "6:|b:r|t:r|ret:r|co:rr|bo:rr|g:r",
                ITERABLE
                        + " t(function (log) { var s = 0; for (var x of mk(log)) s += x; return s; });"
                        + " t(function (log) { for (var x of mk(log)) break; return 'b'; });"
                        + " t(function (log) { try { for (var x of mk(log)) throw 1; } catch (e) {}"
                        + "   return 't'; });"
                        + " t(function (log) { (function () { for (var x of mk(log)) return; })();"
                        + "   return 'ret'; });"
                        + " t(function (log) { outer: for (var y of [1, 2]) {"
                        + "   for (var x of mk(log)) { continue outer; } } return 'co'; });"
                        + " t(function (log) { outer: for (var y of mk(log)) {"
                        + "   for (var x of mk(log)) { break outer; } } return 'bo'; });"
                        + " t(function (log) { function* g() { for (var x of mk(log)) yield x; }"
                        + "   var it = g(); it.next(); it.return(); return 'g'; });"
                        + " r.join('|')");
    }

    @Test
    void returnMustGiveAnObjectAfterABreak() {
        assertBoth(
                "TypeError,body",
                "var r = [];"
                        + " var a = { [Symbol.iterator]() { return { next() { return {done: false}; },"
                        + "   return() { return 1; } }; } };"
                        + " try { for (var x of a) break; } catch (e) { r.push(e.name); }"
                        + " var b = { [Symbol.iterator]() { return { next() { return {done: false}; },"
                        + "   return() { throw new Error('ret'); } }; } };"
                        + " try { for (var y of b) throw new Error('body'); } catch (e) { r.push(e.message); }"
                        + " r.join()");
    }
}
