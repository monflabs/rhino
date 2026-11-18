# Code complexity added over stock Rhino

What the ES2026 work added to the engine's source, what kind of complexity it is, and how hard each area is to
follow for someone who maintains it. This is a code review, made on 2026-11-18 at `397cd54b5`; for what the changes
do see [Features on top of stock Rhino](features.md), and for the optimizations
[Performance optimizations](performance.md).

The fork added about 29,000 lines to the engine, a 22% increase. Roughly two thirds of that reads as routine work
for someone who knows Rhino and has the ECMAScript specification open. The remaining third sits in four or five
areas where correctness depends on rules enforced in other files.

The comparison is against upstream `master` at the fork point (`a2fd8fe11`, 2.0.0-SNAPSHOT, October 2026), which is
somewhat later than the Rhino 1.9.1 release.

## The size of the change

| Measure | Before | After |
| --- | --- | --- |
| Engine source lines (`rhino/src/main/java`) | 129,086 | 158,021 |
| test262 in-scope pass rate | 56.9% | 99.2% |
| Interpreter icodes | 63 | 108 |
| Version gates (`isLatestEcmaScript`) | 0 | 451 in 58 files |
| New tokens / node properties | | 22 / 17 |
| New engine source files | | 45 |
| New test files | | 141 |
| Tests that check both sides of a gate (`assertGatedBehavior`) | | 226 uses |

566 engine files show up in the diff, but 230 of them changed only because the package was renamed to
`org.monflabs.mozilla.javascript`. The work spans 246 commits.

## Where the code grew

The fourteen files that grew most, in lines:

| File | At the fork point | Now | Net added |
| --- | ---: | ---: | ---: |
| `Parser` | 5,851 | 8,878 | +3,027 |
| `ScriptRuntime` | 6,380 | 8,147 | +1,767 |
| `Splitter` (new) | | 1,456 | +1,456 |
| `NativeRegExp` | 5,199 | 6,594 | +1,395 |
| `BodyCodegen` | 5,061 | 6,391 | +1,330 |
| `IRFactory` | 2,636 | 3,720 | +1,084 |
| `NodeTransformer` | 605 | 1,640 | +1,035 |
| `NativeArray` | 2,520 | 3,363 | +843 |
| `NativeProxy` | 1,382 | 2,089 | +707 |
| `ModuleRecord` (new) | | 688 | +688 |
| `Optimizer` | 446 | 1,043 | +597 |
| `Interpreter` | 4,511 | 4,949 | +438 |
| `Context` | 3,099 | 3,535 | +436 |
| `CodeGenerator` | 2,084 | 2,316 | +232 |

## Five kinds of complexity

### Spec transcription

The largest share, and easy to follow with the specification open. Modules, Proxy and Reflect, Atomics, iterator
helpers and async functions mostly follow the spec algorithms and name the operation they implement.
`ModuleRecord`, `AsyncFunctionDriver`, `NativeAtomics` and `EventLoop` are clean. Async functions use no threads or
continuations: they compile as generators that a driver resumes from promise reactions.

### Version gating

Temporary: about 2,250 lines exist only to keep the old behavior. Most of the 451 gates are a one-line condition.
The costly form is the whole-method twin: the old body is kept as `legacyXxx` and the public method dispatches on
the language version. That accounts for 765 lines in `NativeProxy`, 455 each in `NativeArray` and `NativeSet`, and
172 for `destructuringArrayLegacy` in the parser. Every method with a twin has to be read twice. All of it goes away
when the language versions are removed (plan milestone M15).

### Everything implemented twice

Inherited from Rhino's two backends. Each language feature exists in both `BodyCodegen` (compiled) and
`CodeGenerator` with `Interpreter`. The central switches were already huge upstream and were lengthened, not
restructured: `generateExpression` is now 1,007 lines and `visitExpression` 827. With no icodes left, the depth of
an outer variable is packed into the high byte of an index.

### Desugaring tricks in the front end

The least obvious code in the fork:

- Scoping is done by renaming variables to `name$body` and `name$block` (`Parser.java`, `separateBodyVars`,
  `hideBlockBindings` and `hideLoopHeadBindings`), with bail-outs when `eval` or `with` is present.
- Modules are re-emitted as `function*` source text and compiled a second time.
- Classes are encoded as object literals, with the class kind stored as a one-letter string prefix.
- The parser gained 19 instance fields, 10 of them boolean, mostly saved and restored by hand in `try`/`finally`.

### Optimizations with rules enforced elsewhere

Where a future edit can break things without any error:

- `PropertyCache` relies on `invalidate()` being called from 8 sites across the slot maps, and on every map marking
  dropped slots as stale.
- Positional variable access (`NativeCall.getVar` and `setVar`) skips the temporal-dead-zone and const checks
  because `NodeTransformer` proved them unnecessary at compile time.
- Number typing in `Optimizer` is a whole-program fixpoint spread over six files. It has already produced silent
  miscompiles (commit `7441cc4e2`).
- `Splitter` and the compile retry ladder exist only because of the JVM's class-file and JIT size limits.

None of these rules is enforced by assertions, only by tests.

## Difficulty by area

1 means trivial to follow; 5 means it needs deep knowledge of the engine and the specification. The ratings are
judgments, not measurements.

**Front end**

| Area | Difficulty | Why |
| --- | :---: | --- |
| Tokens, lexer, Unicode identifiers | 2 | Small, local additions |
| Modules (parser and `ModuleRecord`) | 3 | Spec-shaped and localized; the source rewrite is the surprise |
| Early errors and cover grammar | 3 | Simple one by one, but scattered and driven by parser flags |
| Async parsing | 3 | Lookahead helpers are clear; flags set by callers and reset inside are not |
| Classes and private names | 4 | Spread across four files with encoded conventions |
| Destructuring and iterator close | 4 | Long methods, a legacy twin, and a guard protocol across three files |
| Scope-renaming fixups | 5 | Hidden invariants and silent bail-outs |
| TDZ and registers in `NodeTransformer` | 5 | Semantics mixed with optimization in one 541-line walker |

**Backends and optimizer**

| Area | Difficulty | Why |
| --- | :---: | --- |
| Call linking (`MHJSFunctionCode`, `DefaultLinker`) | 2 | Local and commented |
| Caches (`GlobalNameCache`, `PropertyCache`) | 3 | Small and well explained; the rules they depend on live in other files |
| `Interpreter`, `CodeGenerator`, icodes | 3 | Mechanical mirror of the compiled side; packed operands are the trap |
| `Splitter` and class units | 4 | Readable, but correctness rests on scoping and completion semantics |
| `BodyCodegen` additions | 4 | JVM verifier and stack subtleties inside 1,000-line methods |
| Activation and outer-variable positions | 4 | Depends on guarantees established at compile time elsewhere |
| `Optimizer` number typing | 5 | Whole-program fixpoints across six files, with a history of silent miscompiles |

**Runtime and built-ins**

| Area | Difficulty | Why |
| --- | :---: | --- |
| Date, Set, Object, String | 2 | One-line gates or legacy twins |
| Atomics, SharedArrayBuffer, `EventLoop` | 2 | Deliberately coarse locking, well documented |
| Async function driver | 2 | A generator resumed from promise reactions |
| Proxy and Reflect | 3 | Spec-commented, but each method has a legacy twin |
| `NativeArray` | 3 | Mostly transcription; the fast-path conditions are subtle |
| `ScriptRuntime` | 4 | Many small gates, hidden `Context` state, entry points for both backends |
| Async generators and for-await | 4 | Callback state machine with several flags |
| `NativeRegExp` | 5 | Gates threaded through a backtracking compiler; `v`-flag set algebra |

## The hardest places to read

- **Scope-renaming fixups** in `Parser`. They need the spec's function-instantiation rules and the invariants of
  the AST walk; behavior differs when `eval` or `with` forces a bail-out.
- **Temporal dead zone and registers** in `NodeTransformer` (`classifyTdz`, `checkTemporalDeadZone`,
  `keepVarsInRegisters`). Semantics and a register-allocation optimization share a 541-line tree walker.
- **Number parameters and returns** in `Optimizer` (`optimize`, `recursiveNumberArguments`, `numberReturns`). The
  analysis assumes every value is a number and retracts; soundness depends on a function name being used nowhere
  else.
- **Optimized direct calls**, `BodyCodegen.visitOptimizedCall`. A 261-line method with two paths joined at labels
  with recorded stack heights, to satisfy the JVM verifier.
- **Async generators**, `NativeAsyncGenerator`. A callback state machine with `busy`, `done` and `delegate` flags;
  `yield*` delegation needs the specification alongside.
- **The RegExp compiler**, `NativeRegExp`. 36 gates threaded through a 6,600-line hand-written backtracking
  compiler, plus set algebra for the `v` flag.
- **Primitive receivers** in `ScriptRuntime`. Getters see a primitive `this` through fields saved and restored on
  `Context`, a side channel that is easy to miss.

## Where a future edit is most likely to break something

| Edit | Result | Protection today |
| --- | --- | --- |
| A new slot-map type or mutation path that skips `stale` or `invalidate()` | Stale property reads | `PropertyCacheTest` (8 tests), `GlobalNameCacheTest` (23); no assertion |
| A new node type or a new use of a function name | A number-typed method returns NaN or converts wrongly | `NumberParametersTest`, `NumberReturnsTest`, `NestedDirectCallTest` |
| A new construct that declares a binding at run time | Positional outer variables bypass it | None seen |
| A change to `Splitter` | Wrong completion or scoping in very large functions | The [stress modes](validation.md#stress-modes), run by hand |

## What helps a reader

- Comments are slightly sparser than upstream (14% of added lines against 18%), but they explain why and often
  state the invariant.
- `OPTIMIZATION.md` describes 41 mechanisms, each with what it does, when it applies, why it stays correct and the
  measured gain.
- `BACKWARD_COMPATIBILITY.md` lists 116 behavior changes with before and after examples.
- Legacy branches are labelled "stock Rhino", so they are easy to find and, later, to delete.
- New built-ins use the same `ClassDescriptor.Builder` pattern as upstream, so there is no mix of styles.

## How this was assessed

Size counts come from `git diff` with rename detection and are measured. The qualitative findings come from three
parallel reads of the front end, the backends with the optimizer, and the runtime with the built-ins. Those reads
covered the large files in depth but only skimmed or skipped some smaller ones, among them `JSFunction`,
`Arguments`, `NativeString`, `NativeObject`, `NativePromise` and the iterator helpers.

The review also found two pieces of dead code, since removed: a `useIterator` flag in `Parser.destructuringArray`
that was always true, and a gate tested again after the legacy early return in four `NativeArray` methods.
