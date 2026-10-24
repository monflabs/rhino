/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The length of an empty array set to 0 again keeps it dense: shift and pop on an empty array set
 * it, and a queue emptied then refilled stayed sparse, its shift copying every element through the
 * slot map (JSHint, in the Web Tooling Benchmark).
 */
class NativeArrayDenseTest {

    @ParameterizedTest
    @CsvSource({"200, true", "200, false", "250, true", "250, false"})
    void emptyArrayStaysDense(int version, boolean interpreted) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(version);
            cx.setInterpretedMode(interpreted);
            TopLevel scope = cx.initStandardObjects();
            Object result =
                    cx.evaluateString(
                            scope,
                            "var q = [], log = [];"
                                    + "for (var i = 0; i < 5; i++) {"
                                    + "  log.push(q.shift(), q.pop());"
                                    + "  q.length = 0;"
                                    + "  q[q.length] = i; q[q.length] = i + 1;"
                                    + "  log.push(q.shift(), q.shift());"
                                    + "}"
                                    + "q.push(7, 8); [q, log.join()]",
                            "dense.js",
                            1,
                            null);
            NativeArray pair = (NativeArray) result;
            NativeArray q = (NativeArray) pair.get(0, pair);
            assertTrue(q.getDenseOnly());
            assertEquals(2L, q.getLength());
            assertEquals(",,0,1,,,1,2,,,2,3,,,3,4,,,4,5", pair.get(1, pair));
        }
    }
}
