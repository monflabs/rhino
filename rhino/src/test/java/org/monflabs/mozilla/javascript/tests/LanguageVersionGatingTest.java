/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.monflabs.mozilla.javascript.CompilerEnvirons;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.testutils.Utils;

class LanguageVersionGatingTest {

    @ParameterizedTest
    @ValueSource(
            ints = {
                Context.VERSION_DEFAULT,
                Context.VERSION_1_5,
                Context.VERSION_1_6,
                Context.VERSION_1_7,
                Context.VERSION_1_8,
                Context.VERSION_ES6,
                Context.VERSION_ECMASCRIPT
            })
    void onlyVersionEcmaScriptIsTheLatest(int version) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(version);
            boolean latest = version == Context.VERSION_ECMASCRIPT;
            assertEquals(latest, cx.isLatestEcmaScript());

            CompilerEnvirons env = new CompilerEnvirons();
            env.initFromContext(cx);
            assertEquals(latest, env.isLatestEcmaScript());
        }
    }

    @Test
    void gatedBehaviorHelperChecksBothVersions() {
        Utils.assertGatedBehavior(2, 2, "1 + 1");
        assertThrows(AssertionError.class, () -> Utils.assertGatedBehavior(2, 3, "1 + 1"));
        Utils.assertWithAllModes_ECMASCRIPT("ok", "'o' + 'k'");
    }
}
