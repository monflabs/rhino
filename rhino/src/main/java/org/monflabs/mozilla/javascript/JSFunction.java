package org.monflabs.mozilla.javascript;

import java.util.EnumSet;
import org.monflabs.mozilla.javascript.debug.DebuggableScript;

/**
 * Represents a JavaScript function built upon a a {@link JSDescriptor}. All the immutable metadata
 * associated with the function is stored on the descriptor, with only the lexically bound this, the
 * home object required for super calls, and mutable properties held on the function object itself.
 */
public class JSFunction extends BaseFunction implements ScriptOrFn<JSFunction> {
    private final JSDescriptor<JSFunction> descriptor;
    private final Object lexicalThis;
    private final Object lexicalNewTarget;
    private Scriptable homeObject;

    public JSFunction(
            Context cx,
            VarScope scope,
            JSDescriptor<JSFunction> descriptor,
            Object lexicalThis,
            Object lexicalNewTarget,
            Scriptable homeObject) {
        super(scope);
        this.descriptor = descriptor;
        this.lexicalThis = lexicalThis;
        this.lexicalNewTarget = lexicalNewTarget;
        this.homeObject = homeObject;
        if (descriptor.isAsync()) {
            initAsyncFunction(cx, scope);
            return;
        }
        ScriptRuntime.setFunctionProtoAndParent(this, cx, scope, descriptor.isES6Generator());
        if (!descriptor.isShorthand()
                || (descriptor.isES6Generator() && cx != null && cx.isLatestEcmaScript())) {
            setupDefaultPrototype(scope);
        }
    }

    /**
     * An async function inherits from %AsyncFunction.prototype% and has no prototype property; an
     * async generator function inherits from %AsyncGeneratorFunction.prototype%, and its prototype
     * property from %AsyncGeneratorPrototype%.
     */
    private void initAsyncFunction(Context cx, VarScope scope) {
        boolean generator = descriptor.isAsyncGenerator();
        setParentScope(scope);
        setPrototype(
                TopLevel.getBuiltinPrototype(
                        ScriptableObject.getTopLevelScope(scope),
                        generator
                                ? TopLevel.Builtins.AsyncGeneratorFunction
                                : TopLevel.Builtins.AsyncFunction));
        if (cx != null && cx.getLanguageVersion() >= Context.VERSION_ES6) {
            setStandardPropertyAttributes(READONLY | DONTENUM);
        }
        if (generator) {
            setupDefaultPrototype(scope);
        }
    }

    @Override
    SymbolKey generatorPrototypeTag() {
        return descriptor.isAsyncGenerator()
                ? NativeAsyncGenerator.ASYNC_GENERATOR_TAG
                : super.generatorPrototypeTag();
    }

    JSFunction(
            VarScope scope,
            JSDescriptor<JSFunction> descriptor,
            Scriptable lexicalThis,
            Object lexicalNewTarget,
            Scriptable homeObject) {
        super(scope);
        this.descriptor = descriptor;
        this.lexicalThis = lexicalThis;
        this.lexicalNewTarget = lexicalNewTarget;
        this.homeObject = homeObject;
        setParentScope(scope);
        setPrototype(ScriptableObject.getFunctionPrototype(scope));
    }

    @Override
    protected void setPrototypeProperty(Object prototype) {
        super.setPrototypeProperty(prototype);
    }

    @Override
    public VarScope getDeclarationScope() {
        return this.getParentScope();
    }

    @Override
    public JSDescriptor<JSFunction> getDescriptor() {
        return descriptor;
    }

    @Override
    final String decompile(int indent, EnumSet<DecompilerFlag> flags) {
        return descriptor.getSource();
    }

    public boolean isShorthand() {
        return descriptor.isShorthand();
    }

    public boolean isStrict() {
        return descriptor.isStrict();
    }

    /** Whether this is a built-in function (built-ins are marked strict, but expect an object). */
    boolean isBuiltIn() {
        return descriptor.getSourceProvider() instanceof BuiltInSourceCodeProvider;
    }

    @Override
    public int getArity() {
        return descriptor.getArity();
    }

    public DebuggableScript getDebuggableView() {
        return null;
    }

    protected int getLanguageVersion() {
        return descriptor.getLanguageVersion();
    }

    @Override
    protected boolean hasPrototypeProperty() {
        return true;
    }

    @Override
    protected boolean isGeneratorFunction() {
        return descriptor.isES6Generator() && !descriptor.isAsync();
    }

    @Override
    public int getLength() {
        return descriptor.getArity();
    }

    protected int getParamAndVarCount() {
        return descriptor.getParamAndVarCount();
    }

    protected int getParamCount() {
        int count = descriptor.getParamCount();
        if (descriptor.hasRestArg()) {
            return count - 1;
        }
        return count;
    }

    protected boolean getParamOrVarConst(int index) {
        return descriptor.getParamOrVarConst(index);
    }

    protected String getParamOrVarName(int index) {
        return descriptor.getParamOrVarName(index);
    }

    public String getRawSource() {
        return descriptor.getSource();
    }

    @Override
    protected void createPrototypeProperty() {
        if (descriptor.hasPrototype()) {
            super.createPrototypeProperty();
        }
    }

    JSCode<JSFunction> getCode() {
        return descriptor.getCode();
    }

    JSCode<JSFunction> getConstructor() {
        return descriptor.getConstructor();
    }

    @Override
    public Object call(Context cx, Object nt, VarScope scope, Object thisObj, Object[] args) {
        if (descriptor.isClassConstructor()) {
            throw ScriptRuntime.typeErrorById("msg.class.not.new", getFunctionName());
        }
        if (!ScriptRuntime.hasTopCall(cx)) {
            return ScriptRuntime.doTopCall(this, cx, scope, thisObj, args, isStrict());
        }
        var realThis = getThisObj(thisObj);
        return descriptor.getCode().execute(cx, this, lexicalNewTarget, scope, realThis, args);
    }

    public final Object getThisObj(Object thisObj) {
        if (descriptor.hasLexicalThis()) {
            return lexicalThis;
        } else if (!descriptor.isStrict()) {
            if (thisObj == null || Undefined.isUndefined(thisObj)) {
                return ScriptableObject.getTopLevelScope(getDeclarationScope()).getGlobalThis();
            } else {
                return ScriptRuntime.toObject(getDeclarationScope(), thisObj);
            }
        } else {
            return thisObj;
        }
    }

    @Override
    public Scriptable construct(Context cx, Object nt, VarScope s, Object[] args) {
        if (!ScriptRuntime.hasTopCall(cx)) {
            return (Scriptable)
                    ScriptRuntime.doTopCall(
                            (lcx, lscope, lthisObj, largs) -> construct(lcx, lscope, largs),
                            cx,
                            s,
                            null,
                            args,
                            isStrict());
        }
        if (descriptor.getConstructor() == null) {
            throw ScriptRuntime.typeErrorById("msg.not.ctor", getFunctionName());
        }
        if (nt == null || Undefined.isUndefined(nt)) {
            nt = this;
        }
        if (descriptor.isDerivedConstructor()) {
            var binding = new ClassDefinition.DerivedThis(this, nt);
            return binding.result(
                    descriptor.getConstructor().execute(cx, this, nt, s, binding, args));
        }
        var thisObj =
                descriptor.isClassConstructor()
                        ? ClassDefinition.createInstance(this, cx, s, nt)
                        : homeObject == null ? this.createObject(cx, s, nt) : null;
        // Pass `this` in as new.target for now. This can change when
        // the public `construct` signature changes.
        var res = descriptor.getConstructor().execute(cx, this, nt, s, thisObj, args);
        if (res instanceof Scriptable) {
            thisObj = (Scriptable) res;
        }
        return (Scriptable) thisObj;
    }

    public boolean isScript() {
        return descriptor.isScript();
    }

    @Override
    protected boolean hasDefaultParameters() {
        return descriptor.hasDefaultParameters();
    }

    public boolean hasNoFunctionStatementNamed(String name) {
        return descriptor.hasNoFunctionStatementNamed(name);
    }

    @Override
    public String getFunctionName() {
        return descriptor.getName();
    }

    public Object resumeGenerator(
            Context cx, VarScope scope, int operation, Object state, Object value) {
        return descriptor.getCode().resume(cx, this, state, scope, operation, value);
    }

    @Override
    public Scriptable getHomeObject() {
        return homeObject;
    }

    public Object getLexicalNewTarget() {
        return lexicalNewTarget;
    }

    @Override
    public void setHomeObject(Scriptable homeObject) {
        throw new UnsupportedOperationException("Cannot set home object on JS function.");
    }

    /** The home object of a class constructor or static method, known once the class is defined. */
    void initClassHomeObject(Scriptable homeObject) {
        this.homeObject = homeObject;
    }

    public Object getFunctionThis(Scriptable functionThis) {
        if (descriptor.hasLexicalThis()) {
            return this.lexicalThis;
        } else {
            return functionThis;
        }
    }

    /** Create script from compiled bytecode. */
    public static JSScript createScript(
            JSDescriptor<JSScript> desc, Scriptable homeObject, Object staticSecurityDomain) {
        assert (desc.getSecurityDomain() == staticSecurityDomain);
        assert desc.isScript();
        return new JSScript(desc, homeObject);
    }

    /** Create function compiled from Function(...) constructor. */
    public static JSFunction createFunction(
            Context cx,
            VarScope scope,
            JSDescriptor<JSFunction> desc,
            Scriptable homeObject,
            Object staticSecurityDomain) {
        assert (desc.getSecurityDomain() == staticSecurityDomain);
        JSFunction f = new JSFunction(cx, scope, desc, null, Undefined.instance, homeObject);
        return f;
    }

    /** Create function embedded in script or another function. */
    static JSFunction createFunction(
            Context cx, VarScope scope, JSDescriptor<?> parent, int index, Scriptable homeObject) {
        JSDescriptor<JSFunction> desc = parent.getFunction(index);
        JSFunction f = new JSFunction(cx, scope, desc, null, Undefined.instance, homeObject);
        return f;
    }

    @Override
    public boolean isConstructor() {
        return descriptor.getConstructor() != null;
    }
}
