/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Changes of built-ins without a BACKWARD_COMPATIBILITY.md example of their own, gated by
 * VERSION_ECMASCRIPT (plan G): VERSION_ES6 keeps the stock behavior.
 */
class LegacyBuiltinsGateTest {

    @Test
    void subclassingWithoutClasses() {
        // ArrayBuffer[Symbol.species], the RegExp legacy statics through another object,
        // Object(value) with a NewTarget
        Utils.assertGatedBehavior(
                "undefined,,true",
                "function,TypeError,false",
                "[typeof ArrayBuffer[Symbol.species],"
                        + " (function () { try { return Object.setPrototypeOf(function () {}, RegExp)"
                        + ".lastMatch; } catch (e) { return e.name; } })(),"
                        + " Reflect.construct(Object, [1], function F() {}) instanceof Number].join()");
    }

    @Test
    void literalNameInference() {
        // only functions written as a property value are named after the key
        Utils.assertGatedBehavior("x", "", "var f = (0, function () {}); var o = { x: f }; f.name");
    }
}
