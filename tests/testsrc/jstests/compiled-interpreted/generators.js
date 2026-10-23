// yield and await in every kind of expression, including the arguments of direct calls.
function id(x) { return x; }
function add(a, b) { return a + b; }
function cmp(a) { return a === 10; }

function* gen() {
    var r = [];
    r.push(id(yield 1));
    r.push(add(yield 2, yield 3));
    r.push(cmp(yield 4));
    r.push((yield 5) + (yield 6));
    r.push([yield 7, ...(yield 8)]);
    r.push({ a: yield 9, [yield 10]: 1 });
    r.push(`t${yield 11}t`);
    r.push((yield 12) ? yield 13 : yield 14);
    r.push(id(id(yield 15)));
    try { r.push(yield 16); throw new Error("e"); }
    catch (e) { r.push(e.message, yield 17); }
    finally { r.push(yield 18); }
    for (var i = 0; i < 3; i++) { r.push(add(i, yield 19 + i)); }
    r.push(yield* inner());
    var { p = yield 30, q } = { q: yield 31 };
    r.push(p, q);
    return JSON.stringify(r);
}
function* inner() { var x = yield 20; return id(x) * 2; }

var trace = [];
var it = gen();
var step = it.next();
var n = 0;
while (!step.done) {
    trace.push(step.value);
    n++;
    var send = step.value === 8 ? [n, n] : step.value === 10 ? "k" + n : n === 12 ? 0 : n;
    step = it.next(send);
}
trace.push(step.value);

async function af(p) {
    var r = [];
    r.push(id(await p));
    r.push(add(await p, await 2));
    r.push(cmp(await 10));
    r.push([await 1, await p].join());
    try { r.push(await Promise.reject(new Error("rej"))); }
    catch (e) { r.push(e.message); }
    for (var i = 0; i < 3; i++) { r.push(add(i, await i)); }
    for await (var v of agen()) { r.push(v); }
    return r.join(",");
}
async function* agen() { yield id(await 41); yield add(await 1, 1); }

var asyncResult = "pending";
af(Promise.resolve(5)).then(function (v) { asyncResult = v; }, function (e) { asyncResult = "error " + e; });
trace.join(",");
