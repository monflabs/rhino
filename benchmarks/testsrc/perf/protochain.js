// Ordinary prototype-chain lookups and method calls; Proxy support must not slow them down.
function A() { this.a = 1; }
A.prototype.m = function() { return this.a; };
function B() { A.call(this); }
B.prototype = Object.create(A.prototype);
function C() { B.call(this); }
C.prototype = Object.create(B.prototype);
var o = new C(), n = 0;
for (var i = 0; i < 300000 * PERF_SCALE; i++) { n += o.m(); }
if (n === 0) { throw new Error("optimised away"); }
