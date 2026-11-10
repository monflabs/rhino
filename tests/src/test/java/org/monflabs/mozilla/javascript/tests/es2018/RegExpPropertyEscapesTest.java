/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.monflabs.mozilla.javascript.tests.es2018;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * Unicode property escapes: the names, aliases and binary properties of ECMA-262 under
 * VERSION_ECMASCRIPT. The code points used here are in Unicode 15.0, so the results do not depend
 * on the JDK.
 */
public class RegExpPropertyEscapesTest {

    private static final String TEST =
            "function t(p, s) {\n"
                    + "  try { return String(new RegExp('^\\\\p{' + p + '}$', 'u').test(s)); }\n"
                    + "  catch (e) { return e.name; }\n"
                    + "}\n";

    private static void assertGated(String es6, String ecmaScript, String body) {
        Utils.assertGatedBehavior(es6, ecmaScript, TEST + body);
    }

    @Test
    public void anyAndAssigned() {
        assertGated(
                "SyntaxError SyntaxError SyntaxError",
                "true true false",
                "[t('Any', '\\u{10FFFF}'), t('Assigned', 'a'), t('Assigned', '\\u0378')].join(' ')");
    }

    @Test
    public void generalCategory() {
        assertGated(
                "false true SyntaxError SyntaxError",
                "true false true true",
                "[t('General_Category=Other', '\\u00AD'), t('gc=digit', '\\u00B2'),"
                        + " t('LC', 'a'), t('Cased_Letter', '\\u01C5')].join(' ')");
    }

    @Test
    public void binaryPropertiesTheJdkApproximated() {
        assertGated(
                "true true true false false",
                "false false false true true",
                "[t('Hex_Digit', '\\u0660'), t('ID_Start', '\\u2E2F'), t('ID_Continue', '\\u00AD'),"
                        + " t('White_Space', '\\u0085'), t('Case_Ignorable', \"'\")].join(' ')");
    }

    @Test
    public void caseMappingProperties() {
        assertGated(
                "SyntaxError SyntaxError SyntaxError SyntaxError SyntaxError SyntaxError",
                "true false true true false true",
                "[t('Changes_When_Casefolded', 'A'), t('CWCF', 'a'), t('CWKCF', '\\uFB01'),"
                        + " t('Changes_When_Titlecased', '\\u01C6'), t('CWT', '\\u01C5'),"
                        + " t('Cased', '\\u01C5')].join(' ')");
    }

    @Test
    public void jdkBinaryProperties() {
        assertGated(
                "SyntaxError SyntaxError SyntaxError SyntaxError SyntaxError SyntaxError",
                "true true true false true true",
                "[t('Emoji', '\\u{1F600}'), t('ExtPict', '\\u00A9'), t('Ideographic', '\\u3007'),"
                        + " t('Unified_Ideograph', '\\u3007'), t('UIdeo', '\\u4E2D'),"
                        + " t('Bidi_M', '(')].join(' ')");
    }

    @Test
    public void fixedSets() {
        assertGated(
                "SyntaxError SyntaxError SyntaxError SyntaxError SyntaxError SyntaxError",
                "true true true true true false",
                "[t('Pattern_Syntax', '+'), t('NChar', '\\u{10FFFF}'), t('DI', '\\u00AD'),"
                        + " t('Join_C', '\\u200D'), t('RI', '\\u{1F1E6}'),"
                        + " t('XID_Start', '\\u037A')].join(' ')");
    }

    @Test
    public void scriptValuesAreExact() {
        assertGated(
                "true true SyntaxError true",
                "true SyntaxError true true",
                "[t('Script=Latin', 'a'), t('Script=latin', 'a'), t('sc=Qaac', '\\u2C80'),"
                        + " t('sc=Latn', 'a')].join(' ')");
    }

    @Test
    public void negationAndClasses() {
        assertGated(
                "SyntaxError SyntaxError SyntaxError",
                "false false true",
                "function r(re, s) {\n"
                        + "  try { return String(new RegExp(re, 'u').test(s)); }\n"
                        + "  catch (e) { return e.name; }\n"
                        + "}\n"
                        + "[r('^\\\\P{Any}$', 'a'), r('^[\\\\p{Emoji}a]$', 'b'),"
                        + " r('^[^\\\\p{Assigned}]$', '\\u0378')].join(' ')");
    }

    @Test
    public void propertiesWithoutJdkDataAreSyntaxErrors() {
        assertGated(
                "SyntaxError SyntaxError SyntaxError",
                "SyntaxError SyntaxError SyntaxError",
                "[t('Math', '+'), t('Grapheme_Extend', '\\u0300'),"
                        + " t('Script_Extensions=Latin', 'a')].join(' ')");
    }
}
