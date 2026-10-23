// Closures, arguments, rest/default parameters, this binding and recursion, across direct and
// indirect calls.
function counter(start) { var c = start; return { inc: function () { return ++c; },
    get: function () { return c; } }; }
var c1 = counter(5); c1.inc(); c1.inc();
function defaults(a, b = a * 2, ...rest) { return [a, b, rest.length, arguments.length]; }
function args() { arguments[0] = 9; return [arguments.length, arguments[0]]; }
function sloppyArgs(a) { arguments[0] = 7; return a; }
function strictArgs(a) { "use strict"; arguments[0] = 7; return a; }
function fib(n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
function fact(n) { if (n <= 1) { return 1; } return n * fact(n - 1); }
var fns = [];
for (let i = 0; i < 4; i++) { fns.push(() => i * i); }
var vfns = [];
for (var k = 0; k < 4; k++) { vfns.push(function () { return k; }); }
var o = { v: 3, m() { return this.v; }, a: () => typeof this };
function Point(x, y) { this.x = x; this.y = y; }
Point.prototype.len = function () { return Math.sqrt(this.x * this.x + this.y * this.y); };
class Shape { constructor(n) { this.n = n; } area() { return 0; } toString() { return this.n + ":" + this.area(); } }
class Sq extends Shape { constructor(s) { super("sq"); this.s = s; } area() { return this.s * this.s; } }
class Priv { #x = 1; static #count = 0; inc() { Priv.#count++; return ++this.#x; } static get count() { return Priv.#count; } }
var p = new Priv(); p.inc(); p.inc();
var results = [c1.get(), defaults(1), defaults(1, 2, 3, 4), args(1, 2), sloppyArgs(1), strictArgs(1),
    fib(20), fact(20), fact(25), fns.map(f => f()), vfns.map(f => f()), o.m(), o.a(),
    new Point(3, 4).len(), String(new Sq(3)), p.inc(), Priv.count,
    [1, 2, 3].reduce((s, x) => s + x * x, 0), Math.max.apply(null, [1, 5, 3]),
    (function () { return typeof this; }).call(5), (function () { "use strict"; return typeof this; }).call(5)];
JSON.stringify(results);
