# Robustness testing of the MONFLABS fork

test262 says whether the engine does what the specification asks of programs written to test it
(see [CONFORMANCE_MONFLABS.md](CONFORMANCE_MONFLABS.md)). It says little about programs nobody wrote on purpose:
a class compiler that silently falls back to the interpreter, a deeply nested `finally`, a stack or heap exhausted as
a Java error, malformed input to the parser. The robustness work looks for those, with tests in this repository and
three harnesses outside it.

| Layer | Where | What it guards |
| --- | --- | --- |
| `RobustnessFixesTest` and the whole suite | `./gradlew check` | Every fixed bug stays fixed, in both backends |
| test262 | `Test262SuiteTest` | Conformance under `VERSION_ECMASCRIPT` |
| The JSC stress corpus | [JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance) | Crashes and failures in the corners a JIT gets wrong |
| Differential fuzzing | JavascriptPerformance | Wrong results, on programs whose output the specification fully determines |
| Jazzer | JavascriptPerformance | Java exceptions escaping the parser, compiler, regexps and `JSON.parse` |

## In this repository

`tests/testsrc/jstests/robustness/robustness-fixes.js` holds the regressions the harnesses found, one function per
bug, each asserting what V8 computes. `RobustnessFixesTest` runs it under `VERSION_ECMASCRIPT`, compiled and
interpreted, so a fix in one backend that the other lacks fails. A case is written to fail before its fix.

```
./gradlew :tests:test --tests RobustnessFixesTest
./gradlew :tests:test --tests Test262SuiteTest
./gradlew check
```

A fix is checked against the whole suite (`./gradlew check`, both language versions, both backends) and against
test262, which must stay at the failures `tests/testsrc/test262.properties` lists. A change observable under
`VERSION_ES6` is gated and recorded in [BACKWARD_COMPATIBILITY.md](../BACKWARD_COMPATIBILITY.md), as any other
(1.106 to 1.108, and 2.5 for the limits).

## The harnesses of JavascriptPerformance

[JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance), the project that benchmarks this fork
against the other JVM engines, runs three robustness harnesses. Its README is the reference for their options; what
follows is what they do and how their findings are read. They use the `org.monflabs.mozilla:rhino` published to
`~/.m2`:

```
./gradlew :rhino:publishToMavenLocal :rhino-tools:publishToMavenLocal -x test
cd ../JavascriptPerformance
mvn package -DskipTests
tools/robustness/fetch-corpora.sh            # once: a sparse clone of WebKit's JSTests
R="java -cp target/javascript-performance-1.0.0-SNAPSHOT-all.jar org.monflabs.nashorn.performance.robustness.Robustness"
$R corpus --engines=RHINO_MONFLABS_COMPILED,RHINO_MONFLABS_INTERPRETED
$R fuzz --engines=RHINO_MONFLABS_COMPILED,RHINO_MONFLABS_INTERPRETED --seed=40000 --count=500
tools/robustness/jazzer.sh RhinoCompile 600
```

**V8 is the reference** (through Javet): a difference counts only when V8 disagrees with the engine. Each script
runs in a fresh context, in child JVMs that hold 100 files each, so a hang or a dead JVM costs one file and not the
run. Two settings make Rhino's failures visible:

- `rhino.failOnCompileFallback` is on. A class compiler bug otherwise only logs a warning and runs the script
  interpreted, much slower but correct, so no difference in output would show it. With the property it throws.
- The compiled and interpreted backends run as two engines, and when they disagree with each other the finding is
  counted under `backends`, whatever V8 did.

### The JSC stress corpus

JavaScriptCore's `JSTests/stress` is about 6,000 regression tests, many reduced from JIT crashes: integer overflow,
holes, prototype changes in a loop, deep recursion, huge strings. About 4,300 run here. The others need `jsc` shell
functions with real semantics, modules, JSC feature flags, or the legacy `f.caller`. A test checks itself, so
completing is passing. A Java exception escaping the engine, a `StackOverflowError` or `OutOfMemoryError` reached as
a Java error, or a throw where V8 completes is a finding; a test V8 fails as well is JSC-specific. On this corpus
the Java exceptions went from 14 (compiled) and 12 (interpreted) to none, and the limits reached as Java errors
from 63 and 49 to 3.

### Differential fuzzing

The generator writes programs of 10 to 30 top-level statements, each in a `try` of its own: classes with fields,
private members, accessors and inheritance, generators, closures, destructuring, spread, labeled loops, `switch`,
getters, proxies, BigInt, regexps, iterator helpers, promises and about fifty built-in methods. Everything they
print goes through a `__p` that depends on nothing an engine may choose, and implementation-defined corners (`Date`,
`Math.random`, locales, transcendental `Math`, error messages, inconsistent comparators) are never generated. A step
budget makes every program end the same way on every engine. So any difference from V8 is a bug of one of the two
engines, and the harness reduces it, statement by statement, to a program in `<out>/reduced/` that runs as it is in
the Rhino shell or in Node. Its header records what each engine printed.

The same seed always generates the same program. The campaign behind the "Robustness fixes" entry of the changelog
ran batches of 500 programs, seeds 40000, 50000, … 180000, repeated after every batch of fixes until a round found
nothing new. It fuzzed the Monflabs Nashorn fork at the same time, and every Nashorn finding was run on this fork as
well (compiled, and interpreted with `-int`): most were Nashorn's alone (its optimistic types have no counterpart
here), and the one Rhino shared, `join` of an array that holds itself, is fixed in both.

### Jazzer

`tools/robustness/jazzer.sh <Target> [seconds]` runs coverage-guided fuzzing over the engine's own classes, seeded
with the JSC tests and a dictionary of JavaScript tokens:

| Target | What it does with the fuzzer's bytes |
| --- | --- |
| `RhinoCompile` | Parses and compiles them as a script, in both backends |
| `RhinoEval` | Compiles and runs them, within a budget of 100,000 instructions |
| `RhinoRegExp` | Compiles a pattern with flags and runs it on a short subject |
| `RhinoJson` | Parses them as JSON and checks that the value survives a round trip |

A JavaScript error is an expected outcome; a Java exception that escapes is a finding. The corpus Jazzer grows is
kept under `target/robustness/jazzer/<Target>/corpus`, so the next run continues from it.

## Reading a finding

Each reduced program is checked before it becomes a fix:

1. **Which backend.** Run it compiled and with `-int`. A difference in one backend only is a bug of `Codegen` or of
   the `Interpreter`; one in both is in the runtime or a built-in.
2. **Which language version.** Run it under `VERSION_ES6` as well. A fix that changes what `VERSION_ES6` computes is
   gated.
3. **Against the other engines.** Run it on Node and on the Monflabs Nashorn fork. When V8 is the odd one out, the
   specification decides, and V8 has been wrong.

A fixed bug gets a function in `robustness-fixes.js` that fails before the fix and a line in the
[CHANGELOG_MONFLABS.md](../CHANGELOG_MONFLABS.md) entry. Then the suite, test262 and the harness that found it run
again.

### Differences that are not bugs

| Kind | Example | Why it is not a bug |
| --- | --- | --- |
| Property order after a spread | `Object.keys({...1, get y() {}, length() {}})` is `length,y` in V8 | V8 reorders the keys of a literal that starts with a spread; the specification and this fork give `y,length` |
| The last digit of a large double | `2.7356347530552557e+28` in V8 | The shortest-digits printing of a value computed through a different but equally exact sequence of operations |
| `RangeError` for `TypeError` at an engine limit | A string past 2^29 - 24 characters | Which limit is reached first is the engine's own; both are errors |
| A test that needs JSC's watchdog | A JSC test meant to be stopped after a time | It never ends on its own, on any engine without the watchdog |
