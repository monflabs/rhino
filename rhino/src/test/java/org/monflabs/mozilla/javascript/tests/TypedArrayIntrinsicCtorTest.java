/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Typed arrays create their buffers and copies with the intrinsic constructors. */
class TypedArrayIntrinsicCtorTest {

    private static String attempt(String expr) {
        return "(function() { try { return " + expr + "; } catch (e) { return e.name; } })()";
    }

    @Test
    void bufferOfNewTypedArray() {
        Utils.assertGatedBehavior(
                "InternalError,InternalError,InternalError",
                "2,3,true",
                "var A = ArrayBuffer; ArrayBuffer = null;"
                        + "["
                        + attempt("new Int8Array(2).buffer.byteLength")
                        + ","
                        + attempt("new Uint8Array([1, 2, 3]).length")
                        + ","
                        + attempt(
                                "Object.getPrototypeOf(new Float64Array(1).buffer) === A.prototype")
                        + "].join()");
    }

    @Test
    void copiesOfTypedArray() {
        Utils.assertGatedBehavior(
                "InternalError|InternalError|InternalError",
                "2,1,3|1,2,3|3,9,2",
                "var I = Int16Array, a = new I([3, 1, 2]); Int16Array = null;"
                        + "["
                        + attempt("a.toReversed().join()")
                        + ","
                        + attempt("a.toSorted().join()")
                        + ","
                        + attempt("a.with(1, 9).join()")
                        + "].join('|')");
    }
}
