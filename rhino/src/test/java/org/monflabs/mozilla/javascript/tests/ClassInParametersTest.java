/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** Named class expressions in a parameter list: their name is a variable of the function. */
class ClassInParametersTest {

    @Test
    void defaultValues() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "function|function,function|1|5|true|function",
                "[(function (a = class q {}, b) { return typeof a })(),"
                        + " (function (a = class q {}, b = class r {}) {"
                        + "     return [typeof a, typeof b].join() })(),"
                        + " (function (a = (class q {}, 1), b) { return a })(),"
                        + " (function (a = class q {}, b, c) { return b + c })(0, 2, 3),"
                        + " (function (a = class q { m() { return q } }, b) {"
                        + "     return new a().m() === a })(),"
                        + " (function (a = class b {}, b) { return typeof a })()"
                        + "].join('|')");
    }

    @Test
    void patterns() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "function|function|function|1|true",
                "[(function ({a = class q {}}) { return typeof a })({}),"
                        + " (function ([a = class q {}]) { return typeof a })([]),"
                        + " (function ({a} = {a: class q {}}) { return typeof a })(),"
                        + " (function ({a = (class q {}, 1)}) { return a })({}),"
                        + " (function (a = class q {}, {b = a}) { return b === a })(undefined, {})"
                        + "].join('|')");
    }

    @Test
    void arrowFunctions() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "function|function|q|7|true|number",
                "[((a = class q {}, b) => typeof a)(),"
                        + " (({a = class q {}}) => typeof a)({}),"
                        + " ((a = class q extends class r {} {}, b) => a.name)(),"
                        + " ((a = class q { static x = 7 }, b) => a.x)(),"
                        + " ((a = class q { m() { return q } }, b) => new a().m() === a)(),"
                        + " (function (x) { let y = 1; ((a = class q {}, b) => a)(); return typeof y })()"
                        + "].join('|')");
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,2",
                "(function () { var v = 1; var f = (a = class v {}, b = 2) => b;"
                        + " return [v, f()].join() })()");
    }
}
