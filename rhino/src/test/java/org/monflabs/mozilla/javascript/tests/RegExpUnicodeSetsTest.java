/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** RegExp v flag, ES2024 (plan 10.2). */
class RegExpUnicodeSetsTest {

    private static final String T =
            "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                    + " function v(s, f) { return new RegExp(s, f || 'v'); }";

    @Test
    void flag() {
        Utils.assertGatedBehavior(
                "SyntaxError|false|SyntaxError|gimsyu",
                "v|true|SyntaxError|dgimsuvy",
                T
                        + " t(() => v('a').flags);"
                        + " t(() => 'unicodeSets' in RegExp.prototype && v('a').unicodeSets);"
                        + " t(() => v('a', 'uv'));"
                        + " t(() => { var c = ''; var o = {};"
                        + " 'hasIndices global ignoreCase multiline dotAll unicode unicodeSets sticky'"
                        + ".split(' ').forEach((n, i) => Object.defineProperty(o, n,"
                        + " { get() { c += 'dgimsuvy'[i]; } }));"
                        + " Object.getOwnPropertyDescriptor(RegExp.prototype, 'flags').get.call(o);"
                        + " return c; });"
                        + " r.join('|')");
    }

    @Test
    void setOperations() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|false|false|true|true|true|false|true",
                T
                        + " t(() => v('^[\\\\p{L}--[a-z]]+$').test('ABC'));"
                        + " t(() => v('[\\\\p{L}--[a-z]]').test('a'));"
                        + " t(() => v('[\\\\p{L}&&\\\\p{ASCII}]').test('\\u00e9'));"
                        + " t(() => v('[\\\\p{L}&&\\\\p{ASCII}]').test('e'));"
                        + " t(() => v('^[[a-c][x-z]]+$').test('abxz'));"
                        + " t(() => v('[\\\\d--[0-4]]').test('7'));"
                        + " t(() => v('[^\\\\d--[0-4]]').test('7'));"
                        + " t(() => v('[[^a]&&[a-c]]').test('b'));"
                        + " r.join('|')");
    }

    @Test
    void stringDisjunctions() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "abcdx|abc|true|true|true|2|true|SyntaxError|true",
                T
                        + " t(() => v('^[\\\\q{abc|d}x]+$').exec('abcdx')[0]);"
                        + " t(() => v('[\\\\q{a|ab|abc}]').exec('abc')[0]);"
                        + " t(() => v('^[\\\\q{}a]$').test(''));"
                        + " t(() => v('^[\\\\q{AbC}]$', 'vi').test('abc'));"
                        + " t(() => v('^[\\\\q{\\\\u{1F468}\\\\u200D\\\\u{1F469}}]$')"
                        + ".test('\\u{1F468}\\u200D\\u{1F469}'));"
                        + " t(() => v('(?<=[\\\\q{ab|c}])z').exec('abz').index);"
                        + " t(() => v('^[\\\\q{ab|c}--\\\\q{c}]+$').test('abab'));"
                        + " t(() => v('[^\\\\q{ab}]'));"
                        + " t(() => v('[^\\\\q{a}]').test('b'));"
                        + " r.join('|')");
    }

    @Test
    void syntax() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SyntaxError|SyntaxError|SyntaxError|SyntaxError|SyntaxError|SyntaxError|true",
                T
                        + " t(() => v('[a&&b--c]'));"
                        + " t(() => v('[a&&&b]'));"
                        + " t(() => v('[a-z&&b]'));"
                        + " t(() => v('[(]'));"
                        + " t(() => v('[a!!b]'));"
                        + " t(() => v('[a-]'));"
                        + " t(() => v('[\\\\(\\\\)\\\\-\\\\&]').test('&'));"
                        + " r.join('|')");
    }

    @Test
    void caseInsensitiveComplement() {
        // the complement is taken among the case folded characters
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|true|false|false|true",
                T
                        + " t(() => v('[\\\\p{Lowercase}]', 'vi').test('A'));"
                        + " t(() => v('[^\\\\P{Lowercase}]', 'vi').test('A'));"
                        + " t(() => v('\\\\P{Lowercase}', 'vi').test('A'));"
                        + " t(() => v('[\\\\P{Lowercase}]', 'vi').test('A'));"
                        + " t(() => new RegExp('\\\\P{Lowercase}', 'ui').test('A'));"
                        + " r.join('|')");
    }

    @Test
    void lookbehindAlternatives() {
        Utils.assertGatedBehavior(
                "null|null|null|z",
                "z|z|z|z",
                "[/(?<=x\\d|y\\d)z/.exec('y1z'), /(?<=x\\d|y\\d)z/.exec('x1z'),"
                        + " /(?<=a(?:x\\d|y\\d))z/.exec('ay1z'), /(?<=\\dx|y)z/.exec('1xz')]"
                        + ".map(String).join('|')");
    }

    @Test
    void unicodeClassAlternative() {
        Utils.assertGatedBehavior(
                "null|null",
                "2|2",
                "[/[\\u{1F600}]|a/u.exec('\\u{1F600}'), /a|[\\u{1F600}]/u.exec('\\u{1F600}')]"
                        + ".map(m => m && m[0].length).map(String).join('|')");
    }
}
