# Rhino 1.9.1 against the MONFLABS fork: time and memory

Three engines, measured on the same machine with the same harness:

| Name here | What it is | Engine id in the harness |
| --- | --- | --- |
| **Stock** | `org.mozilla:rhino` 1.9.1 from Maven Central, default language version | `RHINO_COMPILED` / `RHINO_INTERPRETED` |
| **Monflabs ES6** | this fork, `Context.VERSION_ES6` (upstream's language level) | `RHINO_MONFLABS_ES6_COMPILED` / `_INTERPRETED` |
| **Monflabs ECMASCRIPT** | this fork, `Context.VERSION_ECMASCRIPT` (ES2026) | `RHINO_MONFLABS_COMPILED` / `_INTERPRETED` |

Compiled time measured 2026-10-30, fork at `efc8516b1`; memory and the partial interpreted run measured
2026-10-28/29, fork at `36f03fef4`. Times are given as speed multipliers, stock time divided by fork time: ×1.40
means the fork runs 1.4 times as fast as stock, and below ×1 it is slower. Memory figures are ratios to stock:
below 1 means the fork uses less memory.

## Summary

**Time, compiled mode (complete: 3 rounds).** Geometric mean over the 53 benchmarks that run on all three engines:

| Suite | Benchmarks | Monflabs ES6 speed | Monflabs ECMASCRIPT speed |
| --- | --- | --- | --- |
| SunSpider 1.0.2 | 26 | ×1.48 | ×1.67 |
| Octane | 11 | ×1.14 | ×1.20 |
| ubench | 10 | ×1.45 | ×1.45 |
| intrinsics (generators, async) | 6 | ×1.54 | ×1.73 |
| **All** | **53** | **×1.40** | **×1.53** |

- The fork is about **1.4× faster** than stock under ES6 and **1.5× faster** under ECMASCRIPT. ECMASCRIPT is ×1.09
  against ES6 on the same engine.
- The run of 2026-10-28/29, at `36f03fef4`, gave ×1.45 and ×1.59. The difference is ubench, whose stock times
  are 70–130 ms and moved by up to 45% between the two runs; Octane is level or better.
- The gains are broad: numeric loops, bit operations, calls and closures, generators. The largest are ×3 to ×6.5,
  on `access-fannkuch`, `math-spectral-norm`, `bitops-nsieve-bits`, `controlflow-recursive`, `navier-stokes` and
  `pdfjs`.
- **Some benchmarks are slower on the fork:**

  | Benchmark | In the run (ES6 / ECMASCRIPT) | Alone (ES6 / ECMASCRIPT) |
  | --- | --- | --- |
  | `deltablue` | ×0.77 / ×0.76 | ×1.05 / ×0.86 |
  | `richards` | ×0.82 / ×0.76 | ×0.96 / ×0.96 |
  | `raytrace` | ×0.79 / ×0.85 | ×0.95 / ×1.10 |
  | `gbemu` | ×0.77 / ×0.70 | ×0.95 / ×1.17 |
  | `access-nbody` | ×0.85 / ×0.73 | |
  | `earley-boyer` | ×0.87 / ×1.01 | |

  These are all property-access-heavy object code. "Alone" is the same harness with `--octane-benchmarks=<file>`
  and one round, so each engine's JVM runs that file only. Most of the gap comes from running after the other
  files in one JVM: stock slows down there too (`richards` 27.3 s alone, 35.2 s in the run), but the fork slows
  down more. `box2d`, ×0.68–0.78 in the previous run, is now faster than stock (×1.31 / ×1.41), and
  `access-nbody` went from ×0.47–0.52 to ×0.73–0.85. The analysis is in
  [Where the fork is slower than Rhino 1.9.1](../rhino-doc/site/performance.md#where-the-fork-is-slower-than-rhino-191).
- **Three benchmarks run under ECMASCRIPT only.** `mandreel` fails its checksum, and `async_generator_stream` and
  `async_request_handler` use syntax beyond ES6. All three fail on stock *and* on Monflabs ES6, so this is a
  language-level limit and not a regression.

**Time, interpreted mode: not measured.** The run was stopped after stock had finished one round, at about 2.5–3
hours per engine per round, before either Monflabs engine had produced a result. Only stock's figures exist, in
`performance-comparison/rhino-time-interpreted-partial.log`. Rerunning it is the first item of
[What remains](#what-remains).

**Memory (complete, one pass: geometric mean over 53 benchmarks; see the caveat on baselines below).**

| Metric | Compiled ES6 | Compiled ECMASCRIPT | Interpreted ES6 | Interpreted ECMASCRIPT |
| --- | --- | --- | --- | --- |
| Allocated by compile | ×0.83 | ×0.96 | ×0.75 | ×0.78 |
| Live heap after compile | ×0.59 | ×0.53 | ×0.61 | ×0.60 |
| Allocated per run | ×0.64 | ×0.60 | ×1.00 | ×0.98 |
| Live heap after the runs | ×0.91 | ×0.81 | ×0.65 | ×0.64 |
| Classes loaded by the benchmark | ×0.55 | ×0.57 | ×1.07 | ×1.02 |

- **Per benchmark, the fork uses less memory than stock almost everywhere.**
  - The compiled script and its realm keep 40–47% less live heap.
  - The fork loads about half as many generated classes.
  - Compiled code allocates about 40% less per run, which partly explains the time gains.
- **The fork's fixed cost is higher.** An empty script costs about 9 MB more allocation, 0.6 MB more live heap,
  2.2 MB more metaspace and 400 more classes. That is the larger ES2026 runtime: more built-ins, `BigInt`,
  `Proxy`, iterator helpers and so on.
  - Under ECMASCRIPT, an empty script's run allocates about 0.45 MB where stock allocates almost nothing. This is
    presumably the extra per-realm setup.
  - The fixed cost matters for an embedder that creates many short-lived contexts.
- **Compiling the large Octane sources under ECMASCRIPT can allocate much more than stock, transiently.** Examples:
  `pdfjs` 322 MB against 106 MB, `box2d` 124 MB against 62 MB, `regexp` 57 MB against 28 MB. The likely cause is
  the splitting of functions too large for the JVM, not yet confirmed. The geometric mean is still ×0.96 and the
  live heap after compile is lower, so this is garbage and not retained memory.

## How the tests are run

Everything runs from [JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance). The changes that
add the two ES6 engines and memory mode are listed in [Harness changes](#harness-changes) below.

### Engines

- Only one `org.mozilla:rhino` fits on the classpath, so the harness pom pins `rhino.version` to **1.9.1**. It was
  1.8.0 before.
- The fork comes from the `org.monflabs.mozilla:rhino` in `~/.m2`. Publish it with
  `./gradlew :rhino:publishToMavenLocal -x test`.
- `MonflabsRhinoExecutor(compiled, ecmascript)` sets `VERSION_ECMASCRIPT` or `VERSION_ES6`, and either
  optimization level 9 (compiled) or the interpreter. Each pair of engines is the same jar.
- The group `--engines=RHINO_VERSIONS` selects all six engines.

### Suites

`--suites=octane,sunspider,ubench,intrinsics`:

| Suite | Count | Notes |
| --- | --- | --- |
| Octane (master) | 12 | Its own score is printed in the log, the harness reports wall time |
| SunSpider 1.0.2 | 26 | |
| ubench | 10 | Microbenchmarks: calls, closures, loops |
| intrinsics | 8 | Generators and async functions; 2 need syntax beyond ES6 |

### Time

```
cd JavascriptPerformance
mvn package -DskipTests
J="java -jar target/javascript-performance-1.0.0-SNAPSHOT-all.jar --suites=octane,sunspider,ubench,intrinsics --rounds=3"
$J --engines=RHINO_COMPILED,RHINO_MONFLABS_ES6_COMPILED,RHINO_MONFLABS_COMPILED --warmup=2 --iterations=3 \
   --report=rhino-time-compiled.csv --html-report=rhino-time-compiled.html
$J --engines=RHINO_INTERPRETED,RHINO_MONFLABS_ES6_INTERPRETED,RHINO_MONFLABS_INTERPRETED --warmup=1 --iterations=1 \
   --report=rhino-time-interpreted.csv --html-report=rhino-time-interpreted.html
```

(In zsh, `$J` is not split into words. Write the commands out in full.)

`IsolatedRunner` runs each engine in a JVM of its own, round after round, and interleaves the engines within a
round. Every JVM gets `-Xms1g -Xmx1g -XX:+UseG1GC`.

- **Within a JVM**, a benchmark is compiled once, run `warmup` times, then timed over `iterations` runs. The
  fastest of those runs is kept.
- **Across rounds**, the median is kept.

These are the rules the Nashorn performance gate uses: interleaving, minimum within a JVM, median across JVMs and
a pinned heap. Each was introduced after it was seen to move results by 15–30%.

- **Compiled run time:** 4h 16m.
- **Interpreted run time:** about 25 hours projected, because stock Octane interpreted takes up to 6 minutes a
  run (`crypto`).

  To keep it affordable, use `--rounds=1` for interpreted, or leave out the four slowest Octane benchmarks.

To summarise a CSV, run `python3 performance-comparison/timesum.py rhino-time-compiled.csv`. It prints the
geometric mean per suite and the speed multiplier per benchmark.

### Memory

```
java -jar target/javascript-performance-1.0.0-SNAPSHOT-all.jar --memory \
     --suites=octane,sunspider,ubench,intrinsics --engines=RHINO_VERSIONS \
     --warmup=1 --iterations=2 --memory-report=rhino-memory.csv
python3 performance-comparison/memsum.py rhino-memory.csv
```

Memory is never measured in the same run as time, because it calls `System.gc()`.

Each engine first runs an empty script, which gives its fixed cost (the row `engine/empty`). Then `MemoryWatch`
takes six figures for every benchmark, in the calling thread. Each is a difference from a baseline taken after a
full GC (`System.gc()` repeated until the heap stops shrinking).

| Column | How it is measured |
| --- | --- |
| compile alloc KB | Bytes this thread allocated during `init`: the realm, the parse and the compile. Counted with `com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes` |
| retained compiled KB | Live heap after `init`, after a full GC |
| alloc per run KB | Bytes allocated by one run, after `warmup` runs. The lowest of `iterations` runs |
| retained after run KB | Live heap after the runs, before the context is closed |
| metaspace KB | Growth of the `Metaspace` memory pool |
| classes | Growth of `ClassLoadingMXBean.getTotalLoadedClassCount` |

The whole memory run took about 5 hours.

## Results in full

The raw data is in [`performance-comparison/`](performance-comparison/):

- `rhino-time-compiled.csv`
- `rhino-time-compiled.html`: the harness's chart report.
- `rhino-memory.csv`
- `rhino-time-interpreted-partial.log`
- the two summary scripts.

### Time, compiled: per benchmark

The stock column is wall time in ms; the other two columns are speed multipliers against it.

| Benchmark | Stock ms | ES6 | ECMASCRIPT |
| --- | --- | --- | --- |
| **intrinsics** | | | |
| async_generator_stream | FAILED | FAILED | 202 ms |
| async_promise_combinators | 171 | ×1.33 | ×1.54 |
| async_request_handler | FAILED | FAILED | 251 ms |
| generator_iterable_collection | 263 | ×1.45 | ×1.61 |
| generator_nqueens | 387 | ×2.20 | ×2.21 |
| generator_pipeline | 647 | ×1.12 | ×1.55 |
| generator_tokenizer | 176 | ×1.30 | ×1.40 |
| generator_tree_iterator | 411 | ×2.19 | ×2.25 |
| **Octane** | | | |
| box2d | 12682 | ×1.31 | ×1.41 |
| crypto | 33062 | ×1.25 | ×1.35 |
| deltablue | 28065 | ×0.77 | ×0.76 |
| earley-boyer | 59634 | ×0.87 | ×1.01 |
| gbemu-part1 | 6230 | ×0.77 | ×0.70 |
| mandreel | FAILED | FAILED | 40829 ms |
| navier-stokes | 24028 | ×3.01 | ×3.97 |
| pdfjs | 13181 | ×3.04 | ×3.56 |
| raytrace | 33384 | ×0.79 | ×0.85 |
| regexp | 13597 | ×0.93 | ×0.85 |
| richards | 35242 | ×0.82 | ×0.76 |
| splay | 3800 | ×0.87 | ×0.95 |
| **SunSpider** | | | |
| 3d-cube | 396 | ×1.67 | ×1.87 |
| 3d-morph | 2224 | ×1.77 | ×3.01 |
| 3d-raytrace | 280 | ×1.25 | ×1.15 |
| access-binary-trees | 1532 | ×0.84 | ×0.95 |
| access-fannkuch | 2124 | ×6.23 | ×6.52 |
| access-nbody | 3021 | ×0.85 | ×0.73 |
| access-nsieve | 319 | ×1.74 | ×1.78 |
| bitops-3bit-bits-in-byte | 20866 | ×3.60 | ×3.39 |
| bitops-bits-in-byte | 3247 | ×1.58 | ×1.32 |
| bitops-bitwise-and | 705 | ×1.74 | ×1.77 |
| bitops-nsieve-bits | 1197 | ×3.90 | ×3.94 |
| controlflow-recursive | 3526 | ×1.80 | ×3.62 |
| crypto-aes | 602 | ×1.33 | ×1.45 |
| crypto-md5 | 949 | ×1.38 | ×1.28 |
| crypto-sha1 | 503 | ×1.38 | ×1.55 |
| date-format-tofte | 296 | ×1.22 | ×1.29 |
| date-format-xparb | 400 | ×1.00 | ×1.03 |
| math-cordic | 2932 | ×1.26 | ×2.47 |
| math-partial-sums | 2286 | ×1.38 | ×1.30 |
| math-spectral-norm | 2923 | ×2.31 | ×5.70 |
| regexp-dna | 302 | ×1.00 | ×0.98 |
| string-base64 | 1042 | ×1.00 | ×1.11 |
| string-fasta | 156 | ×1.13 | ×1.14 |
| string-tagcloud | 503 | ×1.00 | ×1.15 |
| string-unpack-code | 238 | ×0.92 | ×0.96 |
| string-validate-input | 603 | ×1.01 | ×1.16 |
| **ubench** | | | |
| function-closure | 1248 | ×1.84 | ×1.74 |
| function-correct-args | 85 | ×1.13 | ×1.10 |
| function-excess-args | 85 | ×1.16 | ×1.12 |
| function-missing-args | 111 | ×1.25 | ×1.22 |
| interp_hot_closure | 117 | ×1.41 | ×1.41 |
| interp_hot_property | 97 | ×1.39 | ×1.35 |
| interp_recursive_fib | 70 | ×0.81 | ×0.86 |
| loop-empty-resolve | 23 | ×1.92 | ×2.09 |
| loop-empty | 194 | ×2.00 | ×2.00 |
| loop-sum | 349 | ×2.25 | ×2.25 |

Differences under about 10% on one benchmark are within the noise of a 3-round run. Examples are `regexp` going
from ×0.93 to ×0.85, and `splay`. ES6 against ECMASCRIPT on the same engine differ most where the language
version changes the code generated:

- `math-spectral-norm`, `controlflow-recursive` and `3d-morph` are much faster under ECMASCRIPT.
- `bitops-bits-in-byte` and `math-partial-sums` are faster under ES6.

### Memory: the fixed cost of an engine (empty script)

| Engine | Compile alloc KB | Retained KB | Alloc/run KB | Retained after run KB | Metaspace KB | Classes |
| --- | --- | --- | --- | --- | --- | --- |
| Stock compiled | 26,158 | 1,178 | 8 | 1,178 | 5,570 | 1,264 |
| Monflabs ES6 compiled | 35,051 | 1,801 | 76 | 1,772 | 7,743 | 1,642 |
| Monflabs ECMASCRIPT compiled | 35,763 | 1,813 | 477 | 1,882 | 7,980 | 1,704 |
| Stock interpreted | 25,375 | 1,144 | 49 | 1,146 | 5,271 | 1,231 |
| Monflabs ES6 interpreted | 33,973 | 1,765 | 43 | 1,735 | 7,370 | 1,600 |
| Monflabs ECMASCRIPT interpreted | 34,680 | 1,777 | 456 | 1,846 | 7,600 | 1,662 |

The per-benchmark memory figures are in `rhino-memory.csv`, one row per benchmark and engine.

## Caveats

- **Baselines leak in the memory run.** It ran one JVM per *engine*, so each benchmark's baseline includes what the
  previous ones left reachable.
  - The worst case: after the failed `mandreel`, `navier-stokes` on Monflabs ES6 compiled reads −65 MB retained
    and −40 MB metaspace.
  - Smaller negatives, of −0.1 to −0.75 MB, appear elsewhere.

  That is why the summary gives geometric means and not totals, and why the metaspace figures are not summarised.
  The harness now runs one JVM per *(engine, benchmark)*. Each child first loads the engine on an empty script,
  without reporting it, so a benchmark's figures leave out the engine's fixed cost. The change has been
  smoke-tested on ubench, where no figure is negative, but the full suites have not been rerun with it (see
  [What remains](#what-remains)).
- **A ratio depends on the files run before it.** Each engine runs all the files in one JVM per round, so a
  file's profile, code cache and heap are those the previous files left. Object-heavy Octane files read 15–35%
  slower on the fork than alone; see the table in the [Summary](#summary).
- **The machine was not idle.** The measurements ran on an Apple M2 (8 cores, 24 GB) under Azul Zulu 25.0.4.
  Chrome Remote Desktop used about a third of a core throughout. All three engines were exposed to that equally,
  since they were interleaved, but it widens the noise.
- **Octane is timed, not scored.** The harness reports the wall time of a run. The Octane score each benchmark
  prints is in the log, but not used.
- **A failed benchmark is left out of the means.** Only the 53 that run on all three engines count. The ECMASCRIPT
  engine runs 56.

## What remains

To redo this:

1. **Interpreted time.** Run the second time command, preferably with `--rounds=1`, overnight. Budget about 8 hours
   per round for the three engines.
2. **Memory, one JVM per benchmark.** Rerun the memory command, which now starts one JVM per (engine, benchmark).
   Expect about 5 hours. Then check that no figure is negative and that the totals in `memsum.py` add up.
3. **The regressions.** Analysed in [performance.md](../rhino-doc/site/performance.md#where-the-fork-is-slower-than-rhino-191).
   Since this run, slot keys are compared without `Objects.equals` (`deltablue` about 18% faster, `richards`
   6–10%), and the double conversions are linked without a guard that calls the native `Class.isInstance`
   (`box2d` 18.4 s → 9.1 s alone, faster than stock), and objects created by `new` start with a hash table
   (`deltablue` a further 9–12%, now faster than stock; `raytrace` within about 2% of stock). The deoptimizations
   of the inherited-property cache stop after warm-up, and the property names `raytrace` seemed to hash again
   were a JFR attribution (no `String` is allocated while it runs). Still open: `richards`, about 2.5% behind
   stock (median of 6 alternating runs each: 29.19 s, against 28.48 s). JFR puts 11% of it in
   `MHJSFunctionCode.execute`, but calling functions through a generated subclass, as 1.9.1 does, left it
   unchanged (see the "Tried and dropped" table of performance.md); no single cause found. The compiled timing
   was rerun on 2026-10-30. Still open from it:
   - why the fork loses more than stock when the object-heavy files run after the others in one JVM (`deltablue`
     ×1.05 alone, ×0.77 in the run, under ES6);
   - whether `deltablue` under ECMASCRIPT is ×0.86 alone, measured in one round only.
4. **ECMASCRIPT compile allocation.** Confirm what makes `pdfjs`, `box2d` and `regexp` allocate twice as much
   during compile.

## Harness changes

These changes to JavascriptPerformance were made for this comparison.

- **`pom.xml`**: `rhino.version` 1.8.0 → 1.9.1.
- **`ScriptExecutor.ENGINE`**: `RHINO_MONFLABS_ES6_INTERPRETED` and `RHINO_MONFLABS_ES6_COMPILED`.
  - `MonflabsRhinoExecutor(boolean compiled, boolean ecmascript)` creates them.
  - `--engines=RHINO_VERSIONS` selects all six engines.
- **`BenchmarkCollector`**: four more chart colours, indexed modulo the palette. There were 9 colours for 11
  engines before, a latent `ArrayIndexOutOfBoundsException`.
- **Memory mode** (`--memory`, `--memory-report=`, `--only-file=SUITE/FILE`):
  - new classes `MemoryWatch` and `MemoryRunner`;
  - `IsolatedRunner.childCommand`, shared by both runners;
  - `BenchmarkRunner.measureEngineFootprint`.
