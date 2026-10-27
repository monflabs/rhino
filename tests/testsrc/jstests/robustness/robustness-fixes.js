load("testsrc/assert.js");

// Regressions found by the robustness harnesses of JavascriptPerformance (JSC stress tests,
// differential fuzzing against V8, Jazzer), one function per bug.

(function integerIncrementPastIntRange() {
    var i;
    for (i = 2147483645; i < 2147483650; ++i) {}
    assertEquals(2147483650, i);
    var v = -2147483648;
    var o = { 0: (v--) };
    assertEquals(-2147483648, o[0]);
    assertEquals(-2147483649, v);
    var w = 2147483647;
    w++;
    assertEquals(2147483648, w);
})();

(function integerMultiplyNegativeZero() {
    function mul(a, b) { return a * b; }
    assertTrue(Object.is(mul(0, -3), -0));
    assertTrue(Object.is(mul(-5, 0), -0));
    assertTrue(Object.is(mul(0, 0), 0));
})();

(function arrowFunctionHasNoPrototype() {
    var arrow = () => 1;
    assertEquals(undefined, arrow.prototype);
    assertFalse(arrow.hasOwnProperty("prototype"));
})();

(function boundFunctionInstanceof() {
    var arrow = () => {};
    var bound = arrow.bind();
    assertFalse(Function.prototype[Symbol.hasInstance].call(bound, 1));
    assertFalse(1 instanceof bound);
    assertFalse(Symbol() instanceof bound);
    function F() {}
    var B = F.bind(null);
    assertTrue(new F() instanceof B);
})();

(function regExpStringIteratorReceiver() {
    var next = Object.getPrototypeOf("aa".matchAll(/a/g)).next;
    assertThrows(function () { next.call(Object.getPrototypeOf("aa".matchAll(/a/g))); }, TypeError);
    assertThrows(function () { next.call([1, 2][Symbol.iterator]()); }, TypeError);
    assertThrows(function () { [...Object.getPrototypeOf("aa".matchAll(/a/g))]; }, TypeError);
})();

(function joinPastTheStringLimit() {
    var big = "x".repeat(1 << 20);
    assertThrows(function () { new Array(1024).fill(big).join(""); }, RangeError);
    assertThrows(function () { "x".padStart(2 ** 31 - 1, "ab"); }, RangeError);
})();

(function dateSettersOutOfRange() {
    assertTrue(isNaN(new Date(0).setMilliseconds(-1.79769e+308)));
    assertTrue(isNaN(new Date(0).setSeconds(1.79769e+308)));
})();

(function logicalAssignmentToUndefined() {
    function and() { return undefined &&= 42; }
    function or() { return undefined ||= 42; }
    function nullish() { return undefined ??= 42; }
    assertEquals(undefined, and());
    assertEquals(42, or());
    assertEquals(42, nullish());
    assertThrows(function () { (new Function("x &&= 42; let x = true;"))(); }, ReferenceError);
})();

(function superPropertyAccess() {
    function func(x) { return "global"; }
    function value() { return "v"; }
    class A {
        constructor() { this._v = 1; this.got = []; }
        get v() { return this._v; }
        set v(x) { this._v = x; }
        get func() { return function (x) { return "getter " + x; }; }
        set foo(x) { this.got.push(x); }
    }
    class B extends A {
        call() { return super.func(1); }
        plusEq(x) { super[value()] += x; return this._v; }
        loops() { for (super.foo in {a: 1, b: 2}) {} for (super.foo of [3]) {} [super.foo] = [4]; return this.got.join(); }
    }
    var b = new B();
    assertEquals("getter 1", b.call());
    assertEquals(6, b.plusEq(5));
    assertEquals("a,b,3,4", b.loops());
})();

(function computedKeysOfNumberVariables() {
    function t() { let c = 0; return JSON.stringify({ [++c]: ++c }); }
    function u() { let c = 0; return JSON.stringify({ [c]: ++c }); }
    function w() { var x = 4096; return JSON.stringify({...x, [x]: x}); }
    assertEquals('{"1":2}', t());
    assertEquals('{"0":1}', u());
    assertEquals('{"4096":4096}', w());
})();

(function exponentInAGeneratorLoop() {
    function* g() { let i = 0; while (i++ < 3) { yield i ** 2; } }
    assertEquals("1,4,9", [...g()].join());
})();

(function generatorReturnFromFinally() {
    function* g() { try { yield 1; } finally { return 5; } }
    var it = g();
    it.next();
    assertEquals('{"value":5,"done":true}', JSON.stringify(it.return(9)));
    assertEquals('{"done":true}', JSON.stringify(it.next()));
})();

(function deeplyNestedFinally() {
    var src = "";
    for (var i = 0; i < 30; i++) src += "try {";
    src += "return 0;";
    for (var i = 0; i < 30; i++) src += "} finally { return 1; }";
    assertEquals(1, new Function(src)());
})();

(function stackOverflowIsARangeError() {
    function f() { return f() + 1; }
    assertThrows(f, RangeError);
})();

(function spreadOfANumber() {
    assertEquals("{}", JSON.stringify({...5}));
    assertEquals("{}", JSON.stringify({...1e21}));
})();

(function fromEntriesWithIndexKeys() {
    var o = Object.fromEntries([["1", "one"], ["a", "x"]]);
    assertEquals("one", o[1]);
    assertEquals("1,a", Object.keys(o).join());
})();

(function shortestSubnormals() {
    assertEquals("5e-324", String(Number.MIN_VALUE));
    assertEquals("1e-323", String(2 * Number.MIN_VALUE));
    assertEquals("1.5e-323", String(3 * Number.MIN_VALUE));
})();

(function longBigIntLiteral() {
    var digits = "123456789".repeat(400);
    assertEquals(digits, eval(digits + "n").toString());
    assertEquals(digits, BigInt(digits).toString());
})();

(function tooManyCaptureGroups() {
    assertThrows(function () { new RegExp("(a)".repeat(70000)); }, SyntaxError);
})();

(function strictnessAfterAStrictCalleeThrows() {
    // a sloppy caller stays sloppy: an assignment to a primitive's property is silently dropped
    function strictThrow() { "use strict"; throw new Error("x"); }
    var primitive = 0;
    try { strictThrow(); } catch (e) {}
    primitive[1] = 2;
    class Throws { constructor() { throw new Error("x"); } }
    try { new Throws(); } catch (e) {}
    primitive[1] = 2;
    new Promise(function () { "use strict"; throw new Error("x"); }).catch(function () {});
    primitive[1] = 2;
    assertEquals(undefined, primitive[1]);
})();

(function cyclicJoin() {
    // an array that holds itself joins to "" where it recurs, through join as through toString
    var a = [-0, "t", -1];
    a[1] = a;
    assertEquals("0,,-1", String(a));
    assertEquals("0,,-1", a.join());
    assertEquals("0;;-1", a.join(";"));
})();

"success";
