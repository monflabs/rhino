/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Arrays written with gaps, or from their last index down, have holes where nothing was written.
 */
class ArrayHolesTest {

    @Test
    void holes() {
        String expected =
                "6 false false true -----1 [null,null,null,null,null,1] 5"
                        + " 1000 0 999 1000 2001 2000 1024 1023 3 6";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "var r = [];"
                        + "var a = []; a[5] = 1;"
                        + "r.push(a.length, 0 in a, 4 in a, 5 in a, a.join('-'), JSON.stringify(a),"
                        + "  Object.keys(a).join());"
                        + "var b = []; for (var i = 999; i >= 0; i--) b[i] = i;"
                        + "r.push(b.length, b[0], b[999], Object.keys(b).length);"
                        + "var c = []; c[2000] = 1; r.push(c.length, Object.keys(c).join());"
                        + "var d = [1, 2]; d[1023] = 3;"
                        + "r.push(d.length, d.indexOf(3), d.filter(x => true).length,"
                        + "  d.reduce((s, x) => s + x, 0));"
                        + "r.join(' ')");
    }

    @Test
    void holesReadThroughThePrototype() {
        Utils.assertGatedBehavior(
                "proto true false",
                "proto true false",
                "Object.prototype[3] = 'proto'; var e = []; e[10] = 1;"
                        + "var r = [e[3], 3 in e, e.hasOwnProperty(3)];"
                        + "delete Object.prototype[3]; r.join(' ')");
    }

    @Test
    void frozenTruncatedAndSorted() {
        Utils.assertGatedBehavior(
                "0 0 2  1 501",
                "0 0 2  1 501",
                "var r = [];"
                        + "var f = Object.freeze([]); try { f[4] = 1; } catch (x) {} r.push(f.length);"
                        + "var g = []; Object.preventExtensions(g); g[7] = 1; r.push(g.length);"
                        + "var h = []; h[900] = 'x'; h.length = 2; r.push(h.length, h[900]);"
                        + "var s = []; s[500] = 1; s.sort(); r.push(s[0], s.length);"
                        + "r.join(' ')");
    }
}
