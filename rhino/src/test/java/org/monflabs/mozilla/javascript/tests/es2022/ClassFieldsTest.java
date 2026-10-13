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

/** Public class fields, instance and static (plan 8.1). */
class ClassFieldsTest {

    @Test
    void instanceFields() {
        Utils.assertWithAllModes_ES6(
                "[1,2,3,true,null,\"f\",[\"x\",\"y\",\"c1\",\"z\",\"f\"]]",
                "class A { x = 1; y = this.x + 1; ['c' + 1] = 3; z; f = function() {}; }\n"
                        + "var a = new A();\n"
                        + "JSON.stringify([a.x, a.y, a.c1, 'z' in a, a.z, a.f.name, Object.keys(a)])");
        Utils.assertWithAllModes_ES6(
                "1,10,10,pm",
                "class B { x = 1; m() { return 'pm'; } }\n"
                        + "class C extends B { y = this.x * 10; s = super.m();"
                        + " constructor() { super(); this.w = this.y; } }\n"
                        + "var c = new C(); [c.x, c.y, c.w, c.s].join()");
        Utils.assertWithAllModes_ES6(
                "1,method",
                "class K { x = 1; x() { return 'method'; } }\n"
                        + "[new K().x, K.prototype.x()].join()");
    }

    @Test
    void staticFields() {
        Utils.assertWithAllModes_ES6(
                "S,true,C,3,",
                "class C { static s = 'S'; static self = this; static n = this.name;\n"
                        + " static\n c = 3 }\n"
                        + "[C.s, C.self === C, C.n, C.c, new C().c].join()");
        Utils.assertWithAllModes_ES6(
                "D", "var name; var D = class { static f = (name = this.name); }; name");
    }

    @Test
    void computedKeysInSourceOrder() {
        // static and instance computed keys are evaluated in source order, after the heritage
        Utils.assertWithAllModes_ES6(
                "h,a,b,c,d|1|2|3|true",
                "var log = [];\n"
                        + "function k(n) { log.push(n); return n; }\n"
                        + "class B {}\n"
                        + "var C = class extends (log.push('h'), B) {\n"
                        + "  [k('a')] = 1; static [k('b')] = 2; [k('c')]() { return 3; }"
                        + " static [k('d')]; };\n"
                        + "[log.join(), new C().a, C.b, new C().c(), 'd' in C].join('|')");
        Utils.assertWithAllModes_ES6(
                "4,5,3,6",
                "let i = 0; class D { [i++] = i++; static [i++] = i++; [i++] = i++; }\n"
                        + "var d = new D(); [d[0], d[2], D[1], i].join()");
    }

    @Test
    void earlyErrors() {
        for (String source :
                new String[] {
                    "class A { x = arguments; }",
                    "class A { x = () => arguments; }",
                    "class A { constructor = 1; }",
                    "class A { static prototype = 1; }",
                    "class A { x = 1 y = 2 }"
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
    }
}
