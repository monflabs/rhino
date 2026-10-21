/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/**
 * The arguments object of strict functions, which a strict function that needs no activation keeps
 * in a variable under VERSION_ECMASCRIPT, and of functions that keep their other variables in
 * registers: its properties and behavior are those of the arguments object of an activation.
 */
class StrictArgumentsTest {

    @Test
    void argumentsObjects() {
        Utils.assertGatedBehavior(EXPECTED_ES6, EXPECTED_ECMASCRIPT, SCRIPT);
    }

    @Test
    void variableNamedArguments() {
        Utils.assertWithAllModes(
                "object,5,object,function,number",
                "function before() { return typeof arguments; var arguments = 5; }"
                        + "function after() { var arguments = 5; return arguments; }"
                        + "function closure() { var g = function () {}; return typeof arguments;"
                        + "  var arguments; }"
                        + "function decl() { return typeof arguments; function arguments() {} }"
                        + "function param(arguments) { return typeof arguments; }"
                        + "[before(1), after(1), closure(1), decl(1), param(1)].join()");
    }

    private static final String EXPECTED_ES6 =
            "strict3 | 0:vwec 1:vwec 2:vwec length:vw-c callee:afunctionfunction-- Symbol(Symbol.iterator):vw-c | 3 | [object Arguments] | 0,1,2 | {\"0\":1,\"1\":2,\"2\":3} | callee TypeError | 1,2,3 | true; strict0 | length:vw-c callee:afunctionfunction-- Symbol(Symbol.iterator):vw-c | 0 | [object Arguments] |  | {} | callee TypeError |  | true; sloppy0 | 0:vwec 1:vwec length:vw-c callee:vw-c Symbol(Symbol.iterator):vw-c | 2 | [object Arguments] | 0,1 | {\"0\":4,\"1\":5} | function | 4,5 | true; sloppy2 | 0:vwec length:vw-c callee:vw-c Symbol(Symbol.iterator):vw-c | 1 | [object Arguments] | 0 | {\"0\":1} | function | 1 | true; 6:3 ; 0:0 ; 1,9,2,true ; 9 ; 0,1/7 ; 4 ; 2 ; object ; 0 ; 3:2; bound | 0:vwec 1:vwec 2:vwec length:vw-c callee:afunctionfunction-- Symbol(Symbol.iterator):vw-c | 3 | [object Arguments] | 0,1,2 | {\"0\":\"b1\",\"1\":\"x\",\"2\":\"y\"} | callee TypeError | b1,x,y | true; 2 4 2; callee throws TypeError; true";

    private static final String EXPECTED_ECMASCRIPT =
            "strict3 | 0:vwec 1:vwec 2:vwec length:vw-c callee:afunctionfunction-- Symbol(Symbol.iterator):vw-c | 3 | [object Arguments] | 0,1,2 | {\"0\":1,\"1\":2,\"2\":3} | callee TypeError | 1,2,3 | true; strict0 | length:vw-c callee:afunctionfunction-- Symbol(Symbol.iterator):vw-c | 0 | [object Arguments] |  | {} | callee TypeError |  | true; sloppy0 | 0:vwec 1:vwec length:vw-c callee:vw-c Symbol(Symbol.iterator):vw-c | 2 | [object Arguments] | 0,1 | {\"0\":4,\"1\":5} | function | 4,5 | true; sloppy2 | 0:vwec length:vw-c callee:vw-c Symbol(Symbol.iterator):vw-c | 1 | [object Arguments] | 0 | {\"0\":1} | function | 1 | true; 6:3 ; 0:0 ; 1,9,2,true ; 9 ; 1/7 ; 4 ; 2 ; object ; 0 ; 3:2; bound | 0:vwec 1:vwec 2:vwec length:vw-c callee:afunctionfunction-- Symbol(Symbol.iterator):vw-c | 3 | [object Arguments] | 0,1,2 | {\"0\":\"b1\",\"1\":\"x\",\"2\":\"y\"} | callee TypeError | b1,x,y | true; 2 4 2; callee throws TypeError; true";

    private static final String SCRIPT =
            "var lines = []; "
                    + "function desc(o) { "
                    + "  var r = []; "
                    + "  var all = Object.getOwnPropertyNames(o).concat(Object.getOwnPropertySymbols(o)); "
                    + "  for (var i = 0; i < all.length; i++) { "
                    + "    var d = Object.getOwnPropertyDescriptor(o, all[i]); "
                    + "    r.push(String(all[i]) + ':' + ('value' in d ? 'v' + (d.writable ? 'w' : '-') : 'a' + (typeof d.get) + (typeof d.set)) + (d.enumerable ? 'e' : '-') + (d.configurable ? 'c' : '-')); "
                    + "  } "
                    + "  return r.join(' '); "
                    + "} "
                    + "function show(label, a) { "
                    + "  var out = [label, desc(a), a.length, Object.prototype.toString.call(a), Object.keys(a).join(','), JSON.stringify(a)]; "
                    + "  try { out.push(typeof a.callee); } catch (e) { out.push('callee ' + e.name); } "
                    + "  var it = []; for (var x of a) it.push(x); out.push(it.join(',')); "
                    + "  out.push(a[Symbol.iterator] === Array.prototype.values); "
                    + "  lines.push(out.join(' | ')); "
                    + "} "
                    + "function strict3(a, b) { 'use strict'; return arguments; } "
                    + "function strict0() { 'use strict'; return arguments; } "
                    + "function sloppy0() { return arguments; } "
                    + "function sloppy2(a, b) { return arguments; } "
                    + "function strictUse(a) { 'use strict'; var s = 0; for (var i = 0; i < arguments.length; i++) s += arguments[i]; return s + ':' + arguments.length; } "
                    + "function strictWrite(a) { 'use strict'; arguments[0] = 9; arguments[5] = 1; return a + ',' + arguments[0] + ',' + arguments.length + ',' + (5 in arguments); } "
                    + "function sloppyMapped(a) { arguments[0] = 9; return a; } "
                    + "function strictDelete(a, b) { 'use strict'; delete arguments[0]; arguments.length = 7; return Object.keys(arguments).join() + '/' + arguments.length; } "
                    + "function strictNested(a) { 'use strict'; function inner() { return arguments.length; } return inner(1, 2, 3) + arguments.length; } "
                    + "function strictEval(a) { 'use strict'; return eval('arguments.length'); } "
                    + "function strictTypeof() { 'use strict'; return typeof arguments; } "
                    + "function sloppyDefaults(a = 1) { return arguments.length; } "
                    + "var strictRest = (function () { 'use strict'; return function (a, ...r) { return arguments.length + ':' + r.length; }; })(); "
                    + "show('strict3', strict3(1, 2, 3)); "
                    + "show('strict0', strict0()); "
                    + "show('sloppy0', sloppy0(4, 5)); "
                    + "show('sloppy2', sloppy2(1)); "
                    + "lines.push([strictUse(1, 2, 3), strictUse(), strictWrite(1, 2), sloppyMapped(1), strictDelete(1, 2), strictNested(1), strictEval(1, 2), strictTypeof(), sloppyDefaults(), strictRest(1, 2, 3)].join(' ; ')); "
                    + "var f = function () { 'use strict'; return arguments; }; var g = f.bind(null, 'b1'); show('bound', g('x', 'y')); "
                    + "lines.push([strict3.apply(null, [7, 8]).length, Reflect.apply(strict0, null, [1, 2, 3, 4]).length, new (function () { 'use strict'; this.n = arguments.length; })(1, 2).n].join(' ')); "
                    + "try { (function () { 'use strict'; return arguments.callee; })(); } catch (e) { lines.push('callee throws ' + e.name); } "
                    + "lines.push((function () { return arguments.callee; })() !== undefined); "
                    + "lines.join('; ') ";
}
