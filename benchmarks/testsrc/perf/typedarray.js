// Typed array element writes, including the ones an internal loop makes (set): the check that
// decides whether a key names an element runs on every write.
var a = new Int32Array(1024);
var b = new Int32Array(1024);
var total = 0;
for (var n = 0; n < 500 * PERF_SCALE; n++) {
    for (var i = 0; i < 1024; i++) {
        a[i] = i + n;
    }
    b.set(a);
    total += b[1023];
}
if (total === 0) { throw new Error("optimised away"); }
