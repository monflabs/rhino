/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests.es2025;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** RegExp.escape (plan 11.3). */
class RegExpEscapeTest {

    @Test
    void gated() {
        Utils.assertGatedBehavior("undefined", "function", "typeof RegExp.escape");
    }

    @Test
    void escapes() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "\\x61bc\\.d|\\x31\\+1\\x3d2|\\x20\\n\\u2028\\ud800x\\/|\u00e9\\x2c",
                "[RegExp.escape('abc.d'), RegExp.escape('1+1=2'),"
                        + " RegExp.escape(' \\n\\u2028\\ud800x/'), RegExp.escape('\\u00e9,')].join('|')");
    }

    @Test
    void matchesLiterally() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|TypeError",
                "var s = '(a.b)*[c]{2}|$^\\\\';\n"
                        + "var r = [new RegExp('^' + RegExp.escape(s) + '$', 'u').test(s)];\n"
                        + "try { RegExp.escape(1); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }
}
