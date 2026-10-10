/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/**
 * ClassDefinitionEvaluation (ECMA-262 15.7.14) for the class compiled as a constructor (or none,
 * for the default constructor), an optional heritage, and two object literals holding the methods
 * of the prototype and of the constructor (static members).
 */
final class ClassDefinition {

    private ClassDefinition() {}

    static Scriptable define(
            Object constructor,
            Object heritage,
            boolean hasHeritage,
            Object prototypeMembers,
            Object staticMembers,
            String name,
            Context cx,
            VarScope scope) {
        TopLevel top = (TopLevel) ScriptableObject.getTopLevelScope(scope);
        Scriptable protoParent = TopLevel.getBuiltinPrototype(top, TopLevel.Builtins.Object);
        Scriptable constructorParent =
                TopLevel.getBuiltinPrototype(top, TopLevel.Builtins.Function);
        if (hasHeritage) {
            if (heritage == null) {
                protoParent = null;
            } else if (!(heritage instanceof Constructable) || !isConstructor(heritage)) {
                throw ScriptRuntime.typeErrorById("msg.class.heritage", describe(heritage));
            } else {
                Object parentProto =
                        ScriptableObject.getProperty((Scriptable) heritage, "prototype");
                if (parentProto == null) {
                    protoParent = null;
                } else if (parentProto instanceof Scriptable) {
                    protoParent = (Scriptable) parentProto;
                } else {
                    throw ScriptRuntime.typeErrorById("msg.class.heritage", describe(heritage));
                }
                constructorParent = (Scriptable) heritage;
            }
        }

        BaseFunction f;
        if (constructor instanceof BaseFunction) {
            f = (BaseFunction) constructor;
        } else {
            f = new DefaultConstructor(scope, name, hasHeritage);
            ScriptRuntime.setFunctionProtoAndParent(f, cx, scope);
        }
        f.setPrototype(constructorParent);

        // the object literal of the methods is the prototype (and the home object of its methods)
        ScriptableObject proto = (ScriptableObject) prototypeMembers;
        proto.setPrototype(protoParent);
        // the parser placed constructor first; a computed ['constructor'] member replaced it
        ScriptableObject.DescriptorInfo ctorSlot =
                proto.getOwnPropertyDescriptor(cx, "constructor");
        if (ctorSlot != null
                && ctorSlot.isDataDescriptor()
                && Undefined.isUndefined(ctorSlot.value)) {
            proto.defineProperty("constructor", f, 0);
        }
        // the methods are non-enumerable
        makeNonEnumerable(proto);
        if (f instanceof JSFunction) {
            ((JSFunction) f).initClassHomeObject(proto);
        }
        f.setPrototypeProperty(proto);
        f.setPrototypePropertyAttributes(
                ScriptableObject.DONTENUM | ScriptableObject.READONLY | ScriptableObject.PERMANENT);

        ScriptableObject statics = (ScriptableObject) staticMembers;
        for (Object id : ownKeys(statics)) {
            ScriptableObject.DescriptorInfo own = statics.getOwnPropertyDescriptor(cx, id);
            var desc =
                    new ScriptableObject.DescriptorInfo(
                            Boolean.FALSE,
                            own.writable,
                            own.configurable,
                            own.getter,
                            own.setter,
                            own.value);
            // the home object of a static method is the constructor
            for (Object method : new Object[] {desc.value, desc.getter, desc.setter}) {
                if (method instanceof JSFunction
                        && ((JSFunction) method).getHomeObject() == statics) {
                    ((JSFunction) method).initClassHomeObject(f);
                }
            }
            f.defineOwnProperty(cx, id, desc);
        }
        return f;
    }

    /** The heritage in an error message: an object is not converted (it may be a proxy). */
    private static String describe(Object heritage) {
        return heritage instanceof Scriptable
                ? ScriptRuntime.typeof(heritage)
                : ScriptRuntime.toString(heritage);
    }

    private static Object[] ownKeys(ScriptableObject obj) {
        try (var map = obj.startCompoundOp(false)) {
            return obj.getIds(map, true, true);
        }
    }

    private static boolean isConstructor(Object value) {
        return AbstractEcmaObjectOperations.isConstructor(Context.getCurrentContext(), value);
    }

    private static void makeNonEnumerable(ScriptableObject obj) {
        for (Object id : ownKeys(obj)) {
            if (id instanceof Symbol) {
                obj.setAttributes(
                        (Symbol) id, obj.getAttributes((Symbol) id) | ScriptableObject.DONTENUM);
            } else if (id instanceof Integer) {
                obj.setAttributes(
                        (Integer) id, obj.getAttributes((Integer) id) | ScriptableObject.DONTENUM);
            } else {
                String key = id.toString();
                obj.setAttributes(key, obj.getAttributes(key) | ScriptableObject.DONTENUM);
            }
        }
    }

    /**
     * The this binding of a derived constructor (ECMA-262 9.1.1.3): the frame of the constructor
     * and of the arrow functions in it hold this object as their this, unbound until super()
     * returns.
     */
    static final class DerivedThis {
        private final BaseFunction constructor;
        private final Object newTarget;
        private Object value;

        DerivedThis(BaseFunction constructor, Object newTarget) {
            this.constructor = constructor;
            this.newTarget = newTarget;
        }

        Object get() {
            if (value == null) {
                throw ScriptRuntime.constructError(
                        "ReferenceError",
                        ScriptRuntime.getMessageById("msg.class.this.before.super"));
            }
            return value;
        }

        /** The super constructor, looked up before the arguments of super(...) are evaluated. */
        Callable superCallee() {
            Object parent = constructor.getPrototype();
            return (cx, scope, thisObj, args) -> {
                if (!(parent instanceof Constructable) || !isConstructor(parent)) {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.super.not.ctor", constructor.getFunctionName());
                }
                Scriptable result = ((Constructable) parent).construct(cx, newTarget, scope, args);
                if (value != null) {
                    throw ScriptRuntime.constructError(
                            "ReferenceError",
                            ScriptRuntime.getMessageById("msg.class.super.twice"));
                }
                value = result;
                return result;
            };
        }

        /** The result of [[Construct]] for the value returned by the constructor body. */
        Scriptable result(Object returned) {
            if (returned instanceof Scriptable) {
                return (Scriptable) returned;
            }
            if (!Undefined.isUndefined(returned)) {
                throw ScriptRuntime.typeErrorById("msg.class.derived.return");
            }
            return (Scriptable) get();
        }
    }

    /** OrdinaryCreateFromConstructor(newTarget, "%Object.prototype%") for a base class. */
    static Scriptable createInstance(BaseFunction f, Context cx, VarScope scope, Object nt) {
        if (nt instanceof JSFunction || !(nt instanceof Scriptable)) {
            return f.createObject(cx, scope, nt);
        }
        Scriptable instance = f.createObject(cx, scope, nt);
        Object proto = ScriptableObject.getProperty((Scriptable) nt, "prototype");
        if (proto instanceof Scriptable) {
            instance.setPrototype((Scriptable) proto);
        }
        return instance;
    }

    /** The constructor of a class that has none (ECMA-262 15.7.14 step 14.a). */
    static final class DefaultConstructor extends BaseFunction {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final boolean derived;

        DefaultConstructor(VarScope scope, String name, boolean derived) {
            super(scope);
            this.name = name;
            this.derived = derived;
        }

        @Override
        public String getFunctionName() {
            return name;
        }

        @Override
        public int getLength() {
            return 0;
        }

        @Override
        public int getArity() {
            return 0;
        }

        @Override
        public Object call(Context cx, Object nt, VarScope s, Object thisObj, Object[] args) {
            throw ScriptRuntime.typeErrorById("msg.class.not.new", name);
        }

        @Override
        public Scriptable construct(Context cx, Object nt, VarScope scope, Object[] args) {
            if (nt == null || Undefined.isUndefined(nt)) {
                nt = this;
            }
            if (derived) {
                // constructor(...args) { super(...args); }
                return (Scriptable)
                        new DerivedThis(this, nt).superCallee().call(cx, scope, null, args);
            }
            return createInstance(this, cx, scope, nt);
        }
    }
}
