/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.optimizer;

import java.math.BigInteger;
import java.util.List;
import org.monflabs.mozilla.javascript.Callable;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.ES6Generator;
import org.monflabs.mozilla.javascript.JSFunction;
import org.monflabs.mozilla.javascript.JavaScriptException;
import org.monflabs.mozilla.javascript.Kit;
import org.monflabs.mozilla.javascript.NativeGenerator;
import org.monflabs.mozilla.javascript.NativeIterator;
import org.monflabs.mozilla.javascript.NewLiteralStorage;
import org.monflabs.mozilla.javascript.Script;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;

public final class OptRuntime extends ScriptRuntime {
    public static final Integer oneObj = Integer.valueOf(1);
    public static final Integer minusOneObj = Integer.valueOf(-1);

    /** Implement ....() call shrinking optimizer code. */
    public static Object call0(Callable fun, Scriptable thisObj, Context cx, VarScope scope) {
        return fun.call(cx, scope, thisObj, ScriptRuntime.emptyArgs);
    }

    public static Object call0Optional(
            Callable fun, Scriptable thisObj, Context cx, VarScope scope) {
        if (fun == null) {
            return Undefined.instance;
        }
        return call0(fun, thisObj, cx, scope);
    }

    /** Implement ....(arg) call shrinking optimizer code. */
    public static Object call1(
            Callable fun, Scriptable thisObj, Object arg0, Context cx, VarScope scope) {
        return fun.call(cx, scope, thisObj, new Object[] {arg0});
    }

    /** Implement ....(arg0, arg1) call shrinking optimizer code. */
    public static Object call2(
            Callable fun,
            Scriptable thisObj,
            Object arg0,
            Object arg1,
            Context cx,
            VarScope scope) {
        return fun.call(cx, scope, thisObj, new Object[] {arg0, arg1});
    }

    /** Implement ....(arg0, arg1, ...) call shrinking optimizer code. */
    public static Object callN(
            Callable fun, Scriptable thisObj, Object[] args, Context cx, VarScope scope) {
        return fun.call(cx, scope, thisObj, args);
    }

    /** Implement name(args) call shrinking optimizer code. */
    @Deprecated(since = "1.8.1", forRemoval = true)
    @SuppressWarnings("removal")
    public static Object callName(Object[] args, String name, Context cx, VarScope scope) {
        Callable f = getNameFunctionAndThis(name, cx, scope);
        Scriptable thisObj = lastStoredScriptable(cx);
        return f.call(cx, scope, thisObj, args);
    }

    /** Implement name() call shrinking optimizer code. */
    @Deprecated(since = "1.8.1", forRemoval = true)
    @SuppressWarnings("removal")
    public static Object callName0(String name, Context cx, VarScope scope) {
        Callable f = getNameFunctionAndThis(name, cx, scope);
        Scriptable thisObj = lastStoredScriptable(cx);
        return f.call(cx, scope, thisObj, ScriptRuntime.emptyArgs);
    }

    @Deprecated(since = "1.8.1", forRemoval = true)
    @SuppressWarnings("removal")
    public static Object callName0Optional(String name, Context cx, VarScope scope) {
        Callable f = getNameFunctionAndThisOptional(name, cx, scope);
        if (f == null) {
            return Undefined.instance;
        }
        Scriptable thisObj = lastStoredScriptable(cx);
        return f.call(cx, scope, thisObj, ScriptRuntime.emptyArgs);
    }

    /** Implement x.property() call shrinking optimizer code. */
    @Deprecated(since = "1.8.1", forRemoval = true)
    @SuppressWarnings("removal")
    public static Object callProp0(Object value, String property, Context cx, VarScope scope) {
        Callable f = getPropFunctionAndThis(value, property, cx, scope);
        Scriptable thisObj = lastStoredScriptable(cx);
        return f.call(cx, scope, thisObj, ScriptRuntime.emptyArgs);
    }

    @Deprecated(since = "1.8.1", forRemoval = true)
    @SuppressWarnings("removal")
    public static Object callProp0Optional(
            Object value, String property, Context cx, VarScope scope) {
        Callable f = getPropFunctionAndThisOptional(value, property, cx, scope);
        if (f == null) {
            return Undefined.instance;
        }
        Scriptable thisObj = lastStoredScriptable(cx);
        return f.call(cx, scope, thisObj, ScriptRuntime.emptyArgs);
    }

    public static Object add(Object val1, double val2, Context cx) {
        if (val1 instanceof Double) {
            return ((Double) val1) + val2;
        }
        if (val1 instanceof Integer) {
            return ((Integer) val1) + val2;
        }
        return ScriptRuntime.add(val1, val2, cx);
    }

    public static Object add(double val1, Object val2, Context cx) {
        if (val2 instanceof Double) {
            return val1 + ((Double) val2);
        }
        if (val2 instanceof Integer) {
            return val1 + ((Integer) val2);
        }
        return ScriptRuntime.add(val1, val2, cx);
    }

    /**
     * @deprecated Use {@link #elemIncrDecr(Object, double, Context, VarScope, int)} instead
     */
    @Deprecated
    public static Object elemIncrDecr(Object obj, double index, Context cx, int incrDecrMask) {
        return elemIncrDecr(obj, index, cx, getTopCallScope(cx), incrDecrMask);
    }

    public static Object elemIncrDecr(
            Object obj, double index, Context cx, VarScope scope, int incrDecrMask) {
        return ScriptRuntime.elemIncrDecr(obj, Double.valueOf(index), cx, scope, incrDecrMask);
    }

    public static Object[] padStart(Object[] currentArgs, int count) {
        Object[] result = new Object[currentArgs.length + count];
        System.arraycopy(currentArgs, 0, result, count, currentArgs.length);
        return result;
    }

    public static void initFunction(
            JSFunction fn, int functionType, boolean hoist, VarScope scope, Context cx) {
        ScriptRuntime.initFunction(cx, scope, fn, functionType, false);
        if (hoist) {
            while (scope.isNestedScope()) {
                scope = scope.getParentScope();
            }
            scope.put(fn.getFunctionName(), scope, fn);
        }
    }

    public static Object callSpecial(
            Context cx,
            Callable fun,
            Scriptable thisObj,
            Object[] args,
            VarScope scope,
            Scriptable callerThis,
            int callType,
            String fileName,
            int lineNumber,
            boolean isOptionalChainingCall) {
        return ScriptRuntime.callSpecial(
                cx,
                fun,
                thisObj,
                args,
                scope,
                callerThis,
                callType,
                fileName,
                lineNumber,
                isOptionalChainingCall);
    }

    public static Object newObjectSpecial(
            Context cx,
            Object fun,
            Object[] args,
            VarScope scope,
            Scriptable callerThis,
            int callType) {
        return ScriptRuntime.newSpecial(cx, fun, args, scope, callType);
    }

    /** The template of an array literal of constants (BodyCodegen#visitConstantLiteral). */
    private static final class ConstantArray {
        final Object[] elements;

        ConstantArray(Object[] elements) {
            this.elements = elements;
        }
    }

    /** The template of an object literal of constants: its keys (String or Integer) and values. */
    private static final class ConstantObject {
        final Object[] ids;
        final Object[] values;

        ConstantObject(Object[] ids, Object[] values) {
            this.ids = ids;
            this.values = values;
        }
    }

    /**
     * Parses a literal of constants that the class compiler encoded as text (too large for the code
     * and constant pool of a class): {@code [n,e...}, <code>{n,k v...</code>, {@code s<len>:},
     * {@code i<int>;}, {@code d<double>;}, {@code t}, {@code f}, {@code n}.
     */
    public static Object parseConstantLiteral(String[] chunks) {
        String text = chunks.length == 1 ? chunks[0] : String.join("", chunks);
        int[] pos = {0};
        Object result = parseConstant(text, pos);
        if (pos[0] != text.length()) {
            throw Kit.codeBug();
        }
        return result;
    }

    private static Object parseConstant(String text, int[] pos) {
        char c = text.charAt(pos[0]++);
        switch (c) {
            case '[':
                {
                    Object[] elements = new Object[parseCount(text, pos, ',')];
                    for (int i = 0; i < elements.length; i++) {
                        elements[i] = parseConstant(text, pos);
                    }
                    return new ConstantArray(elements);
                }
            case '{':
                {
                    int count = parseCount(text, pos, ',');
                    Object[] ids = new Object[count];
                    Object[] values = new Object[count];
                    for (int i = 0; i < count; i++) {
                        ids[i] = parseConstant(text, pos);
                        values[i] = parseConstant(text, pos);
                    }
                    return new ConstantObject(ids, values);
                }
            case 's':
                {
                    int length = parseCount(text, pos, ':');
                    String value = text.substring(pos[0], pos[0] + length);
                    pos[0] += length;
                    return value;
                }
            case 'i':
                return Integer.valueOf(parseCount(text, pos, ';'));
            case 'd':
                {
                    int end = text.indexOf(';', pos[0]);
                    double value = Double.parseDouble(text.substring(pos[0], end));
                    pos[0] = end + 1;
                    return Double.valueOf(value);
                }
            case 't':
                return Boolean.TRUE;
            case 'f':
                return Boolean.FALSE;
            case 'n':
                return null;
            default:
                throw Kit.codeBug();
        }
    }

    private static int parseCount(String text, int[] pos, char end) {
        int stop = text.indexOf(end, pos[0]);
        int value = Integer.parseInt(text.substring(pos[0], stop));
        pos[0] = stop + 1;
        return value;
    }

    /** A new object or array from the template of a literal of constants, as its code makes it. */
    public static Scriptable newConstantLiteral(Object template, Context cx, VarScope scope) {
        return (Scriptable) instantiate(template, cx, scope);
    }

    private static Object instantiate(Object template, Context cx, VarScope scope) {
        if (template instanceof ConstantArray) {
            Object[] elements = ((ConstantArray) template).elements;
            Object[] values = new Object[elements.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = instantiate(elements[i], cx, scope);
            }
            return ScriptRuntime.newArrayLiteral(values, null, cx, scope);
        }
        if (template instanceof ConstantObject) {
            ConstantObject object = (ConstantObject) template;
            Object[] values = new Object[object.values.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = instantiate(object.values[i], cx, scope);
            }
            Scriptable result = cx.newObject(scope);
            ScriptRuntime.fillObjectLiteral(
                    result, object.ids, values, new int[values.length], cx, scope);
            return result;
        }
        return template;
    }

    /**
     * A relational comparison {@code num op value} (Token.LT, LE, GT, GE) of a number with a value
     * of any type: a BigInt, a string or an object compares as ScriptRuntime#compare does.
     */
    public static boolean compareNumberWith(double num, Object value, int op) {
        if (value instanceof Number && !(value instanceof BigInteger)) {
            return ScriptRuntime.compareTo(num, ((Number) value).doubleValue(), op);
        }
        return ScriptRuntime.compare(wrapDouble(num), value, op);
    }

    /** A relational comparison {@code value op num}, see #compareNumberWith. */
    public static boolean compareWithNumber(Object value, double num, int op) {
        if (value instanceof Number && !(value instanceof BigInteger)) {
            return ScriptRuntime.compareTo(((Number) value).doubleValue(), num, op);
        }
        return ScriptRuntime.compare(value, wrapDouble(num), op);
    }

    public static Double wrapDouble(double num) {
        if (Double.isNaN(num)) {
            return NaNobj;
        }
        return Double.valueOf(num);
    }

    static String encodeIntArray(int[] array) {
        // XXX: this extremely inefficient for small integers
        if (array == null) {
            return null;
        }
        int n = array.length;
        char[] buffer = new char[1 + n * 2];
        buffer[0] = 1;
        for (int i = 0; i != n; ++i) {
            int value = array[i];
            int shift = 1 + i * 2;
            buffer[shift] = (char) (value >>> 16);
            buffer[shift + 1] = (char) value;
        }
        return new String(buffer);
    }

    private static int[] decodeIntArray(String str, int arraySize) {
        // XXX: this extremely inefficient for small integers
        if (arraySize == 0) {
            if (str != null) throw new IllegalArgumentException();
            return null;
        }
        if (str.length() != 1 + arraySize * 2 && str.charAt(0) != 1) {
            throw new IllegalArgumentException();
        }
        int[] array = new int[arraySize];
        for (int i = 0; i != arraySize; ++i) {
            int shift = 1 + i * 2;
            array[i] = (str.charAt(shift) << 16) | str.charAt(shift + 1);
        }
        return array;
    }

    public static Scriptable newArrayLiteral(
            Object[] objects, String encodedInts, int skipCount, Context cx, VarScope scope) {
        int[] skipIndexes = decodeIntArray(encodedInts, skipCount);
        return newArrayLiteral(objects, skipIndexes, cx, scope);
    }

    // Work around because our bytecode generator can't handle the
    // appropriate scaffolding for calling static interface methods.
    @SafeVarargs
    public static <E> List<E> listOf(E... items) {
        return List.of((E[]) items);
    }

    public static void main(final Script script, final String[] args) {
        ContextFactory.getGlobal()
                .call(
                        cx -> {
                            TopLevel global = getGlobal(cx);

                            // get the command line arguments and define "arguments"
                            // array in the top-level object
                            Object[] argsCopy = new Object[args.length];
                            System.arraycopy(args, 0, argsCopy, 0, args.length);
                            Scriptable argsObj = cx.newArray(global, argsCopy);
                            global.defineProperty("arguments", argsObj, ScriptableObject.DONTENUM);
                            script.exec(cx, global, global.getGlobalThis());
                            return null;
                        });
    }

    public static void throwStopIteration(Object scope, Object genState) {
        Object value = getGeneratorReturnValue(genState);
        Object si =
                (value == Undefined.instance)
                        ? NativeIterator.getStopIterationObject((VarScope) scope)
                        : new NativeIterator.StopIteration(value);
        if (si instanceof NativeIterator.StopIteration) {
            throw new NativeIterator.GeneratorCompletion((NativeIterator.StopIteration) si);
        }
        throw new JavaScriptException(si, "", 0);
    }

    public static Scriptable createNativeGenerator(
            Context cx,
            VarScope scope,
            Scriptable thisObj,
            JSFunction funObj,
            int maxLocals,
            int maxStack) {
        GeneratorState gs = new GeneratorState(scope, thisObj, maxLocals, maxStack);
        if (cx.getLanguageVersion() >= Context.VERSION_ES6) {
            ES6Generator generator = new ES6Generator(scope, funObj, gs);
            if (funObj.getDescriptor().isAsyncGenerator()) {
                return ScriptRuntime.createAsyncGenerator(scope, generator, funObj);
            }
            return funObj.getDescriptor().isAsync()
                    ? ScriptRuntime.startAsyncFunction(cx, scope, generator)
                    : generator;
        } else {
            return new NativeGenerator(scope, funObj, gs);
        }
    }

    public static Object[] getGeneratorStackState(Object obj) {
        GeneratorState rgs = (GeneratorState) obj;
        if (rgs.stackState == null) rgs.stackState = new Object[rgs.maxStack];
        return rgs.stackState;
    }

    public static Object[] getGeneratorLocalsState(Object obj) {
        GeneratorState rgs = (GeneratorState) obj;
        if (rgs.localsState == null) rgs.localsState = new Object[rgs.maxLocals];
        return rgs.localsState;
    }

    public static void setGeneratorReturnValue(Object obj, Object val) {
        GeneratorState rgs = (GeneratorState) obj;
        rgs.returnValue = val;
    }

    public static Object getGeneratorReturnValue(Object obj) {
        GeneratorState rgs = (GeneratorState) obj;
        return rgs.returnValue;
    }

    public static boolean isNullOrUndefined(Object obj) {
        return obj == null || Undefined.isUndefined(obj);
    }

    public static void spread(
            Context cx,
            VarScope scope,
            NewLiteralStorage store,
            Object source,
            int sourcePosition) {
        store.spread(cx, scope, source, sourcePosition);
    }

    public static class GeneratorState {
        static final String CLASS_NAME =
                "org/monflabs/mozilla/javascript/optimizer/OptRuntime$GeneratorState";

        @SuppressWarnings("unused")
        public int resumptionPoint;

        static final String resumptionPoint_NAME = "resumptionPoint";
        static final String resumptionPoint_TYPE = "I";

        @SuppressWarnings("unused")
        public final Scriptable thisObj;

        @SuppressWarnings("unused")
        public final VarScope activationFrame;

        static final String activationFrame_NAME = "activationFrame";
        static final String activationFrame_TYPE = "Lorg/monflabs/mozilla/javascript/VarScope;";
        static final String thisObj_NAME = "thisObj";
        static final String thisObj_TYPE = "Lorg/monflabs/mozilla/javascript/Scriptable;";

        Object[] stackState;
        Object[] localsState;
        int maxLocals;
        int maxStack;
        Object returnValue = Undefined.instance;

        GeneratorState(VarScope activationFrame, Scriptable thisObj, int maxLocals, int maxStack) {
            this.activationFrame = activationFrame;
            this.thisObj = thisObj;
            this.maxLocals = maxLocals;
            this.maxStack = maxStack;
        }
    }
}
