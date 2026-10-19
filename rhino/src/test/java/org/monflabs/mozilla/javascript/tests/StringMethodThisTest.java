/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A method called on a primitive string receives the string itself as this under
 * VERSION_ECMASCRIPT, a String object before.
 */
class StringMethodThisTest {

    @Test
    void builtInsDoNotCallAnOverriddenToString() {
        Utils.assertGatedBehavior(
                "x -1 Xd",
                "abc 1 abcd",
                "String.prototype.toString = function () { return 'X'; };"
                        + "String.prototype.valueOf = function () { return 'Y'; };"
                        + "['abc'.toLowerCase(), 'abc'.indexOf('b'), 'abc'.concat('d')].join(' ')");
    }

    @Test
    void userFunctionsAccessorsAndMissingMethods() {
        Utils.assertGatedBehavior(
                "object:true object acc TypeError",
                "object:true string acc TypeError",
                "String.prototype.sloppy = function () {"
                        + "  return typeof this + ':' + (this instanceof String); };"
                        + "String.prototype.strict = function () { 'use strict'; return typeof this; };"
                        + "Object.defineProperty(String.prototype, 'acc', {"
                        + "  get() { return function () { return 'acc'; }; } });"
                        + "var e; try { 'abc'.nope(); } catch (x) { e = x.name; }"
                        + "['abc'.sloppy(), 'abc'.strict(), 'abc'.acc(), e].join(' ')");
    }

    @Test
    void methodsOfObjectPrototypeAndThisStringValue() {
        String expected = "true abc abc q c";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "['abc'.hasOwnProperty('length'), 'abc'.toString(), 'abc'.valueOf(),"
                        + " String.prototype.toString.call('q'), 'abc'.at(-1)].join(' ')");
    }
}
