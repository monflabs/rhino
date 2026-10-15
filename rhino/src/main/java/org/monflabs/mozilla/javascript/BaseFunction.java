/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.PROTO;
import static org.monflabs.mozilla.javascript.Symbol.Kind.REGULAR;

import java.io.Serial;
import java.util.EnumSet;
import org.monflabs.mozilla.javascript.xml.XMLObject;

/**
 * The base class for Function objects. That is one of two purposes. It is also the prototype for
 * every "function" defined except those that are used as GeneratorFunctions via the ES6 "function
 * *" syntax.
 *
 * <p>See ECMA 15.3.
 *
 * @author Norris Boyd
 */
public class BaseFunction extends ScriptableObject implements Function {
    @Serial private static final long serialVersionUID = 5311394446546053859L;

    private static final Object FUNCTION_TAG = "Function";
    private static final String FUNCTION_CLASS = "Function";
    static final SymbolKey GENERATOR_FUNCTION_CLASS =
            new SymbolKey("GeneratorFunctionPrototype", REGULAR);
    static final SymbolKey ASYNC_FUNCTION_CLASS = new SymbolKey("AsyncFunctionPrototype", REGULAR);
    static final SymbolKey ASYNC_GENERATOR_FUNCTION_CLASS =
            new SymbolKey("AsyncGeneratorFunctionPrototype", REGULAR);

    private static final String APPLY_TAG = "APPLY_TAG";
    private static final String CALL_TAG = "CALL_TAG";
    private static final String PROTOTYPE_PROPERTY_NAME = "prototype";

    private static final ClassDescriptor DESCRIPTOR;
    private static final ClassDescriptor ES6_DESCRIPTOR;
    private static final ClassDescriptor GENERATOR_DESCRIPTOR;
    private static final ClassDescriptor ASYNC_DESCRIPTOR;
    private static final ClassDescriptor ASYNC_GENERATOR_DESCRIPTOR;
    private static final JSDescriptor<JSFunction> APPLY_DESCRIPTOR;
    private static final JSDescriptor<JSFunction> HAS_INSTANCE_DESCRIPTOR;
    private static final JSDescriptor<JSFunction> CALL_DESCRIPTOR;

    private static final BuiltInSlot.Descriptor<BaseFunction> NAME_DESCRIPTOR =
            new BuiltInSlot.Descriptor<>(
                    "name", BaseFunction::nameGetter, BaseFunction::nameSetter);
    private static final BuiltInSlot.Descriptor<BaseFunction> LENGTH_DESCRIPTOR =
            new BuiltInSlot.Descriptor<>(
                    "length", BaseFunction::lengthGetter, BaseFunction::lengthSetter);
    private static final BuiltInSlot.Descriptor<BaseFunction> ARITY_DESCRIPTOR =
            new BuiltInSlot.Descriptor<>("arity", BaseFunction::arityGetter);
    private static final BuiltInSlot.Descriptor<BaseFunction> ARGUMENTS_DESCRIPTOR =
            new BuiltInSlot.Descriptor<>(
                    "arguments", BaseFunction::argumentsGetter, BaseFunction::argumentsSetter);
    private static final SlotMapDescriptor<BaseFunction> BASIC_MAP =
            new SlotMapDescriptor.Builder<BaseFunction>()
                    .withSlot(LENGTH_DESCRIPTOR, DONTENUM | READONLY)
                    .withSlot(NAME_DESCRIPTOR, DONTENUM | READONLY)
                    .build();

    private static final SlotMapDescriptor<BaseFunction> ARITY_MAP =
            SlotMapDescriptor.Builder.extending(BASIC_MAP)
                    .withSlot(ARITY_DESCRIPTOR, PERMANENT | DONTENUM | READONLY)
                    .build();

    private static final SlotMapDescriptor<BaseFunction> ARGUMENTS_MAP =
            SlotMapDescriptor.Builder.extending(ARITY_MAP)
                    .withSlot(ARGUMENTS_DESCRIPTOR, PERMANENT | DONTENUM)
                    .build();

    private static final BuiltInSlot.Descriptor<BaseFunction> CALLER_DESCRIPTOR =
            new BuiltInSlot.Descriptor<>("caller", (function, start) -> Undefined.instance);

    /**
     * The properties of a sloppy function under VERSION_ECMASCRIPT: its own arguments (of the call
     * running) and caller (undefined: the caller is not tracked), as Function.prototype has
     * %ThrowTypeError% for them.
     */
    private static final SlotMapDescriptor<BaseFunction> SLOPPY_MAP =
            SlotMapDescriptor.Builder.extending(BASIC_MAP)
                    .withSlot(ARGUMENTS_DESCRIPTOR, PERMANENT | DONTENUM | READONLY)
                    .withSlot(CALLER_DESCRIPTOR, PERMANENT | DONTENUM | READONLY)
                    .build();

    private static final BuiltInSlot.Descriptor<BaseFunction> PROTOTYPE_DESCRIPTOR =
            new BuiltInSlot.Descriptor<>(
                    PROTOTYPE_PROPERTY_NAME,
                    BaseFunction::prototypeGetter,
                    BaseFunction::prototypeSetter,
                    BaseFunction::prototypeAttrSetter,
                    BaseFunction::prototypeDescSetter);

    static {
        var builder =
                new ClassDescriptor.Builder(
                                FUNCTION_CLASS,
                                1,
                                BaseFunction::js_constructor,
                                BaseFunction::js_constructor)
                        .withMethod(PROTO, "call", 1, BaseFunction::js_call)
                        .withMethod(PROTO, "apply", 2, BaseFunction::js_apply)
                        .withMethod(PROTO, "bind", 1, BaseFunction::js_bind)
                        .withMethod(PROTO, "toSource", 1, BaseFunction::js_toSource)
                        .withMethod(PROTO, "toString", 0, BaseFunction::js_toString)
                        .withMethod(
                                PROTO,
                                SymbolKey.HAS_INSTANCE,
                                1,
                                BaseFunction::js_hasInstance,
                                DONTENUM | READONLY | PERMANENT,
                                DONTENUM | READONLY);

        DESCRIPTOR = builder.build();
        ES6_DESCRIPTOR =
                builder.withProp(
                                PROTO,
                                "arguments",
                                BaseFunction::js_protoArgumentsGetter,
                                BaseFunction::js_protoArgumentsSetter,
                                DONTENUM | READONLY)
                        .build();

        APPLY_DESCRIPTOR = DESCRIPTOR.findProtoDesc("apply");
        HAS_INSTANCE_DESCRIPTOR = DESCRIPTOR.findProtoDesc(SymbolKey.HAS_INSTANCE);
        CALL_DESCRIPTOR = DESCRIPTOR.findProtoDesc("call");

        GENERATOR_DESCRIPTOR =
                new ClassDescriptor.Builder(
                                GENERATOR_FUNCTION_CLASS,
                                "GeneratorFunction",
                                1,
                                BaseFunction::js_gen_constructor,
                                BaseFunction::js_gen_constructor)
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                value("GeneratorFunction", READONLY | DONTENUM))
                        .build();

        ASYNC_DESCRIPTOR =
                new ClassDescriptor.Builder(
                                ASYNC_FUNCTION_CLASS,
                                "AsyncFunction",
                                1,
                                BaseFunction::js_async_constructor,
                                BaseFunction::js_async_constructor)
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                value("AsyncFunction", READONLY | DONTENUM))
                        .build();

        ASYNC_GENERATOR_DESCRIPTOR =
                new ClassDescriptor.Builder(
                                ASYNC_GENERATOR_FUNCTION_CLASS,
                                "AsyncGeneratorFunction",
                                1,
                                BaseFunction::js_async_gen_constructor,
                                BaseFunction::js_async_gen_constructor)
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                value("AsyncGeneratorFunction", READONLY | DONTENUM))
                        .build();
    }

    static JSFunction init(Context cx, VarScope scope, boolean sealed) {
        var proto =
                new LambdaFunction(
                        scope, "", 0, null, (lcx, lscope, lthisObj, largs) -> Undefined.instance);

        if (cx.getLanguageVersion() < Context.VERSION_ES6) {
            return DESCRIPTOR.buildConstructor(cx, scope, proto, sealed);
        } else if (!cx.isLatestEcmaScript()) {
            return ES6_DESCRIPTOR.buildConstructor(cx, scope, proto, sealed);
        } else {
            // caller and arguments are %ThrowTypeError%, defined with the Object prototype
            return DESCRIPTOR.buildConstructor(cx, scope, proto, sealed);
        }
    }

    /** Gives a sloppy function its own arguments and caller properties (VERSION_ECMASCRIPT). */
    void installSloppyFunctionProperties() {
        SLOPPY_MAP.installMap(this);
    }

    /**
     * AddRestrictedFunctionProperties ( F, realm ) (10.2.4): caller and arguments of
     * Function.prototype are accessors whose get and set are %ThrowTypeError%.
     */
    static void addRestrictedFunctionProperties(
            Context cx, VarScope scope, ScriptableObject functionPrototype) {
        functionPrototype.defineOwnProperty(
                cx, "caller", createThrowingProp(cx, scope, functionPrototype));
        functionPrototype.defineOwnProperty(
                cx, "arguments", createThrowingProp(cx, scope, functionPrototype));
    }

    /**
     * @deprecated Use {@link #init(Context, VarScope, boolean)} instead
     */
    @Deprecated
    static void init(VarScope scope, boolean sealed) {
        init(Context.getContext(), scope, sealed);
    }

    static Object initAsGeneratorFunction(Context cx, VarScope scope, boolean sealed) {
        var proto = new NativeObject();

        return GENERATOR_DESCRIPTOR.buildConstructor(
                cx,
                scope,
                proto,
                sealed,
                (c, ctor) -> {
                    VarScope top = ScriptableObject.getTopLevelScope(scope);

                    var function = (Scriptable) ScriptableObject.getProperty(scope, FUNCTION_CLASS);
                    var functionProto =
                            (Scriptable)
                                    ScriptableObject.getProperty(function, PROTOTYPE_PROPERTY_NAME);
                    proto.setPrototype(functionProto);

                    var generatorProto =
                            ScriptableObject.getTopScopeValue(top, ES6Generator.GENERATOR_TAG);

                    proto.setAttributes("constructor", DONTENUM | READONLY);
                    proto.defineProperty("prototype", generatorProto, READONLY | DONTENUM);
                    if (generatorProto instanceof ScriptableObject && c.isLatestEcmaScript()) {
                        // %GeneratorPrototype%.constructor is %GeneratorFunction.prototype%;
                        // %GeneratorPrototype% is created, and possibly sealed, before
                        ScriptableObject generatorPrototype = (ScriptableObject) generatorProto;
                        boolean wasSealed = generatorPrototype.isSealed;
                        generatorPrototype.isSealed = false;
                        try {
                            generatorPrototype.defineProperty(
                                    "constructor", proto, READONLY | DONTENUM);
                        } finally {
                            generatorPrototype.isSealed = wasSealed;
                        }
                    }
                });
    }

    /** %AsyncFunction% (ECMA-262 27.7), not a global: reached through async functions. */
    static Object initAsAsyncFunction(Context cx, VarScope scope, boolean sealed) {
        var proto = new NativeObject();

        return ASYNC_DESCRIPTOR.buildConstructor(
                cx,
                scope,
                proto,
                sealed,
                (c, ctor) -> {
                    var function = (Scriptable) ScriptableObject.getProperty(scope, FUNCTION_CLASS);
                    var functionProto =
                            (Scriptable)
                                    ScriptableObject.getProperty(function, PROTOTYPE_PROPERTY_NAME);
                    proto.setPrototype(functionProto);
                    ctor.setPrototype(function);
                    proto.setAttributes("constructor", DONTENUM | READONLY);
                });
    }

    /**
     * %AsyncGeneratorFunction% (ECMA-262 27.4), not a global: its prototype is the prototype of
     * async generator functions, and that prototype's prototype property is
     * %AsyncGeneratorPrototype%.
     */
    static Object initAsAsyncGeneratorFunction(Context cx, VarScope scope, boolean sealed) {
        var proto = new NativeObject();

        return ASYNC_GENERATOR_DESCRIPTOR.buildConstructor(
                cx,
                scope,
                proto,
                sealed,
                (c, ctor) -> {
                    VarScope top = ScriptableObject.getTopLevelScope(scope);
                    var function = (Scriptable) ScriptableObject.getProperty(scope, FUNCTION_CLASS);
                    var functionProto =
                            (Scriptable)
                                    ScriptableObject.getProperty(function, PROTOTYPE_PROPERTY_NAME);
                    proto.setPrototype(functionProto);
                    ctor.setPrototype(function);
                    proto.setAttributes("constructor", DONTENUM | READONLY);
                    Object generatorProto =
                            ScriptableObject.getTopScopeValue(
                                    top, NativeAsyncGenerator.ASYNC_GENERATOR_TAG);
                    proto.defineProperty("prototype", generatorProto, READONLY | DONTENUM);
                    if (generatorProto instanceof ScriptableObject) {
                        ScriptableObject generatorPrototype = (ScriptableObject) generatorProto;
                        boolean wasSealed = generatorPrototype.isSealed;
                        generatorPrototype.isSealed = false;
                        try {
                            generatorPrototype.defineProperty(
                                    "constructor", proto, READONLY | DONTENUM);
                        } finally {
                            generatorPrototype.isSealed = wasSealed;
                        }
                    }
                });
    }

    public BaseFunction(VarScope scope) {
        declarationScope = scope;
        createProperties();
    }

    public BaseFunction(VarScope scope, Scriptable prototype) {
        super(scope, prototype);
        declarationScope = scope;
        createProperties();
        ScriptRuntime.setBuiltinProtoAndParent(this, scope, TopLevel.Builtins.Function);
    }

    protected void createProperties() {
        SlotMapDescriptor<BaseFunction> desc;

        Context cx = Context.getCurrentContext();
        if (cx == null || !cx.isStrictMode()) {
            if (cx == null || cx.getLanguageVersion() < Context.VERSION_ES6) {
                desc = ARGUMENTS_MAP;
            } else if (cx.isLatestEcmaScript()) {
                // the arity of JavaScript 1.2 is not a property of ECMAScript functions
                desc = BASIC_MAP;
            } else {
                desc = ARITY_MAP;
            }
        } else {
            desc = BASIC_MAP;
        }

        desc.installMap(this);
    }

    private static Object lengthGetter(BaseFunction function, Scriptable start) {
        return function.lengthValue != null ? function.lengthValue : function.getLength();
    }

    private static boolean lengthSetter(
            BaseFunction function,
            Object value,
            Scriptable owner,
            Scriptable start,
            boolean isThrow) {
        // a redefined length (Object.defineProperty) is ignored before VERSION_ECMASCRIPT
        Context cx = Context.getCurrentContext();
        if (cx != null && cx.isLatestEcmaScript()) {
            function.lengthValue = value;
        }
        return true;
    }

    private static Object arityGetter(BaseFunction function, Scriptable start) {
        return function.getArity();
    }

    private static Object argumentsGetter(BaseFunction function, Scriptable start) {
        return function.getArguments();
    }

    private static boolean argumentsSetter(
            BaseFunction function,
            Object value,
            Scriptable owner,
            Scriptable start,
            boolean isThrow) {
        function.argumentsObj = value;
        return true;
    }

    private static Object nameGetter(BaseFunction function, Scriptable start) {
        return function.nameValue != null ? function.nameValue : function.getFunctionName();
    }

    private static boolean nameSetter(
            BaseFunction function,
            Object value,
            Scriptable owner,
            Scriptable start,
            boolean isThrow) {
        function.nameValue = value;
        return true;
    }

    /** Forces setting the function's name, bypassing all "readonly" checks. */
    void setFunctionName(String name) {
        nameValue = name;
    }

    protected void createPrototypeProperty() {
        try (var map = startCompoundOp(true)) {
            createPrototypeProperty(map);
        }
    }

    protected void createPrototypeProperty(CompoundOperationMap<Scriptable> compoundOp) {
        compoundOp.compute(
                this,
                compoundOp,
                PROTOTYPE_PROPERTY_NAME,
                0,
                (k, i, s, m, o) -> {
                    if (s == null) {
                        return new BuiltInSlot<BaseFunction>(
                                PROTOTYPE_DESCRIPTOR, prototypePropertyAttributes, this);
                    }
                    return s;
                });
    }

    private static Object prototypeGetter(BaseFunction function, Scriptable start) {
        return function.getPrototypeProperty();
    }

    private static boolean prototypeSetter(
            BaseFunction function,
            Object value,
            Scriptable owner,
            Scriptable start,
            boolean isThrow) {
        function.prototypeProperty = value == null ? UniqueTag.NULL_VALUE : value;
        return true;
    }

    private static void prototypeAttrSetter(BaseFunction function, int attributes) {
        function.prototypePropertyAttributes = attributes;
    }

    protected static boolean prototypeDescSetter(
            BaseFunction builtIn,
            BuiltInSlot<BaseFunction> current,
            Object id,
            ScriptableObject.DescriptorInfo info,
            boolean checkValid,
            Object key,
            int index) {
        try (var map = builtIn.startCompoundOp(true)) {
            return ScriptableObject.defineOrdinaryProperty(
                    (o, i, k, e, m, s) -> {
                        if (i.value != NOT_FOUND) {
                            builtIn.prototypeProperty =
                                    i.value == null ? UniqueTag.NULL_VALUE : i.value;
                        }
                        return s;
                    },
                    builtIn,
                    map,
                    id,
                    info,
                    checkValid,
                    key,
                    index);
        }
    }

    static DescriptorInfo createThrowingProp(Context cx, VarScope scope, ScriptableObject obj) {
        var thrower = ScriptRuntime.typeErrorThrower(scope);
        return new DescriptorInfo(false, NOT_FOUND, true, thrower, thrower, null);
    }

    protected final boolean defaultHas(String name) {
        return super.has(name, this);
    }

    protected final Object defaultGet(String name) {
        return super.get(name, this);
    }

    protected final void defaultPut(String name, Object value) {
        super.put(name, this, value);
    }

    @Override
    public String getClassName() {
        return isGeneratorFunction() ? "GeneratorFunction" : FUNCTION_CLASS;
    }

    // Generated code will override this
    protected boolean isGeneratorFunction() {
        return isGeneratorFunction;
    }

    /**
     * The tag of the intrinsic that the prototype property of a generator function inherits from,
     * or null for a function that is not a generator.
     */
    SymbolKey generatorPrototypeTag() {
        return isGeneratorFunction() ? ES6Generator.GENERATOR_TAG : null;
    }

    // Generated code will override this
    protected boolean hasDefaultParameters() {
        return false;
    }

    /**
     * Gets the value returned by calling the typeof operator on this object.
     *
     * @see ScriptableObject#getTypeOf()
     * @return "function" or "undefined" if {@link #avoidObjectDetection()} returns {@code true}
     */
    @Override
    public String getTypeOf() {
        return avoidObjectDetection() ? "undefined" : "function";
    }

    /**
     * Implements the instanceof operator for JavaScript Function objects.
     *
     * <p><code>
     * foo = new Foo();<br>
     * foo instanceof Foo;  // true<br>
     * </code>
     *
     * @param instance The value that appeared on the LHS of the instanceof operator
     * @return true if the "prototype" property of "this" appears in value's prototype chain
     */
    @Override
    public boolean hasInstance(Scriptable instance) {
        Context cx = Context.getCurrentContext();

        // Attempt to call custom Symbol.hasInstance implementation if present

        Object hasInstanceMethod = ScriptRuntime.getObjectElem(this, SymbolKey.HAS_INSTANCE, cx);
        if (hasInstanceMethod instanceof Callable) {
            return ScriptRuntime.toBoolean(
                    ((Callable) hasInstanceMethod)
                            .call(cx, getParentScope(), this, new Object[] {instance}));
        }

        Object protoProp = ScriptableObject.getProperty(this, PROTOTYPE_PROPERTY_NAME);
        if (protoProp instanceof Scriptable) {
            return ScriptRuntime.jsDelegatesTo(instance, (Scriptable) protoProp);
        }
        throw ScriptRuntime.typeErrorById("msg.instanceof.bad.prototype", getFunctionName());
    }

    protected static final int Id_length = 1,
            Id_arity = 2,
            Id_name = 3,
            Id_prototype = 4,
            Id_arguments = 5,
            MAX_INSTANCE_ID = 5;

    /** Whether f is %Function.prototype%[@@hasInstance]. */
    static boolean isHasInstanceIntrinsic(Object f) {
        return f instanceof JSFunction
                && ((JSFunction) f).getDescriptor() == HAS_INSTANCE_DESCRIPTOR;
    }

    static boolean isApply(Callable f) {
        if (f instanceof KnownBuiltInFunction kf) {
            var tag = kf.getTag();
            return tag == APPLY_TAG;
        }
        if (f instanceof JSFunction jsf) {
            return jsf.getDescriptor() == APPLY_DESCRIPTOR;
        }
        return false;
    }

    static boolean isApplyOrCall(Callable f) {
        if (f instanceof KnownBuiltInFunction) {
            var kf = (KnownBuiltInFunction) f;
            var tag = kf.getTag();
            return tag == APPLY_TAG || tag == CALL_TAG;
        }
        if (f instanceof JSFunction) {
            var desc = ((JSFunction) f).getDescriptor();
            return desc == APPLY_DESCRIPTOR || desc == CALL_DESCRIPTOR;
        }
        return false;
    }

    private static Object js_hasInstance(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!(thisObj instanceof Callable && thisObj instanceof Scriptable)) {
            return false;
        }
        if (cx.isLatestEcmaScript()
                && !(thisObj instanceof BoundFunction)
                && (args.length == 0 || ScriptRuntime.isPrimitive(args[0]))) {
            // OrdinaryHasInstance (7.3.21) step 4: a primitive is not an instance
            return false;
        }
        Object protoProp = null;
        if (thisObj instanceof BoundFunction)
            protoProp =
                    ((JSFunction) ((BoundFunction) thisObj).getTargetFunction())
                            .getPrototypeProperty();
        else {
            protoProp = ScriptableObject.getProperty((Scriptable) thisObj, PROTOTYPE_PROPERTY_NAME);
        }

        if (ScriptRuntime.isObject(protoProp) || protoProp instanceof XMLObject) {
            if (args.length > 0 && args[0] instanceof Scriptable) {
                Scriptable obj = (Scriptable) args[0];

                return ScriptRuntime.jsDelegatesTo(obj, (Scriptable) protoProp);
            }
            return false; // NOT_FOUND, null etc.
        }

        throw ScriptRuntime.typeErrorById(
                "msg.instanceof.bad.prototype",
                (thisObj instanceof BaseFunction)
                        ? ((BaseFunction) thisObj).getFunctionName()
                        : "unknown");
    }

    private static Object js_bind(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (!(thisObj instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(thisObj);
        }
        Callable targetFunction = (Callable) thisObj;
        int argc = args.length;
        final Scriptable boundThis;
        final Object[] boundArgs;
        // an undefined this stays undefined for a strict target (null before VERSION_ECMASCRIPT)
        Scriptable noThis = cx.isLatestEcmaScript() ? Undefined.SCRIPTABLE_UNDEFINED : null;
        if (argc > 0) {
            boundThis =
                    Undefined.isUndefined(args[0]) && noThis != null
                            ? noThis
                            : ScriptRuntime.toObjectOrNull(cx, args[0], s);
            boundArgs = new Object[argc - 1];
            System.arraycopy(args, 1, boundArgs, 0, argc - 1);
        } else {
            boundThis = noThis;
            boundArgs = ScriptRuntime.emptyArgs;
        }
        return new BoundFunction(cx, s, targetFunction, boundThis, boundArgs);
    }

    private static Object js_apply(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return ScriptRuntime.applyOrCall(true, cx, f.getDeclarationScope(), thisObj, args);
    }

    private static Object js_call(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        var thisArg = ScriptRuntime.toObject(f.getDeclarationScope(), thisObj);
        return ScriptRuntime.applyOrCall(false, cx, f.getDeclarationScope(), thisArg, args);
    }

    private static Object js_toSource(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        BaseFunction realf = realFunction(thisObj, "toSource");
        int indent = 0;
        EnumSet<DecompilerFlag> flags = EnumSet.of(DecompilerFlag.TO_SOURCE);
        if (args.length != 0) {
            indent = ScriptRuntime.toInt32(args[0]);
            if (indent >= 0) {
                flags = EnumSet.noneOf(DecompilerFlag.class);
            } else {
                indent = 0;
            }
        }
        return realf.decompile(indent, flags);
    }

    private static Object js_toString(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (cx.isLatestEcmaScript()
                && thisObj instanceof NativeProxy
                && thisObj instanceof Callable) {
            return "function () {\n\t[native code]\n}\n";
        }
        BaseFunction realf = realFunction(thisObj, "toString");
        int indent = ScriptRuntime.toInt32(args, 0);
        return realf.decompile(indent, EnumSet.noneOf(DecompilerFlag.class));
    }

    private static Scriptable js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return jsConstructor(cx, f, nt, f.getDeclarationScope(), args, "function");
    }

    private static Scriptable js_gen_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return jsConstructor(cx, f, nt, f.getDeclarationScope(), args, "function*");
    }

    private static Scriptable js_async_gen_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return jsConstructor(cx, f, nt, f.getDeclarationScope(), args, "async function*");
    }

    private static Scriptable js_async_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return jsConstructor(cx, f, nt, f.getDeclarationScope(), args, "async function");
    }

    private static BaseFunction realFunction(Object thisObj, String functionName) {
        if (thisObj == null) {
            throw ScriptRuntime.notFunctionError(null);
        }
        if (thisObj instanceof BaseFunction) {
            // a function is not converted (it was, with its default value, before
            // VERSION_ECMASCRIPT)
            Context cx = Context.getCurrentContext();
            if (cx != null && cx.isLatestEcmaScript()) {
                return (BaseFunction) thisObj;
            }
        }
        if (thisObj instanceof Scriptable) {
            Object x = ((Scriptable) thisObj).getDefaultValue(ScriptRuntime.FunctionClass);
            if (x instanceof Delegator) {
                x = ((Delegator) x).getDelegee();
            }
            return ensureType(x, BaseFunction.class, functionName);
        }
        return ensureType(thisObj, BaseFunction.class, functionName);
    }

    /** Make value as DontEnum, DontDelete, ReadOnly prototype property of this Function object */
    public void setImmunePrototypeProperty(Object value) {
        if ((prototypePropertyAttributes & READONLY) != 0) {
            throw new IllegalStateException();
        }
        prototypeProperty = (value != null) ? value : UniqueTag.NULL_VALUE;
        createPrototypeProperty();
        setAttributes(PROTOTYPE_PROPERTY_NAME, DONTENUM | PERMANENT | READONLY);
    }

    protected Scriptable getClassPrototype() {
        Object protoVal = getPrototypeProperty();
        if (protoVal instanceof Scriptable) {
            return (Scriptable) protoVal;
        }
        return ScriptableObject.getObjectPrototype(getDeclarationScope());
    }

    /** Should be overridden. */
    @Override
    public final Object call(Context cx, VarScope scope, Object thisObj, Object[] args) {
        return call(cx, Undefined.instance, scope, thisObj, args);
    }

    public Object call(Context cx, Object nt, VarScope s, Object thisObj, Object[] args) {
        return Undefined.instance;
    }

    @Override
    public Scriptable construct(Context cx, Object nt, VarScope scope, Object[] args) {
        if (cx.getLanguageVersion() >= Context.VERSION_ES6 && this.getHomeObject() != null) {
            // Only methods have home objects associated with them
            throw ScriptRuntime.typeErrorById("msg.not.ctor", getFunctionName());
        }

        Scriptable result = createObject(cx, scope);
        if (result == null) {
            Object val = call(cx, nt, scope, null, args);
            if (!(val instanceof Scriptable)) {
                // It is program error not to return Scriptable from
                // the call method if createObject returns null.
                throw new IllegalStateException(
                        "Bad implementation of call as constructor, name="
                                + getFunctionName()
                                + " in "
                                + getClass().getName());
            }
            result = (Scriptable) val;
            if (result.getPrototype() == null) {
                Scriptable proto = getClassPrototype();
                if (result != proto) {
                    result.setPrototype(proto);
                }
            }
            if (result.getParentScope() == null) {
                VarScope parent = getParentScope();
                result.setParentScope(parent);
            }
        } else {
            Object val = call(cx, nt, scope, result, args);
            if (val instanceof Scriptable) {
                result = (Scriptable) val;
            }
        }
        return result;
    }

    /**
     * Creates new script object. The default implementation of {@link #construct} uses this method
     * to to get the value for {@code thisObj} argument when invoking {@link #call}. The method is
     * allowed to return {@code null} to indicate that {@link #call} will create a new object
     * itself. In this case {@link #construct} will set scope and prototype on the result {@link
     * #call} unless they are already set.
     */
    public Scriptable createObject(Context cx, VarScope scope) {
        Scriptable newInstance = new NativeObject();
        newInstance.setPrototype(getClassPrototype());
        newInstance.setParentScope(getParentScope());
        return newInstance;
    }

    public Scriptable createObject(Context cx, VarScope scope, Object nt) {
        Scriptable newInstance = new NativeObject();
        Object proto;
        if (nt instanceof JSFunction || nt instanceof ClassDefinition.DefaultConstructor) {
            proto = ((BaseFunction) nt).getPrototypeProperty();
        } else if (nt instanceof NativeProxy && cx.isLatestEcmaScript()) {
            // GetPrototypeFromConstructor: Get, then GetFunctionRealm, which throws for a revoked
            // proxy
            proto = ScriptableObject.getProperty((Scriptable) nt, "prototype");
            if (!(proto instanceof Scriptable)) {
                ((NativeProxy) nt).getTargetThrowIfRevoked();
            }
        } else {
            proto = Undefined.instance;
        }

        if (proto instanceof Scriptable) {
            newInstance.setPrototype((Scriptable) proto);
        } else if (nt instanceof BaseFunction && nt != this && cx.isLatestEcmaScript()) {
            // %Object.prototype% of the realm of newTarget (10.1.14 GetPrototypeFromConstructor)
            newInstance.setPrototype(
                    ScriptableObject.getObjectPrototype(
                            ScriptableObject.getTopLevelScope(
                                    ((BaseFunction) nt).getDeclarationScope())));
        } else {
            newInstance.setPrototype(getClassPrototype());
        }

        newInstance.setParentScope(getParentScope());
        return newInstance;
    }

    /**
     * Decompile the source information associated with this js function/script back into a string.
     *
     * @param indent How much to indent the decompiled result.
     * @param flags Flags specifying format of decompilation output.
     */
    String decompile(int indent, EnumSet<DecompilerFlag> flags) {
        StringBuilder sb = new StringBuilder();
        boolean justbody = flags.contains(DecompilerFlag.ONLY_BODY);
        if (!justbody) {
            sb.append("function ");
            Context cx = Context.getCurrentContext();
            sb.append(
                    cx != null && cx.isLatestEcmaScript()
                            ? nativeFunctionName(getFunctionName())
                            : getFunctionName());
            sb.append("() {\n\t");
        }
        sb.append("[native code]\n");
        if (!justbody) {
            sb.append("}\n");
        }
        return sb.toString();
    }

    /**
     * The name in the NativeFunction syntax of a built-in's source text: an optional get/set, then
     * an identifier name or a computed [Symbol.x] key; any other name is left out.
     */
    private static String nativeFunctionName(String name) {
        String key = name;
        if (key.startsWith("get ") || key.startsWith("set ")) {
            key = key.substring(4);
        }
        if (key.isEmpty() || key.startsWith("[") || TokenStream.isValidIdentifierName(key)) {
            return name;
        }
        return name.length() == key.length() ? "" : name.substring(0, 3);
    }

    public int getArity() {
        return 0;
    }

    public int getLength() {
        return 0;
    }

    /**
     * Function.prototype.arguments and caller are a TypeError for strict functions and for
     * built-ins (ECMA-262 forbids exposing them); only sloppy functions keep the legacy behavior.
     */
    private static void checkSloppyFunction(Scriptable thisObj) {
        if (!(thisObj instanceof JSFunction && !((JSFunction) thisObj).isStrict())) {
            ScriptRuntime.ThrowTypeError.throwNotAllowed();
        }
    }

    private static Object js_protoArgumentsGetter(Scriptable thisObj) {
        if (Context.getCurrentContext().isLatestEcmaScript()) {
            checkSloppyFunction(thisObj);
        }
        return LambdaConstructor.convertThisObject(thisObj, BaseFunction.class).getArguments();
    }

    private static void js_protoArgumentsSetter(Scriptable thisObj, Object value) {
        if (Context.getCurrentContext().isLatestEcmaScript()) {
            checkSloppyFunction(thisObj);
        }
        LambdaConstructor.convertThisObject(thisObj, BaseFunction.class).setArguments(value);
    }

    public String getFunctionName() {
        return "";
    }

    /**
     * Sets the attributes of the "name", "length", and "arity" properties, which differ for many
     * native objects.
     */
    public void setStandardPropertyAttributes(int attributes) {
        setAttributes("name", attributes);
        setAttributes("length", attributes);
        Context cx = Context.getCurrentContext();
        if (cx == null || !cx.isLatestEcmaScript()) {
            // which creates arity for a strict function, before VERSION_ECMASCRIPT
            setAttributes("arity", attributes);
        }
    }

    public void setPrototypePropertyAttributes(int attributes) {
        prototypePropertyAttributes = attributes;
        getMap().compute(
                        this,
                        PROTOTYPE_PROPERTY_NAME,
                        0,
                        (k, i, s, m, o) -> {
                            if (s != null) {
                                s.setAttributes(attributes);
                            }
                            return s;
                        });
    }

    protected boolean hasPrototypeProperty() {
        return prototypeProperty != null && prototypeProperty != UniqueTag.NOT_FOUND;
    }

    public Object getPrototypeProperty() {
        Object result = prototypeProperty;
        if (result == null || result == UniqueTag.NOT_FOUND) {
            result = Undefined.instance;
        } else if (result == UniqueTag.NULL_VALUE) {
            result = null;
        }
        return result;
    }

    protected void setPrototypeProperty(Object prototype) {
        if (prototype != null) {
            createPrototypeProperty();
            this.prototypeProperty = prototype;
        } else {
            prototypeProperty = UniqueTag.NOT_FOUND;
        }
    }

    protected synchronized Object setupDefaultPrototype(VarScope scope) {
        if (!has(PROTOTYPE_PROPERTY_NAME, this)) {
            createPrototypeProperty();
        }
        NativeObject obj = new NativeObject();
        obj.setParentScope(getParentScope());

        // put the prototype property into the object now, then in the
        // wacky case of a user defining a function Object(), we don't
        // get an infinite loop trying to find the prototype.
        prototypeProperty = obj;
        Scriptable proto;
        SymbolKey generatorTag = generatorPrototypeTag();
        if (generatorTag != null) {
            // For generator functions, the .prototype property's [[Prototype]]
            // should be %GeneratorPrototype% (%AsyncGeneratorPrototype%), not Object.prototype
            VarScope top = ScriptableObject.getTopLevelScope(scope);
            Object generatorProto = ScriptableObject.getTopScopeValue(top, generatorTag);
            if (generatorProto instanceof Scriptable) {
                proto = (Scriptable) generatorProto;
            } else {
                proto = getObjectPrototype(getDeclarationScope()); // fallback
            }
        } else {
            proto = getObjectPrototype(getDeclarationScope());
        }
        if (proto != obj) {
            // not the one we just made, it must remain grounded
            obj.setPrototype(proto);
        }

        Context cx = Context.getCurrentContext();
        if (generatorTag == null || cx == null || !cx.isLatestEcmaScript()) {
            // the prototype of a generator function has no constructor (ECMA-262 15.5.4)
            obj.defineProperty("constructor", this, DONTENUM);
        }
        return obj;
    }

    Object getArguments() {
        // <Function name>.arguments is deprecated, so we use a slow
        // way of getting it that doesn't add to the invocation cost.
        // TODO: add warning, error based on version
        if (argumentsObj != NOT_FOUND) {
            // Should after changing <Function name>.arguments its
            // activation still be available during Function call?
            // This code assumes it should not:
            // defaultGet("arguments") != NOT_FOUND
            // means assigned arguments
            return argumentsObj;
        }
        Context cx = Context.getContext();
        NativeCall activation = ScriptRuntime.findFunctionActivation(cx, this);
        // return (activation == null) ? null : activation.get("arguments", activation);
        if (activation == null) {
            return null;
        }
        if (activation.function.isStrict() && cx.getLanguageVersion() >= Context.VERSION_ES6) {
            ScriptRuntime.ThrowTypeError.throwNotAllowed();
        }
        Object arguments = activation.get("arguments", activation);
        if (arguments instanceof Arguments && cx.getLanguageVersion() >= Context.VERSION_ES6) {
            return new Arguments.ReadonlyArguments((Arguments) arguments, cx);
        }
        return arguments;
    }

    void setArguments(Object caller) {}

    private static Scriptable jsConstructor(
            Context cx, JSFunction f, Object nt, VarScope scope, Object[] args, String kind) {
        int arglen = args.length;
        StringBuilder sourceBuf = new StringBuilder();

        // CreateDynamicFunction: the parameters end with a line feed, the body is between two;
        // before VERSION_ECMASCRIPT, "function anonymous(a) {" + body
        boolean latest = cx.isLatestEcmaScript();
        sourceBuf
                .append(!latest && "function*".equals(kind) ? "function *" : kind)
                .append(" anonymous(");

        // Append arguments as coma separated strings
        for (int i = 0; i < arglen - 1; i++) {
            if (i > 0) {
                sourceBuf.append(',');
            }
            sourceBuf.append(ScriptRuntime.toString(args[i]));
        }
        sourceBuf.append(latest ? "\n) {\n" : ") {");
        if (arglen != 0) {
            sourceBuf.append(ScriptRuntime.toString(args[arglen - 1]));
        }
        sourceBuf.append("\n}");
        String source = sourceBuf.toString();

        int[] linep = new int[1];
        String filename = Context.getSourcePositionFromStack(linep);
        if (filename == null) {
            filename = "<eval'ed string>";
            linep[0] = 1;
        }

        String sourceURI = ScriptRuntime.makeUrlForGeneratedScript(false, filename, linep[0]);

        TopLevel global = ScriptableObject.getTopLevelScope(f.getDeclarationScope());

        ErrorReporter reporter;
        reporter = DefaultErrorReporter.forEval(cx.getErrorReporter());

        Evaluator evaluator = Context.createInterpreter();
        if (evaluator == null) {
            throw new JavaScriptException("Interpreter not present", filename, linep[0]);
        }

        // Compile with explicit interpreter instance to force interpreter
        // mode.
        var res =
                (JSFunction)
                        cx.compileFunction(global, source, evaluator, reporter, sourceURI, 1, null);

        if (res.getDescriptor().isAsyncGenerator()) {
            ScriptRuntime.setBuiltinProtoAndParent(
                    (ScriptableObject) res, f, nt, scope, TopLevel.Builtins.AsyncGeneratorFunction);
        } else if (res.getDescriptor().isAsync()) {
            ScriptRuntime.setBuiltinProtoAndParent(
                    (ScriptableObject) res, f, nt, scope, TopLevel.Builtins.AsyncFunction);
        } else if (res.getDescriptor().isES6Generator()) {
            ScriptRuntime.setBuiltinProtoAndParent(
                    (ScriptableObject) res, f, nt, scope, TopLevel.Builtins.GeneratorFunction);
        } else {
            ScriptRuntime.setBuiltinProtoAndParent(
                    (ScriptableObject) res, f, nt, scope, TopLevel.Builtins.Function);
        }
        return res;
    }

    public void setHomeObject(Scriptable homeObject) {
        this.homeObject = homeObject;
    }

    public Scriptable getHomeObject() {
        return homeObject;
    }

    @Override
    public boolean isConstructor() {
        return !(Context.getCurrentContext().getLanguageVersion() >= Context.VERSION_ES6
                && this.getHomeObject() != null);
    }

    @Override
    public VarScope getDeclarationScope() {
        return declarationScope;
    }

    private final VarScope declarationScope;
    private Object prototypeProperty;
    private Object argumentsObj = NOT_FOUND;
    private Object nameValue = null;
    Object lengthValue = null;
    private boolean isGeneratorFunction = false;

    /**
     * The fields of the class this function is the constructor of, or null (see ClassDefinition).
     */
    ClassDefinition.Element[] classFields;

    /** The private methods and accessors of the instances of the class, or null. */
    PrivateName.Method[] classPrivateMethods;

    /** The static fields of the class, until they are defined (see ClassDefinition). */
    ClassDefinition.Element[] classStaticElements;

    private Scriptable homeObject = null;

    // For function object instances, attributes are
    //  {configurable:false, enumerable:false};
    // see ECMA 15.3.5.2
    private int prototypePropertyAttributes = PERMANENT | DONTENUM;
}
