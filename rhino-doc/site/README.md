# Monflabs Rhino

[Rhino](https://github.com/mozilla/rhino) is a JavaScript engine written in Java, maintained by
Mozilla. Monflabs Rhino is a fork of it, taken from its development line after release 1.9.1
(October 2026). It keeps Rhino's architecture,
API and license (MPL 2.0) and brings the language up to **ECMAScript 2026**. Its conformance is
measured against the official [test262](https://github.com/tc39/test262) suite. It does this
**without breaking existing code**: every change that could alter what an existing script does is
tied to a language version, so an application can keep the behavior of stock Rhino.

```xml
<dependency>
  <groupId>org.monflabs.mozilla</groupId>
  <artifactId>rhino</artifactId>
  <version>2026.0</version>
</dependency>
```

The fork is not published to Maven Central. Build it and install it in your local repository from
a checkout of [monflabs/rhino](https://github.com/monflabs/rhino) (branch
`monflabs_enhancements`):

```bash
./gradlew :rhino:publishToMavenLocal :rhino-tools:publishToMavenLocal -x test
```

## At a glance

| | Stock Rhino 1.9.1 | Monflabs Rhino 2026.0 |
|---|---|---|
| Language | ES6 and parts of later editions | ES2026, measured by test262 |
| test262 (in scope) | 56.9% (where the fork started) | 99.1% (42,248 of 42,616 tests) |
| Default language version | `VERSION_ES6` (200) | `VERSION_ECMASCRIPT` (250) |
| Stock behavior | — | `VERSION_ES6`, selected explicitly |
| Java packages | `org.mozilla.javascript` | `org.monflabs.mozilla.javascript` |
| Maven group | `org.mozilla` | `org.monflabs.mozilla` |
| Java | 17 and later | 21 and later (25 recommended) |
| Target | Any JVM, Android included | Java server applications |

test262 is run in scope: ES2026 minus proper tail calls, `intl402/`, `staging/` and the proposals
after ES2026, the same exclusions as the Nashorn fork.

## The language versions

Rhino has always had a language version on its `Context`. The fork adds one more:

- **`Context.VERSION_ECMASCRIPT` (250)** is the latest ECMAScript, the target of the fork and the
  default of a new `Context`, of the shell, of the debugger and of the `javax.script` engine. It
  includes the changes the specification requires that can break existing code, such as the
  temporal dead zone of `let`, `const` assignment errors and the early errors.
- **`Context.VERSION_ES6` (200)** keeps the exact behavior of stock Rhino, bugs included: the
  features that stock Rhino did not have, the new syntax, the module API and the new globals, are
  `VERSION_ECMASCRIPT` only. What cannot change a result, such as the optimizations and the
  compiler's handling of large scripts, applies to every version.

```java
try (Context cx = Context.enter()) {
    // ECMAScript 2026: the default
    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
    // or the behavior of stock Rhino
    cx.setLanguageVersion(Context.VERSION_ES6);
}
```

In the shell, `-version 250` is the default, and `-version 200` gives the behavior of stock Rhino.

The plan is to remove the language versions eventually and always behave as `VERSION_ECMASCRIPT`.
For now every version, and the code that tests for them, stays.

## What it is for

The fork targets **Java server applications** that embed a JavaScript engine: compiled scripts,
many scopes, Java objects passed to scripts and back. It requires Java 21. It does not target
Android or micro JVMs, so it uses current Java freely. The engine has no dependencies.

Like stock Rhino, it has two backends, and every feature is implemented in both:

- a **class compiler** that turns scripts into JVM classes (compiled mode, the default);
- an **interpreter** (`cx.setInterpretedMode(true)`), which generates no classes: it starts
  sooner on short scripts and runs slower.

The test suites run in both modes and under both `VERSION_ES6` and `VERSION_ECMASCRIPT`.
A separate test runs the same scripts compiled and interpreted, and compares what they compute.

## How it was built

The same work was done earlier for [Nashorn](https://github.com/monflabs/nashorn), the engine
that the JDK used to ship, which now implements ES2026 and passes test262. That fork was the
reference for this one, for the order of the features, the algorithms of the built-ins and the
pitfalls. The logic was ported, not the code: Rhino keeps its own structure, its two backends and
its own way of defining built-ins. As in Nashorn, the built-ins are written in Java, not in
JavaScript.

## In this documentation

- [The playground in the browser](playground/index.html ':ignore'): the Rhino playground, a
  Swing application, running in this page with [CheerpJ](https://cheerpj.com), with no Java to
  install. It has samples of the language and of the Rhino extensions, and lets you switch between
  ES2026 and ES6. It runs the interpreter only: CheerpJ cannot link the call sites of the classes
  the compiler generates. The desktop playground has both modes.
- [Features on top of stock Rhino](features.md): what the fork adds, the language and the API
  for embedders.
- [Changes and incompatibilities](changes.md): what behaves differently, and how to migrate.
- [Performance optimizations](performance.md): what was made faster, and how performance is
  guarded.
- [Asynchronous code](async.md): generators, promises and the optional event loop, and how to
  write host functions such as `setTimeout()` and `fetch()` in Java.
- [How the engine is validated](validation.md): the harnesses that check the engine, from test262
  to the JavascriptPerformance project.

The detailed records are in the fork's repository:
[CHANGELOG_MONFLABS.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/CHANGELOG_MONFLABS.md)
(every change),
[BACKWARD_COMPATIBILITY.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/BACKWARD_COMPATIBILITY.md)
(every incompatibility, with examples),
[OPTIMIZATION.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/OPTIMIZATION.md)
(every optimization, with measurements) and
[MONFLABS_PLAN.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/MONFLABS_PLAN.md)
(the plan and its status).
