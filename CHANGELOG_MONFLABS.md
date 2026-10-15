# MONFLABS changelog

Changes made in the `org.monflabs.mozilla` fork of Rhino, newest first. Each entry says what changed, why, which
test262 slice moved, which behavior is gated by `Context.VERSION_ECMASCRIPT`, and the performance impact when
relevant. The plan these changes follow is in [MONFLABS_PLAN.md](MONFLABS_PLAN.md).

## Unreleased

### Proxy and String object internal methods (2026-10-15, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.78): `Object.prototype.toString` checks IsArray before
  reading `@@toStringTag`; `Object.defineProperties` goes through [[OwnPropertyKeys]] and [[GetOwnProperty]];
  `Reflect.ownKeys` keeps the key order of a proxy; a String object rejects an incompatible definition of one of its
  indexes.
- Javadoc of `Context.VERSION_ECMASCRIPT`: added the missing entries for 1.74–1.77.
- Tests: `ConformanceResidualsTest.toStringReadsIsArrayBeforeTheTag`, `definePropertiesUsesOwnPropertyKeys`,
  `reflectOwnKeysKeepsProxyOrder`, `stringIndexesCannotBeRedefined`.
- test262: 42,202 → 42,206 of 42,616.

### Destructuring target order (2026-10-15, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.77): an object pattern evaluates the reference of a
  property, element or private field target (object and key expression, in temps) before reading the source
  property, and converts a computed key of the pattern right away.
- Tests: `ConformanceResidualsTest.destructuringTargetReferenceBeforeGet`.
- test262: 42,198 → 42,202 of 42,616.

### push and unshift with prototype indexes (2026-10-15, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.76): the dense fast path of `push` and `unshift` is taken
  only when no prototype has a property at the indexes written (a lookup per pushed element; for `unshift`, the
  integer keys of the prototypes that are not empty arrays).
- Tests: `ConformanceResidualsTest.pushAndUnshiftHonorPrototypeIndexes`.
- test262: 42,194 → 42,198 of 42,616.

### Loop head temporal dead zone (2026-10-15, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.75): a closure in the head expression of
  `for (let/const ... in/of ...)` that reads a loop binding sees it in its temporal dead zone: the parser renames
  those references to bindings of a block around the loop that are never initialized (only for that shape).
- Tests: `ConformanceResidualsTest.loopHeadClosuresSeeTheTemporalDeadZone`.
- test262: 42,188 → 42,194 of 42,616.

### Switch discriminant and catch parameter scopes (2026-10-15, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.74): the discriminant of a `switch` and the parameter of
  a `catch` do not see the `let`/`const` declarations of the block (the parser renames those they name, as for the
  parameters of functions).
- Tests: `ConformanceResidualsTest.switchDiscriminantAndCatchParameterSeeOutside`.
- The ReferenceError and TypeError messages name a renamed binding as written.
- test262: 42,185 → 42,188 of 42,616.

### Generator return inside a destructuring pattern (2026-10-15, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.73): the `finally` that closes the iterators of a
  destructuring statement reads the completion in the local of its `try` (the throwable, in both backends; only
  generators can return there, so compiled non-generator code passes null): a generator's return closes them as a
  return completion, whose errors are thrown.
- Tests: `ConformanceResidualsTest.generatorReturnClosesDestructuringIterators`.
- test262: 42,168 → 42,185 of 42,616 (99.0%).

### Parameters and the body's lexical declarations, ArrayBuffer allocation order (2026-10-14, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.68): the parameters do not see the `let`/`const`
  declarations of the body (the parser renames those the parameters name, as for the `var` declarations).
- Fixed (`VERSION_ECMASCRIPT`, 1.72): `new ArrayBuffer(length)` reads the prototype of newTarget before allocating.
- Tests: `ConformanceResidualsTest.parametersDoNotSeeBodyLexicals`, `arrayBufferPrototypeBeforeAllocation`.
- test262: 42,164 → 42,168 of 42,616.

### %ThrowTypeError% on Function.prototype (2026-10-14, plan M13)

- Changed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.71, decision 2026-10-14): `Function.prototype.caller`
  and `arguments` are accessors of %ThrowTypeError% (AddRestrictedFunctionProperties); sloppy functions get own
  `arguments` (the legacy arguments of the running call) and `caller` (undefined, as the caller is not tracked)
  properties.
- Tests: `ConformanceResidualsTest.restrictedFunctionProperties`.
- test262: 42,157 → 42,164 of 42,616.

### Object literal accessors, class heritage strictness, AsyncIterator this (2026-10-14, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.70): a data property of an object literal with
  accessors is defined, so it replaces an accessor of the same name.
- Fixed: a function in the heritage of a class is strict code even when the script is sloppy (a function parsed in
  strict code is marked strict); `%AsyncIteratorPrototype%[@@asyncIterator]` returns a primitive `this` unchanged.
- Tests: `ConformanceResidualsTest.dataPropertyAfterAccessorInLiteral`, `classHeritageIsStrict`.
- test262: 42,154 → 42,157 of 42,616.

### Primitive this of strict methods and built-ins (2026-10-14, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.69): a method call on a primitive hands the
  primitive as `this` to a strict function, or to a built-in that takes it (the lookup wraps the method in a call
  that binds the primitive; no backend change): `Object.prototype.toLocaleString`, the String matcher methods,
  `isWellFormed`/`toWellFormed`, RegExp `@@matchAll`/`@@split`, `Date.prototype[@@toPrimitive]`. `flatMap` passes
  its `thisArg` unchanged, and `ScriptRuntime.getV` (GetV with a primitive receiver) serves `JSON.stringify` (the
  `toJSON` of a BigInt) and `Iterator.from` (`@@iterator` of a string).
- `ClassDescriptor.Builder.withPrimitiveThis(boolean)` opts methods in and out.
- Tests: `ConformanceResidualsTest.primitiveThis`.
- test262: 42,138 → 42,154 of 42,616.

### Parameter closures and the body's var declarations (2026-10-14, plan M13)

- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.68): with parameter expressions, the `var` bindings of a
  function body that a closure of the parameters names are renamed by the parser (a separate var environment,
  FunctionDeclarationInstantiation step 28), so the closure resolves the name outside the function; nothing changes
  at run time or in the backends. Skipped when the body uses a direct `eval` or `with`.
- Tests: `ConformanceResidualsTest.parameterClosuresDoNotSeeBodyVars`.
- test262: 42,118 → 42,138 of 42,616 (98.9%).

### Functions without arity, yield in arrow parameters (2026-10-14, plan M13)

- Changed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.66, decision 2026-10-14): functions have no `arity`
  property (JavaScript 1.2, a copy of `length`), as in ECMAScript; `VERSION_ES6` keeps it.
- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.67): a `yield` expression in the parameters of an arrow
  function is an early error.
- Tests: `ConformanceResidualsTest.noArity`, `yieldInArrowParameters`.
- test262: 42,112 → 42,118 of 42,616.

### Long tail: class early errors, decodeURI, Math.log2, final sigma, Proxy.revocable (2026-10-14, plan M13)

- Fixed (classes, a `VERSION_ECMASCRIPT` feature): a generator or async generator `constructor` and a static
  generator `prototype` method are early errors, and so is an escaped `static` used as a modifier.
- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.65): `decodeURI` decodes U+FFFE/U+FFFF to themselves;
  `Math.log2` is exact for powers of two; lower casing applies Final_Sigma over code points (the JDK misses a
  supplementary case-ignorable before the sigma); the revoke function of `Proxy.revocable` has no `prototype`.
- Tests: `ConformanceResidualsTest`.

### JSON source text access, Array.fromAsync (2026-10-14, plan 12.6-12.8)

- New (`VERSION_ECMASCRIPT`): a `JSON.parse` reviver gets a third argument, a context object whose `source` is the
  source text of a primitive value still holding the value parsed from it (12.6); `JSON.rawJSON(text)` (a frozen,
  null-prototype object for the text of a JSON primitive, emitted verbatim by `JSON.stringify`) and
  `JSON.isRawJSON`. The JSON parser records the source of the values only when a reviver is given.
- New (`VERSION_ECMASCRIPT`): `Array.fromAsync(items, mapfn, thisArg)` (12.7), over an async iterable, a sync
  iterable (through an Async-from-Sync iterator) or an array-like, awaiting each value and each result of `mapfn`;
  each step runs in the reaction of the awaited promise. An error of `mapfn` or of a definition closes an iterator
  (its `return` result is awaited first). An array-like longer than 2^32 - 1 with a `this` that is not a
  constructor is a RangeError.
- `Date.parse` (12.8) already passed test262.
- Tests: `JsonSourceTextTest`, `ArrayFromAsyncTest`.
- test262: 41,981 → 42,097 of 42,616 (98.5% → 98.8%); `json-parse-with-source` and `Array.fromAsync` 100%.

### Math.sumPrecise, Map and WeakMap upsert, Iterator.concat, Uint8Array base64 (2026-10-14, plan 12.1-12.5)

- `Error.isError` (12.1) already passed (stock Rhino has it).
- New (`VERSION_ECMASCRIPT`): `Math.sumPrecise(items)` (12.2), the exactly rounded sum of an iterable of Numbers
  (no coercion: anything else is a TypeError, and the iterator is closed). The sum is Shewchuk's algorithm with the
  overflow handling of the proposal's polyfill (non-overlapping partials, a count of 2^1024), measured against an
  exact `BigDecimal` sum (Nashorn's choice): equal on 300,000 random and edge cases, 18 times faster on usual values
  (8 ms against 145 ms for a million values) and 2.7 times faster on values spread over the whole exponent range.
- New (`VERSION_ECMASCRIPT`): `Map.prototype.getOrInsert`/`getOrInsertComputed` and the `WeakMap` ones (12.3); a
  key the callback sets meanwhile has its value replaced. The test262 runner no longer skips `upsert`.
- New: `Iterator.concat(...items)` (12.4), an Iterator Helper over each iterable in turn (each argument must be an
  object with a `@@iterator` method; its iterator is opened when the concatenation reaches it; `return()` closes the
  current one).
- New (`VERSION_ECMASCRIPT`): `Uint8Array.fromBase64`/`fromHex` and `Uint8Array.prototype.toBase64`/`toHex`/
  `setFromBase64`/`setFromHex` (12.5), with the `alphabet` (`base64`, `base64url`), `lastChunkHandling` (`loose`,
  `strict`, `stop-before-partial`) and `omitPadding` options; the `setFrom` methods write the bytes decoded before an
  invalid character, then throw, and return `{read, written}`. Encoding uses `java.util.Base64` and `HexFormat`.
- Noticed: stock Rhino prints the subnormal `2 * 5e-324` as `9.9e-324` instead of the shortest `1e-323` (M13).
- Tests: `MathSumPreciseTest`, `UpsertTest`, `IteratorConcatTest`, `Uint8ArrayBase64Test`.
- test262: 41,810 → 41,981 of 42,616 (98.1% → 98.5%); `Math.sumPrecise`, `upsert`, `iterator-sequencing` and
  `uint8array-base64` 100% (the immutable `ArrayBuffer` tests of `setFromBase64`/`setFromHex` are a later proposal).

### Import attributes and JSON modules (2026-10-14, plan 11.6)

- New (modules and `import()`, which are `VERSION_ECMASCRIPT` features): the `with { key: "value", ... }` clause
  of `import` and `export ... from` (a key repeated is an early SyntaxError), and the `with` option of `import()`
  (its own enumerable string keys, string values, else a rejected TypeError). A module request is the specifier
  with its attributes, so the same specifier imported as JavaScript and as JSON gives two modules.
- `type: "json"` loads a JSON module (decision 2026-10-14, as in Nashorn the engine builds it): the loaders return
  the text as before, the engine parses it as JSON into a module whose only export, `default`, is the value, shared
  by every JSON import of that module in the realm; invalid JSON is a SyntaxError. A loader that returns a module of
  Java values gives it as is. The `ModuleLoader` API is unchanged.
- Another attribute key is a SyntaxError when the module is loaded (a TypeError for `import()`), another `type` a
  TypeError.
- The test262 runner no longer skips `import-attributes` and `json-modules`; it skips the `import-text` and
  `import-bytes` proposals (after ES2026), whose tests it skipped before through `import-attributes`.
- Tests: `ModuleTest.jsonModules`.
- test262: 41,721 → 41,810 of 42,616 (97.9% → 98.1%); `import-attributes` and `json-modules` 100%.

### RegExp pattern modifiers (2026-10-14, plan 11.4)

- New (`VERSION_ECMASCRIPT`): modifiers groups `(?ims-ims:...)`, which set or clear the `i`, `m` and `s` flags for
  their disjunction, with the early errors (repeated or unknown flags, `(?-:`). The flags are recorded on the parse
  tree and selected at emit time where the engine decides at compile time (case-insensitive literals, the case
  folding of each class); a `SETFLAGS` opcode at the entry and exit of the group sets a flags register the matcher
  reads for the rest (back references, `.`, `^`/`$`, `\b` under `iu`), saved in the backtrack frames so that
  backtracking out of the group restores the outer flags. A static `RegExp.multiline` still makes `^` and `$`
  multiline inside `(?-m:...)`.
- Duplicate named groups (11.4) already passed.
- Tests: `RegExpModifiersTest` (gated syntax, each flag added and removed, classes, back references, nesting,
  lookbehind, quantified groups, backtracking, early errors).
- test262 (with 11.3): 41,631 → 41,721 of 42,616 (97.7% → 97.9%); `regexp-modifiers` 69.6% → 100%,
  `RegExp.escape` 0% → 100%.

### RegExp.escape (2026-10-14, plan 11.3)

- New (`VERSION_ECMASCRIPT`): `RegExp.escape(string)` (EncodeForRegExpEscape: syntax characters and `/` with a
  backslash, control escapes, the other punctuators, white space and lone surrogates as `\x`/`\u` escapes, and a
  leading ASCII letter or digit as `\x`); a TypeError for a non-string.
- Tests: `RegExpEscapeTest`.

### Iterator helpers (2026-10-14, plan 11.1)

- New (`VERSION_ECMASCRIPT`): the ES2025 `Iterator` global, an abstract constructor (a subclass constructs, `new
  Iterator()` and `Iterator()` throw), `Iterator.from` (with `%WrapForValidIteratorPrototype%`), and the helpers of
  `Iterator.prototype`: lazy `map`, `filter`, `take`, `drop`, `flatMap` (Iterator Helper objects, with the generator
  states: a helper resumed from its own callback is a TypeError, `return()` closes the underlying iterators) and
  eager `reduce`, `toArray`, `forEach`, `some`, `every`, `find`, with the `constructor` and `@@toStringTag`
  accessors (SetterThatIgnoresPrototypeProperties). Iterators are closed as the spec says (invalid callback or
  limit, a callback that throws, `some`/`every`/`find` stopping early, `take` reaching its limit).
- Changed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.64): the `Iterator` function of JavaScript 1.7 is
  replaced; the prototypes of the built-in iterators (including the Java iterable and map iterators) and of
  generators inherit `%Iterator.prototype%` and its `@@iterator` instead of having their own, so every iterator
  gets the helpers (`Iterator.from(javaList).map(...)` works).
- Built-ins can opt in to a primitive `this` from `call`/`apply` (`ClassDescriptor.Builder.withPrimitiveThis`);
  the iterator built-ins do, the other built-ins still get the wrapper object (M13).
- `ClassDescriptor.populate` fills an intrinsic prototype without defining a global property.
- Tests: `IteratorHelpersTest` (the gated global, the prototype chains, lazy and eager helpers, iterator closing,
  `Iterator.from`, subclassing, reentrancy, the accessors, primitive `this`).
- test262: 41,283 → 41,631 of 42,616 (96.9% → 97.7%); `iterator-helpers` 12.5% → 99.7% (`from/iterable-primitives`
  needs a getter to see a primitive receiver, M13).

### Atomics.waitAsync and host job schedulers (2026-10-13, plan 10.3)

- New (`VERSION_ECMASCRIPT`, like `Atomics`): `Atomics.waitAsync`. The result is `{async: false, value:
  "not-equal" | "timed-out"}` when the wait would not block, otherwise `{async: true, value: promise}`; the promise
  settles with `"ok"` in a job posted by the `Atomics.notify` (from any agent), or `"timed-out"` in a job scheduled
  for the timeout. Notified async waiters count in the result of `notify`.
- New API: `HostJobScheduler` (jobs posted from another thread, jobs after a delay), set with
  `Context.setHostJobScheduler`. A server implements it over its own executor or event loop. `EventLoop` is the
  default implementation: `run(cx)` runs the jobs on the Context's thread until none is posted, expected or
  scheduled (no timer thread; jobs falling due while microtasks run, as with a script polling through promise
  reactions, run between them). Without a scheduler, `waitAsync` throws a TypeError when it would have to wait.
- Embeddings are unaffected: there is no scheduler unless the host sets one.
- The test262 runner and its `$262.agent` agents run an `EventLoop` after the script (async tests).
- Tests: `AtomicsWaitAsyncTest` (same-agent and cross-thread notify, timeouts, a timeout while microtasks poll, a
  host scheduler).
- test262: 41,182 → 41,283 of 42,616 (96.6% → 96.9%); `Atomics.waitAsync` 0% → 100%.

### RegExp v flag (2026-10-13, plan 10.2)

- New (`VERSION_ECMASCRIPT`): the RegExp `v` flag (`unicodeSets`), in literals and for the constructor, with
  `RegExp.prototype.unicodeSets` and `v` in `flags`. Classes take the ES2024 class-set grammar: nested classes,
  intersection `&&`, difference `--`, string disjunctions `\q{a|bc}`, and the `v`-mode syntax restrictions
  (reserved double punctuators, syntax characters to escape). A class is a set expression evaluated per code point;
  under `i` each operand is case folded before the set operations, so a complement differs from `u` mode
  (`/[^\P{Lowercase}]/vi` matches `"A"`). A class with strings matches them longest first, then its code points.
  `u` and `v` together are a SyntaxError.
- Limit: the properties of strings (`\p{RGI_Emoji}`, `Basic_Emoji`, `Emoji_Keycap_Sequence`, ...) are a
  SyntaxError, as in Nashorn: the JDK carries no emoji sequence data and Unicode data comes from the JDK only
  (decision 4.3).
- Fixed (`VERSION_ECMASCRIPT`, `BACKWARD_COMPATIBILITY.md` 1.63): in a lookbehind, every alternative is matched
  backwards, and alternatives starting with a literal are no longer preselected on their first character; with
  `u`, a class in an alternative matches a supplementary character (the alternation no longer preselects on one
  UTF-16 code unit).
- Tests: `RegExpUnicodeSetsTest`.
- test262: 41,080 → 41,182 of 42,616 (96.4% → 96.6%); `regexp-v-flag` 27% → 75% (the rest needs emoji sequence
  data), `built-ins/RegExp/CharacterClassEscapes` passes (the `u` alternation fix).

### Language residuals, Annex B, generators and realms only under VERSION_ECMASCRIPT (2026-10-13, plan G7)

- `BACKWARD_COMPATIBILITY.md` 2.35-2.46 are gated, with the undocumented changes of the same commits. Under
  `VERSION_ES6`, stock Rhino again for: logical assignment writing back, `set: undefined` accessors, compound
  assignment keys, error messages of null bases, strict writes to deleted globals, `with` and
  `Symbol.unscopables`, `instanceof`, arrow and nested cover initializers, `eval(...args)`, ASI after multi-line
  comments, object rest/spread of proxies and numeric pattern keys, `in` in for heads, strict `eval`/`arguments`
  targets, generator method prototypes, `>>>` with BigInt, the compiled `finally` and exception ranges
  (`ClassFileWriter.setKeepLiveExceptionRanges`), `for-in` shadowing, inexact Use Strict Directives,
  `trimLeft`/`trimRight` and the HTML method lengths (a second String descriptor), the RegExp legacy statics
  (enumerable, non-configurable), `compile` on subclasses, `setYear`, HTML close comments, optional element
  accesses and calls, strict generators resumed from sloppy code, `yield` in a `finally` run by `return()`,
  array-likes too long for Java (`IllegalArgumentException`) and the GetPrototypeFromConstructor fallbacks.
- `BACKWARD_COMPATIBILITY.md`: section 2 moves into section 1 (1.17-1.62, formerly 2.1-2.46); the platform section
  is now 2. The entries of 2.40-2.46 are corrected (frozen writes and `delete` in strict generators, same-realm
  prototype fallbacks, optional element calls).
- Tests: `SyntaxResidualsTest`, `AnnexBTest`, `OptionalChainingOperatorTest`, `NativeProxyTest` and
  `ObjectLiteralSyntaxTest` assert both sides; new `LanguageResidualsGateTest`. The compiled `finally` and the
  generator `return()` behaviors differ by mode under `VERSION_ES6`, as in stock Rhino, and are asserted per mode.
- test262: unchanged (41,080/42,616, 96.4%).

### Array, Promise, Set, typed array and RegExp residuals only under VERSION_ECMASCRIPT (2026-10-13, plan G6)

- `BACKWARD_COMPATIBILITY.md` 2.29-2.34 are gated, with the undocumented changes of the same commits. Under
  `VERSION_ES6`, stock Rhino again for: ArraySpeciesCreate, `pop`/`shift`/`splice`/`copyWithin`/`flat`/`flatMap`/
  `reverse` (the stock bodies kept as `legacy*` methods), `slice` with species, `toString`, `push` past 2^53-1 and
  array length truncation; the Promise combinators (`resolve` read lazily, iterator closing, the `then` getter,
  the `" rejected"` status); iterator `return` of the built-ins; IsRegExp in `startsWith`/`endsWith`/`includes`;
  ToPrimitive with `null`; bound function `length`; the immutable `Object.prototype`, `__proto__` getter and
  setter-only descriptors; the Set methods (`legacyUnion` and friends); the Error prototypes, `cause`, Date
  `@@toPrimitive`, MakeTime/TimeClip and `Date.parse("-000000...")`; BigInt conversions of Numbers; the typed array
  constructors, `from`, `with`, `set`, `subarray`, `toReversed`/`toSorted`, `@@species` (absent) and `@@iterator`
  (its own function); the RegExp prototype, constructor, `compile(re, flags)`, `flags` order (`gimsyu`), `source`,
  `toString`, quantifiers, negative `lastIndex`, `lastIndex` writes, RegExpExec, `@@search`, `@@replace` and
  format-control characters in regexp literals.
- Tests: the stock `harmony/typed-array-ctor.js`, `harmony/typedarrays.js` and `es2023/typedArray-with.js` are
  restored, with a gated twin in `BuiltinsResidualsTest`; `BuiltinsResidualsTest` and `RegExpCoreTest` assert both
  sides. The interpreter keeps a stock bug for rest parameters with `apply`/`bind` under `VERSION_ES6`.
- test262: unchanged (41,080/42,616, 96.4%).

### Typed arrays, Reflect, Proxy and built-in residuals only under VERSION_ECMASCRIPT (2026-10-13, plan G5)

- `BACKWARD_COMPATIBILITY.md` 2.23-2.28 are gated, with the undocumented changes of the same commits. Under
  `VERSION_ES6`, stock Rhino again for: typed array keys, descriptors, definitions, deletions and stores (only the
  rare branches check the version: in-bounds element writes stay free of it); `Reflect.defineProperty`,
  `deleteProperty`, `get`, `has`, `set` and `setPrototypeOf` (the stock bodies kept as `legacy*` methods); the proxy
  `get`/`has`/`put`/`delete`/`getOwnPropertyDescriptor`/`setPrototype` and the public 3-argument
  `defineOwnProperty` override (restored), index keys passed to traps as numbers, no lookup ending at a proxy;
  `Object.setPrototypeOf` and `copyWithin` with proxies and undefined; own keys with the integer keys first (the
  public `ScriptableObject.KeyComparator` is stock again; the spec order is a private comparator);
  `String.prototype` methods using the Symbol methods of primitives; Array methods ignoring failed writes; Date
  setters; `__defineGetter__` and friends; `hasOwnProperty`, `propertyIsEnumerable`, `isPrototypeOf` and
  `Object.prototype.toString`; `indexOf`/`lastIndexOf`; JSON revivers and replacers; `Object.hasOwn` (length 1),
  `Object.assign` and `Date.prototype.toJSON`.
- Java API: `AbstractEcmaObjectOperations.ordinarySet(..., Scriptable receiver)` is back (`@Deprecated`, with its
  stock semantics), next to the new `Object` receiver overload.
- Tests: the stock `date.tojson.doctest` and the `NativeProxyTest` message are restored.
- test262: unchanged (41,080/42,616, 96.4%). Perf gate: no regression.

### Iteration, literals, yield and function environments only under VERSION_ECMASCRIPT (2026-10-13, plan G4)

- `BACKWARD_COMPATIBILITY.md` 2.15-2.22 are gated, with the undocumented changes of the same commits. Under
  `VERSION_ES6`: for-of uses the stock iteration (`ScriptRuntime.enumInitInOrder`/`enumNextInOrder` restored: `next`
  read at each step, the result converted to an object) and never closes its iterator; getters and setters accept
  any parameters and are constructors; a rest parameter may have an initializer; duplicate `__proto__`, escaped
  `get`/`set` and the token after a `"use strict"` are accepted as before; any anonymous function stored in a literal
  is named after its key; `yield` keeps its JavaScript 1.7 rules (`yield*` may span lines, a function expression in a
  generator cannot be named `yield`), generator parameters are destructured at the first `next()`, a generator's
  prototype has a `constructor` and `%GeneratorPrototype%` none, `yield*` calls `next()` without an argument for
  undefined, and a generator whose body throws stays resumable; generators the class compiler cannot handle are not
  interpreted instead; function-expression names are ordinary variables and `name = null` stores `this.name`;
  `Object.defineProperty` without `enumerable` on a non-configurable property throws as before (internal [[Set]]
  keeps the existing enumerability); `length` of functions cannot be redefined; `Function.prototype.bind` results
  are constructors as before; `Object`, `WeakRef`, `FinalizationRegistry` and `DataView` ignore NewTarget as before,
  `ArrayBuffer[Symbol.species]` does not exist, the RegExp legacy statics read through any object;
  `new.target` in eval code is a SyntaxError.
- Tests: `LegacyBuiltinsGateTest`; the stock `LambdaPropertyTest` and `es6/NewTargetTest` are restored.
- Performance: the stock for-of reads an array whose iteration is not observable (the original
  `Array.prototype.values` and array iterator `next`) by index, as the array iterator does (own elements, the length
  at each step); without it, the gate measured for-of under `VERSION_ES6` 35-58% slower.
- test262: unchanged (41,080/42,616, 96.4%). Perf gate: no regression (for-of re-measured).

### Parameters, toString, eval and destructuring only under VERSION_ECMASCRIPT (2026-10-13, plan G3)

- `BACKWARD_COMPATIBILITY.md` 2.10-2.14 are gated, with the undocumented changes of the same commits: under
  `VERSION_ES6`, only already-strict code rejects duplicate simple parameters, rest parameters are names,
  `arguments` in a default value needs no activation, destructuring parameters are desugared in hash order; method
  and `Function` constructor source texts, proxy `toString`, built-in names and the internal `[Symbol.getSize]` of
  Map/Set prototypes are those of stock Rhino; eval in parameters and indirect eval behave as before.
- Destructuring under `VERSION_ES6` is stock again: `Parser.destructuringArrayLegacy` keeps index access and the
  partial iterator path of parameters (global `Symbol`, holes skipped, closed with the old ternary, lazy default);
  defaults apply when the target is undefined too; object patterns do not check null, computed keys are evaluated
  first; no names for default functions; `for ({x = 1} of ...)` and member targets with defaults are syntax errors;
  `for (const x = ...;;)` is rejected by the interpreter, as in stock; the array iterator reads own holes only.
- test262: unchanged (41,080/42,616, 96.4%). Perf gate: no regression.

### Lexical declarations and functions only under VERSION_ECMASCRIPT (2026-10-13, plan G2)

- `BACKWARD_COMPATIBILITY.md` 2.1-2.9 are gated: `VERSION_ES6` keeps the stock behavior of `const` in loops
  (registers initialized once, `for (const x of ...)` heads), loop heads without per-iteration bindings, `let x;`,
  assignable block-level consts, `typeof this` ("object") and boxed `call`/`apply` receivers of strict functions, no
  `Function.prototype.caller` (bound functions get their own throwing `caller`), the old
  `Function.prototype.arguments` accessor, `bind` (null `this`, dynamic name, the target's arity as length), and the
  stock arguments object (restored next to the spec one in `Arguments`; destructured parameters keep it mapped).
- The interpreter's per-function check uses the new `JSDescriptor.isLatestEcmaScript()`.
- Fix of the 2.9 example: the old value of a non-configurable index was 7 (still mapped), not 1.
- test262: unchanged (41,080/42,616, 96.4%; it runs under `VERSION_ECMASCRIPT`). Perf gate: no regression.

### New features only under VERSION_ECMASCRIPT (2026-10-13, plan G1)

- Decision: `VERSION_ES6` and the older versions keep the behavior of stock Rhino; the features stock Rhino did not
  have are `VERSION_ECMASCRIPT`-only, as stock Rhino ties its ES6 features to `VERSION_ES6`.
- Before `VERSION_ECMASCRIPT`, these are syntax errors again: classes (with fields, private names and static
  blocks), async functions, arrows, methods and generators, `for await`, spread arguments, `import()`, the RegExp
  `d` flag; `await` is a reserved word again. The globals and properties the fork added do not exist:
  `SharedArrayBuffer`, `Atomics`, `Symbol.asyncIterator`, `RegExp.prototype.hasIndices`, the async iteration
  prototypes. `Context.loadModule` and `evaluateModule` throw unless the version is `VERSION_ECMASCRIPT`.
- Tests: the tests of these features run under `VERSION_ECMASCRIPT`; `LatestFeaturesGateTest` checks both sides.
- test262: unchanged (41,080/42,616, 96.4%). Perf gate: no regression.

### ES2022 residuals; M8 done (2026-10-13, plan 8.6)

- Fix: the computed keys of the static and instance elements of a class are evaluated in source order (a 8.1
  limit): when both kinds have computed keys, each is evaluated into a temporary of the class scope once the heritage
  is, and the literals of members read them (Token#FIELD_KEY kind 5 converts a method key).
- The other ES2022 items need no work: `.at()`, `Object.hasOwn` and the error `cause` pass.
- test262: 41,080/42,616 (96.4%), 3 more tests pass. Perf gate: no regression against 8.5.

### Top-level await (2026-10-13, plan 8.5)

- New: `await` and `for await` at the top level of a module. A module that uses them is compiled as an async
  generator (its body is driven by AsyncFunctionDriver), and module evaluation follows the async-evaluation
  algorithm of ES2022 (InnerModuleEvaluation with async parents and evaluation order, as in Nashorn); a graph
  without top-level await evaluates synchronously, as before.
- API: `ModuleRecord.evaluate` (and `Context.evaluateModule`) runs the promise jobs while an asynchronous module
  graph is pending, then throws its evaluation error, so embedders keep a synchronous contract. `import()` settles
  through the evaluation promise of the module (ContinueDynamicImport), one job after a failed load.
- Fix: an export of a name bound by a destructuring declaration (`export var { x } = o`) is no longer reported as
  undeclared; an escaped `await` (`\u0061wait`) is not the keyword.
- test262: `top-level-await` is no longer an unsupported feature: 41,077/42,616 (96.4%), 260 more tests pass.
  Perf gate: no regression against 8.4.

### RegExp match indices (2026-10-13, plan 8.4)

- New (ES6 and later): the RegExp `d` flag, in literals and for the constructor; `RegExp.prototype.hasIndices`; a
  match of a regexp with `d` has an `indices` array of `[start, end]` pairs (undefined for a capture that did not
  participate) with a `groups` object for the named groups (a duplicate name keeps the pair of the group that
  matched).
- test262: 40,817/42,616 (95.8%), 26 more tests pass. Perf gate: no regression against 8.3.

### Class static blocks (2026-10-13, plan 8.3)

- New: `static { ... }` in a class body, run in order with the static fields, with the class as `this`, `super.x`
  and the private names of the class; its `var`s are local to it.
- Implementation: like a static field, an entry of the statics literal (Token#FIELD_KEY kind 4) whose value is a
  synthetic method with the statements of the block as body, called once the class name is bound.
- Early errors: `return`, `arguments`, `await` (as an identifier or an expression; a function expression named
  `await` stays valid), `super()`.
- test262: 40,791/42,616 (95.7%), 36 more tests pass (every in-scope `class-static-block` test). Perf gate: no regression against 8.2.

### Private names (2026-10-12, plan 8.2)

- New: private fields, methods and accessors, instance and static (`#x`, `#m() {}`, `get #a() {}`), `obj.#x` (also
  in optional chains, calls, compound assignments, `++`, destructuring targets) and `#x in obj`, with their brand
  checks (a TypeError of the accessing code's realm) and early errors (undeclared or duplicate names,
  `#constructor`, `delete this.#x`, `super.#x`), also in direct eval code.
- Implementation: each private identifier a class declares is a const binding `#x` of the class scope, initialized
  with a new PrivateName each time the class is evaluated; `obj.#x` is an element access with that key, which
  ScriptRuntime hands to PrivateName (PrivateGet/PrivateSet/`in`). Private elements live in a lazily allocated
  identity map of the ScriptableObject (a weak side table for other Scriptables), invisible to reflection, Java
  interop and Proxy. Private methods and accessors are entries of the member literals keyed by a FieldKey
  (Token#FIELD_KEY kind 2), added to each instance before its fields, or to the constructor.
- `eval` inside a class sees its private names (checked against the scope chain at compile time); `arguments` in
  eval code in a field initializer is now a SyntaxError (a 8.1 limit), using a new JSDescriptor flag.
- Gated by `VERSION_ECMASCRIPT`: ZWNJ and ZWJ in identifiers are no longer stripped (BACKWARD_COMPATIBILITY.md
  1.16).
- test262: `class-fields-private` is no longer an unsupported feature: 40,755/42,616 (95.6%), 3,911 more tests
  pass; no new failure. Perf gate: no regression against 8.1.

### Public class fields (2026-10-12, plan 8.1)

- New: public instance and static class fields, with computed keys, initializers (`this` is the instance or the
  constructor, `super.x` works, an anonymous function is named after its key) and ASI between fields.
- Implementation: a field is an entry of the object literal of the members of its class (the prototype's, or the
  statics') whose key is a ClassFieldKey (Token#FIELD_KEY) and whose value is its initializer, a synthetic method; the
  literal keeps such entries aside, and ClassDefinition records them on the constructor. A base class defines its
  instance fields when the instance is created, a derived class when super() returns; static fields are defined once
  the class name is bound (Token#CLASS_STATIC), and an anonymous class is named first.
- Early errors: `arguments` in an initializer, a field named `constructor`, a static field named `prototype`.
- Known limits: computed keys of static and instance elements are evaluated per literal (statics after) rather than
  in source order; `arguments` inside a direct eval in an initializer is not a SyntaxError.
- test262: `class-fields-public` is no longer an unsupported feature: 36,844/42,616 (86.5%), 771 more tests pass;
  the newly failing class tests need private names (8.2) or are the limits above. Perf gate: no regression against M7.

### Prototypes from another realm's newTarget; M7 done (2026-10-12, plan 7.x)

- All versions: GetPrototypeFromConstructor falls back to the intrinsic of newTarget's realm for the native errors
  (each constructor now passes its own type; before, the realm's Error.prototype), `WeakRef`,
  `FinalizationRegistry` and ordinary objects; see BACKWARD_COMPATIBILITY.md 2.46.
- The other ES2021 items need no work: logical assignment, `replaceAll`, `Promise.any`/`AggregateError`, numeric
  separators pass but for tests that need class fields (M8).
- test262: 36,073/42,616 (84.6%), 11 more tests pass. Perf gate: no regression against M6.

### Dynamic import and import.meta; M6 done (2026-10-12, plan 6.x)

- New: `import(specifier [, options])` in scripts and modules (IR: Token#IMPORT_CALL): the specifier is converted at
  once, and a promise job loads (through the module loaders), links and evaluates the module, then settles with its
  namespace; a module that does not parse rejects with a SyntaxError. The referrer is the script or module the call
  is in (for eval code, the one that contains the eval). Import attributes are validated but not supported (M11).
- New: `import.meta` in modules, an ordinary object with a null prototype whose `url` is the module's origin, or its
  name (as in Nashorn); a SyntaxError in scripts.
- Fixes: a module that fails to load is not kept in the realm's registry; promise jobs do not run while a module
  graph is being loaded (`Context.loadModule`, `import()`); `PathModuleLoader` only loads regular files.
- All versions: an array-like longer than 2^31 - 1 is a RangeError (before, a Java IllegalArgumentException); see
  BACKWARD_COMPATIBILITY.md 2.45.
- The other ES2020 items need no work: `globalThis`, optional chaining, nullish coalescing, BigInt, `matchAll`,
  `allSettled`, for-in order, `export * as ns` pass but for later features (Iterator helpers, `waitAsync`, private
  names) and the primitive `this` residual.
- test262: `dynamic-import` is no longer an unsupported feature, its directories run, and every test gets a
  `PathModuleLoader` rooted at its directory (import() from a script): 36,062/42,616 (84.6%), 559 more tests pass.
  The newly failing tests need `import.defer`, source phase imports or import attributes. Perf gate: no regression against M5.

### M5 (ES2019) needs no work (2026-10-12, plan 5.1)

- Every ES2019 feature passes its in-scope tests (`flat`/`flatMap`, `trimStart`/`trimEnd`, `Symbol.prototype.description`,
  `Object.fromEntries`, well-formed `JSON.stringify`, stable sort, optional catch binding, JSON superset). The
  remaining failures need private methods (M8), or a primitive `this` that reaches a callback or an accessor as a
  wrapper object, a residual recorded in the plan.

### ES2018 residuals; M4 done (2026-10-12, plans 4.2-4.4)

- 4.2 (object rest/spread) and 4.4 (`Promise.prototype.finally`, template literal revision) needed no work: every
  in-scope test passes but those needing private names (M8).
- 4.3 (RegExp ES2018): dotAll, named groups and lookbehind pass. Unicode property escapes keep the JDK's data
  (decision 4.3): `Script_Extensions` and the binary properties the JDK does not carry are not supported, and the
  generated General_Category and Script tests follow a newer Unicode version than the JDK's.
- All versions: the getters of the RegExp flags and `source` answer for the RegExp.prototype of their own realm only;
  see BACKWARD_COMPATIBILITY.md 2.44.
- test262: 35,503/42,616 (83.3%), 7 more tests pass. Perf gate: no regression against 4.1 (the RegExp benchmarks re-measured
  on an idle machine).

### Async iteration (2026-10-12, plan 4.1)

- New: async generators (`async function*`, async generator methods), `yield*` in them, `for await`,
  `Symbol.asyncIterator`, `%AsyncGeneratorFunction%`, `%AsyncGeneratorPrototype%`, `%AsyncIteratorPrototype%` and
  `%AsyncFromSyncIteratorPrototype%`.
- Implementation: an async generator body is a generator (decision 3.1) that yields its awaits as `AwaitRequest`s,
  its `yield*` as a `DelegateRequest`, and the steps of its `for await` loops (next, close) as `AsyncIteration`
  requests (IR: Token#AWAIT and its kinds). `NativeAsyncGenerator` queues next/return/throw requests and drives the
  body, awaiting as the specification does (`return x` awaits `x`; a yielded promise is awaited before it is
  produced; a `return()` at a yield awaits its value). An async function runs the `for await` requests the same way.
- `VERSION_ECMASCRIPT`: `for (async of ...)` is a SyntaxError; see BACKWARD_COMPATIBILITY.md 1.15.
- All versions: a `finally` block run by a generator's `return()` can yield (before, a TypeError in interpreted
  mode), and the return value is kept when it completes; in compiled mode, a `catch` around a `try`/`finally` whose
  `finally` yielded catches what the `finally` rethrows (the exception information of a generator's nested `try`
  was not popped). See BACKWARD_COMPATIBILITY.md 2.43.
- Known issue: in a compiled generator, a labeled `continue` out of a nested for-of loop whose loops both declare
  `const` bindings fails (NullPointerException).
- test262: `async-iteration` is no longer an unsupported feature, and its directories run: 35,496/42,616 (83.3%),
  3,949 more tests pass. The newly failing tests need private methods (M8) or explicit resource management
  (`Symbol.asyncDispose`), or are the 2 noted above. Perf gate: no regression against 3.4 (re-measured on an idle machine;
  compiled typedarray +11%, within tolerance).

### Atomics.wait and notify, test262 agents (2026-10-12, plan 3.4)

- New: `Atomics.wait` and `Atomics.notify`. The waiter lists of a SharedArrayBuffer belong to its storage and are
  used under its lock, so a wait cannot miss a notify between reading the value and suspending. Every agent can
  block, the main thread included. Not yet: `Atomics.waitAsync` (ES2024, 10.3). The test262 tests flagged `CanBlockIsFalse`
  are written for hosts whose main agent cannot block, and are skipped.
- New API: `NativeSharedArrayBuffer.share(scope, buffer)` returns a SharedArrayBuffer of another realm (another
  context, possibly on another thread) over the same memory.
- test262 harness: `$262.agent` (start, broadcast, getReport, sleep, monotonicNow; receiveBroadcast, report,
  leaving in an agent). An agent is a daemon thread with a context and a realm of its own; the agents of a test are
  interrupted when it ends. `$262` is installed before the harness files, which use it (atomicsHelper.js).
- test262: 31,547/42,616 (74.0%), 80 more tests pass. Perf gate: no regression against 3.3 (properties and deltablue re-measured on
  an idle machine).

### SharedArrayBuffer and Atomics (2026-10-12, plan 3.3)

- New: `SharedArrayBuffer` (growable with `maxByteLength`, `grow`, `slice`, `@@species`), and the `Atomics` object:
  `add`, `and`, `compareExchange`, `exchange`, `isLockFree`, `load`, `notify`, `or`, `pause`, `store`, `sub`,
  `xor` over integer and BigInt typed arrays, shared or not. Not yet: `Atomics.wait`, `Atomics.waitAsync` and
  the `$262.agent` harness (3.4); `notify` wakes nobody until then.
- A SharedArrayBuffer is an ArrayBuffer over a storage record (the bytes, allocated up to the maximum length of a
  growable buffer, and the current length), so typed arrays and DataView work on it unchanged. An atomic
  operation holds the lock of its storage, then revalidates the access (RevalidateAtomicAccess), since converting
  the arguments may have detached or shrunk the buffer. `ArrayBuffer.prototype` methods reject a
  SharedArrayBuffer.
- All versions: a strict generator or async function body runs in strict mode when sloppy code resumes it (before,
  a direct `eval` in it was sloppy); see BACKWARD_COMPATIBILITY.md 2.42.
- test262: `SharedArrayBuffer` and `Atomics` are no longer unsupported features, and the `built-ins/AsyncFunction`,
  `async-function` and `await` directories, which 3.1a had left skipped, run: 31,469/42,616 (73.8%), 617 more
  tests pass. The 93 newly failing tests need `$262.agent` or `Atomics.wait`/`waitAsync` (3.4), immutable
  ArrayBuffers, or async iteration (M4). Perf gate: no regression against 3.1b.

### Async functions: early errors; optional chains (2026-10-12, plan 3.1b)

- Early errors: `label: async function f() {}`, and an `await` expression in the parameters of an arrow function
  nested in an async function.
- All versions: `a?.b[c]` short-circuits the element access when `a` is null or undefined (before: a TypeError,
  and `c` was evaluated), and `a?.b[c]()` is undefined (before: a TypeError; `c` is still evaluated there);
  see BACKWARD_COMPATIBILITY.md 2.41.
- 3.2 needs no work: `Object.values`/`entries`/`getOwnPropertyDescriptors`, `padStart`/`padEnd` and trailing
  commas pass every in-scope test.
- test262: 30,852/42,616 (72.4%), 3 more tests pass. Perf gate: no regression against 3.1a (properties and regexptest re-measured
  on an idle machine).

### Async functions (2026-10-12, plan 3.1a)

- New: `async function` declarations and expressions, async arrow functions, async methods (object literals and
  classes), `await`, and `%AsyncFunction%` (the constructor reached from an async function). Not yet: async
  generators and `for await` (M4), top-level `await` (M8).
- Implementation (decision 3.1): an async body is compiled as a generator in both backends, and `await` is a
  yield. `AsyncFunctionDriver` runs the body up to its first `await` before the call returns. It then resumes the
  body from the reactions to each awaited promise (Await without a result capability, on the `Context` microtask
  queue), and settles the promise that the call returned. Parameter initialization runs in the body, so an error
  there rejects the promise.
- `await` is an identifier outside async functions and modules (before, a reserved word in ES6 mode); see
  BACKWARD_COMPATIBILITY.md 2.40.
- Compiled mode: generator arrow functions (async arrows) get the arrow activation (no own `arguments`) and the
  `new.target` of their closure.
- test262: `async-functions` is no longer an unsupported feature: 30,849/42,616 (72.4%), 357 more tests pass. The 24
  async private-method tests it unskips need private methods (M8). Perf gate: against 1.12c, no regression (compiled `instanceof` +7%, within
  tolerance); against the baseline, compiled `instanceof` is at +15%, the drift of earlier milestones (+10-13%).

### ES modules: `super` assignment to a namespace (2026-10-12, plan 1.12c)

- `super.x = v` in a method whose `this` is a module namespace reads the binding (a ReferenceError before its
  initialization) and then fails with a TypeError, as OrdinarySetWithOwnDescriptor specifies (before, always a
  TypeError).
- test262: 30,493/42,616 (71.6%), 1 more test passes. Perf gate: no regression.

### ES modules: early errors (2026-10-11, plan 1.12b)

- Module code: HTML-like comments are not comments, top-level function declarations are lexical (a duplicate
  or a `var` of the same name is a SyntaxError), and `export d\u0065fault` is a SyntaxError.
- test262: 30,492/42,616 (71.6%), 8 more tests pass. Perf gate: no regression (the flagged metrics re-measured on an idle machine).

### ES modules (2026-10-11, plan 1.12a)

- New: ES2015 modules. `import` and `export` in their ES2015-ES2022 forms (named, default, namespace,
  `export * from`, `export * as ns from`, string export names), live bindings, cycles, module scope, namespace
  objects, link before evaluation, a module evaluated once per realm. Not yet: dynamic `import()` and
  `import.meta` (M6), top-level `await` (M8), import attributes and JSON modules (M11).
- New API, a port of Nashorn's `api.modules`: package `org.monflabs.mozilla.javascript.modules` with
  `ModuleLoader` (a chain, first answer wins), `Module` (`source`, `values`, `referrer`), `PathModuleLoader`,
  `ResourceModuleLoader` and `JavaModuleLoader`. The chain is set with `ContextFactory.setModuleLoaders` and can
  be overridden with `Context.setModuleLoaders`; with no loader, a specifier is a file path relative to the
  importing module. `Context.evaluateModule` returns the namespace object; `Context.loadModule` returns a
  `ModuleRecord` (`link`, `evaluate`, `getNamespace`).
- Implementation: a module is parsed with the module goal and rewritten into a strict generator function whose
  scope resolves the imports; no change to the backends.
- test262: the `module` tests run (once each: modules are strict); `top-level-await`, `dynamic-import`,
  `import-attributes` and `json-modules` are unsupported features until their milestones.
- test262: 30,484/42,616 (71.5%); 340 module tests in scope, 336 pass. Perf gate: no regression (compiled
  regexp, strbuild and splay re-measured).

### `__proto__` in object literals, global vars (2026-10-11, plan 1.11b)

- `VERSION_ECMASCRIPT`: only `__proto__: value` sets the prototype of an object literal (B.3.1); a computed
  `["__proto__"]`, a shorthand `{ __proto__ }` and a method define an own property (before, they set the
  prototype).
- `VERSION_ECMASCRIPT`: a global `var` creates a binding unless the global object (or a shared global it inherits
  from) has the property: `var __proto__` and `var toString` shadow the properties of Object.prototype
  (CreateGlobalVarBinding).
- From `VERSION_ES6`: `RegExp.prototype.compile` throws a TypeError of its own realm for an instance of another
  realm.
- test262: 30,156/42,616 (70.8%), 4 more tests pass. Perf gate: no regression (strbuild re-measured).

### Annex B: built-ins, RegExp, comments, calls as targets (2026-10-11, plan 1.11a)

- All versions: `String.prototype.trimLeft`/`trimRight` are the same functions as `trimStart`/`trimEnd`;
  `anchor`, `fontcolor`, `fontsize` and `link` have length 1; `Date.prototype.setYear` applies the two-digit rule
  to ToIntegerOrInfinity(year) (`setYear(-0.5)` is 1900).
- All versions: the legacy static accessors of `RegExp` (`input`, `$_`, `lastMatch`, `$1`–`$9`, ...) are
  non-enumerable and configurable; from `VERSION_ES6`, their receiver must be the `RegExp` of their own realm.
  `RegExp.prototype.compile` throws a TypeError for an instance of a subclass and for a RegExp pattern with
  flags.
- `VERSION_ECMASCRIPT`, RegExp patterns without `u` (B.1.2): `\x` and `\u` need all their hex digits (`/\xa/`
  matches `"xa"`), `\c` followed by a digit or `_` in a class is a control character, a legacy octal escape has
  three digits at most (`/\0022/` is `\x02` then `2`).
- All versions: `-->` after a block comment at the start of a line is an HTML-like comment.
- `VERSION_ECMASCRIPT`: a block function is not hoisted (B.3.3) when a destructured catch parameter binds its
  name; `f() = v` (and `f()++`, `f() += v`, `for (f() in o)`) calls `f` before throwing the ReferenceError;
  `for (var a = 0 in o)` is a SyntaxError in strict code and with a pattern.
- test262: 30,152/42,616 (70.8%), 76 more tests pass. Perf gate: no regression (RegExp and properties benchmarks re-measured).

### Cover initializers, `for-in` shadowing, directives (2026-10-11, plan 1.10g)

- All versions from `VERSION_ES6`: a CoverInitializedName nested in an array or object literal that becomes an
  assignment pattern is accepted (`[{ x = 1 }] = [{}]`, `({ a: { x = 1 } } = o)`; before, a SyntaxError).
- `VERSION_ECMASCRIPT`: a literal containing a CoverInitializedName that is not used as a pattern is a
  SyntaxError (`({ x = 1 }).y`, `[{ x = 1 }] + 1`; before, accepted).
- All versions: `for-in` skips a property of a prototype that is shadowed by a non-enumerable property of an
  object already enumerated; a directive containing an escape or a line continuation (`'use str\<LF>ict'`) is
  not a Use Strict Directive.
- test262: 30,076/42,616 (70.6%), 10 more tests pass. Perf gate: no regression (v8-regexp re-measured).

### Parameters, generators, `finally` in compiled code (2026-10-11, plan 1.10f)

- `VERSION_ECMASCRIPT`: early errors for `let`/`const` redeclaring a parameter, `yield` in the parameters of a
  generator or in a non-generator function nested in a generator (strict code; compiled mode failed with an
  internal error), `new.target` in an arrow function outside any function.
- All versions: generator methods of object literals have a `prototype` (inheriting %GeneratorPrototype%);
  `>>>` converts both operands before throwing for a BigInt.
- Compiled mode, all versions: a `finally` block inlined for a `return` in a `try` with a `catch` is no longer
  covered by the `finally` handler (an exception thrown by it ran the `finally` block a second time); the class
  file writer keeps the live part of an exception range that starts in dead code (a `try` ending with an
  unreachable `return` lost the `finally` handler of its `catch` block).
- test262: 30,066/42,616 (70.6%), 10 more tests pass. Perf gate: no regression (v8-earley-boyer compiled +11%, within tolerance; v8-crypto compiled -10%).

### Early errors (2026-10-10, plan 1.10e)

- `VERSION_ECMASCRIPT`: the static semantics early errors are reported at compile time (before, most of them were
  accepted, and some failed at run time):
  - AssignmentTargetType: `f() = 1` in strict code, `f() &&= 1`, `({}) = 1`, `a?.b = 1`, `` f()`` = 1 ``,
    `() => {} = 1`, and the same targets for `++`/`--` and as `for-in`/`for-of` heads;
  - strict code: `delete x`, `eval++`/`arguments--`, legacy octal (`"\1"`) and `\8`, `\9` escapes in string
    literals, including a string of the directive prologue before `"use strict"`;
  - escaped keywords (`tru\u0065`, `n\u0065w.target`, `new.t\u0061rget`, `o\u0066` in `for-of`);
  - numeric literals: `3in []` (a literal followed by an identifier), `08n`/`00n`, `08_0`;
  - `const x;` without an initializer, `l: const x = 1`, `l: let x`, `l: function* g() {}`, `l: function f() {}` in
    strict code, `if (a) let [x] = y`, `with (o) function f() {}`, `if (a) function* g() {}`, `if (a) l: function
    f() {}`;
  - `catch (e) { let e; }` and `catch (e) { function e() {} }`;
  - `continue l` where `l` labels a block (before, it continued the inner loop);
  - `a?.b`` ` `` (a tagged template in an optional chain);
  - the right-hand side of `for-of` is an AssignmentExpression (`for (x of a, b)`).
- `VERSION_ECMASCRIPT`: `eval?.(x)` and `` eval`x` `` are indirect calls of eval.
- test262: 30,056/42,616 (70.5%), 111 more tests pass.

### Global declarations, supplementary identifiers (2026-10-10, plan 1.10d)

- `VERSION_ECMASCRIPT`: GlobalDeclarationInstantiation of scripts: the global let/const/class are checked
  (CanDeclare…, a name already declared or a non-configurable global is a SyntaxError) and the functions and vars
  (CanDeclareGlobalFunction, CanDeclareGlobalVar: a TypeError on a non-extensible global object or a
  non-configurable, non-writable property) before any binding is created; a global function replaces a
  configurable property with a writable, enumerable, non-configurable one (CreateGlobalFunctionBinding); `var NaN`,
  `var undefined` and `var Infinity` are allowed (before, a "redeclaration of const" TypeError).
- All versions: identifiers may contain supplementary characters (U+10000 and above, e.g. U+13000); the tokenizer
  took them for the BMP character with the same low 16 bits (U+13000 was read as the ideographic space U+3000).
- test262: 29,945/42,616 (70.3%), 22 more tests pass.

### Language residuals: arrow covers, eval spread, instanceof, rest, `let` (2026-10-10, plan 1.10b–c)

All versions unless stated (`BACKWARD_COMPATIBILITY.md` 1.9 and 2.36):

- An object literal with a CoverInitializedName in parentheses (`({ x = 1 }) => x`) is accepted when an arrow
  follows (before, a SyntaxError); without the arrow, it stays a SyntaxError.
- A multi-line comment that contains a line terminator counts as one for automatic semicolon insertion.
- `eval(...args)` is a direct eval (both backends; before, an indirect call).
- From `VERSION_ES6`: `instanceof` follows InstanceofOperator: `Symbol.hasInstance` is called for any left
  operand (a primitive included), a non-callable `Symbol.hasInstance` or a right operand that is neither callable
  nor has one is a TypeError (`1 instanceof Math`); Function.prototype[@@hasInstance] returns false for a
  primitive without reading `prototype`.
- Object rest and spread with a proxy source use CopyDataProperties ([[GetOwnPropertyDescriptor]] for each key
  that is not excluded); numeric keys of a rest pattern (`{ 0: a, ...r }`) are excluded; a symbol computed key is
  excluded in interpreted mode too.
- `in` is allowed inside brackets and braces of a `for` head (`for ([x = "a" in o] of …)`).
- From `VERSION_ES6`: destructuring assignment to a `let`/`const` binding in its temporal dead zone throws a
  ReferenceError (with `VERSION_ECMASCRIPT`, which has the TDZ).
- `VERSION_ECMASCRIPT`: a pattern in a `for-in` head destructures the key (`for ([a] in o)` takes the first
  character); JavaScript 1.7 read `[key, value]` from it, which `VERSION_ES6` keeps.
- In strict code, `eval` and `arguments` as targets of a destructuring assignment are an early SyntaxError.
- `VERSION_ECMASCRIPT`: `let` is an identifier in sloppy code where it does not start a declaration (`var let`,
  `let = 1`, `{ let }`, `for (let in o)`, `if (a) let ⏎ x` — a line terminator after `let` in a single-statement
  position ends the expression statement `let`); `l\u0065t` is always an identifier. `let [` and `let` followed by
  an identifier or `{` still declare.
- test262: 66 more files pass; 29,923 of 42,616 in-scope files (70.2%).

### Language residuals: completion values, assignments, `with` (2026-10-10, plan 1.10a)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 1.8 and 2.35) unless stated:

- `VERSION_ECMASCRIPT`: completion values (the result of `eval` and of scripts) follow the ES2015 reform: `if`,
  loops, `switch`, `try` and `with` complete with `undefined` when their body produces no value
  (`eval("1; if (true) {}")` is `undefined`), a `catch` block starts from `undefined`, and a `finally` block that
  completes normally does not change the value (`eval("6; try { 7; } finally { 8; }")` is 7).
- Logical assignment (`&&=`, `||=`, `??=`) evaluates its target once and does not assign when it short-circuits
  (before, it always wrote back, which threw on a read-only property); all versions.
- A compound assignment or `++`/`--` on an element converts an object key with ToPropertyKey once, and the
  TypeError of a `null`/`undefined` base comes before the key is converted (its message no longer calls the key's
  `toString`).
- An accessor defined with `set: undefined` has no setter: a strict assignment throws a TypeError (before, the
  assignment was silently ignored); all versions.
- In strict code, assigning a global variable that was deleted after its reference was resolved is a
  ReferenceError (Object Environment Record SetMutableBinding).
- `with` honors `Symbol.unscopables` (HasBinding: HasProperty, then the unscopables of the object) and
  GetBindingValue checks the binding again.
- test262: 171 more files pass; 29,857 of 42,616 in-scope files (70.1%).

### RegExp, ES2015 core (2026-10-10, plan 1.9a)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 1.7 and 2.34) unless stated:

- `RegExp.prototype` is an ordinary object: `Object.prototype.toString` gives `[object Object]`, `exec`/`test`
  throw on it, and the flag getters return `undefined` (`source` gives `"(?:)"`) for it and throw a TypeError for
  any other non-RegExp.
- `get flags` reads `hasIndices`, `global`, `ignoreCase`, `multiline`, `dotAll`, `unicode`, `unicodeSets` and
  `sticky` in this order (a missing property is false); `toString` is generic (`source` and `flags`).
- The constructor follows 22.2.4.1: IsRegExp (`Symbol.match`), `RegExp(re)` returns `re` only when
  `re.constructor` is `RegExp`, a regexp-like object gives its `source` and `flags`, and the prototype is taken
  from `new.target` before the pattern is converted.
- `source` escapes line terminators and gives `"(?:)"` for an empty pattern (EscapeRegExpPattern), so it reads
  back as the same literal.
- Patterns: a quantifier bound may exceed 65535 (larger bounds behave as unbounded); a braced quantifier with
  nothing to repeat (`x{1}{1,}`) is a SyntaxError.
- `exec`/`test` treat a negative `lastIndex` as 0 (ToLength). RegExpExec throws a TypeError when a custom `exec`
  returns neither an object nor null; `@@search` compares `lastIndex` with SameValue and writes it with
  Set(…, true); `@@replace` takes `global` from the `flags` property.
- All versions: format-control characters (U+00AD, U+200B, …) belong to a regexp literal instead of being
  stripped.
- `VERSION_ECMASCRIPT`: `\p`/`\P` without the `u` flag are identity escapes (Annex B.1.2); before, Rhino read
  them as property escapes.
- test262: 58 more files pass; 29,686 of 42,616 in-scope files (69.7%).

### Built-ins residuals: typed arrays (2026-10-10, plan 1.8c, part 9)

All versions unless stated (`BACKWARD_COMPATIBILITY.md` 1.6 and 2.33):

- `VERSION_ECMASCRIPT`: typed arrays store their elements little endian (`FEATURE_LITTLE_ENDIAN` defaults to
  true), as test262 and the web expect.
- The constructors apply ToIndex to a primitive argument (a negative or infinite length is a RangeError, a symbol a
  TypeError; before, ToInt32 or an Error); BigInt typed arrays, `DataView.prototype.setBigInt64`/`setBigUint64`
  and `BigInt.asIntN` use ToBigInt (a Number is a TypeError).
- `%TypedArray%.from` iterates any source with a `Symbol.iterator` method (arrays included, so the values are
  read before they are converted and holes become `undefined`); a `null` iterator method is ignored, also in the
  constructors. `%TypedArray%[Symbol.species]` exists; `%TypedArray%.prototype.toString` is
  `Array.prototype.toString` and `[Symbol.iterator]` is `values`.
- `set` converts a primitive source with ToObject; `subarray` and `with` use ToIntegerOrInfinity (Infinity and
  -0.5 work); `with` converts the value before checking the index against the current length; `toReversed`,
  `toSorted` and `with` throw on a detached or out-of-bounds array; `toLocaleString` reads the elements with Get.
- An array iterator that is done stays done, even when its typed array then goes out of bounds.
- A strict assignment through a typed array in the prototype chain throws when the receiver cannot be written.
- The getters of symbol-keyed built-in accessors are named `get [Symbol.toStringTag]` (before, `get
  Symbol(Symbol.toStringTag)`); ToIntegerOrInfinity gives +0 for values in (-1, 0).
- test262: 36 more files pass; 29,628 of 42,616 in-scope files (69.5%).

### Built-ins residuals: Set methods, Date, BigInt, Error (2026-10-10, plan 1.8c, part 8)

All versions unless stated (`BACKWARD_COMPATIBILITY.md` 2.32):

- The Set methods (`union`, `intersection`, `difference`, `symmetricDifference`, `isSubsetOf`, `isSupersetOf`,
  `isDisjointFrom`) follow the spec: GetSetRecord reads and converts `size` (a negative size is a RangeError)
  before reading `has` and `keys`; the iterator returned by `keys()` is used through its `next` method (not
  `Symbol.iterator`) and closed when `isSupersetOf`/`isDisjointFrom` return early; `symmetricDifference` does not
  call `has`; `isSupersetOf` returns false without iterating when the argument is larger.
- Date: TimeClip gives +0 for -0 (`new Date(-0.5)`), MakeTime adds its products in the spec's order, the year
  `-000000` is rejected by the ISO format; from `VERSION_ES6`, `Date.prototype[Symbol.toPrimitive]` is read-only
  and throws for a primitive receiver.
- `BigInt.asIntN`/`asUintN` throw a TypeError for a Number (ToBigInt).
- From `VERSION_ES6`: the options of the Error constructors may be any object (a proxy included), and `cause` is
  read with HasProperty then Get; the prototypes of TypeError, RangeError, … are ordinary objects
  (`Object.prototype.toString` gives `[object Object]`).
- test262: 32 more files pass; 29,589 of 42,616 in-scope files (69.4%).

### Built-ins residuals: Object, array length (2026-10-10, plan 1.8c, part 7)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.31) unless stated:

- ArraySetLength deletes the elements from the end and stops at a non-configurable one: the length stays above
  it and the definition fails (`Object.defineProperty` throws, a strict assignment throws). An array index at or
  above a read-only `length` cannot be defined, and `length` grows only once the element is defined.
- `Object.prototype` is an immutable prototype exotic object: changing its prototype throws (`Object.setPrototypeOf`,
  `__proto__`) or returns false (`Reflect.setPrototypeOf`).
- The index properties of a String object are read-only: a strict assignment throws, and `Object.assign` onto a
  string throws a TypeError (it writes with [[Set]]).
- The `__proto__` getter throws on `undefined`/`null`; an accessor defined with only a setter reports `get:
  undefined` in its descriptor.
- All versions: `Object.freeze`/`seal` of a proxy call its `defineProperty` trap with the partial descriptors of
  SetIntegrityLevel and throw when it refuses; `Object.keys`/`values`/`entries` of a proxy call
  `getOwnPropertyDescriptor` then `get` for each key; `Object.assign` from a proxy source copies index keys as
  indexes.
- test262: 39 more files pass; 29,557 of 42,616 in-scope files (69.4%).

### Built-ins residuals: Promise, String, conversions (2026-10-10, plan 1.8c, part 6)

All versions unless stated (`BACKWARD_COMPATIBILITY.md` 2.30):

- `Promise.all`/`allSettled`/`any`/`race` get `C.resolve` (GetPromiseResolve) before the iterator, and a TypeError
  when it is not callable rejects the promise; when the iteration fails, the error of the iterator's `return`
  method is ignored (IteratorClose with a throw completion). `allSettled` reports `"rejected"` (before,
  `" rejected"`). An abrupt Get of `then` in a promise resolution rejects the promise instead of throwing.
- Iterators used by the built-ins (`IteratorLikeIterable`) read their `return` method when they are closed, not
  when the iteration starts.
- From `VERSION_ES6`: `startsWith`, `endsWith` and `includes` use IsRegExp (`Symbol.match`) before converting the
  argument; a `valueOf`/`toString` that returns `null` gives `null` to ToPrimitive (before, the other method was
  tried).
- The TypeError of a non-callable `Symbol.match`/`replace`/`search`/`split`/`matchAll` method no longer converts
  the argument to a string; `Function.prototype.toString` of a function no longer calls its `valueOf`.
- Interpreted mode: a rest parameter receives the bound arguments of a call through `apply` or a bound function
  (before, `f.bind(null, 7)(0, 1)` gave `[0, 1, 1]` to `...args`).
- From `VERSION_ES6`: the `length` of a bound function comes from the target's own `length` property (Infinity, or
  above 2^31−1, is kept); ToIntegerOrInfinity maps NaN and -0 to +0 (`TypedArray.prototype.subarray`).
- Constructing through a revoked proxy whose `prototype` is not an object throws a TypeError (GetFunctionRealm).
- test262: 44 more files pass; 29,518 of 42,616 in-scope files (69.3%).

### Built-ins residuals: Array methods (2026-10-10, plan 1.8c, part 5)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.29) unless stated:

- ArraySpeciesCreate uses IsArray (a proxy of an array gets its species, a revoked proxy throws) and passes the
  length of the result to the species constructor (`slice`, `splice`).
- `Array.prototype.toString` calls the `join` method of the object, or Object.prototype.toString when it is not
  callable; `Object.prototype.toString` gives `Object` (or `Function`) for a proxy that is not an array.
- `push` throws a TypeError when the new length would exceed 2^53−1; assigning `length` converts the value before
  checking that it is writable (a conversion that freezes the length makes a strict assignment throw).
- All versions: `flat`/`flatMap` follow FlattenIntoArray (HasProperty then Get, no final `length` write) and
  `flatMap` reads the length before checking the callback; `reverse` and the elements copied by `splice` use
  HasProperty then Get; `splice()` without arguments reads and writes `length`; `splice` no longer throws a
  RangeError for a delete count above 2^31−1 when a species constructor creates the result; `copyWithin` takes the
  generic path when the conversion of its arguments changed the array; `pop` on a hole reads the prototype.
- All versions: the Array.prototype iteration methods (`every`, `forEach`, `map`, `reduce`, …) skip the indexes of a typed array
  that are out of bounds after its buffer shrank; `Reflect.has` converts the key with ToPropertyKey (`"0"` finds
  index 0).
- Not yet: the dense fast paths of `push`/`unshift` ignore index setters of `Array.prototype`.
- test262: 42 more files pass; 29,474 of 42,616 in-scope files (69.2%).

### Built-ins residuals: JSON, Object.hasOwn, Object.assign (2026-10-10, plan 1.8c, part 4)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.28) unless stated:

- `JSON.parse` revivers follow InternalizeJSONProperty: IsArray (a proxy of an array is walked by its `length`),
  EnumerableOwnProperties for objects (the `ownKeys` and `getOwnPropertyDescriptor` traps of a proxy), string keys
  (`"0"`, not `0`), and the revived value is written with CreateDataProperty (a read-only but configurable property
  is replaced; a refused definition is ignored) and removed with [[Delete]].
- `JSON.stringify` passes string keys to the replacer function and `undefined` for a property deleted during the
  serialization. All versions: a proxy of an array is serialized, and accepted as a replacer, as an array (IsArray,
  LengthOfArrayLike, Get); a proxy object is serialized with its enumerable own keys.
- `Object.hasOwn` converts the object before the key, consults a proxy's `getOwnPropertyDescriptor` trap, and has
  length 2; `Object.assign` writes with Set(…, true) (a TypeError for a read-only target property) and reads the keys
  of a proxy source through its traps.
- All versions: `Date.prototype.toJSON` returns the result of `toISOString` as is.
- Not yet: the reviver `context` argument (JSON source access, plan 12.6), `toJSON` of a BigInt called with the
  primitive as `this`.
- test262: 27 more files pass; 29,432 of 42,616 in-scope files (69.1%).

### Built-ins residuals: Date setters, Object.prototype methods (2026-10-10, plan 1.8c, part 3)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.27) unless stated:

- All versions: the Date setters (`setDate`, `setHours`, …, not `setFullYear`) read the date value before converting
  their arguments, and leave an invalid date unchanged (ES2023).
- `__defineGetter__`/`__defineSetter__`/`__lookupGetter__`/`__lookupSetter__` follow Annex B (ToObject, ToPropertyKey,
  DefinePropertyOrThrow; the lookups walk [[GetOwnProperty]]/[[GetPrototypeOf]], so proxies are consulted);
  `hasOwnProperty` and `propertyIsEnumerable` convert the key with ToPropertyKey (a symbol from an object) before
  `this`; `isPrototypeOf` returns false for a primitive argument before converting `this`.
- `Object.prototype.toString` uses the builtinTag when `Symbol.toStringTag` is absent or not a string: `Object` for
  the standard objects that rely on the tag (Promise, Map, Symbol, Math, …), `Array` for a proxy of an array.
- All versions: the source text of a class getter or setter starts with `get`/`set`.
- `Array.prototype.indexOf`/`lastIndexOf` return -1 for an empty array before converting `fromIndex`, use
  HasProperty before Get, and take the generic path when the conversion changed the array.
- test262: 57 more files pass; 29,405 of 42,616 in-scope files (69.0%).

### Array methods throw when a write fails (2026-10-10, plan 1.8c, part 2)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.26; `js1_6/Array/regress-304828.js` keeps the 1.x behavior):

- The generic paths of the Array methods store elements and the length with Set(O, P, V, true) and delete with
  DeletePropertyOrThrow (the context is strict while they write): a read-only property, a non-extensible object, a
  non-configurable property or a refusing proxy is a TypeError (before, the write was ignored). The dense fast paths
  of `push`, `pop`, `shift`, `unshift`, `reverse` and `copyWithin` only apply to arrays with a writable length that
  are extensible.
- Not yet: the fast paths ignore index properties of `Array.prototype` (a setter there is not called).
- test262: 16 more files pass; 29,348 of 42,616 in-scope files (68.9%).

### Built-ins residuals: String symbol methods, key order (2026-10-10, plan 1.8c, part 1)

All versions (`BACKWARD_COMPATIBILITY.md` 2.25):

- `String.prototype.match`, `matchAll`, `replace`, `replaceAll`, `search` and `split` look up the `Symbol.match`
  (etc.) method of their argument only when it is an object (ES2025): a primitive is converted to a string.
- OrdinaryOwnPropertyKeys order: the array indexes kept as strings (from 2**31 to 2**32 - 2) are sorted with the
  other indexes, and symbols come after strings (`ScriptableObject.sortKeys`, which leaves keys already in order
  untouched).
- Proxy traps receive property keys as strings (before, an index was passed as a number).
- test262: 29 more files pass; 29,332 of 42,616 in-scope files (68.8%).

### [[Delete]], [[DefineOwnProperty]] and [[SetPrototypeOf]] with results (2026-10-10, plan 1.8b, part 2)

All versions (`BACKWARD_COMPATIBILITY.md` 2.24):

- `ScriptableObject.delete(cx, key)` and `setPrototypeOf(cx, proto)` are [[Delete]] and [[SetPrototypeOf]] returning
  whether they succeeded, overridden by proxies (traps, invariants, or the target's method) and typed arrays;
  `Reflect.deleteProperty`/`setPrototypeOf` return them, `delete` and `Object.setPrototypeOf` on a proxy throw on
  failure (strict code for `delete`), without calling its `has` or `getPrototypeOf` traps afterwards.
- A proxy's [[DefineOwnProperty]] (also the 4-argument `defineOwnProperty`, which bypassed the trap) returns the
  trap's result or the target's, validated without throwing; `Object.defineProperty`/`defineProperties` throw when
  it is false. `Reflect.defineProperty` converts its key first, validates the descriptor and no longer turns every
  error into false (a proxy invariant violation, an abrupt key conversion propagate).
- A proxy without a `get` trap reads `target.[[Get]](P, Receiver)` through the target's prototype chain, the
  receiver staying the proxy (or the object inheriting from it).
- Updated test: `NativeProxyTest.definePropertyFreezedWithoutHandler` (message of the TypeError).
- test262: 18 more files pass; 29,303 of 42,616 in-scope files (68.8%).

### [[Set]] with a result; proxy descriptors (2026-10-10, plan 1.8b, part 1)

All versions (`BACKWARD_COMPATIBILITY.md` 2.24):

- `ScriptableObject.set(cx, key, value, receiver)` is [[Set]] returning whether it succeeded: OrdinarySet
  (`AbstractEcmaObjectOperations.ordinarySet`) through the prototype chain, overridden by proxies (the `set` trap and
  its invariants, SameValue) and typed arrays; `Reflect.set` returns it and accepts any receiver; a definition on the
  receiver returns false rather than throwing (`AbstractEcmaObjectOperations.defineOwn`).
- An assignment through a proxy whose [[Set]] fails throws in strict code; without a `set` trap, the receiver stays
  the proxy (before, the target), so its `getOwnPropertyDescriptor`/`defineProperty` traps run.
- A proxy in a prototype chain answers [[HasProperty]] and [[Set]] itself (`ScriptableObject.endsPropertyLookup`):
  its `getPrototypeOf` and `has` traps are not called by a lookup or an assignment through it;
  `Object.setPrototypeOf` stops its cycle check at a proxy.
- The `getOwnPropertyDescriptor` trap result is validated (ToPropertyDescriptor, CompletePropertyDescriptor,
  IsCompatiblePropertyDescriptor and the non-configurable invariants), and may be an accessor.
- A proxy without a `has` trap answers with the target's [[HasProperty]] (its prototype chain included); an
  assignment in a `with` statement checks the binding again (SetMutableBinding of an object environment).
- `Reflect.get` converts its key with ToPropertyKey and calls a getter of an index key (before, `Reflect.get(o, "4")`
  missed it); `Array.prototype.copyWithin` uses HasProperty and copies `undefined` values (before, deleted).
- test262: 64 more files pass; 29,285 of 42,616 in-scope files (68.7%).

### Typed array internal methods (2026-10-10, plan 1.8a)

All versions (`BACKWARD_COMPATIBILITY.md` 2.23):

- The internal methods of typed arrays follow ECMA-262 10.4.5: a canonical numeric key (`"-0"`, `"1.5"`, out of
  bounds) designates an element or nothing and never reaches the prototype chain (`ScriptableObject
  .endsPropertyLookup`, used by `hasProperty` and `getBase`); `[[OwnPropertyKeys]]` lists the indexes, then the other
  keys (`getIds(map, …)` is now protected); `[[GetOwnProperty]]` describes an element; `[[DefineOwnProperty]]`
  rejects a non-configurable, non-enumerable, non-writable or accessor descriptor; deleting an element fails (a
  TypeError in strict code); a store converts the value even for an invalid index; a store through an object
  inheriting from a typed array creates a property of that object.
- Not yet: `Reflect.set` returning false and receivers that are not objects (with the Reflect/Proxy step).
- test262: 49 more files pass; 29,221 of 42,616 in-scope files (68.6%).

### Environments of functions and eval code (2026-10-10, plan 1.2g)

From `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.9, 2.12, 2.21, 2.22) and under `VERSION_ECMASCRIPT` (1.4, 1.5):

- The name of a named function expression is an immutable binding: an assignment in the function is dropped, or
  throws in strict code (`Token.FN_NAME_ASSIGN`); from closures and eval code, the activation keeps it
  (`NativeCall.isSelfName`, `JSDescriptor.hasSelfNameBinding`). JavaScript 1.x's `name = null` → `this.name = null`
  rewrite is limited to versions before `VERSION_ES6`; a function compiled by `Context.compileFunction` keeps both
  JavaScript 1.x behaviors (`FunctionNode.isCompiledByContext`, `FunctionNullSetTest`).
- Updated tests: `NewTargetTest` (`new.target` in eval code of a function is allowed), `LambdaPropertyTest` (a
  writable non-configurable property may be redefined with a new value).
- Eval code's top-level `let`/`const`/`class` are in a block of their own (`IRFactory.evalLexicalScope`, under
  `VERSION_ECMASCRIPT`), unless the eval code declares functions; a `var` of sloppy eval code conflicting with a
  top-level `let`/`const` of the calling function is a SyntaxError (`JSDescriptor.getParamOrVarLexical`).
- A direct eval in the parameters of an arrow function is checked like one in a function's parameters (an arrow has
  no `arguments` binding); `new.target` in direct eval code is that of the enclosing function
  (`NativeCall.newTarget`).
- A destructured parameter makes `arguments` unmapped.
- `Object.defineProperty` of a non-configurable property compares `enumerable` only when the descriptor has it.
- Not done (see the plan): the separate var environment of a body whose parameters have expressions
  (`scope-paramsbody-var-open`), the TDZ scope of a for-in/of head, Annex B `skip-early-err-try`.
- test262: 56 more files pass; 29,172 of 42,616 in-scope files (68.5%).

### Classes: subclassing built-ins (2026-10-09, plan 1.7c)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.20):

- `WeakRef` and `FinalizationRegistry` take their prototype from `NewTarget` (`ScriptRuntime.findPrototype(f,
  nt)`); `Object` called with a NewTarget other than itself ignores its argument.
- `ArrayBuffer[Symbol.species]` is defined, so `slice` of a subclass instance creates a subclass instance.
- `DataView` reads the prototype of `NewTarget` before checking the buffer again (detached, resized).
- The Annex B legacy static properties of `RegExp` throw a TypeError when read through another constructor (2.20).
- test262: 27 more files pass; 29,116 of 42,616 in-scope files (68.3%).

### Classes: fixes from the class test directories (2026-10-09, plan 1.7)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.18, 2.19):

- Static getters and setters keep their accessor descriptors when copied to the constructor (they became data
  properties); a static `length` or `name` method replaces the built-in property (function `length` can now be
  redefined, like `name`).
- NamedEvaluation of anonymous classes (`var C = class {}`, defaults, properties), and of anonymous functions and
  classes that are the default value of a parameter (2.18).
- The inner binding of a class name is immutable (assigning it is a TypeError, the class code being strict) and in
  its temporal dead zone in the heritage and computed keys (`class C extends C {}`, under `VERSION_ECMASCRIPT`); a
  constructor no longer has its own binding of the class name. In function code, a `const` register of a scope is
  initialized with `SETCONSTVAR` (`NodeTransformer.visitLet`).
- Getters and setters have no `prototype` and are not constructors (2.19).
- A function bound to a non-constructor is not a constructor (`BoundFunction.isConstructor`); `extends` checks it
  before reading `prototype`, and its error message does not convert the value (a proxy).
- An instance created by a plain function for a derived class with a default constructor gets the class
  `prototype`.
- `super.x` in a derived constructor before `super()` is a ReferenceError (this binding checked first).
- The `{` after `extends new F()` starts the class body (not Rhino's `new F() {...}` initializer).
- test262: 226 more files pass; 29,089 of 42,616 in-scope files (68.3%).

### Class test directories un-skipped (2026-10-09, plan 1.7)

- `language/expressions/class` and `language/statements/class` were skipped as whole directories (`~`) in
  `test262.properties`; they now run, their failures listed as expected failures.
- test262: 2,372 more files pass; 28,863 of 42,616 in-scope files (67.7%).

### Classes: super and derived constructors (2026-10-09, plan 1.7b)

All versions from `VERSION_ES6` (additive):

- A derived class constructor (`JSDescriptor.isDerivedConstructor`) runs with a this binding
  (`ClassDefinition.DerivedThis`) as the `this` of its frame, also captured by its arrow functions. `this` in it
  (`Node.DERIVED_THIS_PROP`, `Icode.DERIVED_THIS`) throws a `ReferenceError` until `super(...)` has returned.
- `super(...)` calls the super constructor (the constructor's current `[[Prototype]]`, looked up before the
  arguments) with `new.target`, then binds `this` (`ReferenceError` when called twice); it is also allowed in
  arrow functions and direct `eval` in a derived constructor, and a `SyntaxError` elsewhere.
- [[Construct]] of a derived constructor returns an object result, else the bound `this`; any other result is a
  `TypeError`. The default derived constructor follows the same path.
- The home object of a class constructor is the prototype and that of a static method is the constructor, for
  `super.x`; `super.x` in a derived constructor uses the bound `this`.
- The instance of a base class takes the `prototype` of any new.target (GetPrototypeFromConstructor, e.g.
  `Reflect.construct(C, [], Object)`), not only of an interpreted function.
- The `SyntaxError` message of `super()` outside a derived constructor changed (`msg.class.super.call`).
- test262: 87 more files pass; 26,491 of 42,616 in-scope files (62.2%).

### Classes: syntax and definition (2026-10-09, plan 1.7a)

All versions from `VERSION_ES6` (additive: `class` was a reserved word):

- Class declarations and expressions: constructor, methods, getters/setters, static and computed members, an
  optional `extends` clause (including `null`), strict-mode bodies. The parser builds a `ClassNode`; `IRFactory`
  turns it into a `CLASS` node (constructor, heritage, and the prototype and static members as object literals)
  wrapped in a scope binding the class name; both backends call `ClassDefinition.define`
  (ClassDefinitionEvaluation).
- A class constructor throws a `TypeError` when called without `new` (`JSDescriptor` class-constructor flag); a
  class without a constructor gets a default one forwarding to its parent; `prototype` is non-writable, methods are
  non-enumerable, a computed `['constructor']` member replaces the `constructor` property.
- Early errors: duplicate constructors, special constructor methods, `static prototype`, unnamed declarations, a
  class declaration as a statement body or labeled item.
- Fixes found by the un-skipped tests: BigInt literal property keys in object literals, classes and destructuring
  patterns crashed the compiler.
- `class` removed from the unsupported test262 features. Not yet: `super`, derived constructors binding `this`,
  `new.target`, the home object of static methods (1.7b), subclassing built-ins (1.7c); the class name binding is
  not immutable.
- test262: 364 more files pass; 26,404 of 42,616 in-scope files (62.0%). The static member order tests still fail
  on Rhino's own `arity` property of functions.

### Generator details (2026-10-09, plan 1.6c)

All versions from `VERSION_ES6`:

- `yield*` takes its operand from the next line; a function expression nested in a generator may be named `yield`.
- `yield*` calls the delegate's `next` with the received value, also `undefined` (one argument).
- `%GeneratorPrototype%.constructor` is `%GeneratorFunction.prototype%` (defined also when the standard objects
  are sealed).
- A generator whose body throws is completed (`ES6Generator.resumeLocal`): resuming it again gives `{done: true}`;
  before, the interpreter resumed a frame that could not be resumed (an internal error), e.g. after calling the
  generator's own `next` from inside it.
- test262: 11 more files pass; 26,040 of 42,616 in-scope files (61.1%).

### Generator parameters bound at the call (2026-10-09, plan 1.6b)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.17):

- The destructuring of a generator's parameters joins its default values in the generator's parameter
  initialization block (`FunctionNode.getGeneratorParamInitBlock`), run by both backends before the generator object
  is created; `NodeTransformer` now transforms that block too. Without the abrupt-completion guard (no `try` in the
  prologue of a compiled generator).
- test262: 156 more files pass; 26,029 of 42,616 in-scope files (61.1%).

### yield (2026-10-09, plan 1.6a)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.17):

- The operand of `yield` is an AssignmentExpression (`[yield 1, 2]`); `yield` without operand before `,` and `:`;
  `yield` after a comma and as an argument (the JavaScript 1.7 "parenthesized" rule stays for older versions); a
  statement starting with `yield` is an expression statement; `yield*` must be on one line.
- `yield` is an identifier in non-strict code outside generator functions (`Parser.isYieldAnIdentifier`, applied
  when the token is peeked); generator methods are marked before their body is parsed; the name of a generator
  expression may not be `yield`.
- The `prototype` of a generator function has no `constructor`.
- `Context.compileImpl` runs a script with generators in the interpreter when the class compiler fails on it (a
  `yield` with typed values on the operand stack, inside a call or an object literal), as for class size limits.
- test262: 70 more files pass; 25,873 of 42,616 in-scope files (60.7%).

### Names of object literal values (2026-10-09, plan 1.5b)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.16):

- The runtime name inference of object literals (`NewLiteralStorage`) only names functions marked by `IRFactory` as
  written directly as a property value (`JSDescriptor.isNamedByLiteral`, also emitted by `ClassCompiler`); other
  anonymous functions stored in a property keep their name.
- test262: 1 more file passes (`fn-name-cover`); 25,803 of 42,616 in-scope files (60.5%).

### Early errors of object literals and functions (2026-10-09, plan 1.5a)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.16):

- A getter with parameters, a setter without exactly one (or with a rest parameter), and a rest parameter with an
  initializer are SyntaxErrors. Escaped `get`/`set` (`TokenStream.nameHadEscape`) are not accessor keywords.
- Two `__proto__: value` definitions in an object literal are a SyntaxError unless the literal is a pattern (the
  literal is flagged like `{ x = 1 }`, which is only valid as a pattern).
- A `"use strict"` directive applies to the token already peeked after it (`Parser.applyStrictToPeekedToken`):
  `"use strict"; public = 1` is a SyntaxError.
- `in` is allowed in a computed property name inside a `for` head.
- test262: 19 more files pass; 25,802 of 42,616 in-scope files (60.5%).
- Left: shorthand and computed `__proto__` defining an own property (needs a compile-time marker; plan 1.11); the
  runtime name inference of object literal values names any anonymous function stored in a property
  (`fn-name-cover`); `with` and `Symbol.unscopables` (1.8); `super` and class tests (1.7).

### Spread arguments and for-of IteratorClose (2026-10-09, plan 1.4a)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.15):

- Spread arguments in calls and `new` (`f(...a)`, `o.m(...a)`, `new F(...a)`) work in both backends (they did not
  compile in either): `IRFactory` turns the arguments into one array literal and marks the call with
  `Node.SPREAD_ARGS_PROP`; the interpreter has `Icode.CALL_SPREAD`/`CALL_SPREAD_ON_SUPER`/`NEW_SPREAD`, compiled
  code builds the argument array with `ScriptRuntime.spreadArguments`. A direct `eval(...args)` is an ordinary call.
- `for-of` iterates with `IteratorRecord` (array fast path, `next` read once, TypeError for a non-object result) and
  closes its iterator when left early: `NodeTransformer` emits `Token.ENUM_CLOSE` before a `break`, `continue` or
  `return` that leaves the loop, and the loop is wrapped in a `try`/`finally` whose `ENUM_CLOSE_ABRUPT` closes it
  after a throw. Comprehensions are unchanged. Host objects that are not `ScriptableObject`s keep the old checks
  (no symbol keys, or no `Symbol.iterator`: "is not iterable").
- test262: 85 more files pass; 25,783 of 42,616 in-scope files (60.5%).

### Destructuring evaluation order and for heads (2026-10-09, plan 1.3d)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.14):

- In assignment patterns, a property target (`o.p`, `o[k]`) is evaluated before the iterator is stepped or the
  source property read; computed keys are evaluated in order, right before their property (they were all
  evaluated first, in the pattern's `LET`). A property target with a default is accepted (`[o.p = 1] = …`).
- `for ({ x = 1 } of …)`: an object assignment pattern with defaults is accepted as a for-in/of head (it was a
  SyntaxError); `for ({ x = 1 }; …)` stays one.
- `for (let …)` heads: names bound by nested patterns (`for (let [{ x }] = …)`) are declared in the loop's let
  scope (they were unresolved: a ReferenceError in strict code, a global variable otherwise). `IRFactory.createFor`
  makes the let scope the loop's parent scope; `NodeTransformer.visitLet` no longer swaps it again (which made
  the scope its own parent, and name lookups loop forever).
- test262: 64 more files pass; 25,698 of 42,616 in-scope files (60.3%).

### Object patterns and default names (2026-10-09, plan 1.3c)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.14):

- Object patterns start with a new IR operation `Token.REQUIRE_OBJECT` (RequireObjectCoercible, after a parameter's
  default value): `null`/`undefined` throw a TypeError, also for an empty pattern.
- An anonymous function given as the default of a binding in a pattern is named after the binding
  (`Parser.nameAnonymousFunction`, once the function is transformed, so that the name is not a binding inside it).
- Fixed `for (const x = …; …; …)`: the const initializer was wrapped in an expression statement, which the
  interpreter's code generator rejected (any such loop crashed at compile time).
- test262: 328 more files pass; 25,634 of 42,616 in-scope files (60.2%).

### Iterators of array patterns are closed on abrupt completion (2026-10-08, plan 1.3b)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.13):

- A statement containing array patterns (expression statement, declaration, loop, `try`, parameter list) is
  wrapped by `IRFactory.guardIterators` in `LOCAL_BLOCK { ITER_GUARD_INIT; try { … } finally { ITER_GUARD_CLOSE } }`.
  Its `ITER_OPEN`s link their `IteratorRecord` into the guard local (array fast path excepted); the `finally`
  closes the records that are not done, ignoring errors of `return()`. After a normal completion all records are
  done, so the guard costs a `try`/`finally` and no allocation. Statements without array patterns are unchanged.
- test262: 4 more files pass (most abrupt-completion tests also need 1.3c); 25,306 of 42,616 in-scope files (59.4%).

### Array destructuring follows the iterator protocol (2026-10-08, plan 1.3a)

All versions from `VERSION_ES6` (`BACKWARD_COMPATIBILITY.md` 2.13):

- Array patterns in every context are desugared to new IR operations `Token.ITER_OPEN`, `ITER_STEP`, `ITER_REST`
  and `ITER_CLOSE` (both backends; `Icode` and `BodyCodegen`), implemented by `IteratorRecord` (GetIterator,
  IteratorStepValue, IteratorClose). This replaces index-based access and the separate, partial iterator path of
  parameters (which skipped holes). Plain arrays with the original `Array.prototype[Symbol.iterator]` and
  `%ArrayIteratorPrototype%.next` are read by index (no iterator object; no performance change).
- A default applies when the element is `undefined`; the desugaring no longer reads the target first.
- test262: 298 more files pass; 25,302 of 42,616 in-scope files (59.4%).
- `NativeArrayIterator` reads holes through the prototype chain. Found: `NativeArray.ITERATOR_DESCRIPTOR` is null
  (`Symbol.iterator` is an alias of `values`), so the typed-array constructor's array fast path never runs; left as
  is (enabling it would change typed-array results for holes).
- Next: closing the iterator on abrupt completion (1.3b); object patterns (1.3c). Found: `yield` in an array
  literal swallows the following elements (`[yield 1, b]`), left to 1.6.

### Temporal dead zone of parameters (2026-10-08, plan 1.2f)

Under `VERSION_ECMASCRIPT` (`BACKWARD_COMPATIBILITY.md` 1.2b):

- A default parameter value reading its own parameter or a later one throws a ReferenceError (`IRFactory`
  replaces such reads in the desugared default by the TDZ error node; closures are not affected).
- test262: 14 more files pass; 25,004 of 42,616 in-scope files (58.7%).
- The rest of the planned 1.2f (separate parameter/body environments, eval lexical environment, immutable
  function-expression name, `new.target` in eval) becomes 1.2g, after 1.3.

### Eval code declarations (2026-10-08, plan 1.2e)

All versions (`BACKWARD_COMPATIBILITY.md` 2.12):

- A sloppy direct eval in a parameter list may not declare a `var`/function named like a parameter or `arguments`
  (SyntaxError). The parser marks such calls (`Node.SPECIALCALL_EVAL_IN_PARAMS`); the eval code is compiled with
  the forbidden names (`CompilerEnvirons.setEvalForbiddenVarNames`), checked against its top-level symbols.
- An indirect eval is never strict mode code.

Under `VERSION_ECMASCRIPT` (`BACKWARD_COMPATIBILITY.md` 1.4):

- A `var`/function of sloppy eval code may not reuse the name of a `let`/`const` of an enclosing block or of a
  global lexical declaration (SyntaxError).
- Global functions and vars of eval code are checked (CanDeclareGlobalFunction/Var) before any is created; a
  function over a configurable global redefines it.
- test262: 113 more files pass (`language/eval-code`); 24,990 of 42,616 in-scope files (58.6%).
- `indirect/lex-env-distinct-let.js` and `-const.js` now fail in strict mode too: they passed only because indirect
  eval inherited strictness. Eval code's own lexical environment is plan 1.2g. Async variants wait for M3.

### Function.prototype.toString (2026-10-08, plan 1.2d)

All versions (`BACKWARD_COMPATIBILITY.md` 2.11):

- The source text of a method, getter or setter starts at its key (`get f() {}`, `*g() {}`, `[k]() {}`).
- `Function`/`GeneratorFunction` build their source as CreateDynamicFunction does (`"\n) {\n"` and `"\n}"`),
  so a `//` comment ending the parameters works; `function*` prefix. Error line numbers in the body move by 2.
- `Function.prototype.toString` on a callable proxy gives a NativeFunction string; a built-in whose name is not an
  identifier leaves it out.
- Removed the unused internal `[Symbol.getSize]` property of `Map.prototype` and `Set.prototype`.
- test262: 16 more files pass; 24,877 of 42,616 in-scope files (58.4%). Class members wait for 1.7.

### Function early errors (2026-10-08, plan 1.2c)

All versions (simplest; `BACKWARD_COMPATIBILITY.md` 2.7 and 2.10):

- The parameter list is checked once the body is parsed (`Parser.checkParamsAfterBody`), so a `"use strict"` body
  applies to it: `eval`/`arguments` as the function's name or a parameter, and duplicate parameters, are SyntaxErrors.
  Duplicates are also rejected in methods and with a non-simple list (defaults, rest, patterns).
- `"use strict"` with a destructured parameter is a SyntaxError; a strict `eval = …` / `arguments = …` is reported by
  the parser (a SyntaxError, also from the `Function` constructor, instead of an `InternalError`).
- `Function.prototype.arguments`: a TypeError for strict functions and built-ins, like `caller`.
- Rest parameters with a binding pattern (`...[a, b]`, `...{length}`), in functions and arrows; destructured
  parameters are bound left to right.
- `arguments` used only in a default value is available.
- test262: 83 more files pass (`language/statements|expressions/function`, `arrow-function`, `built-ins/Function`,
  `rest-parameters`); 24,861 of 42,616 in-scope files (58.3%).
- Left for 1.2f: default-parameter TDZ, the separate body scope, `eval` in parameters, the immutable name of a
  function expression. Left for 1.6: `yield` as an identifier in sloppy code.

### The arguments object (2026-10-08, plan 1.2b)

All versions (simplest; `BACKWARD_COMPATIBILITY.md` 2.9):

- `Arguments` follows the spec's arguments exotic object: the indexed values are ordinary own data properties created
  with the object, and a separate parameter map links each index to its parameter. `[[Get]]`, `[[Set]]`,
  `[[GetOwnProperty]]`, `[[DefineOwnProperty]]` and `[[Delete]]` follow ECMA-262 10.4.4 (a redefinition keeps the
  attributes it does not mention; making an index non-writable or an accessor, or deleting it, unmaps it).
- Functions with a rest parameter get an unmapped `arguments` object.
- The indexed values stay in the argument array until an index is redefined or deleted (a call with 2^24
  arguments must not create 2^24 slots); only that index becomes a slot.
- test262: 30 more files pass (`language/arguments-object`, `rest-parameters`, `built-ins/Object`); 24,778 of
  42,616 in-scope files (58.1%).
- Found while testing, left for plan 1.4: spread arguments in calls (`f(...a)`) are not implemented by the
  interpreter (compiled mode only).

### Strict function semantics (2026-10-08, plan 1.2a)

All versions (simplest; documented in `BACKWARD_COMPATIBILITY.md` 2.6–2.8):

- `typeof this` in a strict function called without a receiver is `"undefined"` (the internal
  `Undefined.SCRIPTABLE_UNDEFINED` placeholder was reported as `"object"`).
- `call`/`apply` pass a primitive `this` unboxed to a strict script function (not to built-ins)
  (`ScriptRuntime.getApplyOrCallThisValue`), in both backends.
- `Function.prototype.caller` accessor: a TypeError for strict functions and built-ins, `undefined` for other functions;
  bound functions no longer have an own `caller`.
- `bind`: the bound name is `"bound " +` the target's `name` property read at bind time; `bind()` /
  `bind(undefined)` keep `this` undefined for a strict target.
- test262: 123 more files pass (`language/function-code`, `built-ins/Function`); 24,748 of 42,616 in-scope files
  (58.1%).
- Gaps: getters/methods called on a primitive and `bind` to a primitive still box `this`; `Function.prototype`'s
  `caller`/`arguments` accessors are not the realm's %ThrowTypeError%; bound `length` beyond `int`.

### Global lexical declarations (2026-10-07, plan 1.1e)

- **Gated, `VERSION_ECMASCRIPT`:** the top-level `let`/`const` of a script (not of `eval` code) are declared in the
  `TopLevel` scope, the declarative record of the global environment, instead of on the global object. A script
  starts with a `GLOBAL_LEXICALS` operation (new token/icode) that checks them against earlier declarations
  (`let`/`const`, `var`s and functions, non-configurable globals) and throws a SyntaxError on a collision, then
  declares them uninitialized (temporal dead zone); `initScript` rejects a `var`/function named like a global
  `let`/`const`. Global `const` assignment and the global TDZ follow (1.1c, 1.1d). `TopLevel` routes `put`/`putConst`
  to its own map only once a lexical declaration exists.
- Documented in `BACKWARD_COMPATIBILITY.md` (1.3).
- Destructuring with defaults in a `let`/`const` declaration (`let [x = 1] = a`) no longer reads the new binding
  before assigning it (the desugaring tested `x === undefined`, always true for a new binding): no behavior change
  under `VERSION_ES6`, and no spurious temporal-dead-zone error under `VERSION_ECMASCRIPT`. Names created by a
  desugaring are never checked for the temporal dead zone.
- test262: 25 more files pass (global `let`/`const` declarations and their TDZ, cross-script declarations, global
  destructuring declarations, destructuring assignments to consts); 24,625 of 42,616 in-scope files (57.8%).

### Temporal dead zone (2026-10-07, plan 1.1d)

- **Gated, `VERSION_ECMASCRIPT`:** using a `let`/`const` binding before its declaration has run throws
  `ReferenceError: Cannot access "x" before initialization.` (read, write, `typeof`, `++`/`--`, call), in both
  backends. The compiler classifies each reference (`NodeTransformer.classifyTdz`): after the declaration in the same
  function, nothing is checked; textually before it, the reference always throws; otherwise (closures, `with`,
  `switch` cases) the binding holds `UniqueTag.UNINITIALIZED` until its declaration runs, and the name lookups in
  `ScriptRuntime` throw on it (one comparison). Bindings in registers never hold it, so their number
  specialization is unaffected.
- **All versions from `VERSION_ES6` (bug fix, `BACKWARD_COMPATIBILITY.md` 2.5):** `let x;` in a loop body is
  `undefined` on each iteration in functions without closures (it kept the previous iteration's value).
- New tokens/icodes `TDZ_SENTINEL` and `CHECK_TDZ`; symbols now record their declaration (`Symbol.getNode()`), and a
  block's symbol table is kept after its scope object is created. Only bindings declared by a `let`/`const`
  statement start uninitialized (parameters, catch parameters and desugared bindings are initialized before their
  scope runs); destructuring declarations are not covered yet (plan 1.3).
- test262: 18 more files pass (`let`/`const` before-initialization tests, `for-in`/`for-of` head TDZ); 24,600 of
  42,616 in-scope files.

### Backward compatibility document (2026-10-07)

- `BACKWARD_COMPATIBILITY.md` lists every change that makes code or an embedding behave differently from stock
  Rhino 1.9.0: changes gated by `VERSION_ECMASCRIPT`, bug fixes that also apply to `VERSION_ES6`, and platform
  changes, each with an example, the spec section and how to keep the old behavior. `RELEASE-NOTES.md` points to it.

### Assignment to a const (2026-10-07, plan 1.1c)

- **Gated, `VERSION_ECMASCRIPT`:** assigning to a `const` binding throws `TypeError: Assignment to constant variable
  "x".`, also outside strict mode, for every form of assignment (`=`, compound, `++`/`--`, destructuring, `for-in/of`
  targets) and in both backends. Checked where the assignment already had no effect, so other assignments pay
  nothing: a const register (both backends), and a read-only slot of a scope object (which is always a const).
  Global consts (top-level `const` of a script) are covered with 1.1e.
- **`VERSION_ES6`:** assigning to a block-level `const` used to succeed (its block scope stored it as a plain
  variable); it now has no effect, like every other const (TypeError in strict mode, as before).
- Documented in the `VERSION_ECMASCRIPT` Javadoc and `BACKWARD_COMPATIBILITY.md`.
- test262: 2 more files pass (the other const-assignment tests use a global const, see 1.1e).

### Lexical declarations in loops (2026-10-07, plan 1.1a/1.1b)

Bug fixes, for every language version from `VERSION_ES6` on (decided 2026-10-07):

- **`const` in a loop body takes each iteration's value.** `for (...) { const c = i; }` kept the first value of `c`
  in functions without closures (`0,0,0` instead of `0,1,2`), a JavaScript 1.x const behavior. Code compiled for
  versions before `VERSION_ES6` keeps it.
- **`for (const x of/in ...)`** initializes `x` on each iteration; it stayed `undefined` in functions without
  closures (a `NaN` sum, for example).
- **Per-iteration bindings** for `let`/`const` loop heads: closures created in `for (let i ...)`, `for (let k in
  ...)` or `for (const v of ...)` see their own iteration's value (`0,1,2` instead of `3,3,3`). Implemented with a
  new `COPY_SCOPE` operation that copies the loop's scope object; loops whose bindings live in registers (no
  closures) are unaffected and pay nothing.
- Compiled functions now record the language version they were compiled with (`JSDescriptor`); it was always 0.
- test262: 16 more files pass (`for`, `for-in`, `for-of`, `let`, `block-scope`, and three tests that use such
  loops); in-scope pass rate 57.6% → 57.7%. Committed before the perf gate could run (see `MONFLABS_PLAN.md`).

### Performance gate: longer warmup, backup guard (2026-10-07)

- 20 warmup runs instead of 5 (with shorter runs, `PERF_SCALE` 0.2/0.5): with 5, compiled-mode scripts settled in
  one of two JIT states from one JVM to the next and the gate reported false regressions. A gate run now takes about
  18 minutes. The tolerances in `perf-tolerances.properties` were calibrated with the old settings and still need
  recalibrating with the new ones.
- The gate refuses to run while a Time Machine backup is running (`PERF_ALLOW_BACKUP=1` overrides).

### Gating plumbing for `VERSION_ECMASCRIPT` (2026-10-07)

- `Context.isLatestEcmaScript()` and `CompilerEnvirons.isLatestEcmaScript()`: the single check for behavior that is
  only enabled under `Context.VERSION_ECMASCRIPT` (runtime and compile time respectively), so that every gated
  behavior can be found with one search.
- The Javadoc of `VERSION_ES6`/`VERSION_ECMASCRIPT` now carries the list of behavior changes. It is empty: the audit
  found that nothing in the engine currently behaves differently between the two versions (the previous Javadoc
  mentioned `const` and strict-mode enforcement, but no code tested for it).
- Test helpers `Utils.assertWithAllModes_ECMASCRIPT` and `Utils.assertGatedBehavior(es6, ecma, script)`.
- No behavior change.

### test262 harness upgrades (2026-10-07)

- **A test listed as failing that now passes fails the run** by default, so conformance only moves forward
  (`-Dtest262.allowUnexpectedPass` for exploratory runs). Regenerating `test262.properties` never fails on it.
- **`async` tests run**: `doneprintHandle.js` is loaded, `print` output is captured per test, the microtask queue is
  drained after the test, and only a `$DONE` completion passes. 458 tests that were skipped now run: 308 pass, 150
  fail (mostly tests that use async functions without declaring the feature) and are listed in `test262.properties`.
- **Per-test time limit** of 60 seconds (`-Dtest262.timeout=<seconds>`), enforced through the instruction observer in
  both modes; scripts cannot catch it.
- On a JDK other than the reference one (25), the Unicode-version-dependent tests run but their outcome is not
  checked, so the Java 21 CI job does not fail on Unicode 15/16 data.
- `test262.*` system properties now reach the test JVM.
- In-scope baseline: 24,564 of 42,616 test files pass (57.6%, was 56.9%). No engine change.

### Performance gate (2026-10-07)

- `buildtools/perf-gate.sh [base-ref]` compares the working tree with a base revision (default: the `perf-baseline`
  tag) on 116 metrics, and fails on a regression beyond each metric's tolerance: startup, compile time of a large
  script, and steady-state runs of 20 micro-benchmarks (ported from Nashorn's perf gate) and 7 V8 benchmarks, each in
  interpreted and compiled mode and under `VERSION_ES6` and `VERSION_ECMASCRIPT`. Base and head run the same
  harness (`benchmarks/src/perf`) in interleaved rounds; only the engine jar differs.
- Tolerances (`benchmarks/testsrc/perf-tolerances.properties`) are calibrated by `buildtools/perf-calibrate.py`
  from 12 recordings of identical code; most are 10–20%. Validated in both directions: identical code passes, and a
  deliberate `instanceof` slowdown of +15–19% fails.
- Findings for the final performance pass are recorded in `MONFLABS_PLAN.md` (M14). No engine change.

### Android integration tests removed (2026-10-07)

- The `it-android` module (Rhino's integration tests on an Android emulator) and the Android CI workflow are
  removed, since Android is not a target of this fork. No engine change.

### Bazel build removed (2026-10-07)

- The optional Bazel build that upstream added in March 2026 (as a faster, cacheable alternative for running the
  tests) is removed: `MODULE.bazel`, `.bazelrc`, every `BUILD.bazel`, the `bazel/` rules, `maven_install.json`, the
  Bazel CI workflow, the top-level `rhino-test.config` copy that only Bazel read, and the Bazel-only test support
  (`testutils` `Sharding` and the Bazel runfiles resolver in `TestSource`). Gradle is the only build.

### Java 21 minimum, Java 25 reference for test262 (2026-10-07)

- **Breaking for embedders:** the engine now compiles with `--release 21` (was 17) and requires Java 21 or later.
  This fork targets Java server applications; Android and micro JVMs are no longer supported. The OSGi bundles
  now require `JavaSE` 21 (they declared 11), and `rhino-kotlin` builds with a Java 21 toolchain.
- `test262.properties` is now generated on Java 25 (`RHINO_TEST_JAVA_VERSION=25`, enforced by the build and by CI),
  because Unicode data comes from the running JDK; it was generated on Java 17 before. Regenerating it removed 54
  expected failures (Unicode 14–16 identifier tests and RegExp `Script`/`General_Category` property escapes): the
  in-scope baseline is now 24,256 of 42,616 (56.9%).

### Plan and conformance tooling (2026-10-07)

- `MONFLABS_PLAN.md`: the milestone plan to full ECMAScript 2026 (ECMA-262 17th edition), with the scope, ordering,
  performance and decision rules agreed on 2026-10-07.
- `tests/test262-status.py`: reports the test262 pass rate per feature tag and per directory from the test262 front
  matter and `test262.properties`, restricted to the in-scope tests. Baseline: 24,202 of 42,616 in-scope test files
  pass (56.8%).
- No engine change.

### Package and Maven coordinates renamed (2026-10-07)

- **Breaking for embedders:** every package moved from `org.mozilla.*` to `org.monflabs.mozilla.*`, the Java module
  names from `org.mozilla.rhino*` to `org.monflabs.mozilla.rhino*`, the OSGi bundle symbolic names likewise, and
  the Maven group id from `org.mozilla` to `org.monflabs.mozilla`. This lets the fork and upstream Rhino live on the
  same classpath or module path. Migration: replace `org.mozilla.` with `org.monflabs.mozilla.` in imports, in
  `Packages.org.mozilla...` script references, and in service and dependency declarations.
- `AGENTS.md` documents the conformance goal and the `VERSION_ECMASCRIPT` gating rule.
