/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Property lookups find their slot by key in every kind of slot map and for every kind of slot. */
class SlotKeysTest {

    private static void assertAllVersions(String expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void stringKeysBuiltAtRuntime() {
        assertAllVersions(
                "1 2 3 4 i3 true false",
                "function key(s) { return s.split('').reverse().join(''); }"
                        + "var one = {}; one[key('ba')] = 1;"
                        + "var four = {a: 0, b: 0, c: 0}; four[key('dc')] = 2;"
                        + "var many = {}; for (var i = 0; i < 20; i++) many['p' + i] = i;"
                        + "many[key('xy')] = 3;"
                        + "var o = Object.create(many); o.z = 4; o[3] = 'i3';"
                        + "[one.ab, four.cd, many.yx, o.z, o[1 + 2]].join(' ')"
                        + " + ' ' + (key('xy') in o) + ' ' + ('xy' in o)");
    }

    @Test
    void symbolsAndAccessors() {
        assertAllVersions(
                "s1 s2 g3 7 x  10 max",
                "var s1 = Symbol('a'), s2 = Symbol('a');"
                        + "var o = {}; o[s1] = 's1'; o[s2] = 's2';"
                        + "Object.defineProperty(o, 'g', { get: function () { return 'g3'; } });"
                        + "var n = 0; Object.defineProperty(o, 'w', { set: function (v) { n = v; } });"
                        + "o.w = 7;"
                        + "var big = {}; for (var i = 0; i < 20; i++) big['k' + i] = i;"
                        + "big[s1] = 'x';"
                        + "[o[s1], o[s2], o.g, n, big[s1], big[Symbol('a')], big.k10,"
                        + " Math.max.name].join(' ')");
    }
}
