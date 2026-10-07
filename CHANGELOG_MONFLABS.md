# MONFLABS changelog

Changes made in the `org.monflabs.mozilla` fork of Rhino, newest first. Each entry says what changed, why, which
test262 slice moved, which behavior is gated by `Context.VERSION_ECMASCRIPT`, and the performance impact when
relevant. The plan these changes follow is in [MONFLABS_PLAN.md](MONFLABS_PLAN.md).

## Unreleased

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
