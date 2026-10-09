# MONFLABS changelog

Changes made in the `org.monflabs.mozilla` fork of Rhino, newest first. Each entry says what changed, why, which
test262 slice moved, which behavior is gated by `Context.VERSION_ECMASCRIPT`, and the performance impact when
relevant. The plan these changes follow is in [MONFLABS_PLAN.md](MONFLABS_PLAN.md).

## Unreleased

### Temporal dead zone of parameters (2026-10-08, plan 1.2f)

Under `VERSION_ECMASCRIPT` (`BACKWARD_COMPATIBILITY.md` 1.2b):

- A default parameter value reading its own parameter or a later one throws a ReferenceError (`IRFactory`
  replaces such reads in the desugared default by the TDZ error node; closures are not affected).
- test262: 14 more files pass; 25,004 of 42,616 in-scope files (58.7%).
- The rest of the planned 1.2f (separate parameter/body environments, eval lexical environment, immutable
  function-expression name, `new.target` in eval) becomes 1.2g, after 1.3.

### Eval code declarations (2026-10-08, plan 1.2e)

All versions (`BACKWARD_COMPATIBILITY.md` 2.12):

- A sloppy direct eval in a parameter list may not declare a `var`/function named like a parameter or `arguments`
  (SyntaxError). The parser marks such calls (`Node.SPECIALCALL_EVAL_IN_PARAMS`); the eval code is compiled with
  the forbidden names (`CompilerEnvirons.setEvalForbiddenVarNames`), checked against its top-level symbols.
- An indirect eval is never strict mode code.

Under `VERSION_ECMASCRIPT` (`BACKWARD_COMPATIBILITY.md` 1.4):

- A `var`/function of sloppy eval code may not reuse the name of a `let`/`const` of an enclosing block or of a
  global lexical declaration (SyntaxError).
- Global functions and vars of eval code are checked (CanDeclareGlobalFunction/Var) before any is created; a
  function over a configurable global redefines it.
- test262: 113 more files pass (`language/eval-code`); 24,990 of 42,616 in-scope files (58.6%).
- `indirect/lex-env-distinct-let.js` and `-const.js` now fail in strict mode too: they passed only because indirect
  eval inherited strictness. Eval code's own lexical environment is plan 1.2g. Async variants wait for M3.

### Function.prototype.toString (2026-10-08, plan 1.2d)

All versions (`BACKWARD_COMPATIBILITY.md` 2.11):

- The source text of a method, getter or setter starts at its key (`get f() {}`, `*g() {}`, `[k]() {}`).
- `Function`/`GeneratorFunction` build their source as CreateDynamicFunction does (`"\n) {\n"` and `"\n}"`),
  so a `//` comment ending the parameters works; `function*` prefix. Error line numbers in the body move by 2.
- `Function.prototype.toString` on a callable proxy gives a NativeFunction string; a built-in whose name is not an
  identifier leaves it out.
- Removed the unused internal `[Symbol.getSize]` property of `Map.prototype` and `Set.prototype`.
- test262: 16 more files pass; 24,877 of 42,616 in-scope files (58.4%). Class members wait for 1.7.

### Function early errors (2026-10-08, plan 1.2c)

All versions (simplest; `BACKWARD_COMPATIBILITY.md` 2.7 and 2.10):

- The parameter list is checked once the body is parsed (`Parser.checkParamsAfterBody`), so a `"use strict"` body
  applies to it: `eval`/`arguments` as the function's name or a parameter, and duplicate parameters, are SyntaxErrors.
  Duplicates are also rejected in methods and with a non-simple list (defaults, rest, patterns).
- `"use strict"` with a destructured parameter is a SyntaxError; a strict `eval = …` / `arguments = …` is reported by
  the parser (a SyntaxError, also from the `Function` constructor, instead of an `InternalError`).
- `Function.prototype.arguments`: a TypeError for strict functions and built-ins, like `caller`.
- Rest parameters with a binding pattern (`...[a, b]`, `...{length}`), in functions and arrows; destructured
  parameters are bound left to right.
- `arguments` used only in a default value is available.
- test262: 83 more files pass (`language/statements|expressions/function`, `arrow-function`, `built-ins/Function`,
  `rest-parameters`); 24,861 of 42,616 in-scope files (58.3%).
- Left for 1.2f: default-parameter TDZ, the separate body scope, `eval` in parameters, the immutable name of a
  function expression. Left for 1.6: `yield` as an identifier in sloppy code.

### The arguments object (2026-10-08, plan 1.2b)

All versions (simplest; `BACKWARD_COMPATIBILITY.md` 2.9):

- `Arguments` follows the spec's arguments exotic object: the indexed values are ordinary own data properties created
  with the object, and a separate parameter map links each index to its parameter. `[[Get]]`, `[[Set]]`,
  `[[GetOwnProperty]]`, `[[DefineOwnProperty]]` and `[[Delete]]` follow ECMA-262 10.4.4 (a redefinition keeps the
  attributes it does not mention; making an index non-writable or an accessor, or deleting it, unmaps it).
- Functions with a rest parameter get an unmapped `arguments` object.
- The indexed values stay in the argument array until an index is redefined or deleted (a call with 2^24
  arguments must not create 2^24 slots); only that index becomes a slot.
- test262: 30 more files pass (`language/arguments-object`, `rest-parameters`, `built-ins/Object`); 24,778 of
  42,616 in-scope files (58.1%).
- Found while testing, left for plan 1.4: spread arguments in calls (`f(...a)`) are not implemented by the
  interpreter (compiled mode only).

### Strict function semantics (2026-10-08, plan 1.2a)

All versions (simplest; documented in `BACKWARD_COMPATIBILITY.md` 2.6–2.8):

- `typeof this` in a strict function called without a receiver is `"undefined"` (the internal
  `Undefined.SCRIPTABLE_UNDEFINED` placeholder was reported as `"object"`).
- `call`/`apply` pass a primitive `this` unboxed to a strict script function (not to built-ins)
  (`ScriptRuntime.getApplyOrCallThisValue`), in both backends.
- `Function.prototype.caller` accessor: a TypeError for strict functions and built-ins, `undefined` for other functions;
  bound functions no longer have an own `caller`.
- `bind`: the bound name is `"bound " +` the target's `name` property read at bind time; `bind()` /
  `bind(undefined)` keep `this` undefined for a strict target.
- test262: 123 more files pass (`language/function-code`, `built-ins/Function`); 24,748 of 42,616 in-scope files
  (58.1%).
- Gaps: getters/methods called on a primitive and `bind` to a primitive still box `this`; `Function.prototype`'s
  `caller`/`arguments` accessors are not the realm's %ThrowTypeError%; bound `length` beyond `int`.

### Global lexical declarations (2026-10-07, plan 1.1e)

- **Gated, `VERSION_ECMASCRIPT`:** the top-level `let`/`const` of a script (not of `eval` code) are declared in the
  `TopLevel` scope, the declarative record of the global environment, instead of on the global object. A script
  starts with a `GLOBAL_LEXICALS` operation (new token/icode) that checks them against earlier declarations
  (`let`/`const`, `var`s and functions, non-configurable globals) and throws a SyntaxError on a collision, then
  declares them uninitialized (temporal dead zone); `initScript` rejects a `var`/function named like a global
  `let`/`const`. Global `const` assignment and the global TDZ follow (1.1c, 1.1d). `TopLevel` routes `put`/`putConst`
  to its own map only once a lexical declaration exists.
- Documented in `BACKWARD_COMPATIBILITY.md` (1.3).
- Destructuring with defaults in a `let`/`const` declaration (`let [x = 1] = a`) no longer reads the new binding
  before assigning it (the desugaring tested `x === undefined`, always true for a new binding): no behavior change
  under `VERSION_ES6`, and no spurious temporal-dead-zone error under `VERSION_ECMASCRIPT`. Names created by a
  desugaring are never checked for the temporal dead zone.
- test262: 25 more files pass (global `let`/`const` declarations and their TDZ, cross-script declarations, global
  destructuring declarations, destructuring assignments to consts); 24,625 of 42,616 in-scope files (57.8%).

### Temporal dead zone (2026-10-07, plan 1.1d)

- **Gated, `VERSION_ECMASCRIPT`:** using a `let`/`const` binding before its declaration has run throws
  `ReferenceError: Cannot access "x" before initialization.` (read, write, `typeof`, `++`/`--`, call), in both
  backends. The compiler classifies each reference (`NodeTransformer.classifyTdz`): after the declaration in the same
  function, nothing is checked; textually before it, the reference always throws; otherwise (closures, `with`,
  `switch` cases) the binding holds `UniqueTag.UNINITIALIZED` until its declaration runs, and the name lookups in
  `ScriptRuntime` throw on it (one comparison). Bindings in registers never hold it, so their number
  specialization is unaffected.
- **All versions from `VERSION_ES6` (bug fix, `BACKWARD_COMPATIBILITY.md` 2.5):** `let x;` in a loop body is
  `undefined` on each iteration in functions without closures (it kept the previous iteration's value).
- New tokens/icodes `TDZ_SENTINEL` and `CHECK_TDZ`; symbols now record their declaration (`Symbol.getNode()`), and a
  block's symbol table is kept after its scope object is created. Only bindings declared by a `let`/`const`
  statement start uninitialized (parameters, catch parameters and desugared bindings are initialized before their
  scope runs); destructuring declarations are not covered yet (plan 1.3).
- test262: 18 more files pass (`let`/`const` before-initialization tests, `for-in`/`for-of` head TDZ); 24,600 of
  42,616 in-scope files.

### Backward compatibility document (2026-10-07)

- `BACKWARD_COMPATIBILITY.md` lists every change that makes code or an embedding behave differently from stock
  Rhino 1.9.0: changes gated by `VERSION_ECMASCRIPT`, bug fixes that also apply to `VERSION_ES6`, and platform
  changes, each with an example, the spec section and how to keep the old behavior. `RELEASE-NOTES.md` points to it.

### Assignment to a const (2026-10-07, plan 1.1c)

- **Gated, `VERSION_ECMASCRIPT`:** assigning to a `const` binding throws `TypeError: Assignment to constant variable
  "x".`, also outside strict mode, for every form of assignment (`=`, compound, `++`/`--`, destructuring, `for-in/of`
  targets) and in both backends. Checked where the assignment already had no effect, so other assignments pay
  nothing: a const register (both backends), and a read-only slot of a scope object (which is always a const).
  Global consts (top-level `const` of a script) are covered with 1.1e.
- **`VERSION_ES6`:** assigning to a block-level `const` used to succeed (its block scope stored it as a plain
  variable); it now has no effect, like every other const (TypeError in strict mode, as before).
- Documented in the `VERSION_ECMASCRIPT` Javadoc and `BACKWARD_COMPATIBILITY.md`.
- test262: 2 more files pass (the other const-assignment tests use a global const, see 1.1e).

### Lexical declarations in loops (2026-10-07, plan 1.1a/1.1b)

Bug fixes, for every language version from `VERSION_ES6` on (decided 2026-10-07):

- **`const` in a loop body takes each iteration's value.** `for (...) { const c = i; }` kept the first value of `c`
  in functions without closures (`0,0,0` instead of `0,1,2`), a JavaScript 1.x const behavior. Code compiled for
  versions before `VERSION_ES6` keeps it.
- **`for (const x of/in ...)`** initializes `x` on each iteration; it stayed `undefined` in functions without
  closures (a `NaN` sum, for example).
- **Per-iteration bindings** for `let`/`const` loop heads: closures created in `for (let i ...)`, `for (let k in
  ...)` or `for (const v of ...)` see their own iteration's value (`0,1,2` instead of `3,3,3`). Implemented with a
  new `COPY_SCOPE` operation that copies the loop's scope object; loops whose bindings live in registers (no
  closures) are unaffected and pay nothing.
- Compiled functions now record the language version they were compiled with (`JSDescriptor`); it was always 0.
- test262: 16 more files pass (`for`, `for-in`, `for-of`, `let`, `block-scope`, and three tests that use such
  loops); in-scope pass rate 57.6% → 57.7%. Committed before the perf gate could run (see `MONFLABS_PLAN.md`).

### Performance gate: longer warmup, backup guard (2026-10-07)

- 20 warmup runs instead of 5 (with shorter runs, `PERF_SCALE` 0.2/0.5): with 5, compiled-mode scripts settled in
  one of two JIT states from one JVM to the next and the gate reported false regressions. A gate run now takes about
  18 minutes. The tolerances in `perf-tolerances.properties` were calibrated with the old settings and still need
  recalibrating with the new ones.
- The gate refuses to run while a Time Machine backup is running (`PERF_ALLOW_BACKUP=1` overrides).

### Gating plumbing for `VERSION_ECMASCRIPT` (2026-10-07)

- `Context.isLatestEcmaScript()` and `CompilerEnvirons.isLatestEcmaScript()`: the single check for behavior that is
  only enabled under `Context.VERSION_ECMASCRIPT` (runtime and compile time respectively), so that every gated
  behavior can be found with one search.
- The Javadoc of `VERSION_ES6`/`VERSION_ECMASCRIPT` now carries the list of behavior changes. It is empty: the audit
  found that nothing in the engine currently behaves differently between the two versions (the previous Javadoc
  mentioned `const` and strict-mode enforcement, but no code tested for it).
- Test helpers `Utils.assertWithAllModes_ECMASCRIPT` and `Utils.assertGatedBehavior(es6, ecma, script)`.
- No behavior change.

### test262 harness upgrades (2026-10-07)

- **A test listed as failing that now passes fails the run** by default, so conformance only moves forward
  (`-Dtest262.allowUnexpectedPass` for exploratory runs). Regenerating `test262.properties` never fails on it.
- **`async` tests run**: `doneprintHandle.js` is loaded, `print` output is captured per test, the microtask queue is
  drained after the test, and only a `$DONE` completion passes. 458 tests that were skipped now run: 308 pass, 150
  fail (mostly tests that use async functions without declaring the feature) and are listed in `test262.properties`.
- **Per-test time limit** of 60 seconds (`-Dtest262.timeout=<seconds>`), enforced through the instruction observer in
  both modes; scripts cannot catch it.
- On a JDK other than the reference one (25), the Unicode-version-dependent tests run but their outcome is not
  checked, so the Java 21 CI job does not fail on Unicode 15/16 data.
- `test262.*` system properties now reach the test JVM.
- In-scope baseline: 24,564 of 42,616 test files pass (57.6%, was 56.9%). No engine change.

### Performance gate (2026-10-07)

- `buildtools/perf-gate.sh [base-ref]` compares the working tree with a base revision (default: the `perf-baseline`
  tag) on 116 metrics, and fails on a regression beyond each metric's tolerance: startup, compile time of a large
  script, and steady-state runs of 20 micro-benchmarks (ported from Nashorn's perf gate) and 7 V8 benchmarks, each in
  interpreted and compiled mode and under `VERSION_ES6` and `VERSION_ECMASCRIPT`. Base and head run the same
  harness (`benchmarks/src/perf`) in interleaved rounds; only the engine jar differs.
- Tolerances (`benchmarks/testsrc/perf-tolerances.properties`) are calibrated by `buildtools/perf-calibrate.py`
  from 12 recordings of identical code; most are 10–20%. Validated in both directions: identical code passes, and a
  deliberate `instanceof` slowdown of +15–19% fails.
- Findings for the final performance pass are recorded in `MONFLABS_PLAN.md` (M14). No engine change.

### Android integration tests removed (2026-10-07)

- The `it-android` module (Rhino's integration tests on an Android emulator) and the Android CI workflow are
  removed, since Android is not a target of this fork. No engine change.

### Bazel build removed (2026-10-07)

- The optional Bazel build that upstream added in March 2026 (as a faster, cacheable alternative for running the
  tests) is removed: `MODULE.bazel`, `.bazelrc`, every `BUILD.bazel`, the `bazel/` rules, `maven_install.json`, the
  Bazel CI workflow, the top-level `rhino-test.config` copy that only Bazel read, and the Bazel-only test support
  (`testutils` `Sharding` and the Bazel runfiles resolver in `TestSource`). Gradle is the only build.

### Java 21 minimum, Java 25 reference for test262 (2026-10-07)

- **Breaking for embedders:** the engine now compiles with `--release 21` (was 17) and requires Java 21 or later.
  This fork targets Java server applications; Android and micro JVMs are no longer supported. The OSGi bundles
  now require `JavaSE` 21 (they declared 11), and `rhino-kotlin` builds with a Java 21 toolchain.
- `test262.properties` is now generated on Java 25 (`RHINO_TEST_JAVA_VERSION=25`, enforced by the build and by CI),
  because Unicode data comes from the running JDK; it was generated on Java 17 before. Regenerating it removed 54
  expected failures (Unicode 14–16 identifier tests and RegExp `Script`/`General_Category` property escapes): the
  in-scope baseline is now 24,256 of 42,616 (56.9%).

### Plan and conformance tooling (2026-10-07)

- `MONFLABS_PLAN.md`: the milestone plan to full ECMAScript 2026 (ECMA-262 17th edition), with the scope, ordering,
  performance and decision rules agreed on 2026-10-07.
- `tests/test262-status.py`: reports the test262 pass rate per feature tag and per directory from the test262 front
  matter and `test262.properties`, restricted to the in-scope tests. Baseline: 24,202 of 42,616 in-scope test files
  pass (56.8%).
- No engine change.

### Package and Maven coordinates renamed (2026-10-07)

- **Breaking for embedders:** every package moved from `org.mozilla.*` to `org.monflabs.mozilla.*`, the Java module
  names from `org.mozilla.rhino*` to `org.monflabs.mozilla.rhino*`, the OSGi bundle symbolic names likewise, and
  the Maven group id from `org.mozilla` to `org.monflabs.mozilla`. This lets the fork and upstream Rhino live on the
  same classpath or module path. Migration: replace `org.mozilla.` with `org.monflabs.mozilla.` in imports, in
  `Packages.org.mozilla...` script references, and in service and dependency declarations.
- `AGENTS.md` documents the conformance goal and the `VERSION_ECMASCRIPT` gating rule.
