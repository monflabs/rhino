/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The internal methods of typed arrays, ECMA-262 10.4.5 (plan 1.8a). */
class TypedArrayInternalsTest {

    private static final String PRELUDE =
            "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                    + " var ta = new Int8Array(3); ta.foo = 1;"
                    + " Object.defineProperty(Int8Array.prototype, '-0', { get() { throw new Error('proto'); },"
                    + " configurable: true });"
                    + " Object.defineProperty(Int8Array.prototype, '1.5', { set(v) { throw new Error('proto'); },"
                    + " configurable: true });";

    private static void assertGated(Object expectedES6, Object expected, String script) {
        Utils.assertGatedBehavior(expectedES6, expected, PRELUDE + script + " r.join('|')");
    }

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, PRELUDE + script + " r.join('|')");
        Utils.assertWithAllModes_ECMASCRIPT(expected, PRELUDE + script + " r.join('|')");
    }

    @Test
    void ownKeysAndDescriptors() {
        assertGated(
                "foo|0,1,2|TypeError|undefined",
                "0,1,2,foo|0,1,2,foo|0,true,true,true|undefined",
                "t(() => Reflect.ownKeys(ta).join());"
                        + " t(() => Object.keys(ta).join());"
                        + " t(() => { var d = Object.getOwnPropertyDescriptor(ta, '0');"
                        + " return [d.value, d.writable, d.enumerable, d.configurable].join(); });"
                        + " t(() => Object.getOwnPropertyDescriptor(ta, '5'));");
    }

    @Test
    void numericKeysDoNotReachThePrototype() {
        assertGated(
                "Error|true|true|Error|false",
                "undefined|false|false|undefined|true",
                "t(() => ta['-0']);"
                        + " t(() => '-0' in ta);"
                        + " t(() => '1.5' in ta);"
                        + " t(() => { ta['1.5'] = 7; return ta['1.5']; });"
                        + " t(() => { var o = Object.create(ta); o[0] = 5; return ta[0] === 0 && o.hasOwnProperty(0); });");
    }

    @Test
    void defineAndDelete() {
        assertGated(
                "0,0,0|0,0,0|9|false|true|true",
                "TypeError|TypeError|9|TypeError|true|true",
                "t(() => Object.defineProperty(ta, '0', { configurable: false }));"
                        + " t(() => Object.defineProperty(ta, '0', { get() {} }));"
                        + " t(() => { Object.defineProperty(ta, '1', { value: 9 }); return ta[1]; });"
                        + " t(function () { 'use strict'; return delete ta[0]; });"
                        + " t(function () { 'use strict'; return delete ta['-0']; });"
                        + " t(function () { 'use strict'; return delete ta[10]; });");
    }

    @Test
    void valueIsConvertedForAnInvalidIndex() {
        assertBoth(
                "1",
                "var calls = 0; ta[10] = { valueOf() { calls++; return 1; } }; r.push(calls);");
    }
}
