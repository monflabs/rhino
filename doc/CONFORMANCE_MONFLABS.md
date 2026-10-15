# test262 conformance of the MONFLABS fork

Under `Context.VERSION_ECMASCRIPT`, this fork passes the test262 suite in its scope, ES2026 without the exclusions of
[MONFLABS_PLAN.md](../MONFLABS_PLAN.md) (proper tail calls, `staging/`, `intl402/` and the proposals after ES2026),
except for the tests listed here. Each group has its reason: a documented decision, or a residual still to fix (plan
milestone M13). `tests/testsrc/test262.properties` lists every failing test; `python3 tests/test262-status.py` gives
the current numbers (98.9% of 42,616 tests on 2026-10-14).

## Documented limits

### Unicode data comes from the JDK (decision 4.3)

Unicode data (identifiers, case mapping, RegExp property escapes) comes from the running JDK, as in Nashorn; the fork
bundles no Unicode Character Database. On Java 25 (Unicode 16), this leaves:

- `built-ins/RegExp/property-escapes/generated` (269 tests): the code points added after the JDK's Unicode version,
  the binary properties the JDK does not carry, and `Script_Extensions`, which `java.lang.Character` does not expose.
- `built-ins/RegExp/unicodeSets` (39 tests): the properties of strings of the `v` flag (`\p{RGI_Emoji}`,
  `Basic_Emoji`, `Emoji_Keycap_Sequence`, ...), which need emoji sequence data the JDK does not have; they are a
  SyntaxError.
- `language/identifiers` (12 tests): the identifier characters of Unicode 17.

### Agents that cannot block (decision 3.4)

`built-ins/Atomics/wait/cannot-suspend-throws.js` and its BigInt variant are written for a host whose main agent
cannot block (`CanBlockIsFalse`); every agent of this fork can call `Atomics.wait`, as fits a server.

## Residuals still to fix (M13)

- Generator `return()` while paused inside a destructuring pattern (16 tests, `dstr/*-iter-rtrn-close-*`): the
  pattern's iterator is closed as for a throw (errors of `return` are ignored) instead of as for a return. The fix
  needs the completion kind in the `finally` blocks of both backends.
- The temporal dead zone of the head of `for-in`/`for-of` with `let`/`const`, and of `switch`/`catch` blocks seen by
  closures created in them (10 tests, `scope-*-lex-*`).
- `Array.prototype.push`/`unshift` on an array whose prototype has index setters (4 tests): the dense fast path
  writes the elements without looking at the prototype chain.
- Getters read on a primitive (`(5).x`) and assignments to a property of a primitive receive a wrapper object as
  receiver (4 tests); method calls on primitives already pass the primitive.
- Cross-realm behavior (about 10 tests): errors created in the realm of the function rather than the caller's,
  `%ThrowTypeError%` per realm, `Function.prototype.bind` and `apply` across realms.
- Smaller items: completion values of `break` in `finally`, evaluation order of keyed destructuring targets and of
  `super[expr]++`, private field set order, anonymous classes named by computed keys, proxy edge cases, `arguments`
  in direct eval inside arrow functions.
