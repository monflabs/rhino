/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The format-control characters (Cf) of the source: stripped before {@code VERSION_ECMASCRIPT}, a
 * SyntaxError outside the literals and the comments with it (ES2026 12.1).
 */
class FormatCharactersTest {

    private static String eval(String source) {
        return "try { String(eval('" + source + "')) } catch (e) { e.name }";
    }

    @Test
    void mongolianVowelSeparatorIsNotWhiteSpace() {
        Utils.assertGatedBehavior("1", "SyntaxError", eval("var᠎x = 1; varx"));
        Utils.assertGatedBehavior("12", "SyntaxError", eval("1᠎2"));
    }

    @Test
    void formatCharactersAreNotIdentifierParts() {
        Utils.assertGatedBehavior("1", "SyntaxError", eval("var a​b = 1; ab"));
        Utils.assertGatedBehavior("1", "SyntaxError", eval("var a­b = 1; ab"));
        Utils.assertGatedBehavior("1", "SyntaxError", eval("var a = 1; a⁠"));
    }

    @Test
    void formatCharactersBelongToLiteralsAndComments() {
        Utils.assertWithAllModes_ECMASCRIPT(3, "'a᠎b'.length");
        Utils.assertWithAllModes_ECMASCRIPT(3, "`a​b`.length");
        Utils.assertWithAllModes_ECMASCRIPT(true, "/a᠎b/.test('a᠎b')");
        Utils.assertWithAllModes_ECMASCRIPT(1, "// ᠎\n/* ​ */ 1");
    }

    @Test
    void byteOrderMarkIsWhiteSpace() {
        Utils.assertGatedBehavior(1, 1, "var﻿x = 1; x");
    }
}
