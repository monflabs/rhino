/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Early errors of object literals and strict directives (plan 1.5a); the same in both versions. */
class ObjectLiteralSyntaxTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    private static String errorNames(String... sources) {
        StringBuilder sb = new StringBuilder("var r = [];");
        for (String src : sources) {
            sb.append(" try { eval(\"")
                    .append(src.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"); r.push('ok'); } catch (e) { r.push(e.name); }");
        }
        return sb.append(" r.join()").toString();
    }

    @Test
    void accessorParameters() {
        assertBoth(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,ok,ok",
                errorNames(
                        "({ get a(x) {} })",
                        "({ set a() {} })",
                        "({ set a(x, y) {} })",
                        "({ set a(...x) {} })",
                        "({ get a() {}, set a(x) {} })",
                        "({ set a([x, y] = []) {} })"));
    }

    @Test
    void restParameterWithoutInitializer() {
        assertBoth(
                "SyntaxError,SyntaxError",
                errorNames("(function (...x = []) {})", "({ m(...x = []) {} })"));
    }

    @Test
    void escapedGetAndSetAreNotAccessorKeywords() {
        assertBoth("SyntaxError,ok", errorNames("({ g\\u0065t m() {} })", "({ g\\u0065t: 1 })"));
    }

    @Test
    void duplicateProtoDefinitions() {
        assertBoth(
                "SyntaxError,ok,ok",
                errorNames(
                        "({ __proto__: null, other: null, '__proto__': null })",
                        "({ __proto__: x, __proto__: y } = {})",
                        "({ __proto__: null, ['__proto__']: null })"));
    }

    @Test
    void strictDirectiveAppliesToTheNextToken() {
        assertBoth(
                "SyntaxError,SyntaxError",
                errorNames(
                        "'use strict'; public = 1", "(function () { 'use strict'; public = 1; })"));
    }

    @Test
    void inIsAllowedInAComputedKeyOfAForHead() {
        assertBoth(
                "g",
                "var empty = Object.create(null); var obj;"
                        + " for (obj = { get ['x' in empty]() { return 'g'; } }; ; ) { break; } obj.false");
    }
}
