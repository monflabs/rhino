/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** this, caller and bind for strict functions (plan 1.2a); the same under both versions. */
class StrictFunctionSemanticsTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void thisOfAStrictFunctionCalledWithoutReceiverIsUndefined() {
        Utils.assertGatedBehavior(
                "object,true",
                "undefined,true",
                "'use strict'; function f() { return [typeof this, this === undefined].join(); } f()");
    }

    @Test
    void callAndApplyPassAPrimitiveThisToAStrictFunction() {
        Utils.assertGatedBehavior(
                "object,object,object,object,object",
                "number,string,boolean,object,undefined",
                "'use strict'; function f() { return typeof this; }"
                        + " [f.call(1), f.apply('s'), f.call(true), f.call(null), f.call(undefined)].join()");
        // a non-strict function still gets an object
        assertBoth("object", "function g() { return typeof this; } g.call(1)");
    }

    @Test
    void callerOfStrictFunctionsAndBuiltinsThrows() {
        Utils.assertGatedBehavior(
                "undefined",
                "TypeError,TypeError,undefined",
                "var r = [];"
                        + " (function () { 'use strict'; function s() {}"
                        + "   try { s.caller; } catch (e) { r.push(e.name); } })();"
                        + " try { Math.max.caller; } catch (e) { r.push(e.name); }"
                        + " function sloppy() { return sloppy.caller; } r.push(String(sloppy()));"
                        + " r.join()");
    }

    @Test
    void boundFunctions() {
        Utils.assertGatedBehavior(
                "bound ,bound bound ,false,false,3",
                "bound target,bound bound target,true,true,-1",
                "var t = Object.defineProperty(function () {}, 'name', {value: 'target'});"
                        + " function s() { 'use strict'; return this === undefined; }"
                        + " [t.bind().name, t.bind().bind().name, s.bind()(), s.bind(undefined)(),"
                        + " Object.getOwnPropertyNames(t.bind()).indexOf('caller')].join()");
    }
}
