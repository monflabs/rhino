package org.monflabs.mozilla.classfile.tests;

import static org.junit.jupiter.api.Assertions.*;
import static org.monflabs.mozilla.classfile.ClassFileWriter.ACC_PUBLIC;
import static org.monflabs.mozilla.classfile.ClassFileWriter.ACC_STATIC;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.classfile.ByteCode;
import org.monflabs.mozilla.classfile.ClassFileWriter;
import org.monflabs.mozilla.javascript.DefiningClassLoader;

public class ClassFileWriterTest {
    /**
     * A method that refers to a method past the 65,535 entries of the constant pool: its index
     * wrapped, and the stack map read another constant (a NullPointerException) before the class
     * was written.
     */
    @Test
    public void constantPoolOverflowIsAClassSizeLimit() {
        ClassFileWriter cfw =
                new ClassFileWriter("TestConstantPool", "java/lang/Object", "ClassFileWriterTest");
        for (int m = 0; m < 16; m++) {
            // 2 entries per string
            cfw.startMethod("strings" + m, "()V", (short) (ACC_PUBLIC | ACC_STATIC));
            for (int i = 0; i < 2000; i++) {
                cfw.addLoadConstant("k" + (m * 2000 + i));
                cfw.add(ByteCode.POP);
            }
            cfw.add(ByteCode.RETURN);
            cfw.stopMethod((short) 0);
        }
        cfw.startMethod("calls", "()V", (short) (ACC_PUBLIC | ACC_STATIC));
        for (int i = 0; i < 1000; i++) {
            cfw.addInvoke(ByteCode.INVOKESTATIC, "TestConstantPool", "m" + i, "()V");
        }
        cfw.add(ByteCode.RETURN);
        assertThrows(ClassFileWriter.ClassSizeException.class, () -> cfw.stopMethod((short) 0));
    }

    /**
     * Jumps over 32 KB of code: a conditional branch, a forward goto and a backward one, as goto_w.
     */
    @Test
    public void wideJumps() throws Exception {
        ClassFileWriter cfw =
                new ClassFileWriter("TestWideJumps", "java/lang/Object", "ClassFileWriterTest");
        cfw.setWideJumps(true);
        // public static int count(int n) { int r = 0; while (n > 0) { r++; n--; } return r; }
        cfw.startMethod("count", "(I)I", (short) (ACC_PUBLIC | ACC_STATIC));
        cfw.addPush(0);
        cfw.addIStore(1);
        int top = cfw.acquireLabel();
        int end = cfw.acquireLabel();
        int body = cfw.acquireLabel();
        cfw.markLabel(top);
        cfw.addILoad(0);
        cfw.add(ByteCode.IFGT, body);
        cfw.add(ByteCode.GOTO, end);
        cfw.markLabel(body);
        for (int i = 0; i < 40000; i++) {
            cfw.add(ByteCode.NOP);
        }
        cfw.add(ByteCode.IINC, 1, 1);
        cfw.addILoad(0);
        cfw.addPush(1);
        cfw.add(ByteCode.ISUB);
        cfw.addIStore(0);
        cfw.add(ByteCode.GOTO, top);
        cfw.markLabel(end);
        cfw.addILoad(1);
        cfw.add(ByteCode.IRETURN);
        cfw.stopMethod((short) 2);

        Class<?> cl = new DefiningClassLoader().defineClass("TestWideJumps", cfw.toByteArray());
        assertEquals(5, cl.getMethod("count", int.class).invoke(null, 5));
        assertEquals(0, cl.getMethod("count", int.class).invoke(null, -1));
    }

    @Test
    public void stackMapTable() throws Exception {
        final String CLASS_NAME = "TestStackMapTable";
        final String METHOD_NAME = "returnObject";

        ClassFileWriter cfw =
                new ClassFileWriter(CLASS_NAME, "java/lang/Object", "ClassFileWriterTest.java");

        // public static Object returnObject()
        cfw.startMethod(METHOD_NAME, "()Ljava/lang/Object;", (short) (ACC_PUBLIC | ACC_STATIC));

        cfw.add(ByteCode.NEW, "java/math/BigInteger");
        cfw.add(ByteCode.DUP);

        // new byte[]{ 123 }
        cfw.addPush(1);
        cfw.add(ByteCode.NEWARRAY, ByteCode.T_BYTE);
        cfw.add(ByteCode.DUP);
        cfw.addPush(0);
        cfw.add(ByteCode.BIPUSH, 123);
        cfw.add(ByteCode.BASTORE);

        // new java.math.BigInteger(bytes)
        cfw.addInvoke(ByteCode.INVOKESPECIAL, "java/math/BigInteger", "<init>", "([B)V");

        // generate StackMapTable
        cfw.add(ByteCode.DUP);
        int target = cfw.acquireLabel();
        cfw.add(ByteCode.IFNULL, target);
        cfw.markLabel(target);

        cfw.add(ByteCode.ARETURN);
        cfw.stopMethod((short) 0);

        byte[] bytecode = cfw.toByteArray();
        DefiningClassLoader loader = new DefiningClassLoader();
        Class<?> cl = loader.defineClass(CLASS_NAME, bytecode);

        Method method = cl.getMethod(METHOD_NAME);
        Object ret = method.invoke(cl);

        assertEquals(ret, new BigInteger(new byte[] {123}));
    }

    @Test
    public void lineNumberAboveShortMaxValue() throws Exception {
        final String CLASS_NAME = "TestHighLineNumber";
        final String METHOD_NAME = "boom";
        // Above Short.MAX_VALUE, but within the unsigned range a line_number holds
        final int LINE_NUMBER = 40000;

        ClassFileWriter cfw =
                new ClassFileWriter(CLASS_NAME, "java/lang/Object", "ClassFileWriterTest.java");

        // public static void boom() { throw new RuntimeException(); }
        cfw.startMethod(METHOD_NAME, "()V", (short) (ACC_PUBLIC | ACC_STATIC));
        cfw.addLineNumberEntry(LINE_NUMBER);
        cfw.add(ByteCode.NEW, "java/lang/RuntimeException");
        cfw.add(ByteCode.DUP);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V");
        cfw.add(ByteCode.ATHROW);
        cfw.stopMethod((short) 0);

        byte[] bytecode = cfw.toByteArray();
        DefiningClassLoader loader = new DefiningClassLoader();
        Class<?> cl = loader.defineClass(CLASS_NAME, bytecode);

        Method method = cl.getMethod(METHOD_NAME);
        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, () -> method.invoke(cl));

        // A sign-extended line number used to borrow into the start_pc half of the packed entry,
        // leaving the frame reporting a line it was never given
        StackTraceElement frame = thrown.getCause().getStackTrace()[0];
        assertEquals(LINE_NUMBER, frame.getLineNumber());
    }
}
