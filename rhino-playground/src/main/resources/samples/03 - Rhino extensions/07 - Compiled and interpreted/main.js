// Rhino has two ways to run a script: compiled to Java classes that the JVM optimizes, or run by
// its interpreter. Switch Compiled / Interpreted in the toolbar and compare the times.
function fib(n) {
    return n < 2 ? n : fib(n - 1) + fib(n - 2);
}

function time(label, f) {
    var start = Date.now();
    var result = f();
    print(label, result, (Date.now() - start) + ' ms');
}

time('fib(25)', () => fib(25));
time('sort of 100,000 numbers', () => {
    var a = [];
    for (var i = 0; i < 100000; i++) {
        a.push((i * 7919) % 100003);
    }
    return a.sort((x, y) => x - y)[50000];
});
time('string building', () => {
    var s = '';
    for (var i = 0; i < 20000; i++) {
        s += i % 10;
    }
    return s.length;
});

// The stack trace shows the same lines either way
try {
    null.f();
} catch (e) {
    print(e.message);
}
