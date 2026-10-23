/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The split, replace and match of an ordinary regexp skip the property lookups of their algorithms;
 * once a property they read is replaced, they read it again.
 */
class RegExpOrdinaryTest {

    private static void assertAllVersions(String expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void replacedFlagGetterIsRead() {
        assertAllVersions(
                "a|b a|b true",
                "var re = /,/; var r = ['a,b'.split(re).join('|')]; var log = [];"
                        + "Object.defineProperty(RegExp.prototype, 'global',"
                        + "  {get() { log.push('global'); return false; }, configurable: true});"
                        + "r.push('a,b'.split(re).join('|'), log.length > 0); r.join(' ')");
    }

    @Test
    void replacedExecIsCalled() {
        assertAllVersions(
                "a;b 2 a,b 1",
                "var re = /,/g; var r = ['a,b'.replace(re, ';'), 'a,b'.split(re).length];"
                        + "RegExp.prototype.exec = function (s) { return null; };"
                        + "r.push('a,b'.replace(re, ';'), 'a,b'.split(re).length); r.join(' ')");
    }

    @Test
    void replacedSpeciesIsRead() {
        assertAllVersions(
                "a|b 1",
                "var re = /,/; var calls = 0; 'a,b'.split(re);"
                        + "Object.defineProperty(RegExp, Symbol.species,"
                        + "  {get() { calls++; return RegExp; }, configurable: true});"
                        + "'a,b'.split(re).join('|') + ' ' + calls");
    }

    @Test
    void ownPropertiesOfTheRegExpAreRead() {
        assertAllVersions(
                "a|b 1 a;b 2",
                "var re = /,/; var log = []; 'a,b'.split(re);"
                        + "Object.defineProperty(re, 'flags', {get() { log.push('flags'); return ''; }});"
                        + "'a,b'.split(re).join('|') + ' ' + log.length + ' ' + 'a,b'.replace(re, ';')"
                        + "  + ' ' + log.length");
        assertAllVersions(
                "a|b y",
                "var re = /,/; var log = []; 'a,b'.split(re);"
                        + "function C(p, f) { log.push(f); return new RegExp(p, f); }"
                        + "C[Symbol.species] = C; re.constructor = C;"
                        + "'a,b'.split(re).join('|') + ' ' + log.join()");
    }

    @Test
    void replacedMatchIsReadByTheSplitter() {
        String script =
                "var re = /,/; 'a,b'.split(re); var log = [];"
                        + "Object.defineProperty(RegExp.prototype, Symbol.match,"
                        + "  {get() { log.push('match'); return true; }, configurable: true});"
                        + "'a,b'.split(re); log.length > 0";
        Utils.assertWithAllModes_ES6(false, script);
        Utils.assertWithAllModes_ECMASCRIPT(true, script);
    }

    @Test
    void splitOfAnOrdinaryRegExp() {
        assertAllVersions(
                "a|1|b|2 a|1|b|2|c 1 1 0 3 5 A|a| 0 a|b;c a,b|c",
                "RegExp.prototype.foo = 1;"
                        + "for (var i = 0; i < 20; i++) RegExp.prototype['m' + i] = i;"
                        + "var re = /(\\d)/;"
                        + "var r = ['a1b2c'.split(re, 4).join('|'), 'a1b2c'.split(re).join('|'),"
                        + "  'x'.split(/$/).length, ''.split(/x/).length, ''.split(/(?:)/).length,"
                        + "  '\\u{1F600}x\\u{1F600}'.split(/(?:)/u).length,"
                        + "  '\\u{1F600}x\\u{1F600}'.split(/(?:)/).length, 'AbaB'.split(/b/i).join('|'),"
                        + "  'a,b'.split(/,/, 0).length];"
                        + "var s = /,/; r.push('a,b;c'.split(s).join('|')); s.compile(';');"
                        + "r.push('a,b;c'.split(s).join('|')); r.join(' ')");
    }

    @Test
    void lastIndexOfAnOrdinaryRegExp() {
        assertAllVersions(
                "bb 0 bx 2 ba 0 true b|b 0 2",
                "var r = []; var re = /a/g; re.lastIndex = 5; r.push('aa'.replace(re, 'b'), re.lastIndex);"
                        + "var st = /a/y; st.lastIndex = 1;"
                        + "r.push('ba'.replace(st, 'x'), st.lastIndex, 'ba'.replace(st, 'x'), st.lastIndex);"
                        + "var ro = /a/g; Object.defineProperty(ro, 'lastIndex', {writable: false});"
                        + "try { 'aa'.replace(ro, 'b'); r.push('no'); }"
                        + "catch (e) { r.push(e instanceof TypeError); }"
                        + "var m = /(b)/g;"
                        + "r.push('abcb'.match(m).join('|'), m.lastIndex, [...'abcb'.matchAll(m)].length);"
                        + "r.join(' ')");
    }

    @Test
    void classesOutsideTheBasicPlane() {
        assertAllVersions(
                "true,false,true,true,false,true,false,false,true",
                "[/[\\u{1F600}-\\u{1F64F}x]/u.test('\\u{1F610}'), /[^\\u{1F600}a]/u.test('a'),"
                        + " /[a-\\u{1F600}]/u.test('b'), /[\\p{Lu}\\d]/u.test('Q'), /[^\\P{Lu}]/u.test('q'),"
                        + " /[\\u{10400}]/ui.test('\\u{10428}'), /[\\u{1F600}]/u.test('\\u{1F601}'),"
                        + " /[^\\u{1F600}]/u.test('\\u{1F600}'), /[\\P{L}]/u.test('1')].join()");
    }
}
