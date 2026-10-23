# Changes and incompatibilities

This page helps an embedder move from stock Mozilla Rhino 1.9.1 to the Monflabs fork (release 2026.0). It lists
what changes, why, and what to do about it. The new language features are described in
[Features on top of stock Rhino](features.md), and the engine optimizations in
[Performance optimizations](performance.md).

The complete reference, with one numbered section per change, a before/after example and the ECMA-262 section
for each, is [BACKWARD_COMPATIBILITY.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/BACKWARD_COMPATIBILITY.md).
The numbers in brackets on this page, such as **[1.3]**, refer to its sections.

## The rule: changes are gated by the language version

The fork adds a language version, `Context.VERSION_ECMASCRIPT` (250), which targets ECMAScript 2026 and passes
test262. Every change in the behavior of JavaScript code is active **only** under that version:

| Language version | Behavior |
|------------------|----------|
| `VERSION_ECMASCRIPT` (250) | ECMAScript 2026: new features, spec-conformant semantics, new early errors |
| `VERSION_ES6` (200) and older | Stock Rhino behavior, bug fixes included |

**`VERSION_ECMASCRIPT` is the default language version of the fork [2.3]** (stock Rhino defaults to
`VERSION_ES6`). This is the biggest incompatibility: an embedding that never calls `setLanguageVersion` gets every
change of [Switching to VERSION_ECMASCRIPT](#switching-to-version_ecmascript). To keep stock behavior, select
`VERSION_ES6` explicitly, as shown [below](#the-default-language-version-is-version_ecmascript-23); code then runs
as on stock Rhino, and the only changes left are the platform and packaging ones of the next section.

Inside the engine, every gated behavior tests `Context.isLatestEcmaScript()` (runtime) or
`CompilerEnvirons.isLatestEcmaScript()` (compile time). The full list of gated behaviors is also in the Javadoc of
`Context.VERSION_ECMASCRIPT`.

## Changes for every embedding

These apply whatever the language version. The first one decides which language version your scripts get.

### The default language version is VERSION_ECMASCRIPT [2.3]

This is the largest incompatibility of the fork, and the first thing to decide. A new `Context`, a new
`CompilerEnvirons`, the shell (`-version 250`), the debugger and the `javax.script` engine (`LANGUAGE_VERSION`
`"250"`) all default to `VERSION_ECMASCRIPT`; stock Rhino defaults to `VERSION_ES6`.

To keep stock behavior, set `VERSION_ES6` on every new `Context`:

```java
ContextFactory factory = new ContextFactory() {
    @Override
    protected void onContextCreated(Context cx) {
        super.onContextCreated(cx);
        cx.setLanguageVersion(Context.VERSION_ES6);
    }
};
```

- Shell: run it with `-version 200`.
- `javax.script`: put `ScriptEngine.LANGUAGE_VERSION` = `200` in the engine scope.

In the `javax.script` engine under `VERSION_ECMASCRIPT`, the global `let`, `const` and `class` declarations of a
script are visible to the scripts evaluated after it with the same bindings, and are not stored in the bindings;
a global function declaration is stored there, as before. Evaluating the same top-level `let` twice with the same
bindings is a SyntaxError.

### Packages and Maven coordinates [2.1]

| | Stock Rhino | Monflabs fork |
|---|---|---|
| Java packages | `org.mozilla.javascript…` | `org.monflabs.mozilla.javascript…` |
| Java module names | `org.mozilla.rhino…` | `org.monflabs.mozilla.rhino…` |
| OSGi bundle symbolic names | `org.mozilla.rhino…` | `org.monflabs.mozilla.rhino…` |
| Maven group id | `org.mozilla` | `org.monflabs.mozilla` |
| Version | `1.9.1` | `2026.0` |

The artifacts keep their names (`rhino`, `rhino-tools`, `rhino-xml`, `rhino-engine`, `rhino-all`,
`rhino-kotlin`). Because nothing shares a name, the fork and stock Rhino can live on the same class path or module
path.

```xml
<dependency>
  <groupId>org.monflabs.mozilla</groupId>
  <artifactId>rhino</artifactId>
  <version>2026.0</version>
</dependency>
```

What to do: replace `org.mozilla.` with `org.monflabs.mozilla.` in

- Java imports and `module-info.java` `requires` clauses;
- scripts that reach Rhino classes through `Packages.org.mozilla…`;
- `META-INF/services` files and any configuration that names Rhino classes;
- dependency declarations (Maven, Gradle, OSGi manifests).

### Java 21 minimum; Java server applications only [2.2]

- The engine is compiled with `--release 21` and requires Java 21 or later (stock Rhino: Java 17). The OSGi
  bundles require `JavaSE` 21.
- The minimum is planned to move to Java 25 when it becomes available to the project (expected early 2027).
- The fork targets Java server applications. Android and micro JVMs are not supported; the Android integration
  tests were removed.
- Unicode data (identifier characters, case mapping, RegExp property escapes) comes from the running JDK, so
  results for recent Unicode characters can depend on the JDK version. The conformance reference is Java 25.

### Fallback to the interpreter is logged [2.4]

When the class compiler cannot compile a script (a compiler failure, a `VerifyError`, or a class file limit that
cannot be worked around), the script still runs in the interpreter, which is much slower. The fork now reports it:
a warning to the script's error reporter (`msg.compile.fallback` or `msg.compile.too.large`) and a `WARNING` on the
JDK platform logger `org.monflabs.mozilla.javascript`. A fallback caused by a compiler failure is a bug worth
reporting. To silence the log, set the level of that logger in your logging configuration.

### Java API changes

- New APIs, all opt-in, are described in [Features on top of stock Rhino](features.md): ES modules
  (`Context.loadModule`, `Context.evaluateModule`, the `org.monflabs.mozilla.javascript.modules` loaders, which
  require `VERSION_ECMASCRIPT`), and `HostJobScheduler`/`EventLoop` for `Atomics.waitAsync`.
- `AbstractEcmaObjectOperations.ordinarySet(..., Scriptable receiver)` is deprecated, with its stock semantics, next
  to a new overload taking an `Object` receiver.
- Embedding notes under `VERSION_ECMASCRIPT`, detailed in [Java interop and embedding](#java-interop-and-embedding)
  below: global `let`/`const` are not properties of the global object, and an uninitialized binding reads as
  `UniqueTag.UNINITIALIZED`.
- The build: Gradle is the only build (the optional Bazel build of upstream was removed).

### Bug fixes for every version

Behavior fixes are gated like the rest, so `VERSION_ES6` keeps the bugs of stock Rhino. The exceptions are fixes
of the compiled mode that turn a crash or a fallback to the interpreter into compiled code with the same result,
for example a `yield` inside an array literal with a spread element, or a backward jump of more than 32 KB at the
end of a very large loop. They are listed in
[CHANGELOG_MONFLABS.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/CHANGELOG_MONFLABS.md) as
"Fix (all versions)". Performance work also applies to every version and is not observable; see
[Performance optimizations](performance.md).

## Switching to VERSION_ECMASCRIPT

The changes below only apply to code running under `VERSION_ECMASCRIPT`. "Before" is the behavior of stock Rhino
(and of the fork under `VERSION_ES6`), "now" the behavior under `VERSION_ECMASCRIPT`.

Most of them only affect code that relied on non-standard behavior. The ones most likely to show up in real code
are, in order: `const` and the temporal dead zone (1.1, 1.2), global `let`/`const` (1.3), new early errors (1.12,
1.26, 1.32), the strict `this` of functions (1.22, 1.69), array destructuring through iterators (1.29), the
`Iterator` global (1.64) and typed array byte order (1.6).

The features stock Rhino did not have (classes, async functions, modules, `for await`, spread arguments, the new
built-ins) also exist only under this version; see [Features on top of stock Rhino](features.md).

### Scoping and declarations

**`const` cannot be assigned [1.1, 1.20].** Every form of assignment throws a TypeError, in sloppy code too.

```js
const k = 1;
k = 2;        // before: ignored (TypeError in strict mode)   now: TypeError
```

**Temporal dead zone [1.2, 1.2b, 1.75, 1.91].** Using a `let`/`const` before its declaration has run is a
ReferenceError, including from a closure, from a default parameter value, or from a `switch` clause entered past
the declaration.

```js
function f() {
  g();             // before: g returns undefined   now: ReferenceError
  let x = 1;
  function g() { return x; }
}
function h(a = b, b) {}  h();   // before: a is undefined   now: ReferenceError
```

**Global lexical declarations [1.3].** The top-level `let`/`const` of a script are bindings of the global scope,
shared by every script run in it, but not properties of the global object. Declaring the same name twice, even
from two scripts, is a SyntaxError.

```js
// script 1                  // script 2, same scope
let x = 1;                   let x = 2;      // before: x becomes 2   now: SyntaxError
"x" in globalThis;           // before: true   now: false
```

A REPL or a server that re-runs the same script in one scope must use a fresh scope, or `var`, for state that is
redefined.

**Loops create one binding per iteration [1.17, 1.18, 1.19, 1.21].**

```js
var fs = [];
for (let i = 0; i < 3; i++) fs.push(() => i);
fs.map(f => f());     // before: [3, 3, 3]   now: [0, 1, 2]
```

Also, in functions without closures, `const c = i` in a loop body takes each iteration's value (before, the first
one), `for (const v of a)` assigns `v` (before, it stayed `undefined`), and `let x;` resets `x` to `undefined` on
each iteration.

**Other scoping changes**

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| `eval("let a = 1"); typeof a` | `"number"` | `"undefined"` (eval's own block) | 1.5 |
| `let x; eval("var x")` | allowed | SyntaxError | 1.4 |
| `var NaN;` at top level | TypeError | allowed; declarations validated before any is created | 1.11 |
| `var toString; typeof toString` (global) | `"function"` | `"undefined"` | 1.14 |
| `var f = function g() { g = 1; return typeof g; }` | `"number"` | `"function"` (TypeError if strict) | 1.37 |
| `function f(g = () => x) { var x = 1; return g(); }` | the body's `x` | the outer `x` | 1.68 |
| `function f(x = arguments) { function arguments() {} }` | `x` is the function | `x` is the arguments object | 1.83 |
| `switch (x) { case x: let x; }` | ReferenceError | the outer `x` | 1.74 |
| `{ function* g() {} } typeof g` (sloppy) | `"function"` | `"undefined"` | 1.79 |
| Completion value of `eval("1; if (true) {}")` | `1` | `undefined` | 1.8 |

### Syntax and early errors

Code that the specification rejects no longer compiles: the SyntaxError is reported when the script is compiled,
not when the faulty line runs. This is the area most likely to break existing scripts that "worked" on stock Rhino.

```js
const x;                     // before: x is undefined                 now: SyntaxError
f() &&= 1;                   // before: ReferenceError at run time     now: SyntaxError
"use strict"; delete x;      // before: runs                           now: SyntaxError
function f(a = 0, a) {}      // before: accepted                       now: SyntaxError
({ get a(x) {} });           // before: accepted                       now: SyntaxError
function g() { yield 1; }    // before: a JavaScript 1.7 generator     now: SyntaxError
```

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| Invalid assignment targets, `delete` of a name and octal escapes in strict code, escaped keywords, `08n`, `3in x` | various | SyntaxError | 1.12 |
| A declaration as the body of `if`, a loop, `with` or a label | accepted | SyntaxError | 1.12 |
| `continue` to a label that is not a loop | continues | SyntaxError | 1.12 |
| `let` redeclaring a parameter or a catch parameter | accepted | SyntaxError | 1.12 |
| Duplicate parameters with defaults, in methods, or in a function whose body is strict | accepted | SyntaxError | 1.26 |
| `"use strict"` in a function with non-simple parameters | accepted | SyntaxError | 1.26 |
| Setter without exactly one parameter, rest parameter with a default, duplicate `__proto__` | accepted | SyntaxError | 1.32 |
| Strict reserved words (`public`, `static`, …) right after `"use strict"` | accepted | SyntaxError | 1.32 |
| `yield` inside the parameters of an arrow in a generator | accepted | SyntaxError | 1.67 |
| `yield*` split across lines | `yield* 1` | SyntaxError | 1.33 |
| `for (async of [])` | a for-of loop | SyntaxError | 1.15 |
| `var let = 1;`, `var yield = 1;`, `var await = 1;` (sloppy scripts) | SyntaxError | variables | 1.10, 1.33, 1.56 |
| `var f = ({ x = 1 }) => x;`, `[{ x = 1 }] = [{}]` | SyntaxError | valid | 1.52, 1.54 |
| `'use str\<newline>ict'` | strict | not a directive | 1.54 |
| `*/ -->` after a multi-line comment | SyntaxError | HTML-like comment | 1.55 |
| A raw ZWNJ/ZWJ inside an identifier | stripped (`a‌b` is `ab`) | part of the name | 1.16 |
| `for (var [k, v] in { ab: 1 })` | `k = "ab"`, `v = 1` | destructures the key: `"a"`, `"b"` | 1.9 |

`eval?.()` and `eval` used as a tag are indirect evals, an indirect eval is never strict, and `eval(...args)` is a
direct eval **[1.12, 1.28, 1.52]**.

### Functions and `this`

**Strict functions get an unconverted `this` [1.22, 1.69, 1.81, 1.89].**

```js
"use strict";
function f() { return typeof this; }
f();                 // before: "object"   now: "undefined"
f.call(1);           // before: "object"   now: "number"
```

A strict method or getter called on a primitive receives the primitive. Methods of `String.prototype` called on a
primitive string receive the string itself, so an override of `String.prototype.toString` no longer changes their
result.

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| `(function (a, b) {}).arity` | `2` | `undefined` (use `length`) | 1.66 |
| `strictFn.caller`, `Math.max.caller`, `strictFn.arguments` | `undefined` / `null` | TypeError | 1.23, 1.71 |
| `Object.getOwnPropertyNames(sloppyFn)` | `length,name,prototype` | adds `arguments`, `caller` | 1.71 |
| `t.bind().name` with a redefined `name` | `"bound "` | `"bound target"` | 1.24 |
| `function g(x, ...r) { x = 2; return arguments[0]; } g(1)` | `2` | `1` (unmapped `arguments`) | 1.25 |
| `new (Object.getOwnPropertyDescriptor({ get a() {} }, "a").get)()` | an object | TypeError | 1.35 |
| `({ *g() {} }).g.toString()` | `"() {}"` | `"*g() {}"` | 1.27 |
| `String(Function("a", "return a"))` | `function anonymous(a) {…}` | spec text; error line numbers in the body are 2 higher | 1.27 |
| `function f(g = function () {}) { return g.name; }` | `""` | `"g"` | 1.30, 1.34 |
| `({ get foo() {}, foo: 1 }).foo` (strict) | TypeError | `1` | 1.70 |

### Destructuring and iteration

**Array patterns use the iterator protocol [1.29].**

```js
var [a] = { 0: 1, length: 1 };      // before: a is 1    now: TypeError (not iterable)
var [b, , c] = new Set([1, 2, 3]);  // before: undefined  now: b = 1, c = 3
var {} = null;                      // before: fine       now: TypeError   [1.30]
```

**The `Iterator` global is the ES2025 Iterator [1.64].** The JavaScript 1.7 `Iterator(obj)` function, which also
iterated over Java collections, is replaced by the abstract `Iterator` constructor with `Iterator.from` and the
iterator helpers. `StopIteration` stays.

```js
Iterator(javaList).next();          // before: the first element   now: TypeError
for (const e of javaList) { … }     // works in both
Iterator.from(javaList).map(…)      // now: Java iterables get the helpers
```

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| `for (x of it) break;` | iterator left open | `it.return()` is called | 1.31 |
| Result of `next()` that is not an object | converted | TypeError | 1.31 |
| Built-in iterator prototypes | inherit `Object.prototype` | inherit `Iterator.prototype` | 1.64 |
| Iterator results with a setter on `Object.prototype.value` | setter called | own data properties | 1.87 |
| Order of evaluation in `({ [k()]: t()[tk()] } = s())` | reads the source first | targets first | 1.30, 1.77 |
| Generator `return()` with `yield` in a `finally` | TypeError or wrong value | completes as specified | 1.59, 1.73 |
| A strict generator resumed from sloppy code | runs sloppy | runs strict | 1.58 |

### Objects, properties, Proxy and Reflect

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| `Object.getPrototypeOf({ ['__proto__']: p }) === p` | `true` | `false` (own property) | 1.14 |
| `Object.defineProperty(o, "x", { value: 2 })` on a non-configurable writable `x` | TypeError | allowed | 1.38 |
| `Reflect.set(Object.freeze({x: 1}), "x", 2)` | `true` | `false` | 1.40 |
| A proxy `set` trap returning `false`, in strict code | ignored | TypeError | 1.40 |
| Proxy invariant violations (`getOwnPropertyDescriptor`, `defineProperty`, …) | allowed | TypeError | 1.40 |
| `Object.prototype.toString.call(new Proxy([], {}))` | `"[object Object]"` | `"[object Array]"` | 1.43, 1.78 |
| `Object.assign(Object.freeze({ a: 1 }), { a: 2 })` | ignored | TypeError | 1.44 |
| `Object.setPrototypeOf(Object.prototype, {})` | allowed | TypeError | 1.47 |
| `with` and `Symbol.unscopables` | ignored | honored | 1.51 |
| `o.p &&= 1` with a read-only `o.p = 0` | write attempted | no write | 1.51 |
| `1 instanceof Math` | `false` | TypeError | 1.52 |
| `a?.b[0]` with `a` undefined | TypeError | `undefined` | 1.57 |
| `for-in` over a key shadowed by a non-enumerable own property | listed | skipped | 1.54 |
| `Reflect.ownKeys` of a proxy | integer keys first | the trap's order | 1.78 |

### Built-ins

**Array methods throw when a write fails [1.42, 1.45, 1.47, 1.76].**

```js
Object.freeze([1]).push(2);               // before: 2, nothing changed   now: TypeError
var a = [0, 1, 2]; Object.defineProperty(a, "1", { configurable: false });
a.length = 0;                             // before: length 0   now: length 2
```

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| `Array.prototype.toString.call({ join() { return "x"; } })` | `""` | `"x"` | 1.45 |
| `[].push(1)` with a setter on `Array.prototype[0]` | stored in the array | setter called | 1.76 |
| `"a1b".match(1)` with a custom `Number.prototype[Symbol.match]` | custom result | `["1"]` | 1.41 |
| `"a".startsWith({ [Symbol.match]: true })` | `false` | TypeError | 1.46 |
| `JSON.parse("[1]", (k, v) => typeof k)` | `k` is a number for indexes | always a string | 1.44 |
| `Promise.allSettled([Promise.reject(1)])` status | `" rejected"` | `"rejected"` | 1.46 |
| `BigInt.asIntN(8, 1)` | `1n` | TypeError | 1.48 |
| `Object.prototype.toString.call(TypeError.prototype)` | `"[object Error]"` | `"[object Object]"` | 1.48 |
| Date setter on an invalid date | may store a value | date left invalid | 1.43 |
| `__defineGetter__` on a frozen object | ignored | TypeError | 1.43 |
| `decodeURI("%EF%BF%BE")` | U+FFFD | U+FFFE | 1.65 |
| `Math.log2(8)` | `2.9999999999999996` | `3` | 1.65 |
| Final sigma in `toLowerCase` after supplementary characters | `σ` | `ς` | 1.65 |
| `String.prototype.trimLeft === trimStart` | `false` | `true` | 1.55 |
| `new F()` with `F.prototype` not an object | `F.prototype` / `Error.prototype` | `Object.prototype` / the error's own prototype | 1.62 |
| `console.log(Infinity, NaN)` | `null null` | `Infinity NaN` | 1.95 |

### Typed arrays

**Byte order is little endian [1.6].** `Context.FEATURE_LITTLE_ENDIAN` is true by default under
`VERSION_ECMASCRIPT`. A `ContextFactory` that overrides `hasFeature` still decides; `DataView` keeps its explicit
`littleEndian` argument.

```js
new Uint8Array(new Uint16Array([1]).buffer)[0];   // before: 0   now: 1
```

If typed array buffers are exchanged with Java code (for example as `byte[]`) that assumes big endian, either
adapt that code or keep the old order by overriding `hasFeature(cx, Context.FEATURE_LITTLE_ENDIAN)`.

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| `Reflect.ownKeys(new Int8Array(2))` | `[]` | `["0", "1"]` | 1.39 |
| `Object.getOwnPropertyDescriptor(ta, "0")` | TypeError | a data descriptor | 1.39 |
| `ta["-0"]`, `ta.NaN = 3; ta.NaN` | read through the prototype / `3` | `undefined` | 1.39, 1.80 |
| `new Int8Array(-1)` | Error "invalid argument" | RangeError | 1.49 |
| `new BigInt64Array([1])` | `[1n]` | TypeError | 1.49 |
| `Float64Array.from([, 1])[0]` | `0` | `NaN` | 1.49 |
| `new Int16Array(2)` after `ArrayBuffer = null` | InternalError | works (intrinsic constructors) | 1.86 |
| `new Int8Array({ length: 2 ** 53 })` | `java.lang.IllegalArgumentException` | RangeError | 1.61 |

### RegExp

**`\p` needs the `u` or `v` flag [1.7], and patterns without `u` follow Annex B [1.13].**

```js
/\p{L}/.test("a");     // before: true (property escape)   now: false (matches "p{L}")
/\p{L}/u.test("a");    // true in both
/\xa/.test("\n");      // before: true   now: false (matches "xa")
```

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| `Object.prototype.toString.call(RegExp.prototype)` | `"[object RegExp]"` | `"[object Object]"` | 1.50 |
| `RegExp.prototype.global` | `false` | `undefined` | 1.50 |
| `new RegExp("").source` | `""` | `"(?:)"` | 1.50 |
| `new RegExp("x{1}{1,}")` | a regexp | SyntaxError | 1.50 |
| Order of `flags` | `gimsyu` | `dgimsuvy` | 1.50 |
| `/a/.compile(/b/, "g")` | recompiled | TypeError | 1.55 |
| `RegExp.lastMatch` read through a subclass | the last match | TypeError | 1.36 |
| Legacy statics (`RegExp.$1`, …) enumerable | `true` | `false` | 1.55 |
| `/(?<=x\d\|y\d)z/.exec("y1z")` | `null` | `["z"]` | 1.63 |
| `flags` getter called on a primitive | `""` | TypeError | 1.85 |

### Java interop and embedding

These are the changes most likely to affect the Java side of an embedding.

| Item | Before | Now | Ref |
|------|--------|-----|-----|
| Reading a global `let`/`const` from Java | property of the global object | read it from the scope (`scope.get("x", scope)`, `ScriptableObject.getProperty(scope, "x")`), not `getGlobalThis()` | 1.3 |
| Running a script with the same top-level `let` twice in one scope | allowed | SyntaxError on the second run | 1.3 |
| A `let`/`const` read from Java before its declaration has run | `undefined` | `UniqueTag.UNINITIALIZED` | 1.2 |
| `Iterator(javaCollection)` | iterates it | TypeError; use `for-of` or `Iterator.from` | 1.64 |
| `Context.getElements`, `f.apply` with a huge array-like | `IllegalArgumentException` | RangeError | 1.61 |
| A custom top scope that is not a `TopLevel` | n/a | global `let`/`const` are defined on that scope, as before | 1.3 |
| Code run at runtime in a function's scope (a legacy `Script` called as a function, a host function evaluating code in its caller's scope) | sees all the locals of a function with closures | sees only the variables a nested function refers to | 1.88 |
| Assigning the name of a function compiled with `Context.compileFunction` (event handlers) | mutable variable, `name = null` stores `this.name` | unchanged (JavaScript 1.x semantics kept) | 1.37 |

Shared scopes keep working: `var x` in a per-request scope that inherits from a shared global scope still sees
the shared `x` **[1.14]**.

### Compiled-mode fixes

These fix bugs of stock Rhino's class compiler (`optimizationLevel` 0 and above) where compiled code gave a
different result than the interpreter. Under `VERSION_ES6`, compiled code keeps the stock bugs.

| Item | Before (compiled) | Now | Ref |
|------|-------------------|-----|-----|
| `function f(n) { if (n === 0) return 0; return 1 + f(n - 1); } f(10)` | `1` | `10` | 1.94 |
| `function rest(...a) { return a; }` called directly as `rest('xy')` | `'xy'` | `['xy']` | 1.92 |
| `function f(x) { return x < 2; } f(1n)` | TypeError | `true` | 1.93 |
| `function mul(x, y) { return x * y; } mul(2n, 3n)` | TypeError | `6n` | 1.90 |
| An exception in a `finally` inlined for a `return` in a `try`/`catch` | `finally` runs twice | runs once | 1.53 |
| A `let` read in a later `switch` clause | `0` or the previous value | ReferenceError | 1.91 |
| `super[k()]++` | key evaluated twice | once, ToNumeric | 1.82 |

## Migration checklist

1. **Pick a language version deliberately.** The fork defaults to `VERSION_ECMASCRIPT`, so an embedding that
   never sets a version gets every change of this page.
   - To keep stock Rhino behavior, call `cx.setLanguageVersion(Context.VERSION_ES6)` in
     `ContextFactory.onContextCreated` (or run the shell with `-version 200`, or put `LANGUAGE_VERSION` `200` in the
     `javax.script` engine scope), and migrate later.
   - To move to ECMAScript 2026, keep the default and continue with steps 6 to 10.
2. **Dependencies.** Change the Maven group id to `org.monflabs.mozilla` and the version to `2026.0`.
3. **Java code.** Replace `org.mozilla.` with `org.monflabs.mozilla.` in imports, `module-info.java`,
   `META-INF/services` files, OSGi manifests and configuration.
4. **Scripts.** Replace `Packages.org.mozilla…` references in scripts with `Packages.org.monflabs.mozilla…`.
5. **Runtime.** Run on Java 21 or later. Android and micro JVMs are not supported.
6. **Compile every script once** under `VERSION_ECMASCRIPT`: the new early errors (1.12, 1.26, 1.32, 1.33) show up
   at compile time, before any code runs.
7. **Look for the common runtime differences:** assignments to `const` (1.1), use before declaration (1.2),
   top-level `let`/`const` redeclared across scripts or re-run in one scope (1.3), `this` in strict functions
   (1.22), array destructuring of non-iterables (1.29), `Iterator(...)` (1.64), `.arity` (1.66).
8. **Check the Java side:** global `let`/`const` read through `getGlobalThis()` (1.3), byte order of typed array
   data shared with Java (1.6), code evaluated in a function's scope at runtime (1.88).
9. **Watch the logs** for `org.monflabs.mozilla.javascript` warnings about scripts falling back to the
   interpreter (2.4), and report the ones caused by a compiler failure.
10. **Run your test suite in both compiled and interpreted mode**, since a few fixes are specific to compiled code.

For anything not covered here, the per-section details are in
[BACKWARD_COMPATIBILITY.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/BACKWARD_COMPATIBILITY.md),
and the full history of the fork in
[CHANGELOG_MONFLABS.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/CHANGELOG_MONFLABS.md).
