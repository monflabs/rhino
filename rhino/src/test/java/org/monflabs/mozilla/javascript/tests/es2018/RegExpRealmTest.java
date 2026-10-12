/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2018;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The flag getters of RegExp.prototype answer for their own realm's prototype only (plan 4.3). */
class RegExpRealmTest {

    @Test
    void flagGettersOfAnotherRealm() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    TopLevel scope = cx.initStandardObjects();
                    TopLevel other = cx.initStandardObjects();
                    ScriptableObject.putProperty(scope, "other", other.getGlobalThis());
                    Object result =
                            cx.evaluateString(
                                    scope,
                                    "var r = [];\n"
                                            + "for (var name of ['global', 'dotAll', 'source']) {\n"
                                            + "  var get = Object.getOwnPropertyDescriptor("
                                            + "RegExp.prototype, name).get;\n"
                                            + "  var otherGet = Object.getOwnPropertyDescriptor("
                                            + "other.RegExp.prototype, name).get;\n"
                                            + "  r.push(String(get.call(RegExp.prototype)));\n"
                                            + "  try { get.call(other.RegExp.prototype); }"
                                            + " catch (e) { r.push(e instanceof TypeError); }\n"
                                            + "  try { otherGet.call(RegExp.prototype); }"
                                            + " catch (e) { r.push(e instanceof other.TypeError); }\n"
                                            + "}\n"
                                            + "r.join()",
                                    "test",
                                    1,
                                    null);
                    assertEquals("undefined,true,true,undefined,true,true,(?:),true,true", result);
                    return null;
                });
    }
}
