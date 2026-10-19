/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The compiled code of an object literal holds the object in a local while it creates the methods
 * of the literal, their home object: the local is released after the literal.
 */
class ObjectLiteralLocalsTest {

    @Test
    void manyLiteralsInAFunction() {
        StringBuilder script = new StringBuilder("function f() { var n = 0, a = [];");
        for (int i = 0; i < 1200; i++) {
            script.append("a.push({ v: ").append(i).append(" });");
        }
        script.append("var get = function () { return a.length; };")
                .append("return get() + ',' + a[1199].v; }")
                .append("f()");
        String expected = "1200,1199";
        Utils.assertGatedBehavior(expected, expected, script.toString());
    }

    @Test
    void homeObjectAfterANestedLiteral() {
        String expected = "outer inner";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "var o = { a: { m() { return super.y; } }, m() { return super.y; } };"
                        + "Object.setPrototypeOf(o, { y: 'outer' });"
                        + "Object.setPrototypeOf(o.a, { y: 'inner' });"
                        + "o.m() + ' ' + o.a.m()");
    }
}
