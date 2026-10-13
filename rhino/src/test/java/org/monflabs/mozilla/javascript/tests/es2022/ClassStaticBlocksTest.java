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

/** Class static blocks (plan 8.3). */
class ClassStaticBlocksTest {

    @Test
    void staticBlocks() {
        // run in order with the static fields, with the class as this, and their own var scope
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,true,2,2,3,true,",
                "var log = [];\n"
                        + "class A { static x = 1;\n"
                        + "  static { log.push(this.x, this === A); var v = 2; log.push(v); }\n"
                        + "  static y = 2; static #p = 3;\n"
                        + "  static { log.push(this.y, A.#p,\n"
                        + "      super.toString === Function.prototype.toString); } }\n"
                        + "log.push(typeof v === 'undefined' ? undefined : v);\n"
                        + "log.join()");
        Utils.assertWithAllModes_ECMASCRIPT(
                "f", "var r; class C { static { r = (function await() { return 'f'; })(); } }; r");
    }

    @Test
    void earlyErrors() {
        for (String source :
                new String[] {
                    "class A { static { return; } }",
                    "class A { static { arguments; } }",
                    "class A { static { await; } }",
                    "class A { static { var await; } }",
                    "class A { static { super(); } }"
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
