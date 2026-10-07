# Rhino

Rhino is an open source (MPL 2.0) JavaScript engine, implemented in Java 17. The build system used is gradle, via the
gradle wrapper (`./gradlew`). There are no external dependencies, except for JUnit for unit tests.

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

- [TokenStream](rhino/src/main/java/org/mozilla/javascript/TokenStream.java) is the lexer
- [Parser](rhino/src/main/java/org/mozilla/javascript/Parser.java) is the parser, which produces an AST modeled by the
  subclasses of [AstNode](rhino/src/main/java/org/mozilla/javascript/ast/AstNode.java)
- the [IRFactory](rhino/src/main/java/org/mozilla/javascript/IRFactory.java) will generate a tree IR, modeled
  by [Node](rhino/src/main/java/org/mozilla/javascript/Node.java)
- there are two backends:
    - one which generates java classes, in [Codegen](rhino/src/main/java/org/mozilla/javascript/optimizer/Codegen.java);
    - and one which generates a bytecode for [Interpreter](rhino/src/main/java/org/mozilla/javascript/Interpreter.java),
      in [CodeGenerator](rhino/src/main/java/org/mozilla/javascript/CodeGenerator.java).

[ScriptRuntime](rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java) is the main class for all runtime
methods, shared between compiled classes and interpreter.

Builtins such as `Object` or `Array` are implemented in
[NativeObject](rhino/src/main/java/org/mozilla/javascript/NativeObject.java),
[NativeArray](rhino/src/main/java/org/mozilla/javascript/NativeArray.java), etc.

## Project goal: ECMAScript conformance

The goal is to update Rhino to support the latest ECMAScript standard **without breaking existing code**. The following
constraints apply to every change made toward that goal:

- **Backward compatibility by default.** Existing scripts and embeddings must keep working unchanged under the default
  language version (`Context.VERSION_ES6`) and older versions. New spec features that cannot break existing code may be
  enabled for these versions.
- **Breaking changes are gated by `Context.VERSION_ECMASCRIPT`.** This language version always represents the latest
  ECMAScript standard. Any behavior change that can break existing code (e.g. stricter early errors,
  `const`/strict-mode enforcement, changed semantics of legacy extensions, removed non-standard behavior) must only be
  active when the language version is `VERSION_ECMASCRIPT`, and must be disabled otherwise. Gate on it (e.g.
  `cx.getLanguageVersion() >= Context.VERSION_ECMASCRIPT`) rather than introducing ad-hoc flags.
- **Document every breaking change.** Each change gated by `VERSION_ECMASCRIPT` must be documented (what changes, why
  the spec requires it, and how to get the old behavior) in [RELEASE-NOTES.md](RELEASE-NOTES.md) and, where relevant,
  in the Javadoc of `Context.VERSION_ECMASCRIPT`.
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
