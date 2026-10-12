/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2021;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * GetPrototypeFromConstructor: a newTarget whose prototype is not an object gives the intrinsic of
 * its own realm (plan 7.3).
 */
class PrototypeFromConstructorRealmTest {

    @Test
    void intrinsicOfTheRealmOfNewTarget() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    TopLevel other = cx.initStandardObjects();
                    ScriptableObject.putProperty(scope, "other", other.getGlobalThis());
                    Object result =
                            cx.evaluateString(
                                    scope,
                                    "var nt = new other.Function(); nt.prototype = null;\n"
                                            + "[Object.getPrototypeOf(Reflect.construct(TypeError, [], nt))"
                                            + " === other.TypeError.prototype,\n"
                                            + " Object.getPrototypeOf(Reflect.construct(WeakRef, [{}], nt))"
                                            + " === other.WeakRef.prototype,\n"
                                            + " Object.getPrototypeOf(Reflect.construct(function () {}, [], nt))"
                                            + " === other.Object.prototype].join()",
                                    "test",
                                    1,
                                    null);
                    assertEquals("true,true,true", result);
                    return null;
                });
    }
}
