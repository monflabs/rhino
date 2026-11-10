package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The identifier characters are ID_Start and ID_Continue (UAX #31) under VERSION_ECMASCRIPT: the
 * JDK's identifier characters also include Pattern_Syntax and the ignorable controls.
 */
class IdentifierCharactersTest {

    private static final String PRELUDE =
            "var r = []; function t(src) { try { r.push(String(eval(src))); }"
                    + " catch (e) { r.push(e.name); } }";

    @Test
    void verticalTildeIsNotAnIdentifierCharacter() {
        Utils.assertGatedBehavior(
                "1|2|3|4",
                "SyntaxError|SyntaxError|SyntaxError|SyntaxError",
                PRELUDE
                        + " t('var ⸯ = 1; ⸯ'); t('var aⸯ = 2; aⸯ');"
                        + " t('var \\\\u2E2F = 3; ⸯ'); t('var a\\\\u{2E2F} = 4; aⸯ');"
                        + " r.join('|')");
    }

    @Test
    void controlCharactersAreNotIdentifierParts() {
        Utils.assertGatedBehavior(
                "1|2|3",
                "SyntaxError|2|3",
                PRELUDE
                        + " t('var a\u0001b = 1; a\u0001b'); t('var ª· = 2; ª·');"
                        + " t('var x‍y = 3; x‍y'); r.join('|')");
    }

    @Test
    void groupAndPrivateNames() {
        Utils.assertGatedBehavior(
                "a|SyntaxError|b",
                "SyntaxError|SyntaxError|b",
                PRELUDE
                        + " t(\"/(?<ⸯ>a)/.exec('a')[1]\"); t('(class { #ⸯ; })');"
                        + " t(\"/(?<a·>b)/.exec('b').groups['a·']\"); r.join('|')");
    }
}
