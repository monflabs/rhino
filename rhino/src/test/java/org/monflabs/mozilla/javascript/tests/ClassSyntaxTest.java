/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Class declarations and expressions (plan 1.7a); the same in both versions. */
class ClassSyntaxTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void methodsAccessorsAndStatics() {
        assertBoth(
                "3,6,4,s,function,A,1|0,constructor/m|dyn12|own",
                "class A { constructor(x) { this.x = x; } get dbl() { return this.x * 2; }"
                        + " m() { return this.x + 1; } static s() { return 's'; } }"
                        + " var a = new A(3); var r = [[a.x, a.dbl, a.m(), A.s(), typeof A, A.name, A.length].join()];"
                        + " class B { m() {} }"
                        + " r.push([Object.keys(B.prototype).length, Object.getOwnPropertyNames(B.prototype).join('/')].join());"
                        + " var k = 'dyn'; class D { [k + '1']() { return 1; } static [k]() { return 2; } }"
                        + " r.push(k + new D().dyn1() + D.dyn());"
                        + " r.push((class { static name() { return 'own'; } }).name());"
                        + " r.join('|')");
    }

    @Test
    void constructorAndPrototype() {
        assertBoth(
                "TypeError|TypeError|false,false,false|true|1,true,true",
                "var r = []; class A {} try { A(); } catch (e) { r.push(e.name); }"
                        + " try { new (class { m() {} }).prototype.m(); } catch (e) { r.push(e.name); }"
                        + " var d = Object.getOwnPropertyDescriptor(class {}, 'prototype');"
                        + " r.push([d.writable, d.enumerable, d.configurable].join());"
                        + " r.push(A.prototype.constructor === A);"
                        + " class P { constructor() { this.v = 1; } } class Q extends P {} var q = new Q();"
                        + " r.push([q.v, q instanceof P, Object.getPrototypeOf(Q) === P].join());"
                        + " r.join('|')");
    }

    @Test
    void nameBindingsAndStrictMode() {
        assertBoth(
                "Named,undefined|1|undefined|1,16,3,4",
                "var r = []; var B = class Named { who() { return Named.name; } };"
                        + " r.push(new B().who() + ',' + typeof Named);"
                        + " class A { m() { return x; } } let x = 1; r.push(new A().m());"
                        + " class S { m() { return this; } } r.push(S.prototype.m.call(undefined));"
                        + " var o = { 1n: 1, 0x10n: 2 }; class N { 2n() { return 3; } static 3n() { return 4; } }"
                        + " r.push([Object.keys(o), new N()['2'](), N['3']()].join());"
                        + " r.join('|')");
    }

    /** The name of a class expression over a binding of the same name in the function. */
    @Test
    void nameShadowsAnOuterBinding() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|function,undefined|5|function|function,1",
                "var r = [];"
                        + " r.push((function () { var L; L = class L { m() { return L; } };"
                        + " return new L().m() === L; })());"
                        + " r.push((function () { var L; var M = class L { get g() { return 1; } };"
                        + " return [typeof M, typeof L]; })());"
                        + " r.push((function () { var L = 5; (class L { x = 1 }); return L; })());"
                        + " r.push((function (L) { L = class L { m() {} }; return typeof L; })());"
                        + " r.push((function () { let L; L = class L { static s = 1 };"
                        + " return [typeof L, (() => L.s)()]; })());"
                        + " r.join('|')");
    }

    @Test
    void earlyErrors() {
        assertBoth(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,TypeError",
                "var r = []; function t(s) { try { eval(s); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t('class A { constructor() {} constructor() {} }'); t('class A { get constructor() {} }');"
                        + " t('class A { static prototype() {} }'); t('class {}'); t('class A extends B, C {}');"
                        + " t('class A { m() { var yield; } }');"
                        + " t('var C = class { static [\"prototype\"]() {} }');"
                        + " r.join()");
    }

    @Test
    void declarationsAreNotStatementBodies() {
        assertBoth(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,ok",
                "var r = []; function t(s) { try { eval(s); r.push('ok'); } catch (e) { r.push(e.name); } }"
                        + " t('l: class C {}'); t('with ({}) class C {}'); t('if (1) class C {}');"
                        + " t('while (0) class C {}'); t('l: { class C {} }');"
                        + " r.join()");
    }

    @Test
    void computedConstructorIsAMethod() {
        assertBoth(
                "true,1,2",
                "class C { ['constructor']() { return 1; } }"
                        + " class D { get ['constructor']() { return 2; } }"
                        + " [C !== C.prototype.constructor, new C().constructor(), new D().constructor].join()");
    }

    @Test
    void staticAccessorsAndOverriddenBuiltins() {
        assertBoth(
                "g,s|function,function|false,false",
                "var r = [], v; class C { static get ['a']() { return 'g'; } static set ['a'](x) { v = x; } }"
                        + " C.a = 's'; r.push(C.a + ',' + v);"
                        + " class D { static length() {} static name() {} } r.push(typeof D.length + ',' + typeof D.name);"
                        + " var d = Object.getOwnPropertyDescriptor(class { get a() {} }.prototype, 'a');"
                        + " var o = Object.getOwnPropertyDescriptor({ set b(x) {} }, 'b');"
                        + " r.push(['prototype' in d.get, 'prototype' in o.set].join()); r.join('|')");
    }

    @Test
    void namedEvaluation() {
        assertBoth(
                "a,b,c,d,e,f",
                "var a = class {}; let b = class { constructor() {} }; var o = { c: class {} };"
                        + " var [d = class {}] = []; function g(e = class {}) { return e; } var f; f = class {};"
                        + " [a.name, b.name, o.c.name, d.name, g().name, f.name].join()");
    }

    @Test
    void innerNameIsImmutable() {
        assertBoth(
                "TypeError,TypeError,2",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(function () { class C { constructor() { C = 1; } } new C(); });"
                        + " t(function () { class C { m() { C = 1; } } new C().m(); });"
                        + " t(function () { class C {} C = 2; return C; }); r.join()");
    }

    @Test
    void heritageChecks() {
        assertBoth(
                "TypeError,TypeError,1",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(function () { class A extends (() => {}).bind() {} });"
                        + " t(function () { class A extends new Proxy(() => {}, { get() { throw 1; } }) {} });"
                        + " t(function () { function B() {} B.prototype = { set m(v) { throw 2; } };"
                        + " class C extends B { m() { return 1; } } return new C().m(); }); r.join()");
    }
}
