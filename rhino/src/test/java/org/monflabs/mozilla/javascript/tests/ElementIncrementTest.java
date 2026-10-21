/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * An increment of an element whose index is itself an element read with a number index ({@code
 * r[d[1]]++}): compiled code took the index for a double ("Stack underflow" when compiling).
 */
class ElementIncrementTest {

    private static void assertAllVersions(String expected, String script) {
        Utils.assertGatedBehavior(expected, expected, script);
    }

    @Test
    void indexReadFromAnElement() {
        assertAllVersions(
                "-1,1 | 0,2,0 | 2 | 2",
                "function a() { var r = [0, 0]; var d = [0, 1]; r[d[1]]++; r[d[0]]--; return r.join(); }"
                        + "function b() { var r = [0, 0]; var d = [0, 1];"
                        + "  return [r[d[1]]++, ++r[d[1]], r[d[0]]--].join(); }"
                        + "function c() { var r = { k: 1 }; var d = ['k']; r[d[0]]++; return r.k; }"
                        + "function e() { var r = [0]; var i = 0; r[i]++; r[i + 0]++; return r[0]; }"
                        + "[a(), b(), c(), e()].join(' | ')");
    }
}
