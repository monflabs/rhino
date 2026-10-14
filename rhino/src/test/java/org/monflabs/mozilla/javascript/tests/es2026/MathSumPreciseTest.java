/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2026;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Math.sumPrecise (plan 12.2). */
class MathSumPreciseTest {

    @Test
    void gated() {
        Utils.assertGatedBehavior("undefined", "function", "typeof Math.sumPrecise");
    }

    @Test
    void exactSums() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1|0.30000000000000004|1.7976931348623157e+308|Infinity|true|3",
                "var M = Number.MAX_VALUE, ulp = Math.pow(2, 971);\n"
                        + "[Math.sumPrecise([1e20, 1, -1e20]), Math.sumPrecise([0.1, 0.2]),"
                        + " Math.sumPrecise([M, M, -M]), Math.sumPrecise([M, ulp / 2]),"
                        + " Math.sumPrecise([5e-324, 5e-324]) === 1e-323,"
                        + " Math.sumPrecise(new Set([1, 2]))].join('|')");
    }

    @Test
    void specialValues() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|true|true|Infinity|-Infinity|NaN|NaN",
                "[Object.is(Math.sumPrecise([]), -0), Object.is(Math.sumPrecise([-0, -0]), -0),"
                        + " Object.is(Math.sumPrecise([1, -1]), 0), Math.sumPrecise([Infinity, 1]),"
                        + " Math.sumPrecise([-Infinity, -1e308, -1e308]),"
                        + " Math.sumPrecise([Infinity, -Infinity]), Math.sumPrecise([1, NaN])].join('|')");
    }

    @Test
    void onlyNumbers() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError,TypeError,TypeError,1",
                "var r = [], closed = 0;\n"
                        + "try { Math.sumPrecise([1, '2']); } catch (e) { r.push(e.name); }\n"
                        + "try { Math.sumPrecise([1n]); } catch (e) { r.push(e.name); }\n"
                        + "var it = { [Symbol.iterator]() { return { next() { return { value: {} } },"
                        + " return() { closed++; return {}; } }; } };\n"
                        + "try { Math.sumPrecise(it); } catch (e) { r.push(e.name); }\n"
                        + "r.push(closed); r.join()");
    }
}
