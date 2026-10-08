# Backward compatibility

This fork of Rhino (`org.monflabs.mozilla`) moves the engine toward ECMAScript 2026 (see
[MONFLABS_PLAN.md](MONFLABS_PLAN.md)). This document lists every change that can make code or an embedding behave
differently from **stock Rhino 1.9.0**, so that upgrading is a deliberate, informed step. It is updated with each
change; [CHANGELOG_MONFLABS.md](CHANGELOG_MONFLABS.md) has the full history.

The rule (see `AGENTS.md`): a spec behavior that can break existing code is only enabled under the language version
`Context.VERSION_ECMASCRIPT` (`cx.setLanguageVersion(Context.VERSION_ECMASCRIPT)`, or `-version 250` in the shell).
The default, `Context.VERSION_ES6`, keeps the previous behavior, except for the bug fixes in section 2, which were
decided to apply to every version.

| # | Change | Applies to | Since |
|---|--------|-----------|-------|
| 1.1 | Assignment to a `const` throws a TypeError | `VERSION_ECMASCRIPT` | plan 1.1c, 2026-10-07 |
| 1.2 | Temporal dead zone: using a `let`/`const` before its declaration throws a ReferenceError | `VERSION_ECMASCRIPT` | plan 1.1d, 2026-10-07 |
| 2.1 | `const` in a loop body takes each iteration's value | `VERSION_ES6` and later | plan 1.1a, 2026-10-07 |
| 2.2 | `for (const x of/in …)` initializes `x` | `VERSION_ES6` and later | plan 1.1a, 2026-10-07 |
| 2.3 | `let`/`const` loop heads create a binding per iteration | `VERSION_ES6` and later | plan 1.1b, 2026-10-07 |
| 2.4 | Assignment to a block-level `const` has no effect | `VERSION_ES6` | plan 1.1c, 2026-10-07 |
| 2.5 | `let x;` in a loop body is `undefined` on each iteration | `VERSION_ES6` and later | plan 1.1d, 2026-10-07 |
| 3.1 | Packages and Maven coordinates renamed | all | 2026-10-07 |
| 3.2 | Java 21 minimum; Android not supported | all | 2026-10-07 |

---

## 1. Changes under `VERSION_ECMASCRIPT` only

Code running under `VERSION_ES6` (the default) is not affected. To keep the previous behavior, do not select
`VERSION_ECMASCRIPT`. Each change is also listed in the Javadoc of `Context.VERSION_ECMASCRIPT`, and every place in
the engine that implements one tests `isLatestEcmaScript()`.

### 1.1 Assignment to a `const` throws a TypeError

```js
const k = 1;
k = 2;       // VERSION_ES6: no effect (TypeError in strict mode)
             // VERSION_ECMASCRIPT: TypeError: Assignment to constant variable "k".
```

- Covers every form of assignment: `=`, compound assignments (`+=`, …), `++`/`--`, destructuring
  (`[k] = [2]`, `({k} = o)`), and a `for-in/of` target (`for (k of a)`), in strict and sloppy code, interpreted and
  compiled.
- **Why:** a `const` creates a strict immutable binding, so changing it is a TypeError whatever the strictness of
  the code (ECMA-262 2026, §9.1.1.1.5 SetMutableBinding, step 2: "If the binding for N in envRec is a strict binding,
  set S to true").
- **Not covered yet:** a `const` declared at the top level of a script, which still lives on the global object
  (it will be with plan 1.1e, global lexical declarations).

### 1.2 Temporal dead zone (TDZ)

```js
function f() {
  g();             // VERSION_ES6: g returns undefined
  let x = 1;       // VERSION_ECMASCRIPT: ReferenceError: Cannot access "x" before initialization.
  function g() { return x; }
}
```

- Reading, writing, `typeof`, `++`/`--` or calling a `let`/`const` binding before its declaration has run throws a
  ReferenceError: textually before the declaration in the same function (`x; let x;`, `let x = x;`,
  `for (let a of a)`), or from a closure, a `with` statement or `eval` code that runs too early. Under
  `VERSION_ES6`, such a read gives `undefined` and a write succeeds.
- **Why:** a lexical binding is created uninitialized and only initialized when its declaration is evaluated
  (ECMA-262 2026, §9.1.1.1.6 GetBindingValue and §9.1.1.1.5 SetMutableBinding, step 3: "If the binding for N in
  envRec has not yet been initialized, throw a ReferenceError").
- **Cost:** none for `VERSION_ES6`. Under `VERSION_ECMASCRIPT`, references that can be proven safe at compile time
  (in the same function, after the declaration) are not checked; others are checked by one comparison.
- **Not covered yet:** top-level `let`/`const` of a script (plan 1.1e); bindings of a destructuring declaration
  (`let [a, b] = …`, plan 1.3); a read that a `switch` case reaches by
  jumping over a declaration in a function without closures; a write from a closure is checked before its
  right-hand side is evaluated, not after.
- **Embedding note:** under `VERSION_ECMASCRIPT`, Java code that reads such a binding from a scope object before its
  declaration has run (`scope.get("x", scope)`) gets the internal marker `UniqueTag.UNINITIALIZED`.

---

## 2. Changes that also apply to `VERSION_ES6`

These fix behavior that contradicted the ECMAScript 2015 specification and every other engine, in code that only
exists since `let`/`const` (decided 2026-10-07: the old results were plain bugs that no code can sensibly rely on).
Code compiled for a version before `VERSION_ES6` (JavaScript 1.x) keeps the old behavior.

### 2.1 `const` in a loop body takes each iteration's value

```js
function f() {
  var r = [];
  for (var i = 0; i < 3; i++) { const c = i; r.push(c); }
  return r.join();
}
f();   // before: "0,0,0" (in functions without closures)   now: "0,1,2"
```

- **Why:** each evaluation of a `const` declaration initializes the binding (ECMA-262 2026, §14.3.1); the old
  behavior was JavaScript 1.x `const`, which ignored every evaluation but the first.

### 2.2 `for (const x of/in …)` initializes `x`

```js
function f() { var s = 0; for (const v of [1, 2, 3]) { s += v; } return s; }
f();   // before: NaN (v stayed undefined in functions without closures)   now: 6
```

- **Why:** the loop binding is initialized with each value (§14.7.5.7 ForIn/OfBodyEvaluation).

### 2.3 `let`/`const` loop heads create a binding per iteration

```js
var fs = [];
for (let i = 0; i < 3; i++) { fs.push(function () { return i; }); }
fs.map(function (f) { return f(); });   // before: [3, 3, 3]   now: [0, 1, 2]
```

- Applies to `for (let …; …; …)`, `for (let/const … in …)` and `for (let/const … of …)`.
- **Why:** §14.7.4.4 CreatePerIterationEnvironment and §14.7.5.7 ForIn/OfBodyEvaluation.
- **Performance:** only loops whose bindings live in a scope object (functions that create closures, and scripts)
  copy the scope once per iteration; other loops are unchanged.

### 2.4 Assignment to a block-level `const` has no effect (`VERSION_ES6`)

```js
function f() { { const k = 1; (function () {}); k = 2; return k; } }
f();   // before: 2 (the assignment succeeded)   now: 1 (no effect; TypeError in strict mode)
```

- Before, a `const` declared in a block of a function with closures (or of a script) was stored as an ordinary
  variable, so assigning to it succeeded; every other `const` ignored the assignment. It is now consistent. Under
  `VERSION_ECMASCRIPT`, the assignment throws (see 1.1).

### 2.5 `let x;` in a loop body is `undefined` on each iteration

```js
function f() {
  var r = [];
  for (var i = 0; i < 2; i++) { let x; if (i == 0) x = 1; r.push(x); }
  return r.join();
}
f();   // before: "1,1" (in functions without closures)   now: "1,"
```

- **Why:** a `let` declaration without an initializer initializes the binding to `undefined` each time it is
  evaluated (§14.3.1). A script's top-level `let x;` is not changed (plan 1.1e).

---

## 3. Platform and embedding

### 3.1 Packages and Maven coordinates renamed

- Java packages `org.mozilla.*` became `org.monflabs.mozilla.*`; Java module names `org.mozilla.rhino*` became
  `org.monflabs.mozilla.rhino*`, and the OSGi bundle symbolic names likewise; the Maven group id `org.mozilla`
  became `org.monflabs.mozilla`. This lets the fork and stock Rhino coexist on a class path or module path.
- **Migration:** replace `org.mozilla.` with `org.monflabs.mozilla.` in imports, `Packages.org.mozilla…` script
  references, `META-INF/services` files and dependency declarations.

### 3.2 Java 21 minimum; Android not supported

- The engine is compiled for Java 21 (stock Rhino 1.9.0: Java 17); the OSGi bundles require JavaSE 21. The minimum
  will move to Java 25 when it becomes available to us (expected early 2027).
- This fork targets Java server applications: Android and micro JVMs are not supported (the Android integration
  tests were removed).
