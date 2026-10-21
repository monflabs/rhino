/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The let/const bindings of the clauses of a switch are in their temporal dead zone in the clauses
 * entered past their declaration (BACKWARD_COMPATIBILITY.md 1.91), whether they live in a scope
 * object or in registers.
 */
class SwitchLexicalTest {

    @Test
    void clausesEnteredPastTheDeclaration() {
        Utils.assertGatedBehavior(
                "write=3; closure read=undefined; loop re-entry=0,,2; default=undefined; same case=6; fall through=5",
                "write!ReferenceError; closure read!ReferenceError; loop re-entry=0,ReferenceError,2; default=ReferenceError; same case=6; fall through=5",
                "var out = []; "
                        + "function t(label, f) { try { out.push(label + '=' + f()); } catch (e) { out.push(label + '!' + e.name); } } "
                        + "t('write', function () { switch (1) { case 0: let s = 5; case 1: s = 3; return s; } }); "
                        + "t('closure read', function () { var g = function () {}; switch (1) { case 0: let s = 5; case 1: return s; } }); "
                        + "t('loop re-entry', function () { var r = []; for (var k = 0; k < 3; k++) { try { switch (k % 2) { case 0: let s = k; r.push(s); break; case 1: r.push(s); } } catch (e) { r.push(e.name); } } return r.join(); }); "
                        + "t('default', function () { switch (9) { case 0: let d = 1; default: try { return d; } catch (e) { return e.name; } } }); "
                        + "t('same case', function () { switch (0) { case 0: let s = 5; return s + 1; } }); "
                        + "t('fall through', function () { switch (0) { case 0: let s = 5; case 1: return s; } }); "
                        + "out.join('; ') ");
    }

    @Test
    void readsWritesAndTypeof() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "reg read!ReferenceError; reg typeof!ReferenceError; reg write!ReferenceError; reg const!ReferenceError; obj read!ReferenceError; obj typeof!ReferenceError; obj write!ReferenceError; closure read!ReferenceError | top-level ReferenceError",
                "var out = []; "
                        + "function t(label, f) { try { out.push(label + '=' + f()); } catch (e) { out.push(label + '!' + e.name); } } "
                        + "t('reg read', function () { switch (1) { case 0: let s = 5; case 1: return s; } }); "
                        + "t('reg typeof', function () { switch (1) { case 0: let s = 5; case 1: return typeof s; } }); "
                        + "t('reg write', function () { switch (1) { case 0: let s = 5; case 1: s = 3; return s; } }); "
                        + "t('reg const', function () { switch (1) { case 0: const s = 5; case 1: return s; } }); "
                        + "t('obj read', function () { var g = function () {}; switch (1) { case 0: let s = 5; case 1: return s; } }); "
                        + "t('obj typeof', function () { var g = function () {}; switch (1) { case 0: let s = 5; case 1: return typeof s; } }); "
                        + "t('obj write', function () { var g = function () {}; switch (1) { case 0: let s = 5; case 1: s = 3; return s; } }); "
                        + "t('closure read', function () { switch (1) { case 0: let s = 5; case 1: return (function () { return s; })(); } }); "
                        + "var first = out.join('; '); "
                        + "var top = []; "
                        + "switch (1) { case 0: let ts = 5; case 1: try { top.push(ts); } catch (e) { top.push(e.name); } } "
                        + "first + ' | top-level ' + top.join() ");
    }

    @Test
    void initializedBindings() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "same case=6; fall through=5; typeof after=number; incr after=8; incr before!ReferenceError; loop re-entry=0,ReferenceError,2; nested!ReferenceError; default=ReferenceError; const fall=3; closure=4; closure tdz=ReferenceError; with activation same=10; with activation fall=7; number var=42 | top-level ReferenceError,5",
                "var out = []; "
                        + "function t(label, f) { try { out.push(label + '=' + f()); } catch (e) { out.push(label + '!' + e.name); } } "
                        + "var keep = function () { return 0; }; "
                        + "t('same case', function () { switch (0) { case 0: let s = 5; return s + 1; } }); "
                        + "t('fall through', function () { switch (0) { case 0: let s = 5; case 1: return s; } }); "
                        + "t('typeof after', function () { switch (0) { case 0: let s = 5; return typeof s; } }); "
                        + "t('incr after', function () { switch (0) { case 0: let s = 5; s++; s += 2; return s; } }); "
                        + "t('incr before', function () { switch (1) { case 0: let s = 5; case 1: s++; return s; } }); "
                        + "t('loop re-entry', function () { var r = []; for (var k = 0; k < 3; k++) { try { switch (k % 2) { case 0: let s = k; r.push(s); break; case 1: r.push(s); } } catch (e) { r.push(e.name); } } return r.join(); }); "
                        + "t('nested', function () { switch (0) { case 0: let a = 1; switch (1) { case 0: let b = 2; case 1: return a + (typeof b); } } }); "
                        + "t('default', function () { switch (9) { case 0: let d = 1; default: try { return d; } catch (e) { return e.name; } } }); "
                        + "t('const fall', function () { var r = 0; for (var k = 0; k < 3; k++) { switch (0) { case 0: const c = k; r += c; } } return r; }); "
                        + "t('closure', function () { var fs = []; switch (0) { case 0: let s = 4; fs.push(function () { return s; }); } return fs[0](); }); "
                        + "t('closure tdz', function () { switch (1) { case 0: let s = 4; case 1: try { return (function () { return s; })(); } catch (e) { return e.name; } } }); "
                        + "t('with activation same', function () { var g = function () {}; switch (0) { case 0: let s = 5; s = s * 2; return s; } }); "
                        + "t('with activation fall', function () { var g = function () {}; switch (0) { case 0: let s = 5; case 1: s = 7; return s; } }); "
                        + "t('number var', function () { var x = 0; for (var i = 0; i < 10; i++) { switch (i % 3) { case 0: let n = i * 2; x += n; break; default: x += 1; } } return x; }); "
                        + "var first = out.join('; '); "
                        + "var top = []; "
                        + "switch (1) { case 0: let ts = 5; case 1: try { ts = 3; top.push(ts); } catch (e) { top.push(e.name); } } "
                        + "switch (0) { case 0: let tu = 5; case 1: top.push(tu); } "
                        + "first + ' | top-level ' + top.join() ");
    }
}
