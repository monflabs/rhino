# Comparison with Rhino 1.9.1: time and memory

How the fork compares with stock Rhino 1.9.1 on the JavascriptPerformance suites, in time and in memory. For the
optimizations behind these figures see [Performance optimizations](performance.md).

Three engines, measured on the same machine with the same harness:

| Name here | What it is | Engine id in the harness |
| --- | --- | --- |
| **Stock** | `org.mozilla:rhino` 1.9.1 from Maven Central, default language version | `RHINO_COMPILED` / `RHINO_INTERPRETED` |
| **Monflabs ES6** | this fork, `Context.VERSION_ES6` (upstream's language level) | `RHINO_MONFLABS_ES6_COMPILED` / `_INTERPRETED` |
| **Monflabs ECMASCRIPT** | this fork, `Context.VERSION_ECMASCRIPT` (ES2026) | `RHINO_MONFLABS_COMPILED` / `_INTERPRETED` |

Compiled time measured 2026-10-31, fork at `2ccfaeaa4`; memory and the partial interpreted run measured
2026-10-28/29, fork at `36f03fef4`.

Every figure is a percentage of stock. For time, 72% means the fork takes 72% of the time stock takes; above 100%
it is slower. For memory, 83% means the fork uses 83% of what stock uses.

## Summary

**Time, compiled mode (complete: 3 rounds).** Geometric mean over the 53 benchmarks that run on all three engines:

| Suite | Benchmarks | Monflabs ES6 | Monflabs ECMASCRIPT | 2026-10-30 run (ES6 / ECMASCRIPT) |
| --- | --- | --- | --- | --- |
| SunSpider 1.0.2 | 26 | 69% | 59% | 68% / 60% |
| Octane | 11 | 89% | 82% | 88% / 83% |
| ubench | 10 | 70% | 70% | 69% / 69% |
| intrinsics (generators, async) | 6 | 60% | 56% | 65% / 58% |
| **All** | **53** | **72%** | **65%** | **71% / 66%** |

- The fork takes about **72%** of stock's time under ES6 and **65%** under ECMASCRIPT. On the same engine,
  ECMASCRIPT takes 90% of the time of ES6.
- **This run repeats the previous one on the same engine.** Every commit between `efc8516b1` (the run of
  2026-10-30) and `2ccfaeaa4` changes documentation only, so the two runs measure the variation between sessions,
  not a change. See [Variation between runs](#variation-between-runs).
  - The means agree within 1 point, and each suite within 2, except intrinsics, whose stock times are 140–610 ms.
  - 39 to 42 of the 53 per-benchmark times agree within 10%, depending on the engine. The rest move by up to 55%,
    in both directions and on either engine.
- The run of 2026-10-28/29, at `36f03fef4`, gave 69% and 63%. The difference is ubench, whose stock times are
  70–130 ms and moved by up to 45% between the two runs; Octane is level or better.
- The gains are broad: numeric loops, bit operations, calls and closures, generators. The largest, at 17–31% of
  stock's time under ECMASCRIPT, are `access-fannkuch`, `math-spectral-norm`, `controlflow-recursive`,
  `bitops-nsieve-bits`, `bitops-3bit-bits-in-byte`, `navier-stokes`, `pdfjs` and `generator_nqueens`.
- **Some benchmarks are slower on the fork:**

  | Benchmark | In the run (ES6 / ECMASCRIPT) | 2026-10-30 run | Alone (ES6 / ECMASCRIPT) |
  | --- | --- | --- | --- |
  | `deltablue` | 132% / 133% | 131% / 132% | 95% / 116% |
  | `richards` | 147% / 148% | 121% / 132% | 104% / 104% |
  | `raytrace` | 124% / 115% | 126% / 118% | 105% / 91% |
  | `gbemu` | 132% / 119% | 131% / 143% | 105% / 85% |
  | `access-nbody` | 128% / 183% | 118% / 138% | |
  | `splay` | 128% / 111% | 115% / 105% | |
  | `earley-boyer` | 112% / 96% | 115% / 99% | |

  These are all property-access-heavy object code. "Alone" is the same harness with `--octane-benchmarks=<file>`
  and one round, so each engine's JVM runs that file only; it was measured at `efc8516b1`, the same engine code.
  Most of the gap comes from running after the other files in one JVM: stock slows down there too (`richards`
  27.3 s alone, 35.3 s in the run), but the fork slows down more.
  - `richards`, and `access-nbody` under ECMASCRIPT, are the furthest behind in this run, but with no code change.
    The fork's `richards` took 42.7 s (ES6) and 46.6 s (ECMASCRIPT) on 2026-10-30, and 52.1 s and 51.9 s here. Its
    three rounds agree with each other in both runs (Octane scores 1080–1244 here), so the fork's JIT settles
    differently from one session to the next, not from one JVM to the next.
  - `box2d`, at 128–147% in the run of 2026-10-28/29, is faster than stock (70% / 66%).

  The analysis is in
  [Where the fork is slower than Rhino 1.9.1](performance.md#where-the-fork-is-slower-than-rhino-191).
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
| Allocated by compile | 83% | 96% | 75% | 78% |
| Live heap after compile | 59% | 53% | 61% | 60% |
| Allocated per run | 64% | 60% | 100% | 98% |
| Live heap after the runs | 91% | 81% | 65% | 64% |
| Classes loaded by the benchmark | 55% | 57% | 107% | 102% |

- **Per benchmark, the fork uses less memory than stock almost everywhere.**
  - The compiled script and its realm keep 53–59% of stock's live heap.
  - The fork loads 55–57% of stock's generated classes.
  - Compiled code allocates 60–64% of what stock allocates per run, which partly explains the time gains.
- **The fork's fixed cost is higher.** An empty script costs 134–137% of stock's allocation, 153–154% of its live
  heap, 139–143% of its metaspace and 130–135% of its classes (about 9 MB, 0.6 MB, 2.2 MB and 400 classes more).
  That is the larger ES2026 runtime: more built-ins, `BigInt`, `Proxy`, iterator helpers and so on.
  - Under ECMASCRIPT, an empty script's run allocates about 0.45 MB where stock allocates almost nothing. This is
    presumably the extra per-realm setup.
  - The fixed cost matters for an embedder that creates many short-lived contexts.
- **Compiling the large Octane sources under ECMASCRIPT can allocate much more than stock, transiently.** Examples:
  `pdfjs` 304% (322 MB against 106 MB), `box2d` 200% (124 MB against 62 MB), `regexp` 204% (57 MB against 28 MB).
  The likely cause is the splitting of functions too large for the JVM, not yet confirmed. The geometric mean is
  still 96% and the live heap after compile is lower, so this is garbage and not retained memory.

## How the tests are run

Everything runs from [JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance). The changes that
add the two ES6 engines and memory mode are listed in [Harness changes](#harness-changes) below.

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

- **Compiled run time:** 4h 16m on 2026-10-30, 4h 12m on 2026-10-31.
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

The whole memory run took about 5 hours.

## Results in full

The raw data is in
[`performance-comparison/`](https://github.com/monflabs/rhino/tree/monflabs_enhancements/rhino-doc/site/performance-comparison):

- [`rhino-time-compiled.csv`](performance-comparison/rhino-time-compiled.csv ':ignore'): the run of 2026-10-31.
- [`rhino-time-compiled.html`](performance-comparison/rhino-time-compiled.html ':ignore'): the harness's chart
  report for it.
- [`rhino-time-compiled-2026-10-30.csv`](performance-comparison/rhino-time-compiled-2026-10-30.csv ':ignore'): the
  previous run, on the same engine code.
- [`rhino-time-compiled-isc8000.csv`](performance-comparison/rhino-time-compiled-isc8000.csv ':ignore'): the run
  with `-XX:InlineSmallCode=8000` (see [Caveats](#caveats)).
- [`rhino-memory.csv`](performance-comparison/rhino-memory.csv ':ignore')
- [`rhino-time-interpreted-partial.log`](performance-comparison/rhino-time-interpreted-partial.log ':ignore')
- the two summary scripts, `timesum.py` and `memsum.py`.

### Time, compiled: per benchmark

The stock column is wall time in ms; the other two columns are the fork's time as a percentage of it.

| Benchmark | Stock ms | ES6 | ECMASCRIPT |
| --- | --- | --- | --- |
| **intrinsics** | | | |
| async_generator_stream | FAILED | FAILED | 202 ms |
| async_promise_combinators | 144 | 80% | 75% |
| async_request_handler | FAILED | FAILED | 252 ms |
| generator_iterable_collection | 236 | 70% | 72% |
| generator_nqueens | 532 | 32% | 31% |
| generator_pipeline | 610 | 87% | 63% |
| generator_tokenizer | 173 | 67% | 63% |
| generator_tree_iterator | 370 | 46% | 48% |
| **Octane** | | | |
| box2d | 12963 | 70% | 66% |
| crypto | 32430 | 78% | 75% |
| deltablue | 28879 | 132% | 133% |
| earley-boyer | 61224 | 112% | 96% |
| gbemu-part1 | 6189 | 132% | 119% |
| mandreel | FAILED | FAILED | 38315 ms |
| navier-stokes | 21783 | 36% | 27% |
| pdfjs | 13409 | 30% | 26% |
| raytrace | 34029 | 124% | 115% |
| regexp | 13878 | 106% | 119% |
| richards | 35258 | 147% | 148% |
| splay | 3698 | 128% | 111% |
| **SunSpider** | | | |
| 3d-cube | 346 | 68% | 59% |
| 3d-morph | 2112 | 60% | 34% |
| 3d-raytrace | 228 | 87% | 100% |
| access-binary-trees | 1962 | 111% | 100% |
| access-fannkuch | 1922 | 20% | 18% |
| access-nbody | 2993 | 128% | 183% |
| access-nsieve | 291 | 64% | 62% |
| bitops-3bit-bits-in-byte | 20920 | 27% | 27% |
| bitops-bits-in-byte | 3531 | 91% | 45% |
| bitops-bitwise-and | 708 | 58% | 57% |
| bitops-nsieve-bits | 1181 | 27% | 26% |
| controlflow-recursive | 4578 | 43% | 21% |
| crypto-aes | 649 | 65% | 48% |
| crypto-md5 | 937 | 73% | 76% |
| crypto-sha1 | 482 | 77% | 59% |
| date-format-tofte | 308 | 74% | 75% |
| date-format-xparb | 388 | 98% | 100% |
| math-cordic | 2868 | 66% | 34% |
| math-partial-sums | 2201 | 83% | 96% |
| math-spectral-norm | 2853 | 43% | 17% |
| regexp-dna | 287 | 103% | 107% |
| string-base64 | 994 | 100% | 87% |
| string-fasta | 153 | 91% | 92% |
| string-tagcloud | 458 | 102% | 90% |
| string-unpack-code | 219 | 110% | 109% |
| string-validate-input | 592 | 96% | 87% |
| **ubench** | | | |
| function-closure | 1198 | 58% | 60% |
| function-correct-args | 92 | 75% | 75% |
| function-excess-args | 91 | 76% | 76% |
| function-missing-args | 115 | 80% | 80% |
| interp_hot_closure | 96 | 88% | 89% |
| interp_hot_property | 83 | 86% | 88% |
| interp_recursive_fib | 103 | 83% | 87% |
| loop-empty-resolve | 22 | 59% | 55% |
| loop-empty | 185 | 55% | 53% |
| loop-sum | 311 | 52% | 52% |

ES6 against ECMASCRIPT on the same engine differ most where the language version changes the code generated:

- `math-spectral-norm`, `controlflow-recursive`, `math-cordic` and `3d-morph` are much faster under ECMASCRIPT, in
  both runs.
- `math-partial-sums` and `access-nbody` are faster under ES6, in both runs.
- `bitops-bits-in-byte` is not a language-version difference. It was faster under ES6 on 2026-10-30 (63% against
  76%) and is faster under ECMASCRIPT here (91% against 45%); see below.

### Variation between runs

The runs of 2026-10-30 and 2026-10-31 measure the same engine code, on the same machine, with the same harness
settings. Taking each engine's own times, the new run as a percentage of the old:

| Engine | Geometric mean of the 53 | Benchmarks within 10% |
| --- | --- | --- |
| Stock | 99% | 39 |
| Monflabs ES6 | 100% | 42 |
| Monflabs ECMASCRIPT | 98% | 40 |

So a suite mean is good to about 2 points, but one benchmark's percentage is not, even in a 3-round run. The
largest moves, all without a code change:

| Benchmark | Engine | 2026-10-30 | 2026-10-31 |
| --- | --- | --- | --- |
| `bitops-bits-in-byte` | ES6 / ECMASCRIPT | 2,057 / 2,459 ms | 3,218 / 1,584 ms |
| `richards` | ES6 / ECMASCRIPT | 42.7 / 46.6 s | 52.1 / 51.9 s |
| `access-nbody` | ECMASCRIPT | 4,164 ms | 5,491 ms |
| `crypto-aes` | ECMASCRIPT | 416 ms | 312 ms |
| `gbemu` | ECMASCRIPT | 8,908 ms | 7,375 ms |
| `generator_nqueens` | stock | 387 ms | 532 ms |
| `controlflow-recursive` | stock | 3,526 ms | 4,578 ms |
| `interp_recursive_fib` | stock | 70 ms | 103 ms |

- **The rounds of one run agree with each other.** For example, `richards` on the fork scores 1080–1244 in this
  run's three rounds, and stock 1564–1777. The variation is between sessions, not between the JVMs of a
  session, so the median of 3 rounds does not remove it.
- **It is in both directions and on every engine.** The last three rows are stock moving, not the fork.
  `access-nbody` was already known to have a fast and a slow JIT state: alone, 9 runs in 10 land in the fast one
  (see [The upstream commit](performance.md#the-upstream-commit)).
- **Conclusion.** To call a change on one benchmark, compare two sessions, or run that file alone with
  alternating runs, as the regression analysis does.

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
- **A percentage depends on the files run before it.** Each engine runs all the files in one JVM per round, so a
  file's profile, code cache and heap are those the previous files left. Object-heavy Octane files read much
  higher in the run than alone, for example `deltablue` 132% against 95% under ES6; see the table in the
  [Summary](#summary).
  - **Cause.** The first file compiles the shared runtime helpers on their own, into code larger than
    `InlineSmallCode` (2,500 bytes). Later files then call those helpers instead of inlining them, using the
    first file's type profiles. Stock is exposed to the same thing, but its hot lookup path is a chain of small
    wrappers that stay inlinable. The fork's per-site property cache (`PropertyCache.getPropAndThis`, about
    2.6 KB compiled) and its other typed links do not: after `crypto`, C2 refused to inline them 522 times, against
    135 when `deltablue` runs alone.
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
- **The machine was not always idle.** The measurements ran on an Apple M2 (8 cores, 24 GB) under Azul Zulu
  25.0.4. During the run of 2026-10-30, Chrome Remote Desktop used about a third of a core throughout. During the
  run of 2026-10-31 its host was running but idle (2 s of CPU in all), and the per-benchmark variation was no
  smaller. All three engines were exposed equally, since they were interleaved.
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
3. **The regressions.** Analysed in [Where the fork is slower than Rhino 1.9.1](performance.md#where-the-fork-is-slower-than-rhino-191).
   - **Done since the run of 2026-10-28/29**, and included in both later runs:
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
     JVM (`deltablue` 95% alone, 132% in the run, under ES6). The cause is explained in [Caveats](#caveats).
     Three fixes were tried and dropped; all three are in the "Tried and dropped" table of
     [OPTIMIZATION.md](https://github.com/monflabs/rhino/blob/monflabs_enhancements/OPTIMIZATION.md):
     - the miss paths of `PropertyCache` moved out of line (2026-10-30): `crypto` → `deltablue` level, because
       the hit path is still too big to inline after other scripts;
     - the hot helpers forced inline through a compiler directive (2026-10-30): the gain depends on compile order;
     - the cached gets linked per site as a `MethodHandle` chain, as `NativeArrayLinker` does for elements
       (2026-10-31). It helped the shared sequences (`richards` after `box2d` about 20% faster), but cost
       `deltablue` alone 11% and `v8-raytrace` 35% in the gate, because the chain at every get site uses up C2's
       inlining budget in the script methods.

     A per-site fix has to add no bytecode to the site. Earlier attempts to inline the slot lookup made
     `v8-richards` 60–85% slower in the gate. Shared shapes (smaller lookups) are the remaining candidate.
   - **Whether `deltablue` under ECMASCRIPT is at 116% alone**: measured in one round only.
   - **Which in-run figures to trust.** The run of 2026-10-31 repeated the engine of 2026-10-30 and moved
     `richards` from 121% / 132% to 147% / 148%, and `access-nbody` under ECMASCRIPT from 138% to 183%, with no
     code change (see [Variation between runs](#variation-between-runs)). Before reading them as a trend, measure
     each file alone in two sessions, or record which JIT state each session settles in.
4. **ECMASCRIPT compile allocation.** Confirm what makes `pdfjs`, `box2d` and `regexp` allocate about twice as
   much as stock during compile.

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
