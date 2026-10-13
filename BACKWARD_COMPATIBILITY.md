# Backward compatibility

This fork of Rhino (`org.monflabs.mozilla`) moves the engine toward ECMAScript 2026 (see
[MONFLABS_PLAN.md](MONFLABS_PLAN.md)). This document lists every change that can make code or an embedding behave
differently from **stock Rhino 1.9.0**, so that upgrading is a deliberate, informed step. It is updated with each
change; [CHANGELOG_MONFLABS.md](CHANGELOG_MONFLABS.md) has the full history.

The target is the language version `Context.VERSION_ECMASCRIPT` (`cx.setLanguageVersion(Context.VERSION_ECMASCRIPT)`,
or `-version 250` in the shell). The plan is to eventually remove the language versions and always behave that way;
until then, a change incompatible with `VERSION_ES6` behavior is either only active under `VERSION_ECMASCRIPT`
(section 1) or applied to every version (section 2), whichever was easier to implement. Everything in sections 1 and
2 is what `VERSION_ES6` code may notice; section 3 lists platform and API changes.

| # | Change | Applies to | Since |
|---|--------|-----------|-------|
| 1.1 | Assignment to a `const` throws a TypeError | `VERSION_ECMASCRIPT` | plan 1.1c, 2026-10-07 |
| 1.2 | Temporal dead zone: using a `let`/`const` before its declaration throws a ReferenceError | `VERSION_ECMASCRIPT` | plan 1.1d, 2026-10-07 |
| 1.3 | Top-level `let`/`const` are not properties of the global object; redeclaring one in another script is a SyntaxError | `VERSION_ECMASCRIPT` | plan 1.1e, 2026-10-07 |
| 2.1 | `const` in a loop body takes each iteration's value | `VERSION_ES6` and later | plan 1.1a, 2026-10-07 |
| 2.2 | `for (const x of/in …)` initializes `x` | `VERSION_ES6` and later | plan 1.1a, 2026-10-07 |
| 2.3 | `let`/`const` loop heads create a binding per iteration | `VERSION_ES6` and later | plan 1.1b, 2026-10-07 |
| 2.4 | Assignment to a block-level `const` has no effect | `VERSION_ES6` | plan 1.1c, 2026-10-07 |
| 2.5 | `let x;` in a loop body is `undefined` on each iteration | `VERSION_ES6` and later | plan 1.1d, 2026-10-07 |
| 2.6 | `this` of strict functions: `typeof this` is `"undefined"` without a receiver; `call`/`apply` pass primitives unboxed | `VERSION_ES6` and later | plan 1.2a, 2026-10-08 |
| 2.7 | `caller` of strict functions and built-ins throws a TypeError; non-strict functions' `caller` is `undefined` | `VERSION_ES6` and later | plan 1.2a, 2026-10-08 |
| 2.8 | Bound functions: `name` from the target's `name` property; `bind()` without a receiver gives a strict target `this === undefined` | `VERSION_ES6` and later | plan 1.2a, 2026-10-08 |
| 2.9 | `arguments`: redefining an index follows the spec (attributes, mapping); rest parameters give an unmapped `arguments` | `VERSION_ES6` and later | plan 1.2b, 2026-10-08 |
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
- A `const` declared at the top level of a script is covered too, since it is a global lexical declaration (1.3).

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
- **Not covered yet:** bindings of a destructuring declaration
  (`let [a, b] = …`, plan 1.3); a read that a `switch` case reaches by
  jumping over a declaration in a function without closures; a write from a closure is checked before its
  right-hand side is evaluated, not after.
- **Embedding note:** under `VERSION_ECMASCRIPT`, Java code that reads such a binding from a scope object before its
  declaration has run (`scope.get("x", scope)`) gets the internal marker `UniqueTag.UNINITIALIZED`.

### 1.2b Temporal dead zone of parameters

```js
function f(x = y, y) { return x; }
f();          // VERSION_ES6: undefined   VERSION_ECMASCRIPT: ReferenceError
function g(x = x) {}
g();          // VERSION_ES6: fine        VERSION_ECMASCRIPT: ReferenceError
```

- A default value may not read its own parameter or a later one: they are not initialized yet. A closure created in
  a default value and called later is not affected.
- **Why:** ECMA-262 2026, §10.2.11 FunctionDeclarationInstantiation step 26 (IteratorBindingInitialization of the
  formals, in order).

### 1.3 Global lexical declarations

```js
// script 1                         // script 2, same scope
let x = 1;                          let x = 2;   // VERSION_ES6: fine (x becomes 2)
const c = 2;                                     // VERSION_ECMASCRIPT: SyntaxError: Identifier "x" has
"x" in globalThis;  // ES6: true                 //   already been declared.
                    // ECMASCRIPT: false
```

- The top-level `let`/`const` of a script are bindings of the global environment's declarative record (the
  `TopLevel` scope), shared by all the scripts run in that scope, and not properties of the global object:
  `globalThis.x`, `"x" in globalThis` and `Object.keys(globalThis)` do not see them; `x` does.
- Running a script that declares a `let`/`const` whose name is already a `let`/`const` of an earlier script, a
  `var` or function of an earlier script, or a non-configurable global (`undefined`, `NaN`, `Infinity`) throws a
  SyntaxError before the script runs; so does a `var` or function whose name is a global `let`/`const`.
- Assignment to a global `const` throws (1.1) and the temporal dead zone applies to global bindings (1.2).
- **Why:** ECMA-262 2026, §9.1.1.4 Global Environment Records and §16.1.7 GlobalDeclarationInstantiation.
- **Embedding notes:** Java code reads such a binding from the scope (`scope.get("x", scope)`,
  `ScriptableObject.getProperty(scope, "x")`), not from `scope.getGlobalThis()`. An application that runs the same
  script twice in one scope (a REPL re-running `let x = …`) gets a SyntaxError the second time. `let`/`const` in
  `eval` code are not affected (as before, they become variables of the eval's caller; plan 1.2g). With a custom top
  scope that is not a `TopLevel`, the declarations are made on that scope as before.
- **Not covered yet:** if a script declares both `var`s and a conflicting `let`, its `var`s may have been created on
  the global object before the SyntaxError.

### 1.4 Declarations of eval code

```js
let x = 1;
(0, eval)("var x");                 // VERSION_ES6: fine   VERSION_ECMASCRIPT: SyntaxError
{ let y; { eval("var y"); } }       // VERSION_ES6: fine   VERSION_ECMASCRIPT: SyntaxError
function f() { let z; eval("var z"); }   // VERSION_ES6: fine   VERSION_ECMASCRIPT: SyntaxError at the call
```

- Sloppy eval code may not declare a `var` or function whose name is a `let`/`const` of a block between the eval
  and its variable scope, of the top level of the calling function, or (when its variable scope is the global one)
  a global lexical declaration. A catch parameter or a `with` object does not count.
- When eval code declares global functions and vars, it checks them all before creating any: a function whose name
  is a non-configurable global that is read-only or non-enumerable (`NaN`, `undefined`, …), or a new name on a
  non-extensible global object, throws a TypeError and declares nothing (before, the functions preceding it were
  created). A function replacing a configurable global property redefines it as writable, enumerable and
  configurable (before, a read-only one was kept, or threw in strict mode).
- **Why:** ECMA-262 2026, §19.2.1.3 EvalDeclarationInstantiation steps 3 and 10–11, §9.1.1.4.16
  CanDeclareGlobalFunction and §9.1.1.4.18 CreateGlobalFunctionBinding, §10.2.11 FunctionDeclarationInstantiation
  step 30 (the separate lexical environment of a non-strict function).

### 1.5 Lexical declarations of eval code

```js
eval("let a = 1; const b = 2; class C {}");
typeof a;                           // VERSION_ES6: "number"   VERSION_ECMASCRIPT: "undefined"
eval("typeof c; let c;");           // VERSION_ES6: "undefined"   VERSION_ECMASCRIPT: ReferenceError
```

- The top-level `let`, `const` and `class` declarations of eval code (direct or indirect) belong to the eval code:
  they are in a block of their own, in their temporal dead zone until declared, and gone after the eval; its `var`s
  and functions still go to the caller's variable scope. Not done when the eval code also declares functions at its
  top level (they would not see the block), which keep the previous behavior.
- **Why:** ECMA-262 2026, §19.2.1.1 PerformEval step 16 (a new declarative environment for the lexical declarations).

### 1.6 Byte order of typed arrays

```js
new Uint8Array(new Uint16Array([1]).buffer)[0];   // VERSION_ES6: 0   VERSION_ECMASCRIPT: 1
```

- `Context.FEATURE_LITTLE_ENDIAN` is true by default under `VERSION_ECMASCRIPT`: the elements of typed arrays are
  stored little endian, the byte order of the platforms ECMAScript code is written for (and that test262 expects).
  A `ContextFactory` that overrides `hasFeature` still decides; `DataView` keeps its explicit `littleEndian`
  argument.
- **Why:** ECMA-262 2026, §25.1.3.17 RawBytesToNumeric and §25.1.3.18 NumericToRawBytes use the platform's
  byte order ([[LittleEndian]] is implementation-defined).

### 1.7 `\p` in a regexp without the `u` flag

```js
/\p{L}/.test("a");        // VERSION_ES6: true (property escape)   VERSION_ECMASCRIPT: false (matches "p{L}")
```

- Without the `u` (or `v`) flag, `\p` and `\P` are identity escapes that match the letters `p` and `P`; Rhino
  read them as Unicode property escapes in every mode.
- **Why:** ECMA-262 2026, Annex B.1.2 (IdentityEscape) and §22.2.1 (UnicodePropertyValueExpression only with
  [+UnicodeMode]).

### 1.8 Completion values of statements

```js
eval("1; if (true) {}");                   // VERSION_ES6: 1   VERSION_ECMASCRIPT: undefined
eval("6; try { 7; } finally { 8; }");      // VERSION_ES6: 8   VERSION_ECMASCRIPT: 7
```

- The value of a script or of `eval` code: `if`, loops, `switch`, `try` and `with` statements give `undefined`
  when their body produces no value, instead of keeping the previous statement's value; a `finally` block that
  completes normally no longer replaces the value. Function code is not affected.
- **Why:** ECMA-262 2026, §14 (UpdateEmpty in the evaluation of each statement), the ES2015 completion reform.

### 1.9 Destructuring in a `for-in` head

```js
for (var [k, v] in { ab: 1 }) print(k, v);   // VERSION_ES6: ab 1   VERSION_ECMASCRIPT: a b
```

- The pattern destructures the key, a string, like any other value; the JavaScript 1.7 `[key, value]` reading is
  kept for `VERSION_ES6` (`for each` and `for-of` are not affected).
- **Why:** ECMA-262 2026, §14.7.5 (ForIn/OfBodyEvaluation with a ForDeclaration or AssignmentPattern).

### 1.10 `let` as an identifier

```js
var let = 1;              // VERSION_ES6: SyntaxError   VERSION_ECMASCRIPT: a variable named let
if (a) let
x = 1;                    // VERSION_ES6: SyntaxError (let declaration)   VERSION_ECMASCRIPT: let; x = 1;
```

- In sloppy code, `let` is a keyword only where it starts a lexical declaration; elsewhere it is an identifier,
  and in a single-statement position (the body of `if`, loops, `with`, a label) a line terminator after `let` ends
  the statement. An escaped `l\u0065t` is always an identifier.
- **Why:** ECMA-262 2026, §14.3.1 (LexicalDeclaration), §14.5 (ExpressionStatement lookahead), §13.1
  (`let` is not a reserved word).

### 1.11 Global declaration instantiation

```js
var NaN;                  // VERSION_ES6: TypeError (redeclaration of const)   VERSION_ECMASCRIPT: allowed
// a script `var x; let undefined;`: VERSION_ES6 creates x then fails; VERSION_ECMASCRIPT fails before creating x
```

- The declarations of a script are validated before any of them is created; a `var` may name an existing
  non-writable global (`NaN`); a global function redefines a configurable property of the same name.
- **Why:** ECMA-262 2026, §16.1.7 (GlobalDeclarationInstantiation), §9.1.1.4.15–18.

### 1.12 Early errors

```js
f() &&= 1;                 // VERSION_ES6: ReferenceError at run time   VERSION_ECMASCRIPT: SyntaxError
"use strict"; delete x;    // VERSION_ES6: false or true                VERSION_ECMASCRIPT: SyntaxError
const x;                   // VERSION_ES6: x is undefined               VERSION_ECMASCRIPT: SyntaxError
l: { for (;;) continue l; } // VERSION_ES6: continues the loop          VERSION_ECMASCRIPT: SyntaxError
var r = 'g'; (function () { var r = 'l'; return eval?.('r'); })(); // VERSION_ES6: 'l'   VERSION_ECMASCRIPT: 'g'
```

- Code that the specification rejects with an early error no longer compiles: invalid assignment targets, strict
  mode restrictions (`delete` of an identifier, `eval++`, octal escapes), escaped keywords, a numeric literal
  directly followed by an identifier, `08n`, `const` without an initializer, declarations as the body of a
  label, `if`, `with` or a loop, a catch parameter redeclared by a lexical declaration, `continue` to a label
  that is not a loop, a tagged template in an optional chain. The full list is in CHANGELOG_MONFLABS.md (plan
  1.10e).
- `eval?.()` and a tagged template on `eval` are indirect evals.
- Also (plan 1.10f): `let`/`const` redeclaring a parameter (`function f(p) { let p; }`), `yield` in the
  parameters of a generator or in a non-generator nested in one (strict code), `new.target` in an arrow function
  that is not inside a function.
- Also (plan 1.10g): an object or array literal containing a CoverInitializedName (`{ x = 1 }`) that is not an
  assignment pattern (`({ x = 1 }).y`, `[{ x = 1 }] + 1`).
- **Why:** ECMA-262 2026, the "Static Semantics: Early Errors" of §13–§14, §12.9.3 (numeric literals), §13.3.6.1
  (direct eval is a CallExpression only).

### 1.13 Annex B: RegExp patterns, calls as assignment targets

```js
/\xa/.test('\n');              // VERSION_ES6: true (\xa is \x0a)   VERSION_ECMASCRIPT: false (matches "xa")
/\0022/.test('\x02');          // VERSION_ES6: true                  VERSION_ECMASCRIPT: false (\x02 then 2)
f() = 1;                       // VERSION_ES6: ReferenceError, f not called   VERSION_ECMASCRIPT: f called first
```

- RegExp patterns without the `u` flag follow the Annex B.1.2 grammar for incomplete hex escapes, class control
  letters and legacy octal escapes; a call as an assignment target is evaluated before the ReferenceError; Annex
  B.3.3 does not hoist a block function named by a destructured catch parameter; `for (var a = 0 in o)` is a
  SyntaxError in strict code.
- **Why:** ECMA-262 2026, B.1.2 (RegExp patterns), §13.15.1 (web-compat AssignmentTargetType), B.3.3, B.3.5.

### 1.14 `__proto__` in object literals, global vars shadowing Object.prototype

```js
Object.getPrototypeOf({ ['__proto__']: p }) === p;   // VERSION_ES6: true   VERSION_ECMASCRIPT: false (own property)
var toString; typeof toString;                       // VERSION_ES6: "function"   VERSION_ECMASCRIPT: "undefined"
```

- Only the non-computed, non-shorthand `__proto__: value` form sets the prototype. A global `var` is created
  unless the global object has the property itself or through a shared global it inherits from (Rhino's shared
  scopes keep working: `var x` in a per-request scope still sees the shared `x`); properties of Object.prototype
  no longer prevent the binding.
- **Why:** ECMA-262 2026, B.3.1 (`__proto__` property names), §9.1.1.4.17 (CreateGlobalVarBinding).

### 1.15 `for (async of ...)`

```js
for (async of []);   // VERSION_ES6: a for-of loop assigning to `async`   VERSION_ECMASCRIPT: SyntaxError
```

- The target of a for-of loop cannot be an unescaped, unparenthesized `async` (it would be ambiguous with an async
  arrow function); `for ((async) of [])`, `for (\u0061sync of [])` and `for await (async of [])` stay valid.
- **Why:** ECMA-262 2026, §14.7.5 (for-in, for-of and for-await statements: `[lookahead ≠ async of]`).

### 1.16 ZWNJ and ZWJ in identifiers

```js
var a‌b = 1; typeof ab;   // (a raw U+200C between a and b) VERSION_ES6: "number"   VERSION_ECMASCRIPT: "undefined"
```

- A raw ZERO WIDTH NON-JOINER or ZERO WIDTH JOINER in an identifier is one of its characters, as its `\u200C` or
  `\u200D` escape is; before, the lexer stripped it like any other format character (an ES3 rule), so `a‌b` was
  the identifier `ab`. The other format characters are still stripped.
- **Why:** ECMA-262 2026, §12.7 (Names and Keywords: `IdentifierPartChar` includes ZWNJ and ZWJ).

---

## 2. Changes that also apply to `VERSION_ES6`

These fix behavior that contradicted the ECMAScript specification and every other engine; they apply to every
version from `VERSION_ES6`. Code compiled for a version before `VERSION_ES6` (JavaScript 1.x) keeps the old behavior
unless stated otherwise.

### 2.1 `const` in a loop body takes each iteration's value

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

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

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
function f() { var s = 0; for (const v of [1, 2, 3]) { s += v; } return s; }
f();   // before: NaN (v stayed undefined in functions without closures)   now: 6
```

- **Why:** the loop binding is initialized with each value (§14.7.5.7 ForIn/OfBodyEvaluation).

### 2.3 `let`/`const` loop heads create a binding per iteration

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

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

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
function f() { { const k = 1; (function () {}); k = 2; return k; } }
f();   // before: 2 (the assignment succeeded)   now: 1 (no effect; TypeError in strict mode)
```

- Before, a `const` declared in a block of a function with closures (or of a script) was stored as an ordinary
  variable, so assigning to it succeeded; every other `const` ignored the assignment. It is now consistent. Under
  `VERSION_ECMASCRIPT`, the assignment throws (see 1.1).

### 2.5 `let x;` in a loop body is `undefined` on each iteration

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
function f() {
  var r = [];
  for (var i = 0; i < 2; i++) { let x; if (i == 0) x = 1; r.push(x); }
  return r.join();
}
f();   // before: "1,1" (in functions without closures)   now: "1,"
```

- **Why:** a `let` declaration without an initializer initializes the binding to `undefined` each time it is
  evaluated (§14.3.1). A script's top-level `let x;` is unchanged under `VERSION_ES6`.

### 2.6 `this` of strict functions

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
"use strict";
function f() { return typeof this; }
f();            // before: "object"   now: "undefined" (this === undefined was already true)
f.call(1);      // before: "object"   now: "number" (call/apply no longer box a primitive this)
```

- **Why:** in strict code, `this` is not coerced (ECMA-262 2026, §10.2.1.2 OrdinaryCallBindThis). A non-strict
  function still receives an object. **Not covered yet:** a strict getter or method called on a primitive (`(5).x`,
  `"s".m()`) still receives the wrapper object; so does a strict function bound to a primitive with `bind`.

### 2.7 `caller` of strict functions throws

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
(function () { "use strict"; function s() {} s.caller; })();   // before: undefined   now: TypeError
Math.max.caller;                                                // before: undefined   now: TypeError
```

- **Why:** `Function.prototype.caller` is a throwing accessor and strict functions have no own `caller` (§10.2.4
  AddRestrictedFunctionProperties, §17.1 Forbidden Extensions). Non-strict functions still answer `undefined`
  (their caller is not tracked). Bound functions no longer have an own throwing `caller` property (the prototype's applies).
- The `arguments` accessor of `Function.prototype` follows the same rule (plan 1.2c): reading or writing
  `f.arguments` is a TypeError when `f` is strict or built-in (before: `null` unless the strict function was running).

### 2.8 Bound functions

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var t = Object.defineProperty(function () {}, "name", { value: "target" });
t.bind().name;                                   // before: "bound "   now: "bound target"
(function () { "use strict"; return function () { return this; }.bind()(); })();   // before: null   now: undefined
```

- **Why:** §20.2.3.2 Function.prototype.bind reads the target's `name` property when binding, and a bound `this` of
  `undefined` stays `undefined` for a strict target.

### 2.9 The `arguments` object

*Since plan G2 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
function f(x) { Object.defineProperty(arguments, "0", { configurable: false }); x = 7; return arguments[0]; }
f(1);   // before: the property became non-writable and non-enumerable (still 7)   now: 7, and writable and enumerable
function g(x, ...rest) { x = 2; return arguments[0]; }
g(1);   // before: 2   now: 1 (a rest parameter makes arguments unmapped)
```

- `Object.defineProperty` on an index keeps the attributes it does not mention; making it non-writable or an
  accessor, or deleting it, ends its link with the parameter (before, some of these threw "Cannot change the value
  of attribute" or kept the link). Functions with a rest parameter, like those with default values, get an unmapped
  `arguments` object.
- A destructured parameter, like a default value or a rest parameter, makes `arguments` unmapped.
- **Why:** ECMA-262 2026, §10.4.4 Arguments Exotic Objects and §10.2.11 FunctionDeclarationInstantiation step 18.

### 2.10 Early errors of parameter lists

*Since plan G3 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
function f(a = 0, a) {}                 // before: accepted   now: SyntaxError (duplicate in a non-simple list)
function g(a, a) { "use strict"; }      // before: accepted   now: SyntaxError (the body makes it strict)
({ m(a, a) {} });                       // before: accepted   now: SyntaxError (methods)
function eval() { "use strict"; }       // before: accepted   now: SyntaxError
function h([a]) { "use strict"; }       // before: accepted   now: SyntaxError ("use strict" with a pattern)
new Function("'use strict'; eval = 1"); // before: InternalError   now: SyntaxError
```

- A `"use strict"` directive applies to the function's own name and parameter list, which are checked once the body
  is parsed. Duplicate parameter names stay allowed only in a non-strict function (or generator) with a simple
  parameter list.
- **Why:** ECMA-262 2026, §15.2.1 (FunctionDeclaration early errors), §15.4.1 (methods: UniqueFormalParameters) and
  §13.15.1 (strict assignment to `eval`/`arguments`).

### 2.11 `Function.prototype.toString`

*Since plan G3 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
({ get f() {} }).__lookupGetter__("f").toString();  // before: "f() {}"          now: "get f() {}"
({ *g() {} }).g.toString();                         // before: "() {}"           now: "*g() {}"
String(Function("a", "return a"));     // before: "function anonymous(a) {return a\n}"
                                       // now:    "function anonymous(a\n) {\nreturn a\n}"
Function.prototype.toString.call(new Proxy(function () {}, {}));   // before: TypeError   now: "function () { [native code] }"
Object.getOwnPropertySymbols(Map.prototype).length;               // before: 3 (an internal "[Symbol.getSize]")   now: 2
```

- The source text of a method, getter or setter starts at its key, including `get`, `set`, `*` and computed keys.
- The `Function` and `GeneratorFunction` constructors build their source as the spec does: a line feed ends the
  parameters, and the body sits between two line feeds. Line numbers reported for errors in such a function's body
  are 2 higher than before (the body starts on line 3 of the generated source). The generator prefix is `function*`.
- A callable proxy gives a NativeFunction string. A built-in whose name is not an identifier (`RegExp`'s `$*`, …)
  leaves the name out of its source text.
- `Map.prototype` and `Set.prototype` no longer have an own, internal `[Symbol.getSize]` symbol property.
- **Why:** ECMA-262 2026, §20.2.3.5 Function.prototype.toString (source text of a MethodDefinition, NativeFunction
  syntax) and §20.2.1.1.1 CreateDynamicFunction; §24.1.3 (the properties of `Map.prototype`).

### 2.12 Eval code

*Since plan G3 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
function f(a = eval("var a = 1")) {}
f();                                 // before: fine   now: SyntaxError (also "var arguments")
"use strict";
(0, eval)("var static = 1");         // before: SyntaxError (strict)   now: fine, a global var
```

- A sloppy direct eval in a parameter list may not declare a `var` or function named like a parameter or
  `arguments` (not `arguments` for an arrow function, which has none): they share the parameters' environment. This
  now applies to arrow functions too.
- An indirect eval is never strict mode code, even when called from strict code (before, it inherited the caller's
  strictness, and its `var`s did not reach the global object).
- **Why:** ECMA-262 2026, §10.2.11 FunctionDeclarationInstantiation step 20, §19.2.1.3 EvalDeclarationInstantiation
  step 3 and §19.2.1.1 PerformEval steps 12–13.

### 2.13 Array destructuring follows the iterator protocol

*Since plan G3 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var [a] = { 0: 1, length: 1 };    // before: a is 1         now: TypeError (not iterable)
var [b, , c] = new Set([1, 2, 3]); // before: undefined      now: b is 1, c is 3
var x = 5; var [x = 1] = [];       // before: x stays 5      now: x is 1
function f([p, , q]) {} f([1, 2, 3]);  // before: q was 2    now: q is 3
```

- Array patterns (declarations, assignments, parameters, `catch`, `for` heads) get an iterator from the value,
  step it once per element or hole, collect the rest for a rest element, and close it (`return()`) when the
  pattern is done before the iterator. A value that is not iterable is a TypeError. Plain arrays are still read by
  index when that is not observable (the original `Array.prototype[Symbol.iterator]` and
  `%ArrayIteratorPrototype%.next`).
- A default value applies when the element is `undefined`, whatever the target held before.
- Versions before `VERSION_ES6` keep reading by index.
- The array iterator (`values()`, `for-of`, spread) reads a hole through the prototype chain, like `a[i]` (before,
  it gave `undefined` even when `Array.prototype` had that index).
- **Why:** ECMA-262 2026, §8.6.2 IteratorBindingInitialization and §13.15.5.5 IteratorDestructuringAssignmentEvaluation.
- When a default value or a target throws (or a generator is returned from inside the pattern), the iterator is
  closed too; an error of its `return()` does not replace the error being thrown.

### 2.14 Object patterns and default names

*Since plan G3 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var {} = null;                       // before: fine           now: TypeError
var [f = function () {}] = [];       // before: f.name is ""   now: "f"
```

- An object pattern (from `VERSION_ES6`) throws a TypeError when the value is `null` or `undefined`, also when it
  is empty or reads no property.
- An anonymous function, generator or arrow function given as the default value of a binding in a pattern is named
  after that binding, as `var f = function () {}` already was.
- In assignment patterns, a property target (`o.p`, `o[k]`) is evaluated before the value it receives is read (the
  iterator stepped, the source property read), and computed keys are evaluated in order, each right before its
  property (before, all computed keys were evaluated first). `[o.p = 1] = …` and `{ a: o.p = 1 } = …` are accepted
  (before: SyntaxError).
- **Why:** ECMA-262 2026, §8.6.2 BindingInitialization (RequireObjectCoercible), §8.6.3 KeyedBindingInitialization
  (NamedEvaluation of the initializer) and §13.15.5 DestructuringAssignmentEvaluation (order of evaluation).

### 2.15 `for-of` closes its iterator

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
for (var x of iterable) break;   // before: iterable's iterator was left open   now: its return() is called
```

- A `for-of` loop left before its iterator is done (`break`, `continue` to an outer loop, `return`, a throw, a
  generator's `return()`) calls the iterator's `return()`. After a jump, an error of `return()` (or a result that
  is not an object) propagates; after a throw, the throw wins. `next` is read once, when the loop starts, and a
  result of `next()` that is not an object is a TypeError (before, it was converted to an object).
- **Why:** ECMA-262 2026, §14.7.5.7 ForIn/OfBodyEvaluation and §7.4.11 IteratorClose.

### 2.16 Early errors of object literals and functions

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
({ get a(x) {} });                 // before: accepted   now: SyntaxError (a getter has no parameters)
({ set a() {} });                  // before: accepted   now: SyntaxError (a setter has exactly one)
function f(...rest = []) {}        // before: accepted   now: SyntaxError
({ __proto__: a, __proto__: b });  // before: accepted   now: SyntaxError (still valid as a pattern)
({ g\u0065t m() {} });             // before: a getter   now: SyntaxError
"use strict"; public = 1;          // before: ReferenceError at runtime   now: SyntaxError
```

- From `VERSION_ES6`. A `"use strict"` directive now also applies to the token right after it, which was read before
  the directive took effect; words reserved in strict code (`public`, `static`, `implements`, …) are rejected there
  like everywhere else in strict code.
- `in` is allowed in a computed property name inside a `for` head (`for (o = { ['x' in y]: 1 }; ; )`).
- Only an anonymous function written as a property value (`{ k: function () {} }`, also parenthesized) is named after
  its key; before, any anonymous function used as a value was renamed, e.g. `var f = (0, function () {});
  ({ x: f }); f.name` gave `"x"`, now `""`.
- **Why:** ECMA-262 2026, §15.4.1 (MethodDefinition early errors), §15.1.1 (FormalParameters), §B.3.1 (duplicate
  `__proto__`), §12.7.2 (escaped keywords), §11.2.2 (directive prologues).

### 2.17 `yield`

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var yield = 1;                               // before: SyntaxError   now: fine (non-strict code)
function f() { yield 1; }                    // before: a JavaScript 1.7 generator   now: SyntaxError
function* g() { var a = [yield 1, 2]; }      // before: a was [x]   now: [x, 2]
function* h() { yield\n* 1 }                // before: yield* 1   now: SyntaxError
```

- From `VERSION_ES6`: outside generator functions, in non-strict code, `yield` is an identifier; a function that is
  not a `function*` is no longer made a (legacy) generator by a `yield` (versions 1.7 and 1.8 keep them). The
  operand of `yield` is an AssignmentExpression, so a comma after it ends it; `yield` may be an argument
  (`f(yield)`) or follow a comma. `yield*` must be on one line. The `prototype` of a generator function has no
  `constructor`.
- A script with a generator whose `yield` the class compiler cannot handle (inside a call or an object literal) is
  run by the interpreter instead (`Context.compileImpl`).
- The destructured parameters of a generator are bound when it is called, like its default values already were: a
  pattern that cannot be destructured throws at the call (before, at the first `next()`). Their iterators are not
  closed when a default or a target throws (the prologue of a compiled generator holds no `try`).
- **Why:** ECMA-262 2026, §15.5 (Generator Function Definitions), §13.1.1 (`yield` as an IdentifierReference),
  §10.2.11 FunctionDeclarationInstantiation.

### 2.18 Names of functions and classes in default values

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
function f(g = function () {}) { return g.name; }   // before: ""   now: "g"
var [h = () => 1] = []; h.name;                      // before: ""   now: "h"
```

- From `VERSION_ES6`: an anonymous function, arrow function, generator or class that is the default value of a
  parameter or of a destructuring target is named after the binding, like one assigned to a variable already was.
- **Why:** ECMA-262 2026, §8.6.3 (IsAnonymousFunctionDefinition, NamedEvaluation in SingleNameBinding and
  AssignmentElement initializers).

### 2.19 Getters and setters are not constructors

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var d = Object.getOwnPropertyDescriptor({ get a() { return 1; } }, "a");
"prototype" in d.get;   // before: true   now: false
new d.get();            // before: an object   now: TypeError
```

- From `VERSION_ES6`: the getters and setters of object literals (and classes) are method definitions: they have
  no `prototype` property and are not constructors, like the methods of an object literal already were.
- **Why:** ECMA-262 2026, §15.4.4 (MethodDefinition: DefineMethodProperty without MakeConstructor).

### 2.20 Legacy static properties of RegExp read through another object

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var F = Object.setPrototypeOf(function () {}, RegExp);
F.lastMatch;   // before: the last match   now: TypeError
```

- From `VERSION_ES6`: the legacy static properties `RegExp.input` (`$_`), `lastMatch` (`$&`), `lastParen` (`$+`),
  `leftContext`, `rightContext` and `$1`–`$9` are read (and `input` written) only on `RegExp` itself, not through
  an object inheriting from it, such as a class extending `RegExp`. `RegExp.multiline` (`$*`) is not affected.
- **Why:** ECMA-262 Annex B proposal "Legacy RegExp features" (GetLegacyRegExpStaticProperty), tested by test262.

### 2.21 The name of a function expression is immutable

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var f = function g() { g = 1; return typeof g; };
f();   // before: "number"   now: "function" (in strict code: TypeError)
var h = function k() { return function () { k = null; }; };   // JavaScript 1.x: this.k = null
```

- From `VERSION_ES6`: inside a named function expression, assigning its name (directly, from a nested function or
  from eval code) has no effect, and throws a TypeError in strict code. A parameter or a declaration of the same name
  in the function still makes it an ordinary variable.
- From `VERSION_ES6`: `name = null` where `name` is the name of an enclosing function is an ordinary assignment
  (JavaScript 1.x rewrote it as `this.name = null`).
- A function compiled by `Context.compileFunction` (e.g. an event handler of an embedding) keeps both JavaScript 1.x
  behaviors: its name is a mutable variable, and `name = null` stores `this.name`.
- **Why:** ECMA-262 2026, §15.2.5 InstantiateOrdinaryFunctionExpression (CreateImmutableBinding of the name).

### 2.22 `Object.defineProperty` on a non-configurable property

*Since plan G4 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var o = Object.defineProperty({}, "x", { value: 1, writable: true, enumerable: true });
Object.defineProperty(o, "x", { value: 2 });   // before: TypeError   now: o.x is 2
```

- All versions: redefining a non-configurable property with a descriptor that does not mention `enumerable` no
  longer throws "Cannot change the enumerable attribute".
- **Why:** ECMA-262 2026, §10.1.6.3 ValidateAndApplyPropertyDescriptor step 5.b (compares [[Enumerable]] only when
  the descriptor has one).

### 2.23 Typed arrays as exotic objects

*Since plan G5 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var ta = new Int8Array(2);
Reflect.ownKeys(ta);                          // before: []   now: ["0", "1"]
Object.getOwnPropertyDescriptor(ta, "0");     // before: TypeError   now: {value: 0, writable, enumerable, configurable}
"use strict"; delete ta[0];                   // before: false   now: TypeError
Object.defineProperty(ta, "0", {configurable: false});   // before: fine (no effect)   now: TypeError
ta["-0"];                                     // before: from the prototype   now: undefined
Object.create(ta)[0] = 1;                     // before: set ta[0]   now: an own property of the new object
```

- All versions: the elements of a typed array behave as specified for its internal methods: canonical numeric keys
  that are not valid indexes are absent without consulting the prototype chain; the elements are listed by
  `Reflect.ownKeys` and have descriptors; they cannot be deleted, made non-configurable, non-enumerable, read-only or
  accessors.
- **Why:** ECMA-262 2026, §10.4.5 TypedArray Exotic Objects.

### 2.24 Reflect.set and proxies

*Since plan G5 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
Reflect.set(Object.freeze({x: 1}), "x", 2);       // before: true   now: false
Reflect.set({}, "x", 1, "receiver");              // before: TypeError   now: false
var p = new Proxy({}, { set() { return false; } });
(function () { "use strict"; p.x = 1; })();        // before: fine   now: TypeError
var q = new Proxy({}, { getOwnPropertyDescriptor() { return { value: 1, configurable: false }; } });
Object.getOwnPropertyDescriptor(q, "x");          // before: a descriptor   now: TypeError (invariant)
```

- All versions: `Reflect.set` returns false when the assignment fails, and accepts a receiver that is not an object.
  Proxies check the invariants of their `set` and `getOwnPropertyDescriptor` traps; a failed assignment through a
  proxy throws in strict code; a proxy in a prototype chain handles a lookup itself (its `getPrototypeOf` trap is not
  called by `in` or an assignment).
- Likewise for deleting, defining and changing the prototype: `Reflect.deleteProperty`, `Reflect.defineProperty`
  and `Reflect.setPrototypeOf` return the result of the proxy's trap (or target), `delete` (strict code),
  `Object.defineProperty` and `Object.setPrototypeOf` throw when it is false, and invariant violations of these
  traps throw a TypeError (before, `Reflect.defineProperty` returned false for any error). A proxy without a `get`
  trap calls the target's getters with the proxy as `this` (before, the target).
- **Why:** ECMA-262 2026, §10.1.9 [[Set]], §10.5 (proxy internal methods), §28.1 (Reflect).

### 2.25 String methods and the symbols of primitives

*Since plan G5 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
Number.prototype[Symbol.match] = function () { return "custom"; };
"a1b".match(1);          // before: "custom"   now: ["1"] (1 is converted to the string "1")
```

- All versions: `String.prototype.match`, `matchAll`, `replace`, `replaceAll`, `search` and `split` use the
  `Symbol.match`/`matchAll`/`replace`/`search`/`split` method of their argument only when it is an object.
- **Why:** ECMA-262 2025 and later, §22.1.3 (the "If regexp is an Object" steps).

### 2.26 Array methods on frozen objects

*Since plan G5 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
Object.freeze([1]).push(2);                    // before: 2 (nothing changed)   now: TypeError
Array.prototype.pop.call("abc");               // before: "c"   now: TypeError (length is read-only)
```

- From `VERSION_ES6`: the Array methods that write (`push`, `pop`, `shift`, `unshift`, `splice`, `reverse`,
  `fill`, `copyWithin`, …) throw a TypeError when an element or the length cannot be written or deleted.
- **Why:** ECMA-262 2026, §23.1.3 (the methods use Set(O, P, V, true) and DeletePropertyOrThrow).

### 2.27 Object.prototype residuals and Date setters

*Since plan G5 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
var d = new Date(NaN); d.setDate({ valueOf() { d.setTime(0); return 1; } });
d.getTime();                                       // before: NaN   now: 0 (an invalid date is left unchanged)
Object.prototype.__defineGetter__.call(Object.freeze({}), "x", f);   // before: ignored   now: TypeError
Object.prototype.toString.call(new Proxy([], {}));                  // before: "[object Object]"   now: "[object Array]"
```

- All versions: a Date setter does not store a new value when the date was invalid before its arguments were
  converted.
- From `VERSION_ES6`: `__defineGetter__` and `__defineSetter__` define the accessor with DefinePropertyOrThrow (a
  TypeError on a frozen or non-extensible object) and convert `this` with ToObject; `__lookupGetter__`/`Setter__`
  use the [[GetOwnProperty]] of each object; `hasOwnProperty`, `propertyIsEnumerable` and `isPrototypeOf` follow
  the order of their spec steps; `Object.prototype.toString` gives `Object` for a standard object whose
  `Symbol.toStringTag` was removed (before, its class name) and `Array` for a proxy of an array.
- **Why:** ECMA-262 2026, §21.4.4 (Date setters), §20.1.3 (Object.prototype), Annex B.2.2.

### 2.28 JSON revivers and replacers, Object.hasOwn, Object.assign

*Since plan G5 (2026-10-13): only under `VERSION_ECMASCRIPT`; `VERSION_ES6` keeps the "before" behavior.*

```js
JSON.parse("[1]", function (k, v) { return typeof k; });     // before: "string" for "", "number" for 0   now: "string"
JSON.stringify([1], (k, v) => typeof k === "number" ? 2 : v); // before: "[2]"   now: "[1]"
Object.assign(Object.freeze({ a: 1 }), { a: 2 });             // before: ignored   now: TypeError
```

- From `VERSION_ES6`: the keys passed to a `JSON.parse` reviver and a `JSON.stringify` replacer are always strings;
  a revived value is defined with CreateDataProperty (it replaces a read-only configurable property, before ignored);
  the replacer gets `undefined` for a property deleted while serializing; `Object.assign` throws a TypeError when a
  target property cannot be written; `Object.hasOwn` converts its first argument before the key.
- All versions: `JSON.stringify` treats a proxy of an array as an array; `Date.prototype.toJSON` no longer throws when
  `toISOString` returns an object.
- **Why:** ECMA-262 2026, §25.5.1 (JSON.parse), §25.5.2 (JSON.stringify), §20.1.2.1 (Object.assign), §20.1.2.13
  (Object.hasOwn), §21.4.4.37 (Date.prototype.toJSON).

### 2.29 Array methods and species

```js
Array.prototype.toString.call({ join() { return "x"; } });   // before: ""   now: "x"
Array.prototype.push.call({ length: 2 ** 53 - 1 }, 1);       // before: length unchanged   now: TypeError
Reflect.has([1], "0");                                        // before: false   now: true
```

- From `VERSION_ES6`: `Array.prototype.toString` calls `this.join` (or Object.prototype.toString); `push` throws a
  TypeError past 2^53−1; an assignment to an array's `length` converts the value before the writability check;
  ArraySpeciesCreate sees through proxies; `Object.prototype.toString` of a non-array proxy is `[object Object]` or
  `[object Function]` (before, the class of the target).
- All versions: `flat`/`flatMap` no longer write `length` on the result at the end; `splice()` with no argument reads
  and writes `length`; `reverse`, `splice` and `flat` call HasProperty before Get; typed array iteration skips
  indexes out of bounds after a resize; `Reflect.has` converts its key (index keys given as strings are found).
- **Why:** ECMA-262 2026, §23.1.3 (Array.prototype), §10.4.2.3 (ArraySpeciesCreate), §10.4.2.4 (ArraySetLength),
  §28.1.9 (Reflect.has), §20.1.3.6 (Object.prototype.toString).

### 2.30 Promise combinators, IsRegExp, ToPrimitive and null

```js
Promise.allSettled([Promise.reject(1)]);    // before: status " rejected"   now: status "rejected"
"a".startsWith({ [Symbol.match]: true });   // before: false   now: TypeError
"x".indexOf({ valueOf() { return null; }, toString: null });   // before: TypeError   now: -1 (searches "null")
```

- All versions: `Promise.all`/`allSettled`/`any`/`race` read `C.resolve` before the iterator and reject (instead of
  throwing) when the iterator's `return` method is not callable; the iterators of the built-ins read `return` when
  they are closed; an abrupt `then` getter rejects the promise.
- From `VERSION_ES6`: `startsWith`/`endsWith`/`includes` throw a TypeError for any object whose `Symbol.match` is
  truthy; `null` returned by `valueOf`/`toString` is a valid primitive for ToPrimitive.
- From `VERSION_ES6`: the `length` of a bound function is computed from the target's own `length` property (a
  redefined `length` is honored; before, the declared arity was used).
- **Why:** ECMA-262 2026, §27.2.4 (Promise combinators), §7.4.11 (IteratorClose), §7.2.6 (IsRegExp), §7.1.1.1
  (OrdinaryToPrimitive), §20.2.3.2 (Function.prototype.bind).

### 2.31 Array length truncation, Object.prototype, String indexes

```js
var a = [0, 1, 2]; Object.defineProperty(a, "1", { configurable: false });
a.length = 0;                               // before: length 0   now: length 2 (TypeError in strict mode)
Object.setPrototypeOf(Object.prototype, {}); // before: allowed   now: TypeError
"use strict"; new String("ab")[0] = "x";     // before: ignored   now: TypeError
```

- From `VERSION_ES6`: truncating an array stops at a non-configurable element; an index cannot be defined at or
  above a read-only `length`; the prototype of `Object.prototype` cannot be changed; assigning an index of a String
  object throws in strict mode, also from `Object.assign` (`Object.assign("a", [1])`); the `__proto__`
  getter throws for `undefined`/`null`; a setter-only accessor's descriptor has `get: undefined`.
- **Why:** ECMA-262 2026, §10.4.2 (Array exotic objects), §10.4.7 (immutable prototype exotic objects), §10.4.3
  (String exotic objects), §20.1.2.1 (Object.assign), §20.1.3.8.1 (`__proto__`).

### 2.32 Set methods, BigInt.asIntN, Error prototypes

```js
new Set([1]).symmetricDifference({ size: 1, has() { throw 0; }, keys() { /* ... */ } });  // before: throws 0
BigInt.asIntN(8, 1);                                    // before: 1n   now: TypeError
Object.prototype.toString.call(TypeError.prototype);    // before: "[object Error]"   now: "[object Object]"
```

- All versions: the Set methods read `size`, `has` and `keys` in the spec's order, use the `keys()` iterator
  directly and no longer call `has` in `symmetricDifference`; `BigInt.asIntN`/`asUintN` reject Numbers.
- From `VERSION_ES6`: `TypeError.prototype` and the other NativeError prototypes are plain objects (no
  `[[ErrorData]]`); `Date.prototype[Symbol.toPrimitive]` is read-only.
- **Why:** ECMA-262 2026, §24.2.4 (Set.prototype), §21.2.2.1 (BigInt.asIntN), §20.5.6.3 (NativeError
  prototypes), §21.4.4.45 (Date.prototype[@@toPrimitive]).

### 2.33 Typed array constructors and conversions

```js
new Int8Array(-1);                     // before: Error "invalid argument"   now: RangeError
new BigInt64Array([1]);                // before: BigInt64Array [1n]   now: TypeError (1 is a Number)
Float64Array.from([, 1])[0];           // before: 0   now: NaN
```

- All versions: the typed array constructors use ToIndex for a primitive length; BigInt typed arrays and DataView's
  BigInt setters reject Numbers; `%TypedArray%.from` uses the iterator of arrays too; `set` accepts a primitive
  source (ToObject); `toReversed`/`toSorted`/`with` throw for a detached array; `with(i)` without a value stores
  `undefined` converted (NaN in a float array; before, 0).
- **Why:** ECMA-262 2026, §23.2.5.1 (TypedArray), §23.2.2.1 (%TypedArray%.from), §7.1.13 (ToBigInt), §23.2.3
  (%TypedArray%.prototype).

### 2.34 RegExp.prototype, constructor and patterns

```js
Object.prototype.toString.call(RegExp.prototype);   // before: "[object RegExp]"   now: "[object Object]"
RegExp.prototype.global;                            // before: false   now: undefined
new RegExp("x{1}{1,}");                             // before: a regexp   now: SyntaxError
new RegExp("").source;                              // before: ""   now: "(?:)"
```

- From `VERSION_ES6`: `RegExp.prototype` is a plain object, its flag getters give `undefined` and `exec` throws
  on it; `flags` lists `dgimsuvy` in this order; the constructor uses `Symbol.match` and the `source`/`flags` of
  regexp-like objects; `toString` is generic; a braced quantifier after a quantifier is a SyntaxError; quantifier
  bounds above 65535 are accepted; a negative `lastIndex` is 0; `source` escapes line terminators and gives
  `"(?:)"` for an empty pattern.
- All versions: format-control characters stay in regexp literals.
- **Why:** ECMA-262 2026, §22.2.6 (RegExp.prototype), §22.2.4.1 (RegExp), §22.2.6.13.1 (EscapeRegExpPattern),
  Annex B.1.2 (InvalidBracedQuantifier), §12.1 (format-control characters).

### 2.35 Logical and compound assignment, setter-less accessors, `with`

```js
var o = {}; Object.defineProperty(o, "p", { value: 0, writable: false });
o.p &&= 1;                                  // before: TypeError (strict) or a write   now: 0, no write
"use strict"; Object.defineProperty(o, "q", { get() {}, set: undefined }); o.q = 1;   // before: ignored   now: TypeError
with ({ v: 1, [Symbol.unscopables]: { v: true } }) v;   // before: 1   now: the outer v
```

- All versions: logical assignment does not write when it short-circuits; an accessor with `set: undefined` has
  no setter (TypeError in strict mode).
- From `VERSION_ES6`: compound assignment and `++`/`--` convert an object key once; strict assignments to a
  deleted global binding throw a ReferenceError; `with` honors `Symbol.unscopables`.
- **Why:** ECMA-262 2026, §13.15.2 (assignment operators), §10.1.9.2 (OrdinarySetWithOwnDescriptor), §9.1.1.2
  (Object Environment Records).

### 2.36 `instanceof`, arrow covers, `eval(...)`

```js
1 instanceof Math;                          // before: false   now: TypeError (not callable, no @@hasInstance)
0 instanceof { [Symbol.hasInstance]: v => v === 0 };   // before: false   now: true
var f = ({ x = 1 }) => x;                   // before: SyntaxError   now: an arrow function
```

- All versions: arrow parameters may use CoverInitializedName; `eval(...args)` is a direct eval; a multi-line
  comment with a line terminator ends a statement for ASI.
- From `VERSION_ES6`: `instanceof` calls `Symbol.hasInstance` for primitives too and rejects a right operand that
  is not callable and has no `Symbol.hasInstance`.
- **Why:** ECMA-262 2026, §13.10.2 (InstanceofOperator), §15.3 (ArrowParameters cover grammar), §19.2.1.1
  (direct eval), §12.4 (comments).

### 2.37 Generator methods, `>>>` with BigInt, `finally` in compiled code

```js
({ *g() {} }).g.prototype;                  // before: undefined   now: an object inheriting %GeneratorPrototype%
0n >>> { [Symbol.toPrimitive]() { throw 'mine'; } };   // before: TypeError   now: throws 'mine'
function f() { try { return 1; } catch (e) {} finally { n++; throw 'f'; } }   // compiled: before n += 2, now n += 1
```

- All versions: a generator method has a `prototype` property; both operands of `>>>` are converted
  (ToNumeric) before a BigInt operand throws; in compiled mode, an exception thrown by a `finally` block inlined
  for a `return` (or `break`/`continue`) in a `try` with a `catch` no longer runs the `finally` block again, and a
  `try` whose block ends with an unreachable `return` keeps covering its `catch` block with the `finally`.
- **Why:** ECMA-262 2026, §15.5.4 (generator method definitions), §13.9.3 (unsigned right shift), §14.15.3
  (try statement).

### 2.38 `for-in` shadowing, Use Strict Directives

```js
var o = Object.create({ p: 1 }, { p: { value: 0, enumerable: false } });
for (var k in o) print(k);                  // before: p   now: (nothing)
function f() { 'use str\
ict'; return this; }                        // before: strict   now: sloppy (the directive is not exact)
```

- All versions: a non-enumerable property hides the enumerable property of the same name of a prototype from
  `for-in`; only the exact source text `'use strict'` or `"use strict"` is a Use Strict Directive.
- **Why:** ECMA-262 2026, §14.7.5.9 (EnumerateObjectProperties), §11.2.1 (directive prologues).

### 2.39 Annex B built-ins and HTML-like comments

```js
String.prototype.trimLeft === String.prototype.trimStart;   // before: false   now: true
Object.getOwnPropertyDescriptor(RegExp, '$1').enumerable;   // before: true    now: false
/a/.compile(/b/, 'g');                                      // before (ES6): recompiled   now: TypeError
/* comment
*/ --> the rest of this line is a comment                   // before: SyntaxError   now: a comment
```

- All versions: the Annex B aliases, lengths and descriptors of String and RegExp; `setYear` rounding;
  `compile` rejects subclass instances and a RegExp with flags; HTML close comments after block comments.
- **Why:** ECMA-262 2026, B.2.2 (String), B.2.3.4 (setYear), B.2.4 (RegExp), B.1.1 (HTML-like comments).

### 2.40 `await` is an identifier in scripts; async functions

```js
var await = 1;                     // before (ES6): SyntaxError (reserved word)   now: a variable
async function f() { return 1; }   // before: SyntaxError   now: an async function
var o = { async m() {} };          // before: SyntaxError   now: an async method
```

- ES6 and later versions: `await` is reserved only in async functions and modules, and `async` starts an async
  function, arrow function or method. Neither form parsed before; `async` stays an ordinary identifier elsewhere
  (`async(x)` is still a call). Rhino only reserved `await` under ES6: it was already an identifier in earlier versions.
- **Why:** ECMA-262 2026, §13.1.1 (Static Semantics: Early Errors, `await`), §15.8 (async function definitions).

### 2.41 Element accesses in an optional chain

```js
var a;
a?.b[0];          // before: TypeError (cannot read property "0" of undefined)   now: undefined
a?.b[f()];        // before: f is called   now: f is not called
```

- ES6 and later versions: a `[...]` access after `?.` in the same chain is short-circuited like a `.name` access.
- **Why:** ECMA-262 2026, §13.3.9 (Optional Chains).

### 2.42 Strict generators resumed from sloppy code

```js
var g = (function () { 'use strict'; return function* () { eval('var x = 010'); }; })();
g().next();       // before: no error (the eval was sloppy)   now: SyntaxError (the eval is strict)
```

- ES6 and later versions: the body of a strict generator (or async function) runs in strict mode whoever resumes
  it; before, it took the strictness of the code that called `next()`, which a direct `eval` in it inherited.
- **Why:** ECMA-262 2026, §11.2.2 (strict mode code), §19.2.1.1 (PerformEval: the strictness of the caller).

### 2.43 Generators: yield in a finally run by return(), catch after a resumed finally

```js
function* g() { try { yield 1; } finally { yield 2; } }
var it = g(); it.next();
it.return(9);   // before (interpreted): TypeError "Yield from closing generator"   now: { value: 2, done: false }
it.next();      // now: { value: 9, done: true }
```

- ES6 and later versions: a `finally` block run by `return()` can yield; the return completes when the generator is
  resumed. In compiled mode, a `catch` around a `try`/`finally` whose `finally` yielded catches the exception that
  the `finally` rethrows when the generator is resumed (before, it escaped the generator).
- **Why:** ECMA-262 2026, §27.5.3.3 (GeneratorResume), §14.15.3 (try statement evaluation).

### 2.44 RegExp flag getters and another realm's RegExp.prototype

```js
var other = /* the global object of another realm */;
Object.getOwnPropertyDescriptor(RegExp.prototype, 'global').get.call(other.RegExp.prototype);
// before: undefined   now: TypeError
```

- ES6 and later versions: the getters of the flags and `source` answer `undefined` (`"(?:)"`) only for the
  RegExp.prototype of their own realm; another object that is not a RegExp is a TypeError of the getter's realm.
- **Why:** ECMA-262 2026, §22.2.6.4.1 (RegExpHasFlag), §22.2.6.13 (get RegExp.prototype.source).

### 2.45 Array-likes longer than 2^31 - 1

```js
new Int8Array({ length: 2 ** 53 });        // before: java.lang.IllegalArgumentException   now: RangeError
f.apply(null, { length: 2 ** 32 });        // same
```

- All versions: an array-like whose length exceeds what a Java array holds is a RangeError, not a Java exception.
- **Why:** ECMA-262 2026, §23.2.5.1.4 (InitializeTypedArrayFromArrayLike), §7.3.18 (CreateListFromArrayLike):
  the specification's limit is memory; a JavaScript error is what a script can handle.

### 2.46 The prototype of objects constructed for a newTarget of another realm

```js
var nt = new other.Function(); nt.prototype = null;   // other: the global object of another realm
Object.getPrototypeOf(Reflect.construct(TypeError, [], nt));
// before: other.Error.prototype   now: other.TypeError.prototype
Object.getPrototypeOf(Reflect.construct(function () {}, [], nt));
// before: Object.prototype   now: other.Object.prototype
```

- All versions: when the `prototype` of newTarget is not an object, a native error, a `WeakRef`, a
  `FinalizationRegistry` and an ordinary object get the intrinsic prototype of newTarget's realm.
- **Why:** ECMA-262 2026, §10.1.14 (GetPrototypeFromConstructor).

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
