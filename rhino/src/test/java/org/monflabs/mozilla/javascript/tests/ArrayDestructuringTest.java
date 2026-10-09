/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Array patterns follow the iterator protocol (plan 1.3a); the same in both versions. */
class ArrayDestructuringTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    private static final String LOGGING_ITERABLE =
            "var log = [];"
                    + " var iterable = { [Symbol.iterator]() { var i = 0; return {"
                    + "   next() { log.push('next'); i++; return {done: i > 3, value: i}; },"
                    + "   return() { log.push('return'); return {}; } }; } };";

    @Test
    void iterablesAreSteppedAndClosed() {
        assertBoth(
                "1|next,return",
                LOGGING_ITERABLE + " var [a] = iterable; [a, log.join()].join('|')");
        assertBoth(
                "1,3|next,next,next,return",
                LOGGING_ITERABLE
                        + " var [a, , b] = iterable; [[a, b].join(), log.join()].join('|')");
        // a done iterator is not closed, nor stepped again
        assertBoth(
                "1,2,3|next,next,next,next",
                LOGGING_ITERABLE
                        + " var [...rest] = iterable; [rest.join(), log.join()].join('|')");
    }

    @Test
    void holesInEveryContext() {
        assertBoth(
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
        assertBoth(
                "xy,7,3,1-2-3",
                "var [p, q] = 'xy'; var [s, t] = new Set([3, 4]);"
                        + " var [[m], {n}] = [[1], {n: 2}];"
                        + " var [u, ...[v, w]] = [1, 2, 3];"
                        + " [p + q, s + t, m + n, [u, v, w].join('-')].join()");
    }

    @Test
    void defaultsUseTheStepValueOnly() {
        assertBoth("1,6", "var a = 5; var [a = 1, b = 6] = []; [a, b].join()");
    }

    @Test
    void notIterable() {
        assertBoth(
                "TypeError,TypeError",
                "var r = [];"
                        + " try { var [x] = undefined; } catch (e) { r.push(e.name); }"
                        + " try { var [y] = {}; } catch (e) { r.push(e.name); }"
                        + " r.join()");
    }
}
