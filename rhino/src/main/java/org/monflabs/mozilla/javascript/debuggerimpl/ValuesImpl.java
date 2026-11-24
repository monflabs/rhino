/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.debuggerimpl;

import java.util.ArrayList;
import java.util.List;
import org.monflabs.mozilla.api.debugger.DebugException;
import org.monflabs.mozilla.api.debugger.DebugProperty;
import org.monflabs.mozilla.api.debugger.DebugValues;
import org.monflabs.mozilla.api.debugger.ExecutionContext;
import org.monflabs.mozilla.javascript.Callable;
import org.monflabs.mozilla.javascript.NativeArray;
import org.monflabs.mozilla.javascript.NativeSymbol;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;

final class ValuesImpl implements DebugValues {
    private final DebuggerImpl debugger;

    ValuesImpl(DebuggerImpl debugger) {
        this.debugger = debugger;
    }

    @Override
    public String type(Object v) {
        if (v == null) {
            return "object";
        }
        if (Undefined.isUndefined(v)) {
            return "undefined";
        }
        if (v instanceof CharSequence) {
            return "string";
        }
        if (v instanceof Boolean) {
            return "boolean";
        }
        if (v instanceof Number) {
            return "number";
        }
        if (v instanceof NativeSymbol) {
            return "symbol";
        }
        if (v instanceof java.math.BigInteger) {
            return "bigint";
        }
        return v instanceof Callable ? "function" : "object";
    }

    @Override
    public String subtype(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof NativeArray) {
            return "array";
        }
        if (v instanceof Scriptable s) {
            String c = s.getClassName();
            return switch (c) {
                case "Error" -> "error";
                case "RegExp" -> "regexp";
                case "Date" -> "date";
                case "Map" -> "map";
                case "Set" -> "set";
                case "Promise" -> "promise";
                default -> null;
            };
        }
        return null;
    }

    @Override
    public String className(Object v) {
        return v instanceof Scriptable s ? s.getClassName() : null;
    }

    @Override
    public String description(Object v) {
        try {
            if (v instanceof Callable) {
                return ScriptRuntime.toString(v);
            }
            if (v instanceof NativeArray a) {
                return "Array(" + a.getLength() + ")";
            }
            if (v instanceof Scriptable s && !(v instanceof NativeSymbol)) {
                if ("Error".equals(s.getClassName())) {
                    return ScriptRuntime.toString(v);
                }
                return s.getClassName();
            }
            return v == null ? "null" : ScriptRuntime.toString(v);
        } catch (RuntimeException e) {
            return String.valueOf(v);
        }
    }

    @Override
    public boolean isPrimitive(Object v) {
        return !(v instanceof Scriptable) || v instanceof NativeSymbol;
    }

    @Override
    public Object toJava(Object v) {
        return v instanceof CharSequence ? v.toString() : v;
    }

    @Override
    public String unserializable(Object v) {
        if (v instanceof Double d) {
            if (d.isNaN()) {
                return "NaN";
            }
            if (d.isInfinite()) {
                return d > 0 ? "Infinity" : "-Infinity";
            }
            if (d == 0 && 1 / d < 0) {
                return "-0";
            }
        }
        return v instanceof java.math.BigInteger b ? b + "n" : null;
    }

    @Override
    public List<DebugProperty> ownProperties(
            Object object, boolean includeNonEnumerable, boolean includeIndexed) {
        List<DebugProperty> result = new ArrayList<>();
        if (object instanceof TopLevel tl) {
            object = tl.getGlobalThis();
        }
        if (!(object instanceof ScriptableObject so)) {
            if (object instanceof VarScope vs) {
                for (Object id : vs.getIds()) {
                    String name = String.valueOf(id);
                    result.add(
                            new DebugProperty(
                                    name,
                                    id,
                                    vs.get(name, vs),
                                    null,
                                    null,
                                    true,
                                    true,
                                    false,
                                    true,
                                    false));
                }
            }
            return result;
        }
        Object[] ids = includeNonEnumerable ? so.getAllIds() : so.getIds();
        for (Object id : ids) {
            boolean indexed = id instanceof Number;
            if (indexed && !includeIndexed) {
                continue;
            }
            String name = String.valueOf(id);
            Object value;
            boolean thrown = false;
            try {
                value =
                        indexed
                                ? ScriptableObject.getProperty(so, ((Number) id).intValue())
                                : ScriptableObject.getProperty(so, name);
            } catch (RuntimeException e) {
                value = e.getMessage();
                thrown = true;
            }
            int attrs = 0;
            try {
                attrs = so.getAttributes(name);
            } catch (RuntimeException e) {
                // no attributes for this id
            }
            result.add(
                    new DebugProperty(
                            name,
                            id,
                            value,
                            null,
                            null,
                            (attrs & ScriptableObject.READONLY) == 0,
                            (attrs & ScriptableObject.DONTENUM) == 0,
                            (attrs & ScriptableObject.PERMANENT) == 0,
                            true,
                            thrown));
        }
        return result;
    }

    @Override
    public List<DebugProperty> internalProperties(Object object) {
        Object proto = prototype(object);
        if (proto == null) {
            return List.of();
        }
        return List.of(
                new DebugProperty(
                        "[[Prototype]]",
                        "[[Prototype]]",
                        proto,
                        null,
                        null,
                        false,
                        false,
                        false,
                        true,
                        false));
    }

    @Override
    public Object prototype(Object object) {
        return object instanceof Scriptable s ? s.getPrototype() : null;
    }

    @Override
    public long arrayLength(Object array) {
        return array instanceof NativeArray a ? a.getLength() : 0;
    }

    @Override
    public void setProperty(Object object, Object key, Object value) {
        if (object instanceof Scriptable s) {
            if (key instanceof Number n) {
                ScriptableObject.putProperty(s, n.intValue(), value);
            } else {
                ScriptableObject.putProperty(s, String.valueOf(key), value);
            }
        }
    }

    @Override
    public Object callFunction(Object function, Object thisValue, Object... arguments)
            throws DebugException {
        try {
            return debugger.run(
                    cx -> {
                        Scriptable thisObj = thisValue instanceof Scriptable s ? s : null;
                        Callable f = (Callable) function;
                        VarScope scope =
                                function instanceof Scriptable fs && fs.getParentScope() != null
                                        ? fs.getParentScope()
                                        : null;
                        return f.call(cx, scope, thisObj, arguments);
                    });
        } catch (Exception e) {
            throw DebuggerImpl.asDebugException(e);
        }
    }

    @Override
    public Object evaluateWith(ExecutionContext context, String expression, Object thisValue)
            throws DebugException {
        if (thisValue == null) {
            return debugger.evaluate(context, expression);
        }
        try {
            return debugger.run(
                    cx -> {
                        Scriptable thisObj = (Scriptable) thisValue;
                        Callable f =
                                (Callable)
                                        cx.evaluateString(
                                                (VarScope) ((DebuggerImpl.ContextImpl) context).top,
                                                "(function(){ return eval("
                                                        + quote(expression)
                                                        + "); })",
                                                "<debugger>",
                                                1,
                                                null);
                        return f.call(
                                cx,
                                (VarScope) ((DebuggerImpl.ContextImpl) context).top,
                                thisObj,
                                new Object[0]);
                    });
        } catch (Exception e) {
            throw DebuggerImpl.asDebugException(e);
        }
    }

    private static String quote(String s) {
        return ScriptRuntime.escapeString(s, '"') == null
                ? "\"\""
                : "\"" + ScriptRuntime.escapeString(s, '"') + "\"";
    }
}
