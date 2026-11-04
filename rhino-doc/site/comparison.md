# Comparison with Rhino 1.9.1: time and memory

How the fork compares with stock Rhino 1.9.1 on the JavascriptPerformance suites, in time and in memory. For the
optimizations behind these figures see [Performance optimizations](performance.md).

Three engines, measured on the same machine with the same harness:

| Name here | What it is | Engine id in the harness |
| --- | --- | --- |
| **Stock** | `org.mozilla:rhino` 1.9.1 from Maven Central, default language version | `RHINO_COMPILED` / `RHINO_INTERPRETED` |
| **Monflabs ES6** | this fork, `Context.VERSION_ES6` (upstream's language level) | `RHINO_MONFLABS_ES6_COMPILED` / `_INTERPRETED` |
| **Monflabs ECMASCRIPT** | this fork, `Context.VERSION_ECMASCRIPT` (ES2026) | `RHINO_MONFLABS_COMPILED` / `_INTERPRETED` |

Compiled time and memory measured 2026-11-03/04, fork at `5afb843a9`; the partial interpreted run measured
2026-10-28/29, fork at `36f03fef4`, and not rerun. All on one machine and one JVM, see [Test context](#test-context).

Every figure is a percentage of stock. For time, 72% means the fork takes 72% of the time stock takes; above 100%
it is slower. For memory, 83% means the fork uses 83% of what stock uses.

## Summary

**Time, compiled mode (complete: 3 rounds).** Geometric mean over the 53 benchmarks that run on all three engines:

| Suite | Benchmarks | Monflabs ES6 | Monflabs ECMASCRIPT |
| --- | --- | --- | --- |
| SunSpider 1.0.2 | 26 | 69% | 61% |
| Octane | 11 | 85% | 78% |
| ubench | 10 | 61% | 62% |
| intrinsics (generators, async) | 6 | 69% | 65% |
| **All** | **53** | **70%** | **65%** |

- The fork takes about **70%** of stock's time under ES6 and **65%** under ECMASCRIPT. On the same engine,
  ECMASCRIPT takes 93% of the time of ES6.
- The short ubench and intrinsics benchmarks (20–600 ms) move most from one session to the next, on stock as
  much as on the fork; see [Variation between runs](#variation-between-runs).
- The gains are broad: numeric loops, bit operations, calls and closures, generators. The largest, at 16–26% of
  stock's time under ECMASCRIPT, are `math-spectral-norm`, `access-fannkuch`, `controlflow-recursive`,
  `bitops-3bit-bits-in-byte`, `bitops-nsieve-bits`, `navier-stokes` and `pdfjs`.
- **Some benchmarks are slower on the fork:**

  | Benchmark | In the run (ES6 / ECMASCRIPT) | Alone (ES6 / ECMASCRIPT) |
  | --- | --- | --- |
  | `deltablue` | 128% / 131% | 95% / 116% |
  | `richards` | 135% / 137% | 104% / 104% |
  | `gbemu` | 127% / 113% | 105% / 85% |
  | `raytrace` | 119% / 109% | 105% / 91% |
  | `access-nbody` | 104% / 193% | |
  | `regexp` | 106% / 116% | |
  | `access-binary-trees` | 115% / 93% | |
  | `splay` | 110% / 95% | |
  | `string-unpack-code` | 111% / 108% | |
  | `earley-boyer` | 106% / 88% | |
  | `math-partial-sums` | 105% / 111% | |

  Most are property-access-heavy object code. "Alone" is the same harness with `--octane-benchmarks=<file>` and
  one round, so each engine's JVM runs that file only; it was measured at `efc8516b1`, before two later engine
  changes (`instanceof` without calling the intrinsic `@@hasInstance`, and named gets and sets on objects without
  the primitive paths). Most of the gap comes from running after the other files in one JVM: stock slows down
  there too, but the fork slows down more (see [Caveats](#caveats)).
  - `access-nbody` under ECMASCRIPT and `math-partial-sums` have a fast and a slow JIT state from one session to
    the next.

  The analysis is in
  [Where the fork is slower than Rhino 1.9.1](performance.md#where-the-fork-is-slower-than-rhino-191).
- **Three benchmarks run under ECMASCRIPT only.** `mandreel` fails its checksum, and `async_generator_stream` and
  `async_request_handler` use syntax beyond ES6. All three fail on stock *and* on Monflabs ES6, so this is a
  language-level limit and not a regression.

**Time, interpreted mode: not measured.** The run was stopped after stock had finished one round, at about 2.5–3
hours per engine per round, before either Monflabs engine had produced a result. Only stock's figures exist, in
`performance-comparison/rhino-time-interpreted-partial.log`. It was not rerun on 2026-11-03.

**Memory (complete, one pass, one JVM per engine and benchmark: geometric mean over 53 benchmarks).**

| Metric | Compiled ES6 | Compiled ECMASCRIPT | Interpreted ES6 | Interpreted ECMASCRIPT |
| --- | --- | --- | --- | --- |
| Allocated by compile | 100% | 106% | 89% | 93% |
| Live heap after compile | 86% | 85% | 64% | 63% |
| Allocated per run | 59% | 58% | 100% | 98% |
| Live heap after the runs | 99% | 96% | 102% | 102% |
| Metaspace | 104% | 104% | 135% | 138% |
| Classes loaded by the benchmark | 96% | 92% | 193% | 200% |

- **This run is the first without leaking baselines.** The run of 2026-10-28/29 measured every benchmark of an
  engine in one JVM, so a benchmark's baseline included what the previous ones left, and some figures were
  negative. Each benchmark now has a JVM of its own, which first loads the engine on an empty script; no figure is
  negative. The two runs are not comparable: a fresh JVM counts the classes and the code the benchmark is the first
  to use. The earlier figures (53–59% of stock's live heap after compile, 55–57% of its classes) are in
  `rhino-memory-2026-10-29.csv`.
- **Compiled, the fork allocates much less per run (58–59% of stock)**, which partly explains the time gains. The
  live heap after compile is 85–86% of stock's, after the runs level, and the classes 92–96%.
- **Interpreted, a benchmark loads more classes than on stock, but few.** After the empty script, stock's interpreted
  benchmarks load 2 to 253 classes (62 on average), the fork's 16 to 390 (100 under ES6, 111 under ECMASCRIPT):
  about 40–50 more per benchmark, presumably the built-ins the fork loads on first use rather than with the realm
  (not confirmed). The geometric means are high because the counts are small; the metaspace behind them is
  0.1–0.3 MB for most benchmarks.
- **The fork's fixed cost is higher.** An empty script costs 134–137% of stock's allocation, 153–155% of its live
  heap, 140–144% of its metaspace and 130–135% of its classes (about 9 MB, 0.6 MB, 2.2 MB and 400 classes more).
  That is the larger ES2026 runtime: more built-ins, `BigInt`, `Proxy`, iterator helpers and so on.
  - Under ECMASCRIPT, an empty script's run allocates about 0.45 MB where stock allocates almost nothing. This is
    presumably the extra per-realm setup.
  - The fixed cost matters for an embedder that creates many short-lived contexts.
- **Compiling the large Octane sources under ECMASCRIPT allocates much more than stock, transiently.** `pdfjs` 328%
  (326 MB against 99 MB), `regexp` 305% (65 MB against 21 MB), `box2d` 166% (122 MB against 73 MB). Under ES6 they
  are 114%, 171% and below 110%. The likely cause is the splitting of functions too large for the JVM, not yet
  confirmed. The live heap after compile is lower, so this is garbage and not retained memory.
- **`pdfjs` loads twice stock's classes and metaspace** (1,323 classes against 601, 9.8 MB against 4.6 MB), and
  `string-validate-input` keeps 2.3 MB after its runs against 0.9 MB, on both language versions.

## How the tests are run

Everything runs from [JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance). The changes that
add the two ES6 engines and memory mode are listed in [Harness changes](#harness-changes) below.

### Test context

| | |
| --- | --- |
| Machine | Apple M2 (8 cores: 4 performance, 4 efficiency), 24 GB, macOS 27.2 |
| JDK | Azul Zulu 25.36+15-CA, OpenJDK 25.0.4+7-LTS, HotSpot 64-Bit Server VM, tiered C1/C2 |
| JVM options | `-Xms1g -Xmx1g -XX:+UseG1GC` for every engine's JVM, otherwise the defaults |
| Fork | `5afb843a9` (2026-11-03) |
| Stock | `org.mozilla:rhino` 1.9.1 from Maven Central |

**These figures hold for this JVM and this machine.** Most of the fork's gains, and most of its losses, come from
what the JIT does with the code: which runtime helpers C2 inlines into the compiled scripts, which allocations
escape analysis removes. Those decisions follow thresholds compared with the size of the code already compiled
(`InlineSmallCode`), per-method budgets and the order in which methods get hot, and they differ with the JVM vendor
and its compiler (OpenJ9 or the Graal compiler use other heuristics altogether), the JDK version, the GC, the CPU
architecture and the JIT flags. A percentage measured here may be smaller, absent or reversed elsewhere. The
mechanisms are described in
[OPTIMIZATION.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/OPTIMIZATION.md#test-context) and
[OPTIMIZATION_DROPPED.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/OPTIMIZATION_DROPPED.md).

### Engines

- Only one `org.mozilla:rhino` fits on the classpath, so the harness pom pins `rhino.version` to **1.9.1**.
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

- **Compiled run time:** 4h 16m on 2026-10-30, 4h 8m on 2026-11-03.
- **Interpreted run time:** about 25 hours projected, because stock Octane interpreted takes up to 6 minutes a
  run (`crypto`).

  To keep it affordable, use `--rounds=1` for interpreted, or leave out the four slowest Octane benchmarks.

To summarise a CSV, run `python3 performance-comparison/timesum.py rhino-time-compiled.csv` from `rhino-doc/site`.
It prints the geometric mean per suite and each benchmark's time as a percentage of stock.

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

The whole memory run took 5h 6m on 2026-11-03/04, with one JVM per engine and benchmark.

## Results in full

The raw data is in
[`performance-comparison/`](https://github.com/monflabs/rhino/tree/monflabs_enhancements/rhino-doc/site/performance-comparison):

- [`rhino-time-compiled.csv`](performance-comparison/rhino-time-compiled.csv ':ignore'): the run of 2026-11-03.
- [`rhino-time-compiled.html`](performance-comparison/rhino-time-compiled.html ':ignore'): the harness's chart
  report for it.
- [`rhino-time-compiled-2026-10-30.csv`](performance-comparison/rhino-time-compiled-2026-10-30.csv ':ignore'): the
  previous run, fork at `efc8516b1`.
- [`rhino-time-compiled-isc8000.csv`](performance-comparison/rhino-time-compiled-isc8000.csv ':ignore'): the run
  with `-XX:InlineSmallCode=8000` (see [Caveats](#caveats)).
- [`rhino-memory.csv`](performance-comparison/rhino-memory.csv ':ignore'): the memory run of 2026-11-03/04.
- [`rhino-memory-2026-10-29.csv`](performance-comparison/rhino-memory-2026-10-29.csv ':ignore'): the previous one,
  with leaking baselines (see [Caveats](#caveats)).
- [`rhino-time-interpreted-partial.log`](performance-comparison/rhino-time-interpreted-partial.log ':ignore')
- the two summary scripts, `timesum.py` and `memsum.py`.

### Time, compiled: per benchmark

The stock column is wall time in ms; the other two columns are the fork's time as a percentage of it.

| Benchmark | Stock ms | ES6 | ECMASCRIPT |
| --- | --- | --- | --- |
| **intrinsics** | | | |
| async_generator_stream | FAILED | FAILED | 202 ms |
| async_promise_combinators | 144 | 80% | 74% |
| async_request_handler | FAILED | FAILED | 248 ms |
| generator_iterable_collection | 235 | 71% | 71% |
| generator_nqueens | 355 | 48% | 47% |
| generator_pipeline | 574 | 93% | 66% |
| generator_tokenizer | 131 | 93% | 96% |
| generator_tree_iterator | 368 | 46% | 48% |
| **Octane** | | | |
| box2d | 13259 | 68% | 62% |
| crypto | 33061 | 76% | 73% |
| deltablue | 29008 | 128% | 131% |
| earley-boyer | 60544 | 106% | 88% |
| gbemu-part1 | 6258 | 127% | 113% |
| mandreel | FAILED | FAILED | 39053 ms |
| navier-stokes | 22639 | 34% | 26% |
| pdfjs | 13452 | 31% | 26% |
| raytrace | 33730 | 119% | 109% |
| regexp | 13650 | 106% | 116% |
| richards | 37309 | 135% | 137% |
| splay | 4150 | 110% | 95% |
| **SunSpider** | | | |
| 3d-cube | 353 | 67% | 57% |
| 3d-morph | 1649 | 76% | 43% |
| 3d-raytrace | 238 | 80% | 92% |
| access-binary-trees | 1845 | 115% | 93% |
| access-fannkuch | 1882 | 20% | 20% |
| access-nbody | 2952 | 104% | 193% |
| access-nsieve | 293 | 61% | 62% |
| bitops-3bit-bits-in-byte | 23523 | 24% | 24% |
| bitops-bits-in-byte | 3266 | 91% | 88% |
| bitops-bitwise-and | 726 | 57% | 56% |
| bitops-nsieve-bits | 1226 | 26% | 25% |
| controlflow-recursive | 4589 | 43% | 21% |
| crypto-aes | 658 | 50% | 49% |
| crypto-md5 | 961 | 71% | 74% |
| crypto-sha1 | 496 | 76% | 59% |
| date-format-tofte | 280 | 82% | 85% |
| date-format-xparb | 395 | 90% | 96% |
| math-cordic | 2280 | 82% | 50% |
| math-partial-sums | 1645 | 105% | 111% |
| math-spectral-norm | 2941 | 42% | 16% |
| regexp-dna | 299 | 100% | 100% |
| string-base64 | 1018 | 95% | 83% |
| string-fasta | 153 | 92% | 90% |
| string-tagcloud | 463 | 102% | 86% |
| string-unpack-code | 218 | 111% | 108% |
| string-validate-input | 601 | 95% | 83% |
| **ubench** | | | |
| function-closure | 1225 | 56% | 58% |
| function-correct-args | 126 | 55% | 55% |
| function-excess-args | 125 | 55% | 55% |
| function-missing-args | 140 | 65% | 66% |
| interp_hot_closure | 101 | 82% | 83% |
| interp_hot_property | 84 | 83% | 86% |
| interp_recursive_fib | 134 | 63% | 62% |
| loop-empty-resolve | 22 | 55% | 59% |
| loop-empty | 190 | 51% | 53% |
| loop-sum | 306 | 53% | 53% |

ES6 against ECMASCRIPT on the same engine differ most where the language version changes the code generated:

- `math-spectral-norm`, `controlflow-recursive`, `math-cordic` and `3d-morph` are much faster under ECMASCRIPT, in
  both runs.
- `math-partial-sums` and `access-nbody` are faster under ES6, in both runs.
- `bitops-bits-in-byte` is not a language-version difference. It was faster under ES6 on 2026-10-30 (63% against
  76%), and level here (91% against 88%).

### Variation between runs

Stock's code is the same in every run, so its own times show how much a session moves with no code change. The run
of 2026-11-03 against the run of 2026-10-30, stock's time in the new run as a percentage of the old: the geometric
mean of the 53 benchmarks is 99%, but only 32 of them are within 10%. The largest moves:

| Benchmark | 2026-10-30 | 2026-11-03 | New / old |
| --- | --- | --- | --- |
| `interp_recursive_fib` | 70 ms | 134 ms | 191% |
| `function-correct-args` | 85 ms | 126 ms | 148% |
| `function-excess-args` | 85 ms | 125 ms | 147% |
| `controlflow-recursive` | 3,526 ms | 4,589 ms | 130% |
| `math-partial-sums` | 2,286 ms | 1,645 ms | 72% |
| `3d-morph` | 2,224 ms | 1,649 ms | 74% |
| `generator_tokenizer` | 176 ms | 131 ms | 74% |
| `math-cordic` | 2,932 ms | 2,280 ms | 78% |

- **A suite mean is good to a couple of points, one benchmark's percentage is not**, even in a 3-round run. The
  rounds of one run agree with each other: the variation is between sessions, not between the JVMs of a session,
  so the median of 3 rounds does not remove it.
- **It is in both directions, and the fork has it too.** `access-nbody` has a fast and a slow JIT state: alone, 9
  runs in 10 land in the fast one (see [The upstream commit](performance.md#the-upstream-commit)), and `deltablue`
  under ECMASCRIPT has two (see [Caveats](#caveats)).
- **Conclusion.** To call a change on one benchmark, compare two sessions, or run that file alone with
  alternating runs, as the regression analysis does.

### Memory: the fixed cost of an engine (empty script)

| Engine | Compile alloc KB | Retained KB | Alloc/run KB | Retained after run KB | Metaspace KB | Classes |
| --- | --- | --- | --- | --- | --- | --- |
| Stock compiled | 26,209 | 1,178 | 8 | 1,178 | 5,552 | 1,264 |
| Monflabs ES6 compiled | 35,102 | 1,802 | 76 | 1,773 | 7,767 | 1,643 |
| Monflabs ECMASCRIPT compiled | 35,824 | 1,813 | 479 | 1,882 | 7,992 | 1,705 |
| Stock interpreted | 25,423 | 1,144 | 49 | 1,146 | 5,280 | 1,231 |
| Monflabs ES6 interpreted | 34,036 | 1,766 | 43 | 1,736 | 7,375 | 1,601 |
| Monflabs ECMASCRIPT interpreted | 34,754 | 1,777 | 457 | 1,846 | 7,604 | 1,663 |

They are within 0.3% of the run of 2026-10-28/29. The per-benchmark memory figures are in `rhino-memory.csv`, one
row per benchmark and engine.

## Caveats

- **Baselines leaked in the memory run of 2026-10-28/29.** It ran one JVM per *engine*, so each benchmark's
  baseline included what the previous ones left reachable (the worst case: `navier-stokes` on Monflabs ES6 compiled
  read −65 MB retained after the failed `mandreel`). The run of 2026-11-03/04 starts one JVM per *(engine,
  benchmark)*; each child first loads the engine on an empty script, without reporting it, so a benchmark's figures
  leave out the engine's fixed cost. No figure is negative.
- **A percentage depends on the files run before it.** Each engine runs all the files in one JVM per round, so a
  file's profile, code cache and heap are those the previous files left. Object-heavy Octane files read much
  higher in the run than alone, for example `deltablue` 128% against 95% under ES6; see the table in the
  [Summary](#summary).
  - **Cause.** The first file compiles the shared runtime helpers on their own, into code larger than
    `InlineSmallCode` (2,500 bytes). Later files then call those helpers instead of inlining them, using the
    first file's type profiles. Stock is exposed to the same thing, but its hot lookup path is a chain of small
    wrappers that stay inlinable. The fork's per-site property cache (`PropertyCache.getPropAndThis`, about
    2.6 KB compiled) and its other typed links do not: after `crypto`, C2 refused to inline them 522 times, against
    135 when `deltablue` runs alone.
  - **It also depends on compile order within one file.** `deltablue` under ECMASCRIPT takes either about 25 s or
    about 31 s on the same jar, depending on whether `PropertyCache.getPropAndThis` gets compiled on its own at
    2.9 KB or 3.4 KB, either side of `InlineSmallCode` (found 2026-11-03; see
    [OPTIMIZATION_DROPPED.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/OPTIMIZATION_DROPPED.md)).
  - **With `-XX:InlineSmallCode=8000`** (all engines;
    [CSV](performance-comparison/rhino-time-compiled-isc8000.csv ':ignore'), 2026-10-31, same engine and settings
    as the run of 2026-10-30), All goes from 71% / 66% to 71% / 61%:

    | Benchmark | Without the flag (2026-10-30) | With it |
    |---|---|---|
    | deltablue | 131% / 132% | 107% / 104% |
    | richards | 121% / 132% | 116% / 114% |
    | raytrace | 126% / 118% | 122% / 107% |
    | gbemu | 131% / 143% | 134% / 127% |
    | access-nbody | 118% / 138% | 105% / 96% |

    The flag shortens each engine's own times overall: stock's to 97%, the fork's to 97% under ES6 and 90% under
    ECMASCRIPT. Some ES6 files got slower: `crypto-sha1` 364 → 614 ms, `generator_tokenizer` 135 → 210 ms,
    `access-fannkuch` 341 → 424 ms. It is a tuning option for embedders, not a fix. Given the
    [variation between runs](#variation-between-runs), the per-benchmark gains of one session with the flag are
    indicative only.
- **The machine was not always idle.** During the run of 2026-10-30, Chrome Remote Desktop used about a third of a
  core throughout. During the runs of 2026-11-03/04, the Chrome Remote Desktop host and the
  Microsoft Remote Desktop client were running; their CPU use was not measured. All three engines were exposed
  equally, since they were interleaved.
- **Octane is timed, not scored.** The harness reports the wall time of a run. The Octane score each benchmark
  prints is in the log, but not used.
- **A failed benchmark is left out of the means.** Only the 53 that run on all three engines count. The ECMASCRIPT
  engine runs 56.

## What remains

To redo this:

1. **Interpreted time.** Not rerun on 2026-11-03. Run the second time command, preferably with `--rounds=1`,
   overnight. Budget about 8 hours per round for the three engines.
2. **Memory, one JVM per benchmark.** Done on 2026-11-03/04: no figure is negative and the `memsum.py` totals are
   positive.
3. **The regressions.** Analysed in [Where the fork is slower than Rhino 1.9.1](performance.md#where-the-fork-is-slower-than-rhino-191).
   - **Done since the run of 2026-10-30**, and included in the run of 2026-11-03: `instanceof` without calling the
     intrinsic `@@hasInstance` (`earley-boyer` −8% alone under ECMASCRIPT), and named gets and sets on objects
     without the primitive paths (`deltablue` after `crypto` −15% under ES6).
   - **Done since the run of 2026-10-28/29**, and included in all later runs:
     - slot keys are compared without `Objects.equals` (`deltablue` about 18% faster, `richards` 6–10%);
     - the double conversions are linked without a guard that calls the native `Class.isInstance` (`box2d`
       18.4 s → 9.1 s alone, 71% of stock's time);
     - objects created by `new` start with a hash table (`deltablue` a further 9–12%, about 90% of stock's time
       alone; `raytrace` within about 2% of stock).

     Two suspects were ruled out: the deoptimizations of the inherited-property cache stop after warm-up, and the
     property names `raytrace` seemed to hash again were a JFR attribution (no `String` is allocated while it
     runs).
   - **`richards` alone**, at about 102.5% of stock's time (median of 6 alternating runs each: 29.19 s, against
     28.48 s). JFR puts 11% of it in `MHJSFunctionCode.execute`, but calling functions through a generated
     subclass, as 1.9.1 does, left it unchanged (see [Tried and dropped](performance.md#tried-and-dropped)); no
     single cause found.
   - **The shared JVM.** The fork loses more than stock when the object-heavy files run after the others in one
     JVM (`deltablue` 95% alone, 128% in the run, under ES6). The cause is explained in [Caveats](#caveats).
     Several fixes were tried and dropped; all are in the tried-and-dropped list,
     [OPTIMIZATION_DROPPED.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/OPTIMIZATION_DROPPED.md):
     - the miss paths of `PropertyCache` moved out of line (2026-10-30): `crypto` → `deltablue` level, because
       the hit path is still too big to inline after other scripts;
     - the hot helpers forced inline through a compiler directive (2026-10-30): the gain depends on compile order;
     - the cached gets linked per site as a `MethodHandle` chain, as `NativeArrayLinker` does for elements
       (2026-10-31). It helped the shared sequences (`richards` after `box2d` about 20% faster), but cost
       `deltablue` alone 11% and `v8-raytrace` 35% in the gate, because the chain at every get site uses up C2's
       inlining budget in the script methods;
     - a per-script copy of the cache's hit path (2026-11-03): inconclusive, measured under the compilation race
       below;
     - the hit path made smaller (2026-11-03): `deltablue` under ECMASCRIPT 19% faster, but `box2d` 22–27%
       slower, because the cache was then inlined at every site and used up the inlining budgets of its methods.

     A per-site fix has to add no bytecode to the site. Earlier attempts to inline the slot lookup made
     `v8-richards` 60–85% slower in the gate. Shared shapes (smaller lookups) are the remaining candidate.
   - **`deltablue` under ECMASCRIPT at 116% alone** was measured in one round, and is explained by a compilation
     race: the file takes about 25 s or about 31 s on the same jar, depending on the size of the standalone compile
     of `PropertyCache.getPropAndThis` (see [Caveats](#caveats)).
   - **Which in-run figures to trust.** Stock's own times move by up to 91% between sessions with no code change
     (see [Variation between runs](#variation-between-runs)). Before reading a file's in-run figure as a trend,
     measure it alone in two sessions, or record which JIT state each session settles in.
4. **ECMASCRIPT compile allocation.** Confirm what makes `pdfjs` (328%), `regexp` (305%) and `box2d` (166%)
   allocate so much more than stock during compile, against 114%, 171% and under 110% under ES6.

## Harness changes

These changes to JavascriptPerformance were made for this comparison.

- **`pom.xml`**: `rhino.version` set to 1.9.1.
- **`ScriptExecutor.ENGINE`**: `RHINO_MONFLABS_ES6_INTERPRETED` and `RHINO_MONFLABS_ES6_COMPILED`.
  - `MonflabsRhinoExecutor(boolean compiled, boolean ecmascript)` creates them.
  - `--engines=RHINO_VERSIONS` selects all six engines.
- **`BenchmarkCollector`**: four more chart colours, indexed modulo the palette. There were 9 colours for 11
  engines before, a latent `ArrayIndexOutOfBoundsException`.
- **Memory mode** (`--memory`, `--memory-report=`, `--only-file=SUITE/FILE`):
  - new classes `MemoryWatch` and `MemoryRunner`;
  - `IsolatedRunner.childCommand`, shared by both runners;
  - `BenchmarkRunner.measureEngineFootprint`.
