// Function declarations called by name are direct calls in compiled code, and their parameters
// may be passed as unboxed numbers: every operator on such a parameter, against every kind of
// operand.
var values = [0, -0, 1, 10, 2.5, -3, NaN, Infinity, "10", "", "x", true, null, undefined, 10n, {}];

function eq(a) { return [a == 10, a != 10, a === 10, a !== 10, a == 0, a === 0, a !== 0,
    a == "10", a === "10", a == null, a === null, a === undefined, a == true, a === a]; }
function rel(a) { return [a < 10, a <= 10, a > 10, a >= 10, a < 0, a > -1, 10 < a, 0 >= a]; }
function arith(a) { return [a + 1, a - 1, a * 2, a / 4, a % 3, -a, +a, a + "", 1 + a, 7 - a]; }
function bits(a) { return [a | 0, a & 3, a ^ 5, ~a, a << 2, a >> 1, a >>> 1]; }
function unary(a) { return [typeof a, !a, !!a, void a, a ? 1 : 2, a || 3, a && 4, a ?? 5]; }
function incdec(a) { var b = a; b++; var c = a; c--; var d = a; ++d; var e = a; --e;
    var f = a; f += 2; var g = a; g *= 3; return [b, c, d, e, f, g]; }
function two(a, b) { return [a === b, a !== b, a == b, a < b, a + b, a - b, a * b]; }
function branch(a) { if (a === 10) return "is10"; if (a !== 0) return "not0"; return "else"; }
function loop(n) { var s = 0; for (var i = 0; i < n; i++) { if (i === 3) continue; s += i; }
    return s; }
function rec(n) { return n === 0 ? 0 : n + rec(n - 1); }
function swi(a) { switch (a) { case 10: return "ten"; case "10": return "str"; case 0: return "zero";
    default: return "other"; } }

function safe(f, a, b) {
    try { return String(arguments.length > 2 ? f(a, b) : f(a)); }
    catch (e) { return e.name; }
}

var out = [];
for (var i = 0; i < values.length; i++) {
    var v = values[i];
    out.push([safe(eq, v), safe(rel, v), safe(arith, v), safe(bits, v), safe(unary, v),
        safe(incdec, v), safe(branch, v), safe(swi, v)].join(" | "));
    for (var j = 0; j < values.length; j++) {
        out.push(safe(two, v, values[j]));
    }
}
// literal arguments, which take the unboxed path without a variable in between
out.push(eq(10), eq(0), eq(2.5), rel(10), arith(3), bits(-7), unary(0), incdec(1.5));
out.push(two(10, 10), two(1, 2), two(NaN, NaN), two(0, -0), branch(10), branch(0), branch(1));
out.push(loop(10), rec(20), swi(10), swi(0), swi(1));
out.join("\n");
