# MONFLABS changelog

Changes made in the `org.monflabs.mozilla` fork of Rhino, newest first. Each entry says what changed, why, which
test262 slice moved, which behavior is gated by `Context.VERSION_ECMASCRIPT`, and the performance impact when
relevant. The plan these changes follow is in [MONFLABS_PLAN.md](MONFLABS_PLAN.md).

## Unreleased

### Assignment to a const (2026-10-07, plan 1.1c)

- **Gated, `VERSION_ECMASCRIPT`:** assigning to a `const` binding throws `TypeError: Assignment to constant variable
  "x".`, also outside strict mode, for every form of assignment (`=`, compound, `++`/`--`, destructuring, `for-in/of`
  targets) and in both backends. Checked where the assignment already had no effect, so other assignments pay
  nothing: a const register (both backends), and a read-only slot of a scope object (which is always a const).
  Global consts (top-level `const` of a script) are covered with 1.1e.
- **`VERSION_ES6`:** assigning to a block-level `const` used to succeed (its block scope stored it as a plain
  variable); it now has no effect, like every other const (TypeError in strict mode, as before).
- Documented in the `VERSION_ECMASCRIPT` Javadoc and `RELEASE-NOTES.md`.
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
