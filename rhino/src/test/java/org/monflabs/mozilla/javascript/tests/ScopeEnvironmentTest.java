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
    void switchDiscriminant() {
        Utils.assertWithAllModes_ECMASCRIPT(
                2, "(function(x) { switch (x) { case 1: let x = 2; return x; } })(1)");
        Utils.assertWithAllModes_ECMASCRIPT(
                2, "(function(x) { switch (x) { case 1: let x = 2; return (() => x)(); } })(1)");
        Utils.assertWithAllModes_ECMASCRIPT(
                "outer",
                "(function() { var x = 'outer';"
                        + " return (function() {"
                        + " switch (x) { case 'outer': let x = 'inner'; } return x; })(); })()");
        Utils.assertWithAllModes_ECMASCRIPT(
                1,
                "(function(x) { switch ((() => x)()) { case 1: let x = 2; return 1; }"
                        + " return 0; })(1)");
        // the bindings of the clauses, declared by a pattern
        Utils.assertWithAllModes_ECMASCRIPT(
                3,
                "(function(x) { switch (x) { case 1: let [x, y] = [1, 2]; return x + y; } })(1)");
    }

    @Test
    void switchDiscriminantWithEval() {
        Utils.assertWithAllModes_ECMASCRIPT(
                2, "(function(x) { switch (x) { case 1: let x = 2; eval('1'); return x; } })(1)");
    }

    @Test
    void catchParameter() {
        Utils.assertWithAllModes_ECMASCRIPT(
                5, "var x = 'a'; try { throw {a: 5}; } catch ({[x]: v}) { let x; v; }");
        Utils.assertWithAllModes_ECMASCRIPT(
                5,
                "(function() { var x = 'a';"
                        + " try { throw {a: 5}; } catch ({[x]: v}) { let x = 1; return v; } })()");
        Utils.assertWithAllModes_ECMASCRIPT(
                6,
                "(function() { var x = 'a';"
                        + " try { throw {a: 5}; }"
                        + " catch ({[x]: v, w = () => x}) { let x = 1; return v + x; } })()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "a",
                "(function() { var x = 'a';"
                        + " try { throw {}; } catch ({w = () => x}) { let x = 1; return w(); } })()");
        // leaving the two scopes of the catch by a jump
        Utils.assertWithAllModes_ECMASCRIPT(
                3,
                "(function() { var n = 0;"
                        + " for (var i = 0; i < 3; i++) {"
                        + " try { throw [i]; } catch ([v]) { let f = () => v; n += f(); continue; }"
                        + " } return n; })()");
    }

    @Test
    void catchParameterWithEval() {
        Utils.assertWithAllModes_ECMASCRIPT(
                5, "var x = 'a'; try { throw {a: 5}; } catch ({[x]: v}) { let x; eval('1'); v; }");
    }

    @Test
    void forOfHead() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError",
                "(function() { var f;"
                        + " try { for (let q of (f = () => q, [1])) return f(); }"
                        + " catch (e) { return e.name; } })()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError",
                "(function() { var f;"
                        + " try { for (let q in (f = () => q, {a: 1})) return f(); }"
                        + " catch (e) { return e.name; } })()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError",
                "(function() { try { for (let q of [q]) return 1; }"
                        + " catch (e) { return e.name; } })()");
        // the closures of the body see the binding of their iteration
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2",
                "(function() { var f, g = [];"
                        + " for (let q of (f = () => q, [1, 2])) g.push(() => q);"
                        + " return g.map(h => h()).join(); })()");
    }

    @Test
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
