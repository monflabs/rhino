# Rhino 1.9.1 against the MONFLABS fork: time and memory

Three engines, measured on the same machine with the same harness:

| Name here | What it is | Engine id in the harness |
| --- | --- | --- |
| **Stock** | `org.mozilla:rhino` 1.9.1 from Maven Central, default language version | `RHINO_COMPILED` / `RHINO_INTERPRETED` |
| **Monflabs ES6** | this fork, `Context.VERSION_ES6` (upstream's language level) | `RHINO_MONFLABS_ES6_COMPILED` / `_INTERPRETED` |
| **Monflabs ECMASCRIPT** | this fork, `Context.VERSION_ECMASCRIPT` (ES2026) | `RHINO_MONFLABS_COMPILED` / `_INTERPRETED` |

Measured 2026-10-28/29, fork at `36f03fef4`. A ratio below 1 means the fork is faster, or uses less memory, than
stock.

## Summary

**Time, compiled mode (complete: 3 rounds).** Geometric mean over the 53 benchmarks that run on all three engines:

| Suite | Benchmarks | Monflabs ES6 / stock | Monflabs ECMASCRIPT / stock |
| --- | --- | --- | --- |
| SunSpider 1.0.2 | 26 | ×0.67 | ×0.59 |
| Octane | 11 | ×0.90 | ×0.83 |
| ubench | 10 | ×0.60 | ×0.61 |
| intrinsics (generators, async) | 6 | ×0.64 | ×0.57 |
| **All** | **53** | **×0.69** | **×0.63** |

- The fork is about **1.45× faster** than stock under ES6 and **1.6× faster** under ECMASCRIPT. ECMASCRIPT is about
  9% faster than ES6 on the same engine.
- The gains are broad: numeric loops, bit operations, calls and closures, generators. The largest are 4× to 6×,
  on `access-fannkuch`, `math-spectral-norm`, `bitops-nsieve-bits`, `controlflow-recursive`, `navier-stokes` and
  `pdfjs`.
- **Some benchmarks are slower on the fork:**
  - `access-nbody`: ×1.9–2.1.
  - `box2d`: ×1.3–1.5.
  - `richards`: ×1.24.
  - `gbemu`: ×1.15–1.3.
  - `deltablue`, `raytrace` and `earley-boyer`: ×1.0–1.17.

  These are all property-access-heavy object code. Run alone, `access-nbody` and `gbemu` are level with stock or
  faster: their ratios come from running after the other files in one JVM. The others were already slower in the
  upstream code the fork starts from, which is not 1.9.1 but upstream `master` of 2026-10-05, about 270 commits
  later. A bisection of upstream finds one commit for `box2d`; the analysis, and what the fork changed since, is in
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
geometric mean per suite and the ratio per benchmark.

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

The stock column is wall time in ms; the other two columns are ratios to it.

| Benchmark | Stock ms | ES6 | ECMASCRIPT |
| --- | --- | --- | --- |
| **intrinsics** | | | |
| async_generator_stream | FAILED | FAILED | 197 ms |
| async_promise_combinators | 146 | 0.75 | 0.70 |
| async_request_handler | FAILED | FAILED | 231 ms |
| generator_iterable_collection | 230 | 0.71 | 0.65 |
| generator_nqueens | 361 | 0.46 | 0.45 |
| generator_pipeline | 594 | 0.86 | 0.62 |
| generator_tokenizer | 181 | 0.69 | 0.64 |
| generator_tree_iterator | 383 | 0.45 | 0.43 |
| **Octane** | | | |
| box2d | 12838 | 1.48 | 1.28 |
| crypto | 32877 | 0.70 | 0.68 |
| deltablue | 28069 | 1.14 | 1.17 |
| earley-boyer | 60873 | 1.14 | 0.97 |
| gbemu-part1 | 6389 | 1.28 | 1.15 |
| mandreel | FAILED | FAILED | 38509 ms |
| navier-stokes | 23445 | 0.36 | 0.25 |
| pdfjs | 12947 | 0.30 | 0.28 |
| raytrace | 35061 | 1.15 | 1.08 |
| regexp | 13589 | 1.08 | 1.18 |
| richards | 35625 | 1.24 | 1.24 |
| splay | 3990 | 1.06 | 1.11 |
| **SunSpider** | | | |
| 3d-cube | 364 | 0.64 | 0.52 |
| 3d-morph | 1878 | 0.66 | 0.38 |
| 3d-raytrace | 281 | 0.74 | 0.74 |
| access-binary-trees | 1683 | 1.19 | 1.03 |
| access-fannkuch | 1998 | 0.19 | 0.18 |
| access-nbody | 2953 | 2.12 | 1.94 |
| access-nsieve | 304 | 0.64 | 0.59 |
| bitops-3bit-bits-in-byte | 22575 | 0.26 | 0.26 |
| bitops-bits-in-byte | 2959 | 0.43 | 0.85 |
| bitops-bitwise-and | 659 | 0.61 | 0.59 |
| bitops-nsieve-bits | 1260 | 0.25 | 0.23 |
| controlflow-recursive | 3897 | 0.49 | 0.25 |
| crypto-aes | 636 | 0.67 | 0.49 |
| crypto-md5 | 943 | 0.73 | 0.76 |
| crypto-sha1 | 493 | 0.72 | 0.64 |
| date-format-tofte | 325 | 0.79 | 0.68 |
| date-format-xparb | 400 | 0.92 | 0.93 |
| math-cordic | 2767 | 0.57 | 0.41 |
| math-partial-sums | 2484 | 0.59 | 0.74 |
| math-spectral-norm | 2945 | 0.41 | 0.16 |
| regexp-dna | 288 | 1.05 | 1.02 |
| string-base64 | 993 | 0.96 | 0.75 |
| string-fasta | 155 | 0.88 | 0.85 |
| string-tagcloud | 459 | 1.03 | 0.88 |
| string-unpack-code | 217 | 1.12 | 1.14 |
| string-validate-input | 576 | 0.98 | 0.88 |
| **ubench** | | | |
| function-closure | 1181 | 0.57 | 0.58 |
| function-correct-args | 123 | 0.55 | 0.59 |
| function-excess-args | 122 | 0.57 | 0.60 |
| function-missing-args | 136 | 0.65 | 0.65 |
| interp_hot_closure | 97 | 0.80 | 0.88 |
| interp_hot_property | 87 | 0.83 | 0.78 |
| interp_recursive_fib | 130 | 0.62 | 0.61 |
| loop-empty-resolve | 20 | 0.55 | 0.55 |
| loop-empty | 174 | 0.53 | 0.53 |
| loop-sum | 337 | 0.46 | 0.45 |

Differences under about 10% on one benchmark are within the noise of a 3-round run. Examples are `regexp` going
from ×1.08 to ×1.18, and `splay`. ES6 against ECMASCRIPT on the same engine differ most where the language
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
   of the inherited-property cache stop after warm-up. Still open: `richards` (2–5% behind stock), and the
   property names hashed again in `raytrace`. Rerun the compiled timing.
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
