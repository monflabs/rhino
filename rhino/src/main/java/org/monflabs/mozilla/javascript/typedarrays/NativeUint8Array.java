/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.typedarrays;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.PROTO;

import java.io.ByteArrayOutputStream;
import java.io.Serial;
import java.util.Base64;
import java.util.HexFormat;
import org.monflabs.mozilla.javascript.ClassDescriptor;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.JSFunction;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.ScriptRuntimeES6;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.SymbolKey;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;

/**
 * An array view that stores 8-bit quantities and implements the JavaScript "Uint8Array" interface.
 * It also implements List&lt;Integer&gt; for direct manipulation in Java.
 */
public class NativeUint8Array extends NativeTypedArrayView<Integer> {
    @Serial private static final long serialVersionUID = 4309679036296927829L;

    private static final String CLASS_NAME = "Uint8Array";

    private static final ClassDescriptor DESCRIPTOR = makeDescriptor(true);
    private static final ClassDescriptor LEGACY_DESCRIPTOR = makeDescriptor(false);

    /** The base64 and hex methods (ES2026 23.3) are a feature of VERSION_ECMASCRIPT. */
    private static ClassDescriptor makeDescriptor(boolean latest) {
        var builder =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeUint8Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(1))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(1))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies);
        if (latest) {
            builder.withMethod(CTOR, "fromBase64", 1, NativeUint8Array::js_fromBase64)
                    .withMethod(CTOR, "fromHex", 1, NativeUint8Array::js_fromHex)
                    .withMethod(PROTO, "toBase64", 0, NativeUint8Array::js_toBase64)
                    .withMethod(PROTO, "toHex", 0, NativeUint8Array::js_toHex)
                    .withMethod(PROTO, "setFromBase64", 1, NativeUint8Array::js_setFromBase64)
                    .withMethod(PROTO, "setFromHex", 1, NativeUint8Array::js_setFromHex);
        }
        return builder.build();
    }

    public NativeUint8Array() {}

    public NativeUint8Array(NativeArrayBuffer ab, int off, int len) {
        super(ab, off, len, len);
    }

    public NativeUint8Array(int len) {
        this(new NativeArrayBuffer(len), 0, len);
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    public static JSFunction init(Context cx, VarScope scope, boolean sealed) {
        return NativeTypedArrayView.initSubClass(
                cx, scope, cx.isLatestEcmaScript() ? DESCRIPTOR : LEGACY_DESCRIPTOR, sealed);
    }

    @Override
    public int getBytesPerElement() {
        return 1;
    }

    private static Object js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return NativeTypedArrayView.js_constructor(
                cx,
                f,
                nt,
                s,
                thisObj,
                args,
                NativeUint8Array::new,
                1,
                TopLevel.Builtins.Uint8Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        int byteBits = arrayBuffer.buffer.get(index + offset);
        return Conversions.byteBitsToUint(byteBits);
    }

    @Override
    protected Object js_set(int index, Object c) {
        int val = Conversions.toUint8(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        arrayBuffer.buffer.put(index + offset, (byte) val);
        return null;
    }

    @Override
    public Integer get(int i) {
        ensureIndex(i);
        return (Integer) js_get(i);
    }

    @Override
    public Integer set(int i, Integer aByte) {
        ensureIndex(i);
        return (Integer) js_set(i, aByte);
    }

    /**
     * The result of FromBase64 or FromHex (23.3.3.1 and 23.3.3.2): the characters read, the bytes
     * decoded, and the id of the SyntaxError to throw once they are written, or null.
     */
    private record Decoded(int read, byte[] bytes, String error) {}

    /** ValidateUint8Array ( ta ) (23.3.3.4). */
    private static NativeUint8Array validate(Object thisObj) {
        if (!(thisObj instanceof NativeUint8Array)) {
            throw ScriptRuntime.typeErrorById("msg.this.not.instance", CLASS_NAME);
        }
        return (NativeUint8Array) thisObj;
    }

    private static String stringArg(Object[] args) {
        Object string = args.length > 0 ? args[0] : Undefined.instance;
        if (!(string instanceof CharSequence)) {
            throw ScriptRuntime.typeErrorById("msg.not.a.string");
        }
        return string.toString();
    }

    /** GetOptionsObject ( options ) (14.5.2.1 of ECMA-402, 23.3.3). */
    private static Scriptable options(Object[] args, int index) {
        Object options = args.length > index ? args[index] : Undefined.instance;
        if (Undefined.isUndefined(options)) {
            return null;
        }
        if (!ScriptRuntime.isObject(options)) {
            throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(options));
        }
        return (Scriptable) options;
    }

    /** The alphabet option: base64 (the default) or base64url. */
    private static boolean urlAlphabet(Scriptable options) {
        return "base64url".equals(option(options, "alphabet", "base64", "base64", "base64url"));
    }

    private static String option(
            Scriptable options, String name, String byDefault, String... values) {
        if (options == null) {
            return byDefault;
        }
        Object value = ScriptableObject.getProperty(options, name);
        if (value == Scriptable.NOT_FOUND || Undefined.isUndefined(value)) {
            return byDefault;
        }
        if (value instanceof CharSequence) {
            for (String v : values) {
                if (v.equals(value.toString())) {
                    return v;
                }
            }
        }
        throw ScriptRuntime.typeErrorById("msg.base64.bad.option", name);
    }

    private static String lastChunkHandling(Scriptable options) {
        return option(
                options, "lastChunkHandling", "loose", "loose", "strict", "stop-before-partial");
    }

    /** GetUint8ArrayBytes ( ta ) (23.3.3.5). */
    private byte[] currentBytes() {
        if (isTypedArrayOutOfBounds()) {
            throw ScriptRuntime.typeErrorById("msg.typed.array.out.of.bounds");
        }
        int len = getLength();
        byte[] bytes = new byte[len];
        arrayBuffer.buffer.get(offset, bytes, 0, len);
        return bytes;
    }

    private static NativeUint8Array fromBytes(VarScope scope, byte[] bytes) {
        NativeUint8Array ta = new NativeUint8Array(bytes.length);
        ta.arrayBuffer.buffer.put(0, bytes, 0, bytes.length);
        ScriptRuntime.setBuiltinProtoAndParent(
                ta.arrayBuffer, scope, TopLevel.Builtins.ArrayBuffer);
        ScriptRuntime.setBuiltinProtoAndParent(ta, scope, TopLevel.Builtins.Uint8Array);
        return ta;
    }

    /** SetUint8ArrayBytes ( into, bytes ) (23.3.3.6), then the deferred error, if any. */
    private Scriptable write(Context cx, VarScope scope, Decoded decoded) {
        arrayBuffer.buffer.put(offset, decoded.bytes, 0, decoded.bytes.length);
        if (decoded.error != null) {
            throw syntaxError(decoded.error);
        }
        Scriptable result = cx.newObject(scope);
        ScriptableObject.putProperty(result, "read", decoded.read);
        ScriptableObject.putProperty(result, "written", decoded.bytes.length);
        return result;
    }

    private static RuntimeException syntaxError(String id) {
        return ScriptRuntime.constructError("SyntaxError", ScriptRuntime.getMessageById(id));
    }

    /** Uint8Array.fromBase64 ( string [ , options ] ) (23.3.2.1). */
    private static Object js_fromBase64(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        String string = stringArg(args);
        Scriptable options = options(args, 1);
        boolean url = urlAlphabet(options);
        Decoded decoded = fromBase64(string, url, lastChunkHandling(options), Integer.MAX_VALUE);
        if (decoded.error != null) {
            throw syntaxError(decoded.error);
        }
        return fromBytes(s, decoded.bytes);
    }

    /** Uint8Array.fromHex ( string ) (23.3.2.2). */
    private static Object js_fromHex(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Decoded decoded = fromHex(stringArg(args), Integer.MAX_VALUE);
        if (decoded.error != null) {
            throw syntaxError(decoded.error);
        }
        return fromBytes(s, decoded.bytes);
    }

    /** Uint8Array.prototype.toBase64 ( [ options ] ) (23.3.2.4). */
    private static Object js_toBase64(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeUint8Array ta = validate(thisObj);
        Scriptable options = options(args, 0);
        boolean url = urlAlphabet(options);
        Object omit =
                options == null
                        ? Undefined.instance
                        : ScriptableObject.getProperty(options, "omitPadding");
        boolean omitPadding = omit != Scriptable.NOT_FOUND && ScriptRuntime.toBoolean(omit);
        Base64.Encoder encoder = url ? Base64.getUrlEncoder() : Base64.getEncoder();
        if (omitPadding) {
            encoder = encoder.withoutPadding();
        }
        return encoder.encodeToString(ta.currentBytes());
    }

    /** Uint8Array.prototype.toHex ( ) (23.3.2.5). */
    private static Object js_toHex(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return HexFormat.of().formatHex(validate(thisObj).currentBytes());
    }

    /** Uint8Array.prototype.setFromBase64 ( string [ , options ] ) (23.3.2.6). */
    private static Object js_setFromBase64(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeUint8Array ta = validate(thisObj);
        String string = stringArg(args);
        Scriptable options = options(args, 1);
        boolean url = urlAlphabet(options);
        String lastChunkHandling = lastChunkHandling(options);
        if (ta.isTypedArrayOutOfBounds()) {
            throw ScriptRuntime.typeErrorById("msg.typed.array.out.of.bounds");
        }
        return ta.write(cx, s, fromBase64(string, url, lastChunkHandling, ta.getLength()));
    }

    /** Uint8Array.prototype.setFromHex ( string ) (23.3.2.7). */
    private static Object js_setFromHex(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeUint8Array ta = validate(thisObj);
        String string = stringArg(args);
        if (ta.isTypedArrayOutOfBounds()) {
            throw ScriptRuntime.typeErrorById("msg.typed.array.out.of.bounds");
        }
        return ta.write(cx, s, fromHex(string, ta.getLength()));
    }

    private static boolean isAsciiWhitespace(char c) {
        return c == 0x09 || c == 0x0A || c == 0x0C || c == 0x0D || c == 0x20;
    }

    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && isAsciiWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int base64Value(char c, boolean url) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == (url ? '-' : '+')) {
            return 62;
        }
        if (c == (url ? '_' : '/')) {
            return 63;
        }
        return -1;
    }

    /**
     * DecodeBase64Chunk ( chunk [ , throwOnExtraBits ] ) (23.3.3.3): 2, 3 or 4 sextets to 1, 2 or 3
     * bytes; null when the padding bits of a partial chunk are not zero and that is an error.
     */
    private static byte[] decodeChunk(int[] chunk, int length, boolean throwOnExtraBits) {
        switch (length) {
            case 2:
                if (throwOnExtraBits && (chunk[1] & 0x0f) != 0) {
                    return null;
                }
                return new byte[] {(byte) ((chunk[0] << 2) | (chunk[1] >> 4))};
            case 3:
                if (throwOnExtraBits && (chunk[2] & 0x03) != 0) {
                    return null;
                }
                return new byte[] {
                    (byte) ((chunk[0] << 2) | (chunk[1] >> 4)),
                    (byte) (((chunk[1] & 0x0f) << 4) | (chunk[2] >> 2))
                };
            default:
                return new byte[] {
                    (byte) ((chunk[0] << 2) | (chunk[1] >> 4)),
                    (byte) (((chunk[1] & 0x0f) << 4) | (chunk[2] >> 2)),
                    (byte) (((chunk[2] & 0x03) << 6) | chunk[3])
                };
        }
    }

    /** FromBase64 ( string, alphabet, lastChunkHandling [ , maxLength ] ) (23.3.3.1). */
    private static Decoded fromBase64(
            String string, boolean url, String lastChunkHandling, int maxLength) {
        if (maxLength == 0) {
            return new Decoded(0, new byte[0], null);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int[] chunk = new int[4];
        int chunkLength = 0;
        int read = 0;
        int index = 0;
        int length = string.length();
        for (; ; ) {
            index = skipWhitespace(string, index);
            if (index == length) {
                if (chunkLength > 0) {
                    if (lastChunkHandling.equals("stop-before-partial")) {
                        return new Decoded(read, bytes.toByteArray(), null);
                    }
                    if (!lastChunkHandling.equals("loose") || chunkLength == 1) {
                        return new Decoded(read, bytes.toByteArray(), "msg.base64.bad.padding");
                    }
                    byte[] decoded = decodeChunk(chunk, chunkLength, false);
                    bytes.write(decoded, 0, decoded.length);
                }
                return new Decoded(length, bytes.toByteArray(), null);
            }
            char c = string.charAt(index++);
            if (c == '=') {
                if (chunkLength < 2) {
                    return new Decoded(read, bytes.toByteArray(), "msg.base64.bad.padding");
                }
                index = skipWhitespace(string, index);
                if (chunkLength == 2) {
                    if (index == length) {
                        if (lastChunkHandling.equals("stop-before-partial")) {
                            return new Decoded(read, bytes.toByteArray(), null);
                        }
                        return new Decoded(read, bytes.toByteArray(), "msg.base64.bad.padding");
                    }
                    if (string.charAt(index) != '=') {
                        return new Decoded(read, bytes.toByteArray(), "msg.base64.bad.padding");
                    }
                    index = skipWhitespace(string, index + 1);
                }
                if (index < length) {
                    return new Decoded(read, bytes.toByteArray(), "msg.base64.bad.padding");
                }
                byte[] decoded =
                        decodeChunk(chunk, chunkLength, lastChunkHandling.equals("strict"));
                if (decoded == null) {
                    return new Decoded(read, bytes.toByteArray(), "msg.base64.bad.padding");
                }
                bytes.write(decoded, 0, decoded.length);
                return new Decoded(length, bytes.toByteArray(), null);
            }
            int value = base64Value(c, url);
            if (value < 0) {
                return new Decoded(read, bytes.toByteArray(), "msg.base64.bad.char");
            }
            int remaining = maxLength - bytes.size();
            if ((remaining == 1 && chunkLength == 2) || (remaining == 2 && chunkLength == 3)) {
                // the chunk would not fit
                return new Decoded(read, bytes.toByteArray(), null);
            }
            chunk[chunkLength++] = value;
            if (chunkLength == 4) {
                byte[] decoded = decodeChunk(chunk, 4, false);
                bytes.write(decoded, 0, decoded.length);
                chunkLength = 0;
                read = index;
                if (bytes.size() >= maxLength) {
                    return new Decoded(read, bytes.toByteArray(), null);
                }
            }
        }
    }

    /** FromHex ( string [ , maxLength ] ) (23.3.3.2). */
    private static Decoded fromHex(String string, int maxLength) {
        int length = string.length();
        if (length % 2 != 0) {
            return new Decoded(0, new byte[0], "msg.hex.invalid");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int read = 0;
        while (read < length && bytes.size() < maxLength) {
            int hi = Character.digit(string.charAt(read), 16);
            int lo = Character.digit(string.charAt(read + 1), 16);
            if (hi < 0 || lo < 0 || string.charAt(read) > 'f' || string.charAt(read + 1) > 'f') {
                return new Decoded(read, bytes.toByteArray(), "msg.hex.invalid");
            }
            bytes.write((hi << 4) | lo);
            read += 2;
        }
        return new Decoded(read, bytes.toByteArray(), null);
    }
}
