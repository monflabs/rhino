package org.monflabs.mozilla.javascript.optimizer;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import org.monflabs.mozilla.classfile.ByteCode;
import org.monflabs.mozilla.classfile.ClassFileWriter;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.JSCode;
import org.monflabs.mozilla.javascript.JSCodeExec;
import org.monflabs.mozilla.javascript.JSCodeResume;
import org.monflabs.mozilla.javascript.JSFunction;
import org.monflabs.mozilla.javascript.ScriptOrFn;
import org.monflabs.mozilla.javascript.VarScope;

/** Subclass of {@link JSCode} for compiled Java methods. */
public class MHJSFunctionCode extends MHJSCode<JSFunction> {
    private JSCodeExec<JSFunction> exec;
    private final JSCodeResume<JSFunction> resume;

    private final MethodHandle execMH;

    /** The lookup of the compiled class until the method is linked (first call), null after. */
    private Lookup lookup;

    protected MHJSFunctionCode(MethodHandle execMH, MethodHandle resumeMH) {
        this(null, execMH, resumeMH);
    }

    MHJSFunctionCode(Lookup lookup, MethodHandle execMH, MethodHandle resumeMH) {
        this.execMH = execMH;
        this.lookup = lookup;
        if (lookup == null) {
            exec = handleCall(execMH);
        }
        resume =
                resumeMH != null
                        ? (cx, xobj, state, scope, operation, value) -> {
                            try {
                                return resumeMH.invokeExact(
                                        cx, xobj, state, scope, operation, value);
                            } catch (Error e) {
                                throw e;
                            } catch (Throwable e) {
                                throw (RuntimeException) e;
                            }
                        }
                        : (cx, xobj, state, scope, operation, value) -> {
                            throw new UnsupportedOperationException();
                        };
    }

    private static JSCodeExec<JSFunction> handleCall(MethodHandle execMH) {
        return (cx, xobj, newTarget, scope, thisObj, args) -> {
            try {
                return execMH.invokeExact(cx, xobj, newTarget, scope, thisObj, args);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                throw (RuntimeException) e;
            }
        };
    }

    @Override
    public Object execute(
            Context cx,
            JSFunction executableObject,
            Object newTarget,
            VarScope scope,
            Object thisObj,
            Object[] args) {
        if (lookup != null) {
            link();
        }
        return exec.execute(cx, executableObject, newTarget, scope, thisObj, args);
    }

    /**
     * Makes the call go through an instance of a class whose method calls the compiled method
     * directly, rather than through the method handle, which the JIT does not inline (it is not a
     * constant). Done on the first call, so that a function that never runs does not get a class.
     */
    @SuppressWarnings("unchecked")
    private synchronized void link() {
        Lookup l = lookup;
        if (l == null) {
            return;
        }
        JSCodeExec<JSFunction> direct;
        try {
            MethodType sam =
                    MethodType.methodType(
                            Object.class,
                            Context.class,
                            ScriptOrFn.class,
                            Object.class,
                            VarScope.class,
                            Object.class,
                            Object[].class);
            direct =
                    (JSCodeExec<JSFunction>)
                            LambdaMetafactory.metafactory(
                                            l,
                                            "execute",
                                            MethodType.methodType(JSCodeExec.class),
                                            sam,
                                            execMH,
                                            execMH.type())
                                    .getTarget()
                                    .invoke();
        } catch (Throwable e) {
            direct = handleCall(execMH);
        }
        exec = direct;
        lookup = null;
    }

    @Override
    public Object resume(
            Context cx,
            JSFunction executableObject,
            Object state,
            VarScope scope,
            int operation,
            Object value) {
        return resume.resume(cx, executableObject, state, scope, operation, value);
    }

    public static class Builder extends MHJSCode.Builder<JSFunction> {

        Builder(MHJSCode.BuilderEnv env) {
            super(env);
        }

        @Override
        protected MHJSCode<JSFunction> buildCode(MethodHandle exec, MethodHandle resume) {
            return new MHJSFunctionCode(lookup, exec, resume);
        }

        @Override
        public void buildByteCode(ClassFileWriter cfw, String mainClass) {
            cfw.addInvoke(
                    ByteCode.INVOKESTATIC,
                    mainClass,
                    "getLookup",
                    "()Ljava/lang/invoke/MethodHandles$Lookup;");
            cfw.addLoadConstantClass(mainClass);
            cfw.addLoadConstant(methodName);
            cfw.addLoadConstant(methodType);
            if (resumeName == null) {
                cfw.add(ByteCode.ACONST_NULL);
                cfw.add(ByteCode.ACONST_NULL);
            } else {
                cfw.addLoadConstant(resumeName);
                cfw.addLoadConstant(resumeType);
            }
            var className = MHJSFunctionCode.class.getName();
            var signature = className.replaceAll("\\.", "/");
            var type =
                    MethodType.methodType(
                                    MHJSFunctionCode.class,
                                    Lookup.class,
                                    Class.class,
                                    String.class,
                                    String.class,
                                    String.class,
                                    String.class)
                            .toMethodDescriptorString();
            cfw.addInvoke(ByteCode.INVOKESTATIC, signature, "makeJSCode", type);
        }
    }

    public static MHJSFunctionCode makeJSCode(
            Lookup lookup,
            Class<?> clazz,
            String methodName,
            String methodType,
            String resumeName,
            String resumeType) {
        try {
            MethodHandle exec =
                    lookup.findStatic(
                            clazz,
                            methodName,
                            MethodType.fromMethodDescriptorString(
                                    methodType, clazz.getClassLoader()));
            MethodHandle resume = null;
            if (resumeName != null) {
                resume =
                        lookup.findStatic(
                                clazz,
                                resumeName,
                                MethodType.fromMethodDescriptorString(
                                        resumeType, clazz.getClassLoader()));
            }
            return new MHJSFunctionCode(exec, resume);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new Error("Gnerated class did not contain expected methods", e);
        }
    }
}
