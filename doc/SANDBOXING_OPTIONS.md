# Running customer code per request: time and memory limits

> **Superseded by [RESOURCE_LIMITS.md](RESOURCE_LIMITS.md)**, which is based on experiments. Corrections to what
> follows: the regex engine does poll the observer (`longRunningRegExp` test); `InterpreterV2` is dead code; the
> interpreter had real gaps (calls without a branch were never counted); `ThreadMXBean` allocation counters do not
> work on virtual threads.

Goal: run untrusted customer scripts on a Java server, one `Context` per request, with access only to a set of host
APIs, a memory limit per request and a kill after a deadline (for example 5 s).

**Requirement: it must work in compiled mode.** Scripts run through `Codegen` (generated classes), not only the
interpreter.

**Short version:** the instruction observer works in both backends, so a timeout is available today in compiled mode
(`setInstructionObserverThreshold` makes `BodyCodegen` emit `ScriptRuntime.addInstructionCount` calls; the test262
runner already uses it for its per-test limit). It does not cover native built-ins (regex, sort, and so on), blocking
host calls or recursion depth in compiled code, and there is no memory limit in either mode. The engine work is to
close those gaps, not to build the mechanism.

*Correction:* an earlier version of this document said counting was interpreter-only. That was wrong, based on a
failed search.

The findings below were inferred from a code scan (grep and reading `Context`, `Interpreter`, `Codegen`). No
experiments were run, and the docs on virtual threads and the event loop were not read.

## What exists today

- **Instruction observer.** `Context.setInstructionObserverThreshold()` plus `observeInstructionCount()` (overridable
  via `ContextFactory`) is the hook for killing a script. The interpreter counts bytecode instructions. In compiled
  mode, `setGenerateObserverCount(true)` (implied by a non-zero threshold) makes `BodyCodegen` emit
  `ScriptRuntime.addInstructionCount(cx, n)` at jumps, loop targets, `throw`, `return`, no-op statements and the
  function epilogue. The code must be compiled with the flag on; code compiled earlier without it is not counted. The
  meaning of an "instruction" differs between the modes. `ObserveInstructionCountTest` covers both.
- **Stack depth.** `setMaximumInterpreterStackDepth` is interpreter-only. Compiled code uses the Java stack and can hit
  `StackOverflowError`.
- **Other instrumentation.** The `debug` package (`Debugger`, `DebugFrame`, `DebuggableScript`) reports line changes,
  calls and exceptions, but it is interpreter-oriented and too heavy for enforcement.
- **Sandboxing.** The pieces are there: `initSafeStandardObjects` (no `Packages`/`java`), `ClassShutter`,
  `WrapFactory` and `SecurityController`.
- **Memory.** There is no accounting anywhere. Only the JVM's `-Xmx` applies.
- **Gaps in the observer.** No interrupt or observer checks were found in the RegExp engine (`NativeRegExp`), and
  there is no `Thread.interrupted` handling. Single calls into native code, such as regex backtracking, `sort`,
  `repeat`, `join` or `JSON`, are therefore not counted and cannot be stopped.
- **Blocking.** `Atomics.wait` blocks, including on the main thread (plan decision 3.4). It must not be exposed to
  customer code.

## Timeout options

| Option | Pros | Cons |
|---|---|---|
| **T1. Observer that checks `nanoTime()` against a deadline** (no engine change; works in both modes) | Works now. Deterministic. Cheap to build. | Interpreter is slower than compiled code. Does not stop native-heavy built-ins, regex ReDoS, or blocking host calls. Granularity depends on the threshold. |
| **T2. Use the existing compiled-mode counting** (`setInstructionObserverThreshold`, deadline check in `observeInstructionCount`). **Available today; needs hardening.** | Already there and tested. Keeps JIT speed. Unrestricted code pays nothing because the calls are only generated when enabled. | Each counted site is a call into `ScriptRuntime`, so it slows code (not measured here). Scripts must be compiled with counting on, so the script cache must key on it. Needs verification on this fork's newer paths (invokedynamic calls, direct calls to nested functions, generators/async, multi-class split): is every loop and call path counted? Compiled recursion depth is still unbounded except by `StackOverflowError`. |
| **T3. Cooperative checks in native built-ins** (regex backtracking, sort, join, repeat, JSON, and so on) | Closes the biggest remaining hole, ReDoS, a common tenant attack. | Many sites. Must stay off hot paths. |
| **T4. `Thread.interrupt()` or a watchdog thread** | Simple. | `Thread.stop` is dead on Java 21. Interrupt only helps at blocking points. A runaway thread cannot be killed. |
| **T5. Process or container isolation** (a worker JVM per tenant or pool, killed on timeout) | The only true hard kill. Also bounds memory. | Latency, operational complexity, and a serialization boundary for host APIs. |

## Memory options

| Option | Pros | Cons |
|---|---|---|
| **M1. Allocation budget via `com.sun.management.ThreadMXBean`** (thread-allocated bytes, sampled in the observer) | No engine change. | Measures cumulative allocation, not live heap, so it over-counts. Needs platform threads (virtual-thread support needs checking). A single huge allocation (`'x'.repeat(2**29)`, `new ArrayBuffer(1<<30)`) happens before the next sample. |
| **M2. Engine-side caps and accounting** (max string length, array length, ArrayBuffer size, Map/Set size, regexp stack, plus a per-`Context` budget charged at large allocation sites) | Deterministic. Fails before the allocation. Reports a clean error. | Approximate, not byte-exact. Touches many built-ins. Some perf cost. |
| **M3. Process or container with its own `-Xmx`** | Real isolation of memory. | Same costs as T5. |
| **M4. Per-thread heap limits in the JVM** | None. HotSpot has no such feature. | n/a |

## Recommendations

1. **Phase 0, now:** `ContextFactory` that sets the observer threshold, a deadline check in
   `observeInstructionCount` (read the clock, throw a sticky uncatchable error), and a depth limit for compiled code
   (see Phase 1). Tests in both modes. Then verify coverage with adversarial scripts: nested loops, generators and
   async, recursion, `try/finally` loops, regex, big `sort`/`join`/`repeat`.

   **Phase 1, engine work, in this order:**
   1. Fix every coverage gap found in Phase 0 in `BodyCodegen` (any loop or call path that is not counted).
   2. A call-depth counter for compiled code, enabled with the observer flag, throwing a catchable-by-host error.
   3. A `ResourceLimits` API on `Context` (deadline, instruction budget, memory budget) shared by both backends.
   4. M2 caps on strings, arrays and buffers. They are cheap and cover most of the risk.
   5. T3 checks in the RegExp engine and the heaviest built-ins.

   These are additions, not behavior changes, so they need no `VERSION_ECMASCRIPT` gating. They do need unit tests in
   both modes, a CHANGELOG_MONFLABS entry, and `buildtools/perf-gate.sh` (unrestricted code must not regress).
   Also read the docs on blocking host work on virtual threads: a 5 s budget needs host APIs (DB, HTTP) to honor the
   same deadline.

2. **Hardening that needs no engine change (do in parallel):**
   - Use a safe scope with a deny-all `ClassShutter`.
   - Expose host APIs as `ScriptableObject`s or lambda functions, not reflected Java objects, to avoid `getClass()`
     leaks. Remove `Atomics` and `SharedArrayBuffer`.
   - Build one sealed shared scope at startup and give each request a lightweight scope that inherits from it. Cache
     compiled scripts.
   - Add the M1 allocation check from a watchdog thread, with an `-Xmx` safety margin.
   - Make the termination error sticky, so a script's `finally { while(true){} }` cannot escape it. How each backend
     handles non-JS errors in `finally` has not been verified and needs a test.

   A watchdog thread alone cannot stop a compiled script; it must go through the observer.

3. **Phase 3, only if tenants are adversarial enough.** Put the highest-risk tenants in worker processes (T5/M3).
   In-JVM enforcement cannot give a hard guarantee: a tight native loop, or a mistake in an unguarded built-in, still
   ties up a thread.

## Open items

- How Nashorn (the reference implementation for this fork) handles limits has not been checked.
- Cost of the T2 checks in compiled mode (and the interpreter slowdown, for the interim option) has not been measured.
- Whether the checks can live in the existing Codegen loop and call emission paths, and how the multi-class split
  (plan 17.3) and direct calls affect where depth accounting goes, has not been investigated.
- Virtual-thread support in `ThreadMXBean` allocation counters needs verification.
