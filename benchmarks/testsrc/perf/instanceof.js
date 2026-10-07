// instanceof: @@hasInstance must not add a check to every instanceof.
function Base() {}
function Derived() {}
Derived.prototype = new Base();
var objs = [new Derived(), new Base(), {}, [], new Date()];
var n = 0;
for (var i = 0; i < 150000 * PERF_SCALE; i++) {
    var o = objs[i % objs.length];
    if (o instanceof Base) { n++; }
    if (o instanceof Derived) { n++; }
}
if (n === 0) { throw new Error("optimised away"); }
