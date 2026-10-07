// Built-in methods called on a primitive string.
var s = "abcdefghijklmnopqrstuvwxyz0123456789";
var total = 0;
for (var round = 0; round < 3 * PERF_SCALE; round++) {
    for (var i = 0; i < 20000; i++) {
        total += s.charCodeAt(i % 36);
        total += s.charAt(i % 36).length;
        total += s.slice(2, 8).length;
        total += s.indexOf("z");
        total += s.substring(i % 10, (i % 10) + 5).length;
    }
}
if (total === 0) { throw new Error("optimised away"); }
