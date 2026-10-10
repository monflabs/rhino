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
                throw ScriptRuntime.typeErrorById(
                        "msg.class.heritage", ScriptRuntime.toString(heritage));
            } else {
                Object parentProto =
                        ScriptableObject.getProperty((Scriptable) heritage, "prototype");
                if (parentProto == null) {
                    protoParent = null;
                } else if (parentProto instanceof Scriptable) {
                    protoParent = (Scriptable) parentProto;
                } else {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.heritage", ScriptRuntime.toString(heritage));
                }
                constructorParent = (Scriptable) heritage;
            }
        }

        BaseFunction f;
        if (constructor instanceof BaseFunction) {
            f = (BaseFunction) constructor;
        } else {
            f =
                    new DefaultConstructor(
                            scope, name, hasHeritage && heritage != null ? heritage : null);
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
        f.setPrototypeProperty(proto);
        f.setPrototypePropertyAttributes(
                ScriptableObject.DONTENUM | ScriptableObject.READONLY | ScriptableObject.PERMANENT);

        ScriptableObject statics = (ScriptableObject) staticMembers;
        for (Object id : ownKeys(statics)) {
            ScriptableObject.DescriptorInfo desc = statics.getOwnPropertyDescriptor(cx, id);
            desc.enumerable = Boolean.FALSE;
            f.defineOwnProperty(cx, id, desc);
        }
        return f;
    }

    private static Object[] ownKeys(ScriptableObject obj) {
        try (var map = obj.startCompoundOp(false)) {
            return obj.getIds(map, true, true);
        }
    }

    private static boolean isConstructor(Object value) {
        if (value instanceof BaseFunction) {
            return ((BaseFunction) value).isConstructor();
        }
        return value instanceof Function;
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

    /** The constructor of a class that has none (ECMA-262 15.7.14 step 14.a). */
    static final class DefaultConstructor extends BaseFunction {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final Object parent;

        DefaultConstructor(VarScope scope, String name, Object parent) {
            super(scope);
            this.name = name;
            this.parent = parent;
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
            if (parent instanceof Constructable) {
                // constructor(...args) { super(...args); }
                return ((Constructable) parent).construct(cx, nt, scope, args);
            }
            return createObject(cx, scope, nt);
        }
    }
}
