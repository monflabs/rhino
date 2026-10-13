/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2022;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Private fields, methods and accessors, instance and static, and #x in obj (plan 8.2). */
class PrivateNamesTest {

    @Test
    void instanceElements() {
        Utils.assertWithAllModes_ES6(
                "7,70,107,#m,[]",
                "class A {\n"
                        + "  #x = 1;\n"
                        + "  #m() { return this.#x * 10; }\n"
                        + "  get #a() { return this.#x + 100; }\n"
                        + "  set #a(v) { this.#x = v; }\n"
                        + "  test() {\n"
                        + "    this.#x++; this.#x += 2; this.#a = 7;\n"
                        + "    return [this.#x, this.#m(), this.#a, this.#m.name,\n"
                        + "        JSON.stringify(Object.keys(this))].join();\n"
                        + "  }\n"
                        + "}\n"
                        + "new A().test()");
        Utils.assertWithAllModes_ES6(
                "9,8,true",
                "class G { #x; #y; #f = () => this;\n"
                        + "  m() { [this.#x] = [9]; ({ y: this.#y } = { y: 8 });\n"
                        + "    return [this.#x, this.#y, this.#f() === this].join(); } }\n"
                        + "new G().m()");
        Utils.assertWithAllModes_ES6(
                ",5,6",
                "class E { #x = 5; #m() { return 6; }\n"
                        + "  static f(o) { return [o?.#x, o?.#x, o?.#m()]; } }\n"
                        + "[E.f(null)[0], E.f(new E())[1], E.f(new E())[2]].join()");
    }

    @Test
    void staticElements() {
        Utils.assertWithAllModes_ES6(
                "s,S,true,false",
                "class A { static #s = 's'; static #m() { return A.#s.toUpperCase(); }\n"
                        + "  static t() { return [A.#s, A.#m(), #s in A, #s in new A()]; } }\n"
                        + "A.t().join()");
    }

    @Test
    void brandChecks() {
        Utils.assertWithAllModes_ES6(
                "true,false,TypeError,TypeError,TypeError,TypeError,TypeError",
                "function err(f) { try { f(); return 'none'; } catch (e) { return e.name; } }\n"
                        + "class A { #x = 1; #m() {} get #g() { return 1; } set #s(v) {}\n"
                        + "  static has(o) { return #x in o; }\n"
                        + "  static get(o) { return o.#x; }\n"
                        + "  static t(a) { return [err(() => A.get({})), err(() => { a.#m = 1; }),\n"
                        + "      err(() => { a.#g = 1; }), err(() => a.#s), err(() => #x in 1)]; } }\n"
                        + "var a = new A();\n"
                        + "[A.has(a), A.has({})].concat(A.t(a)).join()");
        // each evaluation of a class creates new private names
        Utils.assertWithAllModes_ES6(
                "true,false",
                "function make() { return class { #x; static has(o) { return #x in o; } }; }\n"
                        + "var C1 = make(), C2 = make();\n"
                        + "[C1.has(new C1()), C1.has(new C2())].join()");
    }

    @Test
    void returnOverride() {
        // the fields of a derived class are added to the object the base constructor returns
        Utils.assertWithAllModes_ES6(
                "1,true,TypeError",
                "class Base { constructor(o) { return o; } }\n"
                        + "class Stamp extends Base { #x = 1; static get(o) { return o.#x; } }\n"
                        + "var o = Object.freeze({});\n"
                        + "new Stamp(o);\n"
                        + "var r; try { new Stamp(o); } catch (e) { r = e.name; }\n"
                        + "[Stamp.get(o), Object.isFrozen(o), r].join()");
    }

    @Test
    void nestedClassesAndEval() {
        Utils.assertWithAllModes_ES6(
                "1,2,3",
                "class Outer { #x = 1;\n"
                        + "  m() { var self = this;\n"
                        + "    class Inner { #y = 2; f(o) { return [self.#x, this.#y]; } }\n"
                        + "    return new Inner().f().concat(eval('this.#x + 2')); } }\n"
                        + "new Outer().m().join()");
    }

    @Test
    void earlyErrors() {
        for (String source :
                new String[] {
                    "this.#x",
                    "class A { m() { this.#y; } }",
                    "class A { #x; #x; }",
                    "class A { get #x() {} get #x() {} }",
                    "class A { static get #x() {} set #x(v) {} }",
                    "class A { #constructor; }",
                    "class A { #x; m() { delete this.#x; } }",
                    "class A { #x; m() { return super.#x; } }",
                    "class A { #x; m() { return #x + 1; } }",
                    "({ #x: 1 })"
                }) {
            Utils.runWithAllModes(
                    cx -> {
                        cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                        TopLevel scope = cx.initStandardObjects();
                        assertThrows(
                                EvaluatorException.class,
                                () -> cx.evaluateString(scope, source, "test", 1, null),
                                source);
                        return null;
                    });
        }
        Utils.assertWithAllModes_ES6(
                true,
                "class A { get #x() { return 1; } set #x(v) {} t() { return this.#x === 1; } }\n"
                        + "new A().t()");
        // in eval code
        Utils.assertWithAllModes_ES6(
                "SyntaxError,SyntaxError",
                "function err(f) { try { f(); return 'none'; } catch (e) { return e.name; } }\n"
                        + "class A { #x; m() { eval('this.#y'); } }\n"
                        + "class B { x = eval('arguments'); }\n"
                        + "[err(() => new A().m()), err(() => new B())].join()");
    }

    @Test
    void joinersInIdentifiers() {
        // ZWNJ and ZWJ are identifier parts, no longer stripped as format characters
        Utils.assertGatedBehavior("number", "undefined", "var a‌b = 1; typeof ab");
        Utils.assertWithAllModes_ECMASCRIPT(
                1, "class A { #a_\\u200C_b = 1; m() { return this.#a_‌_b; } }; new A().m()");
    }
}
