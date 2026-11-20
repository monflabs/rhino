/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The environments that code outside of a block or of a function body is evaluated in: the
 * discriminant of a switch, the parameter of a catch, the head of a for-in/of loop and the
 * parameter expressions of a function do not see the declarations that follow them.
 */
class ScopeEnvironmentTest {

    @Test
    @Disabled("the switch discriminant is renamed, not scoped: not with eval")
    void switchDiscriminantWithEval() {
        Utils.assertWithAllModes_ECMASCRIPT(
                2, "(function(x) { switch (x) { case 1: let x = 2; eval('1'); return x; } })(1)");
    }

    @Test
    @Disabled("the catch parameter is renamed, not scoped: not with eval")
    void catchParameterWithEval() {
        Utils.assertWithAllModes_ECMASCRIPT(
                5, "var x = 'a'; try { throw {a: 5}; } catch ({[x]: v}) { let x; eval('1'); v; }");
    }

    @Test
    @Disabled("the for-of head is renamed, not scoped: not with eval")
    void forOfHeadWithEval() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError",
                "(function() { var f;"
                        + " try { for (let q of (f = () => eval('q'), [1])) return f(); }"
                        + " catch (e) { return e.name; } })()");
    }

    @Test
    @Disabled("the body variables are renamed, not scoped: not with eval")
    void bodyVarWithEval() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "outside",
                "var x = 'outside';"
                        + " (function(g = () => x) { var x = 'inside'; eval('1'); return g(); })()");
    }

    @Test
    @Disabled("a body var with the name of a parameter is not separated")
    void bodyVarNamedAsParameter() {
        Utils.assertWithAllModes_ECMASCRIPT(
                1, "(function(a, g = () => a) { var a; a = 2; return g(); })(1)");
    }

    @Test
    @Disabled("a function declaration of the body is not separated")
    void bodyFunctionDeclaration() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "outside",
                "var x = 'outside';"
                        + " (function(g = () => x) { function x() {} return g(); })()");
    }

    @Test
    @Disabled("the renamed body variable collides with a variable of that name")
    void bodyVarNameCollision() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "inside/user",
                "var x = 'outside';"
                        + " (function(g = () => x) {"
                        + " var x = 'inside'; var x$body = 'user'; return x + '/' + x$body; })()");
    }
}
