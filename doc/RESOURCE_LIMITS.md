# Per-request resource limits for untrusted scripts

Status: opt-in prototype, uncommitted. API: `ResourceLimits`, `ResourceScope`, `ResourceLimitError` in
`org.monflabs.mozilla.javascript`. Verified facts are marked **(verified)**, design assumptions **(assumed)**.

## 1. Conclusion

- A **time limit** is reliable in both backends for script code and for the instrumented built-ins. It is not a hard
  kill: a single unpolled JDK call, or blocking host Java code that ignores interrupts, can overrun it.
- **Memory** has three distinct tiers, and only the last is a guarantee:
  1. *Hard cap*: not possible in-process on HotSpot (no per-thread heap quota). Needs a worker JVM/container with `-Xmx`.
  2. *Conservative accounting limit*: bytes charged before large blocks are allocated (strings, arrays, buffers,
     BigInts). Never refunded by GC, so it can reject early; it does not see small objects.
  3. *Best-effort backstop*: optional cumulative per-thread allocation cap (`ThreadMXBean`), platform threads only.
     Retained <= allocated, so it bounds retention loosely (garbage counts).
- Stack depth is a separate risk: compiled code ends in a `StackOverflowError` that scripts cannot catch and the host
  must.
- Process isolation is **not** equivalent to any of this; use it for adversarial tenants.

## 2. Codebase findings (verified by probes in both modes unless noted)

- Observer: `Context.observeInstructionCount` ([Context.java:2744](../rhino/src/main/java/org/monflabs/mozilla/javascript/Context.java));
  compiled code calls `ScriptRuntime.addInstructionCount` ([ScriptRuntime.java:6112](../rhino/src/main/java/org/monflabs/mozilla/javascript/ScriptRuntime.java))
  only if compiled with `generateObserverCount`; a script cached without it is never counted.
- The counter is not reset when the observer throws, so a re-fire after a swallowed error is natural.
- Exceptions: the interpreter skips JS `catch`/`finally` for an `Error` (unless `FEATURE_ENHANCED_JAVA_ACCESS`);
  compiled `finally` is catch-all. Hence a stackless sticky `Error`, re-raised at every safepoint.
- Gap fixed: the interpreter did not count a call without a branch (exponential recursion ran forever); it now counts
  at frame entry ([Interpreter.java:1143](../rhino/src/main/java/org/monflabs/mozilla/javascript/Interpreter.java), helper at 4917).
- Gap fixed: native loops with no callback were unpolled (`getRawElem` [ArrayLikeAbstractOperations.java:407], `hasElem`
  [NativeArray.java:1969], `setElem` 1084, sort comparator).
- `InterpreterV2` is dead code. The regex engine does poll.
- Still open: small-object memory, compiled call depth (only `StackOverflowError`), unpolled single JDK calls,
  host cache of scripts compiled without the flag.

## 3. Architecture

`ResourceScope` is the request-level controller (deadline, sticky termination, charged bytes, cancel, shared across
threads). `Binding` is its activation on one `Context`: it enables counting, saves and restores the Context state,
and is closed in `finally`. `Context.releaseContext()` closes forgotten bindings. Nested bindings are all checked
and charged (tightest wins). An optional shared daemon watchdog interrupts bound threads at the deadline or on
`cancel` (the interrupt flag is cleared on close so it cannot leak to a pooled thread).

## 4. Change plan (files)

| File | Why | Modes | Overhead | Remaining evasion |
|---|---|---|---|---|
| `ResourceLimits/Scope/LimitError` (new) | API, state | both | none unbound | see section 1 |
| `Context.java` 731, 2744, 3517 | check on observer, cleanup | both | one null test per observer call | none |
| `Interpreter.java` | count at frame entry | interp | one compare per call | none known |
| `ArrayLikeAbstractOperations`, `NativeArray` | poll in native loops, charge growth/sort/join/sparse | both | mask test per element | native loops not listed |
| `NativeString` 1408, 1460; `ConsString` 45, 61 | charge repeat/pad/flatten, string overflow | both | size test | small strings |
| `typedarrays/*Buffer` | charge buffers | both | none | |
| `ScriptRuntime` 4863/4945/5021, `NativeBigInt` 113 (`asIntN` and `asUintN`) | BigInt size cap + charge | both | compare only above 2^16 bits | |
| `module-info.java` | `requires static jdk.management` | n/a | none | |

## 5. API

```java
ResourceLimits limits = ResourceLimits.builder()
        .timeout(Duration.ofSeconds(5)).maxMemoryBytes(64L << 20)
        .maxBigIntBits(1 << 20).interruptBlockingCalls(true).build();
try (ResourceScope scope = limits.newScope()) {
    scope.call(factory, cx -> cx.evaluateString(global, src, "request", 1, null));
} catch (ResourceLimitError e) { /* e.getKind(): TIME, MEMORY, ALLOCATION, CANCELLED */ }
catch (StackOverflowError e) { /* compiled recursion */ }
```
Host code that holds big data for scripts can call `scope.charge(bytes)` / `ResourceScope.chargeCurrent`.

## 6. Verification

- `ResourceLimitsTest` (19 tests, JUnit 5, both backends): loops, `catch`/`finally` swallowing, native loops, sort
  comparator, large allocations (six scripts), BigInt, in-limit work, Context restore, forgotten binding, sticky
  termination, cancel from another thread, interrupt of a blocking host call, concurrency, nesting, allocation
  limit, virtual threads, interpreter call boundaries. All pass.
- Probes against a 256 MB heap found two gaps that are now fixed: sparse `Array(1e8).fill`, and `s = s + s` length
  wrap in the interpreter.
- `./gradlew check`: the first run had 1 failure of 177,429 (test262
  `Atomics/wait/waiterlist-order-of-operations-is-fifo.js`, compiled, a timing test); the rerun passed.
- Benchmark (fib(27) + 3M-iteration loop + 200k sort; ms per round after warmup; machine not idle-verified):

  | Mode | baseline jar | modified, no limits | limits, interval 10000 | limits, interval 1000 |
  |---|---|---|---|---|
  | interpreter | 521-558 | 537-569 | 524-554 | 538-553 |
  | compiled | 231-282 | 233-282 | 243-280 | 291-317 |

- **Not run:** `buildtools/perf-gate.sh` (about 18 minutes, needs an idle machine; required before committing), the
  Atomics.wait interrupt test with real `Atomics.wait`, a heap-exhaustion test under a small `-Xmx` in CI.

## Decision matrix

| | Unmodified Rhino + observer hook | Modified Rhino: timeout + accounting | Worker JVM/process |
|---|---|---|---|
| Time guarantee | script code only; regex yes; native loops no | plus polled built-ins, interrupt of blocking calls | hard kill |
| Memory guarantee | none | conservative budget + loose allocation cap; no hard cap | hard cap (`-Xmx`, cgroup) |
| Stack | SOE (compiled), depth limit (interp) | same | isolated |
| Complexity | low | medium (engine patch to maintain) | high (IPC, host API marshalling) |
| Overhead | low | measured within noise at 10k interval | startup, serialization |
| Operational cost | low | low | high (pool, restart, monitoring) |
| Use for | trusted-ish scripts | many tenants, semi-hostile, low latency | adversarial tenants, strict SLAs |
