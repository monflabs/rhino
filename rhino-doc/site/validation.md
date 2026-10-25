# How the engine is validated

Monflabs Rhino is checked by several harnesses. Some are part of the build and run on every change; others are
external, and run the engine on real programs next to other JavaScript engines. This page lists them, what each
one checks, and how to run it.

Most of them run in both backends, the class compiler and the interpreter, and a change must pass in both.

| Harness | Where | What it checks | Run |
|---|---|---|---|
| [test262](#test262) | `tests/` | Conformance to ECMAScript 2026 | `./gradlew :tests:test` |
| [The Mozilla test suite](#the-mozilla-test-suite) | `tests/testsrc/tests` | The behavior of stock Rhino | `./gradlew :tests:test` |
| [Doctests and script tests](#doctests-and-script-tests) | `tests/testsrc` | Regressions, Rhino extensions | `./gradlew :tests:test` |
| [Compiled against interpreted](#compiled-against-interpreted) | `tests/testsrc/jstests/compiled-interpreted` | The two backends compute the same results | `./gradlew :tests:test` |
| [Java unit tests](#java-unit-tests) | every module | The embedding API, `javax.script`, source maps | `./gradlew test` |
| [Stress modes](#stress-modes) | `tests/` | The splitting of large functions and wide jumps | `-Drhino.splitWeight=3`, ... |
| [Playground samples](#playground-samples) | `rhino-playground` | Every bundled sample runs | `./gradlew :rhino-playground:test` |
| [The perf gate](#the-perf-gate) | `buildtools/perf-gate.sh` | No performance regression | `buildtools/perf-gate.sh` |
| [JMH benchmarks](#jmh-benchmarks) | `benchmarks/` | Performance of single areas | `./gradlew jmh` |
| [JavascriptPerformance](#javascriptperformance) | [external project](https://github.com/monflabs/JavascriptPerformance) | Real programs, checked results, against other engines | `java -jar ...` |

## In the build

### test262

[test262](https://github.com/tc39/test262), the official ECMAScript conformance suite, is the target of the fork.
`Test262SuiteTest` runs it from a git submodule (`git submodule update --init` fetches it).

- **Scope.** ES2026, minus proper tail calls, `intl402/`, `staging/` and the proposals after ES2026: the same
  exclusions as the Nashorn fork. 42,616 tests are in scope; the fork passes 99.1% of them (42,248) under
  `VERSION_ECMASCRIPT`, where stock Rhino 1.9.1 passed 56.9%.
- **Modes.** Each test runs compiled and interpreted, and in strict and sloppy mode unless it is flagged for one.
- **Expected failures** are listed in `tests/testsrc/test262.properties`, and grouped with their reasons in
  [doc/CONFORMANCE_MONFLABS.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/doc/CONFORMANCE_MONFLABS.md).
  A listed test that starts to pass **fails the run**, so conformance only moves forward: the file is regenerated
  in the same change.
- **Async tests** run: the microtask queue is drained after the test, and it passes only if `$DONE` reported
  completion. Every test has a 60-second limit, so a test that never ends fails instead of hanging the run.
- **Reference JDK.** Unicode data comes from the JDK, so the expectations are generated on Java 25. On another JDK
  the Unicode-dependent tests run, but their outcome is not checked.

```bash
./gradlew :tests:test --tests org.monflabs.mozilla.javascript.tests.Test262SuiteTest
python3 tests/test262-status.py [--by-feature] [--by-dir N]   # the in-scope pass rate
```

`tests/README.md` describes the expectations file, how to regenerate it, and how to run a subset.

### The Mozilla test suite

`MozillaSuiteTest` runs the legacy test scripts of Rhino and SpiderMonkey (`tests/testsrc/tests`: `ecma`, `ecma_2`,
`ecma_3`, `js1_5`, ...), about 2,400 files, in the interpreter and in compiled mode. The lists of the files to run
are `tests/src/test/resources/interpreted.tests` and `compiled.tests`. These tests were written for stock Rhino,
so they guard the behavior that existing scripts rely on.

### Doctests and script tests

- **Doctests** (`tests/testsrc/doctests`, `DoctestsTest`) are recorded shell sessions: each input line is run, and
  its output is compared with the recording.
- **Script tests** (`tests/testsrc/jstests`) are JavaScript files, each run by a Java test. They cover regressions
  from GitHub issues, the Rhino extensions, the features of each edition (`es6`, `es2019` to `es2025`, ...) and the
  behavior that `VERSION_ES6` keeps (`backwardcompat`).

### Compiled against interpreted

In the tests, a compiler bug that makes a script fall back to the interpreter **fails** the test, instead of logging
a warning (`rhino.failOnCompileFallback=true`). `CompiledInterpretedTest` goes further: it runs the scripts of
`tests/testsrc/jstests/compiled-interpreted`, and the benchmark scripts, in both backends, and compares what they
compute. A script is added there for each shape that the class compiler specializes.

### Java unit tests

About 530 JUnit test classes across the modules test the engine from Java: the embedding API (`Context`, scopes,
Java interop, the language versions), the `javax.script` engine (`rhino-engine`), the stack traces of the
different formats, the source maps (`SourceMapSpecSuiteTest`, the
[source map specification's](https://github.com/tc39/source-map-tests) test cases), CommonJS modules and E4X.

### Stress modes

Some code paths of the class compiler are only reached by very large scripts. Three system properties force them
on every script, and test262 and the Mozilla tests must then pass as without them:

- `-Drhino.splitWeight=3` splits every function into chunks of a few statements, as the compiler does for a function
  too large for a class file;
- `-Drhino.classUnits=3` spreads the functions of every script over 3 classes;
- `-Drhino.wideJumps=true` makes every branch wide.

```bash
RHINO_TEST_JAVA_VERSION=25 ./gradlew :tests:test -Drhino.splitWeight=3 -DupdateTest262properties
git diff tests/testsrc/test262.properties   # a test that fails only split is a splitting bug
```

### Playground samples

`SampleRunTest` runs every sample bundled with the playground, compiled and interpreted, and fails on any error, so
the samples cannot drift from what the engine supports. The samples that end in an error on purpose are listed in
the test.

## Performance

A change must not make the engine slower. [Performance optimizations](performance.md#how-performance-is-measured-and-guarded)
describes the method in detail.

### The perf gate

`buildtools/perf-gate.sh [base-ref]` compares the working tree with an earlier revision (by default the upstream
base, tagged `perf-baseline`) on 116 metrics: startup, compilation, and steady-state execution of scripts that each
guard a hot path, in both backends and both language versions. The two revisions are measured on the same machine,
in alternating rounds, against tolerances measured on that machine. It runs before every engine change, and takes
about 18 minutes.

### JMH benchmarks

`benchmarks/` holds the JMH benchmarks of stock Rhino: SunSpider, the V8 benchmarks, and micro-benchmarks of slot
maps, property access and built-in object creation (`./gradlew jmh`; `BENCHMARK=<regex>` and `EVALMODE=Compiler` or
`Interpreter` narrow a run). See `benchmarks/README.md`.

## Outside the build

### JavascriptPerformance

[JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance) is a separate project that runs the same
benchmarks on several engines: the fork (under `VERSION_ECMASCRIPT`, compiled and interpreted), stock Rhino 1.8.0
(under `VERSION_ES6`), the [Monflabs Nashorn](https://github.com/monflabs/nashorn) fork, OpenJDK Nashorn, GraalJS
and V8. Each engine runs in its own JVM, in interleaved rounds.

It is a correctness harness as much as a performance one. **A benchmark that throws, does not compile, or fails its
own result check is reported as FAILED**, not timed: Octane's and the V8 benchmarks' checksums (mandreel's, for
example), the results of the `intrinsics` programs, and fingerprints of the Web Tooling results computed on V8.
Running the same file on six engines also shows which failures belong to the engine and which to the benchmark.

| Suite | What it runs | Why it matters for validation |
|---|---|---|
| `octane` | Google's Octane: Box2D, Crypto, DeltaBlue, EarleyBoyer, GameBoy emulator, Mandreel, NavierStokes, PDF.js, RayTrace, RegExp, Richards, Splay | Large ES5 programs, with result checks; `mandreel` and `pdfjs` are compiled programs of several MB |
| `sunspider` | SunSpider 1.0.2, 26 files | Each file is wrapped in a function, which takes other paths than top-level code |
| `v8-benchmarks` | The V8 Benchmark Suite, version 6 | Checked results |
| `ubench` | Micro-benchmarks of single operations | Hot paths, compared with the other engines |
| `intrinsics` | Generators, iterators, promises, async functions and async generators in small programs | ES2015+ features, each checked against its expected result |
| `web-tooling` | The [Web Tooling Benchmark](https://github.com/v8/web-tooling-benchmark): Acorn, Babel, Prettier, TypeScript, Terser, ... | Real modern libraries of 1 to 8 MB, checked against V8's results |
| `jetstream3` | The JavaScript workloads of [JetStream 3.0](https://github.com/WebKit/JetStream): ARES-6, class fields, BigInt, Proxy (MobX, Vue), three.js, Babylon.js, React server-side rendering, ... | Recent language features and large bundles |

`web-tooling` and `jetstream3` reach code that no other harness does: classes, destructuring, generators and very
large functions written by real tools. Several fixes of the fork came from them: Prettier's thousands of nested
functions ran the compiler out of memory, and the module functions of the Babylon.js and three.js bundles were too
large to compile until the splitter learned to move `let`, `const` and `class` declarations into its chunks.

The fork is not on Maven Central, so it is first published to the local Maven repository:

```bash
# in the rhino checkout
./gradlew :rhino:publishToMavenLocal :rhino-tools:publishToMavenLocal -x test
# in the JavascriptPerformance checkout
mvn package -DskipTests
java -jar target/javascript-performance-1.0.0-SNAPSHOT-all.jar \
  --suites=octane,sunspider,ubench,intrinsics --engines=RHINO,NASHORN_MONFLABS \
  --rounds=1 --warmup=1 --iterations=1
```

The `RHINO` group is the fork and stock Rhino, both compiled. The project's README describes the options, the
reports (CSV and HTML) and the method.
