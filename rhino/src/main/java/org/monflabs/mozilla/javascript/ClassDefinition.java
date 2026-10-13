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

    /**
     * The key of a field, in the literal of the members of a class (Token#FIELD_KEY): the entry is
     * not a property of the literal but a field of the class, defined on each instance (or once on
     * the constructor, for a static field). {@code named}: the initializer is an anonymous function
     * definition, which the key names (NamedEvaluation). {@code method}: the entry is a private
     * method or accessor, whose key is a PrivateName.
     */
    static final class FieldKey implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final Object key;
        final boolean named;
        final boolean method;

        FieldKey(Object key, boolean named, boolean method) {
            this.key = key;
            this.named = named;
            this.method = method;
        }
    }

    /**
     * A field of a class (ClassFieldDefinition): its key and its initializer, or null; or a private
     * method or accessor, whose initializer is the function, and getterSetter as in
     * fillObjectLiteral.
     */
    static final class Element implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final Object key;
        final Object initializer;
        final boolean named;
        final boolean method;
        final int getterSetter;

        Element(FieldKey key, Object initializer, int getterSetter) {
            this.key = key.key;
            this.initializer = initializer;
            this.named = key.named;
            this.method = key.method;
            this.getterSetter = getterSetter;
        }
    }

    private static final String ELEMENTS = "ClassDefinition.elements";

    /** Records a field entry of the literal of the members of a class (fillObjectLiteral). */
    @SuppressWarnings("unchecked")
    static void addElement(Scriptable literal, FieldKey key, Object initializer, int getterSetter) {
        ScriptableObject obj = (ScriptableObject) literal;
        java.util.List<Element> elements =
                (java.util.List<Element>) obj.getAssociatedValue(ELEMENTS);
        if (elements == null) {
            elements = new java.util.ArrayList<>();
            obj.associateValue(ELEMENTS, elements);
        }
        elements.add(new Element(key, initializer, getterSetter));
    }

    @SuppressWarnings("unchecked")
    private static Element[] takeElements(ScriptableObject literal) {
        java.util.List<Element> elements =
                (java.util.List<Element>) literal.getAssociatedValue(ELEMENTS);
        if (elements == null || elements.isEmpty()) {
            return null;
        }
        Element[] result = elements.toArray(new Element[0]);
        elements.clear();
        return result;
    }

    /**
     * The private methods and accessors among elements, a getter and a setter of the same name
     * combined; null if there are none.
     */
    private static PrivateName.Method[] privateMethods(Element[] elements) {
        if (elements == null) {
            return null;
        }
        java.util.Map<Object, PrivateName.Method> methods = new java.util.LinkedHashMap<>();
        for (Element element : elements) {
            if (!element.method) {
                continue;
            }
            PrivateName name = (PrivateName) element.key;
            if (element.getterSetter == 0) {
                methods.put(name, new PrivateName.Method(name, element.initializer, null, null));
                continue;
            }
            PrivateName.Method accessor =
                    methods.computeIfAbsent(
                            name, k -> new PrivateName.Method(name, null, null, null));
            if (element.getterSetter == -1) {
                accessor.getter = element.initializer;
            } else {
                accessor.setter = element.initializer;
            }
        }
        return methods.isEmpty() ? null : methods.values().toArray(new PrivateName.Method[0]);
    }

    /** The fields among elements, or null if there are none. */
    private static Element[] fields(Element[] elements) {
        if (elements == null) {
            return null;
        }
        Element[] fields =
                java.util.Arrays.stream(elements).filter(e -> !e.method).toArray(Element[]::new);
        return fields.length == 0 ? null : fields;
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
                nameAfterKey((BaseFunction) value, field.key);
            }
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

    /** SetFunctionName(F, key) of an anonymous function that a computed field key names. */
    private static void nameAfterKey(BaseFunction f, Object key) {
        if (!"".equals(f.get("name", f))) {
            return;
        }
        if (key instanceof PrivateName) {
            f.setFunctionName(((PrivateName) key).getDescription());
        } else if (key instanceof Symbol) {
            String description = ((Symbol) key).getName();
            f.setFunctionName(description.isEmpty() ? "" : "[" + description + "]");
        } else {
            f.setFunctionName(ScriptRuntime.toString(key));
        }
    }

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
        Element[] instanceElements = takeElements(proto);
        f.classFields = fields(instanceElements);
        f.classPrivateMethods = privateMethods(instanceElements);
        Element[] staticElements = takeElements(statics);
        if (staticElements != null) {
            // the home object of a static initializer or private method is the constructor
            for (Element element : staticElements) {
                if (element.initializer instanceof JSFunction
                        && ((JSFunction) element.initializer).getHomeObject() == statics) {
                    ((JSFunction) element.initializer).initClassHomeObject(f);
                }
            }
            PrivateName.Method[] staticMethods = privateMethods(staticElements);
            if (staticMethods != null) {
                for (PrivateName.Method method : staticMethods) {
                    PrivateName.addMethod(f, method);
                }
            }
            f.classStaticElements = fields(staticElements);
        }
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
