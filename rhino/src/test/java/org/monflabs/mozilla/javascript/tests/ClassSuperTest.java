/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** super(), derived constructors, super.x and new.target in classes (plan 1.7b). */
class ClassSuperTest {

    private static final String PRELUDE =
            "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                    + " class A { constructor(x) { this.x = x; } m() { return 'Am' + this.x; }"
                    + " static s() { return 'As'; } get g() { return 'Ag'; } }";

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ECMASCRIPT(expected, PRELUDE + script + " r.join('|')");
        Utils.assertWithAllModes_ECMASCRIPT(expected, PRELUDE + script + " r.join('|')");
    }

    @Test
    void superCallBindsThis() {
        assertBoth(
                "5,6,true,true|ReferenceError|ReferenceError|ReferenceError|7|9",
                "t(function () { class B extends A { constructor() { super(5); this.y = 6; } }"
                        + " var b = new B(); return [b.x, b.y, b instanceof A, b.constructor === B].join(); });"
                        + " t(function () { class B extends A { constructor() { this.y = 1; super(1); } } new B(); });"
                        + " t(function () { class B extends A { constructor() { super(1); super(2); } } new B(); });"
                        + " t(function () { class B extends A { constructor() {} } new B(); });"
                        + " t(function () { class B extends A { constructor() { var f = () => super(7); f(); } }"
                        + " return new B().x; });"
                        + " t(function () { class B extends A { constructor(...a) { super(...a); } } return new B(9).x; });");
    }

    @Test
    void derivedConstructorResult() {
        assertBoth(
                "1|TypeError|true|object",
                "t(function () { class B extends A { constructor() { return {o: 1}; } } return new B().o; });"
                        + " t(function () { class B extends A { constructor() { super(1); return 3; } } new B(); });"
                        + " t(function () { class N extends null { constructor() { return Object.create(N.prototype); } }"
                        + " return new N() instanceof N; });"
                        + " t(function () { class C { constructor() { return 1; } } return typeof new C(); });");
    }

    @Test
    void superProperties() {
        assertBoth(
                "Bm/Am2,Bs/As,Bg/Ag|Am3|Amo",
                "t(function () { class B extends A { m() { return 'Bm/' + super.m(); }"
                        + " static s() { return 'Bs/' + super.s(); } get g() { return 'Bg/' + super.g; } }"
                        + " var b = new B(2); return [b.m(), B.s(), b.g].join(); });"
                        + " t(function () { class B extends A { constructor() { super(3); this.q = super.m(); } }"
                        + " return new B().q; });"
                        + " t(function () { class B extends A { m() { return super.m.call({x: 'o'}); } }"
                        + " return new B().m(); });");
    }

    @Test
    void newTargetAndDefaultConstructor() {
        assertBoth(
                "true|true|other|TypeError|3,true",
                "t(function () { var nt; class B extends A { constructor() { nt = new.target; super(1); } }"
                        + " class C extends B {} new C(); return nt === C; });"
                        + " t(function () { class B extends A { constructor() { var f = () => new.target; super(1);"
                        + " this.nt = f(); } } return new B().nt === B; });"
                        + " t(function () { class B extends A {} Object.setPrototypeOf(B, function () { this.x = 'other'; });"
                        + " return new B(1).x; });"
                        + " t(function () { class B extends null {} new B(); });"
                        + " t(function () { class B extends A {} var o = Reflect.construct(B, [3], Object);"
                        + " return [o.x, Object.getPrototypeOf(o) === Object.prototype].join(); });");
    }

    @Test
    void evalInDerivedConstructor() {
        assertBoth(
                "4|5|ReferenceError|SyntaxError",
                "t(function () { class B extends A { constructor() { super(4); this.e = eval('this.x'); } }"
                        + " return new B().e; });"
                        + " t(function () { class B extends A { constructor() { eval('super(5)'); } } return new B().x; });"
                        + " t(function () { class B extends A { constructor() { eval('this'); } } new B(); });"
                        + " t(function () { eval('class X { m() { super(); } }'); });");
    }

    @Test
    void builtinSubclasses() {
        assertBoth(
                "true|true|,true|TypeError|true",
                "t(function () { class W extends WeakRef {} return new W({}) instanceof W; });"
                        + " t(function () { class AB extends ArrayBuffer {} return new AB(4).slice(0, 1) instanceof AB; });"
                        + " t(function () { class O extends Object {} var o = new O({a: 1});"
                        + " return [o.a, Object.getPrototypeOf(o) === O.prototype].join(); });"
                        + " t(function () { class R extends RegExp {} return R.lastMatch; });"
                        + " t(function () { class E extends TypeError {} var e = new E('m');"
                        + " return e instanceof E && e instanceof TypeError && e.message === 'm'; });");
    }
}
