/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** The long tail of test262 (plan M13). */
class ConformanceResidualsTest {

    @Test
    void decodeUriNoncharacters() {
        Utils.assertGatedBehavior(
                "fffd,fffd",
                "fffe,ffff",
                "[decodeURI('%EF%BF%BE'), decodeURIComponent('%EF%BF%BF')]"
                        + ".map(s => s.charCodeAt(0).toString(16)).join()");
    }

    @Test
    void revokeIsNotAConstructor() {
        Utils.assertGatedBehavior(
                "true",
                "false",
                "String(Proxy.revocable({}, {}).revoke.hasOwnProperty('prototype'))");
    }

    @Test
    void log2OfPowersOfTwo() {
        Utils.assertGatedBehavior(
                "2.9999999999999996,-1074",
                "3,-1074",
                "[Math.log2(8), Math.log2(Number.MIN_VALUE)].join()");
    }

    @Test
    void finalSigmaPastSupplementaryCaseIgnorable() {
        Utils.assertGatedBehavior(
                "σ,ς,σ",
                "ς,ς,σ",
                "['a\\ud834\\ude42\\u03a3', 'a\\u03a3', '\\u03a3'].map(s => s.toLowerCase().slice(-1))"
                        + ".join()");
    }

    @Test
    void classEarlyErrors() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "SyntaxError,SyntaxError,SyntaxError,SyntaxError,ok,ok",
                "['class C { * constructor() {} }', 'class C { async * constructor() {} }',"
                        + " 'class C { static * prototype() {} }', 'class C { st\\\\u0061tic m() {} }',"
                        + " 'class C { st\\\\u0061tic() {} }', 'class C { * [\"constructor\"]() {} }']"
                        + ".map(src => { try { eval(src); return 'ok'; } catch (e) { return e.name; } })"
                        + ".join()");
    }

    @Test
    void noArity() {
        Utils.assertGatedBehavior(
                "2|length,name,arity,prototype",
                "undefined|length,name,arguments,caller,prototype",
                "var f = function (a, b) {};\n"
                        + "f.arity + '|' + Object.getOwnPropertyNames(f).join()");
    }

    @Test
    void yieldInArrowParameters() {
        Utils.assertGatedBehavior(
                "ok",
                "SyntaxError",
                "try { eval('function *g() { (x = yield) => {}; }'); 'ok' } catch (e) { e.name }");
    }

    @Test
    void parameterClosuresDoNotSeeBodyVars() {
        Utils.assertGatedBehavior(
                "inside|inside|1,1,1|inside,dz",
                "outside|inside|1,1,1|inside,dz",
                "var x = 'outside', probeParams, probeBody;\n"
                        + "function f(_ = probeParams = function () { return x; }) {\n"
                        + "  var x = 'inside'; probeBody = function () { return x; };\n"
                        + "}\n"
                        + "f();\n"
                        + "function g(a = () => x) { var x = 1;"
                        + " return [x, (function () { return x; })(), (() => x)()]; }\n"
                        + "function h(a = () => x) { var x = 'inside', o = { x: x };"
                        + " var { x: z } = { x: 'dz' }; return [o.x, z]; }\n"
                        + "[probeParams(), probeBody(), g(), h()].join('|')");
    }

    @Test
    void primitiveThis() {
        Utils.assertGatedBehavior(
                "object|object|object|object|object|TestString",
                "boolean|boolean|string|bigint|string|TestString",
                "'use strict';\n"
                        + "Boolean.prototype.f = function () { return typeof this; };\n"
                        + "Boolean.prototype.toString = function () { return typeof this; };\n"
                        + "var matcher = { [Symbol.match](s) { return typeof s; } };\n"
                        + "Object.defineProperty(BigInt.prototype, 'toJSON',"
                        + " { get() { var t = typeof this; return () => t; }, configurable: true });\n"
                        + "var flat = [1].flatMap(function () { return [typeof this]; }, 's');\n"
                        + "var r = [true.f(), true.toLocaleString(), ''.match(matcher),"
                        + " JSON.parse(JSON.stringify(1n)), flat[0],"
                        + " [1].flatMap(function () { return [this]; }, 'TestString')[0]];\n"
                        + "r.join('|')");
    }

    @Test
    void dataPropertyAfterAccessorInLiteral() {
        Utils.assertGatedBehavior(
                "TypeError",
                "1",
                "'use strict'; try { String(({ get foo() { return 0; }, foo: 1 }).foo) }"
                        + " catch (e) { e.name }");
    }

    @Test
    void classHeritageIsStrict() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "TypeError|undefined",
                "var D = class extends function () { arguments.callee; } {};\n"
                        + "var F = class extends function () { return this; } {};\n"
                        + "var r; try { new D(); r = 'none'; } catch (e) { r = e.name; }\n"
                        + "r + '|' + typeof Object.getPrototypeOf(F).call(undefined)");
    }

    @Test
    void restrictedFunctionProperties() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "true|true|2|undefined|length,name,arguments,caller,prototype|TypeError|length,name",
                "var d = Object.getOwnPropertyDescriptor(Function.prototype, 'caller');\n"
                        + "var tte = (function () { 'use strict';"
                        + " return Object.getOwnPropertyDescriptor(arguments, 'callee').get; })();\n"
                        + "function sloppy() { return sloppy.arguments.length; }\n"
                        + "function strict() { 'use strict'; }\n"
                        + "var r = [d.get === d.set && d.get === tte,"
                        + " Object.getOwnPropertyDescriptor(Function.prototype, 'arguments').get === tte,"
                        + " sloppy(1, 2), String(sloppy.caller), Object.getOwnPropertyNames(sloppy).join()];\n"
                        + "try { strict.caller; r.push('none'); } catch (e) { r.push(e.name); }\n"
                        + "r.push(Object.getOwnPropertyNames(() => 1).join());\n"
                        + "r.join('|')");
    }

    @Test
    void parametersDoNotSeeBodyLexicals() {
        Utils.assertGatedBehavior(
                "NaN|undefined",
                "out1|object",
                "var x = 'out', r = [], args;\n"
                        + "function f(a = x) { let x = 1; return a + x; }\n"
                        + "function g(y = args = arguments) { let arguments; }\n"
                        + "try { r.push(f()); } catch (e) { r.push(e.name); }\n"
                        + "try { g(); r.push(typeof args); } catch (e) { r.push(e.name); }\n"
                        + "r.join('|')");
    }

    @Test
    void arrayBufferPrototypeBeforeAllocation() {
        Utils.assertGatedBehavior(
                "RangeError",
                "Dummy",
                "var nt = function () {}.bind(null);\n"
                        + "Object.defineProperty(nt, 'prototype', { get() { throw 'Dummy'; } });\n"
                        + "try { Reflect.construct(ArrayBuffer, [7 * Math.pow(1024, 5)], nt); 'none' }"
                        + " catch (e) { typeof e === 'string' ? e : e.name }");
    }

    @Test
    void generatorReturnClosesDestructuringIterators() {
        Utils.assertGatedBehavior(
                "0|1",
                "1|TypeError",
                "var closed = 0;\n"
                        + "var iterable = { [Symbol.iterator]() { return {"
                        + " next() { return { value: undefined, done: false }; },"
                        + " return() { closed++; return null; } }; } };\n"
                        + "function* g() { var a; [a = yield] = iterable; }\n"
                        + "var it = g(); it.next();\n"
                        + "var r; try { it.return(); r = 'none'; } catch (e) { r = e.name; }\n"
                        + "closed + '|' + (r === 'none' ? 1 : r)");
    }

    @Test
    void switchDiscriminantAndCatchParameterSeeOutside() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "outside|inside|outside|inside|1|clean",
                "let x = 'outside'; var r = [], pd, ps, pp, pb;\n"
                        + "switch (pd = function () { return x; }, null) {"
                        + " case ps = function () { return x; }, null: let x = 'inside'; }\n"
                        + "try { throw []; } catch ([_ = pp = function () { return x; }]) {"
                        + " pb = function () { return x; }; let x = 'inside'; }\n"
                        + "let y = 1; switch (y) { case 1: r.push(1); let y = 2; }\n"
                        + "try { switch (y) { case 1: y; let y; } } catch (e) {"
                        + " r.push(e.message.indexOf('$') < 0 ? 'clean' : e.message); }\n"
                        + "[pd(), ps(), pp(), pb()].concat(r).join('|')");
    }

    @Test
    void loopHeadClosuresSeeTheTemporalDeadZone() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "ReferenceError|ReferenceError|clean|1,2,10",
                "let x = 'outside'; var f, g, h, r = [];\n"
                        + "for (let x of (f = function () { typeof x; }, [])) ;\n"
                        + "for (let [x] in (g = () => x, { a: 1 })) ;\n"
                        + "for (let x of (h = (x) => x * 2, [1, 2])) r.push(x);\n"
                        + "var out = [];\n"
                        + "try { f(); } catch (e) { out.push(e.name); }\n"
                        + "try { g(); } catch (e) { out.push(e.name);"
                        + " out.push(e.message.indexOf('$') < 0 ? 'clean' : e.message); }\n"
                        + "out.push(r.concat([h(5)]).join()); out.join('|')");
    }

    @Test
    void pushAndUnshiftHonorPrototypeIndexes() {
        Utils.assertGatedBehavior(
                "1:0|1:0",
                "TypeError:1|TypeError:1",
                "function run(method) {\n"
                        + "  var array = [], calls = 0;\n"
                        + "  Object.defineProperty(Array.prototype, '0', { configurable: true,"
                        + " set(v) { Object.freeze(array); calls++; } });\n"
                        + "  var r; try { array[method](1); r = array.length; } catch (e) { r = e.name; }\n"
                        + "  delete Array.prototype[0]; return r + ':' + calls;\n"
                        + "}\n"
                        + "run('push') + '|' + run('unshift')");
    }

    @Test
    void destructuringTargetReferenceBeforeGet() {
        Utils.assertGatedBehavior(
                "source,key,get,target,target-key,set",
                "source,key,target,target-key,get,set",
                "var log = [];\n"
                        + "function source() { log.push('source');"
                        + " return { get p() { log.push('get'); return 1; } }; }\n"
                        + "function target() { log.push('target');"
                        + " return { set q(v) { log.push('set'); } }; }\n"
                        + "function key() { log.push('key'); return 'p'; }\n"
                        + "function targetKey() { log.push('target-key'); return 'q'; }\n"
                        + "({ [key()]: target()[targetKey()] } = source());\n"
                        + "log.join()");
    }

    @Test
    void toStringReadsIsArrayBeforeTheTag() {
        Utils.assertGatedBehavior(
                "TypeError",
                "[object Array]",
                "var h = Proxy.revocable([], { get: function() { h.revoke(); } });\n"
                        + "try { Object.prototype.toString.call(h.proxy); } catch (e) { e.name }");
    }

    @Test
    void definePropertiesUsesOwnPropertyKeys() {
        Utils.assertGatedBehavior(
                "TypeError",
                "0,foo,Symbol()",
                "var t = {}; t[Symbol()] = 1; t.foo = 2; t[0] = 3; var keys = [];\n"
                        + "var p = new Proxy(t, { getOwnPropertyDescriptor: function(_, k) {"
                        + " keys.push(String(k)); } });\n"
                        + "try { Object.defineProperties({}, p); keys.join(); } catch (e) { e.name }");
    }

    @Test
    void reflectOwnKeysKeepsProxyOrder() {
        Utils.assertGatedBehavior(
                "length,foo,0,Symbol()",
                "Symbol(),length,foo,0",
                "var keys = [Symbol(), 'length', 'foo', '0'];\n"
                        + "var p = new Proxy(new Proxy([], { ownKeys: function() { return keys; } }),"
                        + " {});\n"
                        + "Reflect.ownKeys(p).map(String).join()");
    }

    @Test
    void stringIndexesCannotBeRedefined() {
        Utils.assertGatedBehavior(
                "ok,true,false,s",
                "TypeError,false,true,s",
                "var s = new String('str'), r = [];\n"
                        + "try { Object.defineProperty(s, '0', { value: 'x' }); r.push('ok'); }"
                        + " catch (e) { r.push(e.name); }\n"
                        + "r.push(Reflect.defineProperty(s, '0', { value: 'x' }),"
                        + " Reflect.defineProperty(s, '0', { value: 's' }), s[0]);\n"
                        + "r.join()");
    }

    @Test
    void awaitLabelInStrictScript() {
        Utils.assertGatedBehavior(
                "SyntaxError",
                "ok",
                "try { eval('\"use strict\"; await: 1;'); 'ok'; } catch (e) { e.name }");
    }

    @Test
    void blockGeneratorsAreNotHoisted() {
        Utils.assertGatedBehavior(
                "function,function,function",
                "ReferenceError,undefined,function",
                "var r = [];\n"
                        + "eval('switch (0) { default: function* x() {} }');\n"
                        + "try { r.push(typeof eval('x')); } catch (e) { r.push(e.name); }\n"
                        + "eval('{ function* y() {} } { function z() {} }');\n"
                        + "r.push(typeof y, typeof z); r.join()");
    }

    @Test
    void typedArrayNaNKey() {
        Utils.assertGatedBehavior(
                "true,3,true,0/1",
                "false,,false,0/1",
                "var ta = new Int32Array(2); ta.NaN = 3;\n"
                        + "['NaN' in ta, ta.NaN, Reflect.defineProperty(ta, 'NaN', { value: 1 }),"
                        + " Object.keys(ta).join('/')].join()");
    }

    @Test
    void anonymousClassNamedByComputedKey() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "[t]|kk||e",
                "var s = Symbol('t'), k = 'kk';\n"
                        + "var o = { [s]: class {}, [k]: class { constructor() {} },"
                        + " [Symbol()]: class {}, e: class extends Object {} };\n"
                        + "[o[s].name, o[k].name, o[Object.getOwnPropertySymbols(o)[1]].name,"
                        + " o.e.name].join('|')");
    }

    @Test
    void accessorsReceiveThePrimitive() {
        Utils.assertGatedBehavior(
                "object,object,object,TypeError,TypeError,undefined",
                "number,number,object,set number,TypeError,undefined",
                "var r = [];\n"
                        + "Object.defineProperty(Number.prototype, 'sx', { configurable: true,"
                        + " get: function() { 'use strict'; return typeof this; },"
                        + " set: function(v) { 'use strict'; r.push('set ' + typeof this); } });\n"
                        + "Object.defineProperty(Number.prototype, 'lx', { configurable: true,"
                        + " get: function() { return typeof this; } });\n"
                        + "r.push((5).sx, typeof (5).sx === 'string' ? (5)['sx'] : '', (5).lx);\n"
                        + "(function() { 'use strict';"
                        + " try { (5).sx = 1; } catch (e) { r.push(e.name); }"
                        + " try { (5).nope = 1; r.push('ok'); } catch (e) { r.push(e.name); } })();\n"
                        + "(5).nope = 1; r.push(String((5).nope));\n"
                        + "delete Number.prototype.sx; delete Number.prototype.lx; r.join()");
    }

    @Test
    void superIncrementEvaluatesKeyOnce() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "1,6,1,1",
                "var calls = 0; function k() { calls++; return 'p'; }\n"
                        + "var proto = { p: 1, q: '5', b: 1n };\n"
                        + "var obj = { __proto__: proto,"
                        + " m() { return [super[k()]++, ++super.q, super.b++]; } };\n"
                        + "obj.m().concat([calls]).join()");
    }

    @Test
    void functionDeclarationsAfterParameters() {
        Utils.assertGatedBehavior(
                "function,inner,inner,f",
                "object,outer,inner,f",
                "var args, f = function () { return 'outer'; };\n"
                        + "function g(x = args = arguments) { function arguments() {} }\n"
                        + "g();\n"
                        + "var h = (a = () => f()) => { function f() { return 'inner'; }"
                        + " return [a(), f(), f.name]; };\n"
                        + "[typeof args].concat(h()).join()");
    }

    @Test
    void breakInFinallyCompletionValue() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "42|undefined|2|11",
                "[eval('99; do { -99; try { 39 } finally { 42; break; -2 }; } while (false);'),\n"
                        + " eval('99; do { -99; try { 39 } finally { break; -2 }; } while (false);'),\n"
                        + " eval('1; try { 2 } finally { 3 }'),\n"
                        + " eval('for (var i = 0; i < 2; i++) { try { i } finally { 10 + i; continue; } }')]"
                        + ".map(String).join('|')");
    }

    @Test
    void compoundAssignmentInWithResolvesOnce() {
        Utils.assertGatedBehavior(
                "has:p,get:p,has:p,set:p|1|ReferenceError",
                "has:p,get:Symbol(Symbol.unscopables),has:p,get:p,has:p,set:p|1|ReferenceError",
                "var log = [], env = { p: 0 }, r;\n"
                        + "var proxy = new Proxy(env, {"
                        + " has(t, k) { log.push('has:' + String(k)); return k in t; },"
                        + " get(t, k) { log.push('get:' + String(k)); return t[k]; },"
                        + " set(t, k, v) { log.push('set:' + String(k)); t[k] = v; return true; } });\n"
                        + "with (proxy) { p += 1; }\n"
                        + "with ({}) { try { undeclaredU += 1; } catch (e) { r = e.name; } }\n"
                        + "log.join() + '|' + env.p + '|' + r");
    }

    @Test
    void regExpFlagsGetterRejectsPrimitives() {
        Utils.assertGatedBehavior(
                "TypeError,ok,ok,gi",
                "TypeError,TypeError,TypeError,gi",
                "var g = Object.getOwnPropertyDescriptor(RegExp.prototype, 'flags').get, r = [];\n"
                        + "[null, 4, 's'].forEach(function (v) {"
                        + " try { g.call(v); r.push('ok'); } catch (e) { r.push(e.name); } });\n"
                        + "r.push(g.call(/a/gi)); r.join()");
    }
}
