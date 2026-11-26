# Rhino Benchmarks

This directory contains a collection of various benchmarks that have been added to the Rhino
project over the years. They have all been collected here and run using the JMH framework.

## Running the Benchmarks

To run all the benchmarks that exist, simply run, from the top-level directory:

    ./gradlew jmh

In addition, the environment variable BENCHMARK may be used to restrict which benchmarks are
run -- it is a regular expression that matches the names of the benchmarks. 

For example, to run only the SunSpider and V8 benchmarks, you can run:

    BENCHMARK=V8|SunSpider ./gradlew jmh

With the settings of `build.gradle` (4 warmup iterations of 5 seconds and 5 measured iterations of
2 seconds, for 173 combinations of a benchmark and an evaluation mode), running all the benchmarks
takes about an hour and a half.

If you want to run benchmarks in one mode (Interpreter or Compiler)
then that can be done using the `EVALMODE` environment variable. For example:
```
EVALMODE=Compiler BENCHMARK=V8 ./gradlew jmh
```
will run all the V8 benchmarks, but only in compiled mode.

The `PROFILERS` environment variable adds JMH profilers: `cpu` and `alloc` select the async profiler
with JFR output, and any other value is a comma-separated list of JMH profiler names.

## How the Benchmarks work

Java, with its just-in-time compilation pipeline, bytecode generation in Rhino, and
Java's super-complex garbage collector, is sensitive to warm up time and as a result, Rhino has 
more variation between runs than other JavaScript engines. To get a repeatable result, we use
the JMH framework, which runs each benchmark many times and does other "black hole" protection
operations to try and get an accurate result. For this reason, these benchmarks take a lot longer
to run than your favorite on-line JavaScript benchmarking web site.

The purpose of these benchmarks has historically been to make Rhino perform better in server-side
environments. The benchmarks that run scripts take the evaluation mode as a parameter (`evalMethod`:
`Interpreter` and `Compiler`) and run in both unless `EVALMODE` selects one. They set
`Context.VERSION_ES6`: they do not measure `VERSION_ECMASCRIPT`, which the performance gate below
covers.

## Benchmark Notes

Here are a few notes on the specific benchmarks:

* **SunSpiderBenchmark**: These are the venerable JavaScript benchmarks that have been used
for a long time. They test both low-level operations like math operations, as well as higher-level
tasks.
* **V8Benchmark**: These are Google's V8 benchmarks, which may have been created to show how 
efficient V8 is. They are still a good way to show how far we have to go.
* **SlotMapBenchmark**: This is a low-level benchmark of the various SlotMap types in Rhino.
Tiny changes in SlotMap performance affect property access in Rhino and translate into big
changes in the other benchmarks.
* **PropertyBenchmark**: This is a micro-benchmark that uses JavaScript code to test the efficiency
of object property access.
* **ObjectBenchmark**: These serve a similar purpose to PropertyBenchmark and perhaps we should
have deleted these by now.
* **BuiltinBenchmark**: This tries to measure the relative performance of the various ways to create
native JavaScript objects in Java -- the reflection-based method, the IdScriptableObject that is used
for many internal objects, and lambda functions.
* **MathBenchmark**: This is a vehicle for quickly testing low-level math operations.
* **GeneratorBenchmark** and **ThrowBenchmark**: Micro-benchmarks of generators (native, transpiled)
and of exceptions thrown at several stack depths.
* **NumberFormatBenchmark**: Number to string conversions (`ScriptRuntime.toString`, `toFixed`),
called from Java.
* **RegexpAtomicQuantifierBenchmark**: One regular expression with a Unicode property escape on a
5,000-character input.
* **StartupBenchmark**: One `initStandardObjects()` call.
* **ContextThreadLocalBenchmark**: Strategies to keep the current `Context` in a `ThreadLocal`.

## Performance gate

`buildtools/perf-gate.sh [base-ref]` compares the performance of the working tree with an earlier
revision (by default the `perf-baseline` tag, the last commit before the ES2026 work started) and
fails if any metric regressed beyond its tolerance. Run it before committing a change to the
engine. It takes about 18 minutes, and refuses to run during a Time Machine backup
(`PERF_ALLOW_BACKUP=1` overrides): a backup takes a full core and turns every metric into a
"regression".

    buildtools/perf-gate.sh                       # against perf-baseline
    buildtools/perf-gate.sh HEAD                  # uncommitted changes only
    PERF_JAVA_OPTS='-Dperf.only=instanceof' buildtools/perf-gate.sh HEAD   # a subset, in minutes

Environment variables: `PERF_ROUNDS` (rounds, default 3), `PERF_SETTLE` (seconds to wait after the
builds, default 30), `PERF_JAVA_OPTS` (extra JVM options for the harness), `PERF_BASE_REF` (the base
when no argument is given) and `JAVA_HOME` (the JDK that measures; default: `java` on the path).
System properties of the harness, passed through `PERF_JAVA_OPTS`: `perf.only` (a regular expression
on metric names), `perf.warmup` and `perf.iterations` (default 20 and 10), and `perf.tolerance` (one
band for every metric).

How it works:

* The base revision is checked out in a temporary git worktree and only its `rhino` jar is built.
  The harness (`benchmarks/src/perf`, `PerfBenchmark`) and the scripts always come from the
  working tree, so the two sides differ only in the engine.
* 114 metrics (all durations, lower is better), each in interpreted (`int`) and compiled (`cmp`) mode
  and under `VERSION_ES6` (`es6`) and `VERSION_ECMASCRIPT` (`ecma`):
  * `startup.50scopes`: 50 `initStandardObjects()` calls (per language version only);
  * `compile.earley-boyer`: compiling a 195 KB script;
  * `run.<script>`: steady-state execution of each script in `testsrc/perf` (ported from Nashorn's
    perf gate; each one guards a hot path that the ES2015+ work could slow down);
  * `run.v8-<benchmark>`: steady-state execution of the V8 benchmark functions, normalized to one
    call.
* Inside a JVM, each metric is the fastest of 10 runs after 20 warmup runs (noise only ever makes
  a run slower; with only 5 warmup runs, compiled-mode scripts settled in one of two JIT states from
  one JVM to the next, e.g. 18 or 33 ms, which made the gate flaky). The JVMs run with `-Xbatch`, so
  that a JIT compilation blocks the script instead of running in the background: the background
  compilation of a large script method (`arith.js` in compiled mode) took 2 to 3 seconds, the
  script ran ten times slower in the JVM interpreter meanwhile, and in about a quarter of the JVMs
  that outlasted the warmup (108 ms measured instead of 12 ms). With `-Xbatch` the timings are
  steady from the fifth run on. The two sides are measured in alternating rounds (3 by default, one JVM per side
  and round, heap pinned to 1 GB with G1), and each side's result is the median across rounds.
* Tolerances are per metric, in `testsrc/perf-tolerances.properties`, measured rather than chosen:
  `buildtools/perf-calibrate.py` simulates the gate's 3-vs-3 comparison 5,000 times over
  recordings of identical code and takes the 99th percentile of the apparent regression, plus a
  25% margin, rounded up to a multiple of 5% (minimum 10%). A metric without a band gets 15%. A
  metric missing from the working tree's run fails the gate; one the base does not have is reported
  as new.

Scripts read `PERF_SCALE` (0.2 in the interpreter, 0.5 in compiled mode) to size their loops, so a
run takes 5–40 ms in either mode. When adding a script, keep it in that range, and only use
features the base revision supports.

### Sensitivity and calibration

The bands were calibrated on 12 recordings on an Apple M-series MacBook Air (2026-11-21, the first
calibration with `-Xbatch`). 106 of the 114 metrics have the minimum band of 10%; the widest are
`run.instanceof.int` (30% and 40%). Two bands are wider by decision, not by calibration: the accepted
costs of `perf-calibrate.py` (`ACCEPTED`), minimum bands that a recalibration keeps. Since 2026-11-25,
`compile.earley-boyer.int.ecma` has 30% (the compile-time analyses of `VERSION_ECMASCRIPT`, +16% fully
warmed up) and `compile.earley-boyer.cmp.es6` 15% (level once warm, +8–12% after the gate's warmup).
Before `-Xbatch`, compiled-mode metrics settled in different
JIT states from one JVM to the next (`run.wideobject.cmp` at 19 or 60 ms, with a band of 250%) and
only detected cliffs. With the first bands (2026-10-07), a deliberate regression of
`instanceof` was caught at +15–19%; one of +4–8% was not. For a considered judgement on a smaller
difference, run the relevant subset with more rounds on an idle machine:

    PERF_ROUNDS=7 PERF_JAVA_OPTS='-Dperf.only=instanceof -Dperf.tolerance=0.05' buildtools/perf-gate.sh HEAD

With `-Xbatch`, the rounds of one gate agree with each other, so a flag that repeats in every round
is not yet a regression: it can be another JIT state of the same code, stable for a build, which
depends on what ran before the metric in the JVM. On 2026-11-21, `run.forof.cmp.ecma` was flagged at
+13.2% in all three rounds by a change that left the compiled code of that script byte-identical:
+16% after the `startup` benchmark alone, −4% after the five scripts that precede it, level alone
(+1.1% over 7 rounds). Check a flag with the metric alone (`-Dperf.only`, which also changes what
runs before it) and after other predecessors.

The bands are machine-specific. To recalibrate on another machine, record the same engine 12 times
(the script needs at least 6, twice the number of rounds) and regenerate the bands:

    cd benchmarks
    for i in $(seq 1 12); do
      java -Xms1g -Xmx1g -XX:+UseG1GC -Xbatch -cp build/classes/java/perf:../rhino/build/libs/rhino-<version>.jar \
        org.monflabs.mozilla.javascript.perf.PerfBenchmark record build/perf-calib/run-$i.json
    done
    python3 ../buildtools/perf-calibrate.py --write testsrc/perf-tolerances.properties build/perf-calib/run-*.json
