# MONFLABS plan: Rhino to full ECMAScript 2026

This is the working plan for bringing this fork of Rhino (`org.monflabs.mozilla`, branch `monflabs_enhancements`) to
full conformance with **ECMA-262 17th edition (ECMAScript 2026, June 2026)**, measured by test262, without breaking
existing code and without slowing the engine down.

- **Normative source:** `~/Documents/EcmaScript 262-402-404 Specifications/ECMA-262_17th_edition_june_2026.pdf`.
  Earlier editions (5th–16th) in the same folder can be used to understand when and why a behavior changed.
- **Reference implementation:** Nashorn (`../nashorn`), which went through the same journey and ended up with ES2026
  and a passing test262. See its `CLAUDE.md`, `CHANGELOG.md`, `doc/CONFORMANCE.md`,
  `doc/nashorn/internals/*.md` and `buildtools/perf-gate.sh`. Port the logic, not the structure (see `AGENTS.md`).
- **Status:** see the [tracker](#status-tracker) at the end; it is updated with every milestone.

## Decisions taken

| Date       | Decision                                                                                                                                                                                                                                                         |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2026-10-07 | Breaking changes are gated by `Context.VERSION_ECMASCRIPT`, which always means the latest edition (`AGENTS.md`).                                                                                                                                                  |
| 2026-10-07 | Packages renamed to `org.monflabs.mozilla.*`, Maven group `org.monflabs.mozilla`.                                                                                                                                                                                |
| 2026-10-07 | **Scope = Nashorn's exclusions.** Out: proper tail calls, `staging/`, `intl402/`, and everything that postdates ES2026 (see [Scope](#scope)). In: everything else, including ES modules, async, `SharedArrayBuffer`/`Atomics` and Annex B.                         |
| 2026-10-07 | **Ordering = foundations first, then edition by edition** (ES2015 gaps, then ES2016 → ES2026).                                                                                                                                                                   |
| 2026-10-07 | **Performance guardrail = a port of Nashorn's perf gate** (interleaved base-vs-head measurement, per-metric tolerances, both evaluation modes).                                                                                                                    |
| 2026-10-07 | **Full ES modules** with a pluggable module-loader API, coexisting with CommonJS `require()`.                                                                                                                                                                    |
| 2026-10-07 | **Target = Java server applications.** Android and micro JVMs are not targets. Compile for **Java 21** (21 APIs allowed now; the floor moves to Java 25 when available, expected early 2027). **Java 25 is the reference JDK for test262** (`test262.properties` generated with `RHINO_TEST_JAVA_VERSION=25`). |
| 2026-10-07 | **Gradle is the only build**: the optional upstream Bazel build is removed. |
| 2026-10-07 | The Android integration-test module (`it-android`) and its CI workflow are removed. |
| 2026-10-07 | A test listed as failing that passes **fails the test262 run** by default (opt out: `-Dtest262.allowUnexpectedPass`). |
| 2026-10-07 | 1.1: `const` re-evaluated in a loop body takes each iteration's value — a bug fix, **all versions**. |
| 2026-10-07 | 1.1: per-iteration bindings for `let`/`const` loop heads — **all versions**. |
| 2026-10-07 | 1.1: TDZ (ReferenceError) and TypeError on const assignment — **`VERSION_ECMASCRIPT` only**; under `VERSION_ES6`, assigning to a block-level const becomes a silent no-op like every other const. |
| 2026-10-07 | 1.1: top-level `let`/`const` in a separate declarative scope with cross-script redeclaration checks (like Nashorn's `Global.LexicalScope`) — **`VERSION_ECMASCRIPT` only**. |
| 2026-10-07 | 0.4 and the 1.1 loop fixes were committed **before the perf gate ran** (a stuck Time Machine backup blocked it); the gate is run on them together as soon as the machine is idle, and any regression is fixed in a follow-up. |
| 2026-10-07 | Every change that makes code or an embedding behave differently from stock Rhino (gated or not) is documented in **`BACKWARD_COMPATIBILITY.md`**. |
| 2026-10-08 | **Ultimate goal: remove the language versions and always behave as `VERSION_ECMASCRIPT`** (M15). Until then, each change is gated or applied to all versions, whichever is easier, without asking; every change incompatible with `VERSION_ES6` behavior is still logged in `BACKWARD_COMPATIBILITY.md`. |
| 2026-10-08 | 1.2: `typeof this` in a strict function called without a receiver is `"undefined"` — all versions. |
| 2026-10-13 | **Every behavior change is gated by `VERSION_ECMASCRIPT`**, bug fixes included (supersedes "gated or applied to all versions, whichever is easier"): the changes applied to every version so far (BACKWARD_COMPATIBILITY.md section 2) are gated in step G before M9, and `VERSION_ES6` keeps the behavior of stock Rhino. |
| 2026-10-13 | Step G scope: **features stock Rhino 1.9 did not have are `VERSION_ECMASCRIPT`-only** (classes, async functions and generators, `for await`, private names, static blocks, modules and their Java API, `import()`/`import.meta`, the RegExp `d` flag, and the globals and methods the fork added), as stock Rhino ties ES6 features to `VERSION_ES6`. **Public Java API changes are undone**: the old signatures come back (`@Deprecated`, old semantics). |
| 2026-10-10 | 1.8: typed arrays are little endian by default under `VERSION_ECMASCRIPT` (`FEATURE_LITTLE_ENDIAN`), as Nashorn and test262 assume; gated because embedders may read buffer bytes. |
| 2026-10-10 | **1.9 / M4.3: RegExp — extend Rhino's own engine** (`regexp/NativeRegExp`) for the ES2015+ features (`u`/`y`/`v`/`d` flags, code points, named groups, property escapes, lookbehind), rather than routing to `java.util.regex`: one engine, exact spec semantics. |
| 2026-10-11 | **1.12: module-loader API = a port of Nashorn's `api.modules`**: package `org.monflabs.mozilla.javascript.modules` with a functional `ModuleLoader.load(specifier, referrer)` returning a `Module` (`Module.source` for script modules, `Module.values` for Java-backed ones); loaders form a chain where the first answer wins; path, classpath-resource and Java-values loaders provided. The chain is set on the **`ContextFactory`**, and a `Context` can override it. |
| 2026-10-12 | **3.1: async functions reuse the generator machinery**: an async body is compiled like a generator in both backends, `await` is an internal suspension, and a Java driver resumes it from promise reactions on the `Context` microtask queue (no threads; spec tick order). Nashorn's `AsyncSupport` drives the same kind of suspension, but on virtual threads. |
| 2026-10-12 | 3.4: every agent can block in `Atomics.wait`, the main thread included (as in Nashorn, and as fits a server); the two `CanBlockIsFalse` tests are skipped as written for another host. `Atomics.waitAsync` is deferred to its edition (10.3). |
| 2026-10-12 | **4.3: Unicode property escapes use the JDK's data only** (as Nashorn): no bundled UCD; `Script_Extensions`, the binary properties the JDK does not carry and the Unicode-version gap of the generated tests are documented limits. |
| 2026-10-13 | **10.3: host jobs through a pluggable `HostJobScheduler`** on the `Context` (jobs posted from another thread, jobs after a delay): `Atomics.waitAsync` settles its promise through it, and throws a TypeError without one when it would have to wait (as Nashorn with its event loop off). A server plugs its own executor or loop; `EventLoop` is the default implementation (a thread-safe queue and deadlines, run by the Context's thread, no timer thread), used by the test262 runner. |
| 2026-10-14 | **11.1: under `VERSION_ECMASCRIPT` the global `Iterator` is the ES2025 constructor** (as in Nashorn): the `Iterator` function of JavaScript 1.7 cannot coexist with it (calling the ES2025 one throws), so it stays for the other versions only; Java collections iterate with `for-of` or `Iterator.from`. Built-ins can opt in to a primitive `this` from `call`/`apply` (`ClassDescriptor.Builder.withPrimitiveThis`), used by the iterator built-ins, whose `this-non-object` tests passed before by accident; making it the default for every built-in is left to M13. |
| 2026-10-14 | **11.6: JSON modules are built by the engine** (as in Nashorn): the `ModuleLoader` API is unchanged and loaders return the text as before; an import with `type: "json"` parses that text as JSON into a module whose `default` export is the value, registered apart from the same module imported as JavaScript; another `type` is a TypeError. Unlike Nashorn, this works with every loader of the chain. |
| 2026-10-14 | **12.2: `Math.sumPrecise` uses Shewchuk's algorithm** (the proposal polyfill's, with its overflow handling), not Nashorn's `BigDecimal`: measured equal on 300,000 random and edge cases and 18 times faster on usual values (2.7 times on the whole exponent range). |
| 2026-10-14 | **M13: functions have no `arity` under `VERSION_ECMASCRIPT`** (user decision): the JavaScript 1.2 property is not ECMAScript (as in Nashorn); `VERSION_ES6` keeps it. |
| 2026-10-14 | **M13: `Function.prototype.caller`/`arguments` are %ThrowTypeError% under `VERSION_ECMASCRIPT`** (user decision): sloppy functions get own `arguments` (the legacy arguments of a running call) and `caller` (undefined: not tracked) properties, as V8 has; strict functions, classes, arrows, methods and generators have none. |
| 2026-10-12 | **8.2: private elements in a per-object slot** (as Nashorn): a lazily allocated identity map on ScriptableObject, and a weak side table for the other Scriptables (a constructor's return override); invisible to reflection, Java interop and Proxy. |

Every further decision is added to this table when it is made (see [Decision protocol](#decision-protocol)).

## Target platform

The engine is embedded in **Java server applications**; Android and micro/embedded JVMs are not targets and do not
constrain design or API use. It compiles for **Java 21**, so Java 21 APIs can be used now (virtual threads, sequenced
collections, pattern matching for `switch`, records, `Math.clamp`, `StringBuilder.repeat`, `Character.isEmoji` and the
other emoji properties, …). When Java 25 becomes the floor (expected early 2027), these become available and are
worth revisiting:

- the foreign memory API (`MemorySegment`, final in 22) for `SharedArrayBuffer`/`Atomics` and resizable buffers
  (aligned atomic access with explicit layout; until then, `VarHandle` views over the existing `ByteBuffer`);
- `ScopedValue` (final in 25) as a cheaper current-`Context` lookup than `ThreadLocal` (measured with the perf gate);
- the Class-File API (final in 24), only if replacing Rhino's own `ClassFileWriter` ever pays for itself (Nashorn did
  it; not planned).

Runtime-level improvements of newer JVMs (compact object headers, GC, AOT cache for startup) benefit any server
running the engine on them, whatever the compile target. **Unicode data comes from the running JDK**, which is why
Java 25 (Unicode 16) is the reference for test262; tests needing a newer Unicode than the reference JDK are held out
and documented, as Nashorn does for Unicode 17.

## Ground rules

These restate and extend the constraints in `AGENTS.md`.

1. **`VERSION_ECMASCRIPT` is the target, and every change is gated.** The ultimate goal (decided 2026-10-08) is to
   remove the language versions and always behave as `VERSION_ECMASCRIPT` (milestone M15). Until then (decided
   2026-10-13), every behavior change is only active under `VERSION_ECMASCRIPT` (`cx.isLatestEcmaScript()` at
   runtime, `compilerEnv.isLatestEcmaScript()` for syntax and early errors, tested with
   `Utils.assertGatedBehavior`): the other versions keep their exact behavior, bug fixes included.
   Every change whose result differs from `VERSION_ES6` behavior is documented in
   `BACKWARD_COMPATIBILITY.md` (before/after example, spec section) and `CHANGELOG_MONFLABS.md`; gated ones are also
   named in the Javadoc of `Context.VERSION_ECMASCRIPT`.
2. **Both backends.** Every language feature works in the interpreter (`CodeGenerator`/`Interpreter`) and in compiled
   mode (`Codegen`). test262 runs both; a milestone is not done until both pass.
3. **test262 is the acceptance test.** Each feature is validated by its test262 slice (strict and sloppy, interpreted
   and compiled). `tests/testsrc/test262.properties` is regenerated as tests start passing and is committed with
   the feature. A new expected failure is a regression, never a way to make the build green.
4. **No performance regression.** The perf gate (milestone 0.2) runs before every commit that touches the engine and
   must pass. Obvious optimizations found on the way are welcome if they keep test262 green; each is measured and
   recorded.
5. **Java only.** Built-ins are implemented in Java, using the JDK (`BigDecimal`, `Base64`, `HexFormat`,
   `Character`/`java.text` Unicode data, `java.lang.ref`, …), never in bundled JavaScript.
6. **Unit tests** for anything test262 does not cover (Java API, embedding, `VERSION_ES6` vs `VERSION_ECMASCRIPT`
   gating). New test classes use JUnit 5.
7. **Changelog.** Every change to the engine gets an entry in `CHANGELOG_MONFLABS.md` (what, why, which test262
   slice moved, any gated behavior, perf impact).
8. **Present, then commit.** When a feature is done, it is presented to the user (what changed, test262 before/after,
   perf gate result, gated behavior, open questions), then committed on `monflabs_enhancements`, one commit per
   feature (or per coherent sub-feature).

### Definition of done for each feature

- [ ] Spec sections read in the ES2026 PDF; Nashorn implementation reviewed for approach and pitfalls.
- [ ] Implemented in parser/IR as needed, and in **both** backends.
- [ ] Behavior changes compared to `VERSION_ES6` identified; each one gated or applied to all versions, whichever is
      easier; gated ones listed in the `VERSION_ECMASCRIPT` Javadoc and tested with `Utils.assertGatedBehavior`.
- [ ] Every behavior change compared to `VERSION_ES6` (gated or not) documented in `BACKWARD_COMPATIBILITY.md`.
- [ ] Relevant test262 slice passes; feature tags removed from `UNSUPPORTED_FEATURES` and `~` folders un-skipped.
- [ ] `test262.properties` regenerated on the reference JDK (`RHINO_TEST_JAVA_VERSION=25 ./gradlew :tests:test
      --tests '*Test262SuiteTest' -DupdateTest262properties`), diff reviewed: only removals, or justified moves.
- [ ] `./gradlew check` green (all modules, Mozilla legacy suite included).
- [ ] Perf gate green, numbers recorded in the changelog entry when relevant.
- [ ] `CHANGELOG_MONFLABS.md` entry; tracker below updated; `python3 tests/test262-status.py` numbers updated.
- [ ] Presented to the user; committed.

### Decision protocol

Whenever more than one reasonable option exists (API shape, semantics under `VERSION_ES6`, whether something is
breaking, data source, scope), stop and ask the user, presenting the options with a recommendation. The recommended
option is the one aligned with Nashorn's implementation, unless Rhino's architecture or compatibility rules make it
inappropriate (in that case, say why). Record the answer in [Decisions taken](#decisions-taken).

## Scope

**In scope:** all of `test/built-ins`, `test/language`, `test/annexB` and `test/harness` in the pinned test262
checkout (`tests/test262`, commit `82d777218c`, 2026-05-15, which already carries every ES2026 subtree), except:

| Out of scope                                                                                     | Why                                                                                     |
|--------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `tail-call-optimization`                                                                         | Normative, but a calling convention the JVM cannot afford on every call (as Nashorn).   |
| `staging/`                                                                                       | Proposals, not part of an edition.                                                      |
| `intl402/`                                                                                       | ECMA-402 is a separate standard; open question, as in Nashorn.                          |
| `Temporal`, `explicit-resource-management` (`using`, `DisposableStack`, `SuppressedError`), `import-defer`, `Atomics.pause` | ES2027 (not in the ES2026 PDF).                                      |
| `ShadowRealm`, `decorators`, `joint-iteration` (`Iterator.zip`), `immutable-arraybuffer`, `await-dictionary`, `source-phase-imports*`, `import-bytes`, `import-text`, `nonextensible-applies-to-private`, `Intl.Era-monthcode` | Proposals not in ES2026.          |

`tests/test262-status.py` encodes this list. The **ES2026-specific additions** (confirmed in the PDF) are exactly the
seven Nashorn implemented: `Error.isError`, `Math.sumPrecise`, `Map`/`WeakMap` `getOrInsert`/`getOrInsertComputed`,
`Iterator.concat`, `Uint8Array` base64/hex, JSON source access (`JSON.rawJSON`, `JSON.isRawJSON`, reviver `context`),
and `Array.fromAsync`. The only Temporal content in ES2026 is the date-time string grammar used by `Date.parse`.

`UNSUPPORTED_FEATURES` in `Test262SuiteTest` will shrink to the out-of-scope tags only (plus `IsHTMLDDA`, which needs
a host hook Rhino does not have, and `u180e`, which is a pre-Unicode-6.3 behavior: both to be confirmed as decisions
when reached).

## Baseline (2026-10-07)

Measured with `python3 tests/test262-status.py` against `test262.properties` as regenerated on the reference JDK
(Java 25). On the Java 17 file it replaced, 54 fewer tests passed (Unicode 14–16 identifiers and RegExp property
escapes).

| In-scope test files | Pass   | Fail (all modes) | Fail (strict or sloppy only) | Skipped | Pass rate |
|---------------------|--------|------------------|------------------------------|---------|-----------|
| 42,616              | 24,256 | 3,332            | 805                          | 14,223  | **56.9%** |

Skipped tests are dominated by `class` (4,738), `async-iteration` (4,968), `async-functions` (705), class fields and
private members (~5,000), modules (`module` flag, `language/module-code`, `language/import`, `dynamic-import`), and
`async`-flagged tests the harness cannot run yet.

Per-area pass rates (selected; full list via `--by-feature` / `--by-dir 3`):

| Area                                  | Pass % | Area                                   | Pass % |
|---------------------------------------|--------|----------------------------------------|--------|
| `destructuring-binding`               | 28.1   | `built-ins/RegExp`                     | 66.8   |
| `default-parameters`                  | 22.7   | `regexp-unicode-property-escapes`      | 44.8   |
| `generators`                          | 20.9   | `regexp-v-flag`                        | 27.3   |
| `Symbol.iterator`                     | 19.2   | `regexp-match-indices`                 | 3.2    |
| `computed-property-names`             | 17.2   | `regexp-modifiers`                     | 69.6   |
| `let` / `const`                       | 26 / 0 | `resizable-arraybuffer`                | 45.0   |
| `language/statements/for-of`          | 64.8   | `iterator-helpers`                     | 9.4    |
| `language/arguments-object`           | 30.4   | `set-methods`                          | 75.3   |
| `language/eval-code`                  | 35.4   | `uint8array-base64`                    | 11.4   |
| `globalThis`                          | 2.1    | `json-parse-with-source`               | 0      |
| `Proxy` / `Reflect`                   | 68 / 68| `Math.sumPrecise`, `RegExp.escape`     | 0      |
| `built-ins/Promise`                   | 38.8   | `logical-assignment-operators`         | 38.0   |
| `built-ins/Function`                  | 76.6   | `optional-chaining`                    | 64.3   |
| `built-ins/JSON`                      | 75.2   | `TypedArray` / `DataView`              | 77 / 61|

Already complete or nearly so: `Float16Array`, `array-find-from-last`, `array-grouping`, `Array.prototype.at`,
`Object.fromEntries`, `regexp-lookbehind`, `numeric-separator-literal`, `json-superset`, `stable-array-sort`,
`optional-catch-binding`, `change-array-by-copy` (96%), `Error.isError` (92%), `hashbang` (93%).

---

## Milestones

Each numbered item is a feature: implemented, presented and committed on its own. Items within a milestone are in
order; dependencies are noted. The "test262" line names the feature tags or directories that must end up passing.

### M0 — Foundations (no engine semantics change)

**0.1 Plan, changelog, status tooling.** This file, `CHANGELOG_MONFLABS.md`, `tests/test262-status.py`, `AGENTS.md`
rules. *(done)* `BACKWARD_COMPATIBILITY.md` (added 2026-10-07) lists every behavior change compared to stock Rhino.

**0.2 Performance gate (port of Nashorn's `perf-gate.sh`).**
- Tag `perf-baseline` on the last commit before any engine change (the commit that adds this plan).
- `buildtools/perf-gate.sh [base-ref]`: builds the base revision in a temporary `git worktree` (engine jars only) and
  the working tree; the harness and benchmark scripts always come from the working tree, so both sides differ only
  in the engine.
- Harness (`benchmarks/src/perf/...`, plain Java main, not JMH, as in Nashorn's `PerfBenchmark`):
  1. steady state: each script compiled once, run repeatedly in one scope;
  2. minimum within a JVM, median across JVMs;
  3. base and head interleaved round by round;
  4. pinned heap (`-Xms1g -Xmx1g -XX:+UseG1GC`);
  5. **both** `EvaluationMethod.Interpreter` and compiled mode, and both `VERSION_ES6` and `VERSION_ECMASCRIPT`
     (to prove that the gated paths cost nothing to `VERSION_ES6` users);
  6. per-metric tolerances, measured rather than chosen (10 runs of identical code both ways, then set the band);
     a missing metric fails; `-Dperf.tolerance=` override for a considered re-run.
- Scripts: port Nashorn's 20 `core/src/test/scripts/perf/*.js` (arith, arraymap, closures, concat, evalcache, forof,
  instanceof, matchreplace, megamorphic, properties, protochain, regexp, regexptest, sort, split, strbuild,
  stringmethods, toprimitive, typedarray, wideobject), plus compile-time metrics on a large script, plus a
  steady-state run of selected SunSpider/V8 scripts already in `benchmarks/testsrc`. Add a script whenever a
  feature touches a hot path that no script exercises (e.g. property access with symbols, iteration, calls through
  `super`, generators, async).
- Documentation: `doc`/`benchmarks/README.md` section; `AGENTS.md` rule: run the gate before committing engine
  changes. The existing JMH suite stays for deep dives and profiling (`PROFILERS=cpu`).

**0.3 test262 harness upgrades** (in `Test262SuiteTest`; each fixes test infrastructure, not the engine):
- run `async`-flagged tests: `$DONE`/`doneprintHandle.js` and `asyncHelpers.js`, draining Rhino's microtask queue
  until settled, with a timeout (Nashorn: 120 s per test on its own thread);
- verify `negative: {phase, type}` (parse vs runtime, and the error constructor), if not already done;
- a per-test timeout so an adversarial iterator cannot hang the run;
- **fail on unexpected pass** (today it is only logged), so conformance only moves forward — `Test262UnexpectedlyPassingTest`
  to be checked first; decision if it changes the existing workflow;
- `$262` host hooks audit: `createRealm`, `detachArrayBuffer`, `evalScript`, `gc`, `global`, `IsHTMLDDA`, `agent`
  (agent arrives with M3.4);
- `module` flag support arrives with M1.12 (modules).
- Re-baseline `test262.properties` on Java 25 after each harness change (tests that were silently skipped now run
  and are listed as failing; that is expected and not a regression).

*Done 2026-10-07.* State after the audit: negative tests were already verified (phase and error name; any
compile-time `EvaluatorException` is accepted as a `SyntaxError`, a leniency to revisit if a test shows it hides a
wrong error). `$262` has `createRealm`, `detachArrayBuffer`, `evalScript`, `gc`, `global`; `agent` throws (M3.4) and
`IsHTMLDDA` is absent (needs a host hook; decision when reached). Unexpected passes fail the run by default
(decided 2026-10-07, opt out with `-Dtest262.allowUnexpectedPass`). On a JDK other than the reference one, the
Unicode-dependent tests are run but not checked.

**0.4 Gating plumbing.** A single, cheap way to test for the latest edition (e.g. a cached boolean on `Context` /
`CompilerEnvirons` set from `getLanguageVersion() >= VERSION_ECMASCRIPT`) so that gated checks cost a field read on
hot paths. Javadoc of `VERSION_ECMASCRIPT` gets a "Behavior changes" list maintained from then on. Unit test helper
that runs a script under both versions.

*Done 2026-10-07.* The existing idiom (`cx.getLanguageVersion() >= …`, 134 uses) is already a field read and an int
compare, so no cached flag is needed; the value of the helpers is a single, grep-able name for every gated behavior.
Rule: runtime behavior follows the version of the context running the code (existing Rhino semantics); syntax and
early errors follow the version the code was compiled with. Audit result: as of this item, **nothing in the engine
behaves differently under `VERSION_ECMASCRIPT` than under `VERSION_ES6`** (the old Javadoc mentioned `const` and
strict-mode enforcement, but no code tested for it); the "Behavior changes" list starts empty.

### M1 — ES2015 completion

Most later work depends on these. Order chosen by dependency: scoping → functions → destructuring/iteration →
object literals → generators → classes → built-in protocols → RegExp → modules.

**1.1 Lexical declarations.** `let`/`const` per spec: TDZ, per-iteration bindings in `for`/`for-in`/`for-of` heads
(closures capture each iteration), `const` assignment `TypeError`, redeclaration early errors, global lexical
environment (`language/global-code`, `language/block-scope`, `language/statements/{let,const,for}`),
`let` as an identifier in sloppy code. *Gating:* TDZ errors and `const` enforcement are breaking → `VERSION_ECMASCRIPT`
(audit what `VERSION_ES6` already enforces first). Nashorn: `ES6Desugar`, `Lower`, block scoping in `codegen`.

Split into separately committed steps:
- *1.1a (done)* `const` in a loop body: a JavaScript 1.x const kept its first value (`0,0,0`); code compiled for
  ES6+ now initializes the binding on every evaluation. Also `for (const x of/in …)` initializes `x` (it stayed
  `undefined` in functions without closures). Functions now record their compile-time language version (it was
  always 0).
- *1.1b (done)* per-iteration bindings: `COPY_SCOPE` (new token/icode) replaces a loop's let scope with a copy after
  the init and before each increment (`for`), or at the start of each iteration (`for-in/of`); only emitted when the
  loop has bindings, and dropped when they live in registers (no closure can observe them).
- *1.1c (done)* const assignment: `ScriptRuntime.constAssignment` (TypeError under `VERSION_ECMASCRIPT`) called
  where an assignment already had no effect: const registers, and read-only slots of scope objects (always consts);
  block consts in a `LocalScope` are now really read-only. Global consts wait for 1.1e.
- *1.1d (done)* TDZ: compile-time classification of each reference (none / always throws / runtime check);
  `UniqueTag.UNINITIALIZED` held by bindings in scope objects until their declaration runs, checked by the name
  lookups; `let x;` initializes. Only bindings declared by a let/const statement start uninitialized (parameters,
  catch parameters and desugared bindings are initialized before their scope runs). Known gaps: destructuring
  declarations (to do with 1.3), switch cases in functions without closures, and a closure write is checked before
  its right-hand side.
- *1.1e (done)* global lexical scope: a `GLOBAL_LEXICALS` operation at the start of a script declares its
  top-level let/const in `TopLevel`'s own map (GlobalDeclarationInstantiation checks); they are removed from the
  script's variables. Gap: a script whose `var`s and `let`s conflict may create its `var`s before the SyntaxError.

**1.2 Function semantics.** Mapped/unmapped `arguments` (`language/arguments-object`), strict-mode completeness,
function `name` inference and `length` with default/rest parameters, default parameter scope (separate environment
when parameters have expressions), rest parameters, duplicate-parameter early errors, `Function` constructor
(`built-ins/Function`, `ThrowTypeError`), `Function.prototype.toString` source text (ES2019 revision, done here
because tests depend on it) — *gating* of the `toString` format is a decision. Direct/indirect `eval` semantics
(`language/eval-code`, `language/function-code`: var hoisting, conflicts with lexical declarations).

Split into separately committed steps (2026-10-08, from a test262 run of the function directories: 621 failing
files, of which destructured parameters go to 1.3, and async/class syntax to their items):
- *1.2a (done)* strict function semantics: `this` without a receiver and through `call`/`apply`, `caller`, `bind`.
- *1.2b (done)* the `arguments` object: rewritten as the spec's arguments exotic object (own data properties plus a
  parameter map); rest parameters give an unmapped `arguments`. Spread arguments in calls are missing from the
  interpreter: added to 1.4.
- *1.2c (done)* function early errors: duplicate parameters, `eval`/`arguments` as names, `use strict` with a
  non-simple list (checked once the body is parsed), strict `eval =`; the `Function` constructor; the `arguments`
  accessor; rest parameters with a binding pattern; `arguments` in default values. `yield` as an identifier in
  sloppy code moves to 1.6 (generators); the parameter scope moves to 1.2f/1.2g.
- *1.2d (done)* `Function.prototype.toString`: method/accessor source text, the `Function` constructor's source,
  built-ins, proxies. Class members wait for 1.7.
- *1.2e (done)* `eval` code: `var` conflicts with parameters/`arguments` (eval in a parameter list) and with
  lexical declarations, global function/var definability (CanDeclareGlobalFunction), indirect eval never strict. The
  eval's own lexical environment and `new.target` in eval move to 1.2g.
- *1.2f (done)* temporal dead zone of parameters: a default value reading its own or a later parameter
  (`dflt-params-ref-later`, `-ref-self`), gated with the TDZ.
- *1.2g (done 2026-10-10, partly)* environments: a separate var environment for the body when parameters have expressions
  (`scope-paramsbody-var-open`, `scope-param-elem-var-*`), `eval` in parameters (also arrows), the immutable binding
  of a function expression's name (`named-*-reassign-fn-name-in-body*`, `scope-name-var-open-*`), the lexical
  environment of eval code (`lex-env-distinct-*`, `lex-env-no-init-*`), a function's top-level `let` against an eval
  `var` (`scope-body-lex-distinct`), `new.target` in eval. They need a scope between the function's activation and
  its body (or eval code) in both backends, with hoisted functions closing over it; after 1.3 because destructured
  parameters go through the same desugaring. Done: immutable function-expression name, eval lexical environment,
  function top-level `let` vs eval `var`, eval in arrow parameters, `new.target` in eval, unmapped `arguments` with
  destructured parameters. Deferred (no milestone yet; ~25 tests): the separate var environment for the body
  (`scope-paramsbody-var-open`, also 12 class tests and the `arrow-fn-body-cntns-arguments-*` eval tests), which
  needs a second activation created after the parameters in both backends with hoisted functions created after it;
  the TDZ scope of a for-in/of head (`for-in/scope-body-lex-*`).

**1.3 Destructuring everywhere.** Binding and assignment patterns in declarations, parameters, `catch`, `for` heads,
nested defaults, holes (ignored in parameter patterns: `function f([x, , y])` binds `y` to the second element), rest elements; `IteratorClose` on abrupt completion; evaluation order
(`destructuring-binding`, `destructuring-assignment`, `language/expressions/assignment`,
`language/statements/for-of`).

Split into steps (2026-10-08, decision: IR desugaring with new iterator operations, Nashorn-aligned):
- *1.3a (done)* array patterns follow the iterator protocol (`ITER_OPEN`/`ITER_STEP`/`ITER_REST`/`ITER_CLOSE`,
  `IteratorRecord`, array fast path), in every context.
- *1.3b (done)* IteratorClose on abrupt completion (a throwing default or target), with a statement-level guard for
  expression-position patterns.
- *1.3c (done)* object patterns: RequireObjectCoercible, function names of anonymous defaults (evaluation order: 1.3d).
- *1.3d (done)* evaluation order of assignment targets and computed keys; for-in/of assignment heads with
  defaults; names of nested patterns in `for (let …)` heads. Left: generator parameters bound at call time (1.6),
  `yield` as an identifier (1.6), class patterns (1.7).

**1.4 Iteration protocol.** Spread arguments in calls (`f(...a)`) in the interpreter (`CodeGenerator`/`Interpreter`; compiled mode has them). `GetIterator`/`IteratorStep`/`IteratorClose` as shared runtime helpers used by `for-of`,
spread, destructuring, `Array.from`, `yield*`, and the iterable constructors (`Map`, `Set`, `WeakMap`, `WeakSet`,
`Promise.all*`, `%TypedArray%.from`). `%IteratorPrototype%`, `%ArrayIteratorPrototype%` etc. (`Symbol.iterator`,
`for-of`). Perf: fast path for built-in array iteration in `for-of` (Nashorn did the same).

Steps:
- *1.4a (done)* spread arguments in calls and `new` (both backends); `for-of` on `IteratorRecord` with IteratorClose
  on `break`/`continue`/`return`/throw. Next: iterable built-ins (`Array.from`, `Map`/`Set`/`WeakMap`/`WeakSet`
  constructors, `Promise.all*`, `%TypedArray%.from`), `yield*`, direct `eval(...args)`.

**1.5 Object literal extensions.** Computed property names (17%), shorthand and concise methods, accessor names,
`__proto__: ` in literals (Annex B), `super` property access in object methods (`[[HomeObject]]`),
`language/expressions/object`, `language/expressions/super`.

Steps:
- *1.5a (done)* early errors: accessor parameters, rest initializer, escaped `get`/`set`, duplicate `__proto__`,
  strict directive applied to the next token, `in` in computed keys of `for` heads. Remaining in
  `language/expressions/object`: class/generator/`yield`-identifier tests (1.6, 1.7), parameter scopes (1.2g),
  `__proto__` own properties (1.11).
- *1.5b (done)* only functions written as a property value are named after the property (`isNamedByLiteral`).

**1.6 Generators completion.** Generator declarations/expressions/methods, `yield*` delegation with
`return`/`throw` forwarding, `GeneratorPrototype` details, generator objects as iterables; both backends
(`generators`, `built-ins/Generator*`). `yield` as an identifier outside generators in sloppy code
(`var yield`, from 1.2c). Prerequisite for async functions (M3.1) if they are built on the generator
machinery (decision at M3.1).

Steps:
- *1.6a (done)* `yield` parsing (AssignmentExpression operand, contextual identifier, `yield*` on one line),
  generator `prototype`, interpreter fallback when the class compiler cannot compile a `yield` in an expression.
- *1.6c (done)* `yield*` across a newline and with one argument, `function yield` in a generator,
  `%GeneratorPrototype%.constructor`, a generator completed by a throw.
- *1.6b (done)* generator parameters bound when the generator is called (FunctionDeclarationInstantiation before the
  generator object is created; ~110 destructuring tests).

**1.7 Classes (ES2015).** Declarations and expressions, constructors, `extends` (including `null` and expressions),
`super()` and `super.x`, derived-constructor `this` TDZ, `new.target`, static methods, accessors, computed members,
class name binding, strict-mode bodies, `[[ConstructorKind]]`, subclassing built-ins via `NewTarget` plumbing
through every built-in constructor (`Array`, `Error`s, `RegExp`, `Map`, `Set`, `Promise`, typed arrays, `Function`,
`Boolean`, `Number`, `String`, `Date`, `ArrayBuffer`, `DataView`). Removes `class` from `UNSUPPORTED_FEATURES` and
un-skips `language/{statements,expressions}/class` (~8,400 tests; tests using later class features stay skipped by
their own tags until M8). Biggest single item; may be split into 1.7a (syntax + runtime) and 1.7b (built-in
subclassing). Nashorn: classes desugared in its IR with `NewTarget` threaded through built-in constructors.

Steps:
- *1.7a (done)* class syntax and ClassDefinitionEvaluation: `ClassNode` desugared by `IRFactory` into a `CLASS`
  node (constructor, heritage, prototype and static members as object literals) evaluated by
  `ClassDefinition.define`; class constructors throw when called; default constructors; inner name binding;
  `class` removed from `UNSUPPORTED_FEATURES`.
- *1.7b (done)* `super()`, derived-constructor `this` bound by `super()`, `super.x` in methods and static
  methods (home object of statics), `new.target`.
- *1.7c (done)* subclassing built-ins via `NewTarget`: most built-ins already took the prototype from
  `NewTarget`; fixed `WeakRef`, `FinalizationRegistry`, `Object(value)`, `ArrayBuffer[Symbol.species]`, the
  order of `DataView`, and the Annex B `RegExp` legacy accessors on a subclass.
- Left for other items: separate parameter and body scopes (1.2g), async methods (M3), private names, fields and
  static blocks (M8), Iterator helpers, the `arity` own property of functions (property order tests).

**1.8 Well-known symbols and built-in protocol conformance.** `Symbol.species`, `toStringTag`, `unscopables`
(`with`), `hasInstance`, `toPrimitive`, `isConcatSpreadable`; `Proxy` invariants and trap ordering, `Reflect`
(68% → 100%); `TypedArray`/`ArrayBuffer`/`DataView` (non-resizable) conformance; `String.fromCodePoint`,
`String.raw`, `String.prototype` methods; `Promise` (non-async parts); `Object` residuals; `cross-realm` behaviors
(needs `$262.createRealm`).

Steps (2026-10-10, from a run of the in-scope built-ins failures: 435 files):
- *1.8a (done)* typed array internal methods (10.4.5).
- *1.8b* `[[Set]]` returning a boolean (`Reflect.set`, receivers, Proxy `set`), Proxy invariants and trap order.
  Part 1 (done): [[Set]], proxy `set`/`getOwnPropertyDescriptor`, proxies in prototype chains. Part 2 (done):
  `deleteProperty`/`defineProperty`/`setPrototypeOf` results, proxies as targets, `get` receiver. Left: `with` and
  proxies (name resolution through the `has` trap), `ownKeys` order of a proxy target.
- *1.8c (done)* `Object.*`, `Array.prototype`, `String.prototype`, `Function.prototype`, `JSON`, `Date`, `Promise`
  residuals, by directory, in 9 parts (Set methods, typed arrays, BigInt, Error included).
- Left for other items: the primitive `this` of methods called on primitives (`"a".f()` and
  `Function.prototype.call` box it, so strict functions and built-ins see an object), cross-realm tests (need
  `$262.createRealm`), `with` and proxies, `Function.prototype.arguments`/`caller` as %ThrowTypeError% accessors
  (sloppy functions would need own `arguments`/`caller`), index setters of `Array.prototype` bypassed by the dense
  fast paths of `push`/`unshift`, JSON source access (12.6), `Array.fromAsync` (12.7), `SharedArrayBuffer` (3.x),
  Iterator helpers, RegExp (1.9).
- Perf gate (2026-10-10, end of 1.8): no regression; `evalcache` is +4% to +9% over `perf-baseline` (within
  tolerance on 7 rounds, flagged once at +12% on 3 rounds) — to watch.

**1.9 RegExp (ES2015).** `u` flag (code point semantics, `\u{...}`, case folding), `y` sticky, `flags`, the four
symbol methods (`@@match`, `@@replace`, `@@search`, `@@split`) with observable spec steps, `RegExp` subclassing,
`lastIndex` handling (`built-ins/RegExp` core). Rhino keeps its own regexp engine (`regexp/NativeRegExp`) — whether
to extend it or to route some features to `java.util.regex`, as Nashorn did, is a decision at M4.3 (decided
2026-10-10: extend the own engine).

Steps:
- *1.9a (done)* `RegExp.prototype` as an ordinary object, flag getters, `flags`, the constructor (IsRegExp,
  regexp-like objects), `source` escaping, generic `toString`, the symbol methods' RegExpExec/`lastIndex` steps,
  quantifier bounds, invalid braced quantifiers, format-control characters in literals. Left: the `this` of
  `RegExp.prototype` methods called on primitives (the primitive-`this` item of 1.8).

**1.10 Template literals and misc syntax residuals.** Tagged template caching (`[[TemplateMap]]`), raw strings,
`new.target` outside classes, `language/expressions/arrow-function` residuals (82%), `language/literals`,
`language/identifiers` (Unicode ID_Start/ID_Continue from the JDK), reserved words.

Steps:
- *1.10a (done)* completion values (gated), logical assignment short-circuit, compound assignment key conversion,
  setter-less accessors, strict PutValue on deleted globals, `with` and `Symbol.unscopables`.
- *1.10b (done)* arrow CoverInitializedName, comment ASI, `eval(...)`, `instanceof`, rest/spread of proxies, TDZ of
  destructuring assignment, `for-in` patterns (gated).
- *1.10c (done)* `let` as an identifier in sloppy code (gated), strict `eval`/`arguments` destructuring targets.
- *1.10d (done)* GlobalDeclarationInstantiation of scripts (gated), supplementary characters in identifiers.
- *1.10e (done)* early errors (gated): assignment targets, strict mode, escaped keywords, numeric literals,
  `const` initializers, declarations in statement positions, catch parameters, `continue` labels; `eval?.()`.
- *1.10f (done)* parameter redeclarations, `yield` in parameters, `new.target` in arrows (gated); generator
  method prototypes, `>>>` operand order; compiled-mode `finally` exception ranges. Left: `__proto__` in object
  literals (computed and shorthand forms define an own property; needs a runtime flag in both backends).
- *1.10g (done)* nested CoverInitializedName in patterns (the misuse gated), `for-in` shadowing, exact Use Strict
  Directives. Left for later (scope residuals, with the for-in/of head TDZ and paramsbody scopes): the switch
  discriminant and catch parameter evaluated outside the block's lexical scope; completion value of a `break`
  in `finally`.

**1.11 Annex B (ES2015 parts).** Block-level function declarations in sloppy mode (B.3.3), `__proto__`,
`__defineGetter__` and kin, HTML-like comments, legacy octal, `RegExp` legacy syntax (`annexB/language`,
`annexB/built-ins`). *Gating:* B.3.3 changes how existing Rhino scripts resolve functions declared in blocks →
`VERSION_ECMASCRIPT` (decision).

Steps:
- *1.11a (done)* `trimLeft`/`trimRight` aliases, HTML method lengths, `setYear`, the legacy RegExp accessors
  (non-enumerable, configurable, receiver checked against their own realm), `compile` (subclass instances,
  flags with a RegExp), RegExp pattern grammar (`\xa`, `[\c0]`, three-digit octal escapes; gated), HTML-like
  comments after block comments, B.3.3 hoisting skipped for destructured catch parameters, `f() = v` evaluates
  the call, `for (var a = 0 in o)` in strict code (gated). Left: IsHTMLDDA (`emulates-undefined`), async
  functions in eval.
- *1.11b (done)* `__proto__` in object literals (only `__proto__: v` sets the prototype; gated), global `var`
  bindings shadowing Object.prototype (gated; shared scopes keep their bindings), `compile` on a cross-realm
  instance. Annex B is complete except IsHTMLDDA (not supported) and async functions (M3).

**1.12 ES modules.** Module parsing (`import`/`export` in all forms, `default`, namespace objects, live bindings,
cyclic graphs, `language/module-code`, `language/import`, `language/export`), a pluggable **module-loader API**
(`ModuleLoader` resolving a specifier relative to a referrer and returning source, registered on the
`ContextFactory`/`Context`; filesystem and classpath loaders provided; coexists with `commonjs.module.Require`), module
records, linking and evaluation in both backends, `module` flag support in the test262 harness. API shape is a
decision (recommended: modeled on Nashorn's `api.modules`). Later module features land in their editions: `export * as
ns` and `import()`/`import.meta` (M6), top-level await (M8), import attributes and JSON modules (M11).

Design (2026-10-11, no backend change): a module is parsed with the module goal (strict, top-level `import` and
`export`), which records its imports and exports and rewrites its source, keeping the lines, into a strict
generator function. The first `next()` instantiates it: declarations are hoisted and getter closures for the
exported bindings are registered (InitializeEnvironment). The second `next()` evaluates the body. The
generator's scope is a `ModuleScope` whose parent is the global: it resolves imported names to the exporting
module's getters (live bindings, TDZ, immutable). `ModuleRecord` links (ResolveExport, ambiguity, cycles),
builds the namespace object and evaluates the graph depth first.

Steps:
- *1.12a (done)* API (`modules` package, `ContextFactory`/`Context` registration, `Context.evaluateModule`),
  module goal parsing, rewriting, records, linking, namespace objects, evaluation; test262 runs the `module`
  tests. Left: `super.x = v` with a namespace receiver (OrdinarySet does not use [[GetOwnProperty]]), early
  errors of module code not tied to import/export (HTML-like comments, duplicate top-level functions, escaped
  keywords in exports), `arguments` at the top level of a module.
- *1.12b (done)* module early errors: HTML-like comments, lexical top-level functions, escaped `default`. Left:
  `super.x = v` with a namespace receiver, `arguments` at the top level of a module; the rest of the module tests
  need async functions (M3), class fields (M7) or later module features.
- *1.12c (done)* `super.x = v` with a namespace receiver follows OrdinarySetWithOwnDescriptor (the binding is
  read: ReferenceError before initialization, TypeError after). 1.12 is done. Residual, not covered by test262:
  `arguments` at the top level of a module is the generator's `arguments` object rather than an unresolvable
  reference. `await` as an identifier in scripts belongs to M3.

### M2 — ES2016

**2.1** `**` and `**=` (72%: precedence and unary-operand early errors, BigInt). **2.2** `Array.prototype.includes`
(64%: `SameValueZero`, typed arrays, length coercion).

Status (2026-10-12, done): both features pass every in-scope test after M1; the remaining tagged tests need class
fields (M8), Atomics (3.3) or `import()` (M6).

### M3 — ES2017

**3.1 Async functions.** `async function`, async arrows, async methods, `await`; job queue integration with Rhino's
existing microtask processing (`Context` promise jobs); both backends. Implementation strategy is a decision
(recommended, as Nashorn's `AsyncSupport`: a generator-like suspension driven by promise reactions, reusing M1.6).
Un-skips `async-functions` and the `async` flag tests that depend only on promises.
**3.2** `Object.values`/`entries`/`getOwnPropertyDescriptors`, `String.prototype.padStart`/`padEnd`, trailing commas in
parameter and argument lists (residuals only).
**3.3** `SharedArrayBuffer` (non-growable) and `Atomics` (all operations except `wait`).
**3.4** `Atomics.wait`/`notify` and the `$262.agent` multi-agent harness (worker threads, a host that can block).
Memory-model tests that need true parallel agents are verified here.

Steps:
- *3.1a (done)* async functions, arrows and methods, `await`, `%AsyncFunction%`; `AsyncFunctionDriver` over the
  generator machinery of both backends; `await` an identifier in scripts; `async-functions` tests enabled.
- *3.1b (done)* async early errors (labelled async functions, await in nested arrow parameters); element
  accesses in optional chains short-circuit. 3.1 is done except what needs async generators (M4) and private
  methods (M8).
- *3.2 (done)* nothing to do: every in-scope test passes.
- *3.3 (done)* `SharedArrayBuffer` and `Atomics` except `wait`/`waitAsync`; atomic operations lock the storage of
  their buffer (shared by every agent over the same bytes, for 3.4); strict generators resumed by sloppy code.
- *3.4 (done)* `Atomics.wait`/`notify` (waiter lists on the storage, under its lock; every agent can block, the main
  thread included) and the `$262.agent` harness (agents are threads with a context and a realm each, sharing
  storage through `NativeSharedArrayBuffer.share`). `Atomics.waitAsync` (ES2024) is left to 10.3, with the
  question of how a host delivers its timeouts. M3 is done.

### M4 — ES2018

**4.1 Async iteration.** Async generators, `for await`, `Symbol.asyncIterator`, `%AsyncFromSyncIteratorPrototype%`,
`%AsyncIteratorPrototype%` (`async-iteration`: 4,968 tests, the largest skipped group after classes).
**4.2 Object rest/spread** in literals and patterns (34%/45%: `CopyDataProperties`, excluded keys, getters order).
**4.3 RegExp ES2018.** `s` (dotAll, 35%), named groups residuals (91%), lookbehind (done), **Unicode property escapes**
(45%): data source is a decision — the JDK's `Character`/`Character.UnicodeScript` cover General_Category and Script
but not all binary properties nor `Script_Extensions` (Nashorn held some out as engine limits); options: generate
compact tables from the UCD at build time vs JDK-only with documented hold-outs.
**4.4** `Promise.prototype.finally` (52%), template literal revision (invalid escapes in tagged templates).

Steps:
- *4.1 (done)* async generators, `yield*` in them, `for await`, the async iteration intrinsics; generators can yield
  in a `finally` run by `return()`, and compiled generators keep the handlers of nested `try` statements.
- *4.2 (done)* nothing to do: what fails needs private names (M8).
- *4.3 (done)* RegExp flag getters answer for their own realm only. Unicode property escapes keep the JDK's data
  (decision 4.3): `Script_Extensions` (175 generated tests) and the ~40 binary properties the JDK does not carry
  stay unsupported, and the generated General_Category/Script tests follow a newer Unicode than JDK 25's.
- *4.4 (done)* nothing to do. M4 is done.

### M5 — ES2019

Mostly present. **5.1** Residuals: `flat`/`flatMap`, `trimStart`/`trimEnd` (and Annex B aliases as the same
function objects), `Symbol.prototype.description`, well-formed `JSON.stringify`, stable sort, `Function.prototype.toString`
(if not finished in 1.2).

Status (2026-10-12, done): every ES2019 feature passes its in-scope tests. Residual, cross-cutting: a primitive
`this` reaches callbacks and accessors as a wrapper object (`Callable.call` takes a `Scriptable` this), e.g. a strict
`thisArg` callback of `flatMap`/`map`, the getter of `BigInt.prototype.toJSON` in `JSON.stringify`, `Symbol.asyncIterator`
called on a primitive, the flag getters of RegExp on a primitive. To be fixed with the engine-wide call path, before M15.

### M6 — ES2020

**6.1** `globalThis` (2%: property attributes, strict/sloppy access). **6.2** Optional chaining (64%) and nullish
coalescing (88%) residuals: short-circuit chains, `?.` with calls/templates/`super`, early errors.
**6.3** `BigInt` residuals (74%: typed arrays `BigInt64Array`/`BigUint64Array`, `DataView` accessors, `asIntN`/`asUintN`,
comparisons, mixed-type errors). **6.4** `String.prototype.matchAll`, `Promise.allSettled` (with async tests).
**6.5** `for-in` enumeration order (78%; *gating* decision — may change existing iteration order).
**6.6 Module additions:** `export * as ns from`, dynamic `import()`, `import.meta` (host-populated through the
module-loader API).

Status (2026-10-12, done): 6.1-6.5 pass but for later features and the primitive `this` residual (see M5).
6.6: `import()` is an IR node (Token#IMPORT_CALL) whose promise job loads, links and evaluates; `import.meta` is
rewritten in module code to an object the module record hands to the body, with a `url` (origin, or name) as in
Nashorn, rather than populated by the host.

### M7 — ES2021

**7.1** Logical assignment `&&=`, `||=`, `??=` (38%: short-circuit without assignment, named evaluation).
**7.2** `String.prototype.replaceAll`, `Promise.any` + `AggregateError` residuals. **7.3** `WeakRef`/`FinalizationRegistry`
residuals (cleanup callbacks scheduled on the job queue), numeric separators residuals.

Status (2026-10-12, done): every ES2021 feature passes but for tests needing class fields (M8); the native errors,
`WeakRef`, `FinalizationRegistry` and ordinary objects take their fallback prototype from newTarget's realm.

### M8 — ES2022

**8.1 Class fields.** Public instance and static fields, computed keys, `NamedEvaluation`, initializer scope
(`class-fields-public`, `class-static-fields-public`). **8.2 Private names.** `#x` fields, methods, accessors,
static forms, brand checks, `#x in obj` (`class-fields-private`, `class-methods-private`, `class-static-*-private`,
`class-fields-private-in`; ~4,700 tests); storage invisible to reflection, Java interop, and `Proxy` (decision on
representation; Nashorn uses a per-object slot). **8.3** Class static blocks (23%). **8.4** RegExp `d` flag
(`hasIndices`, `.indices`; 3%). **8.5** Top-level `await` in modules (async module evaluation). **8.6** Residuals:
`.at()`, `Object.hasOwn`, error `cause`.

Steps:
- *8.1 (done)* public instance and static fields (entries of the member literals keyed by a ClassFieldKey, recorded
  on the constructor; Token#FIELD_KEY, Token#CLASS_STATIC). Limits: static and instance computed keys are not
  interleaved in source order; `eval('arguments')` in an initializer (fixed in 8.2).
- *8.2 (done)* private names (a const binding `#x` of the class scope holding a PrivateName; an element access with
  it as the key; per-object identity map), direct eval early errors, ZWNJ/ZWJ identifier parts (gated). Limit: a
  destructuring target `this.#x` is evaluated after the value is read (as for public members).
- *8.3 (done)* class static blocks (a static element whose initializer is a synthetic method holding the block).
- *8.4 (done)* RegExp `d` flag (`hasIndices`, `indices`); the one remaining test needs the `v` flag (10.2).
- *8.5 (done)* top-level await (a module with it is an async generator; ES2022 async module evaluation in
  ModuleRecord; `evaluate` runs the jobs of a pending graph).
- *8.6 (done)* computed keys of static and instance elements in source order; `.at()`, `Object.hasOwn`, `cause`
  pass. Left for M13: a destructuring target `this.x` is evaluated after the value is read; class methods with
  default parameters do not get a separate var scope; the `arity` own property of functions.

Status (2026-10-13, done): every ES2022 feature passes but for the limits above, the `v` flag test of
`regexp-match-indices` (10.2) and the Unicode 17 identifiers (JDK data).

### G — Gate every change by `VERSION_ECMASCRIPT`

Decided 2026-10-13: `VERSION_ES6` and the older versions keep the exact behavior of stock Rhino, bug fixes included.
The changes applied to every version so far (BACKWARD_COMPATIBILITY.md section 2, 2.1-2.46, from 40 commits) are
gated with `isLatestEcmaScript()`, their tests check both sides with `Utils.assertGatedBehavior`, and the test
expectations those commits changed are restored under `VERSION_ES6`. Section 2 then moves into section 1. Done in
batches by area, each verified (test262 must not move), perf-gated and committed. The analysis of the 40 commits
(about 210 distinct changes, some 70 of them undocumented) is split as follows:
- *G1 (done)* features stock Rhino did not have become `VERSION_ECMASCRIPT`-only (syntax, module API, new
  globals); `LatestFeaturesGateTest`.
- *G2 (done)* 2.1-2.9 (const/let, strict `this`, `caller`, bind, `arguments`); the stock arguments object is kept
  next to the spec one in `Arguments`.
- *G3 (done)* 2.10-2.14 (parameter early errors, `toString`, eval code, destructuring); stock array patterns in
  `Parser.destructuringArrayLegacy`.
- *G4 (done)* 2.15-2.22 (iteration protocol, literal early errors, `yield`, function environments); the stock
  for-of iteration is restored in `ScriptRuntime`.
- *G5 (done)* 2.23-2.28 (typed arrays, Reflect and Proxy, String symbols, Array writes, Date, JSON); the stock
  Reflect and Proxy methods are kept as `legacy*` methods.
- *G6 (done)* 2.29-2.34 (Array, Promise, Object, Set methods, typed arrays, RegExp); the stock Array, Set, typed
  array and RegExp algorithms are kept as `legacy*` methods.
- *G7 (done)* 2.35-2.46 (language residuals, Annex B, `await`, generators, realms, array-likes); the stock
  compiled `finally` and exception ranges are restored under `VERSION_ES6`. BACKWARD_COMPATIBILITY.md section 2
  became 1.17-1.62.

### M9 — ES2023

**9.1** Residuals: `findLast*`, change-array-by-copy (96%), hashbang (93%), symbols as `WeakMap`/`WeakRef` keys.

Status (2026-10-13, done): no engine change needed. Every ES2023 test passes (the earlier failures were fixed by
M2-M8); the only ones not run need `upsert` (12.3). Stock Rhino already had all four features, with the same
results as `VERSION_ES6` and `VERSION_ECMASCRIPT` in both modes (checked against the stock jar), so nothing is gated.

### M10 — ES2024

**10.1 Resizable `ArrayBuffer` / growable `SharedArrayBuffer`** (45%): `maxByteLength`, `resize`/`grow`,
length-tracking and out-of-bounds typed-array views, `transfer`/`transferToFixedLength`/`detached`. Perf-sensitive:
fixed-length buffers must keep their current element-access fast path (Nashorn gated the resizable path off the hot
path). **10.2 RegExp `v` flag** (27%): class set operations, `\q{}`, properties of strings (data source per 4.3).
**10.3** `Atomics.waitAsync`. **10.4** Residuals: `Promise.withResolvers`, `Object.groupBy`/`Map.groupBy`,
`isWellFormed`/`toWellFormed`.

Steps:
- *10.1 (done earlier)* resizable `ArrayBuffer` and growable `SharedArrayBuffer` already pass (the 45% above
  predates M5); the remaining failures are immutable-ArrayBuffer tests (out of scope).
- *10.2 (done)* RegExp `v` flag: the class-set grammar (nested classes, `&&`, `--`, `\q{}` strings, reserved
  punctuators), parsed into a set expression evaluated per code point (with case folding of each operand under
  `i`); classes with strings compile to an alternation, longest strings first. Properties of strings
  (`\p{RGI_Emoji}` and kin) are a SyntaxError: the JDK has no emoji sequence data (decision 4.3), 46 tests.
  Also fixed (gated): alternatives in a lookbehind, a `u` class in an alternative matching a supplementary
  character.
- *10.3 (done)* `Atomics.waitAsync`: a waiter whose notify posts a job through the Context's `HostJobScheduler`
  (decision 2026-10-13), and a scheduled job for a finite timeout; `EventLoop` is the default scheduler (the
  test262 runner and its agents run one). Without a scheduler, only the synchronous results are available.
- *10.4 (done earlier)* `Promise.withResolvers`, `Object.groupBy`/`Map.groupBy` and `isWellFormed`/`toWellFormed`
  pass, but for one test each of the last two: the primitive `this` residual (1.8c, left for M13).

Status (2026-10-13, done): every ES2024 feature passes but for the properties of strings of the `v` flag (no
emoji sequence data in the JDK, decision 4.3) and the primitive `this` residual.

### M11 — ES2025

**11.1 Iterator helpers** (9%): `Iterator` global, `Iterator.from`, lazy `map`/`filter`/`take`/`drop`/`flatMap`,
eager `reduce`/`toArray`/`forEach`/`some`/`every`/`find`, `%WrapForValidIteratorPrototype%`,
`%IteratorHelperPrototype%`. **11.2 Set methods** (75%): `union`, `intersection`, `difference`,
`symmetricDifference`, `isSubsetOf`, `isSupersetOf`, `isDisjointFrom` over GetSetRecord. **11.3** `RegExp.escape`.
**11.4 RegExp pattern modifiers** `(?ims-ims:…)` (70%) and duplicate named groups (81%). **11.5** `Promise.try`
(with async tests). **11.6 Import attributes and JSON modules** (`with { type: "json" }`, dynamic `import()` second
argument). `Float16Array`, `Math.f16round`, `DataView` float16 are already complete.

Status (2026-10-14, done): every ES2025 feature passes but for `Iterator.from` with a primitive receiver of
`@@iterator` getters (a getter on a primitive sees its wrapper object, M13). Set methods, `Promise.try`, duplicate
named groups and Float16 already passed; the iterator built-ins take a primitive `this` from `call`/`apply`.

### M12 — ES2026

**12.1** `Error.isError` (92%). **12.2** `Math.sumPrecise` (exact summation, `BigDecimal` as in Nashorn, or a
Shewchuk-style algorithm if faster — measured). **12.3** Upsert: `Map`/`WeakMap` `getOrInsert`/`getOrInsertComputed`.
**12.4** `Iterator.concat` (`iterator-sequencing`). **12.5** `Uint8Array` base64/hex: `fromBase64`, `fromHex`,
`toBase64`, `toHex`, `setFromBase64`, `setFromHex` with `alphabet`/`lastChunkHandling`/`omitPadding` (11%).
**12.6** JSON source access: reviver `context.source`, `JSON.rawJSON`, `JSON.isRawJSON` (0%). **12.7** `Array.fromAsync`
(needs async + async iteration). **12.8** `Date.parse` date-time string grammar alignment (decision: Rhino's legacy
lenient parsing must stay for `VERSION_ES6`).

Status (2026-10-14, done): every ES2026 feature passes. `Error.isError` and `Date.parse` needed no change; the
remaining failures of the built-ins concerned are the primitive `this` residual and later proposals (immutable
`ArrayBuffer`, Temporal, explicit resource management, `Iterator.zip`).

### M13 — Long tail to zero

Walk the remaining entries of `test262.properties` area by area (`annexB`, `built-ins/Date`, `built-ins/String`,
`built-ins/Object`, `built-ins/Array`, `language/*`) until the only remaining expected failures are documented
decisions. Each remaining exception gets a recorded reason in a `doc/CONFORMANCE_MONFLABS.md` (mirrors Nashorn's
`doc/CONFORMANCE.md`). Then decide whether to flip Rhino's default to stricter test262 expectations.

Status (2026-10-15, done): 99.1% of the in-scope tests pass (42,223 of 42,616). The remaining failures are the
documented limits of `doc/CONFORMANCE_MONFLABS.md`: Unicode data from the JDK (decision 4.3), agents that can block
(decision 3.4) and a single realm per error. The stricter expectations are already in place: a test listed as failing
that passes fails the run (0.3).

### M15 — A single language version

Remove the version distinction: the engine always behaves as `VERSION_ECMASCRIPT`. Remove the `isLatestEcmaScript()`
gates (keeping the `VERSION_ECMASCRIPT` side), make `VERSION_ECMASCRIPT` the default and then the only behavior of
the ES6+ versions, decide what remains of the JavaScript 1.x versions (a decision when reached), and update
`BACKWARD_COMPATIBILITY.md` (everything listed there then applies to every embedding).

### M14 — Performance (continuous, with a final dedicated pass)

Optimizations found along the way are done when obvious and measured. A final pass profiles the V8/SunSpider/perf-gate
scripts on the ES2026 engine (`PROFILERS=cpu ./gradlew jmh`) in both modes. Candidates known from Nashorn's
experience: number fast paths in BigInt-aware generic operators, regexp caching, `for-of` over built-in arrays without
an iterator object, `Promise.prototype.then` using the intrinsic `%Promise%`, symbol-keyed property lookup without
double `ToPrimitive`. Every optimization keeps test262 green and records its gain in the changelog.


Findings recorded while building the perf gate (2026-10-07), to investigate in this pass:

- Top-level script code is often slower in compiled mode than in the interpreter (e.g. `wideobject`, `split`,
  `matchreplace`, `regexp` in `testsrc/perf`), most likely global variable access from compiled top-level code.
- `run.wideobject.cmp` is bimodal across JVMs (19 ms or 60 ms): the JIT settles in one of two states.
- `run.properties.cmp` depends on what ran before it in the same JVM (profile pollution): 25 ms alone, 50 ms in the
  full run.
---

## Status tracker

| #    | Feature                                         | Status      | test262 slice before → after | Commit |
|------|-------------------------------------------------|-------------|------------------------------|--------|
| 0.1  | Plan, changelog, status tooling                 | done        | baseline 56.9% (JDK 25)      | 7ed50a954 |
| 0.2  | Perf gate                                       | done        | n/a (116 metrics)            | (this) |
| 0.3  | test262 harness upgrades                        | done        | 56.9% → 57.6% (async runs)   | (this) |
| 0.4  | Gating plumbing                                 | done        | n/a                          | (this) |
| 1.1  | Lexical declarations                            | done        | 57.6% → 57.8% (1.1a–e)       | 1434be149+ |
| 1.2  | Function semantics                              | done        | 57.8% → 58.7% (1.2a–g; parameter/body environments deferred) | (this) |
| 1.3  | Destructuring                                   | done        | 58.7% → 60.3% (1.3a–d)       | 45615e120 |
| 1.4  | Iteration protocol                              | done        | 60.3% → 60.5% (1.4a)         | b04e5d7a9 |
| 1.5  | Object literal extensions                       | done        | 60.5% (1.5a–b)               | d3ae8ef1c |
| 1.6  | Generators completion                           | done        | 60.5% → 61.1% (1.6a–c)       | ab6194c9e |
| 1.7  | Classes                                         | done        | 61.1% → 68.3% (1.7a–c)       | c8e10cff9 |
| 1.8  | Well-known symbols, Proxy/Reflect, built-ins    | done        | 68.5% → 69.5% (1.8a–c9)      | be7ac7655 |
| 1.9  | RegExp ES2015                                   | done        | 69.5% → 69.7% (1.9a)         | 13a800a18 |
| 1.10 | Templates and syntax residuals                  | done        | 69.7% → 70.6% (1.10a–g)      | 162371592 |
| 1.11 | Annex B (ES2015)                                | done        | 70.6% → 70.8% (1.11a–b)      | bf1e76df7 |
| 1.12 | ES modules                                      | done        | 70.8% → 71.6% (1.12a–c)      | 4d51232fe |
| 2.x  | ES2016                                          | done        | 71.6% (no change needed)     | 4d51232fe |
| 3.x  | ES2017 (async functions, SAB/Atomics)           | done        | 71.6% → 74.0% (3.1a–3.4)      | bdba199cf |
| 4.x  | ES2018 (async iteration, rest/spread, RegExp)   | done        | 74.0% → 83.3% (4.1–4.4)       | 9b7abd3ce |
| 5.x  | ES2019 residuals                                | done        | 83.3% (no change needed)     | 8f111e95f |
| 6.x  | ES2020                                          | done        | 83.3% → 84.6% (6.6)          | 3b5acfd55 |
| 7.x  | ES2021                                          | done        | 84.6% (+11)                  | 162d5a13f |
| 8.x  | ES2022 (class fields, private names, TLA)       | done        | 84.6% → 96.4% (8.1–8.6)      | 42c918e88 |
| G    | Gate the section-2 changes (VERSION_ECMASCRIPT) | done        | 96.4% (unchanged)            | 387bb2191 |
| 9.x  | ES2023 residuals                                | done        | 100% (no change needed)      | (this) |
| 10.x | ES2024 (resizable buffers, v flag)              | done        | 96.4% → 96.9% (10.2–10.3)    | 6ee110658 |
| 11.x | ES2025 (iterator helpers, Set methods, …)       | done        | 96.9% → 98.1% (11.1–11.6)    | 89c3ca0a7 |
| 12.x | ES2026                                          | done        | 98.1% → 98.8% (12.1–12.8)    | a05746ba7 |
| 13   | Long tail to zero                               | done        | 98.8% → 99.1% (42,223)       | 6a5e3ecae |
| 14   | Performance pass                                | continuous  |                              |        |
| 15   | Single language version                         | todo        |                              |        |
