# Performance optimizations

The fork adds ES2015 to ES2026 semantics to Rhino, and most of those semantics cost something: iterator protocols,
temporal dead zones, per-iteration bindings, species lookups, spec-exact object literals. The performance work of
the fork had two goals: pay for that without slowing existing code down, then go further where stock Rhino left
time on the table. This page describes how performance is measured, what was changed, where each change applies,
and what is known to be still slow.

For the language features themselves see [Features](features.md); for behavior changes between language versions
see [Changes](changes.md). The full engineering record, with the internal class and method names, is
[OPTIMIZATION.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/OPTIMIZATION.md) in the
repository.

## What "stock Rhino" means here

Three reference points appear in the figures below:

| Name | What it is |
|---|---|
| **Upstream base** | The upstream Rhino code the fork started from, tagged `perf-baseline` in the repository: upstream `master` of 2026-10-05 (`a2fd8fe11`), about 270 commits after the 1.9.1 release. The perf gate compares against it. |
| **Rhino 1.9.1** | The upstream release, from Maven Central. It is older than the upstream base, and faster on some object-heavy benchmarks (see [Where the fork is slower than Rhino 1.9.1](#where-the-fork-is-slower-than-rhino-191)). |
| **Previous commit** | Most figures on this page are before/after a single change, measured against the commit before it. |
| **Rhino 1.8.0** | The upstream release used by the external JavascriptPerformance harness, and quoted in a few micro-benchmarks as a reference. |

Language versions: `Context.VERSION_ECMASCRIPT` (250) is the default language version of a new `Context`, the
shell and `javax.script`. `Context.VERSION_ES6` (200) keeps the behavior of stock Rhino and must be selected
explicitly. "All versions" below means an optimization applies to both, and to the older versions too.

A figure such as "355 → 167 ms" is the time before and after one change, on the same machine, in the same run. The
absolute numbers come from an Apple M-series laptop and are only meaningful relative to each other.

## Principles

- **No observable change.** An optimization keeps the behavior of the engine, including the order of side
  effects, getters and setters, proxies and Java interop. When it depends on conditions (an unmodified built-in,
  a plain array, a function that does not read `this`), it checks them and takes the general path otherwise.
- **Not gated unless it has to be.** An optimization with no observable change applies to every language version
  it can serve. A few rely on semantics that only `Context.VERSION_ECMASCRIPT` has (for example, that code
  evaluated at runtime in a function's scope no longer sees its register variables); those apply to that
  version only, and are marked below.
- **Both backends.** Each optimization is done in the class compiler and in the interpreter unless noted. Several
  are compiled-mode only, because they work on generated JVM bytecode or on JIT inlining.
- **Measured.** Every engine change runs the perf gate before it is committed. A change that regresses a metric
  beyond its tolerance is reworked or dropped (see [Tried and dropped](#tried-and-dropped)).

## How performance is measured and guarded

### The perf gate

`buildtools/perf-gate.sh [base-ref]` compares the working tree with an earlier revision (by default
`perf-baseline`, the upstream base) and fails if any metric regressed beyond its tolerance.

- **Same harness, different engine.** The base revision is checked out in a temporary git worktree and only its
  engine jar is built. The harness and the benchmark scripts come from the working tree, so the two sides differ
  only in the engine.
- **116 metrics**, each in interpreted and compiled mode, and under `VERSION_ES6` and `VERSION_ECMASCRIPT`:
  - `startup.50scopes`: 50 `initStandardObjects()` calls;
  - `compile.earley-boyer`: compiling a 195 KB script;
  - `run.<script>`: steady-state execution of each script in `testsrc/perf` (ported from Nashorn's gate; each
    guards a hot path the ES2015+ work could slow down: global names, closures, prototype chains, `instanceof`,
    string building, typed arrays, regexps, ...);
  - `run.v8-<benchmark>`: the V8 benchmark functions, normalized to one call.
- **Method.** Inside a JVM a metric is the fastest of 10 runs after 20 warmup runs. The two sides run in
  alternating rounds (3 by default), one JVM per side and round, with a pinned heap (`-Xms1g -Xmx1g
  -XX:+UseG1GC`); each side's result is the median across rounds.
- **Tolerances are measured, not chosen.** `buildtools/perf-calibrate.py` simulates the gate's 3-against-3
  comparison 5,000 times over 12 recordings of identical code and takes the 99th percentile of the apparent
  regression, plus a 25% margin (minimum 10%). Most metrics have a 10–20% band. A deliberate regression of
  `instanceof` was caught at +15–19%; one of +4–8% was not. Metrics with several JIT states are not seen in the
  same proportions by every set of 12 recordings, so a recalibration only widens bands: each is the larger of
  the previous one and the new one (2026-10-29: 11 bands widened, `sort.cmp.ecma` 15% → 30%,
  `closures.cmp.es6` 10% → 15%, after both failed gates by chance).
- **Idle machine.** A full run takes about 18 minutes. The gate refuses to run during a Time Machine backup, and
  no build may run alongside it.

A subset can be run in minutes, and with more rounds for a finer judgement:

```bash
buildtools/perf-gate.sh HEAD                      # uncommitted changes only
PERF_ROUNDS=7 PERF_JAVA_OPTS='-Dperf.only=instanceof -Dperf.tolerance=0.05' buildtools/perf-gate.sh HEAD
```

### Pitfalls the gate accounts for

- **Bimodal metrics.** Some compiled-mode metrics (`properties`, `v8-richards`, `closures`, `wideobject`,
  `typedarray`, `split`, `v8-crypto`, `sort`) settle in one of two JIT states from one JVM to the next. A single
  round can flag a regression that is not there; these are confirmed with several rounds, alternating the two
  jars. `sort.cmp.ecma` (about 7.3 or 9 ms after the other scripts, a third of the runs slow on the same jar) has
  a two-state gap wider than its 15% band, so a 3-round gate fails on it by chance.
- **Profile pollution.** The gate runs all benchmarks in one JVM, so what ran before changes the JIT profile of
  what runs next. A change can look neutral on a benchmark alone and regress in the full run; several changes on
  this page were reshaped, or dropped, for that reason.
- **Method size.** A runtime method called from compiled scripts must stay small enough for the JIT to inline it.
  Adding a rare case to such a method can slow down the common one, so rare cases live in methods of their own.
- **Profilers mislead.** A hotspot that disappears from a JFR profile does not always shorten the run. Timings
  decide, not profiles.

### Other benchmarks

- **JMH benchmarks** in `benchmarks/` (`./gradlew jmh`): SunSpider, the V8 benchmarks, and micro-benchmarks of
  slot maps, property access and built-in object creation. `BENCHMARK=<regex>` and `EVALMODE=Compiler` or
  `Interpreter` narrow a run.
- **JavascriptPerformance**, an external harness, runs Octane, SunSpider, ubench and the V8 benchmarks on the
  fork (under `VERSION_ECMASCRIPT`), Rhino 1.8.0 (under `VERSION_ES6`), the Monflabs Nashorn fork, OpenJDK Nashorn,
  GraalJS and V8, each in its own JVM, in interleaved rounds. It wraps every SunSpider file in a function, which
  is how several of the optimizations below were found: code inside a function took slower paths than top-level
  code.

## Calls and functions

### Compiled functions called without a method handle

Upstream calls a compiled function through a `MethodHandle` held in a field. The JIT inlines through a method
handle only when it is a constant, so every call that was not a direct call was a real, non-inlined call. On its
first call, a function now gets a small generated class that calls the compiled method directly.

- **Applies to:** compiled mode, all versions; methods, closures, functions passed as values.
- **Gain:** SunSpider `math-spectral-norm` (in the JavascriptPerformance wrapper) 2,790 → 2,500 ms.

### Direct calls

A call to a function declaration that is never reassigned compiles to a static call of its method, guarded by a
check that the name still holds that function, with a regular call otherwise.

- **Arguments evaluated once.** The arguments were generated in both branches of the guard; they are now
  evaluated once into locals. This brought a recursive `fib` back under the 325-byte limit at which the JIT
  inlines a hot callee. Ten runs of `fib(28)`: 100 → 83 ms (upstream base: 133 ms; Rhino 1.8.0: 55 ms).
- **Nested declarations.** Direct calls covered the top-level declarations of a script only. A call inside a
  function now also targets a declaration of an enclosing function. This matters for code wrapped in a function
  (a module pattern, an IIFE): the shared call path becomes megamorphic once a JVM has run other scripts, while a
  direct call is a static call nothing else can pollute. SunSpider in one JVM: `math-spectral-norm` 2,806 →
  2,296 ms, `controlflow-recursive` 3,534 → 2,591 ms, `crypto-md5` 877 → 737 ms.
- **Applies to:** compiled mode, all versions. A name that holds something else at run time fails the guard and
  takes the regular call.

### Calls that skip computing `this`

A call of a non-strict function computes its `this`: for an undefined `this`, a walk up the scope chain to the
global object; for a primitive, a wrapper object. A function whose body (and arrow functions) contains no `this`,
`super` or direct `eval` cannot observe it, so the engine no longer computes it.

- **Applies to:** both modes, all versions; not arrow functions, generators or async functions. The `this` is
  still computed when a debugger is attached.
- **Gain:** on direct calls, `math-spectral-norm` alone 635 → 400 ms; on other calls (callbacks of `map`,
  `reduce`, `forEach`, calls through a variable) 122 → 106 ms compiled, 1,100 → 1,060 ms interpreted.

### Function objects created with their properties laid out

Creating a function installed `length`, `name`, `arguments`, `caller` and `prototype` one by one, through several
maps, and created the prototype object eagerly. A function now gets its final property map at once, and its
prototype object is created when `prototype` is first read.

- **Applies to:** both modes. The single layout: functions created under `VERSION_ECMASCRIPT`, and non-strict code
  under `VERSION_ES6`. The lazy prototype: every ordinary function that is not a generator.
- **Gain:** 1 million closures created, compiled: 355 → 167 ms (`VERSION_ECMASCRIPT`), 505 → 187 ms
  (`VERSION_ES6`); gate `closures` −28% compiled, −11% interpreted.

### The `arguments` object of strict functions

Using `arguments` gave a function an activation object and moved all its variables into it. Under
`VERSION_ECMASCRIPT`, a strict function that needs no activation for anything else keeps its (unmapped)
`arguments` object in a plain variable, and its variables in registers.

- **Applies to:** both modes, `VERSION_ECMASCRIPT`.
- **Gain:** 1 million calls, compiled: a strict function summing `arguments` 818 → 200 ms (Rhino 1.8.0: 735 ms; a
  rest parameter: 74 ms), passing it to `apply` 1,194 → 341 ms. Interpreted: 1,909 → 1,069 ms.

### Number parameters and return values

The optimizer keeps variables that only ever hold numbers in unboxed `double` registers. Upstream ran it on the
top-level functions of a script only; it now runs on every function, and goes further:

| Change | Gain (SunSpider, compiled) | Versions |
|---|---|---|
| Nested functions optimized like top-level ones | `math-spectral-norm` 1,839 → 745 ms, `bitops-3bit-bits-in-byte` 18,804 → 6,086 ms, `access-fannkuch` 741 → 414 ms, `3d-morph` 1,258 → 765 ms | all |
| Parameters of a nested declaration passed as doubles when every call passes a number, recursive functions included | `controlflow-recursive` 1,130 → 1,000 ms | all |
| A nested declaration that always returns a number returns an unboxed double to its direct calls | `fib` and `tak` in a wrapper 74 → 61 ms | all |
| Number variables of functions with an activation object (the ones in registers) | `controlflow-recursive` in sequence 1,143 → 925 ms | `VERSION_ECMASCRIPT` |

These apply in compiled mode only, and only to nested function declarations that nothing but direct calls can
reach: not a top-level declaration (a global that other scripts and the host can call), a function used as a
value, called with a spread or another number of arguments, or one with a rest or destructured parameter.

### Compiled generators complete without a stack trace

A compiled generator signals completion with an internal exception, whose construction walked the Java stack and
formatted a message: about 10 µs per completed generator or async call, five times the interpreter's cost. It is
now a lightweight exception without a stack trace.

- **Applies to:** compiled mode, all versions.
- **Gain:** 30,000 calls: a generator returning a value 333 → 7 ms, an async function 365 → 13 ms, `for-of` over
  a generator 49 → 13 ms.

## Variables and closures

### Cache of top-level names

A name in top-level code (`x`, `Math`, `f()`) is resolved through a per-site cache that holds the slot of the
global object, or of the global `let`/`const` declarations, where the name was found. A slot that leaves its map
is marked stale, which the cache checks.

- **Applies to:** both modes, all versions; also reads of global names from nested functions (the scopes on the
  way are still checked, so a name that `eval` adds to an activation is found as before), and writes of
  initialized global `let` bindings.
- **Gain:** on the gate scripts, `typedarray` −42% to −66%, `toprimitive` −31% to −50%, `strbuild` −34% to −49%,
  `instanceof` −31% to −44%. It also removed a 10–25% slowdown that `VERSION_ECMASCRIPT` had on top-level code.
  From a nested function, 3 million reads of `Math.abs`: 205 → 88 ms compiled. 3 million `G++` on a global `let`:
  140 → 12 ms compiled.

### Variables in registers in functions with closures

A function that needs an activation object (because of a closure, a `catch` clause, `super`, ...) used to keep
every parameter and variable in it, each access a name lookup. Only the names that nested functions refer to
stay in the activation now; the rest, `let` and `const` included, become registers, and a block whose bindings
are all registers gets no scope object.

- **Does not apply** with a direct `eval` or `with` in the function or a nested function, a mapped `arguments`
  object, in generators, or past 256 such variables (the JVM's local limit).
- **Applies to:** both modes, `VERSION_ECMASCRIPT` only: code evaluated at runtime in the function's scope other
  than a direct `eval` (a legacy `Script` object, a host function) no longer sees register variables (see
  [Changes](changes.md)).
- **Gain:** a function with a `catch` clause and 7 variables, called 3 million times: 592 → 87 ms compiled,
  1,295 → 866 ms interpreted. A `let` loop in a function with a closure: 84 → 6 ms compiled; interpreted 1.5 to
  2.9 times faster.

### Captured variables at a fixed position

The variables that nested functions do capture are read and written at their index in the activation object,
both by the function itself and by its nested functions (which find the owner's activation by its descriptor on
the scope chain, then check the name). In compiled mode, a nested function finds that activation once per call
and keeps it in a register. Function declarations referred to by nested functions get a position too.

- **Applies to:** both modes, `VERSION_ECMASCRIPT` (the activation lookup once per call: compiled mode).
- **Gain:** a loop updating a captured variable 143 → 65 ms compiled (58 ms without a closure); a closure reading
  and writing captured variables in a loop 202 → 50 ms compiled, 571 → 517 ms interpreted.

### Per-iteration bindings only when a closure can see them

A `let`/`const` loop head gets a fresh binding per iteration, so that each closure sees its own value. When the
bindings live in a scope object, the copy is now emitted only if the loop creates a function or class, or calls
`eval` directly; otherwise nothing can observe that the iterations share one scope.

- **Applies to:** both modes, `VERSION_ECMASCRIPT`.
- **Gain:** 3 million iterations of a top-level `let` loop: 271 → 180 ms compiled, 441 → 360 ms interpreted.

## Properties and objects

### Per-site cache of inherited properties

A named property read or method lookup (`o.p`, `o.m()`) whose property is found on the prototype chain gets a
per-site cache: for a receiver of the cached class without an own property of that name, the cached slot of the
chain is used directly. Adding or removing a property of an object in a cached chain, replacing its map or
changing a prototype increments a global epoch that every cache checks.

- **Applies to:** both modes, all versions. In compiled mode it is linked only at sites whose first receiver
  inherits the property; own-property sites (`this.x`) keep the direct path. Only plain slot maps are cached,
  not the thread-safe ones; a site gives up after 64 refills (megamorphic sites).
- **Gain:** 3 million accesses three levels up, compiled: a method 66 → 41–54 ms, a property 45 → 18–23 ms;
  gate `protochain` −28% compiled, −13% interpreted.

### Object literals

Under `VERSION_ECMASCRIPT`, object literal properties are created with define-property semantics (so that a data
property replaces an accessor defined earlier in the same literal), which cost a descriptor and two lookups per
property. A plain literal with no getter or setter, checked once per literal, now puts its properties directly.

- **Applies to:** both modes, `VERSION_ECMASCRIPT`.
- **Gain:** 1 million `{x, y}` literals 61 → 44 ms compiled; interpreted 10–15% faster.

### Method calls on primitive strings

`s.m()` on a primitive string created a `String` wrapper object to look `m` up and pass it as `this`. Under
`VERSION_ECMASCRIPT` the method is found on `String.prototype` directly and called with the primitive, as the
specification says; the wrapper is created only if something asks for it.

- **Applies to:** both modes, `VERSION_ECMASCRIPT` (`VERSION_ES6` keeps the wrapper, since its built-ins observe
  an overridden `String.prototype.toString`).
- **Gain:** `indexOf`, `charCodeAt`, `slice` and `startsWith`, 3 million times: 598 → 425 ms compiled, 1,425 →
  1,099 ms interpreted.

## Arrays and iteration

### Built-in iterators without result objects

The engine's own consumers of iterables (spread, `for-of`, destructuring, `Array.from`, the `Map`/`Set`
constructors, `Promise.all`) step a built-in iterator whose `next` is the original one without calling it and
without creating `{ value, done }` objects, and read a plain array by index without creating an iterator at all.
An iterator whose `next` was replaced takes the general path.

- **Applies to:** both modes, `VERSION_ECMASCRIPT` (it relies on iterator results being ordinary objects, a spec
  correction listed in [Changes](changes.md)).
- **Gain:** spreading an 8-element array 200,000 times: 170 → 19 ms compiled, 184 → 38 ms interpreted;
  `for (const [k, v] of map)` 39 → 19 ms.

### Destructuring patterns

An array pattern that only binds names in a `let`/`const` declaration or a parameter list no longer needs the
`try`/`finally` that closes its iterator on an abrupt completion, plain names are assigned without temporaries,
and a plain array with its original iteration methods is recognized from cached slots.

- **Applies to:** both modes, `VERSION_ECMASCRIPT` (older versions read the elements by index, as before).
- **Gain:** 300,000 `let [x, y, z] = a`: 7 → 4 ms compiled, 130 → 58 ms interpreted; `for (const [k, v] of
  pairs)` 71 → 43 ms interpreted.

### Dense element access per call site

Compiled `a[i]` and `a[i] = v` are linked, per call site, to small methods that read or replace a dense element of
a `NativeArray` directly, with the general path for holes, growth, accessors, frozen and non-extensible arrays,
and other receivers. Being per site, they stay inlinable after other scripts have run.

- **Applies to:** compiled mode, all versions.
- **Gain:** SunSpider `access-fannkuch` 1,319 → 830 ms, `bitops-nsieve-bits` 1,175 → 934 ms.

### Arrays stay dense across small gaps

An array left its dense storage for good on the first write beyond 1.5 times its capacity (an array filled from
its last index down did so at once). A write below index 1,024 now grows the dense storage instead, at a cost of
up to 4 KB of holes per array.

- **Applies to:** both modes, all versions.
- **Gain:** `v8-crypto` 46–52 → 36 ms compiled; a 300-element array filled downward 26 → 13 ms.

### Array species, `push` and `unshift`

`slice`, `splice`, `concat`, `map`, `filter`, `flat` and `flatMap` look up `constructor` and `Symbol.species` to
create their result. When the array is plain and those are the original ones (checked from cached slots, without
calling the getter), the result is created directly. Likewise, the check that no prototype has an element at the
indexes `push` and `unshift` write is cached per realm and invalidated through the same epoch as the property
cache.

- **Applies to:** both modes. Species: all versions; `push`/`unshift`: `VERSION_ECMASCRIPT` (older versions never
  check).
- **Gain:** 200,000 calls, compiled: `concat` 41 → 24 ms, `slice` 22 → 17 ms; 4 million calls: `unshift(x)` 400 →
  150 ms, `push(x, y, z)` 82 → 55 ms.

## Strings, numbers and built-ins

### Integers convert to strings without the double formatter

Every number converted to a string (concatenation, `String()`, `join`, JSON, property keys) went through the
shortest-digits double formatter, about 29 ns against 5 ns for `Long.toString`. An integer, or an integral double
up to 2^53 − 1 in magnitude (except `-0`), now takes the integer path; above that bound the digits differ, so it
stops there.

- **Applies to:** both modes, all versions.
- **Gain:** 1 million `"x" + i` 22 → 7 ms compiled; `join` of 1,000 integers 40 → 14 ms.

### RegExp symbol methods on an ordinary regexp

`split` read nine flag getters, looked up the species constructor and compiled a new sticky splitter on every
call; `replace`, `match` and `matchAll` read `flags` too. For an ordinary regexp (same criteria as Nashorn: its
own prototype, no own property but `lastIndex`, and the relevant `RegExp.prototype` properties unchanged), the
flags come from the compiled pattern and the splitter is built once per pattern and flags.

- **Applies to:** both modes, all versions (the `lastIndex` shortcut: `VERSION_ECMASCRIPT`).
- **Gain:** 200,000 calls, compiled: `split` 268 → 146 ms, `replace` 166 → 108 ms, `exec` 119 → 96 ms (Rhino
  1.8.0: 120, 73, 68 ms). Octane `regexp.js` at steady state: 16.4 → 14.7 s (Rhino 1.8.0: 13.2 s). The rest of
  the gap is in the matcher, with no single hotspot.

### Other built-ins

- **Lazy built-ins stay lazy.** Initializing a scope built every lazily loaded constructor (`Map`, `Set`,
  `Promise`, `Proxy`, typed arrays, ...), about 45% of the initialization. They are built on first use again:
  `startup.50scopes` −33% (`VERSION_ECMASCRIPT`) and −42% (`VERSION_ES6`). This matters to embedders that create
  many scopes.
- **`Math.sumPrecise`** uses Shewchuk's algorithm rather than an exact `BigDecimal` sum, as Nashorn does: 18 times
  faster on usual values (8 ms against 145 ms for a million values).

## Large scripts and generated code

Stock Rhino compiles a script to one class, and a function to one method. When either exceeds a class file limit
(64 KB of bytecode per method, a jump over 32 KB, 65,535 constant pool entries, the invokedynamic site limit), the
whole script silently runs in the interpreter. The fork's richer generated code made this happen more often
(Octane `regexp.js`), and generated code such as asm.js output hits it anyway. The class compiler now recovers
instead:

| Limit hit | What the compiler does | Gain |
|---|---|---|
| A function or the top-level code is too large | Re-parses with that function split into chunks of about 600 nodes, each an arrow function called in place; halves the size on each new failure, up to 3 times | Octane `regexp.js` compiled again: score 460 → 606 (Rhino 1.8.0: 538) |
| The class is too large (constant pool, invokedynamic sites) | Spreads the functions over several classes, doubling the count on each failure, up to 256 | Octane `mandreel` over 16 classes: score 628 → 826 |
| A literal of more than 1,000 constants | Compiles it as text parsed once into a cached template | Octane `pdfjs` compiles under `VERSION_ECMASCRIPT`: score 2,100 → 4,505 |

- **Applies to:** compiled mode, all versions, only after a failure: code that fits is compiled as before.
- **Not split:** generators and async functions, derived constructors, functions with a direct `eval` or `with`.
- **Invisible to scripts:** chunks share `this`, `arguments`, `new.target` and `super`; `return`, `break` and
  `continue` across a chunk are replayed at its call site; script stack traces show chunk frames as the
  function's. With splitting forced on every function, test262 and the Mozilla tests pass except one test of
  `f.arguments` and two bytecode tests.
- A remaining fallback to the interpreter is reported as a warning rather than silent.

## Practical guidance

- **Prefer compiled mode for long-running or hot code.** Many optimizations on this page are compiled-mode only:
  direct calls, unboxed numbers, per-site element access, the method-handle removal. The interpreter benefits
  from the others (name and property caches, registers, iterators, built-ins), and starts faster.
- **The default version is the fast one for modern code.** `VERSION_ECMASCRIPT` gets the optimizations that rely
  on its semantics: closures, `arguments`, destructuring, spread and iteration, string method calls and
  per-iteration bindings. On top-level code both versions run the gate scripts at the same speed. It is not faster
  everywhere: Octane `regexp.js` runs at 14.7 s under `VERSION_ECMASCRIPT` against 14.2 s under `VERSION_ES6`.
- **Code shapes that benefit most:**
  - functions wrapped in a function (module pattern, IIFE): nested declarations get direct calls and unboxed
    numbers like top-level ones;
  - numeric code in nested function declarations called by name, with number arguments;
  - closures that capture only a few variables: the rest stay in registers;
  - strict functions using `arguments`, or rest parameters (faster still);
  - dense arrays with plain prototypes and unmodified built-ins.
- **Things that turn optimizations off** for the function involved: a direct `eval` or a `with` statement; a
  mapped `arguments` object (non-strict with parameters); reassigning a function declaration or passing it as a
  value (no direct call, no unboxed parameters); replacing iteration, species or `RegExp.prototype` methods;
  holes, accessors or frozen state on arrays. The behavior is unchanged in each case; only the fast path is lost.
- **Many scopes:** creating a scope with `initStandardObjects()` is cheaper since built-ins load lazily.

## Tried and dropped

Experiments that measured no gain, or a regression elsewhere, and were not kept:

| Experiment | Result | Why |
|---|---|---|
| Prototype-chain cache at every property site | `v8-richards` 2 times slower in the full run | code size and profile pollution at own-property sites; kept at inherited-property sites only |
| `putProperty` with one lookup (own writable property written directly) | own writes 48 → 20 ms alone, but `v8-richards` +58–74% in the gate | regressed in the full run in every variant tried |
| Slot lookups without virtual calls (several variants) | helped `access-nbody`, slowed `v8-richards` by 60–85% | inlining the lookup made other runtime methods too big to inline; needs smaller lookups, not more inlining |
| Per-site cache of own slots by map | not worth it | each object has its own map, so a site only hits for a single object |
| Devirtualizing the slot map query | level | the cost is the chain of dependent loads in the lookup |
| Caching the top-level scope for a function's global `this` | level, then +9% | made the call path larger than the walk it saved; superseded by skipping `this` entirely |
| Five-slot immutable map for function objects | creation faster, `instanceof` 15–20% slower | one more map kind at property reads; a hash table is level |
| `Number` fast path in `toNumeric` | level | the JIT already folds it |
| BMP fast paths in the regexp matcher and its character classes | level to +2% | below noise; samples moved to other frames |
| Hash mixing of integral `Double` keys in `Map`/`Set` | slower | loses the locality of sequential keys |
| Hidden classes (shared shapes) | not attempted | a rewrite of the object model, see below |

## Where the fork is slower than Rhino 1.8.0

Across the JavascriptPerformance run of 2026-10-26 the fork is 1.62 times faster than Rhino 1.8.0 (geometric mean
of 60 files, compiled mode). A few files are slower:

| File | Fork vs 1.8.0 in the harness | Cause |
|---|---:|---|
| SunSpider `access-nbody` | 0.96 | the harness sequence |
| SunSpider `crypto-md5` | 0.86 | the harness sequence |
| Octane and V8 `regexp` | 0.82, 0.90 | the harness sequence |
| SunSpider `regexp-dna` | 0.94 | the regexp matcher |
| Octane and V8 `splay` | 0.82, 1.06 | object creation |
| Octane and V8 `deltablue` | 0.94, 0.86 | property lookup and calls |

(Above 1 is faster.) To separate the engine from the harness, each file was then run alone, one JVM per engine
and file, in 3 interleaved rounds, with the same heap and options as the harness. The figure is the median of the
best of 5 passes, in milliseconds, compiled mode:

| File | Rhino 1.8.0 | Rhino 1.9.1 | Fork, `VERSION_ES6` | Fork, `VERSION_ECMASCRIPT` | Fork vs 1.8.0, alone |
|---|---:|---:|---:|---:|---:|
| `access-nbody` | 3,732 | 2,240 | 2,181 | 2,208 | 1.69 times faster |
| `crypto-md5` | 1,986 | 1,423 | 1,408 | 1,399 | 1.42 times faster |
| Octane `regexp` | 1,109 | 1,187 | 1,072 | 1,114 | level |
| `regexp-dna` | 1,797 | 1,836 | 2,028 | 1,908 | 6% slower |
| Octane `splay` | 421 | 433 | 473 | 464 | 10% slower |
| Octane `deltablue` | 572 | 606 | 732 | 737 | 29% slower |

### The harness sequence

Alone, the fork is faster than 1.8.0 on `access-nbody` and `crypto-md5`, and level on `regexp`. The harness runs
every file of every suite in one JVM per engine, so the files that ran first have already profiled the call sites
that every property lookup shares (the slot map query and its key comparison) and that the regexp matcher shares
with many receiver types. Those sites become megamorphic, the JIT stops inlining them, and the same code runs
slower late in the sequence than alone. This is [profile pollution](#pitfalls-the-gate-accounts-for): `access-nbody`
is level with 1.8.0 alone and about 25% slower after the other files, and forcing the lookup inline helped it but
slowed `v8-richards` (see [Tried and dropped](#tried-and-dropped)). For `regexp`, in an earlier full run, 1.8.0 took 13.7 s and the fork
16.7 s after the Octane files, with a megamorphic `ArrayList.contains` in the matcher.

### Slower in the engine

`deltablue`, `splay` and `regexp-dna` are slower alone too, under both language versions, so the cost is not in the
ES2015+ semantics that `VERSION_ECMASCRIPT` adds but in engine changes that apply to every version. Part of it is
upstream: Rhino 1.9.1 is already 6% slower than 1.8.0 on `deltablue`, and the upstream base of the fork, later
than 1.9.1, slower again (see [Where the fork is slower than Rhino 1.9.1](#where-the-fork-is-slower-than-rhino-191)).

JFR profiles, fork against 1.9.1 (sampled, so the percentages are approximate):

- **`deltablue`** (21% slower than 1.9.1) is about three quarters property lookup in both engines, and the fork
  does more of it:
  - the key comparison of the slot lookup (`StandardSlot.keyMatches`, reached through `EmbeddedSlotMap.query`)
    takes 47% of the samples, against 24% in 1.9.1. Part of that is likely the memory latency of the slot it
    loads rather than the comparison itself;
  - the [per-site cache of inherited properties](#per-site-cache-of-inherited-properties) queries the receiver's
    own slot map on every hit before it uses the cached slot. That pays on deep prototype chains; `deltablue`'s
    objects are small with shallow chains, so the check is mostly extra work (about 8%, plus its lookups);
  - a non-strict function computes its `this` on every call as the specification requires
    (`JSFunction.getThisObj` → `ScriptRuntime.toObject`), about 5% on a benchmark made almost entirely of method
    calls.
- **`splay`** (8% slower than 1.9.1) creates objects, and creation costs more: growing a new object's slot map from
  a single entry to a hash table (`SingleEntrySlotMap` → `EmbeddedSlotMap`, 6%), finding the built-in prototype
  of each new object (12%), and the check of each literal for getters and setters (2%). Garbage collection is not
  the difference: the GC pauses were similar, slightly shorter on the fork.
- **`regexp-dna`** (6%) is in the regexp matcher, spread over it with no single hotspot.

### What would close it

Most of these costs come from the object model: each object has its own slot map, so every property read is a hash
lookup, which is also what makes the lookup sites sensitive to the harness sequence. Shared shapes, described
below, would address them together. Smaller changes could be tried first, each measured in the full perf gate
since earlier changes of this kind regressed `v8-richards`:

- skipping the own-map check of the inherited-property cache when the receiver's map has not changed;
- not calling `toObject` when `this` is already an object;
- comparing keys inline in the slot map that dominates the profile (done: see
  [Where the fork is slower than Rhino 1.9.1](#where-the-fork-is-slower-than-rhino-191));
- sizing the slot map of an object literal for its properties up front.

## Where the fork is slower than Rhino 1.9.1

In the JavascriptPerformance run of 2026-10-28/29 against Rhino 1.9.1 (compiled mode, 3 rounds, fork at
`36f03fef4`), the fork is 1.45 times faster under `VERSION_ES6` and 1.6 times faster under `VERSION_ECMASCRIPT`, but
slower on a few object-heavy files: `access-nbody` ×1.9–2.1, `box2d` ×1.3–1.5, `richards` ×1.24, `gbemu`
×1.15–1.3, `deltablue` and `raytrace` up to ×1.17.

The fork does not start from 1.9.1 but from upstream `master` of 2026-10-05, about 270 commits later. To separate
the two, each file was run alone (one JVM, best of 2 runs after 1, `VERSION_ES6`) on 1.9.1, on that upstream base
and on the fork, in milliseconds:

| File | Rhino 1.9.1 | Upstream base | Fork | Where the difference comes from |
|---|---:|---:|---:|---|
| `access-nbody` | 2,052 | 3,989 | 2,133 | the harness sequence: level alone |
| `gbemu` | 8,283 | 9,011 | 8,031 | the harness sequence: faster alone |
| `box2d` | 12,799 | 18,486 | 18,098 | upstream, one commit (below) |
| `richards` | 28,172 | 34,846 | 35,659 | upstream |
| `raytrace` | 29,667 | 32,934 | 32,850 | upstream |
| `deltablue` | 26,238 | 30,630 | 33,140 | upstream, and about 8% in the fork |

### The upstream commit

Bisecting the upstream commits on `box2d` finds a single step: c1ac6c149, "Start using the new scopes hierarchy
internally" (2025-12-07), 12.9 s → 18.2 s. It made scopes `ScopeObject`s, apart from `ScriptableObject`. Two
effects were measured:

- **The key comparison of the slot lookup.** `SlotMapOwner.get` now serves both kinds of object, and the
  `Objects.equals` of `EmbeddedSlotMap.query` is no longer inlined (24 JFR samples out of line before, 257
  after). Out of line, its type profile is shared by every caller in the JVM. The fork now compares keys by
  identity, then with `equals` called from the slot itself: `deltablue` about 18% faster, `richards` 6–10%,
  `box2d` level.
- **Type guards that call into the JVM.** The guards of the linkers (`Guards.getInstanceOfGuard(Double.class)`
  and the like) are method handles of the native `Class.isInstance`. Inlined, the JIT turns it into a type check;
  at a call site it did not inline, every guard is a JNI call, and on macOS/AArch64 each one also switches the
  thread's code memory between writable and executable twice (`pthread_jit_write_protect_np`). JFR does not see
  this time; the macOS `sample` tool on the main thread does: about 35% of `box2d` before the commit, 48% after,
  about 4.3 of the 5.5 s it lost. The commit made more call sites miss the JIT's inlining budget.

  In `box2d` they are the conversions of the `DoubleLinker` (to boolean, number, numeric, int32 and uint32). Each
  of them takes any value, so they are now linked without a guard: the method converts a double directly and
  anything else through `ScriptRuntime`, and the site never relinks. `box2d` 18.4 s → 9.1 s (Rhino 1.9.1:
  12.8 s); `access-nbody` unchanged (median 2,086 → 2,093 ms over 10 runs, 9 in 10 in its fast JIT state on both);
  `richards`, `deltablue` and `raytrace` level. Guards written as static `instanceof` methods were tried first:
  `box2d` 10.1–11.4 s, but `access-nbody` settled in its slow state 3 to 5 times in 10.

  On Linux/x86, without the code-memory switch, the native calls cost much less; the gain there has not been
  measured.

### What the fork pays on its own

`raytrace` was bisected the same way, to upstream 80cc8c60d ("Reduce heap space used by built in slots", 30.1 s →
32.7 s), whose cost is the key comparison above. With that fixed, the fork was still about 6% slower than 1.9.1 on
`raytrace`, from three costs of its own:

- **Slot maps grown on every new object.** An object starts with a shared empty map, then a single-entry map,
  and is copied into a hash table at its second property; 1.9.1 starts it with a hash table. Objects created by
  `new` now start with one (the table itself is still allocated at the first property): `deltablue` −9 to −12%
  (now about 10% faster than 1.9.1), `splay` −2 to −5%, `raytrace` −1.5 to −4% (within about 2% of 1.9.1),
  `richards`, `box2d` and `access-nbody` level, 3 rounds alone.
- **Strings hashed again.** Property names reach the slot maps as new `String` objects in some paths, whose hash
  is computed at each lookup (`StringLatin1.hashCode`, about 2% of `raytrace`). Not yet traced.
- **`arguments` objects**, created by `Class.create`'s `this.initialize.apply(this, arguments)` (about 2%).

### Also seen in `box2d`

The [per-site cache of inherited properties](#per-site-cache-of-inherited-properties) deoptimizes up to 74
times while `box2d` warms up, in up to 64 compiled methods: the validity check of the cache
(`PropertyCache.inherited`) is compiled as never failing, and fails when a prototype changes. Each deoptimization
discards the compiled script function the check was inlined in. It is a warm-up cost only: over 7 runs, the
deoptimizations of the whole engine fall from 201 and 73 in the first two to none from the fifth (those of the
cache: 6, 14, 1, then none), and the runs settle at 8.95–9.0 s from the fourth.

## Known gap and future work: the object model

Property access on objects is the largest remaining gap with Nashorn. In the JavascriptPerformance run of
2026-10-26 (compiled mode, 3 rounds):

| Octane benchmark | Monflabs Nashorn | Rhino 1.8.0 | Rhino fork |
|---|---|---|---|
| `richards` | 5.0 s | 50.3 s | 42.2 s |
| `deltablue` | 5.8 s | 31.0 s | 33.0 s |
| `raytrace` | 6.5 s | 41.4 s | 38.3 s |

The fork is slightly ahead of Rhino 1.8.0 on `richards` and `raytrace`, and behind on `deltablue` (see
[Where the fork is slower than Rhino 1.8.0](#where-the-fork-is-slower-than-rhino-180)). Own-property lookups are
the largest compiled cost left, about 15%. In Rhino each object has its own slot map (a hash table), so a call site cannot remember where a property
is: every attempt to make the lookup itself cheaper either gained nothing or regressed `v8-richards` (see the
table above).

Nashorn shares a property map among the objects that have the same properties added in the same order, and its
call sites link against that map, so a property read becomes a map check and a field load. Bringing shared shapes
to Rhino is identified but not scheduled. It would mean:

- profiling `richards`, `deltablue`, `raytrace` and the ubench property tests to measure the share of slot
  lookups, map growth and prototype walks;
- prototyping shared shapes for `NativeObject` and constructor-built objects, with a per-site cache keyed by
  shape, measured on those files and in the full perf gate;
- working within what it constrains: the public `ScriptableObject` and `SlotMap` API, embedders that subclass
  `ScriptableObject`, the thread-safe slot map, and both backends.

It is a large change, a rewrite of the object model. Since it would not be observable, it would not be gated by
language version.
