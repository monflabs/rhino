# Optimizations

This document describes the performance work of this fork of Rhino: each optimization added to the engine, the
mechanism behind it, the conditions under which it applies, how it stays correct when a script changes what it relied
on, and what it gained. It also records the experiments that measured no gain, so that they are not tried again.
[CHANGELOG_MONFLABS.md](CHANGELOG_MONFLABS.md) has the history and [MONFLABS_PLAN.md](MONFLABS_PLAN.md) the plan
(milestones M14 and M16).

## Principles

- **No observable change.** An optimization keeps the behavior of the engine, including the order of side effects,
  getters and setters, proxies and Java interop. When it applies only under some conditions, it checks them and falls
  back to the general path otherwise. An optimization with no observable change is not gated by the language
  version: it applies to every version it can serve.
- **Optimizations under `VERSION_ECMASCRIPT` only.** Some rely on semantics that only `VERSION_ECMASCRIPT` has (for
  example variables that code evaluated at runtime may no longer see, `BACKWARD_COMPATIBILITY.md` 1.88). They are
  marked as such below.
- **Both backends.** Each optimization is done in compiled mode (`optimizer/Codegen`, `BodyCodegen`,
  `DefaultLinker`) and in the interpreter (`CodeGenerator`, `Interpreter`), unless it is noted otherwise.
- **Measured.** Every change runs the perf gate (`buildtools/perf-gate.sh`, see
  [benchmarks/README.md](benchmarks/README.md)) against the previous commit: 116 metrics, interpreted and compiled,
  under `VERSION_ES6` and `VERSION_ECMASCRIPT`, each with its own tolerance. A change that regresses a metric beyond
  its tolerance is reworked or dropped.

## Measuring

- **The perf gate** runs the base and the working tree in interleaved rounds with the same harness; only the engine jar
  differs. It takes about 18 minutes and needs an idle machine: no build, and no Time Machine backup (the gate refuses
  to run during one).
- **Bimodal metrics.** `properties.cmp`, `v8-richards.cmp`, `closures.cmp`, `wideobject.cmp`, `typedarray.cmp` and
  `split` settle in one of two JIT states from one JVM to the next, and `v8-splay.cmp` (about 1 ms) varies by ±30%:
  a single round can flag a regression that is not there. `typedarray.cmp` (8.6 ms or 13.5 ms in the full run, the
  same alone) has a 60% tolerance since M16 for that reason; run it alone to see a real change. Confirm with
  several rounds, alternating the two jars.
- **Profile pollution.** The gate runs all benchmarks in one JVM, so what ran before changes the JIT profile of what
  runs next. A change can look neutral on a single benchmark and regress in the full run (see the prototype-chain
  cache below): measure both ways.
- **Micro-benchmarks** for a single pattern: warm up, keep the best of several runs, and run each language version in
  its own JVM, since the second one in a JVM inherits the profile of the first.
- **Method size.** A runtime method called from compiled scripts (through invokedynamic) must stay small enough for
  the JIT to inline it: adding a rare case to such a method can slow down the common one. Put the rare case in a
  method of its own (see the global `let` writes below).
- **Profiles** with JFR (`-XX:StartFlightRecording=settings=profile`). Its attribution to inlined frames can mislead:
  a hotspot removed from the profile does not always shorten the run (see the regexp experiment below). Trust the
  timing, not the profile.

## Optimizations in the engine

### Cache of top-level names

- **What.** A name of top-level code (`x`, `Math`, `f()`) is resolved through a per-site `GlobalNameCache`: the slot
  of the global object, or of the global `let`/`const` declarations, in which the name was found. The next accesses
  read or write that slot directly while the lookup would still find it.
- **Where.** Compiled code links these sites in the `GNAME` namespace (`GNAME:GET`, `BIND`, `SET`, `GETWITHTHIS`,
  and `NAME:INCRDECR`) in `DefaultLinker`; the interpreter keeps one cache per name of each `InterpreterData`
  (`nameCache`), used when the scope is the top scope. It also serves the functions of the script that keep all their
  variables in registers, whose names are then always global.
- **Invalidation.** A slot that leaves its map (removed, replaced, or copied into a larger map) is marked
  `Slot.stale`, which the cache checks. A global `let`/`const` that shadows a property of the global object is seen
  through the size of the top scope, which only grows. The cache holds the scope and the slot weakly.
- **Writes of global `let` bindings** (M16, 16.1) go to the cached slot too, when the binding is initialized, its
  attributes are those of a `let` (not a `const`) and the top scope is not sealed. Otherwise the lookup runs and
  throws the TDZ or `const` error. Before, every write took the lookup: 3 million `G++` from a function cost 140 ms
  against 12 ms now (compiled), 325 ms against 135 ms (interpreted). The `let` paths are separate methods: written
  inline, they grew `nameIncrDecr` from 143 to 207 bytes of bytecode, and the global `var` writes of `typedarray`
  became 10% slower (28–36% in the full gate run), as the larger methods no longer inlined as well into the
  compiled scripts.
- **Gain.** The `testsrc/perf` scripts spend most of their time on global names. Against the previous commit:
  `typedarray` −42% to −66%, `toprimitive` −31% to −50%, `strbuild` −34% to −49%, `instanceof` −31% to −44%,
  `protochain` −28% to −53%, `arith` −30% to −35%. It also removed a slowdown of 10–25% that only `VERSION_ECMASCRIPT`
  had on top-level code. All versions.

### Lazily loaded built-ins stay lazy

- **What.** `TopLevel.cacheBuiltins` no longer builds every lazily loaded constructor (`Map`, `Set`, `Promise`,
  `BigInt`, `Proxy`, typed arrays, `ArrayBuffer`, `DataView`, ...) when a scope is initialized. The cache keeps the
  `LazilyLoadedCtor` and builds the constructor on first use.
- **Correctness.** If the script has replaced or deleted the global property by then, the constructor is built in a
  slot of its own and the property is put back as the script left it, without calling a setter. Isolates share the
  lazy entries of their parent.
- **Gain.** The cache built about 45% of the initialization: `startup.50scopes` −33% (`VERSION_ECMASCRIPT`) and −42%
  (`VERSION_ES6`). All versions.

### Built-in iterators stepped without result objects

- **What.** The consumers of iterables in the engine (spread, `Array.from`, the `Map`/`Set`/`WeakMap`/`WeakSet`
  constructors, `Promise.all`, `for-of`, destructuring) step a built-in iterator whose `next` is the original one
  (`ES6Iterator.isNextMethod`) through `ES6Iterator.step`, without calling `next` and without creating
  `{ value, done }` objects.
- **Why it is correct.** It relies on the result objects being unobservable: `makeIteratorResult` creates ordinary
  objects of the intrinsic `%Object.prototype%` with own data properties, as the specification does
  (`BACKWARD_COMPATIBILITY.md` 1.87; it used to call setters inherited from `Object.prototype`). An iterator whose
  `next` was replaced takes the general path.
- **Gain.** Spreading an 8-element array: 170 ms → 40 ms compiled, 184 ms → 50 ms interpreted (200,000 spreads).
  `VERSION_ECMASCRIPT`.

### Spread and iterator records without iterator objects

- **What.** An `IteratorRecord` (`for-of`, destructuring, spread) reads an array whose iteration methods are the
  original ones by index, without creating an iterator, and steps the other built-in iterators (`Map`, `Set`,
  `String`, ...) through `ES6Iterator.step`.
- **Gain.** Spreading an 8-element array: 40 ms → 19 ms compiled, 50 ms → 38 ms interpreted; `for (const [k, v] of
  map)` 39 ms → 19 ms; `f(...args)` 63 ms → 50 ms (compiled). `VERSION_ECMASCRIPT`.

### Variables in registers in functions with closures

- **What.** A function that needs an activation object (a closure, a `catch` clause, `super`, ...) used to keep all
  its parameters and variables in it, where each access was a name lookup. `NodeTransformer.keepVarsInRegisters` now
  leaves in the activation only the names that a nested function refers to, plus the catch parameters, function
  declarations, `let`/`const` and class bindings. The other `var`s and parameters become registers
  (`FunctionNode.isVarInRegister`), and `NativeCall` no longer defines them (`JSDescriptor.isParamOrVarInRegister`),
  which saved a property creation per variable and per call.
- **When it does not apply.** A direct `eval` or `with` in the function or in a nested function, a use of
  `arguments`, and generators keep every variable in the activation.
- **Semantics.** Code evaluated at runtime in the function's scope other than a direct eval (a legacy `Script`
  object, a host function) no longer sees the variables in registers. Hence `VERSION_ECMASCRIPT` only
  (`BACKWARD_COMPATIBILITY.md` 1.88).
- **Gain.** A loop in a function with a `catch` clause: 156 ms → 52 ms compiled. With a closure over one variable:
  171 ms → 115 ms compiled, 890 ms → 760 ms interpreted. A function with a `catch` clause and 7 variables, called
  3 million times: 592 ms → 87 ms compiled, 1,295 ms → 866 ms interpreted.

### Captured variables at a fixed position of the activation

- **What.** In a function with an activation object, the reads and writes of its own variables that nested functions
  capture go to their slot in the activation (`Node.ACTIVATION_VAR_PROP`, `NativeCall.getVar`/`setVar`) instead of a
  name lookup. The activation keeps its slots by index when it is created and looks them up again when one is stale.
  Compiled code keeps the activation in a local; the interpreter has `Icode.ACTIVATION_GETVAR`/`ACTIVATION_SETVAR`.
- **Gain.** A loop updating a captured variable: 143 ms → 65 ms compiled, 767 ms → 635 ms interpreted, close to the
  same loop without a closure (58 ms). `VERSION_ECMASCRIPT`.

### Nested functions read captured variables at their position

- **What.** A nested function reads and writes a variable of an enclosing function at its slot in that function's
  activation (`Node.OUTER_VAR_PROP`: how many functions out, and the index; `ScriptRuntime.getOuterVar`/
  `setOuterVar`), instead of walking the scope chain by name. This is resolved at compile time: `NodeTransformer`
  hands the slot variables of a function down to its nested functions (`FunctionNode.getOuterVars`), limited to those
  that no scope inside the function declares.
- **Correctness.** At runtime the owner's activation is the first `NativeCall` on the scope chain whose function has
  the owner's descriptor; the variable is checked by name, and looked up by name if anything does not match. The
  interpreter reuses `ACTIVATION_GETVAR`/`ACTIVATION_SETVAR` (the depth in the high byte of the index), since no
  icode values are left.
- **Gain.** A closure reading and writing captured variables in a loop: 202 ms → 50 ms compiled, 571 ms → 517 ms
  interpreted. `VERSION_ECMASCRIPT`.

### Per-site cache of properties found on the prototype chain

- **What.** A named property get or method lookup (`o.p`, `o.m()`) has a `PropertyCache`. For a receiver of the
  cached class, it looks the name up in the receiver only. An own property is read directly; otherwise the cached
  slot of the prototype chain is used when the receiver's prototype is the cached one, the slot is not stale and no
  object of the chain changed since.
- **Where.** In compiled code, one cache per call site, bound by `DefaultLinker` with the name as a constant and
  guarded for `ScriptableObject` receivers. It is linked only at sites whose first receiver inherits the property:
  the sites that read own properties (`this.x`) keep the direct path. In the interpreter, one cache per name of the
  code (`InterpreterData.propCaches`).
- **Invalidation.** The objects of a cached chain are marked (`SlotMapOwner.cachedPrototype`). Adding or removing
  one of their properties (`EmbeddedSlotMap`, `HashSlotMap`), replacing their map (`setMap`) or changing their
  prototype (`setPrototype`) increments a global epoch that every cache checks.
- **Limits.** Only classes that look named properties up in their slot map (`get(String, Scriptable)` and
  `getPrototype` declared by `ScriptableObject`) and maps of the plain kinds are cached, not the thread-safe maps.
  The cache is one immutable entry, so a read from another thread never sees half of an update. A site stops
  refilling it after 64 attempts (megamorphic sites).
- **Lessons.** Linked at every site, the cache made own-property sites slower in the full gate run (richards about
  2× slower, from code size and profile pollution), and primitive receivers (`"s".length`) slower too. Hence the
  guard and the inherited-only linking.
- **Gain.** 3 million accesses three levels up, compiled: a method 66 ms → 41–54 ms, a property 45 ms → 18–23 ms;
  `protochain` −28% compiled, −13% interpreted. All versions.

### Per-iteration bindings that cost nothing without closures

- **What.** A `let`/`const` loop head gets a fresh binding per iteration (a `COPY_SCOPE` of the loop's scope object),
  so that each closure sees its own iteration's value. Loops whose bindings live in registers do not pay for it.
- **Only where a closure can capture them** (M16, 16.2): when the bindings are in a scope object (top-level code, or
  a function with a closure elsewhere), `IRFactory` emits `COPY_SCOPE` only if the loop's head or body creates a
  function or a class or calls `eval` directly (`mayCapture`). Otherwise no code can observe that the iterations
  share one scope, and the copy (an allocation and a fresh map per iteration) is skipped. A closure created in the
  initializer counts too: it must keep seeing the scope of the first iteration.
- **Gain.** 3 million iterations of a top-level `let` loop: 271 ms → 180 ms compiled, 441 ms → 360 ms interpreted;
  `for (const v of a)` in a function with a closure: 92 ms → 67 ms. A `let` loop in a function with a closure is
  bimodal in compiled mode (53 ms in most JVMs, about 105 ms in some, against 80 ms before); 16.11 removes the scope
  object of such loops altogether. `VERSION_ECMASCRIPT`.

### Object literals put their properties directly

- **What** (M16, 16.3). Under `VERSION_ECMASCRIPT` the properties of an object literal are created with the
  CreateDataPropertyOrThrow semantics, so that a data property replaces an accessor defined earlier in the same
  literal (`{get a() {}, a: 1}`): `ScriptRuntime.fillObjectLiteral` went through `defineOwnProperty` with a
  descriptor, a compound operation and two lookups for every property. A plain `NativeObject` whose literal
  defines no getter or setter (checked once per literal from its `getterSetters`) puts its properties directly,
  as `VERSION_ES6` does: the object is new, so each name is either absent or a data property of the same literal,
  and putting the value gives the same result.
- **Lesson.** A first version looked each name up to check for an accessor, then put the value: two lookups
  per property made a 4-property literal 12% slower. The check is per literal, not per property.
- **Gain.** 1 million literals, compiled: `{a, b, c, d}` 118 ms → 102 ms, `{x, y}` 61 ms → 44 ms, a nested literal
  49 ms → 36 ms; interpreted 10–15% faster. `VERSION_ECMASCRIPT`.

### Algorithms of built-ins

- **`Math.sumPrecise`** uses Shewchuk's algorithm (non-overlapping partials, with the overflow handling of the
  proposal's polyfill) rather than an exact `BigDecimal` sum, Nashorn's choice: the same results on 300,000 random
  and edge cases, 18 times faster on usual values (8 ms against 145 ms for a million values) and 2.7 times faster on
  values spread over the whole exponent range. `VERSION_ECMASCRIPT`.
- **Dense fast paths** of the array methods write the elements of a dense array directly. Under
  `VERSION_ECMASCRIPT`, those of `push` and `unshift` are taken only when no prototype has a property at the indexes
  written (`BACKWARD_COMPATIBILITY.md` 1.76), which costs a lookup per pushed element; M16 (16.15) caches that check.

## Tried and dropped

| Experiment | Result | Why |
|---|---|---|
| Skip the non-BMP code point list for BMP characters in regexp classes | level over 12 runs | JFR showed `ArrayList.contains` at 6–20%; the samples moved to the matcher's other frames |
| `Number` fast path at the start of `ScriptRuntime.toNumeric` | level | the JIT already folds `toPrimitive` for numbers |
| A BMP fast path in the regexp matcher | +2% | below noise; `v8-regexp` +5% interpreted comes from the matcher's growth, with no single hotspot |
| Prototype-chain cache at every get site | richards 2× slower in the full run | code size and profile pollution at own-property sites; linked at inherited sites only |
| Devirtualizing `EmbeddedSlotMap.query` (exact-class check) | level | the cost is the chain of dependent loads in the lookup |
| A per-site cache of own slots by map | not worth it | maps belong to objects, not to shared shapes: a site only hits for a single object |
| Hash mixing of integral `Double` keys in `Map`/`Set` | slower | loses the locality of sequential keys |
| Hidden classes (shared shapes) | not attempted | the largest remaining compiled cost (own-property lookups, about 15%), but a rewrite of the object model |

## Second pass (M16)

A code analysis (2026-10-19) of the call path, the object model, the code generated under `VERSION_ECMASCRIPT` and
the built-ins found the items listed in `MONFLABS_PLAN.md` (M16). Each item is documented here when it is done.
