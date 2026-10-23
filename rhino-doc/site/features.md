# Features on top of stock Rhino

Monflabs Rhino is a fork of [Mozilla Rhino](https://github.com/mozilla/rhino), based on stock Rhino 1.9.1. It brings
the engine to **ECMAScript 2026** (ECMA-262, 17th edition) without breaking existing code. The fork is developed at
[github.com/monflabs/rhino](https://github.com/monflabs/rhino) and released as `2026.0`.

This page lists what the fork adds: language features, APIs for embedders and compiler capabilities. What can make
existing code behave differently is described in [Changes and incompatibilities](changes.md), and the engine's
optimizations in [Performance optimizations](performance.md).

## At a glance

| | Stock Rhino 1.9.1 | Monflabs Rhino 2026.0 |
|---|---|---|
| Language | ES2015 and later, partially | ES2026 |
| test262 (in-scope tests) | 56.9% | 99.1% (42,248 of 42,616) |
| Classes, async functions, ES modules | no | yes |
| Default language version | `VERSION_ES6` (200) | `VERSION_ECMASCRIPT` (250) |
| Maven group / Java packages | `org.mozilla` | `org.monflabs.mozilla` |
| Java | 17 and later | 21 and later |

The 56.9% is the fork's starting point, measured with the same test262 checkout and scope (on Java 25).

## Language versions

The fork adds one language version, `Context.VERSION_ECMASCRIPT` (250). It means "the latest edition the engine
supports", currently ES2026.

- **`VERSION_ECMASCRIPT`** has every feature on this page and follows the specification wherever stock Rhino did
  not. It is the default.
- **`VERSION_ES6`** (200) and the older versions keep the behavior of stock Rhino, bug fixes included. The language
  features stock Rhino did not have (classes, async functions, modules, ...) are not available there: their syntax is
  a SyntaxError, as in stock Rhino.

Changes that cannot alter the result of existing code apply under every version. These are the compiler
capabilities described under [Engine capabilities](#engine-capabilities) and the optimizations.

### How to enable

Nothing to do: a new `Context` and a new `CompilerEnvirons` use `VERSION_ECMASCRIPT`, and so do the shell, the
debugger and the `javax.script` engine.

```java
try (Context cx = Context.enter()) {
    // cx.getLanguageVersion() == Context.VERSION_ECMASCRIPT
    Scriptable scope = cx.initStandardObjects();
    cx.evaluateString(scope, "class Point { #x = 1; get x() { return this.#x; } }", "app.js", 1, null);
}
```

To keep the behavior of stock Rhino, select `VERSION_ES6` explicitly:

```java
cx.setLanguageVersion(Context.VERSION_ES6);
```

To apply it to every context, set it in `ContextFactory.onContextCreated`. In the shell, use `-version 200`
(`-version 250` is the default). In the `javax.script` engine, put `ScriptEngine.LANGUAGE_VERSION` = `"200"` in the
engine scope.

Syntax and early errors follow the version the code was compiled with; runtime behavior follows the version of the
context that runs it.

## Language features

Everything in this section requires `VERSION_ECMASCRIPT`, and works in both the interpreter and compiled mode.

### By edition

The table lists what the fork added or completed. Features that stock Rhino already implemented fully (for example
`Array.prototype.at`, `Object.groupBy`, `Promise.withResolvers`, `Promise.try`, the Set methods, `Float16Array` or
`Error.isError`) are not listed.

| Edition | Added | Completed to the specification |
|---|---|---|
| ES2015 | classes (`extends`, `super`, subclassing built-ins), spread arguments in the interpreter, ES modules | `let`/`const` (temporal dead zone, per-iteration bindings, global lexical scope), destructuring, iteration protocol, generators, function semantics, `arguments`, Proxy/Reflect, well-known symbols, RegExp `u`/`y`, Annex B |
| ES2016 | | `**`, `Array.prototype.includes` |
| ES2017 | async functions, arrows and methods, `SharedArrayBuffer`, `Atomics` | |
| ES2018 | async generators, `for await`, `Symbol.asyncIterator` | object rest/spread, RegExp dotAll, named groups, Unicode property escapes |
| ES2020 | `import()`, `import.meta`, `export * as ns` | optional chaining, BigInt, `globalThis` |
| ES2021 | | logical assignment, `WeakRef`, `FinalizationRegistry` |
| ES2022 | class fields, private methods and accessors, `#x in obj`, static blocks, top-level `await`, RegExp `d` flag (match indices) | |
| ES2024 | RegExp `v` flag, `Atomics.waitAsync` | resizable `ArrayBuffer`, growable `SharedArrayBuffer` |
| ES2025 | `Iterator` helpers, `RegExp.escape`, RegExp modifiers, import attributes, JSON modules | |
| ES2026 | `Math.sumPrecise`, `Map`/`WeakMap` `getOrInsert`/`getOrInsertComputed`, `Iterator.concat`, `Uint8Array` base64 and hex, `JSON.rawJSON`, `JSON.isRawJSON`, reviver source text, `Array.fromAsync` | |

### Classes

Class declarations and expressions, with constructors, `extends` (including `extends null` and arbitrary
expressions), `super` calls and property accesses, static and computed members, and subclasses of built-ins. ES2022
adds public and private fields, private methods and accessors, static blocks and the `#x in obj` brand check.

```js
class Counter extends Base {
  static #instances = 0;
  #count = 0;
  static { this.created = new Date(); }
  constructor() { super(); Counter.#instances++; }
  increment() { return ++this.#count; }
  static isCounter(o) { return #count in o; }
}
```

Private elements are invisible to reflection, to Java interop and to `Proxy`.

### Async functions and async iteration

`async function`, async arrows and methods, `await`, async generators, `yield*` in them and `for await`. Async code
runs on the context's microtask queue, the same queue as promise reactions, in the order the specification
defines. No threads are involved: the body is suspended like a generator and resumed from promise reactions.

```js
async function* lines(urls) {
  for (const url of urls) yield await load(url);   // load() returns a promise
}
for await (const line of lines(list)) console.log(line);
```

`Array.fromAsync` builds an array from an async iterable, a sync iterable or an array-like.

### ES modules

`import` and `export` in every form (named, default, namespace, `export * from`, `export * as ns from`, string
export names), live bindings, cyclic graphs, top-level `await`, `import()` in scripts and modules, and `import.meta`
(a null-prototype object whose `url` is the module's origin, or its name).

Import attributes are supported, and `with { type: "json" }` loads a JSON module whose `default` export is the
parsed value:

```js
import config from "./config.json" with { type: "json" };
const { render } = await import("./view.mjs");
```

Modules are loaded through the [module loader API](#module-loaders). They coexist with CommonJS `require()`.

### Built-ins

| Area | Added |
|---|---|
| Iterators | the ES2025 `Iterator` global: `Iterator.from`, `Iterator.concat`, lazy `map`, `filter`, `take`, `drop`, `flatMap`; eager `reduce`, `toArray`, `forEach`, `some`, `every`, `find` |
| Math | `Math.sumPrecise` (exactly rounded sum) |
| Collections | `Map.prototype.getOrInsert`, `getOrInsertComputed`, and the same on `WeakMap` |
| Binary data | `Uint8Array.fromBase64`, `fromHex`, `toBase64`, `toHex`, `setFromBase64`, `setFromHex`; `SharedArrayBuffer`; `Atomics` (including `wait`, `notify`, `waitAsync`) |
| JSON | `JSON.rawJSON`, `JSON.isRawJSON`; a reviver receives a third argument with the `source` text of primitive values |
| Arrays | `Array.fromAsync` |

Every built-in iterator, generator and Java collection iterator inherits `Iterator.prototype`, so the helpers work
on all of them:

```js
Iterator.from(javaList).filter(s => s.length > 3).map(s => s.toUpperCase()).toArray();
Math.sumPrecise([1e20, 0.1, -1e20]);                       // 0.1
new Map().getOrInsertComputed("k", key => key + "!");      // "k!"
new Uint8Array([72, 105]).toBase64();                      // "SGk="
JSON.stringify({ big: JSON.rawJSON("12345678901234567890") });
```

Beyond the new functions, the existing built-ins follow the specification's algorithms under `VERSION_ECMASCRIPT`:
species, property order, Proxy invariants, typed arrays as exotic objects, Promise combinators that close their
iterators, and so on. The details are in [Changes and incompatibilities](changes.md).

### Regular expressions

The fork extends Rhino's own regular expression engine rather than delegating to `java.util.regex`, so there is one
engine with the specification's semantics.

| Feature | Example |
|---|---|
| `d` flag: match indices (`indices`, `indices.groups`), `hasIndices` | `/(?<y>\d{4})/d.exec(s).indices.groups.y` |
| `v` flag: set operations, nested classes, string literals in classes | `/[\p{L}--[a-z]]/v`, `/[\q{abc\|d}]/v` |
| Pattern modifiers | `/(?i:abc)def/` |
| `RegExp.escape` | `new RegExp(RegExp.escape(userInput))` |
| Unicode property escapes, lookbehind, named groups, dotAll | completed to ES2026 |

Unicode data (property escapes, identifiers, case mapping) comes from the running JDK; Java 25 gives Unicode 16. The
fork bundles no Unicode database, so `Script_Extensions`, the binary properties the JDK does not expose and the
properties of strings of the `v` flag (`\p{RGI_Emoji}` and similar) are not supported.

### Specification semantics

Much of the work makes existing syntax behave as the specification says: the temporal dead zone of `let` and
`const`, a TypeError on assignment to a `const`, global lexical declarations shared across scripts, early errors at
compile time, strict-mode `this`, iterator closing in destructuring and `for-of`, completion values, and Annex B.
These change results that existing code may rely on, which is why they are gated. They are listed, with examples, in
[Changes and incompatibilities](changes.md).

## For embedders

### Module loaders

The `org.monflabs.mozilla.javascript.modules` package, modeled on Nashorn's `api.modules`, decides where `import`
finds its modules. A `ModuleLoader` is a functional interface, `Module load(String specifier, Module referrer)`,
returning null when it does not have the module. Loaders form a chain in which the first answer wins.

| Class | Loads |
|---|---|
| `PathModuleLoader(Path root)` | files: a relative specifier against the importing module's directory, any other against the root |
| `ResourceModuleLoader(Class<?> anchor, String root)` | class path resources under a root path |
| `new JavaModuleLoader().add(name, Map<String, Object>)` | modules whose exports are Java values (`default` is the default export) |

A loader returns `Module.source(name, text, origin)` for JavaScript source, where `origin` lets it resolve the
module's relative imports, or `Module.values(name, exports)` for Java values. JSON modules need no special loader:
the engine parses the text of any loader as JSON when the import has `type: "json"`.

The chain is set on the `ContextFactory` and can be overridden per `Context`:

```java
ContextFactory factory = new ContextFactory();
factory.setModuleLoaders(
        new JavaModuleLoader().add("host", Map.of("version", "1.0")),
        new PathModuleLoader(Path.of("scripts")));

try (Context cx = factory.enterContext()) {
    TopLevel scope = cx.initStandardObjects();
    Scriptable ns = cx.evaluateModule(scope,
            "import { version } from 'host'; export const v = version;", "main");
    Object v = ns.get("v", ns);   // "1.0"
}
```

`Context.evaluateModule` loads, links and evaluates a module and returns its namespace object, whose properties are
its exports. `Context.loadModule` returns a `ModuleRecord` with `link`, `evaluate` and `getNamespace` for finer
control. A module is evaluated once per realm. When a module graph uses top-level `await`, `evaluate` runs the promise
jobs until the graph settles, then throws its error if any, so the Java call stays synchronous.

### Host jobs and event loops

Some operations finish later, from another thread or after a delay. In ES2026 the only one is
`Atomics.waitAsync`. The engine hands them to a `HostJobScheduler` set on the context with
`Context.setHostJobScheduler`:

- `expect()` announces a job that another thread will `post` later;
- `schedule(job, delayMillis)` runs a job after a delay.

A server can implement it over its own executor or event loop: the jobs of a context run one at a time, on a thread
where that context is current, never while one of its scripts is running. `EventLoop` is a ready-made
implementation without a timer thread, run by the context's own thread:

```java
EventLoop loop = new EventLoop();
cx.setHostJobScheduler(loop);
cx.evaluateString(scope, source, "script", 1, null);
loop.run(cx);   // or loop.run(cx, timeoutMillis)
```

Without a scheduler, which is the default, `Atomics.waitAsync` throws a TypeError when it would have to wait.

### Shared memory across threads

`SharedArrayBuffer` and `Atomics` work across contexts on different threads. `NativeSharedArrayBuffer.share(scope,
buffer)` returns a `SharedArrayBuffer` of another realm over the same memory, so two contexts can exchange data and
synchronize with `Atomics.wait`/`notify`. Every thread can block in `Atomics.wait`, the main one included, as suits a
server.

### Diagnostics

When compiled mode cannot compile a script and runs it in the interpreter instead, which is much slower, the
compilation now says so: a warning to its error reporter, and a `WARNING` of the JDK platform logger
`org.monflabs.mozilla.javascript`. In stock Rhino this fallback is silent. Set the level of that logger to silence
it.

## Engine capabilities

These apply under every language version, and do not change any result.

### Code too large for one class

The JVM limits a method to 64 KB of bytecode and a class to 65,535 constants and `invokedynamic` sites. In stock
Rhino a script that reaches a limit runs entirely in the interpreter. The fork, following Nashorn:

- checks the 64 KB limit against the bytecode only, as the JVM does, rather than the whole `Code` attribute;
- splits a function too large for one method into nested arrow functions called in place, smaller on each failed
  attempt; `return`, `break` and `continue` across the pieces behave as before, and stack traces show the original
  function;
- splits top-level code the same way, keeping the script's completion value;
- spreads the functions of a script over several classes, up to 256, when one class runs out of constants or
  call sites;
- compiles literals of more than 1,000 constants (large data tables) as data that is parsed once.

Generated code and large libraries now run compiled: the Octane `mandreel` benchmark compiles into 16 classes, and
`pdfjs` compiles with its 500 KB of font tables. What still falls back is reported (see
[Diagnostics](#diagnostics)): generators, async functions, derived constructors and functions with a direct `eval`
or `with` that exceed a limit, since these are not split.

### Compiled generators and async code

A generator or async function that the class compiler could not handle used to send the whole script to the
interpreter, silently. Several such cases now compile, among them bugs inherited from upstream: a `yield` or
`await` in the arguments of a call (`r.push(await x)`), in the source of an object spread (`{ ...await p }`), or in
an array literal with a spread element, which fails with a `VerifyError` in Rhino 1.9.1.

## Tooling

- **Shell**: `-version 250` is the default; `-version 200` gives the stock behavior.
- **`javax.script` engine**: under the default version, the global `let`, `const` and `class` declarations of a
  script are visible to the next scripts evaluated with the same bindings.
- **test262**: the runner executes async tests, module tests and the multi-agent `$262.agent` tests, and a test listed
  as failing that passes fails the run, so conformance only moves forward. `python3 tests/test262-status.py
  [--by-feature]` reports the in-scope pass rate.
- **Java**: the engine compiles for Java 21 and is meant for Java server applications; Android is not supported.
  Java 25 is the reference JDK for test262, since Unicode data comes from the JDK.
- **Playground**: a [browser playground](playground/index.html ':ignore') runs the engine in the page.

## Scope and conformance

### Out of scope

| Excluded | Why |
|---|---|
| Proper tail calls | normative, but a calling convention the JVM cannot afford on every call |
| `intl402` (ECMA-402, `Intl`) | a separate standard |
| test262 `staging/` | proposals, not part of an edition |
| Post-ES2026 proposals: Temporal, explicit resource management (`using`, `DisposableStack`), `import defer`, `ShadowRealm`, decorators, `Iterator.zip`, immutable `ArrayBuffer`, ... | not in ECMAScript 2026 |

### Remaining test262 gaps

Of the 42,616 in-scope tests, 42,248 pass. The rest are documented limits:

| Area | Tests | Reason |
|---|---|---|
| RegExp Unicode property escapes | 269 | code points newer than the JDK's Unicode version, binary properties and `Script_Extensions` the JDK does not expose |
| RegExp `v` flag, properties of strings | 39 | no emoji sequence data in the JDK |
| Identifiers | 12 | Unicode 17 identifier characters |
| Cross-realm | about 10 | errors are created in the realm of the code that catches them |
| `IsHTMLDDA` | 35, skipped | a browser-only host hook (`document.all`) |
| `Atomics.wait` on a main thread that cannot block | 2, skipped | written for hosts whose main thread cannot block |
