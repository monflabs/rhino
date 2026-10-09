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
```

- Sloppy eval code may not declare a `var` or function whose name is a `let`/`const` of a block between the eval
  and its variable scope, or (when its variable scope is the global one) a global lexical declaration. A catch
  parameter or a `with` object does not count.
- When eval code declares global functions and vars, it checks them all before creating any: a function whose name
  is a non-configurable global that is read-only or non-enumerable (`NaN`, `undefined`, …), or a new name on a
  non-extensible global object, throws a TypeError and declares nothing (before, the functions preceding it were
  created). A function replacing a configurable global property redefines it as writable, enumerable and
  configurable (before, a read-only one was kept, or threw in strict mode).
- **Why:** ECMA-262 2026, §19.2.1.3 EvalDeclarationInstantiation steps 3 and 10–11, §9.1.1.4.16
  CanDeclareGlobalFunction and §9.1.1.4.18 CreateGlobalFunctionBinding.
- **Not covered yet:** a function's own top-level `let` (`function f() { let x; eval("var x"); }`).

---

## 2. Changes that also apply to `VERSION_ES6`

These fix behavior that contradicted the ECMAScript specification and every other engine; they apply to every
version from `VERSION_ES6`. Code compiled for a version before `VERSION_ES6` (JavaScript 1.x) keeps the old behavior
unless stated otherwise.

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
  evaluated (§14.3.1). A script's top-level `let x;` is unchanged under `VERSION_ES6`.

### 2.6 `this` of strict functions

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

```js
var t = Object.defineProperty(function () {}, "name", { value: "target" });
t.bind().name;                                   // before: "bound "   now: "bound target"
(function () { "use strict"; return function () { return this; }.bind()(); })();   // before: null   now: undefined
```

- **Why:** §20.2.3.2 Function.prototype.bind reads the target's `name` property when binding, and a bound `this` of
  `undefined` stays `undefined` for a strict target.

### 2.9 The `arguments` object

```js
function f(x) { Object.defineProperty(arguments, "0", { configurable: false }); x = 7; return arguments[0]; }
f(1);   // before: the property became non-writable and non-enumerable, and 1   now: 7 (still mapped)
function g(x, ...rest) { x = 2; return arguments[0]; }
g(1);   // before: 2   now: 1 (a rest parameter makes arguments unmapped)
```

- `Object.defineProperty` on an index keeps the attributes it does not mention; making it non-writable or an
  accessor, or deleting it, ends its link with the parameter (before, some of these threw "Cannot change the value
  of attribute" or kept the link). Functions with a rest parameter, like those with default values, get an unmapped
  `arguments` object.
- **Why:** ECMA-262 2026, §10.4.4 Arguments Exotic Objects and §10.2.11 FunctionDeclarationInstantiation step 18.
- **Not covered yet:** destructured parameters (plan 1.3) still produce a mapped `arguments` object.

### 2.10 Early errors of parameter lists

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

```js
function f(a = eval("var a = 1")) {}
f();                                 // before: fine   now: SyntaxError (also "var arguments")
"use strict";
(0, eval)("var static = 1");         // before: SyntaxError (strict)   now: fine, a global var
```

- A sloppy direct eval in a parameter list may not declare a `var` or function named like a parameter or
  `arguments`: they share the parameters' environment.
- An indirect eval is never strict mode code, even when called from strict code (before, it inherited the caller's
  strictness, and its `var`s did not reach the global object).
- **Why:** ECMA-262 2026, §10.2.11 FunctionDeclarationInstantiation step 20, §19.2.1.3 EvalDeclarationInstantiation
  step 3 and §19.2.1.1 PerformEval steps 12–13.

### 2.13 Array destructuring follows the iterator protocol

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

```js
for (var x of iterable) break;   // before: iterable's iterator was left open   now: its return() is called
```

- A `for-of` loop left before its iterator is done (`break`, `continue` to an outer loop, `return`, a throw, a
  generator's `return()`) calls the iterator's `return()`. After a jump, an error of `return()` (or a result that
  is not an object) propagates; after a throw, the throw wins. `next` is read once, when the loop starts, and a
  result of `next()` that is not an object is a TypeError (before, it was converted to an object).
- **Why:** ECMA-262 2026, §14.7.5.7 ForIn/OfBodyEvaluation and §7.4.11 IteratorClose.

### 2.16 Early errors of object literals and functions

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
- **Why:** ECMA-262 2026, §15.4.1 (MethodDefinition early errors), §15.1.1 (FormalParameters), §B.3.1 (duplicate
  `__proto__`), §12.7.2 (escaped keywords), §11.2.2 (directive prologues).

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
