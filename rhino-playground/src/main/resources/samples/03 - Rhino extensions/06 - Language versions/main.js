// Rhino keeps the language versions of its past: switch Version in the toolbar between ES2026
// and ES6 and run again to compare.
function attempt(label, f) {
    try {
        print(label + ':', f());
    } catch (e) {
        print(label + ':', e.name, '-', e.message);
    }
}

// ES2026 follows the specification: assigning a const throws
attempt('assignment to a const', () => {
    const c = 1;
    c = 2;
    return c;
});

// A let binding cannot be read before its declaration (the temporal dead zone)
attempt('read before let', () => {
    var before = typeof later;
    let later = 1;
    return before;
});

// Each iteration of a for (let ...) loop has its own binding, which closures capture
attempt('closures in a let loop', () => {
    var fs = [];
    for (let i = 0; i < 3; i++) {
        fs.push(() => i);
    }
    return fs.map(f => f()).join();
});

// Functions lost their non-standard arity property
attempt('arity of a function', () => (function (a, b) {}).arity);

// Syntax that stock Rhino did not have is ES2026 only: classes, async functions, private names
function supports(source) {
    try {
        eval(source);
        return 'yes';
    } catch (e) {
        return 'no (' + e.name + ')';
    }
}
print('classes:', supports('class A { #x = 1; static { } }'));
print('async functions:', supports('(async () => await 1)'));
