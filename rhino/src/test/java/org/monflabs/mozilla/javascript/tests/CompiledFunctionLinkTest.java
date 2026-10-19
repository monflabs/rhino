/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * A compiled function is called through a class of its own from its first call: results, this,
 * new.target and exceptions are the same on every call.
 */
class CompiledFunctionLinkTest {

    @Test
    void callsBeforeAndAfterLinking() {
        String expected = "1,2,o,ok,E1 2,3,o,ok,E2 3,4,o,ok,E3";
        Utils.assertGatedBehavior(
                expected,
                expected,
                "var o = { name: 'o', m: function () { return this.name; } };"
                        + "var fs = [function (x) { return x + 1; }, o.m,"
                        + "  function () { if (new.target) return 'bad'; return 'ok'; },"
                        + "  function (x) { throw new Error('E' + x); }];"
                        + "var r = [];"
                        + "for (var i = 1; i <= 3; i++) {"
                        + "  var e; try { fs[3](i); } catch (x) { e = x.message; }"
                        + "  r.push([i, fs[0](i), fs[1].call(o), fs[2](), e].join()); }"
                        + "r.join(' ')");
    }
}
