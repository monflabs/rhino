// Loops, labels, switch, exceptions and finally, with numbers that stay integers and numbers
// that do not.
var r = [];
outer: for (var i = 0; i < 5; i++) {
    for (var j = 0; j < 5; j++) {
        if (j === i) continue outer;
        if (i * j > 6) break outer;
        r.push(i * 10 + j);
    }
}
var w = 0, x = 0.5;
while (w < 100) { w = w * 2 + 1; x = x * 1.5; }
do { w -= 7; } while (w > 50);
r.push(w, x);
function f(n) {
    try { if (n === 0) throw new RangeError("zero"); if (n < 0) return "neg"; return "pos"; }
    catch (e) { return e.name; }
    finally { r.push("finally" + n); }
}
r.push(f(1), f(0), f(-1));
function g(n) { for (var k = 0; ; k++) { try { if (k === n) return k; } finally { if (k === 2) break; } } return "broke"; }
r.push(g(1), g(5));
var s = "";
for (var key in { b: 1, a: 2, 1: 3, 0: 4 }) s += key;
for (var ch of "héllo😀") s += "[" + ch + "]";
r.push(s);
var m = new Map([[1, "a"], ["1", "b"], [NaN, "c"]]);
r.push(m.get(1), m.get("1"), m.get(NaN), [...new Set([3, 1, 3, 2, 1])].join());
var big = 0;
for (var t = 0; t < 1000; t++) { big += t * t; big |= 0; }
r.push(big, 2147483647 + 1, (2147483647 + 1) | 0, -2147483648 - 1, 0.1 + 0.2, 1 / 3, 2 ** 53 + 1);
var n = 0;
switch (typeof n) { case "string": r.push("s"); case "number": r.push("n"); case "x": r.push("fall"); break; default: r.push("d"); }
var { a, b: [c, ...d], ...e } = { a: 1, b: [2, 3, 4], f: 5, g: 6 };
r.push(a, c, d, e);
r.push(null ?? "nn", undefined?.x, ({ y: { z: 1 } })?.y?.z, (void 0)?.[1]);
var tag = (s, ...v) => s.raw.join("|") + v.join("|");
r.push(tag`a${1}b${2}c`);
JSON.stringify(r);
