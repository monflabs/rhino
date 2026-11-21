/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.monflabs.mozilla.javascript.CompilerEnvirons;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.IRFactory;
import org.monflabs.mozilla.javascript.Node;
import org.monflabs.mozilla.javascript.Parser;
import org.monflabs.mozilla.javascript.ast.AstRoot;
import org.monflabs.mozilla.javascript.ast.ScriptNode;

/**
 * The number of IR nodes that {@link IRFactory} produces for a few constructs must stay within a
 * budget: a lowering that makes a tree grow has to be noticed.
 */
class IrSizeTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "53 | function f(x) { var [a, b = 1, ...c] = x; }",
                "40 | function f(y) { for (var x of y) { if (x) break; } }",
                "46 | async function f(y) { for await (var x of y) { if (x) break; } }",
                "46 | class C { constructor() {} m() {} f = 1; #p() {} static { 1; } }",
                "15 | function f(a = 1) {}",
                "24 | function f(x) { switch (x) { case 1: let y = 2; return y; } }",
            })
    void irNodeCount(int budget, String source) {
        int count = irNodeCount(source);
        assertTrue(count <= budget, count + " IR nodes, over the budget of " + budget);
    }

    private static int irNodeCount(String source) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            CompilerEnvirons env = new CompilerEnvirons();
            env.initFromContext(cx);
            AstRoot ast = new Parser(env).parse(source, "test", 1);
            ScriptNode tree = new IRFactory(env, source).transformTree(ast);
            return count(tree);
        }
    }

    private static int count(Node node) {
        int count = 1;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            count += count(child);
        }
        if (node instanceof ScriptNode) {
            ScriptNode script = (ScriptNode) node;
            for (int i = 0; i < script.getFunctionCount(); i++) {
                count += count(script.getFunctionNode(i));
            }
        }
        return count;
    }
}
