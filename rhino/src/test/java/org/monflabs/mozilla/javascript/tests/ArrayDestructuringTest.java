/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Array patterns follow the iterator protocol (plan 1.3a) under VERSION_ECMASCRIPT; VERSION_ES6
 * keeps the stock patterns (indexes, and a partial iterator path for parameters).
 */
class ArrayDestructuringTest {

    private static final String LOGGING_ITERABLE =
            "var log = [];"
                    + " var iterable = { [Symbol.iterator]() { var i = 0; return {"
                    + "   next() { log.push('next'); i++; return {done: i > 3, value: i}; },"
                    + "   return() { log.push('return'); return {}; } }; } };";

    @Test
    void iterablesAreSteppedAndClosed() {
        Utils.assertGatedBehavior(
                "|",
                "1|next,return",
                LOGGING_ITERABLE + " var [a] = iterable; [a, log.join()].join('|')");
        Utils.assertGatedBehavior(
                ",|",
                "1,3|next,next,next,return",
                LOGGING_ITERABLE
                        + " var [a, , b] = iterable; [[a, b].join(), log.join()].join('|')");
        // a done iterator is not closed, nor stepped again
        Utils.assertGatedBehavior(
                "TypeError",
                "1,2,3|next,next,next,next",
                LOGGING_ITERABLE
                        + " try { var [...rest] = iterable; [rest.join(), log.join()].join('|') }"
                        + " catch (e) { e.name }");
    }

    @Test
    void iteratorsAreClosedWhenADefaultOrATargetThrows() {
        Utils.assertGatedBehavior(
                "d:,t:,p:n,r:,ok:,g:",
                "d:nr,t:nr,p:nr,r:nr,ok:nrnr,g:nr",
                "var r = [];"
                        + " function mk(log, retThrows) { return { [Symbol.iterator]() { return {"
                        + "   next() { log.push('n'); return {done: false, value: undefined}; },"
                        + "   return() { log.push('r'); if (retThrows) throw new Error('ret'); return {}; } }; } }; }"
                        + " function t(f) { var log = []; try { f(log); r.push('ok:' + log.join('')); }"
                        + "   catch (e) { r.push(e.message + ':' + log.join('')); } }"
                        + " function thrower(m) { throw new Error(m); }"
                        + " t(function (log) { var [a = thrower('d')] = mk(log); });"
                        + " t(function (log) { var o = { set p(v) { thrower('t'); } }; [o.p] = mk(log); });"
                        + " t(function (log) { (function ([a = thrower('p')]) {})(mk(log)); });"
                        // an error of return() does not replace the error being thrown
                        + " t(function (log) { var [a = thrower('r')] = mk(log, true); });"
                        + " t(function (log) { for (var i = 0; i < 2; i++) { var [a] = mk(log); } });"
                        + " function* g(log) { var [a = yield 1] = mk(log); }"
                        + " t(function (log) { var it = g(log); it.next(); it.return(5); thrower('g'); });"
                        + " r.join()");
    }

    @Test
    void holesInEveryContext() {
        // stock parameters skip holes without stepping the iterator
        Utils.assertGatedBehavior(
                "1/3,1/3,1/3,1/2",
                "1/3,1/3,1/3,1/3",
                "var r = [];"
                        + " var [a, , b] = [1, 2, 3]; r.push(a + '/' + b);"
                        + " let [c, , d] = [1, 2, 3]; r.push(c + '/' + d);"
                        + " [a, , b] = [1, 2, 3]; r.push(a + '/' + b);"
                        + " r.push((function ([x, , y]) { return x + '/' + y; })([1, 2, 3]));"
                        + " r.join()");
    }

    @Test
    void stringsSetsAndNestedPatterns() {
        Utils.assertGatedBehavior(
                "xy,NaN,3,1-2-3",
                "xy,7,3,1-2-3",
                "var [p, q] = 'xy'; var [s, t] = new Set([3, 4]);"
                        + " var [[m], {n}] = [[1], {n: 2}];"
                        + " var [u, ...[v, w]] = [1, 2, 3];"
                        + " [p + q, s + t, m + n, [u, v, w].join('-')].join()");
    }

    @Test
    void defaultsUseTheStepValueOnly() {
        Utils.assertGatedBehavior(
                "5,6", "1,6", "var a = 5; var [a = 1, b = 6] = []; [a, b].join()");
    }

    @Test
    void notIterable() {
        Utils.assertGatedBehavior(
                "TypeError",
                "TypeError,TypeError",
                "var r = [];"
                        + " try { var [x] = undefined; } catch (e) { r.push(e.name); }"
                        + " try { var [y] = {}; } catch (e) { r.push(e.name); }"
                        + " r.join()");
    }
}
