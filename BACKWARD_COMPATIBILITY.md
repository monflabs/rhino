# Backward compatibility

This fork of Rhino (`org.monflabs.mozilla`) moves the engine toward ECMAScript 2026 (see
[MONFLABS_PLAN.md](MONFLABS_PLAN.md)). This document lists every change that can make code or an embedding behave
differently from **stock Rhino 1.9.0**, so that upgrading is a deliberate, informed step. It is updated with each
change; [CHANGELOG_MONFLABS.md](CHANGELOG_MONFLABS.md) has the full history.

The target is the language version `Context.VERSION_ECMASCRIPT`, the default language version since plan M15 (2.3).
Every behavior change is only active under `VERSION_ECMASCRIPT` (section 1): `VERSION_ES6` and the older versions keep
the behavior of stock Rhino, bug fixes included, so an embedding that needs it selects `VERSION_ES6`
(`cx.setLanguageVersion(Context.VERSION_ES6)`, or `-version 200` in the shell). Section 2 lists platform and API
changes.

| # | Change | Applies to |
|---|--------|-----------|
| 1.1 | Assignment to a `const` throws a TypeError | `VERSION_ECMASCRIPT` |
| 1.2 | Temporal dead zone (TDZ) | `VERSION_ECMASCRIPT` |
| 1.2b | Temporal dead zone of parameters | `VERSION_ECMASCRIPT` |
| 1.3 | Global lexical declarations | `VERSION_ECMASCRIPT` |
| 1.4 | Declarations of eval code | `VERSION_ECMASCRIPT` |
| 1.5 | Lexical declarations of eval code | `VERSION_ECMASCRIPT` |
| 1.6 | Byte order of typed arrays | `VERSION_ECMASCRIPT` |
| 1.7 | `\p` in a regexp without the `u` flag | `VERSION_ECMASCRIPT` |
| 1.8 | Completion values of statements | `VERSION_ECMASCRIPT` |
| 1.9 | Destructuring in a `for-in` head | `VERSION_ECMASCRIPT` |
| 1.10 | `let` as an identifier | `VERSION_ECMASCRIPT` |
| 1.11 | Global declaration instantiation | `VERSION_ECMASCRIPT` |
| 1.12 | Early errors | `VERSION_ECMASCRIPT` |
| 1.13 | Annex B: RegExp patterns, calls as assignment targets | `VERSION_ECMASCRIPT` |
| 1.14 | `__proto__` in object literals, global vars shadowing Object.prototype | `VERSION_ECMASCRIPT` |
| 1.15 | `for (async of ...)` | `VERSION_ECMASCRIPT` |
| 1.16 | ZWNJ and ZWJ in identifiers | `VERSION_ECMASCRIPT` |
| 1.17 | `const` in a loop body takes each iteration's value | `VERSION_ECMASCRIPT` |
| 1.18 | `for (const x of/in …)` initializes `x` | `VERSION_ECMASCRIPT` |
| 1.19 | `let`/`const` loop heads create a binding per iteration | `VERSION_ECMASCRIPT` |
| 1.20 | Assignment to a block-level `const` | `VERSION_ECMASCRIPT` |
| 1.21 | `let x;` in a loop body is `undefined` on each iteration | `VERSION_ECMASCRIPT` |
| 1.22 | `this` of strict functions | `VERSION_ECMASCRIPT` |
| 1.23 | `caller` of strict functions throws | `VERSION_ECMASCRIPT` |
| 1.24 | Bound functions | `VERSION_ECMASCRIPT` |
| 1.25 | The `arguments` object | `VERSION_ECMASCRIPT` |
| 1.26 | Early errors of parameter lists | `VERSION_ECMASCRIPT` |
| 1.27 | `Function.prototype.toString` | `VERSION_ECMASCRIPT` |
| 1.28 | Eval code | `VERSION_ECMASCRIPT` |
| 1.29 | Array destructuring follows the iterator protocol | `VERSION_ECMASCRIPT` |
| 1.30 | Object patterns and default names | `VERSION_ECMASCRIPT` |
| 1.31 | `for-of` closes its iterator | `VERSION_ECMASCRIPT` |
| 1.32 | Early errors of object literals and functions | `VERSION_ECMASCRIPT` |
| 1.33 | `yield` | `VERSION_ECMASCRIPT` |
| 1.34 | Names of functions and classes in default values | `VERSION_ECMASCRIPT` |
| 1.35 | Getters and setters are not constructors | `VERSION_ECMASCRIPT` |
| 1.36 | Legacy static properties of RegExp read through another object | `VERSION_ECMASCRIPT` |
| 1.37 | The name of a function expression is immutable | `VERSION_ECMASCRIPT` |
| 1.38 | `Object.defineProperty` on a non-configurable property | `VERSION_ECMASCRIPT` |
| 1.39 | Typed arrays as exotic objects | `VERSION_ECMASCRIPT` |
| 1.40 | Reflect.set and proxies | `VERSION_ECMASCRIPT` |
| 1.41 | String methods and the symbols of primitives | `VERSION_ECMASCRIPT` |
| 1.42 | Array methods on frozen objects | `VERSION_ECMASCRIPT` |
| 1.43 | Object.prototype residuals and Date setters | `VERSION_ECMASCRIPT` |
| 1.44 | JSON revivers and replacers, Object.hasOwn, Object.assign | `VERSION_ECMASCRIPT` |
| 1.45 | Array methods and species | `VERSION_ECMASCRIPT` |
| 1.46 | Promise combinators, IsRegExp, ToPrimitive and null | `VERSION_ECMASCRIPT` |
| 1.47 | Array length truncation, Object.prototype, String indexes | `VERSION_ECMASCRIPT` |
| 1.48 | Set methods, BigInt.asIntN, Error prototypes | `VERSION_ECMASCRIPT` |
| 1.49 | Typed array constructors and conversions | `VERSION_ECMASCRIPT` |
| 1.50 | RegExp.prototype, constructor and patterns | `VERSION_ECMASCRIPT` |
| 1.51 | Logical and compound assignment, setter-less accessors, `with` | `VERSION_ECMASCRIPT` |
| 1.52 | `instanceof`, arrow covers, `eval(...)`, object rest | `VERSION_ECMASCRIPT` |
| 1.53 | Generator methods, `>>>` with BigInt, `finally` in compiled code | `VERSION_ECMASCRIPT` |
| 1.54 | `for-in` shadowing, Use Strict Directives, nested cover initializers | `VERSION_ECMASCRIPT` |
| 1.55 | Annex B built-ins and HTML-like comments | `VERSION_ECMASCRIPT` |
| 1.56 | `await` is an identifier in scripts | `VERSION_ECMASCRIPT` |
| 1.57 | Element accesses and calls in an optional chain | `VERSION_ECMASCRIPT` |
| 1.58 | Strict generators resumed from sloppy code | `VERSION_ECMASCRIPT` |
| 1.59 | Generators: yield in a finally run by return(), catch after a resumed finally | `VERSION_ECMASCRIPT` |
| 1.60 | RegExp flag getters and another realm's RegExp.prototype | `VERSION_ECMASCRIPT` |
| 1.61 | Array-likes longer than 2^31 - 1 | `VERSION_ECMASCRIPT` |
| 1.62 | The prototype of objects constructed for a newTarget whose `prototype` is not an object | `VERSION_ECMASCRIPT` |
| 1.63 | RegExp alternatives in a lookbehind, supplementary characters in an alternative class | `VERSION_ECMASCRIPT` |
| 1.64 | `Iterator` is the ES2025 Iterator, built-in iterators inherit `Iterator.prototype` | `VERSION_ECMASCRIPT` |
| 1.65 | `decodeURI` noncharacters, `Math.log2`, final sigma, `Proxy.revocable` | `VERSION_ECMASCRIPT` |
| 1.66 | Functions have no `arity` property | `VERSION_ECMASCRIPT` |
| 1.67 | A yield expression in arrow parameters is an early error | `VERSION_ECMASCRIPT` |
| 1.68 | Closures in parameter expressions do not see the `var` declarations of the body | `VERSION_ECMASCRIPT` |
| 1.69 | Strict methods called on a primitive receive the primitive as `this` | `VERSION_ECMASCRIPT` |
| 1.70 | A data property of an object literal replaces an accessor of the same name | `VERSION_ECMASCRIPT` |
| 1.71 | `caller` and `arguments`: %ThrowTypeError% on `Function.prototype`, own properties of sloppy functions | `VERSION_ECMASCRIPT` |
| 1.72 | `new ArrayBuffer` reads the prototype of newTarget before allocating | `VERSION_ECMASCRIPT` |
| 1.73 | A generator's `return()` inside a destructuring pattern closes its iterator as a return | `VERSION_ECMASCRIPT` |
| 1.74 | The discriminant of a `switch` and the parameter of a `catch` do not see the block's `let`/`const` | `VERSION_ECMASCRIPT` |
| 1.75 | Closures in the head of `for (let/const ... in/of ...)` see the loop bindings in their TDZ | `VERSION_ECMASCRIPT` |
| 1.76 | `push` and `unshift` go through index properties of the prototypes | `VERSION_ECMASCRIPT` |
| 1.77 | An object pattern evaluates a property target before reading the source property | `VERSION_ECMASCRIPT` |
| 1.78 | Proxy and String object internal methods in `toString`, `defineProperties`, `ownKeys` and index definition | `VERSION_ECMASCRIPT` |
| 1.79 | Generator declarations in blocks are not hoisted to the function (sloppy mode) | `VERSION_ECMASCRIPT` |
| 1.80 | `"NaN"` is a numeric key of a TypedArray | `VERSION_ECMASCRIPT` |
| 1.81 | Accessors read or written on a primitive receive the primitive | `VERSION_ECMASCRIPT` |
| 1.82 | `super[key]++` evaluates the key once and converts the value with ToNumeric | `VERSION_ECMASCRIPT` |
| 1.83 | With parameter expressions, function declarations are instantiated after the parameters | `VERSION_ECMASCRIPT` |
| 1.84 | A compound assignment in `with` resolves the name once | `VERSION_ECMASCRIPT` |
| 1.85 | The `flags` getter of `RegExp.prototype` rejects a primitive `this` | `VERSION_ECMASCRIPT` |
| 2.1 | Packages and Maven coordinates renamed | all |
| 2.2 | Java 21 minimum; Android not supported | all |
| 2.3 | `VERSION_ECMASCRIPT` is the default language version | embeddings that do not set a version |

---

## 1. Changes under `VERSION_ECMASCRIPT` only

Code running under `VERSION_ES6` is not affected. `VERSION_ECMASCRIPT` is the default (2.3): to keep the previous
behavior, select `VERSION_ES6`. Each change is also listed in the Javadoc of `Context.VERSION_ECMASCRIPT`, and every place in
the engine that implements one tests `isLatestEcmaScript()`.

Entries 1.17 to 1.62 were numbered 2.1 to 2.46 (the CHANGELOG uses these numbers) and applied to every version from
`VERSION_ES6` until plan G (2026-10-13), which restored the stock behavior under `VERSION_ES6`. In these entries,
"before" is the behavior under `VERSION_ES6` (stock Rhino) and "now" the one under `VERSION_ECMASCRIPT`.

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

### 1.17 `const` in a loop body takes each iteration's value

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

### 1.18 `for (const x of/in …)` initializes `x`

```js
function f() { var s = 0; for (const v of [1, 2, 3]) { s += v; } return s; }
f();   // before: NaN (v stayed undefined in functions without closures)   now: 6
```

- **Why:** the loop binding is initialized with each value (§14.7.5.7 ForIn/OfBodyEvaluation).

### 1.19 `let`/`const` loop heads create a binding per iteration

```js
var fs = [];
for (let i = 0; i < 3; i++) { fs.push(function () { return i; }); }
fs.map(function (f) { return f(); });   // before: [3, 3, 3]   now: [0, 1, 2]
```

- Applies to `for (let …; …; …)`, `for (let/const … in …)` and `for (let/const … of …)`.
- **Why:** §14.7.4.4 CreatePerIterationEnvironment and §14.7.5.7 ForIn/OfBodyEvaluation.
- **Performance:** only loops whose bindings live in a scope object (functions that create closures, and scripts)
  copy the scope once per iteration; other loops are unchanged.

### 1.20 Assignment to a block-level `const`

```js
function f() { { const k = 1; (function () {}); k = 2; return k; } }
f();   // before: 2 (the assignment succeeded)   now: TypeError (see 1.1)
```

- Before, a `const` declared in a block of a function with closures (or of a script) was stored as an ordinary
  variable, so assigning to it succeeded; every other `const` ignored the assignment. Under `VERSION_ECMASCRIPT`,
  every `const` is a binding of its block and the assignment throws (see 1.1).

### 1.21 `let x;` in a loop body is `undefined` on each iteration

```js
function f() {
  var r = [];
  for (var i = 0; i < 2; i++) { let x; if (i == 0) x = 1; r.push(x); }
  return r.join();
}
f();   // before: "1,1" (in functions without closures)   now: "1,"
```

- **Why:** a `let` declaration without an initializer initializes the binding to `undefined` each time it is
  evaluated (§14.3.1).

### 1.22 `this` of strict functions

```js
"use strict";
function f() { return typeof this; }
f();            // before: "object"   now: "undefined" (this === undefined was already true)
f.call(1);      // before: "object"   now: "number" (call/apply no longer box a primitive this)
```

- **Why:** in strict code, `this` is not coerced (ECMA-262 2026, §10.2.1.2 OrdinaryCallBindThis). A non-strict
  function still receives an object. **Not covered yet:** a strict getter or method called on a primitive (`(5).x`,
  `"s".m()`) still receives the wrapper object; so does a strict function bound to a primitive with `bind`.

### 1.23 `caller` of strict functions throws

```js
(function () { "use strict"; function s() {} s.caller; })();   // before: undefined   now: TypeError
Math.max.caller;                                                // before: undefined   now: TypeError
```

- **Why:** `Function.prototype.caller` is a throwing accessor and strict functions have no own `caller` (§10.2.4
  AddRestrictedFunctionProperties, §17.1 Forbidden Extensions). Non-strict functions still answer `undefined`
  (their caller is not tracked). Bound functions no longer have an own throwing `caller` property (the prototype's applies).
- The `arguments` accessor of `Function.prototype` follows the same rule (plan 1.2c): reading or writing
  `f.arguments` is a TypeError when `f` is strict or built-in (before: `null` unless the strict function was running).

### 1.24 Bound functions

```js
var t = Object.defineProperty(function () {}, "name", { value: "target" });
t.bind().name;                                   // before: "bound "   now: "bound target"
(function () { "use strict"; return function () { return this; }.bind()(); })();   // before: null   now: undefined
```

- **Why:** §20.2.3.2 Function.prototype.bind reads the target's `name` property when binding, and a bound `this` of
  `undefined` stays `undefined` for a strict target.

### 1.25 The `arguments` object

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

### 1.26 Early errors of parameter lists

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

### 1.27 `Function.prototype.toString`

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

### 1.28 Eval code

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

### 1.29 Array destructuring follows the iterator protocol

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
- The array iterator (`values()`, `for-of`, spread) reads a hole through the prototype chain, like `a[i]` (before,
  it gave `undefined` even when `Array.prototype` had that index).
- **Why:** ECMA-262 2026, §8.6.2 IteratorBindingInitialization and §13.15.5.5 IteratorDestructuringAssignmentEvaluation.
- When a default value or a target throws (or a generator is returned from inside the pattern), the iterator is
  closed too; an error of its `return()` does not replace the error being thrown.

### 1.30 Object patterns and default names

```js
var {} = null;                       // before: fine           now: TypeError
var [f = function () {}] = [];       // before: f.name is ""   now: "f"
```

- An object pattern throws a TypeError when the value is `null` or `undefined`, also when it
  is empty or reads no property.
- An anonymous function, generator or arrow function given as the default value of a binding in a pattern is named
  after that binding, as `var f = function () {}` already was.
- In assignment patterns, a property target (`o.p`, `o[k]`) is evaluated before the value it receives is read (the
  iterator stepped, the source property read), and computed keys are evaluated in order, each right before its
  property (before, all computed keys were evaluated first). `[o.p = 1] = …` and `{ a: o.p = 1 } = …` are accepted
  (before: SyntaxError).
- **Why:** ECMA-262 2026, §8.6.2 BindingInitialization (RequireObjectCoercible), §8.6.3 KeyedBindingInitialization
  (NamedEvaluation of the initializer) and §13.15.5 DestructuringAssignmentEvaluation (order of evaluation).

### 1.31 `for-of` closes its iterator

```js
for (var x of iterable) break;   // before: iterable's iterator was left open   now: its return() is called
```

- A `for-of` loop left before its iterator is done (`break`, `continue` to an outer loop, `return`, a throw, a
  generator's `return()`) calls the iterator's `return()`. After a jump, an error of `return()` (or a result that
  is not an object) propagates; after a throw, the throw wins. `next` is read once, when the loop starts, and a
  result of `next()` that is not an object is a TypeError (before, it was converted to an object).
- **Why:** ECMA-262 2026, §14.7.5.7 ForIn/OfBodyEvaluation and §7.4.11 IteratorClose.

### 1.32 Early errors of object literals and functions

```js
({ get a(x) {} });                 // before: accepted   now: SyntaxError (a getter has no parameters)
({ set a() {} });                  // before: accepted   now: SyntaxError (a setter has exactly one)
function f(...rest = []) {}        // before: accepted   now: SyntaxError
({ __proto__: a, __proto__: b });  // before: accepted   now: SyntaxError (still valid as a pattern)
({ g\u0065t m() {} });             // before: a getter   now: SyntaxError
"use strict"; public = 1;          // before: ReferenceError at runtime   now: SyntaxError
```

- A `"use strict"` directive now also applies to the token right after it, which was read before
  the directive took effect; words reserved in strict code (`public`, `static`, `implements`, …) are rejected there
  like everywhere else in strict code.
- `in` is allowed in a computed property name inside a `for` head (`for (o = { ['x' in y]: 1 }; ; )`).
- Only an anonymous function written as a property value (`{ k: function () {} }`, also parenthesized) is named after
  its key; before, any anonymous function used as a value was renamed, e.g. `var f = (0, function () {});
  ({ x: f }); f.name` gave `"x"`, now `""`.
- **Why:** ECMA-262 2026, §15.4.1 (MethodDefinition early errors), §15.1.1 (FormalParameters), §B.3.1 (duplicate
  `__proto__`), §12.7.2 (escaped keywords), §11.2.2 (directive prologues).

### 1.33 `yield`

```js
var yield = 1;                               // before: SyntaxError   now: fine (non-strict code)
function f() { yield 1; }                    // before: a JavaScript 1.7 generator   now: SyntaxError
function* g() { var a = [yield 1, 2]; }      // before: a was [x]   now: [x, 2]
function* h() { yield\n* 1 }                // before: yield* 1   now: SyntaxError
```

- Outside generator functions, in non-strict code, `yield` is an identifier; a function that is
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

### 1.34 Names of functions and classes in default values

```js
function f(g = function () {}) { return g.name; }   // before: ""   now: "g"
var [h = () => 1] = []; h.name;                      // before: ""   now: "h"
```

- An anonymous function, arrow function, generator or class that is the default value of a
  parameter or of a destructuring target is named after the binding, like one assigned to a variable already was.
- **Why:** ECMA-262 2026, §8.6.3 (IsAnonymousFunctionDefinition, NamedEvaluation in SingleNameBinding and
  AssignmentElement initializers).

### 1.35 Getters and setters are not constructors

```js
var d = Object.getOwnPropertyDescriptor({ get a() { return 1; } }, "a");
"prototype" in d.get;   // before: true   now: false
new d.get();            // before: an object   now: TypeError
```

- The getters and setters of object literals (and classes) are method definitions: they have
  no `prototype` property and are not constructors, like the methods of an object literal already were.
- **Why:** ECMA-262 2026, §15.4.4 (MethodDefinition: DefineMethodProperty without MakeConstructor).

### 1.36 Legacy static properties of RegExp read through another object

```js
var F = Object.setPrototypeOf(function () {}, RegExp);
F.lastMatch;   // before: the last match   now: TypeError
```

- The legacy static properties `RegExp.input` (`$_`), `lastMatch` (`$&`), `lastParen` (`$+`),
  `leftContext`, `rightContext` and `$1`–`$9` are read (and `input` written) only on `RegExp` itself, not through
  an object inheriting from it, such as a class extending `RegExp`. `RegExp.multiline` (`$*`) is not affected.
- **Why:** ECMA-262 Annex B proposal "Legacy RegExp features" (GetLegacyRegExpStaticProperty), tested by test262.

### 1.37 The name of a function expression is immutable

```js
var f = function g() { g = 1; return typeof g; };
f();   // before: "number"   now: "function" (in strict code: TypeError)
var h = function k() { return function () { k = null; }; };   // JavaScript 1.x: this.k = null
```

- Inside a named function expression, assigning its name (directly, from a nested function or
  from eval code) has no effect, and throws a TypeError in strict code. A parameter or a declaration of the same name
  in the function still makes it an ordinary variable.
- `name = null` where `name` is the name of an enclosing function is an ordinary assignment
  (JavaScript 1.x rewrote it as `this.name = null`).
- A function compiled by `Context.compileFunction` (e.g. an event handler of an embedding) keeps both JavaScript 1.x
  behaviors: its name is a mutable variable, and `name = null` stores `this.name`.
- **Why:** ECMA-262 2026, §15.2.5 InstantiateOrdinaryFunctionExpression (CreateImmutableBinding of the name).

### 1.38 `Object.defineProperty` on a non-configurable property

```js
var o = Object.defineProperty({}, "x", { value: 1, writable: true, enumerable: true });
Object.defineProperty(o, "x", { value: 2 });   // before: TypeError   now: o.x is 2
```

- Redefining a non-configurable property with a descriptor that does not mention `enumerable` no
  longer throws "Cannot change the enumerable attribute".
- **Why:** ECMA-262 2026, §10.1.6.3 ValidateAndApplyPropertyDescriptor step 5.b (compares [[Enumerable]] only when
  the descriptor has one).

### 1.39 Typed arrays as exotic objects

```js
var ta = new Int8Array(2);
Reflect.ownKeys(ta);                          // before: []   now: ["0", "1"]
Object.getOwnPropertyDescriptor(ta, "0");     // before: TypeError   now: {value: 0, writable, enumerable, configurable}
"use strict"; delete ta[0];                   // before: false   now: TypeError
Object.defineProperty(ta, "0", {configurable: false});   // before: fine (no effect)   now: TypeError
ta["-0"];                                     // before: from the prototype   now: undefined
Object.create(ta)[0] = 1;                     // before: set ta[0]   now: an own property of the new object
```

- The elements of a typed array behave as specified for its internal methods: canonical numeric keys
  that are not valid indexes are absent without consulting the prototype chain; the elements are listed by
  `Reflect.ownKeys` and have descriptors; they cannot be deleted, made non-configurable, non-enumerable, read-only or
  accessors.
- **Why:** ECMA-262 2026, §10.4.5 TypedArray Exotic Objects.

### 1.40 Reflect.set and proxies

```js
Reflect.set(Object.freeze({x: 1}), "x", 2);       // before: true   now: false
Reflect.set({}, "x", 1, "receiver");              // before: TypeError   now: false
var p = new Proxy({}, { set() { return false; } });
(function () { "use strict"; p.x = 1; })();        // before: fine   now: TypeError
var q = new Proxy({}, { getOwnPropertyDescriptor() { return { value: 1, configurable: false }; } });
Object.getOwnPropertyDescriptor(q, "x");          // before: a descriptor   now: TypeError (invariant)
```

- `Reflect.set` returns false when the assignment fails, and accepts a receiver that is not an object.
  Proxies check the invariants of their `set` and `getOwnPropertyDescriptor` traps; a failed assignment through a
  proxy throws in strict code; a proxy in a prototype chain handles a lookup itself (its `getPrototypeOf` trap is not
  called by `in` or an assignment).
- Likewise for deleting, defining and changing the prototype: `Reflect.deleteProperty`, `Reflect.defineProperty`
  and `Reflect.setPrototypeOf` return the result of the proxy's trap (or target), `delete` (strict code),
  `Object.defineProperty` and `Object.setPrototypeOf` throw when it is false, and invariant violations of these
  traps throw a TypeError (before, `Reflect.defineProperty` returned false for any error). A proxy without a `get`
  trap calls the target's getters with the proxy as `this` (before, the target).
- **Why:** ECMA-262 2026, §10.1.9 [[Set]], §10.5 (proxy internal methods), §28.1 (Reflect).

### 1.41 String methods and the symbols of primitives

```js
Number.prototype[Symbol.match] = function () { return "custom"; };
"a1b".match(1);          // before: "custom"   now: ["1"] (1 is converted to the string "1")
```

- `String.prototype.match`, `matchAll`, `replace`, `replaceAll`, `search` and `split` use the
  `Symbol.match`/`matchAll`/`replace`/`search`/`split` method of their argument only when it is an object.
- **Why:** ECMA-262 2025 and later, §22.1.3 (the "If regexp is an Object" steps).

### 1.42 Array methods on frozen objects

```js
Object.freeze([1]).push(2);                    // before: 2 (nothing changed)   now: TypeError
Array.prototype.pop.call("abc");               // before: "c"   now: TypeError (length is read-only)
```

- The Array methods that write (`push`, `pop`, `shift`, `unshift`, `splice`, `reverse`,
  `fill`, `copyWithin`, …) throw a TypeError when an element or the length cannot be written or deleted.
- **Why:** ECMA-262 2026, §23.1.3 (the methods use Set(O, P, V, true) and DeletePropertyOrThrow).

### 1.43 Object.prototype residuals and Date setters

```js
var d = new Date(NaN); d.setDate({ valueOf() { d.setTime(0); return 1; } });
d.getTime();                                       // before: NaN   now: 0 (an invalid date is left unchanged)
Object.prototype.__defineGetter__.call(Object.freeze({}), "x", f);   // before: ignored   now: TypeError
Object.prototype.toString.call(new Proxy([], {}));                  // before: "[object Object]"   now: "[object Array]"
```

- A Date setter does not store a new value when the date was invalid before its arguments were
  converted.
- `__defineGetter__` and `__defineSetter__` define the accessor with DefinePropertyOrThrow (a
  TypeError on a frozen or non-extensible object) and convert `this` with ToObject; `__lookupGetter__`/`Setter__`
  use the [[GetOwnProperty]] of each object; `hasOwnProperty`, `propertyIsEnumerable` and `isPrototypeOf` follow
  the order of their spec steps; `Object.prototype.toString` gives `Object` for a standard object whose
  `Symbol.toStringTag` was removed (before, its class name) and `Array` for a proxy of an array.
- **Why:** ECMA-262 2026, §21.4.4 (Date setters), §20.1.3 (Object.prototype), Annex B.2.2.

### 1.44 JSON revivers and replacers, Object.hasOwn, Object.assign

```js
JSON.parse("[1]", function (k, v) { return typeof k; });     // before: "string" for "", "number" for 0   now: "string"
JSON.stringify([1], (k, v) => typeof k === "number" ? 2 : v); // before: "[2]"   now: "[1]"
Object.assign(Object.freeze({ a: 1 }), { a: 2 });             // before: ignored   now: TypeError
```

- The keys passed to a `JSON.parse` reviver and a `JSON.stringify` replacer are always strings;
  a revived value is defined with CreateDataProperty (it replaces a read-only configurable property, before ignored);
  the replacer gets `undefined` for a property deleted while serializing; `Object.assign` throws a TypeError when a
  target property cannot be written; `Object.hasOwn` converts its first argument before the key.
- `JSON.stringify` treats a proxy of an array as an array; `Date.prototype.toJSON` no longer throws when
  `toISOString` returns an object.
- **Why:** ECMA-262 2026, §25.5.1 (JSON.parse), §25.5.2 (JSON.stringify), §20.1.2.1 (Object.assign), §20.1.2.13
  (Object.hasOwn), §21.4.4.37 (Date.prototype.toJSON).

### 1.45 Array methods and species

```js
Array.prototype.toString.call({ join() { return "x"; } });   // before: ""   now: "x"
Array.prototype.push.call({ length: 2 ** 53 - 1 }, 1);       // before: length unchanged   now: TypeError
Reflect.has([1], "0");                                        // before: false   now: true
```

- `Array.prototype.toString` calls `this.join` (or Object.prototype.toString); `push` throws a
  TypeError past 2^53−1; an assignment to an array's `length` converts the value before the writability check;
  ArraySpeciesCreate sees through proxies; `Object.prototype.toString` of a non-array proxy is `[object Object]` or
  `[object Function]` (before, the class of the target).
- `flat`/`flatMap` no longer write `length` on the result at the end; `splice()` with no argument reads
  and writes `length`; `reverse`, `splice` and `flat` call HasProperty before Get; typed array iteration skips
  indexes out of bounds after a resize; `Reflect.has` converts its key (index keys given as strings are found).
- **Why:** ECMA-262 2026, §23.1.3 (Array.prototype), §10.4.2.3 (ArraySpeciesCreate), §10.4.2.4 (ArraySetLength),
  §28.1.9 (Reflect.has), §20.1.3.6 (Object.prototype.toString).

### 1.46 Promise combinators, IsRegExp, ToPrimitive and null

```js
Promise.allSettled([Promise.reject(1)]);    // before: status " rejected"   now: status "rejected"
"a".startsWith({ [Symbol.match]: true });   // before: false   now: TypeError
"x".indexOf({ valueOf() { return null; }, toString: null });   // before: TypeError   now: -1 (searches "null")
```

- `Promise.all`/`allSettled`/`any`/`race` read `C.resolve` before the iterator and reject (instead of
  throwing) when the iterator's `return` method is not callable; the iterators of the built-ins read `return` when
  they are closed; an abrupt `then` getter rejects the promise.
- `startsWith`/`endsWith`/`includes` throw a TypeError for any object whose `Symbol.match` is
  truthy; `null` returned by `valueOf`/`toString` is a valid primitive for ToPrimitive.
- The `length` of a bound function is computed from the target's own `length` property (a
  redefined `length` is honored; before, the declared arity was used).
- **Why:** ECMA-262 2026, §27.2.4 (Promise combinators), §7.4.11 (IteratorClose), §7.2.6 (IsRegExp), §7.1.1.1
  (OrdinaryToPrimitive), §20.2.3.2 (Function.prototype.bind).

### 1.47 Array length truncation, Object.prototype, String indexes

```js
var a = [0, 1, 2]; Object.defineProperty(a, "1", { configurable: false });
a.length = 0;                               // before: length 0   now: length 2 (TypeError in strict mode)
Object.setPrototypeOf(Object.prototype, {}); // before: allowed   now: TypeError
"use strict"; new String("ab")[0] = "x";     // before: ignored   now: TypeError
```

- Truncating an array stops at a non-configurable element; an index cannot be defined at or
  above a read-only `length`; the prototype of `Object.prototype` cannot be changed; assigning an index of a String
  object throws in strict mode, also from `Object.assign` (`Object.assign("a", [1])`); the `__proto__`
  getter throws for `undefined`/`null`; a setter-only accessor's descriptor has `get: undefined`.
- **Why:** ECMA-262 2026, §10.4.2 (Array exotic objects), §10.4.7 (immutable prototype exotic objects), §10.4.3
  (String exotic objects), §20.1.2.1 (Object.assign), §20.1.3.8.1 (`__proto__`).

### 1.48 Set methods, BigInt.asIntN, Error prototypes

```js
new Set([1]).symmetricDifference({ size: 1, has() { throw 0; }, keys() { /* ... */ } });  // before: throws 0
BigInt.asIntN(8, 1);                                    // before: 1n   now: TypeError
Object.prototype.toString.call(TypeError.prototype);    // before: "[object Error]"   now: "[object Object]"
```

- The Set methods read `size`, `has` and `keys` in the spec's order, use the `keys()` iterator
  directly and no longer call `has` in `symmetricDifference`; `BigInt.asIntN`/`asUintN` reject Numbers.
- `TypeError.prototype` and the other NativeError prototypes are plain objects (no
  `[[ErrorData]]`); `Date.prototype[Symbol.toPrimitive]` is read-only.
- **Why:** ECMA-262 2026, §24.2.4 (Set.prototype), §21.2.2.1 (BigInt.asIntN), §20.5.6.3 (NativeError
  prototypes), §21.4.4.45 (Date.prototype[@@toPrimitive]).

### 1.49 Typed array constructors and conversions

```js
new Int8Array(-1);                     // before: Error "invalid argument"   now: RangeError
new BigInt64Array([1]);                // before: BigInt64Array [1n]   now: TypeError (1 is a Number)
Float64Array.from([, 1])[0];           // before: 0   now: NaN
```

- The typed array constructors use ToIndex for a primitive length; BigInt typed arrays and DataView's
  BigInt setters reject Numbers; `%TypedArray%.from` uses the iterator of arrays too; `set` accepts a primitive
  source (ToObject); `toReversed`/`toSorted`/`with` throw for a detached array; `with(i)` without a value stores
  `undefined` converted (NaN in a float array; before, 0).
- **Why:** ECMA-262 2026, §23.2.5.1 (TypedArray), §23.2.2.1 (%TypedArray%.from), §7.1.13 (ToBigInt), §23.2.3
  (%TypedArray%.prototype).

### 1.50 RegExp.prototype, constructor and patterns

```js
Object.prototype.toString.call(RegExp.prototype);   // before: "[object RegExp]"   now: "[object Object]"
RegExp.prototype.global;                            // before: false   now: undefined
new RegExp("x{1}{1,}");                             // before: a regexp   now: SyntaxError
new RegExp("").source;                              // before: ""   now: "(?:)"
```

- `RegExp.prototype` is a plain object, its flag getters give `undefined` and `exec` throws
  on it; `flags` lists `dgimsuvy` in this order; the constructor uses `Symbol.match` and the `source`/`flags` of
  regexp-like objects; `toString` is generic; a braced quantifier after a quantifier is a SyntaxError; quantifier
  bounds above 65535 are accepted; a negative `lastIndex` is 0; `source` escapes line terminators and gives
  `"(?:)"` for an empty pattern.
- Format-control characters stay in regexp literals.
- **Why:** ECMA-262 2026, §22.2.6 (RegExp.prototype), §22.2.4.1 (RegExp), §22.2.6.13.1 (EscapeRegExpPattern),
  Annex B.1.2 (InvalidBracedQuantifier), §12.1 (format-control characters).

### 1.51 Logical and compound assignment, setter-less accessors, `with`

```js
var o = {}; Object.defineProperty(o, "p", { value: 0, writable: false });
o.p &&= 1;                                  // before: TypeError (strict) or a write   now: 0, no write
"use strict"; Object.defineProperty(o, "q", { get() {}, set: undefined }); o.q = 1;   // before: ignored   now: TypeError
with ({ v: 1, [Symbol.unscopables]: { v: true } }) v;   // before: 1   now: the outer v
null[{ toString() { return "k"; } }];       // before: message with "k"   now: message with "[object Object]"
```

- Logical assignment does not write when it short-circuits; an accessor with `set: undefined` has no setter
  (TypeError in strict mode); compound assignment and `++`/`--` convert an object key once; strict assignments to a
  deleted global binding throw a ReferenceError; `with` honors `Symbol.unscopables` and checks the binding again;
  the TypeError for a null or undefined base does not call the key's `toString`.
- **Why:** ECMA-262 2026, §13.15.2 (assignment operators), §10.1.9.2 (OrdinarySetWithOwnDescriptor), §9.1.1.2
  (Object Environment Records), §13.3.2.1 (property accessors).

### 1.52 `instanceof`, arrow covers, `eval(...)`, object rest

```js
1 instanceof Math;                          // before: false   now: TypeError (not callable, no @@hasInstance)
0 instanceof { [Symbol.hasInstance]: v => v === 0 };   // before: false   now: true
var f = ({ x = 1 }) => x;                   // before: SyntaxError   now: an arrow function
var { 1.5: a } = { 1: "one", "1.5": "onefive" };       // before: "one"   now: "onefive"
```

- `instanceof` calls `Symbol.hasInstance` for primitives too and rejects a right operand that is not callable and
  has no `Symbol.hasInstance`; arrow parameters may use CoverInitializedName; `eval(...args)` is a direct eval; a
  multi-line comment with a line terminator ends a statement for ASI; object rest and spread of a Proxy use
  CopyDataProperties; numeric and BigInt keys of an object pattern are converted to strings and excluded from the
  rest; a symbol computed key of a rest pattern is excluded (interpreted mode threw); `in` is allowed inside
  brackets and braces in a `for` head; in strict code, `eval` and `arguments` as destructuring targets are an early
  SyntaxError.
- **Why:** ECMA-262 2026, §13.10.2 (InstanceofOperator), §15.3 (ArrowParameters cover grammar), §19.2.1.1
  (direct eval), §12.4 (comments), §8.6.3 (rest bindings), §7.3.25 (CopyDataProperties).

### 1.53 Generator methods, `>>>` with BigInt, `finally` in compiled code

```js
({ *g() {} }).g.prototype;                  // before: undefined   now: an object inheriting %GeneratorPrototype%
0n >>> { [Symbol.toPrimitive]() { throw 'mine'; } };   // before: TypeError   now: throws 'mine'
function f() { try { return 1; } catch (e) {} finally { n++; throw 'f'; } }   // compiled: before n += 2, now n += 1
```

- A generator method has a `prototype` property; both operands of `>>>` are converted (ToNumeric) before a BigInt
  operand throws; in compiled mode, an exception thrown by a `finally` block inlined for a `return` (or
  `break`/`continue`) in a `try` with a `catch` no longer runs the `finally` block again, and a `try` whose block
  ends with an unreachable `return` keeps covering its `catch` block with the `finally`. Under `VERSION_ES6`, the
  compiled mode keeps these two bugs of stock Rhino.
- **Why:** ECMA-262 2026, §15.5.4 (generator method definitions), §13.9.3 (unsigned right shift), §14.15.3
  (try statement).

### 1.54 `for-in` shadowing, Use Strict Directives, nested cover initializers

```js
var o = Object.create({ p: 1 }, { p: { value: 0, enumerable: false } });
for (var k in o) print(k);                  // before: p   now: (nothing)
function f() { 'use str\
ict'; return this; }                        // before: strict   now: sloppy (the directive is not exact)
[{ x = 1 }] = [{}];                         // before: SyntaxError   now: x is 1
```

- A non-enumerable property hides the enumerable property of the same name of a prototype from `for-in`; only the
  exact source text `'use strict'` or `"use strict"` is a Use Strict Directive; a CoverInitializedName nested in a
  literal that becomes a pattern is accepted.
- **Why:** ECMA-262 2026, §14.7.5.9 (EnumerateObjectProperties), §11.2.1 (directive prologues), §13.15.5
  (destructuring assignment).

### 1.55 Annex B built-ins and HTML-like comments

```js
String.prototype.trimLeft === String.prototype.trimStart;   // before: false   now: true
Object.getOwnPropertyDescriptor(RegExp, '$1').enumerable;   // before: true    now: false
/a/.compile(/b/, 'g');                                      // before: recompiled   now: TypeError
/* comment
*/ --> the rest of this line is a comment                   // before: SyntaxError   now: a comment
```

- The Annex B aliases and lengths of String (`trimLeft`/`trimRight`, `anchor`/`fontcolor`/`fontsize`/`link`); the
  RegExp legacy statics are non-enumerable and configurable and only read through RegExp itself; `setYear` applies
  the two-digit rule to ToIntegerOrInfinity(year); `compile` rejects subclass instances, instances of another realm
  and a RegExp with flags; HTML close comments after block comments.
- **Why:** ECMA-262 2026, B.2.2 (String), B.2.3.4 (setYear), B.2.4 (RegExp), B.1.1 (HTML-like comments).

### 1.56 `await` is an identifier in scripts

```js
var await = 1;                     // before: SyntaxError (reserved word)   now: a variable
```

- `await` is reserved only in async functions, modules and class static blocks. Async functions, arrows and
  methods (which stock Rhino did not have) only exist under `VERSION_ECMASCRIPT`; `async` stays an ordinary
  identifier elsewhere.
- **Why:** ECMA-262 2026, §13.1.1 (Static Semantics: Early Errors, `await`), §15.8 (async function definitions).

### 1.57 Element accesses and calls in an optional chain

```js
var a, o = {};
a?.b[0];          // before: TypeError (cannot read property "0" of undefined)   now: undefined
a?.b[f()];        // before: f is called   now: f is not called
o["k"]?.();       // before: TypeError (cannot find function k)   now: undefined
```

- A `[...]` access after `?.` in the same chain is short-circuited like a `.name` access; an optional call of an
  element (`o[k]?.()`) returns `undefined` when the element or its base is null or undefined.
- **Why:** ECMA-262 2026, §13.3.9 (Optional Chains).

### 1.58 Strict generators resumed from sloppy code

```js
var g = (function () { 'use strict'; return function* () { eval('var x = 010'); }; })();
g().next();       // before: no error (the eval was sloppy)   now: SyntaxError (the eval is strict)
```

- The body of a strict generator runs in strict mode whoever resumes it; before, it took the strictness of the code
  that called `next()`, which a direct `eval`, a write to a frozen object and `delete` of a non-configurable
  property in it followed (silent failures instead of TypeErrors).
- **Why:** ECMA-262 2026, §11.2.2 (strict mode code), §19.2.1.1 (PerformEval: the strictness of the caller).

### 1.59 Generators: yield in a finally run by return(), catch after a resumed finally

```js
function* g() { try { yield 1; } finally { yield 2; } }
var it = g(); it.next();
it.return(9);   // before (interpreted): TypeError "Yield from closing generator"   now: { value: 2, done: false }
it.next();      // before (compiled): { value: undefined, done: true }   now: { value: 9, done: true }
```

- A `finally` block run by `return()` can yield, and the return completes with its value when the generator is
  resumed. In compiled mode, a `catch` around a `try`/`finally` whose `finally` yielded catches the exception that
  the `finally` rethrows when the generator is resumed (before, it escaped the generator).
- **Why:** ECMA-262 2026, §27.5.3.3 (GeneratorResume), §14.15.3 (try statement evaluation).

### 1.60 RegExp flag getters and another realm's RegExp.prototype

```js
var other = /* the global object of another realm */;
Object.getOwnPropertyDescriptor(RegExp.prototype, 'global').get.call(other.RegExp.prototype);
// before: false   now: TypeError
```

- The getters of the flags and `source` answer `undefined` (`"(?:)"`) only for the RegExp.prototype of their own
  realm; another object that is not a RegExp is a TypeError of the getter's realm (see also 1.50).
- **Why:** ECMA-262 2026, §22.2.6.4.1 (RegExpHasFlag), §22.2.6.13 (get RegExp.prototype.source).

### 1.61 Array-likes longer than 2^31 - 1

```js
new Int8Array({ length: 2 ** 53 });        // before: java.lang.IllegalArgumentException   now: RangeError
f.apply(null, { length: 2 ** 32 });        // same
```

- An array-like whose length exceeds what a Java array holds is a RangeError, not a Java exception (also for
  `Context.getElements`).
- **Why:** ECMA-262 2026, §23.2.5.1.4 (InitializeTypedArrayFromArrayLike), §7.3.18 (CreateListFromArrayLike):
  the specification's limit is memory; a JavaScript error is what a script can handle.

### 1.62 The prototype of objects constructed for a newTarget whose `prototype` is not an object

```js
function nt() {} nt.prototype = null;
Object.getPrototypeOf(Reflect.construct(TypeError, [], nt));   // before: Error.prototype   now: TypeError.prototype
function F() {}
Object.getPrototypeOf(Reflect.construct(F, [], nt));           // before: F.prototype   now: Object.prototype
```

- When the `prototype` of newTarget is not an object, a native error gets the prototype of its own type (before,
  `Error.prototype`), an ordinary object gets `Object.prototype` (before, the constructor's `prototype`), and
  `WeakRef` and `FinalizationRegistry` honor newTarget; each takes the intrinsic of newTarget's realm.
- **Why:** ECMA-262 2026, §10.1.14 (GetPrototypeFromConstructor).

### 1.63 RegExp alternatives in a lookbehind, supplementary characters in an alternative class

```js
/(?<=x\d|y\d)z/.exec("y1z");      // before: null   now: ["z"]
/[\u{1F600}]|a/u.exec("\u{1F600}"); // before: null   now: ["\u{1F600}"]
```

- In a lookbehind, every alternative is matched backwards (before, the alternatives after the first were matched
  forwards, and the alternatives starting with a literal were preselected by their first character instead of
  their last one).
- With the `u` flag, an alternative that is a class matches a supplementary character (before, the alternation
  preselected on the first UTF-16 code unit, which the class does not contain).
- **Why:** ECMA-262 2026, §22.2.2.3 (Disjunction, with direction backward), §22.2.2.7.3 (CharacterSetMatcher,
  on code points with the `u` flag).

### 1.64 `Iterator` is the ES2025 Iterator, built-in iterators inherit `Iterator.prototype`

```js
Iterator(javaList).next();                     // before: the first element   now: TypeError
new Iterator();                                // before: TypeError           now: TypeError (abstract)
var p = Object.getPrototypeOf;
p(p([].values())) === Object.prototype;        // before: true                now: false
p(p([].values())) === Iterator.prototype;      // before: false               now: true
[].values().hasOwnProperty(Symbol.iterator);   // unchanged: false
p([].values()).hasOwnProperty(Symbol.iterator); // before: true               now: false (inherited)
```

- The global `Iterator` is the abstract constructor of ECMA-262 (`Iterator.from`, the iterator helpers on
  `Iterator.prototype`), instead of the `Iterator(obj [, keysOnly])` function of JavaScript 1.7, which iterated over
  the properties of an object or a Java `Iterator`/`Iterable`. `StopIteration` stays. To iterate over a Java
  collection, use `for-of` or `Iterator.from(javaIterable)`, which also gives it the helpers.
- The prototypes of the built-in iterators (Array, String, Map, Set, RegExp String, Java iterable and map
  iterators) and of generators inherit `%Iterator.prototype%` and its `@@iterator`, instead of `Object.prototype`
  (generators: the `Iterator.prototype` of JavaScript 1.7, with `next` and `__iterator__`); they no longer have an
  own `@@iterator`.
- **Why:** ECMA-262 2026, §27.1.3 (the Iterator constructor), §27.1.4 (%Iterator.prototype%), §23.1.5.2,
  §22.1.5.1, §24.1.5.1, §24.2.6.1, §22.2.9.1, §27.5.1 (the iterator prototypes inherit it).

### 1.65 `decodeURI` noncharacters, `Math.log2`, final sigma, `Proxy.revocable`

```js
decodeURI("%EF%BF%BE").charCodeAt(0);              // before: 0xFFFD   now: 0xFFFE
Math.log2(8);                                       // before: 2.9999999999999996   now: 3
"a\u{1D242}\u03A3".toLowerCase().at(-1);              // before: "σ"   now: "ς"
Proxy.revocable({}, {}).revoke.hasOwnProperty("prototype"); // before: true   now: false
```

- `decodeURI` and `decodeURIComponent` decode U+FFFE and U+FFFF to themselves, not to U+FFFD.
- `Math.log2` is exact for the powers of two.
- `toLowerCase` and `toLocaleLowerCase` apply the Final_Sigma condition over code points: a supplementary
  case-ignorable character (such as U+1D242) no longer hides the cased letter before a capital sigma.
- The revoke function of `Proxy.revocable` is not a constructor and has no `prototype`.
- **Why:** ECMA-262 2026, §19.2.6.5 (Decode), §21.3.2.23 (Math.log2, an implementation-approximated value that
  test262 requires exact for powers of two), §22.1.3.30 (toLowerCase, with SpecialCasing.txt), §28.2.2.1
  (Proxy.revocable, a built-in function that is not a constructor).

### 1.66 Functions have no `arity` property

```js
(function (a, b) {}).arity;                 // before: 2   now: undefined
Object.getOwnPropertyNames(function () {}); // before: length,name,arity,prototype   now: length,name,prototype
```

- The `arity` property of JavaScript 1.2, which repeated `length`, is not created on functions (strict functions
  already had none). Use `length` instead.
- **Why:** ECMA-262 2026, §10.2.9 and §20.2.4 (the own properties of function instances: `length`, `name`,
  `prototype`); test262 compares the own property names of classes. Decision 2026-10-14.

### 1.67 A yield expression in arrow parameters is an early error

```js
function* g() { (x = yield) => x; }   // before: compiles   now: SyntaxError
```

- In a generator, the parameters of an arrow function cannot contain a `yield` expression.
- **Why:** ECMA-262 2026, §15.3.1 (ArrowFunction early errors: ArrowParameters Contains YieldExpression).

### 1.68 Closures in parameter expressions do not see the `var` declarations of the body

```js
var x = "outside";
function f(g = () => x) { var x = "inside"; return g(); }
f();   // before: "inside"   now: "outside"
```

- A function with parameter expressions (defaults, destructuring) has a separate environment for the `var`
  declarations of its body: a closure created in the parameters resolves a name the body declares with `var` outside
  the function. The parameters do not see the `let`/`const` declarations of the body either: `function f(a = x)
  { let x; }` reads the outer `x` (before, the body's `x`, undefined), and `function g(a = arguments) { let
  arguments; }` gets the arguments object. Not applied when the body uses a direct `eval` or `with`, which keep the former behavior.
- **Why:** ECMA-262 2026, §10.2.11 (FunctionDeclarationInstantiation, step 28: a separate var environment when
  hasParameterExpressions is true).

### 1.69 Strict methods called on a primitive receive the primitive as `this`

```js
"use strict";
Boolean.prototype.f = function () { return typeof this; };
true.f();                                   // before: "object"   now: "boolean"
[1].flatMap(function () { return this; }, "s"); // before: [String object]   now: ["s"]
```

- A strict function called as a method of a primitive (`p.m()`, `p[k]()`) gets the primitive as `this`, as do
  the built-ins that take a primitive this: `Object.prototype.toLocaleString` (which calls `toString` on the
  primitive), the String methods that hand the string to a custom matcher (`match`, `matchAll`, `search`,
  `replace`, `replaceAll`, `split`), `isWellFormed`, `toWellFormed`, the RegExp `@@matchAll` and `@@split` methods
  and `Date.prototype[@@toPrimitive]` (also through `call`/`apply`, which now reject a non-object `this` where the
  spec requires one). `flatMap` passes its `thisArg` unchanged; `JSON.stringify` calls the `toJSON` of a BigInt with
  the bigint as `this`, and `Iterator.from` reads `@@iterator` of a string with the string as the receiver.
- A sloppy function still gets a wrapper object. Getters read on a primitive still get the wrapper.
- **Why:** ECMA-262 2026, §10.2.1.2 (OrdinaryCallBindThis: the this of a strict function is not converted),
  §7.3.3 (GetV), §7.3.22 (Invoke).

### 1.70 A data property of an object literal replaces an accessor of the same name

```js
"use strict";
({ get foo() { return 0; }, foo: 1 }).foo;   // before: TypeError (no setter)   now: 1
```

- In an object literal, a data property defines the property (CreateDataPropertyOrThrow) instead of assigning it,
  so it replaces a getter or setter defined before it in the same literal.
- **Why:** ECMA-262 2026, §13.2.5.5 (PropertyDefinitionEvaluation: CreateDataPropertyOrThrow).

### 1.71 `caller` and `arguments`: %ThrowTypeError% on `Function.prototype`, own properties of sloppy functions

```js
function f() { return f.arguments.length; }
f(1, 2);                                    // unchanged: 2
Object.getOwnPropertyNames(f);              // before: length,name,prototype   now: length,name,arguments,caller,prototype
Object.getOwnPropertyDescriptor(Function.prototype, "caller").get; // now %ThrowTypeError%
```

- `Function.prototype.caller` and `Function.prototype.arguments` are accessors whose getter and setter are the realm's
  %ThrowTypeError% (the same function as the `callee` accessor of a strict `arguments` object).
- A sloppy function (not a method, arrow, generator, async function or class) has own non-writable `arguments` (the
  arguments of the running call, as before) and `caller` (undefined: the caller is not tracked) properties; the
  others have neither, and reading
  them throws a TypeError.
- **Why:** ECMA-262 2026, §10.2.4 (AddRestrictedFunctionProperties), §17.1 (the own `caller` and `arguments` of a
  sloppy function are an allowed extension). Decision 2026-10-14.

### 1.72 `new ArrayBuffer` reads the prototype of newTarget before allocating

```js
Reflect.construct(ArrayBuffer, [7 * 2 ** 50], newTargetWithThrowingPrototypeGetter);
// before: RangeError   now: the error of the prototype getter
```

- **Why:** ECMA-262 2026, §25.1.3.1 (AllocateArrayBuffer: OrdinaryCreateFromConstructor before CreateByteDataBlock).

### 1.73 A generator's `return()` inside a destructuring pattern closes its iterator as a return

```js
function* g() { var a; [a = yield] = iterableWhoseReturnGivesNull; }
var it = g(); it.next();
it.return();   // before: { value: undefined, done: true }   now: TypeError (return() gave no object)
```

- When `return()` resumes a generator suspended inside an array destructuring pattern, the pattern's iterator is
  closed with a return completion: an error of its `return` method, or a result that is not an object, is thrown
  (before, `VERSION_ES6` does not close it at all).
- **Why:** ECMA-262 2026, §7.4.11 (IteratorClose), §14.3.3.6 and §13.15.5.5 (the iterator is closed with the
  completion of the pattern).

### 1.74 The discriminant of a `switch` and the parameter of a `catch` do not see the block's `let`/`const`

```js
let x = "outside";
switch (x) { case "outside": let x = 1; }        // before: ReferenceError (TDZ)   now: matches the case
try { throw []; } catch ([a = () => x]) { let x = "inside"; }  // a() before: "inside"   now: "outside"
```

- The discriminant of a `switch` is evaluated before the block of its cases, and the parameter of a `catch` (with
  its defaults) outside the block: neither sees the `let`/`const` declarations of the block. Not applied when the
  block uses a direct `eval` or `with`.
- **Why:** ECMA-262 2026, §14.12.4 (SwitchStatement evaluation), §14.15.2 (CatchClauseEvaluation).

### 1.75 Closures in the head of `for (let/const ... in/of ...)` see the loop bindings in their TDZ

```js
let x = "outside", f;
for (let x of (f = () => x, [1])) ;
f();   // before: "outside"   now: ReferenceError (x is in its temporal dead zone)
```

- The expression after `in`/`of` is evaluated where the loop's `let`/`const` bindings exist but are not
  initialized: a closure created there that reads them throws a ReferenceError (the parser points it to bindings of
  a block around the loop that are never initialized).
- **Why:** ECMA-262 2026, §14.7.5.6 (ForIn/OfHeadEvaluation, TDZnames).

### 1.76 `push` and `unshift` go through index properties of the prototypes

```js
Object.defineProperty(Array.prototype, "0", { set(v) { log.push(v); } });
[].push(1);   // before: the element is stored in the array   now: the setter is called, nothing is stored
```

- The fast path of `push` and `unshift` on a dense array writes the elements directly only when no prototype has a
  property at the indexes written; otherwise each element is set as the spec says (a setter of a prototype is
  called, a read-only one fails), and a length that cannot be set then is a TypeError.
- **Why:** ECMA-262 2026, §23.1.3.23 and §23.1.3.37 (Set(O, Pk, E, true)).

### 1.77 An object pattern evaluates a property target before reading the source property

```js
({ [key()]: target()[targetKey()] } = source());
// before: source, key, get, target, targetKey, set   now: source, key, target, targetKey, get, set
```

- In a destructuring assignment, the object and the key expression of a property or element target (`o.p`,
  `o[k]`, `this.#f`) are evaluated before the property of the source is read; a computed key of the pattern is
  converted to a property key right after it is evaluated.
- **Why:** ECMA-262 2026, §13.15.5.6 (KeyedDestructuringAssignmentEvaluation).

### 1.78 Proxy and String object internal methods in `toString`, `defineProperties`, `ownKeys` and index definition

```js
var h = Proxy.revocable([], { get() { h.revoke(); } });
Object.prototype.toString.call(h.proxy);    // before: TypeError (revoked)   now: "[object Array]"
Object.defineProperty(new String("str"), "0", { value: "x" });
                                            // before: no error              now: TypeError
Reflect.ownKeys(new Proxy(new Proxy([], { ownKeys: () => ["b", "a"] }), {}));
                                            // before: integer keys first    now: the trap's order
```

- `Object.prototype.toString` checks IsArray before reading `@@toStringTag`.
- `Object.defineProperties` lists the keys of the properties object through [[OwnPropertyKeys]] (symbols
  included) and keeps the enumerable ones through [[GetOwnProperty]], so the proxy traps are called in order.
- `Reflect.ownKeys` of a proxy returns the keys in the order of its [[OwnPropertyKeys]].
- Defining an index of a String object (below its length) with an incompatible descriptor fails: a TypeError for
  `Object.defineProperty`, `false` for `Reflect.defineProperty`.
- **Why:** ECMA-262 2026, §20.1.3.6, §20.1.2.3.1, §28.1.11, §10.4.3.2.

### 1.79 Generator declarations in blocks are not hoisted to the function (sloppy mode)

```js
{ function* g() {} }
typeof g;   // before: "function"   now: "undefined"
```

- The web-compatibility hoisting of a function declared in a block (or a `switch` case) to the enclosing function
  applies to plain functions only; a generator declaration stays scoped to its block, like an async function.
- **Why:** ECMA-262 2026, §B.3.2.1 (only a FunctionDeclaration is a candidate).

### 1.80 `"NaN"` is a numeric key of a TypedArray

```js
var ta = new Int32Array(2);
ta.NaN = 3;
ta.NaN;     // before: 3   now: undefined
```

- `"NaN"` is a canonical numeric string, as `"Infinity"` or `"1.5"` already were: a TypedArray has no such
  property, setting it does nothing and defining it fails.
- **Why:** ECMA-262 2026, §7.1.21 (CanonicalNumericIndexString), §10.4.5.

### 1.81 Accessors read or written on a primitive receive the primitive

```js
Object.defineProperty(Number.prototype, "x", {
  get() { "use strict"; return typeof this; },
  set(v) { "use strict"; log.push(typeof this); }
});
(5).x;                                // before: "object"      now: "number"
(function () { "use strict"; (5).x = 1; })();
                                      // before: TypeError     now: the setter runs, logs "number"
(function () { "use strict"; (5).y = 1; })();
                                      // TypeError, as before (no setter)
```

- Reading a property of a primitive passes the primitive as `this` to a strict getter (a sloppy one still gets
  the wrapper object). Assigning to a property of a primitive goes through the prototypes' [[Set]] with the
  primitive as receiver: a setter or a proxy `set` trap is called, and strict code gets a TypeError only when the
  assignment fails.
- **Why:** ECMA-262 2026, §6.2.5.5 (GetValue), §6.2.5.6 (PutValue), §10.1.9.2 (OrdinarySet).

### 1.82 `super[key]++` evaluates the key once and converts the value with ToNumeric

```js
var proto = { p: 1, q: "5" };
var obj = { __proto__: proto, m() { return [super[k()]++, ++super.q]; } };
obj.m();    // before: k() called twice, ++super.q is "51" (interpreted mode)   now: k() once, 6
```

- The key of `super[key]++` (and `--`, prefix or postfix) is evaluated and converted once; the old value is
  converted with ToNumeric, so a string is a number and a BigInt stays a BigInt, in both modes.
- **Why:** ECMA-262 2026, §13.4.2.1 (Postfix Increment), §13.4.4.1 (Prefix Increment).

### 1.83 With parameter expressions, function declarations are instantiated after the parameters

```js
function f(x = arguments) { function arguments() {} return typeof x; }
f();        // before: "function"   now: "object"
var g = (a = () => h()) => { function h() { return "inner"; } return a(); };
g();        // before: "inner"      now: the h of the enclosing scope (a ReferenceError if none)
```

- In a function with default values or destructuring patterns in its parameters, the function declarations of the
  body are created once the parameters are initialized, so the parameters see the `arguments` object rather than
  a `function arguments` of the body. In an arrow function, a function declaration of the body that a parameter
  names is bound in the body only, like its `var` declarations.
- **Why:** ECMA-262 2026, §10.2.11 (FunctionDeclarationInstantiation, steps 27-36).

### 1.84 A compound assignment in `with` resolves the name once

```js
with (proxy) { p += 1; }
// before: has, get @@unscopables, has, get @@unscopables, has, get p, has, set p
// now:    has, get @@unscopables, has, get p, has, set p
```

- `x op= v` inside `with` reads the value from the object the reference resolved to, without checking
  `Symbol.unscopables` again; visible through the traps of a proxy.
- **Why:** ECMA-262 2026, §13.15.2 (AssignmentExpression evaluation), §9.1.1.2.6 (GetBindingValue).

### 1.85 The `flags` getter of `RegExp.prototype` rejects a primitive `this`

```js
Object.getOwnPropertyDescriptor(RegExp.prototype, "flags").get.call(4);
// before: ""   now: TypeError
```

- **Why:** ECMA-262 2026, §22.2.6.4 (get RegExp.prototype.flags, step 2).

### 1.86 Typed arrays use the intrinsic constructors, not the global properties

```js
var I = Int16Array, a = new I([3, 1, 2]);
ArrayBuffer = null; Int16Array = null;
new I(2);        // before: InternalError ("ArrayBuffer" is not a constructor)   now: Int16Array [0, 0]
a.toReversed();  // before: InternalError ("Int16Array" is not a constructor)    now: Int16Array [2, 1, 3]
```

- The buffer of a new typed array, and the result of `toReversed`, `toSorted` and `with`, were created through the
  global property of that name.
- **Why:** ECMA-262 2026, §23.2.5.1.6 (AllocateTypedArrayBuffer: %ArrayBuffer%), §23.2.4.3
  (TypedArrayCreateSameType: the intrinsic constructor of the same type).

### 1.87 Iterator results define own data properties

```js
Object.defineProperty(Object.prototype, "value", { set(v) {}, get() { return "g"; } });
[...[1, 2]];   // before: ["g", "g"] (the setter received 1 and 2)   now: [1, 2]
```

- The `{ value, done }` objects of the built-in iterators, generators and iterator helpers were filled with
  `putProperty`, which calls setters inherited from `Object.prototype`, and were created from the global `Object`.
- **Why:** ECMA-262 2026, §7.4.14 (CreateIteratorResultObject: CreateDataPropertyOrThrow on
  OrdinaryObjectCreate(%Object.prototype%)).

### 1.88 Code evaluated at runtime no longer sees the locals of functions with closures

```js
function f() {
  var c = 1, w = 1;
  var g = function () { return c; };   // a closure: f has an activation object
  Script("c = 5; w = 2")();            // runs in the scope of its caller
  return [g(), w];
}
f();   // before: [5, 2]   now: [5, 1]
```

- A function that contains a closure or a `catch` clause has an activation object (`NativeCall`); its variables
  were all properties of it, so code run in its scope at runtime could read and write them: a legacy `Script`
  object called as a function, or a host function that evaluates code in the scope it is called with. Now only the
  variables that a nested function refers to are in the activation object; the others live in registers, as do all
  the variables of a function without closures (which such code never saw). A direct `eval` or a `with` statement
  anywhere in the function, `arguments`, and generators keep every variable in the activation object.
- **Why:** ECMA-262 2026, §10.2.11 (FunctionDeclarationInstantiation): only a direct eval (§19.2.1.1) can reach the
  variables of a function from code evaluated at runtime.

---

## 2. Platform and embedding

### 2.1 Packages and Maven coordinates renamed

- Java packages `org.mozilla.*` became `org.monflabs.mozilla.*`; Java module names `org.mozilla.rhino*` became
  `org.monflabs.mozilla.rhino*`, and the OSGi bundle symbolic names likewise; the Maven group id `org.mozilla`
  became `org.monflabs.mozilla`. This lets the fork and stock Rhino coexist on a class path or module path.
- **Migration:** replace `org.mozilla.` with `org.monflabs.mozilla.` in imports, `Packages.org.mozilla…` script
  references, `META-INF/services` files and dependency declarations.

### 2.2 Java 21 minimum; Android not supported

- The engine is compiled for Java 21 (stock Rhino 1.9.0: Java 17); the OSGi bundles require JavaSE 21. The minimum
  will move to Java 25 when it becomes available to us (expected early 2027).
- This fork targets Java server applications: Android and micro JVMs are not supported (the Android integration
  tests were removed).

### 2.3 `VERSION_ECMASCRIPT` is the default language version

- A new `Context` (and a new `CompilerEnvirons`) uses `VERSION_ECMASCRIPT` (stock Rhino 1.9.0: `VERSION_ES6`), and so do
  the shell, the debugger and the `javax.script` engine (whose `LANGUAGE_VERSION` parameter is now `"250"`). Every
  change of section 1 therefore applies to an embedding that does not set a version.
- In the `javax.script` engine, a script's global `let`, `const` and `class` declarations are visible to the scripts
  evaluated after it with the same bindings (as in one global environment), and are not stored in the bindings; a
  global function declaration is stored in the bindings, as before. Evaluating the same top-level `let` twice with
  the same bindings throws a SyntaxError (redeclaration).
- **Migration:** call `cx.setLanguageVersion(Context.VERSION_ES6)` (for example in `ContextFactory.onContextCreated`),
  run the shell with `-version 200`, or put `ScriptEngine.LANGUAGE_VERSION` = `200` in the engine scope of
  the `javax.script` engine.
