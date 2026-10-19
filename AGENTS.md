# Rhino

Rhino is an open source (MPL 2.0) JavaScript engine, implemented in Java 21. The build system used is gradle, via the
gradle wrapper (`./gradlew`). There are no external dependencies, except for JUnit for unit tests.

## Target platform

- This fork is meant to be embedded in **Java server applications**. Android and micro/embedded JVMs are not targets;
  do not constrain the design or the APIs used for them.
- The code compiles for **Java 21** (`--release 21`); Java 21 APIs may be used freely. The floor moves to Java 25 when
  it becomes available to us (expected early 2027); until then, Java 25-only APIs must not be used.
- **Java 25 is the reference JDK for test262**: `test262.properties` is generated on it
  (`RHINO_TEST_JAVA_VERSION=25`), since Unicode data (identifiers, case mapping, RegExp property escapes) comes from the
  running JDK.

## Useful commands

1. Build: `./gradlew build`
2. Run tests: `./gradlew test`
3. Format code: `./gradlew spotlessApply`
4. Checks (tests, formatting): `./gradlew check`

## Rules and code style

- Important: try to follow the existing code style and patterns.
- Be restrained in regard to writing code comments.
- Always add unit tests for any new feature or bug fixes. They should go either in `rhino` or `tests`; search for
  existing tests to make a decision on a case-by-case basis.
- New test classes should be written using JUnit 5. Migrating existing tests from JUnit 4 to JUnit 5 is not a goal
  though, unless explicitly requested.
- Code style is enforced via spotless. After every change, reformat the code.

## Code organization

The code base is organized in multiple modules. Most changes will go into the `rhino` or `tests` modules. Refer to
README.md for the full list.

- `rhino`: The primary codebase necessary and sufficient to run JavaScript code
- `rhino-tools`: Contains the shell, debugger, and the "Global" object, which many tests and other Rhino-based tools use
- `tests`: The tests that depend on all of Rhino and also the external tests, including the Mozilla legacy test scripts
  and the test262 tests

## Architecture

Rhino follows a classical architecture:

- [TokenStream](rhino/src/main/java/org/monflabs/mozilla/javascript/TokenStream.java) is the lexer
- [Parser](rhino/src/main/java/org/monflabs/mozilla/javascript/Parser.java) is the parser, which produces an AST modeled by the
  subclasses of [AstNode](rhino/src/main/java/org/monflabs/mozilla/javascript/ast/AstNode.java)
- the [IRFactory](rhino/src/main/java/org/monflabs/mozilla/javascript/IRFactory.java) will generate a tree IR, modeled
  by [Node](rhino/src/main/java/org/monflabs/mozilla/javascript/Node.java)
- there are two backends:
    - one which generates java classes, in [Codegen](rhino/src/main/java/org/monflabs/mozilla/javascript/optimizer/Codegen.java);
    - and one which generates a bytecode for [Interpreter](rhino/src/main/java/org/monflabs/mozilla/javascript/Interpreter.java),
      in [CodeGenerator](rhino/src/main/java/org/monflabs/mozilla/javascript/CodeGenerator.java).

[ScriptRuntime](rhino/src/main/java/org/monflabs/mozilla/javascript/ScriptRuntime.java) is the main class for all runtime
methods, shared between compiled classes and interpreter.

Builtins such as `Object` or `Array` are implemented in
[NativeObject](rhino/src/main/java/org/monflabs/mozilla/javascript/NativeObject.java),
[NativeArray](rhino/src/main/java/org/monflabs/mozilla/javascript/NativeArray.java), etc.

## Project goal: ECMAScript conformance

The goal is to update Rhino to support the latest ECMAScript standard **without breaking existing code**. The following
constraints apply to every change made toward that goal:

- **`VERSION_ECMASCRIPT` is the target.** The ultimate goal is to remove the language versions and always behave as
  `Context.VERSION_ECMASCRIPT` (plan milestone M15). Until then, **every behavior change is gated: only active under
  `VERSION_ECMASCRIPT`**, so that `VERSION_ES6` and the older versions keep their exact behavior, bug fixes included.
  Gate with `cx.isLatestEcmaScript()` at runtime and `compilerEnv.isLatestEcmaScript()` for syntax and early errors
  (so that `grep isLatestEcmaScript` lists every gated behavior), rather than ad-hoc flags, and test both sides with
  `Utils.assertGatedBehavior`.
- **Document every breaking change** in [BACKWARD_COMPATIBILITY.md](BACKWARD_COMPATIBILITY.md): every change whose
  result under `VERSION_ECMASCRIPT` differs from `VERSION_ES6` behavior, with a before/after example and the spec
  section. Gated changes are also listed under "Behavior changes" in the Javadoc of `Context.VERSION_ECMASCRIPT`, and
  every change gets a [CHANGELOG_MONFLABS.md](CHANGELOG_MONFLABS.md) entry.
- **test262 is the conformance target.** With `VERSION_ECMASCRIPT`, the engine must pass the full test262 suite. The
  suite already runs with this version (see `Test262SuiteTest`). When a change makes more tests pass, update
  [tests/testsrc/test262.properties](tests/testsrc/test262.properties) as described in
  [tests/README.md](tests/README.md); never add new expected failures to hide a regression.
- **No regressions.** The whole existing test suite (`./gradlew check`), including the Mozilla legacy tests, must keep
  passing under both `VERSION_ES6` and `VERSION_ECMASCRIPT`, in compiled and interpreted mode.

### Reference implementation: Nashorn

The same work has already been done for Nashorn, a peer project on disk at `../nashorn` (see its `CLAUDE.md`,
`doc/CONFORMANCE.md` and `doc/CHANGES-FROM-UPSTREAM.md`). It implements ES2026 and passes test262, so use it as the
primary source of inspiration: the order in which features were added, the spec algorithms implemented in its
built-ins (`core/src/main/java/org/monflabs/nashorn/internal/objects`), the parser/desugaring changes, and the pitfalls
it documents. Port the logic, not the code structure, and keep the differences in mind:

- Nashorn dropped its legacy modes and is ES2026-only; Rhino must keep its existing behavior by default and gate
  breaking changes behind `VERSION_ECMASCRIPT` (Nashorn's closest analog is how it gates Annex B behind `--annexB`).
- Nashorn compiles to JVM bytecode only; Rhino has two backends, so every language feature must be implemented in
  both `Codegen` (compiled mode) and `CodeGenerator`/`Interpreter` (interpreted mode).
- Nashorn's built-ins are generated by nasgen from annotations; Rhino's follow their own patterns (e.g.
  `LambdaConstructor`, `ScriptableObject`). Follow Rhino's existing patterns.
- As in Nashorn, built-ins are implemented in Java, leveraging the JDK, never in JavaScript.

### Workflow

- [MONFLABS_PLAN.md](MONFLABS_PLAN.md) is the plan: scope, milestone order, the definition of done for a feature, the
  decision protocol and the status tracker. Follow it in order and keep its tracker and decision table up to date.
- [CHANGELOG_MONFLABS.md](CHANGELOG_MONFLABS.md) gets an entry for every change to the engine.
- `python3 tests/test262-status.py [--by-feature] [--by-dir N]` reports the in-scope test262 pass rate.
- Scope is ES2026 minus Nashorn's exclusions: proper tail calls, `staging/`, `intl402/` and post-ES2026 proposals.
- No performance regression: run `buildtools/perf-gate.sh` before committing engine changes (about 18 minutes; see
  `benchmarks/README.md`). Keep the machine otherwise idle while it runs.
- Every optimization is documented in [OPTIMIZATION.md](OPTIMIZATION.md): the mechanism, when it applies, how it
  stays correct, and the measured gain; an experiment that measured no gain goes to its "Tried and dropped" table.
- When several reasonable options exist, ask the user; recommend the option aligned with Nashorn.
- When a feature is done, present it to the user, then commit it on the `monflabs_enhancements` branch.
- All work goes on the `monflabs_enhancements` branch, never directly on `master`. Do not merge, fast-forward or push
  it to `master` until the user explicitly asks, typically when releasing.
