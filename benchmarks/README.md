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

Running all the benchmarks takes about half an hour, so this is a valuable thing to do!

If you want to run benchmarks in one mode (Interpreter or Compiler)
then that can be done using the `EVALMODE` environment variable. For example:
```
EVALMODE=Compiler BENCHMARK=V8 ./gradlew jmh
```
will run all the V8 benchmarks, but only in compiled mode.

## How the Benchmarks work

Java, with its just-in-time compilation pipeline, bytecode generation in Rhino, and
Java's super-complex garbage collector, is sensitive to warm up time and as a result, Rhino has 
more variation between runs than other JavaScript engines. To get a repeatable result, we use
the JMH framework, which runs each benchmark many times and does other "black hole" protection
operations to try and get an accurate result. For this reason, these benchmarks take a lot longer
to run than your favorite on-line JavaScript benchmarking web site.

The purpose of these benchmarks has historically been to make Rhino perform better in server-side
environments, so they all run at the maximum optimization level (9). Since so many people also
use Rhino in interpreted mode, if there's an interest in benchmarking that too then we can
always adjust these tests.

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

How it works:

* The base revision is checked out in a temporary git worktree and only its `rhino` jar is built.
  The harness (`benchmarks/src/perf`, `PerfBenchmark`) and the scripts always come from the
  working tree, so the two sides differ only in the engine.
* Metrics (all durations, lower is better), each in interpreted (`int`) and compiled (`cmp`) mode
  and under `VERSION_ES6` (`es6`) and `VERSION_ECMASCRIPT` (`ecma`):
  * `startup.50scopes`: 50 `initStandardObjects()` calls;
  * `compile.earley-boyer`: compiling a 195 KB script;
  * `run.<script>`: steady-state execution of each script in `testsrc/perf` (ported from Nashorn's
    perf gate; each one guards a hot path that the ES2015+ work could slow down);
  * `run.v8-<benchmark>`: steady-state execution of the V8 benchmark functions, normalized to one
    call.
* Inside a JVM, each metric is the fastest of 10 runs after 20 warmup runs (noise only ever makes
  a run slower; with only 5 warmup runs, compiled-mode scripts settled in one of two JIT states from
  one JVM to the next, e.g. 18 or 33 ms, which made the gate flaky). The two sides are measured in alternating rounds (3 by default, one JVM per side
  and round, heap pinned to 1 GB with G1), and each side's result is the median across rounds.
* Tolerances are per metric, in `testsrc/perf-tolerances.properties`, measured rather than chosen:
  `buildtools/perf-calibrate.py` simulates the gate's 3-vs-3 comparison 5,000 times over
  recordings of identical code and takes the 99th percentile of the apparent regression, plus a
  25% margin (minimum 10%). A metric missing from the working tree's run fails the gate.

Scripts read `PERF_SCALE` (0.2 in the interpreter, 0.5 in compiled mode) to size their loops, so a
run takes 5–40 ms in either mode. When adding a script, keep it in that range, and only use
features the base revision supports.

### Sensitivity and calibration

The bands were calibrated on 12 recordings on an Apple M-series MacBook Air (2026-10-07). Most
metrics have a 10–20% band; a few are much noisier because the JIT settles in different states
from one JVM to the next (`run.wideobject.cmp` is bimodal, 19 ms or 60 ms; `run.properties.cmp`
depends on what ran before it), and they only detect cliffs. A deliberate regression of
`instanceof` was caught at +15–19%; one of +4–8% was not. For a considered judgement on a smaller
difference, run the relevant subset with more rounds on an idle machine:

    PERF_ROUNDS=7 PERF_JAVA_OPTS='-Dperf.only=instanceof -Dperf.tolerance=0.05' buildtools/perf-gate.sh HEAD

The bands are machine-specific. To recalibrate on another machine, record the same engine at least
12 times and regenerate the bands:

    cd benchmarks
    for i in $(seq 1 12); do
      java -Xms1g -Xmx1g -XX:+UseG1GC -cp build/classes/java/perf:../rhino/build/libs/rhino-<version>.jar \
        org.monflabs.mozilla.javascript.perf.PerfBenchmark record build/perf-calib/run-$i.json
    done
    python3 ../buildtools/perf-calibrate.py --write testsrc/perf-tolerances.properties build/perf-calib/run-*.json
