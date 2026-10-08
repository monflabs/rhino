/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Mapped and unmapped arguments objects (plan 1.2b, ECMA-262 10.4.4); the same in both versions.
 */
class ArgumentsObjectTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void mappedArgumentsAliasTheParameters() {
        assertBoth(
                "2,3",
                "(function (x) { arguments[0] = 2; var a = x; x = 3; return [a, arguments[0]].join(); })(1)");
    }

    @Test
    void nonWritableIndexIsNoLongerMapped() {
        assertBoth(
                1,
                "(function (x) { Object.defineProperty(arguments, '0', {writable: false});"
                        + " x = 5; return arguments[0]; })(1)");
    }

    @Test
    void nonConfigurableIndexKeepsItsOtherAttributesAndMapping() {
        assertBoth(
                "1/true/true/false,7",
                "(function (x) { Object.defineProperty(arguments, '0', {configurable: false});"
                        + " var d = Object.getOwnPropertyDescriptor(arguments, '0');"
                        + " x = 7; return [[d.value, d.writable, d.enumerable, d.configurable].join('/'),"
                        + " arguments[0]].join(); })(1)");
    }

    @Test
    void deletedIndexIsNoLongerMapped() {
        assertBoth(1, "(function (x) { delete arguments[0]; arguments[0] = 9; return x; })(1)");
    }

    @Test
    void unmappedArguments() {
        // strict functions and non-simple parameter lists
        assertBoth(1, "(function (x) { 'use strict'; x = 2; return arguments[0]; })(1)");
        assertBoth(1, "(function (x, ...rest) { x = 2; return arguments[0]; })(1)");
        assertBoth(1, "(function (x = 0) { x = 2; return arguments[0]; })(1)");
    }

    @Test
    void indexesAreOrdinaryOwnProperties() {
        assertBoth(
                "0|1|2,3",
                "(function () { return [Object.keys(arguments).join('|'), arguments.length].join(); })(1, 2, 3)");
    }
}
