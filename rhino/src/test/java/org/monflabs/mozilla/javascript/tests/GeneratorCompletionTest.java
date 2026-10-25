/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** A generator that completes gives its return value; a legacy one throws StopIteration. */
class GeneratorCompletionTest {

    @Test
    void returnValues() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "{\"value\":1,\"done\":false} {\"value\":8,\"done\":true} {\"done\":true}"
                        + " fin {\"value\":9,\"done\":true} boom {\"value\":6,\"done\":true} 1,2",
                "var r = [];"
                        + "function* g() { var x = yield 1; return x * 2; }"
                        + "var it = g();"
                        + "r.push(JSON.stringify(it.next()), JSON.stringify(it.next(4)),"
                        + "  JSON.stringify(it.next()));"
                        + "function* h() { try { yield 1; } finally { r.push('fin'); } }"
                        + "var i2 = h(); i2.next(); r.push(JSON.stringify(i2.return(9)));"
                        + "function* e() { throw new Error('boom'); }"
                        + "try { e().next(); } catch (x) { r.push(x.message); }"
                        + "function* d() { return yield* g(); }"
                        + "var i3 = d(); i3.next(); r.push(JSON.stringify(i3.next(3)));"
                        + "r.push([...(function* () { yield 1; yield 2; })()].join());"
                        + "r.join(' ')");
    }

    @Test
    void manyCompletions() {
        Utils.assertWithAllModes_ECMASCRIPT(
                30000,
                "function* g(i) { return i; }"
                        + "var n = 0; for (var j = 0; j < 10000; j++) n += g(3).next().value;"
                        + "n");
    }

    @Test
    void legacyGeneratorThrowsStopIteration() {
        Utils.assertWithAllModes(
                Context.VERSION_1_7,
                null,
                "1 true true [object StopIteration] 1",
                "var r = [];"
                        + "function g() { yield 1; }"
                        + "var it = g(); r.push(it.next());"
                        + "try { it.next(); } catch (e) {"
                        + "  r.push(e === StopIteration, e instanceof StopIteration, String(e)); }"
                        + "function g2() { yield 1; return; }"
                        + "var n = 0; for (var v in g2()) n += v; r.push(n);"
                        + "r.join(' ')");
    }

    @Test
    void manyYieldStarsOfDelegatesDoneAtOnce() {
        // the generator resumed itself through a Java call for each: a stack overflow
        Utils.assertWithAllModes_ES6(
                "200000",
                "function* empty() { return 1; }"
                        + "function* loop(n) { var s = 0; for (var i = 0; i < n; i++) s += yield* empty();"
                        + "  return s; }"
                        + "String(loop(200000).next().value)");
    }

    @Test
    void returnThatLeavesAScopeOrAFinallyBlock() {
        // the value of the return was evaluated after leaving the loop's scope and running the
        // finally block
        Utils.assertGatedBehavior(
                "{\"value\":1,\"done\":true} [\"f,e\",1] ReferenceError",
                "{\"value\":1,\"done\":true} [\"e,f\",1]"
                        + " [{\"value\":9,\"done\":false},{\"value\":3,\"done\":true},{\"done\":true}]",
                "function t(f) { try { return JSON.stringify(f()); } catch (e) { return e.name; } }"
                        + "[t(() => { function* g() { for (const x of [1, 2]) { return x; } }"
                        + "  return g().next(); }),"
                        + " t(() => { var log = [];"
                        + "  function* g() { try { return (log.push('e'), 1); } finally { log.push('f'); } }"
                        + "  var r = g().next(); return [log.join(), r.value]; }),"
                        + " t(() => { function* g() { for (const x of [3]) {"
                        + "  try { return x; } finally { yield 9; } } }"
                        + "  var it = g(); return [it.next(), it.next(), it.next()]; })].join(' ')");
    }

    @Test
    void yieldStarInACatchBlockThatAThrowRuns() {
        // the result of the yield* was the internal YieldStarResult
        Utils.assertGatedBehavior(
                "[{\"value\":1,\"done\":false},{\"done\":false},{\"value\":3,\"done\":false},"
                        + "{\"done\":true}]",
                "[{\"value\":1,\"done\":false},{\"value\":\"x!\",\"done\":false},"
                        + "{\"value\":3,\"done\":false},{\"done\":true}]",
                "function* g() { try { yield 1; } catch (e) { yield* [e + '!']; } yield 3; }"
                        + "var it = g();"
                        + "JSON.stringify([it.next(), it.throw('x'), it.next(), it.next()])");
    }

    /** Before VERSION_ECMASCRIPT, the value of the yield* was lost (compiled) or a TypeError. */
    @Test
    void yieldStarInAFinallyBlockThatAReturnRuns() {
        Utils.assertWithAllModes_ECMASCRIPT(
                "[{\"value\":1,\"done\":false},{\"value\":7,\"done\":false},"
                        + "{\"value\":8,\"done\":false},{\"value\":5,\"done\":true}]",
                "function* g() { try { yield 1; } finally { yield* [7, 8]; } }"
                        + "var it = g();"
                        + "JSON.stringify([it.next(), it.return(5), it.next(), it.next()])");
    }
}
