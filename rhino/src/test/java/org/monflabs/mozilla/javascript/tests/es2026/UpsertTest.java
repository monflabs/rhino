/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2026;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Map and WeakMap getOrInsert and getOrInsertComputed (plan 12.3). */
class UpsertTest {

    @Test
    void gated() {
        Utils.assertGatedBehavior(
                "undefined,undefined",
                "function,function",
                "[typeof Map.prototype.getOrInsertComputed,"
                        + " typeof WeakMap.prototype.getOrInsert].join()");
    }

    @Test
    void map() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1|1|2|0:7,a:1,b:3|true|TypeError",
                "var m = new Map([['a', 1]]), r = [];\n"
                        + "r.push(m.getOrInsert('a', 9), m.get('a'), m.getOrInsert('z', 2));\n"
                        + "m.delete('z');\n"
                        + "m.getOrInsertComputed(-0, k => 7);\n"
                        + "m.getOrInsertComputed('b', k => { m.set('b', 'x'); return 3; });\n"
                        + "r.push([...m].map(e => (Object.is(e[0], 0) ? 0 : e[0]) + ':' + e[1]).sort().join());\n"
                        + "r.push(m.getOrInsertComputed('a', () => { throw 1; }) === 1);\n"
                        + "try { m.getOrInsertComputed('q', 1); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }

    @Test
    void weakMap() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1|2|o|TypeError|TypeError",
                "var w = new WeakMap(), k = {}, r = [];\n"
                        + "r.push(w.getOrInsert(k, 1), w.getOrInsert({}, 2));\n"
                        + "r.push(w.getOrInsertComputed({}, key => 'o'));\n"
                        + "try { w.getOrInsert(1, 1); } catch (e) { r.push(e.name); }\n"
                        + "try { w.getOrInsertComputed(1, 'no'); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }
}
