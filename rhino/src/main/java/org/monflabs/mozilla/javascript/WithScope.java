package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.Scriptable.NOT_FOUND;

public class WithScope implements VarScope {
    // This cannot be final because XML dot queries mutate it!
    private Scriptable obj;
    private final VarScope parent;

    public WithScope(VarScope parentScope, Scriptable obj) {
        this.parent = parentScope;
        this.obj = obj;
    }

    @Override
    public VarScope getParentScope() {
        return parent;
    }

    public void setObject(Scriptable obj) {
        this.obj = obj;
    }

    @Override
    public void delete(int index) {}

    @Override
    public void delete(Symbol arg0) {}

    @Override
    public Object get(int index, VarScope start) {
        return NOT_FOUND;
    }

    @Override
    public Object get(Symbol arg0, VarScope arg1) {
        return NOT_FOUND;
    }

    @Override
    public boolean has(int index, VarScope start) {
        return false;
    }

    @Override
    public boolean has(Symbol arg0, VarScope arg1) {
        return false;
    }

    @Override
    public void put(int index, VarScope start, Object value) {}

    @Override
    public void put(Symbol arg0, VarScope arg1, Object arg2) {}

    @Override
    public void defineConst(String name, VarScope start) {
        Kit.codeBug("Attempt to define a const on a `with` scope.");
    }

    @Override
    public boolean isConst(String name) {
        return false;
    }

    @Override
    public void putConst(String name, VarScope start, Object value) {
        Kit.codeBug("Attempt to define a const on a `with` scope.");
    }

    @Override
    public Object get(String name, VarScope start) {
        if (isLatest()) {
            // HasBinding (9.1.1.2.1), then GetBindingValue (9.1.1.2.6)
            if (!hasBinding(name)) {
                return NOT_FOUND;
            }
            if (!ScriptableObject.hasProperty(obj, name)) {
                // the binding was deleted after HasBinding (Symbol.unscopables getter, ...)
                if (Context.isCurrentContextStrict()) {
                    throw ScriptRuntime.constructError(
                            "ReferenceError",
                            ScriptRuntime.getMessageById("msg.is.not.defined", name));
                }
                return Undefined.instance;
            }
            Object value = ScriptableObject.getProperty(obj, name);
            return value == NOT_FOUND ? Undefined.instance : value;
        }
        return ScriptableObject.getProperty(obj, name);
    }

    private static boolean isLatest() {
        Context cx = Context.getCurrentContext();
        return cx != null && cx.isLatestEcmaScript();
    }

    /**
     * HasBinding of the object environment of a with statement: the property, unless the object's
     * {@code Symbol.unscopables} blocks it.
     */
    private boolean hasBinding(String name) {
        if (!ScriptableObject.hasProperty(obj, name)) {
            return false;
        }
        Object unscopables = ScriptableObject.getProperty(obj, SymbolKey.UNSCOPABLES);
        if (unscopables instanceof Scriptable && ScriptRuntime.isObject(unscopables)) {
            Object blocked = ScriptableObject.getProperty((Scriptable) unscopables, name);
            return blocked == NOT_FOUND || !ScriptRuntime.toBoolean(blocked);
        }
        return true;
    }

    @Override
    public void put(String name, VarScope start, Object value) {
        // SetMutableBinding of an object environment (9.1.1.2.5): the binding is checked again
        // (under VERSION_ECMASCRIPT)
        Context cx = Context.getCurrentContext();
        if (cx != null && cx.isLatestEcmaScript()) {
            boolean stillExists = ScriptableObject.hasProperty(obj, name);
            if (!stillExists && cx.isStrictMode()) {
                throw ScriptRuntime.constructError(
                        "ReferenceError", ScriptRuntime.getMessageById("msg.is.not.defined", name));
            }
        }
        ScriptableObject.putProperty(obj, name, value);
    }

    @Override
    public boolean has(String name, VarScope start) {
        return isLatest() ? hasBinding(name) : ScriptableObject.hasProperty(obj, name);
    }

    @Override
    public void delete(String name) {
        ScriptableObject.deleteProperty(obj, name);
    }

    @Override
    public Object[] getIds() {
        return obj.getIds();
    }

    public Scriptable getObject() {
        return obj;
    }

    /** Must return null to continue looping or the final collection result. */
    protected Object updateDotQuery(boolean value) {
        // WithScope itself does not support it
        throw new IllegalStateException();
    }

    @Override
    public boolean isNestedScope() {
        return true;
    }
}
