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
| 2.1 | `const` in a loop body takes each iteration's value | `VERSION_ES6` and later | plan 1.1a, 2026-10-07 |
| 2.2 | `for (const x of/in …)` initializes `x` | `VERSION_ES6` and later | plan 1.1a, 2026-10-07 |
| 2.3 | `let`/`const` loop heads create a binding per iteration | `VERSION_ES6` and later | plan 1.1b, 2026-10-07 |
| 2.4 | Assignment to a block-level `const` has no effect | `VERSION_ES6` | plan 1.1c, 2026-10-07 |
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
