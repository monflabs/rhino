/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** RegExp, ES2015 core (plan 1.9). */
class RegExpCoreTest {

    private static void assertBoth(Object expected, String script) {
        Utils.assertWithAllModes_ES6(expected, script);
        Utils.assertWithAllModes_ECMASCRIPT(expected, script);
    }

    @Test
    void prototypeIsOrdinaryObject() {
        assertBoth(
                "[object Object]|undefined|(?:)|TypeError|/(?:)/",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => Object.prototype.toString.call(RegExp.prototype));"
                        + " t(() => RegExp.prototype.global);"
                        + " t(() => RegExp.prototype.source);"
                        + " t(() => RegExp.prototype.exec(''));"
                        + " t(() => RegExp.prototype.toString());"
                        + " r.join('|')");
    }

    @Test
    void flagsAndConstructor() {
        assertBoth(
                "dgimsuy|g|true|false|ab/gi|x",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => { var c = ''; var o = {};"
                        + " 'hasIndices global ignoreCase multiline dotAll unicode sticky'.split(' ').forEach("
                        + "(n, i) => Object.defineProperty(o, n, { get() { c += 'dgimsuy'[i]; } }));"
                        + " Object.getOwnPropertyDescriptor(RegExp.prototype, 'flags').get.call(o); return c; });"
                        + " t(() => Object.getOwnPropertyDescriptor(RegExp.prototype, 'flags').get.call({ global: 1 }));"
                        + " t(() => { var re = /a/; return RegExp(re) === re; });"
                        + " t(() => { var re = /a/; re.constructor = null; return RegExp(re) === re; });"
                        + " t(() => { var o = { [Symbol.match]: true, source: 'ab', flags: 'gi' };"
                        + " var re = new RegExp(o); return re.source + '/' + re.flags; });"
                        + " t(() => RegExp.prototype.toString.call({ source: 'x', flags: '' }).slice(1, 2));"
                        + " r.join('|')");
    }

    @Test
    void sourceLexerAndQuantifiers() {
        assertBoth(
                "(?:)|\\n|SyntaxError|true|true|­|cd2",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => new RegExp('').source);"
                        + " t(() => new RegExp('\\n').source);"
                        + " t(() => new RegExp('x{1}{1,}'));"
                        + " t(() => !new RegExp('b{' + Number.MAX_SAFE_INTEGER + '}', 'u').test(''));"
                        + " t(() => !new RegExp('b{9007199254740991,}?').test('a'));"
                        + " t(() => eval('/­/').source);"
                        + " t(() => { var re = /(?:ab|cd)\\d?/g; re.lastIndex = -1; return re.exec('aacd22 ')[0]; });"
                        + " r.join('|')");
    }

    @Test
    void symbolMethods() {
        assertBoth(
                "TypeError|0|TypeError|ba",
                "var r = []; function t(f) { try { r.push(String(f())); } catch (e) { r.push(e.name); } }"
                        + " t(() => /./[Symbol.match].call({ exec() { return 1; }, flags: '' }, 'a'));"
                        + " t(() => { var re = /a/; re.lastIndex = -0; re[Symbol.search]('a');"
                        + " return Object.is(re.lastIndex, -0) ? 0 : 1; });"
                        + " t(() => /./[Symbol.search].call({ exec() { return 1; }, lastIndex: 0 }, 'a'));"
                        + " t(() => { var re = /a/g; Object.defineProperty(re, 'global', { value: false });"
                        + " return re[Symbol.replace]('aa', 'b'); });"
                        + " r.join('|')");
    }

    @Test
    void propertyEscapeWithoutUnicodeFlag() {
        Utils.assertGatedBehavior(true, false, "/\\p{L}/.test('a')");
    }
}
