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
- **Reads from nested functions** (M16, 16.14). The reads of a name in other code (`NAME:GET` and
  `NAME:GETWITHTHIS`, and the interpreter's `NAME` and `NAMEANDTHIS` when the scope is not the top scope) walked
  the scope chain by name, then looked the name up in the top scope and the global object. They now get a cache
  too: `GlobalNameCache.nestedName` walks up the activations and block scopes (`NativeCall`, `LocalScope`,
  `CatchScope`) and checks that none of them has the name, one query of a small map each, then reads the cached
  slot of the top scope; a name that one of them has is read from its slot, as the lookup does. Any other scope
  on the way (`with`, a scope of the embedding) takes the lookup. Since every scope on the way is still checked,
  a name that `eval` or code run in the caller's scope adds to an activation is found as before. (The first
  version looked a name found in an activation up again: the variables that functions with closures read by
  name before `VERSION_ECMASCRIPT` cost 452 → 610 ms compiled for 6 million reads, back to 480 ms since.) Gain: a function nested in a wrapper reading `Math.abs` 3 million times, 205 →
  88 ms compiled (`VERSION_ECMASCRIPT`, the other name captured), 266 → 209 ms when it also reads a global
  (`VERSION_ES6` 268 → 232 ms), interpreted 5–10%; SunSpider in one JVM, compiled (every file wrapped in a
  function): 21,744 → 20,939 ms, `access-nbody` 3,151 → 2,632 ms, `math-partial-sums` 1,710 → 1,506 ms,
  `string-validate-input` 490 → 417 ms; `access-fannkuch` 397 → 463 ms in that sequence, which reads no name in
  its loops (the profile of the files before it changes how C2 compiles its array writes). All versions.

### Function objects created with their properties laid out

- **What** (M16, 16.12). Creating a function installed its own properties step by step: a map with `length` and
  `name` (`BaseFunction.createProperties`), their attributes set again by name (`setStandardPropertyAttributes`),
  a second map with `arguments` and `caller` for a sloppy function under `VERSION_ECMASCRIPT`, then the
  `prototype` property through a compound operation that copied the small map into a hash table, and the
  prototype object with its `constructor` property. A `JSFunction` now gets its final map at once
  (`BaseFunction.functionProperties`, `es6FunctionProperties`): the same properties, attributes and order, built
  with the `Context` the constructor gets instead of looking it up. A layout with a `prototype` property is
  built as a hash table sized for it (`SlotMapDescriptor.Builder.hashed`), the kind of map these functions had.
  The prototype object is created when the `prototype` property is first read
  (`BaseFunction.createDefaultPrototype`).
- **When it applies.** The layout at once: functions created under `VERSION_ECMASCRIPT`, and by code that is not
  strict under `VERSION_ES6`; async functions and the other cases install their properties as before (strict
  code before `VERSION_ECMASCRIPT` gives a function an `arity` property of its own kind). The lazy prototype
  object: every `JSFunction` that is not a generator, in a scope with the intrinsic %Object.prototype%.
- **Correctness.** The prototype object is the one that would have been created: an ordinary object whose
  prototype is the intrinsic %Object.prototype% of the function's scope and whose `constructor` is the function,
  created once (under the function's lock) whoever reads it first. A function serialized before its prototype is
  read gets it first (`writeObject`). The own properties of functions of every kind, in every version, are the
  same as before (`FunctionObjectsTest`).
- **Map kind.** A first version kept the five slots of a sloppy function in an `ImmutableSmallSlotMap` extended
  to five: creation 167 → 124 ms, but `instanceof` became 15–20% slower in the full gate run (the function is
  looked up for `Symbol.hasInstance`, then `prototype`, and the property reads saw one more kind of map), also with
  the hash of each slot kept in the map. As a hash table, `instanceof` is level.
- **Gain.** 1 million closures created, compiled: `VERSION_ECMASCRIPT` 355 → 167 ms (Rhino 1.8.0: 113 ms),
  `VERSION_ES6` 505 → 187 ms; a callback passed to `map` 553 → 257 ms and 729 → 295 ms (Rhino 1.8.0: 232 ms); a
  nested declaration returned by each call 569 → 349 ms and 522 → 306 ms. Interpreted: 467 → 344 ms and 751 →
  393 ms. Gate: `closures` −28% compiled, −11% interpreted; `arraymap` −11% to −13% compiled. Under `VERSION_1_8`, which keeps the
  old layout, the micro-benchmarks move by −14% to +12%: the interpreter loop and the escape analysis of the JIT
  come out differently.

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
  `arguments` in a non-strict function with parameters (see "The arguments object of strict functions"), and
  generators keep every variable in the activation. So does a function with more than 256 such
  variables (a module wrapper such as Octane's `pdfjs`), to stay within the 1024 JVM locals of a compiled method.
- **Semantics.** Code evaluated at runtime in the function's scope other than a direct eval (a legacy `Script`
  object, a host function) no longer sees the variables in registers. Hence `VERSION_ECMASCRIPT` only
  (`BACKWARD_COMPATIBILITY.md` 1.88).
- **Gain.** A loop in a function with a `catch` clause: 156 ms → 52 ms compiled. With a closure over one variable:
  171 ms → 115 ms compiled, 890 ms → 760 ms interpreted. A function with a `catch` clause and 7 variables, called
  3 million times: 592 ms → 87 ms compiled, 1,295 ms → 866 ms interpreted.
- **`let` and `const`** (M16, 16.11). The let/const bindings of the function and of its blocks that no nested
  function refers to are registers too: `keepVarsInRegisters` adds those of the blocks to the variables of the
  function (`ScriptNode.addBlockVariables`), where they keep the scope of their block for name resolution, so that
  two bindings of the same name stay apart (`NodeTransformer.registerLexicals`, converted by symbol). A block whose
  bindings are all registers gets no scope object, nor do its per-iteration copies; the head of a `for` loop is
  initialized as variables when all its bindings are registers (`visitLet`), and in a scope object otherwise.
  Their temporal dead zone is checked from the syntax, as in functions without an activation: only a declaration
  of a single name, outside a `switch`, qualifies (not a destructuring, a class declaration or a catch
  parameter). A const register keeps its flag local (`initRegisterVars`). Gain, 3 million iterations, compiled: a
  `let` in the body of a function with a closure 104 → 6 ms, a `let` loop 84 → 6 ms, a `const` in a loop body
  155 → 5 ms, a function with a `try`/`catch` 273 → 22 ms, a loop beside a captured `let` 259 → 88 ms (a `var`:
  9 ms); interpreted 1.5–2.9 times faster.

### The arguments object of strict functions

- **What** (M16, 16.13). A use of `arguments` gave the function an activation object, defined the arguments object
  in it, and kept every variable of the function in it, read and written by name. Under `VERSION_ECMASCRIPT`, the
  decision is made once the function is parsed (`IRFactory`): a strict function that needs no activation for
  anything else (no nested function, `eval`, `with`, generator or async function) gets a variable `arguments`
  instead (`FunctionNode.isArgumentsInRegister`), which its code and the interpreter initialize on entry with an
  unmapped arguments object built from the arguments passed (`ScriptRuntime.createUnmappedArguments`), before
  they are padded to the parameter count. A function that keeps an activation keeps its other variables in
  registers when its arguments object is unmapped (a strict function, or a non-strict one without parameters)
  and it declares no variable named `arguments` (which the arguments object of the activation initializes):
  that object only holds the values passed. Under `VERSION_ECMASCRIPT`, the arguments object also gets its
  @@iterator, `length` and `callee` in one map instead of three definitions.
- **Correctness.** An unmapped arguments object never reads the activation; its properties and behavior are
  the same (`StrictArgumentsTest`). A non-strict function keeps its activation: `f.arguments` finds the arguments
  object there (a strict function's throws). A function using `arguments` is never the target of a direct call,
  which passes the arguments one by one. Code evaluated in the function's scope other than a direct eval no
  longer sees its variables, as for the other functions (`BACKWARD_COMPATIBILITY.md` 1.88).
- **Gain.** 1 million calls with three arguments, compiled: a strict function summing `arguments` 818 → 200 ms
  (a rest parameter: 74 ms; Rhino 1.8.0: 735 ms), passing it to `apply` 1,194 → 341 ms, reading
  `arguments.length` 244 → 121 ms; a non-strict function without parameters 789 → 302 ms and 1,145 → 552 ms.
  Interpreted: 1,909 → 1,069 ms and 2,413 → 1,394 ms. `VERSION_ECMASCRIPT`.

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
  that no scope inside the function declares. Each nested function keeps a reference to the map of names of each
  enclosing function, with its depth, not a copy (`FunctionNode.getOuterVar`): a bundle of thousands of variables
  and thousands of nested functions (Prettier, in the Web Tooling Benchmark) copied 19 million entries, 900 MB, at
  each compilation, and ran out of memory with a 1 GB heap (2026-10-24).
- **Correctness.** At runtime the owner's activation is the first `NativeCall` on the scope chain whose function has
  the owner's descriptor; the variable is checked by name, and looked up by name if anything does not match. The
  interpreter reuses `ACTIVATION_GETVAR`/`ACTIVATION_SETVAR` (the depth in the high byte of the index), since no
  icode values are left.
- **Gain.** A closure reading and writing captured variables in a loop: 202 ms → 50 ms compiled, 571 ms → 517 ms
  interpreted. `VERSION_ECMASCRIPT`.

- **Function declarations** (2026-10-20). The function declarations of the function take positions too when
  nested functions refer to them (they are defined in the activation when the body starts, so `NativeCall`
  finds their slot on first use), and a call of such a name in compiled code reads the position
  (`ScriptRuntime.getVarAndThis`) instead of `getNameAndThis`. A declaration is never in a register, since it is
  stored by name; the names of function expressions stay out, since they are bound inside the expression. The
  interpreter reads the positions but still calls by name (no icode left). Gain: `math-spectral-norm` in the
  JavaScriptPerformance wrapper (four nested declarations called by name) 2,450 ms → 2,358 ms compiled; level when
  the activation holds only a few names, whose lookup costs about as much as `outerActivation`.

- **The activation found once per call** (2026-10-20). `outerActivation` walked the scope chain at every access,
  and in a JVM that had run other scripts it was no longer inlined: 13% of `math-spectral-norm` after the other
  SunSpider files, for the name of the declaration called in its inner loops. A compiled function now reserves
  a register per number of functions out that it reads (null on entry); the first access stores the activation
  that `ScriptRuntime.getOuterActivation` finds, and the next ones read the slot from it. The activation of an
  enclosing function is the same for the whole call, whatever block or `catch` scope the access is in, since the
  walk only stops at the `NativeCall` of the owner's descriptor. When it is not found (null), the access takes
  `getOuterVar`/`setOuterVar` as before. Generators keep the walk, since their registers do not survive a
  `yield`. Gain: `math-spectral-norm` 2,150 ms → 1,985 ms alone, 2,116 → 1,905 ms after the other SunSpider
  files; a closure that reads and writes a captured variable once per call, 3 million calls, 925 → 665 ms; in a
  loop of the closure, 380 → 235 ms.

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

### Slot keys compared without `Objects.equals` (2026-10-29)

- **What.** `StandardSlot.keyMatches` and `CompactSlot.keyMatches` compare the key by identity first, then call
  `equals` on the slot's name themselves, instead of calling `java.util.Objects.equals`.
- **Why.** Upstream commit c1ac6c149 ("Start using the new scopes hierarchy internally", after Rhino 1.9.1) made
  the scopes `ScopeObject`s that share `SlotMapOwner.get` with `ScriptableObject`. Octane `box2d` went from 12.9 s
  to 18.2 s at that commit (found by bisecting 270 upstream commits). JFR: the `Objects.equals` of the slot lookup
  no longer inlined (24 samples out of line before, 257 after, under `EmbeddedSlotMap.query`). Out of line,
  `Objects.equals` carries a type profile shared by every caller in the JVM; the key comparison now has a call
  site of its own, and most keys are the same string as the slot's name, so the identity check settles them.
- **Gain.** Octane, compiled, `VERSION_ES6`, each file alone, 2 rounds: `deltablue` 32.5–33.4 s → 26.6–26.8 s,
  `richards` 30.6–32.2 s → 28.9 s; `raytrace` and `box2d` level (the rest of `box2d`'s regression is elsewhere in
  that commit). Two full gate runs against the commit before: no regression, `v8-richards` level. The first run
  flagged `sort.cmp.ecma` (+24%), level in 7 rounds alone and in the second run (−0.6%). All versions.

### Double conversions linked without a guard (2026-10-29)

- **What.** The `DoubleLinker` links the conversions of a double (`TOBOOLEAN`, `TONUMBER`, `TONUMERIC`,
  `TOINT32`, `TOUINT32`) without a guard. The linked method converts a `Double` directly and any other value with
  the `ScriptRuntime` method the `DefaultLinker` would link, so the site is valid for every value and never
  relinks.
- **Why.** Their guard was `Guards.getInstanceOfGuard(Double.class)`, a method handle of the native
  `Class.isInstance`. At a call site the JIT inlined, it becomes a type check; at one it did not (large script
  functions past the inlining budget), every conversion was a JNI call, and on macOS/AArch64 each JNI call also
  switches the thread's code memory between writable and executable twice (`pthread_jit_write_protect_np`). JFR
  does not sample that time; the macOS `sample` tool on the main thread showed it at about 48% of Octane `box2d`
  (35% before upstream c1ac6c149, which made more sites miss the inlining budget).
- **Gain.** Compiled, `VERSION_ES6`, each file alone: `box2d` 18.4 s → 9.1 s (Rhino 1.9.1: 12.8 s). `richards`,
  `deltablue`, `raytrace` level; `gbemu` 3–7% slower in 2 rounds, not confirmed. `access-nbody` unchanged: median
  2,086 → 2,093 ms over 10 runs, 9 in 10 in its fast JIT state on both. Perf gate against the commit before: the first run flagged
  `sort.cmp.ecma` (+23%), a bimodal metric (2 of 6 runs slow on the same jar after the other scripts, level alone
  in 7 rounds); the second run passed, `v8-richards` level. All versions.
- **Tried first.** The same guards as static `instanceof` methods in every linker (`box2d` 10.1 s) or in the
  `DoubleLinker` only (11.4 s): `access-nbody` settled in its slow JIT state 5 and 3 times in 10, against 1.

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
  object of such loops altogether when no closure refers to their bindings (see "Variables in registers in
  functions with closures"). `VERSION_ECMASCRIPT`.

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

### Compiled generators complete without a stack trace

- **What** (M16, 16.4). A compiled generator body completes by throwing a `StopIteration` wrapped in a
  `JavaScriptException` (`OptRuntime.throwStopIteration`), which `ES6Generator` catches to produce
  `{ value, done: true }`. Building that exception walked the Java stack, and its details converted the
  `StopIteration` object to a string, which threw and caught an internal TypeError with a stack trace and message
  formatting of its own: about 10 µs per completed generator or async call, 5 times the interpreter's cost.
  `NativeIterator.GeneratorCompletion` overrides `fillInStackTrace` and computes its details without conversion.
- **Correctness.** ES6 generators and async functions catch it internally. A legacy (JavaScript 1.7) generator
  rethrows it to the script, which only sees the `StopIteration` value; a Java caller that catches it gets no Java
  stack trace for it.
- **Gain.** 30,000 calls, compiled: a generator returning a value 333 ms → 7 ms, an async function 365 ms → 13 ms,
  `for-of` over a generator 49 ms → 13 ms. All versions.

### Integers convert to strings without the double formatter

- **What** (M16, 16.6). `ScriptRuntime.toString(Object)` and `toString(double)` (string concatenation, `String()`,
  `join`, JSON, property keys) gave every number to `DoubleFormatter`, which computes the shortest decimal digits
  of a double (Ryu-like) and builds a `Decimal`: about 29 ns against 5 ns for `Long.toString`. An `Integer` now
  takes `Integer.toString`, and an integral double whose magnitude is at most 2^53 − 1 (but `-0`, which prints as
  `0` through the formatter) takes `Long.toString` (`integerOrNumberToString`).
- **Correctness.** Below 2^53, integers are exactly representable and spaced by at most 1, so no shorter decimal
  rounds to the same double: the integer's digits are the shortest ones. Above, they are not: `2**60` prints as
  `1152921504606847000`, which is why the fast path stops at 2^53. The bound is tested on the double:
  `Math.abs(Long.MIN_VALUE)` is negative, and a first version printed `Long.MIN_VALUE` with all its digits.
  `numberToString` (any radix) is unchanged.
- **Lesson.** The check was first put in `numberToString`, where it grew the method that calls `DoubleFormatter`;
  it lives in a method of its own on the `toString` paths.
- **Gain.** Compiled, 1 million conversions: `String(i)` 32 ms → 15 ms, `"x" + i` 22 ms → 7 ms; `join` of 1,000
  integers 40 ms → 14 ms (compiled) and 36 ms → 14 ms (interpreted); `JSON.stringify` of integer-valued objects
  94 ms → 81 ms. All versions.

### Arrays stay dense across small gaps

- **What** (M16, 16.7). A `NativeArray` keeps its elements in a dense Java array while `denseOnly` holds; the first
  write beyond 1.5 times the dense capacity cleared it for good, after which every element lived in the slot map
  and every `get`/`put` queried the map first. An array filled from its last index down started that way: its
  first write (index 299 of an empty array) was beyond the capacity of 10. A write to an index below
  `MAX_DENSE_GAP` (1,024) now grows the dense array whatever its capacity.
- **Correctness.** A hole is `NOT_FOUND` in the dense array, so reads go through the prototype chain, `in` and
  `hasOwnProperty` are false, and enumeration skips it, as before. Cost: up to 4 KB of holes per array.
- **Gain.** `v8-crypto` 46–52 ms → 36 ms compiled, −3% interpreted; a 300-element array filled downward 26 ms →
  13 ms compiled. All versions.
- **An empty array whose length is set to 0** (2026-10-24). `shift` and `pop` on an empty array set its length to
  0 (Set(O, "length", 0)), as does `a.length = 0` to clear it; the dense case of `setLength` only took a smaller
  length or a larger one below 1.5 times the current one, which is 0, so the array lost `denseOnly`. A queue
  that is often empty (`next = queue.shift() || read()`) then lived in the slot map, and each `shift` of its
  elements went through it. An unchanged length now leaves the array as it is. Gain: JSHint, in the Web
  Tooling Benchmark, whose lookahead queue of tokens is such a queue: about 9,000 → 450 ms per run, compiled
  and interpreted alike. All versions.

### Array patterns without temporaries and guards

- **What** (M16, 16.9). An array pattern compiles to an `ITER_OPEN`, one `ITER_STEP` per element and an
  `ITER_CLOSE`, inside a statement-wide `try`/`finally` that closes the iterators left open by an abrupt completion
  (`IRFactory.guardIterators`). Three changes trim that code:
  - an element that is a plain name is assigned the step directly, without a temporary;
  - the temporaries of a pattern are not initialized to `undefined` (`NodeTransformer.visitLet`): each is written
    before it is read;
  - a pattern that only binds names (holes and a rest element included) in a `let`/`const` declaration or a
    parameter list is not guarded (`Node.UNGUARDED_ITERATOR_PROP`).

  At run time, `IteratorRecord.open` recognizes a plain array of the realm whose iteration methods are the original
  ones without looking them up: `TopLevel.arrayIteration` holds the slots of `Array.prototype[Symbol.iterator]` and
  `%ArrayIteratorPrototype%.next` with their original values, and `IteratorRecord.step` reads a `NativeArray` by
  `int` index.
- **Correctness.** Initializing a `let`, `const` or parameter binding cannot throw, and an iterator whose step
  throws is done, so nothing can leave the iterator of such a pattern open. A cached slot is valid while it is not
  `stale` (it leaves its map when deleted, replaced by an accessor, or when its map is rebuilt) and holds the same
  value; the array must not have an own `Symbol.iterator`. Otherwise the slots are looked up again, as before.
  Assigning the step directly also resolves the target before the iterator is stepped, which is the specification's
  order (13.15.5.5) and was not the case before. Dropping the guard exposed a bug of compiled generators that
  it hid (a `return` in a `for await (const x …)` loop, see `CHANGELOG_MONFLABS.md`), fixed with it.
- **Gain.** 300,000 executions, compiled / interpreted: `let [x, y, z] = a` 7 ms → 4 ms / 130 ms → 58 ms (ES6: 3 /
  49 ms); `[x, y, z] = a` 6 ms → 3 ms / 127 ms → 79 ms; the swap `[x, y] = [y, x]` 9 ms → 5 ms / 104 ms → 79 ms; a
  destructured parameter 12 ms → 8 ms / 136 ms → 102 ms; `for (const [k, v] of pairs)` 7 ms → 5 ms / 71 ms → 43 ms.
  `VERSION_ECMASCRIPT` (the older versions read the elements by index).

### Array species without lookups

- **What** (M16, 16.10). `slice`, `splice`, `concat`, `map`, `filter` and `flat`/`flatMap` create their result
  through ArraySpeciesCreate: a lookup of `constructor` along the chain of the array, a cross-realm check, and a call
  of the `Symbol.species` getter, then a construction through the `Array` constructor. When the array is a plain
  `NativeArray` whose prototype is the `Array.prototype` of the realm, without an own `constructor`, and
  `Array.prototype.constructor` and `Array[Symbol.species]` are the original ones, the result is created directly
  (`ArrayLikeAbstractOperations.ArraySpecies`, cached in `TopLevel.arraySpecies`).
- **Correctness.** As for array iteration, the cache holds the slots: `constructor` must not be `stale` and must
  hold the original `Array`; the species accessor must not be `stale` and must hold the original getter, since
  `defineProperty` changes an accessor in place. The original getter returns its receiver, so the species of the
  original `Array` is `Array`, which creates the same array as `cx.newArray`. The original getter is recognized
  by its Java lambda (`ScriptRuntimeES6.isSpeciesGetter`), not by calling it: a first version called the getter to
  check it, which test262 caught (`create-proto-from-ctor-realm-*` count the calls of a replaced getter).
- **Gain.** 200,000 calls on an 8-element array, compiled, `VERSION_ECMASCRIPT`: `concat` 41 ms → 24 ms, `slice`
  22 ms → 17 ms, `splice` 23 ms → 17 ms; interpreted `concat` 58 ms → 43 ms. `map` and `filter` are dominated by
  the callback. All versions.

### `push` and `unshift` without a lookup per element

- **What** (M16, 16.15). Under `VERSION_ECMASCRIPT`, the dense fast path of `push` and `unshift` is taken only
  when no prototype has a property at the indexes written (`BACKWARD_COMPATIBILITY.md` 1.76): `push` looked each
  index up along the chain, `unshift` scanned the keys of every prototype. When the prototype of the array is a
  `NativeArray` of length 0 followed by plain `NativeObject`s, the check is cached in the realm
  (`NativeArray.prototypesHaveNoIndex`, `TopLevel.indexFreeArrayPrototype`).
- **Correctness.** An array has no index at or above its length, so an empty `Array.prototype` has no index
  property: its length is read on every call, since an element written to it goes to its dense storage without a
  slot. The other prototypes are scanned for index keys once, and marked as objects of a cached chain
  (`SlotMapOwner.cachedPrototype`): adding or removing one of their properties, replacing their map or changing a
  prototype of the chain then increments `PropertyCache.epoch()`, and the cache holds the epoch it was verified
  at. Only plain slot maps, which report those changes, are trusted. Otherwise the per-element check runs as before.
- **Gain.** 4 million calls on 20-element arrays, compiled: `push(x)` 100 ms → 80 ms, `push(x, y, z)` 82 ms →
  55 ms, `unshift(x)` 400 ms → 150 ms; interpreted: `push(x)` 107 ms → 97 ms, `unshift(x)` 190 ms → 110 ms.
  `VERSION_ECMASCRIPT` only (the older versions never check).

### Direct calls evaluate their arguments once

- **What.** In compiled code, a call to a top-level function that is never reassigned is a direct call of its
  static method (`BodyCodegen.visitOptimizedCall`), guarded by a check that the name still holds that function,
  with a regular call otherwise. The arguments were generated in both branches; they are now evaluated once,
  before the guard, into locals (a `double` pair for a number), and each branch loads them.
- **Why it matters.** Bytecode size decides inlining: C2 inlines a hot callee only up to `FreqInlineSize` (325
  bytes). Upstream's calling convention (strictness, `LookupResult`, `getThisObj`, `new.target`) had made a
  recursive `fib` 345 bytes against 291 in Rhino 1.8.0, so C2 stopped inlining its recursion.
- **Correctness.** The guard has no side effect, so the arguments are still evaluated once, after the callee,
  in order. Generators keep the old code: their locals are saved across a `yield` as references. A number
  argument goes to a local pair that only ever holds doubles (taken above every local in use, reused only by
  later arguments, never released): Rhino's stack map frames cannot merge a double with a reference, and a
  first version that released the pair failed to compile a loop whose next call reused the slot for an object
  (test262 caught it in its resizable buffer helpers).
- **Gain.** Recursive `fib(28)` ten times, compiled: 100 ms → 83 ms (Rhino 1.8.0: 55 ms; upstream base: 133 ms).

### Direct calls to nested function declarations

- **What.** Direct calls (above) covered the function declarations of the script only. A call in a function now
  also targets a declaration of an enclosing function: `OptTransformer.findDeclaration` takes the nearest
  declaration of the name in the enclosing functions, then the script, and the guard checks at run time that
  the name holds a function of that declaration.
- **Why it matters.** Code that wraps its work in a function (a module pattern, an IIFE, the
  JavaScriptPerformance `__benchmarkBody__` wrapper) called its helpers through `JSFunction.call` and
  `MHJSFunctionCode.execute`, whose call of the linked `execute` is one site for every compiled function of
  every script. Run alone, a script keeps that site monomorphic and C2 inlines through it; in a JVM that has run
  other scripts, the site is megamorphic and the shared methods are already compiled into code too big to
  inline (`InlineSmallCode`, 2,500 bytes), so the callee is a separate call and the caller's profile no longer
  types its arguments. `math-spectral-norm` after 19 other SunSpider files: 2,750 ms against 2,450 ms alone;
  `-XX:InlineSmallCode=10000` closed the gap for it and slowed other files. A direct call is a static call of
  its own site, which no other script can pollute.
- **Correctness.** The direct path passes the function object, and the callee takes its scope from it
  (`getDeclarationScope`), so any closure of the declaration runs in its own scope. A name that holds something
  else (reassigned, shadowed by a `var`, a parameter or an inner declaration that was not chosen) fails the guard
  and takes the regular call.
- **Gain.** SunSpider in one JVM, compiled: `math-spectral-norm` 2,806 ms → 2,296 ms (alone 2,372 → 2,101),
  `controlflow-recursive` 3,534 → 2,591, `crypto-md5` 877 → 737.

### Array elements linked per call site

- **What.** Compiled element reads and writes (`a[i]`, `a[i] = v`) are `invokedynamic` sites that linked to
  `ScriptRuntime.getObjectElem`, `getObjectIndex`, `setObjectElem` and `setObjectIndex`. `NativeArrayLinker`
  now links them, for a receiver of exactly the `NativeArray` class, to small methods that read or replace a
  dense element (`NativeArray.getDenseElement`, `setDenseElement`) and call the general method otherwise.
- **Why it matters.** The general methods are shared by every script; in a JVM that has run other scripts they
  are compiled on their own into code too big to inline (see the direct calls to nested declarations above), and
  `math-spectral-norm` spent about 28% of its time in `getObjectElem` and the prototype walk it calls. The
  linked methods are small and per site, so they inline whatever other scripts did.
- **When it applies.** The array holds only dense elements (no accessor or attribute on an index); a read
  needs an element at the index (a hole reads the prototypes on the general path); a write needs an existing
  element below the length on an array that is not sealed, so growing, holes (where a prototype setter may
  apply), frozen and non-extensible arrays take the general path.
- **Gain.** SunSpider compiled, alone: `access-fannkuch` 1,319 ms → 830 ms, `access-nsieve` 320 → 244,
  `bitops-nsieve-bits` 1,175 → 934; `math-spectral-norm` after the other SunSpider files 2,222 → 2,046.

### Nested functions optimized

- **What.** The optimizer (`Optimizer`, with the type flow of `Block`) keeps the variables of a function without
  an activation that only ever hold numbers in double registers, and compiles arithmetic, comparisons and
  element indexes on them without boxing; the arguments of a direct call are passed as doubles. Upstream ran it
  on the functions declared at the top level of the script only, so a function nested in another one (a module
  pattern, an IIFE, the JavaScriptPerformance `__benchmarkBody__` wrapper) ran on boxed `Double`s and
  `Integer`s. `Optimizer.optimize` now walks every function.
- **BigInt parameters.** In a direct-call target, the optimizer typed parameters as numbers in arithmetic and
  converted them with ToNumber, so `mul(2n, 3n)` threw (an upstream bug, in top-level functions). An operation
  whose two operands can both be BigInts (two parameters, a parameter and an operand of unknown type) now takes
  the generic path; a parameter against a number keeps double arithmetic, since a BigInt there throws a TypeError
  either way. Under `VERSION_ES6`, top-level functions keep the old typing (`BACKWARD_COMPATIBILITY.md` 1.90).
- **Gain.** SunSpider in one JVM, compiled: `math-spectral-norm` 1,839 ms → 745 ms, `bitops-3bit-bits-in-byte`
  18,804 → 6,086 ms (about 4,100 alone), `bitops-nsieve-bits` 919 → 312, `access-fannkuch` 741 → 414,
  `3d-morph` 1,258 → 765, `access-nbody` 4,333 → 3,154, `math-cordic` 1,628 → 1,142, `crypto-md5` 772 → 556,
  `crypto-aes` 575 → 431; the string and date files are level.

### Number parameters

- **What.** A direct call passes each argument as an object and a double (`Void.TYPE` in the object when it is a
  number), and the callee converts its parameters to objects on entry unless it uses them in arithmetic. The
  parameters of a nested function declaration are now doubles, like its number variables, when every call is
  known to pass a number: `Optimizer.optimize` keeps the declarations whose name the enclosing function (and
  the functions nested in it) only uses as the callee of their direct calls, without a direct `eval` or `with`
  anywhere in it; it optimizes the callers first, then marks a parameter when every call passes a double in
  that position (`OptFunctionNode.hasNumberArgument`); the type flow of `Block` starts it as a number, and keeps
  it a number variable only if the body assigns it nothing else. The prologue takes the double of the pair, or
  converts the object (a call through the `Object[]` entry, which the analysis excludes).
- **Recursion.** Functions that call themselves or each other cannot wait for their callers. They are assumed to
  take numbers everywhere, then `Optimizer.recursiveNumberArguments` drops each parameter that a call may pass
  something else, until nothing changes: a call from one of them passes a number when the argument is a number
  literal, a number parameter that the caller never assigns, or an arithmetic operation on those (`fib(n - 1)`);
  the operands must all be numbers, since the operation on BigInts gives a BigInt. A caller that is not
  optimized (one that needs an activation, such as a function wrapping its declarations) gets the same check
  without parameters, so its literal arguments count as numbers. SunSpider `controlflow-recursive` alone
  1,130 ms → 1,000 ms (its `ack`, `fib` and `tak` inside the JavaScriptPerformance wrapper); `fib` and `tak` in a
  wrapper, 82 → 75 ms. A top-level `fib` is unchanged (not a candidate).
- **When it does not apply.** A top-level declaration (a global property that other scripts and the host can
  call), a function used as a value, called with another number of arguments or with a spread, and a function
  with a rest or destructured parameter.
- **Gain.** `math-spectral-norm` alone: its `A(i, j)` compiles to double arithmetic; level by itself (C2 had
  inlined the boxed version), but see the next item.

### Number variables of functions with an activation

- **What.** The optimizer skipped every function with an activation object, such as a function wrapping its
  declarations (a module pattern, the JavaScriptPerformance wrapper). Under `VERSION_ECMASCRIPT`, such a
  function keeps the variables that no nested function refers to in registers (M14); the optimizer now types
  those too, and the ones that only hold numbers get double registers (`BodyCodegen.initRegisterVars`). Their
  values then count for the number parameters and returns of the functions they call: in SunSpider
  `controlflow-recursive`, `ack(3, i)` and `tak(3 * i + 3, 2 * i + 2, i + 1)` pass numbers, and `ack` and `tak`
  get number parameters and return doubles.
- **Correctness.** Only register variables are GETVAR/SETVAR nodes in such a function; the variables in the
  activation keep their names, untyped. Generators and functions with `eval`, `with` or an aliasing `arguments`
  have no register variables.
- **Gain.** `controlflow-recursive` alone 955 → 895 ms, in the SunSpider sequence 1,143 → 925 ms; `3d-morph`
  in the sequence 773 → 694 ms; the other files are level (rerun alone). `VERSION_ES6` keeps these variables in
  the activation, so it is unchanged.

### Number return values

- **What.** A compiled function returned an `Object`, so `fib(n - 1) + fib(n - 2)` boxed both results and
  added them with the generic `+`. A nested function declaration that only its direct calls can call (as for
  number parameters) and that always returns a number now has its body in a `_c_<name>_<n>_num` method returning
  a `double`; its `_c_` body method, which the other calls use, calls it and boxes the result. A direct call
  from optimized code calls the `double` method and types the call as a number, and a `?:` whose branches are
  numbers stays a double.
- **When it applies.** Each `return` has a value, outside a `try`, and the function is not a generator or async;
  every value is a number: a literal, a number variable, an arithmetic operation that cannot give a BigInt (on a
  number, or `+x` and `>>>`), a `?:` of numbers, or a direct call of such a function. `Optimizer.numberReturns`
  assumes the candidates return numbers and drops each one with a value that may not be, until nothing changes;
  for recursive functions, `recursiveNumberArguments` finds their number parameters and number returns
  together, since `tak(tak(x - 1, y, z), …)` passes the results of calls and returns a parameter.
- **Correctness.** The analysis runs on the tree before it is rewritten: every function's types are found first
  (`analyzeFunction`), then all are rewritten. A direct call that finds another function under the name converts
  the result with ToNumber, so the analysis only takes a function whose calls all reach it: each callee is its
  name, which resolves to the declaration from the call (`Node.DIRECT_TARGET_PROP`, set by `OptTransformer`), and
  the name is used nowhere else, so it is never assigned. A method call of the same name (`sm.getScope()` in
  `function getScope() { return sm.getScope(); }`) or a call of a parameter of the same name is also a direct call,
  checked at run time, but gave `NaN` when the function was taken as returning a number: Lebab, in the Web Tooling
  Benchmark, failed on it (2026-10-24). The `_num` method keeps the `_c_` prefix, so the script stack of an error
  still lists the function.
- **Gain.** `fib` and `tak` in a wrapper, 74 → 61 ms; SunSpider `controlflow-recursive` alone 1,000 →
  950 ms (only its `fib` qualifies: `ack` and `tak` are called with a variable of the wrapper, which has an
  activation and no types).

### Direct calls without `this`

- **What.** A direct call computed the `this` of its target (`JSFunction.getThisObj`): for a non-strict function
  called by name, the global object, through `ScriptableObject.getTopLevelScope`, a walk up the scope chain at
  every call. It now skips it when the target cannot read its `this` (`OptFunctionNode.readsThis`: no `this`,
  `super` or direct `eval` in its body or in its arrow functions, which share it); the target then receives
  `undefined`, which it never reads.
- **Gain.** `math-spectral-norm` alone 635 ms → 400 ms, after the other SunSpider files 742 → 573 ms (42% of
  its time was `getTopLevelScope` once the arithmetic was unboxed); SunSpider in one JVM:
  `controlflow-recursive` 1,538 → 1,131 ms, `crypto-md5` 556 → 496 ms.

### Calls without `this`

- **What.** Every call of a non-strict function computed its `this` (`JSFunction.getThisObj`): with an
  undefined `this` (a function called by name, a callback of `map` or `forEach`), the global object through
  `ScriptableObject.getTopLevelScope`, a walk up the scope chain; with a primitive, a wrapper object. As for
  direct calls, `JSFunction.getCallThis`, used by `JSFunction.call` and by the interpreter's calls of
  interpreted functions, now passes the `this` unchanged when the function cannot read it:
  `JSDescriptor.ignoresThis`, set by `CodeGenUtils` from the same scan as `OptFunctionNode.readsThis` (no `this`,
  `super` or direct `eval` in its body or its arrow functions), and false for arrow functions, generators and
  async functions.
- **Correctness.** Nothing else reads the `this` of a call but the debugger (`DebugFrame.onEnter`), so the
  `this` is still computed when a debugger is set.
- **Gain.** `map`, `reduce`, `forEach` and calls through a variable, 2,000 times over 1,000 elements: 122 → 106
  ms compiled (`VERSION_ECMASCRIPT`), 1,100 → 1,060 ms interpreted.

### Method calls on primitive strings without a String object

- **What** (M16, 16.16). A call `s.m()` on a primitive string converted the string to a `NativeString` (with its
  own `length` property), looked `m` up through it, and passed it as `this`. Under `VERSION_ECMASCRIPT`,
  `ScriptRuntime.stringMethod` looks `m` up in the slot maps of `String.prototype` and its prototypes and returns
  a `LookupResult` whose call `this` is the string itself (`getThisValue`); the String object is made only if
  `getThis()` is asked for.
- **When it applies.** The name is not `length` or an index (the own properties of a string), every object up to
  the one holding `m` is a `NativeString` or `NativeObject` with a plain slot map, and `m` is a data property
  holding a user function or a built-in that takes a primitive `this`; otherwise the lookup goes through the
  String object, which also gives the error messages.
- **Correctness.** Passing the primitive is what the spec does (`BACKWARD_COMPATIBILITY.md` 1.89): a non-strict
  function boxes it (`JSFunction.getThisObj`), and the `String.prototype` built-ins apply ToString or
  thisStringValue to it. `VERSION_ES6` keeps the String object, since its built-ins observe an overridden
  `toString`.
- **Gain.** `indexOf`, `charCodeAt`, `slice` and `startsWith` in a loop, 3 million times: 598 ms → 425 ms
  compiled, 1,425 ms → 1,099 ms interpreted.

### Compiled functions called without a method handle

- **What.** Upstream's compiled functions are `MHJSFunctionCode` objects that call the static method of the
  function through a `MethodHandle` field. C2 inlines through a method handle only when it is a constant, so a
  call through `JSFunction.call` never inlined the callee (`MHJSFunctionCode.lambda$new$0` was the top frame of
  call-heavy profiles). On the first call of a function, `MHJSFunctionCode.link` replaces it by an instance of a
  class made by `LambdaMetafactory`, whose `execute` calls the static method directly.
- **When it applies.** Every call of a compiled function that is not a direct call (direct calls already call
  the static method): methods, closures, functions passed as values. Linking happens on the first call, so a
  function that never runs gets no class; a first version that linked after 1,000 calls lost the gain, since
  the shared call site in `execute` then also saw the method-handle class.
- **Gain.** SunSpider `math-spectral-norm` in the JavaScriptPerformance wrapper (nested functions called by
  name): 2,790 ms → 2,500 ms compiled (Rhino 1.8.0: 2,150 ms). 2,000 functions each called once: 10 ms either
  way.

### Functions too large for a class file are split (M17)

- **What.** A function whose method exceeds a class file limit (64 KB of bytecode, a jump over 32 KB) made the
  whole script run in the interpreter. The class compiler now reports the function (`FunctionTooLargeException`,
  with its source position), and `Context.compileImpl` parses the script again with that function split
  (`Splitter`, after Nashorn's `SplitIntoFunctions`): runs of statements of its body, or of a block too large on its
  own (or a single statement that is not a block, from the innermost `else` of an `else if` chain out), become
  arrow functions called in place, of about 600 AST nodes (about 6 KB of bytecode, below HotSpot's
  8,000-byte limit for compiling a method), half as large on each new failure, up to 3 times.
- **When it applies.** Compiled mode, only after a failure: code that fits is unchanged. Not split: generators and
  async functions (a `yield` cannot cross a function), derived constructors (`super()`), functions with a direct
  `eval` or a `with`. Function declarations, statements holding a function declared in a block (Annex B), and
  the lexical declarations of blocks and scripts stay in place.
- **How it stays correct.** An arrow function shares `this`, `arguments`, `new.target` and `super`. Names in a
  chunk resolve to the bindings of the enclosing scopes through the syntax tree, as for any nested function; the
  scopes moved into a chunk and their bindings become the chunk's, and a `var` that `IRFactory` declares in a chunk
  is the function's (`Parser.defineSymbol`). A `return` stores its value in a hidden variable (`%return`) and
  returns a state; a `break` or `continue` to a statement outside of the chunk returns a state of its own; the call
  site replays them. Chunk methods are named `_c_<function>_<n>-split`, and script stack traces show their frames as
  the function's, skipping the calls in between. The function gets an activation, as any function with a nested
  function, so `f.arguments` while it runs is its arguments object. Validation: with `-Drhino.splitWeight=3`, compiled
  mode splits every function it can into chunks of a few statements; test262 and the Mozilla tests then pass
  except one test of `f.arguments` and two tests of the generated bytecode (`NewTargetDirectCallTest`). A chunk
  of a script makes no direct call of a function with a rest parameter, as the top-level code it comes from:
  under `VERSION_ES6`, a direct call passes the arguments as they are (BACKWARD_COMPATIBILITY.md 1.92).
- **Scripts (17.2).** The top-level code of a script is split the same way. A chunk of a script takes the
  script's completion value as its parameter (`%completion`: at the top level, `BodyCodegen` reads the script's
  result register for that name) and returns the new value; `IRFactory` turns the statements that set the
  completion value (expression statements, the `undefined` of the ES2015 completion reform) into assignments to
  the parameter in a chunk of a script. Since a chunk returns its completion value, a statement with a break or
  continue to a statement outside of it stays in place. Module and eval code are not split.
- **Statements the chunks did not make smaller (2026-10-24).** A statement too heavy for a chunk is split too,
  when this keeps its meaning:
  - a `var` statement of many declarators becomes several (`var a = 1, b = 2;` is `var a = 1; var b = 2;`): the
    Flow parser in Prettier, compiled from OCaml, declares thousands of constants in one;
  - a `switch` of many clauses (in a function, without declarations in its block) becomes a labeled block: the
    discriminant is evaluated once, then `if (%disc === e) { %case = i; break %found; }` for each case
    expression in order finds the clause entered (the default one otherwise), as a list of statements that is
    chunked as any other; then each run of clauses is a `switch` on that index, under `if (%case < end)`, and
    falls through to the next run; a `break` to the switch leaves the block. The jison parser of CoffeeScript
    has one of 300 clauses;
  - an object literal: runs of its data properties move to chunks whose literals it spreads, `{a: 1, ...(() =>
    ({b: 2, c: 3}))()}`, which define them in the same order, at the same point; accessors, methods (whose home
    object is the literal) and `__proto__` stay. TypeScript's table of diagnostics has thousands;
  - a `?:`: an operand heavier than half a chunk moves to a chunk returning its value, `(() => operand)()`, a value
    evaluated with the same `this`, `arguments` and bindings: Prettier's lexer tables are trees of them.
  - (2026-10-25) a comma expression: runs of its operands move to chunks returning the value of their last
    operand, `(a, b, c, d)` being `((() => (a, b))(), (() => (c, d))())`, the same values in the same order; and
    an operand of `&&`, `||` or `??` heavier than half a chunk, as one of `?:`. The wasm2js code in Babylon.js has
    a `return` of a comma expression of 21,000 nodes, and conditions of thousands.
- **Lexical declarations of a function (2026-10-25).** The `let`, `const` and class declarations of the body of
  a function move to chunks as a `var` does: the module function of a bundle declares thousands of them
  (Babylon.js 1,494), which did not fit in a method however split. Their bindings stay variables of the
  function: they start in their temporal dead zone on entry, as before, and the chunk initializes them through
  its scope chain, the stores of a declaration being marked as initializations (`TDZ_DECLARATION_PROP`); a read
  from a chunk is checked at run time, as from any closure. A destructuring declaration stays, its stores not
  being marked so; the lexical declarations of a block stay, since their scope is the block.
- **Many function declarations (2026-10-25).** The declarations of a function are created on entry, about 30
  bytes of code each: TypeScript's compiler, a function of 3,560 of them, did not fit in a method however split.
  Beyond 64, a call of `OptRuntime.initFunctions` creates them in a loop, from their descriptor indexes encoded in
  a string constant (4,096 per call), with the same constructor arguments and `initFunction` as each inline
  creation. The JetStream 3 `typescript-lib` workload runs compiled: 16 s per run, 30 s interpreted.
- **All the functions too large at once (2026-10-24).** After the first function too large, the class compiler
  goes on with the others (into a class it drops) and reports them all, so the script is parsed again once for
  them, not once per function: Prettier took 14 compilations.
- **Gain.** Octane `regexp.js`, whose `runBlock` functions outgrew the limits with the fork's larger code (direct
  calls to nested declarations; under `VERSION_ECMASCRIPT`, reads of captured variables at their activation
  position), ran interpreted: score 460 → 606 (`VERSION_ES6`), 455 → about 570 (`VERSION_ECMASCRIPT`); upstream's
  base 525, Rhino 1.8.0 538. Chunks of 300 or 1,200 nodes score the same. The JetStream 3 `threejs` workload runs compiled: 5.3 s per
  run, 14.2 s interpreted; Babylon.js's scene compiles, at about the speed of the interpreter (780 and 700 ms).

### Functions too large for the JIT (2026-10-25)

- **What.** HotSpot does not compile a method of more than 8,000 bytes of bytecode (`-XX:+DontCompileHugeMethods`,
  the default): it stays in the bytecode interpreter of the JVM, several times slower than the fork's own
  interpreter. Under `VERSION_ECMASCRIPT`, the class compiler reports a function whose body method exceeds 8,000
  bytes as too large (`Codegen.JIT_METHOD_LIMIT`, `CompilerEnvirons.splitsForJit`), and `Context.compileImpl`
  splits it once, as one too large for a class file; if it does not get smaller, it is compiled as it is. Only
  when the compilation is retried (`Context.compileImpl`, not `ClassCompiler`), and not for the top-level code of
  a script, which usually runs once.
- **Generators and async functions** are split too: no chunk holds a `yield`, an `await` or a `for await`
  (`Splitter.suspends`). In a generator that is not async, a run of statements that yield becomes a generator
  called with `yield*` (`Splitter.delegation`), which forwards `next`, `throw` and `return` to it, unless they
  use the `this`, `arguments`, `new.target` or `super` of the function. A `return()` forwarded to the chunk,
  whose `finally` block yields and lets it complete later, completes the `yield*` normally: the chunk sets a
  variable of its own at each of its exits (`%exitN`), and the generator returns the value of the `yield*` when
  it did not. Gated on `VERSION_ECMASCRIPT`, where a `yield*` in such a block delegates (BACKWARD_COMPATIBILITY.md
  1.105).
- **Smaller and faster generators.** A generator compiled an object or array literal, and the arguments of a
  call, with the code that keeps its stack across a `yield` (values in a local, checkcasts) whether or not one
  held a `yield`: now only when one does (`BodyCodegen.maySuspend`), 12% of the tokenizer of `js-tokens`. Its
  nested functions read the variables they share with it at their position in its activation
  (`NodeTransformer.keepVarsInRegisters`, `getOuterVar`), as those of a function; the generator itself reads them
  by name, its registers not surviving a `yield`. An exception got its interpreter for the script stack by
  reflection (`RhinoException`), and the completion of a compiled generator, an exception, looked up the
  `@@toStringTag` of its value: both once per completion of the chunks of `js-tokens`.
- **Gain.** `js-tokens` compiled: 5.8 s → 4.75 s per run (interpreted 3.2 s). Its tokenizer is a loop whose body
  is too heavy for one chunk: each token enters two chunks, generators of their own (3 million per run, about
  0.55 µs each). Whole and JIT-compiled (`-XX:-DontCompileHugeMethods`), it would take 2.6 s.

### Scripts spread over several classes (M17, 17.3)

- **What.** A script whose class exceeds a limit of the class itself, its constant pool (65,535 entries) or its
  invokedynamic sites (HotSpot numbers them with a u2 index), ran in the interpreter. The class compiler now
  reports how many of the functions it had generated (`ClassTooLargeException`), and `Context.compileImpl`
  compiles again with the functions spread over twice the estimated number of classes (`_u<n>` after the name of
  the main class), round robin by function index, the script itself in the main class; then twice as many on each
  new failure, up to 256.
- **How it stays correct.** Each class has its own constant pool, lookup accessor, `_descriptors` field (the
  same array), regexp and template literal fields and initializers, and number constants; the code of a function
  refers to its own class, and a direct call to the class of its target (`Codegen.classNameOf`). A class limit
  reached in a method over 16 KB splits the method first (Splitter), once. `-Drhino.classUnits=3` spreads every
  script over 3 classes, for testing: with `-Drhino.splitWeight=3`, test262 and the Mozilla tests pass as with the
  split alone.
- **Gain.** Octane `mandreel` (asm.js code, 22,657 integer and 13,143 double constants) compiles over 16
  classes: score 628 → 826.

### Long jumps with `goto_w`

- **What.** A jump over more than 32 KB of bytecode does not fit the 16-bit offset of a branch. Splitting the
  function makes its methods smaller, but not a statement that the Splitter keeps whole, such as a `switch` of
  hundreds of clauses (the action table of a jison parser in CoffeeScript, the printer of Recast in Lebab): the
  script ran in the interpreter. Once the function cannot be split further, `Context.compileImpl` compiles it
  again with wide jumps (`CompilerEnvirons.useWideJumps`): `ClassFileWriter` emits each branch to a label as a
  `goto_w`, and a conditional one as the inverted condition over a `goto_w`.
- **When it applies.** Compiled mode, only after a jump was too long and the function was split as far as it can
  be (or cannot be split, as a generator). A method still over 64 KB with its wider branches runs interpreted.
- **How it stays correct.** The stack map reads `goto_w` as an unconditional jump; the fixups of the labels
  record their width (`WIDE_FIXUP`). Validation: with `-Drhino.wideJumps=true`, every branch is wide; test262
  and the Mozilla tests pass as without it.
- **Gain.** Lebab, compiled instead of interpreted: about 1,250 → 780 ms per run.

### Literals of data compiled as data

- **What.** A literal of more than 1,000 constants (numbers, strings, booleans, `null`, arrays and objects of
  them, with no computed key, accessor, method, spread, hole or `__proto__`) is compiled as text: string constants
  of at most 20,000 characters, which `OptRuntime.parseConstantLiteral` parses once into a template cached in a
  static field of the class; each evaluation makes new objects from it with `ScriptRuntime.newArrayLiteral` and
  `fillObjectLiteral`, as the code of the literal does. Numbers are boxed as `Codegen.pushNumberAsObject` does
  (`-0` a Double).
- **Gain.** Octane `pdfjs` (`CIDToUnicodeMaps`, 500 KB of nested arrays and objects of numbers) compiles under
  `VERSION_ECMASCRIPT` instead of exceeding the constant pool: score 2,100 → 4,505.

### RegExp symbol methods on an ordinary regexp

- **What.** `RegExp.prototype[@@split]` gets the flags of the regexp (`flags`, which calls nine getters), looks up
  its species constructor and constructs a sticky splitter, which compiles the pattern again, on every call;
  `@@replace`, `@@match` and `@@matchAll` read `flags` too. For an ordinary regexp, the flags come from the
  compiled pattern and the splitter is built once per pattern and flags (`NativeRegExp.splitterOf`), through the
  original constructor. As in Nashorn (`NativeRegExp.isOrdinary`), a regexp is ordinary when it is a
  `NativeRegExp` whose prototype is the `RegExp.prototype` of the realm, with no own property but `lastIndex`,
  and the properties those algorithms read keep their original contents: `constructor`, `exec`, `@@match`,
  `flags` and the eight flag getters of the prototype, `@@species` of the constructor. Under
  `VERSION_ECMASCRIPT`, `Set(R, "lastIndex")` and `Get(R, "lastIndex")` of a regexp write and read its field.
- **Correctness.** `OriginalProperties` records the contents of those properties when `RegExp` is initialized
  (the value of a data property, the getter of an accessor) and caches their slots: a slot is valid while it is
  not stale and has the same contents. A stale slot is looked up again, so adding methods to `RegExp.prototype`
  (which rebuilds its map) keeps the fast path; a property defined again, replaced or deleted turns it off. The
  original getters return the bits of the compiled flags, in the order of the `flags` getter of the language
  version (`hasIndices` and `unicodeSets` must exist under `VERSION_ECMASCRIPT`). The splitter is not observable:
  its constructor is the original one, which reads only `@@match` of the regexp, and the fast split does not
  change its `lastIndex`; it is built before the limit is converted, as the specification orders, and again
  after `compile()`. `lastIndex` is an own, non-configurable data property of every regexp. `RegExpOrdinaryTest`
  checks each property once replaced; the same scripts give the same results before and after the change.
- **Matcher.** The characters outside the bitmap of a class (code points and ranges beyond the BMP, property
  escapes) are matched from `int` arrays set with the bitmap, rather than boxed lists. Alone this was level (see
  "Tried and dropped"), but in the harness sequence `ArrayList.contains` took 14% of Octane `regexp.js` after
  `gbemu`: its `equals` call site is shared by the whole JVM.
- **Gain.** 200,000 calls, compiled, `VERSION_ECMASCRIPT`: `split` 268 ms → 146 ms, `replace` 166 ms → 108 ms,
  `exec` 119 ms → 96 ms (`VERSION_ES6` `split` 238 → 151 ms; Rhino 1.8.0: 120, 73, 68 ms). Octane `regexp.js`
  at steady state (best of 4 runs after one): 16.4 s → 14.7 s under `VERSION_ECMASCRIPT` (`VERSION_ES6` 14.2 s,
  Rhino 1.8.0 13.2 s); after the Octane files before it, as in the harness (one run after one), 17.8 s → 16.7 s
  (Rhino 1.8.0 13.7 s). The rest of the gap is in the matcher, without a single hotspot.

### Algorithms of built-ins

- **`Math.sumPrecise`** uses Shewchuk's algorithm (non-overlapping partials, with the overflow handling of the
  proposal's polyfill) rather than an exact `BigDecimal` sum, Nashorn's choice: the same results on 300,000 random
  and edge cases, 18 times faster on usual values (8 ms against 145 ms for a million values) and 2.7 times faster on
  values spread over the whole exponent range. `VERSION_ECMASCRIPT`.
- **Dense fast paths** of the array methods write the elements of a dense array directly. Under
  `VERSION_ECMASCRIPT`, those of `push` and `unshift` are taken only when no prototype has a property at the indexes
  written (`BACKWARD_COMPATIBILITY.md` 1.76), a check cached in the realm (see above).

## Tried and dropped

| Experiment | Result | Why |
|---|---|---|
| Skip the non-BMP code point list for BMP characters in regexp classes | level over 12 runs | JFR showed `ArrayList.contains` at 6–20%; the samples moved to the matcher's other frames |
| `Number` fast path at the start of `ScriptRuntime.toNumeric` | level | the JIT already folds `toPrimitive` for numbers |
| Cache the top-level scope of a function for its global `this` (`JSFunction.getThisObj`) | level, then +9% (`math-spectral-norm`) | the `getTopLevelScope` samples first moved to the method-handle call; once that was fixed, the larger `getThisObj` cost more than the walk it saved |
| A BMP fast path in the regexp matcher | +2% | below noise; `v8-regexp` +5% interpreted comes from the matcher's growth, with no single hotspot |
| Prototype-chain cache at every get site | richards 2× slower in the full run | code size and profile pollution at own-property sites; linked at inherited sites only |
| Devirtualizing `EmbeddedSlotMap.query` (exact-class check) | level | the cost is the chain of dependent loads in the lookup |
| Slot lookups without virtual calls: `EmbeddedSlotMap.query` called directly (at every lookup, or for reads only), `StandardSlot.keyMatches` called directly, string keys hashed and compared without a virtual call | `access-nbody` after the SunSpider files before it 3,150 → 2,300–2,900 ms, `v8-richards` +60–85% in the full gate run | after varied code the calls to `SlotMap.query` and `Slot.keyMatches` are megamorphic, so the lookup is no longer inlined (`access-nbody` is level with Rhino 1.8.0 alone, 2,090 ms, and 25% slower in the sequence); inlining it makes the runtime methods called from `v8-richards` too big to inline there (`propIncrDecr`, `getPropAndThis`). Every variant that helped `access-nbody` slowed `v8-richards`; the key part alone was level. Needs smaller lookups (shared shapes), not more inlining |
| A per-site cache of own slots by map | not worth it | maps belong to objects, not to shared shapes: a site only hits for a single object |
| Hash mixing of integral `Double` keys in `Map`/`Set` | slower | loses the locality of sequential keys |
| `putProperty` with one lookup (M16, 16.5): the receiver's map queried once, an own writable data property written directly | richards +58–74% in the gate, three versions | an exact `NativeObject` check with or without the prototype walk for new properties, with or without a method split to keep `putProperty` inlinable; own writes 48 ms → 20 ms and constructors 155 ms → 86 ms alone, but `v8-richards` (and once `sort`) regressed in the full gate run every time |
| Hidden classes (shared shapes) | not attempted | the largest remaining compiled cost (own-property lookups, about 15%), but a rewrite of the object model |

## Second pass (M16)

A code analysis (2026-10-19) of the call path, the object model, the code generated under `VERSION_ECMASCRIPT` and
the built-ins found the items listed in `MONFLABS_PLAN.md` (M16). Each item is documented here when it is done.
