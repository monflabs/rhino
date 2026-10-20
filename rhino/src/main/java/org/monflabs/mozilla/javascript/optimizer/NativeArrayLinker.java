package org.monflabs.mozilla.javascript.optimizer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.support.Guards;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.NativeArray;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.VarScope;

/**
 * This linker optimizes accesses to the "length" property of native arrays by delegating directly
 * to the native code. It helps in the common case that code is iterating over the length of an
 * array. Element reads and writes at an index of a dense array go to its elements, and take the
 * general path otherwise; the methods are small and linked per call site, so they inline whatever
 * other scripts did with the general path.
 */
@SuppressWarnings("AndroidJdkLibsChecker")
class NativeArrayLinker implements TypeBasedGuardingDynamicLinker {
    @Override
    public boolean canLinkType(Class<?> type) {
        return NativeArray.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest req, LinkerServices svc)
            throws Exception {
        if (req.isCallSiteUnstable()) {
            return null;
        }

        ParsedOperation op = new ParsedOperation(req.getCallSiteDescriptor().getOperation());
        MethodHandle mh = null;
        MethodHandle guard = null;

        if (op.isNamespace(StandardNamespace.PROPERTY)) {
            if (op.isOperation(StandardOperation.GET, RhinoOperation.GETNOWARN)
                    && "length".equals(op.getName())) {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType mType = req.getCallSiteDescriptor().getMethodType();
                mh = lookup.findStatic(NativeArrayLinker.class, "getLength", mType);
                guard = Guards.getInstanceOfGuard(NativeArray.class);
            } else if (op.isOperation(RhinoOperation.GETELEMENT, RhinoOperation.GETINDEX)
                    || op.isOperation(RhinoOperation.SETELEMENT, RhinoOperation.SETINDEX)) {
                String name =
                        op.isOperation(RhinoOperation.GETELEMENT)
                                ? "getElement"
                                : op.isOperation(RhinoOperation.GETINDEX)
                                        ? "getIndex"
                                        : op.isOperation(RhinoOperation.SETELEMENT)
                                                ? "setElement"
                                                : "setIndex";
                MethodType mType = req.getCallSiteDescriptor().getMethodType();
                mh = MethodHandles.lookup().findStatic(NativeArrayLinker.class, name, mType);
                guard = Guards.getClassGuard(NativeArray.class);
            }
        }

        if (mh != null) {
            assert guard != null;
            if (DefaultLinker.DEBUG) {
                System.out.println(op + " native array operation");
            }
            return new GuardedInvocation(mh, guard);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static Object getLength(Object o, Context cx, VarScope scope) {
        long length = ((NativeArray) o).getLength();
        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return (double) length;
    }

    @SuppressWarnings("unused")
    private static Object getElement(Object o, Object id, Context cx, VarScope scope) {
        if (id instanceof Integer) {
            Object v = ((NativeArray) o).getDenseElement((Integer) id);
            if (v != Scriptable.NOT_FOUND) {
                return v;
            }
        } else if (id instanceof Double) {
            double d = (Double) id;
            int i = (int) d;
            if (i == d) {
                Object v = ((NativeArray) o).getDenseElement(i);
                if (v != Scriptable.NOT_FOUND) {
                    return v;
                }
            }
        }
        return ScriptRuntime.getObjectElem(o, id, cx, scope);
    }

    @SuppressWarnings("unused")
    private static Object getIndex(Object o, double index, Context cx, VarScope scope) {
        int i = (int) index;
        if (i == index) {
            Object v = ((NativeArray) o).getDenseElement(i);
            if (v != Scriptable.NOT_FOUND) {
                return v;
            }
        }
        return ScriptRuntime.getObjectIndex(o, index, cx, scope);
    }

    @SuppressWarnings("unused")
    private static Object setElement(
            Object o, Object id, Object value, Context cx, VarScope scope) {
        if (id instanceof Integer) {
            if (((NativeArray) o).setDenseElement((Integer) id, value)) {
                return value;
            }
        } else if (id instanceof Double) {
            double d = (Double) id;
            int i = (int) d;
            if (i == d && ((NativeArray) o).setDenseElement(i, value)) {
                return value;
            }
        }
        return ScriptRuntime.setObjectElem(o, id, value, cx, scope);
    }

    @SuppressWarnings("unused")
    private static Object setIndex(
            Object o, double index, Object value, Context cx, VarScope scope) {
        int i = (int) index;
        if (i == index && ((NativeArray) o).setDenseElement(i, value)) {
            return value;
        }
        return ScriptRuntime.setObjectIndex(o, index, value, cx, scope);
    }
}
