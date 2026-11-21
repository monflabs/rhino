/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

/**
 * ClassDefinitionEvaluation (ECMA-262 15.7.14): {@link #define} creates the constructor and the
 * prototype from the constructor of the class (or none, for the default constructor) and its
 * heritage, then {@link #element} adds each element of the class body, in source order.
 */
final class ClassDefinition {

    private ClassDefinition() {}

    /**
     * A field of a class (ClassFieldDefinition): its key and its initializer, or null; a static
     * block has no key. {@code named}: the initializer is an anonymous function definition, which
     * the key names (NamedEvaluation).
     */
    static final class Element implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final Object key;
        final Object initializer;
        final boolean named;

        Element(Object key, Object initializer, boolean named) {
            this.key = key;
            this.initializer = initializer;
            this.named = named;
        }
    }

    /**
     * ClassElementEvaluation of an element of the class whose constructor is f: flags is its kind
     * and its Node#ELEMENT_* flags, key its key as evaluated (a PrivateName for a private element,
     * null for a static block), and value its function: the method, the accessor, the initializer
     * of a field (null without one) or the static block.
     */
    static void element(BaseFunction f, Object key, Object value, int flags, Context cx) {
        boolean isStatic = (flags & Node.ELEMENT_STATIC) != 0;
        Scriptable home = isStatic ? f : (Scriptable) f.getPrototypeProperty();
        if (value instanceof JSFunction) {
            ((JSFunction) value).initClassHomeObject(home);
        }
        int kind = flags & Node.ELEMENT_KIND_MASK;
        if (kind == Node.ELEMENT_STATIC_BLOCK) {
            f.classStaticElements = append(f.classStaticElements, new Element(null, value, false));
            return;
        }
        if (!(key instanceof PrivateName)) {
            key = ScriptRuntime.toPropertyKey(key);
        }
        if (kind == Node.ELEMENT_FIELD) {
            Element field = new Element(key, value, (flags & Node.ELEMENT_NAMED) != 0);
            if (isStatic) {
                f.classStaticElements = append(f.classStaticElements, field);
            } else {
                f.classFields = append(f.classFields, field);
            }
            return;
        }
        if ((flags & Node.ELEMENT_NAMED) != 0 && value instanceof BaseFunction) {
            nameAfterKey(
                    (BaseFunction) value,
                    key,
                    kind == Node.ELEMENT_GETTER
                            ? "get "
                            : kind == Node.ELEMENT_SETTER ? "set " : "");
        }
        if (key instanceof PrivateName) {
            PrivateName name = (PrivateName) key;
            if (isStatic) {
                PrivateName.defineMethod(
                        f, method(PrivateName.findMethod(f, name), name, value, kind));
            } else {
                f.classPrivateMethods = define(f.classPrivateMethods, name, value, kind);
            }
            return;
        }
        // DefineMethodProperty: not enumerable
        ScriptableObject target = (ScriptableObject) home;
        Object id = key;
        if (key instanceof String) {
            ScriptRuntime.StringIdOrIndex s = ScriptRuntime.toStringIdOrIndex(key);
            id = s.stringId == null ? Integer.valueOf(s.index) : s.stringId;
        }
        Object none = Scriptable.NOT_FOUND;
        ScriptableObject.DescriptorInfo desc =
                kind == Node.ELEMENT_METHOD
                        ? new ScriptableObject.DescriptorInfo(false, true, true, value)
                        : new ScriptableObject.DescriptorInfo(
                                Boolean.FALSE,
                                none,
                                Boolean.TRUE,
                                kind == Node.ELEMENT_GETTER ? value : none,
                                kind == Node.ELEMENT_SETTER ? value : none,
                                none);
        target.defineOwnProperty(cx, id, desc);
    }

    private static Element[] append(Element[] elements, Element element) {
        if (elements == null) {
            return new Element[] {element};
        }
        Element[] result = java.util.Arrays.copyOf(elements, elements.length + 1);
        result[elements.length] = element;
        return result;
    }

    /** A private method, or the accessor that existing is the other half of (null: none). */
    private static PrivateName.Method method(
            PrivateName.Method existing, PrivateName name, Object function, int kind) {
        if (kind == Node.ELEMENT_METHOD) {
            return new PrivateName.Method(name, function, null, null);
        }
        PrivateName.Method accessor =
                existing != null ? existing : new PrivateName.Method(name, null, null, null);
        if (kind == Node.ELEMENT_GETTER) {
            accessor.getter = function;
        } else {
            accessor.setter = function;
        }
        return accessor;
    }

    /** The private methods of the instances with one more, a getter and a setter combined. */
    private static PrivateName.Method[] define(
            PrivateName.Method[] methods, PrivateName name, Object function, int kind) {
        if (methods != null) {
            for (PrivateName.Method m : methods) {
                if (m.name == name) {
                    method(m, name, function, kind);
                    return methods;
                }
            }
        }
        PrivateName.Method added = method(null, name, function, kind);
        if (methods == null) {
            return new PrivateName.Method[] {added};
        }
        PrivateName.Method[] result = java.util.Arrays.copyOf(methods, methods.length + 1);
        result[methods.length] = added;
        return result;
    }

    /** InitializeInstanceElements (7.3.34) of the fields of a class, on a new instance. */
    static void initializeInstanceElements(
            BaseFunction constructor, Scriptable instance, Context cx, VarScope scope) {
        PrivateName.Method[] methods = constructor.classPrivateMethods;
        if (methods != null) {
            for (PrivateName.Method method : methods) {
                PrivateName.addMethod(instance, method);
            }
        }
        Element[] fields = constructor.classFields;
        if (fields == null) {
            return;
        }
        for (Element field : fields) {
            defineField(field, instance, cx, scope);
        }
    }

    /**
     * The static fields of a class, defined on its constructor once its name is bound (15.7.14 step
     * 31, Token#CLASS_STATIC); returns the constructor.
     */
    static Object runStaticElements(Object constructor, Context cx, VarScope scope) {
        if (constructor instanceof BaseFunction) {
            BaseFunction f = (BaseFunction) constructor;
            Element[] elements = f.classStaticElements;
            f.classStaticElements = null;
            if (elements != null) {
                for (Element element : elements) {
                    defineField(element, f, cx, scope);
                }
            }
        }
        return constructor;
    }

    /** DefineField (7.3.33): the initializer is called with the receiver as this. */
    private static void defineField(
            Element field, Scriptable receiver, Context cx, VarScope scope) {
        Object value = Undefined.instance;
        if (field.initializer instanceof Callable) {
            value =
                    ((Callable) field.initializer)
                            .call(cx, scope, receiver, ScriptRuntime.emptyArgs);
            if (field.named && value instanceof BaseFunction) {
                nameAfterKey((BaseFunction) value, field.key, "");
            }
        }
        if (field.key == null) {
            // a static block
            return;
        }
        if (field.key instanceof PrivateName) {
            PrivateName.addField(receiver, (PrivateName) field.key, value);
            return;
        }
        // CreateDataPropertyOrThrow
        var desc = new ScriptableObject.DescriptorInfo(true, true, true, value);
        if (receiver instanceof ScriptableObject) {
            if (!((ScriptableObject) receiver).defineOwnProperty(cx, field.key, desc)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.class.field.define", ScriptRuntime.toString(field.key));
            }
        } else if (field.key instanceof Symbol) {
            ((SymbolScriptable) receiver).put((Symbol) field.key, receiver, value);
        } else {
            receiver.put(ScriptRuntime.toString(field.key), receiver, value);
        }
    }

    /** SetFunctionName(F, key, prefix) of an anonymous function that its key names. */
    private static void nameAfterKey(BaseFunction f, Object key, String prefix) {
        if (!"".equals(f.get("name", f))) {
            return;
        }
        if (key instanceof PrivateName) {
            f.setFunctionName(prefix + ((PrivateName) key).getDescription());
        } else if (key instanceof Symbol) {
            String description = ((Symbol) key).getName();
            f.setFunctionName(description.isEmpty() ? prefix : prefix + "[" + description + "]");
        } else {
            f.setFunctionName(prefix + ScriptRuntime.toString(key));
        }
    }

    static Scriptable define(
            Object constructor,
            Object heritage,
            boolean hasHeritage,
            boolean namedByLiteral,
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
            f = new DefaultConstructor(scope, name, hasHeritage, namedByLiteral);
            ScriptRuntime.setFunctionProtoAndParent(f, cx, scope);
        }
        f.setPrototype(constructorParent);

        // the prototype is the home object of the constructor and of the methods
        ScriptableObject proto = (ScriptableObject) cx.newObject(scope);
        proto.setPrototype(protoParent);
        proto.defineProperty("constructor", f, ScriptableObject.DONTENUM);
        if (f instanceof JSFunction) {
            ((JSFunction) f).initClassHomeObject(proto);
        }
        f.setPrototypeProperty(proto);
        f.setPrototypePropertyAttributes(
                ScriptableObject.DONTENUM | ScriptableObject.READONLY | ScriptableObject.PERMANENT);
        return f;
    }

    /** The heritage in an error message: an object is not converted (it may be a proxy). */
    private static String describe(Object heritage) {
        return heritage instanceof Scriptable
                ? ScriptRuntime.typeof(heritage)
                : ScriptRuntime.toString(heritage);
    }

    private static boolean isConstructor(Object value) {
        return AbstractEcmaObjectOperations.isConstructor(Context.getCurrentContext(), value);
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
                // the fields of a derived class are defined once super() returns
                initializeInstanceElements(constructor, result, cx, scope);
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
        Scriptable instance = f.createObject(cx, scope, nt);
        if (!(nt instanceof JSFunction) && nt instanceof Scriptable) {
            Object proto = ScriptableObject.getProperty((Scriptable) nt, "prototype");
            if (proto instanceof Scriptable) {
                instance.setPrototype((Scriptable) proto);
            }
        }
        // the fields of a base class are defined before its constructor runs
        initializeInstanceElements(f, instance, cx, scope);
        return instance;
    }

    /** The constructor of a class that has none (ECMA-262 15.7.14 step 14.a). */
    static final class DefaultConstructor extends BaseFunction {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final boolean derived;
        final boolean namedByLiteral;

        DefaultConstructor(VarScope scope, String name, boolean derived, boolean namedByLiteral) {
            super(scope);
            this.name = name;
            this.derived = derived;
            this.namedByLiteral = namedByLiteral;
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
