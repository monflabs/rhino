/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.optimizer;

import java.util.List;
import java.util.Map;
import org.monflabs.mozilla.javascript.Kit;
import org.monflabs.mozilla.javascript.Node;
import org.monflabs.mozilla.javascript.NodeTransformer;
import org.monflabs.mozilla.javascript.Token;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.ScriptNode;

/**
 * This class performs node transforms to prepare for optimization.
 *
 * @see NodeTransformer
 * @author Norris Boyd
 */
class OptTransformer extends NodeTransformer {

    OptTransformer(
            ScriptNode root,
            Map<ScriptNode, Map<String, OptFunctionNode>> declarations,
            List<OptFunctionNode> directCallTargets) {
        this.root = root;
        this.declarations = declarations;
        this.directCallTargets = directCallTargets;
    }

    @Override
    protected void visitNew(Node node, ScriptNode tree) {
        detectDirectCall(node, tree);
        super.visitNew(node, tree);
    }

    @Override
    protected void visitCall(Node node, ScriptNode tree) {
        detectDirectCall(node, tree);
        super.visitCall(node, tree);
    }

    private void detectDirectCall(Node node, ScriptNode tree) {
        if (tree.getType() == Token.FUNCTION) {
            Node left = node.getFirstChild();

            // count the arguments
            int argCount = 0;
            if (left != null) {
                Node arg = left.getNext();
                while (arg != null) {
                    arg = arg.getNext();
                    argCount++;
                }
            }

            if (argCount == 0) {
                OptFunctionNode.get(tree).itsContainsCalls0 = true;
            }

            /*
             * Optimize a call site by converting call("a", b, c) into :
             *
             *  FunctionObjectFor"a" <-- instance variable init'd by constructor
             *
             *  // this is a DIRECTCALL node
             *  fn = GetProp(tmp = GetBase("a"), "a");
             *  if (fn == FunctionObjectFor"a")
             *      fn.call(tmp, b, c)
             *  else
             *      ScriptRuntime.Call(fn, tmp, b, c)
             */
            if (declarations != null) {
                String targetName = null;
                if (left.getType() == Token.NAME) {
                    targetName = left.getString();
                } else if (left.getType() == Token.GETPROP) {
                    targetName = left.getFirstChild().getNext().getString();
                } else if (left.getType() == Token.GETPROPNOWARN) {
                    throw Kit.codeBug();
                }
                if (targetName != null) {
                    OptFunctionNode ofn = findDeclaration(tree, targetName);
                    if (ofn != null
                            && node.getIntProp(Node.SPREAD_ARGS_PROP, 0) == 0
                            && argCount == ofn.fnode.getParamCount()
                            && !ofn.fnode.requiresActivation()
                            && !ofn.fnode.requiresArgumentObject()) {
                        // Refuse to directCall any function with more
                        // than 32 parameters - prevent code explosion
                        // for wacky test cases
                        if (argCount <= 32) {
                            node.putProp(Node.DIRECTCALL_PROP, ofn);
                            if (!ofn.isTargetOfDirectCall()) {
                                int index = directCallTargets.size();
                                directCallTargets.add(ofn);
                                ofn.setDirectTargetIndex(index);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The declaration of the function the name most likely holds in the scope of the call: the
     * nearest declaration in the enclosing functions. The call checks at run time that it got that
     * function.
     */
    private OptFunctionNode findDeclaration(ScriptNode tree, String name) {
        for (ScriptNode s = tree; ; ) {
            Map<String, OptFunctionNode> names = declarations.get(s);
            OptFunctionNode ofn = names == null ? null : names.get(name);
            if (ofn != null || s == root || !(s instanceof FunctionNode)) {
                return ofn;
            }
            FunctionNode enclosing = s.getEnclosingFunction();
            s = enclosing == null ? root : enclosing;
        }
    }

    private final ScriptNode root;
    private Map<ScriptNode, Map<String, OptFunctionNode>> declarations;
    private List<OptFunctionNode> directCallTargets;
}
