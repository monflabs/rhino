package org.monflabs.mozilla.javascript.typedarrays;

import java.io.Serial;
import java.math.BigInteger;
import org.monflabs.mozilla.javascript.ScriptRuntime;

public abstract class NativeBigIntArrayView extends NativeTypedArrayView<BigInteger> {
    @Serial private static final long serialVersionUID = -3349222145964894609L;

    protected NativeBigIntArrayView() {}

    protected NativeBigIntArrayView(NativeArrayBuffer ab, int off, int len, int byteLen) {
        super(ab, off, len, byteLen);
    }

    @Override
    protected Object toNumeric(Object num) {
        return ScriptRuntime.toBigIntStrict(num);
    }
}
