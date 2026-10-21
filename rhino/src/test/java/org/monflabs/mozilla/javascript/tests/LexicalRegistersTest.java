/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The let/const bindings that a function with an activation object keeps in registers when no
 * nested function refers to them (VERSION_ECMASCRIPT): temporal dead zone, shadowing, loops with
 * and without closures, and the cases that keep a scope object.
 */
class LexicalRegistersTest {

    @Test
    void lexicalBindings() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "tdz read!ReferenceError; "
                        + "tdz typeof!ReferenceError; "
                        + "tdz write!ReferenceError; "
                        + "tdz self!ReferenceError; "
                        + "shadow=1; "
                        + "siblings=1,2; "
                        + "var and let=l,v; "
                        + "let reset=||; "
                        + "const reassign!TypeError; "
                        + "const block reassign!TypeError; "
                        + "loop closures=10; "
                        + "loop capture=0,1,2; "
                        + "mixed head=10,9,8; "
                        + "for of=6; "
                        + "for in=pq; "
                        + "for of closures=3; "
                        + "destructuring=6; "
                        + "labels=2; "
                        + "try finally=3; "
                        + "eval sees=4; "
                        + "captured other=3; "
                        + "generator=3; "
                        + "nested fn name=function5; "
                        + "class decl=1; "
                        + "const in loop=6; "
                        + "let after closure creation=2",
                "var out = []; "
                        + "function run(label, f) { try { out.push(label + '=' + f()); } catch (e) { out.push(label + '!' + e.name); } } "
                        + "var keep = function () { return 0; }; "
                        + "run('tdz read', function () { var g = keep; x; let x = 1; return x; }); "
                        + "run('tdz typeof', function () { var g = keep; var t = typeof y; let y = 1; return t; }); "
                        + "run('tdz write', function () { var g = keep; z = 2; let z = 1; return z; }); "
                        + "run('tdz self', function () { var g = keep; let w = w + 1; return w; }); "
                        + "run('shadow', function () { var g = keep; let a = 1; { let a = 2; { let a = 3; } } return a; }); "
                        + "run('siblings', function () { var g = keep; var r = []; { let b = 1; r.push(b); } { let b = 2; r.push(b); } return r.join(); }); "
                        + "run('var and let', function () { var g = keep; var c = 'v'; var r = []; { let c = 'l'; r.push(c); } r.push(c); return r.join(); }); "
                        + "run('let reset', function () { var g = keep; var r = []; for (var i = 0; i < 3; i++) { let u; r.push(u); u = i; } return r.join('|'); }); "
                        + "run('const reassign', function () { var g = keep; const k = 1; k = 2; return k; }); "
                        + "run('const block reassign', function () { var g = keep; { const k = 1; k++; } return 0; }); "
                        + "run('loop closures', function () { var r = []; for (let i = 0; i < 3; i++) { r.push(function () { return 7; }); } return r.length + r[0](); }); "
                        + "run('loop capture', function () { var r = []; for (let i = 0; i < 3; i++) { r.push(function () { return i; }); } return r.map(function (f) { return f(); }).join(); }); "
                        + "run('mixed head', function () { var r = []; for (let i = 0, j = 10; i < 3; i++, j--) { r.push(function () { return j; }); } return r.map(function (f) { return f(); }).join() ; }); "
                        + "run('for of', function () { var g = keep; var s = 0; for (const v of [1, 2, 3]) { s += v; } return s; }); "
                        + "run('for in', function () { var g = keep; var s = ''; for (let k in { p: 1, q: 2 }) { s += k; } return s; }); "
                        + "run('for of closures', function () { var r = []; for (const v of [1, 2]) { r.push(function () { return v; }); } return r[0]() + r[1](); }); "
                        + "run('destructuring', function () { var g = keep; let [d, e] = [1, 2]; const { f } = { f: 3 }; return d + e + f; }); "
                        + "run('labels', function () { var g = keep; var n = 0; outer: for (let i = 0; i < 3; i++) { for (let j = 0; j < 3; j++) { if (j == 1) continue outer; if (i == 2) break outer; n++; } } return n; }); "
                        + "run('try finally', function () { var g = keep; let t = 1; try { let t = 2; throw t; } catch (e) { let q = e + t; return q; } finally { t = 9; } }); "
                        + "run('eval sees', function () { var g = keep; let m = 4; return eval('m'); }); "
                        + "run('captured other', function () { let a = 1; let b = 2; var g = function () { return a; }; b += g(); return b; }); "
                        + "run('generator', function () { function* gen() { let x = 1; yield x; let y = x + 1; yield y; } var it = gen(); return it.next().value + it.next().value; }); "
                        + "run('nested fn name', function () { let h = 5; var f = function h() { return typeof h; }; return f() + h; }); "
                        + "run('class decl', function () { var g = keep; class C { m() { return 1; } } let c = new C(); return c.m(); }); "
                        + "run('const in loop', function () { var g = keep; var s = 0; for (var i = 0; i < 3; i++) { const x = i * 2; s += x; } return s; }); "
                        + "run('let after closure creation', function () { var f = function () { return 1; }; let late = f(); { let late2 = late + 1; return late2; } }); "
                        + "out.join('; ') ");
    }
}
