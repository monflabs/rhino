/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2025;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** RegExp pattern modifiers (?ims-ims:...) (plan 11.4). */
class RegExpModifiersTest {

    @Test
    void gated() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "true",
                "try { String(new RegExp('(?i:a)').test('A')) } catch (e) { e.name }");
    }

    @Test
    void scopedFlags() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,false,true,false,true,false,true,false",
                "[/(?i:a)b/.test('Ab'), /(?i:a)b/.test('AB'), /a(?-i:b)/i.test('Ab'),"
                        + " /a(?-i:b)/i.test('aB'), /(?m:^b)/.test('a\\nb'), /(?-m:^b)/m.test('a\\nb'),"
                        + " /(?s:.)/.test('\\n'), /(?-s:.)/s.test('\\n')].join()");
    }

    @Test
    void classesBackreferencesAndNesting() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true,true,true,true,false,true,aAaA,true,i",
                "[/(?i:[a-c])x/.test('Bx'), /(?i:\\u{61})/u.test('A'), /(a)(?i:\\1)/.test('aA'),"
                        + " /(?i:x(?-i:y)z)/.test('XyZ'), /(?i:x(?-i:y)z)/.test('XYZ'),"
                        + " /(?<=(?i:a))b/.test('Ab'), /(?i:a)+/.exec('aAaA')[0],"
                        + " /(?i:a|b)c/.test('Bc'), /(?i:a)/.flags + 'i'].join()");
    }

    @Test
    void backtrackingRestoresFlags() {
        // the alternative tried after backtracking out of the group runs with the outer flags
        Utils.assertWithAllModes_ECMASCRIPT(
                "null|aB",
                "String(/(?:(?i:a)x|a)B/.exec('Ab')) + '|' + /(?:(?i:a)x|a)B/.exec('aB')[0]");
    }

    @Test
    void earlyErrors() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,SyntaxError,ok",
                "['(?ii:a)', '(?-:a)', '(?i-i:a)', '(?x:a)', '(?i)', '(?i-:a)'].map(p => {"
                        + " try { new RegExp(p); return 'ok'; } catch (e) { return e.name; } }).join()");
    }
}
