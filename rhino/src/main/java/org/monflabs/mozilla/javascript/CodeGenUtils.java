package org.monflabs.mozilla.javascript;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.monflabs.mozilla.javascript.ast.AstNode;
import org.monflabs.mozilla.javascript.ast.AstRoot;
import org.monflabs.mozilla.javascript.ast.Block;
import org.monflabs.mozilla.javascript.ast.FunctionNode;
import org.monflabs.mozilla.javascript.ast.Scope;
import org.monflabs.mozilla.javascript.ast.ScriptNode;

/**
 * Common utilities usable by all the compilers for populating {@link JSDescriptor.Builder} entries
 * from IR nodes.
 */
public class CodeGenUtils {

    /** Populates builder data for a nested function. */
    public static void fillInForNestedFunction(
            JSDescriptor.Builder<JSFunction> builder,
            JSDescriptor.Builder<?> parent,
            FunctionNode fn) {
        final AstNode fnParent = fn.getParent();
        if (!(fnParent instanceof AstRoot
                || fnParent instanceof Scope
                || fnParent instanceof Block)) {
            builder.declaredAsFunctionExpression = true;
            boolean isArrow = fn.getFunctionType() == FunctionNode.ARROW_FUNCTION;
            builder.hasLexicalThis = isArrow;
            builder.hasPrototype = !isArrow;
            if (!isArrow) {
                builder.constructor = builder.code;
            } else {
                builder.constructor = new JSCode.NullBuilder<JSFunction>();
            }
        } else {
            builder.hasLexicalThis = false;
            builder.hasPrototype = true;
            builder.constructor = builder.code;
        }

        fillInForFunction(builder, fn);
    }

    private static void fillInForFunction(JSDescriptor.Builder builder, FunctionNode fn) {
        builder.functionType = fn.getFunctionType();
        builder.requiresActivationFrame = fn.requiresActivation();
        builder.requiresArgumentObject = fn.requiresArgumentObject();
        builder.paramIsRegister = fn.getRegisterVars();
        builder.paramIsActivationVar = fn.getActivationVars();
        if (fn.getFunctionName() != null) {
            builder.name = fn.getName();
        }
        if (fn.isInStrictMode()) {
            builder.isStrict = true;
        }
        if (fn.isES6Generator()) {
            builder.isES6Generator = true;
        }
        if (fn.isShorthand()) {
            builder.isShorthand = true;
        }
        if (fn.isNamedByLiteral()) {
            builder.namedByLiteral = true;
        }
        if (fn.isClassConstructor()) {
            builder.classConstructor = true;
        }
        if (fn.isDerivedConstructor()) {
            builder.derivedConstructor = true;
        }
        if (fn.hasSelfNameBinding()) {
            builder.selfNameBinding = true;
        }
        if (fn.isFieldInitializer()) {
            builder.fieldInitializer = true;
        }
        if (fn.isAsyncGenerator()) {
            builder.isAsync = true;
            builder.isAsyncGenerator = true;
        } else if (fn.isAsync()) {
            // an async function has no prototype property and is not a constructor
            builder.isAsync = true;
            builder.hasPrototype = false;
        }
        builder.ignoresThis =
                fn.getFunctionType() != FunctionNode.ARROW_FUNCTION
                        && !fn.isGenerator()
                        && !fn.isES6Generator()
                        && !fn.isAsync()
                        && !readsThis(fn);
        fillInCommon(builder, fn);
    }

    /**
     * Whether a function can read its this: a this, a super or a direct eval in its body or in its
     * arrow functions, which share its this.
     */
    public static boolean readsThis(FunctionNode fn) {
        return readsThis_r(fn);
    }

    private static boolean readsThis_r(Node node) {
        if (node instanceof ScriptNode) {
            ScriptNode scriptOrFn = (ScriptNode) node;
            for (int i = 0, n = scriptOrFn.getFunctionCount(); i != n; ++i) {
                FunctionNode nested = scriptOrFn.getFunctionNode(i);
                if (nested.getFunctionType() == FunctionNode.ARROW_FUNCTION
                        && readsThis_r(nested)) {
                    return true;
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            int type = child.getType();
            if (type == Token.THIS
                    || type == Token.SUPER
                    || ((type == Token.CALL || type == Token.NEW)
                            && child.getIntProp(Node.SPECIALCALL_PROP, Node.NON_SPECIALCALL)
                                    != Node.NON_SPECIALCALL)) {
                return true;
            }
            if (!(child instanceof ScriptNode) && readsThis_r(child)) {
                return true;
            }
        }
        return false;
    }

    /** Populate builder data for a top level function. */
    public static void fillInForTopLevelFunction(
            JSDescriptor.Builder builder,
            FunctionNode fn,
            String rawSource,
            CompilerEnvirons compilerEnv) {

        builder.hasPrototype = true;

        fillInTopLevelCommon(builder, fn, rawSource, compilerEnv);
        fillInForFunction(builder, fn);
    }

    /** Populate builder data for a top level script. */
    public static void fillInForScript(
            JSDescriptor.Builder builder,
            ScriptNode scriptOrFn,
            String rawSource,
            CompilerEnvirons compilerEnv) {
        builder.hasPrototype = false;

        fillInTopLevelCommon(builder, scriptOrFn, rawSource, compilerEnv);
        fillInCommon(builder, scriptOrFn);
    }

    private static void fillInTopLevelCommon(
            JSDescriptor.Builder builder,
            ScriptNode scriptOrFn,
            String rawSource,
            CompilerEnvirons compilerEnv) {
        builder.sourceFile = scriptOrFn.getSourceName();
        builder.sourceCodeProvider = compilerEnv.getSourceCodeProvider();
        builder.isTopLevel = true;
        builder.isScript = true;
        builder.isEvalFunction = compilerEnv.isInEval();
        builder.isStrict = scriptOrFn.isInStrictMode();
        builder.globalLexicals = (String) scriptOrFn.getProp(Node.GLOBAL_LEXICALS_PROP);
        builder.hasLexicalThis = false;
        builder.securityController = compilerEnv.securityController();
        builder.securityDomain = compilerEnv.securityDomain();
    }

    private static void fillInCommon(JSDescriptor.Builder builder, ScriptNode scriptOrFn) {
        builder.paramAndVarNames =
                disambiguateNames(scriptOrFn.getParamAndVarNames(), scriptOrFn.getParamCount());
        builder.paramCount = scriptOrFn.getParamCount();
        builder.paramIsConst = scriptOrFn.getParamAndVarConst();
        builder.paramIsLexical = scriptOrFn.getParamAndVarLexical();
        builder.paramAndVarCount = scriptOrFn.getParamAndVarCount();
        builder.hasRestArg = scriptOrFn.hasRestParameter();
        // a parameter list that is not simple (rest parameters aside): no mapped arguments object
        builder.hasDefaultParameters =
                scriptOrFn.getDefaultParams() != null
                        || (builder.languageVersion >= Context.VERSION_ECMASCRIPT
                                && scriptOrFn instanceof FunctionNode
                                && ((FunctionNode) scriptOrFn).hasDestructuredParams());

        // Calculate arity (function.length) - count params before first default
        builder.arity = FunctionNode.calculateFunctionArity(scriptOrFn);

        builder.sourceStart = scriptOrFn.getRawSourceStart();
        builder.sourceEnd = scriptOrFn.getRawSourceEnd();
    }

    /** Configure the constructor appropriately based on a function's type. */
    public static <T extends ScriptOrFn<T>> void setConstructor(
            JSDescriptor.Builder<T> builder, ScriptNode scriptOrFn) {
        if (scriptOrFn instanceof FunctionNode) {
            FunctionNode f = (FunctionNode) scriptOrFn;
            boolean isArrow = f.getFunctionType() == FunctionNode.ARROW_FUNCTION;
            if (isArrow || f.isMethodDefinition() || f.isGenerator()) {
                builder.constructor = new JSCode.NullBuilder<T>();
            } else {
                builder.constructor = builder.code;
            }
        } else {
            builder.constructor = new JSCode.NullBuilder<T>();
        }
    }

    /**
     * Disambiguate all variable names after the parameters. We avoid disambiguating the parameters
     * themselves because those names can be repeated and are not unique even if an activation frame
     * is used.
     *
     * <p>This is only required when an interpreted function might have duplicate local variable
     * names and might be run under the debugger. This can only occur if the function has nested
     * blocks, uses `let` with a variable name in that nested block which is also used outside that
     * block, and does not require the use of an activation frame for any other reason. When running
     * under the debugger variables are accessed by name rather than by index, and hence those names
     * must not include duplicates.
     */
    private static String[] disambiguateNames(String[] names, int paramCount) {
        String[] result = new String[names.length];
        var checkMap = new HashMap<String, AtomicInteger>();
        for (int i = 0; i < names.length; i++) {
            var counter = checkMap.computeIfAbsent(names[i], k -> new AtomicInteger());
            var count = counter.getAndIncrement();
            if (i >= paramCount && count > 0) {
                result[i] = String.format("%s(%d)", names[i], count);
            } else {
                result[i] = names[i];
            }
        }
        return result;
    }
}
