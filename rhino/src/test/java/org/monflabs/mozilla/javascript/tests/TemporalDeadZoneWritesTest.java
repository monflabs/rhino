/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * An assignment to a let/const binding in its temporal dead zone throws once its right-hand side is
 * evaluated (PutValue, SetMutableBinding); a compound assignment reads the binding first.
 */
class TemporalDeadZoneWritesTest {

    @Test
    void writesFromClosures() {
        Utils.assertGatedBehavior(
                "rhs,rhs+=,rhs-destr,rhs,rhs+=,2,rhs-const",
                "rhs,ReferenceError,ReferenceError,rhs-destr,ReferenceError,rhs,rhs+=,2,rhs-const,"
                        + "TypeError",
                "var log = [];"
                        + "function outer() {"
                        + "  function w() { c = (log.push('rhs'), 1); }"
                        + "  function u() { c += (log.push('rhs+='), 1); }"
                        + "  function v() { [c] = (log.push('rhs-destr'), [1]); }"
                        + "  try { w(); } catch (e) { log.push(e.name); }"
                        + "  try { u(); } catch (e) { log.push(e.name); }"
                        + "  try { v(); } catch (e) { log.push(e.name); }"
                        + "  let c = 0;"
                        + "  w(); u(); log.push(c);"
                        + "  const k = 1;"
                        + "  function wk() { k = (log.push('rhs-const'), 2); }"
                        + "  try { wk(); } catch (e) { log.push(e.name); }"
                        + "}"
                        + "outer(); log.join()");
    }

    @Test
    void writesInTheSameFunction() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "rhs,ReferenceError",
                "var log = [];"
                        + "(function () {"
                        + "  try { d = (log.push('rhs'), 1); } catch (e) { log.push(e.name); }"
                        + "  let d;"
                        + "})();"
                        + "log.join()");
    }
}
