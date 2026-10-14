/* -*- Mode: java; tab-width: 4; indent-tabs-mode: 1; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import static org.monflabs.mozilla.javascript.ClassDescriptor.Builder.value;
import static org.monflabs.mozilla.javascript.ClassDescriptor.Destination.CTOR;

import java.io.Serial;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * This class implements the Math native object. See ECMA 15.8.
 *
 * @author Norris Boyd
 */
final class NativeMath extends ScriptableObject {
    @Serial private static final long serialVersionUID = -8838847185801131569L;

    private static final String MATH_TAG = "Math";
    private static final double LOG2E = 1.4426950408889634;
    private static final Double Double32 = Double.valueOf(32d);

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(MATH_TAG)
                        .withProp(CTOR, "toSource", value("Math"))
                        .withMethod(CTOR, "abs", 1, NativeMath::abs)
                        .withMethod(CTOR, "acos", 1, NativeMath::acos)
                        .withMethod(CTOR, "acosh", 1, NativeMath::acosh)
                        .withMethod(CTOR, "asin", 1, NativeMath::asin)
                        .withMethod(CTOR, "asinh", 1, NativeMath::asinh)
                        .withMethod(CTOR, "atan", 1, NativeMath::atan)
                        .withMethod(CTOR, "atanh", 1, NativeMath::atanh)
                        .withMethod(CTOR, "atan2", 2, NativeMath::atan2)
                        .withMethod(CTOR, "cbrt", 1, NativeMath::cbrt)
                        .withMethod(CTOR, "ceil", 1, NativeMath::ceil)
                        .withMethod(CTOR, "clz32", 1, NativeMath::clz32)
                        .withMethod(CTOR, "cos", 1, NativeMath::cos)
                        .withMethod(CTOR, "cosh", 1, NativeMath::cosh)
                        .withMethod(CTOR, "exp", 1, NativeMath::exp)
                        .withMethod(CTOR, "expm1", 1, NativeMath::expm1)
                        .withMethod(CTOR, "f16round", 1, NativeMath::f16round)
                        .withMethod(CTOR, "floor", 1, NativeMath::floor)
                        .withMethod(CTOR, "fround", 1, NativeMath::fround)
                        .withMethod(CTOR, "hypot", 2, NativeMath::hypot)
                        .withMethod(CTOR, "imul", 2, NativeMath::imul)
                        .withMethod(CTOR, "log", 1, NativeMath::log)
                        .withMethod(CTOR, "log1p", 1, NativeMath::log1p)
                        .withMethod(CTOR, "log10", 1, NativeMath::log10)
                        .withMethod(CTOR, "log2", 1, NativeMath::log2)
                        .withMethod(CTOR, "max", 2, NativeMath::max)
                        .withMethod(CTOR, "min", 2, NativeMath::min)
                        .withMethod(CTOR, "pow", 2, NativeMath::pow)
                        .withMethod(CTOR, "random", 0, NativeMath::random)
                        .withMethod(CTOR, "round", 1, NativeMath::round)
                        .withMethod(CTOR, "sign", 1, NativeMath::sign)
                        .withMethod(CTOR, "sin", 1, NativeMath::sin)
                        .withMethod(CTOR, "sinh", 1, NativeMath::sinh)
                        .withMethod(CTOR, "sqrt", 1, NativeMath::sqrt)
                        .withMethod(CTOR, "sumPrecise", 1, NativeMath::sumPrecise)
                        .withMethod(CTOR, "tan", 1, NativeMath::tan)
                        .withMethod(CTOR, "tanh", 1, NativeMath::tanh)
                        .withMethod(CTOR, "trunc", 1, NativeMath::trunc)
                        .withProp(CTOR, "E", value(Math.E))
                        .withProp(CTOR, "PI", value(Math.PI))
                        .withProp(CTOR, "LN10", value(2.302585092994046))
                        .withProp(CTOR, "LN2", value(0.6931471805599453))
                        .withProp(CTOR, "LOG2E", value(LOG2E))
                        .withProp(CTOR, "LOG10E", value(0.4342944819032518))
                        .withProp(CTOR, "SQRT1_2", value(0.7071067811865476))
                        .withProp(CTOR, "SQRT2", value(1.4142135623730951))
                        .withProp(CTOR, SymbolKey.TO_STRING_TAG, value("Math", DONTENUM | READONLY))
                        .build();
    }

    static Object init(Context cx, VarScope scope, boolean sealed) {
        ScriptableObject math = DESCRIPTOR.populateGlobal(cx, scope, new NativeMath(), false);
        if (!cx.isLatestEcmaScript()) {
            math.delete("sumPrecise");
        }
        if (sealed) {
            math.sealObject();
        }
        return math;
    }

    private NativeMath() {}

    @Override
    public String getClassName() {
        return "Math";
    }

    /**
     * Math.sumPrecise ( items ) (21.3.2.34): the sum of the Numbers of an iterable, computed
     * exactly and rounded once. NaN, or both infinities, give NaN; an infinity gives itself; no
     * value or only -0 gives -0.
     */
    private static Object sumPrecise(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Object items = args.length > 0 ? args[0] : Undefined.instance;
        if (items == null || Undefined.isUndefined(items)) {
            throw ScriptRuntime.typeErrorById("msg.not.iterable", ScriptRuntime.toString(items));
        }
        Object iterator = ScriptRuntime.callIterator(items, cx, s);
        if (!(iterator instanceof Scriptable)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.not.object", "[Symbol.iterator]");
        }
        NativeAsyncFromSyncIterator.IteratorRecord record =
                NativeIteratorPrototype.getIteratorDirect((Scriptable) iterator);
        boolean minusZero = true;
        boolean nan = false;
        boolean plusInfinity = false;
        boolean minusInfinity = false;
        PreciseSum sum = new PreciseSum();
        for (Scriptable r; (r = NativeIteratorPrototype.step(cx, s, record)) != null; ) {
            Object value = NativeIteratorPrototype.value(r);
            if (!(value instanceof Number) || value instanceof BigInteger) {
                NativeIteratorPrototype.closeOnThrow(cx, s, record.iterator);
                throw ScriptRuntime.typeErrorById("msg.not.a.number");
            }
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d)) {
                nan = true;
            } else if (d == Double.POSITIVE_INFINITY) {
                plusInfinity = true;
            } else if (d == Double.NEGATIVE_INFINITY) {
                minusInfinity = true;
            } else if (!nan && !plusInfinity && !minusInfinity) {
                if (d != 0) {
                    sum.add(d);
                }
                if (d != 0 || 1 / d > 0) {
                    minusZero = false;
                }
            }
        }
        if (nan || (plusInfinity && minusInfinity)) {
            return ScriptRuntime.NaNobj;
        }
        if (plusInfinity || minusInfinity) {
            return ScriptRuntime.wrapNumber(
                    plusInfinity ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        }
        if (minusZero) {
            return ScriptRuntime.wrapNumber(-0.0);
        }
        return ScriptRuntime.wrapNumber(sum.result());
    }

    /**
     * The exact sum of finite doubles, rounded once to the nearest double (ties to even), as
     * Shewchuk's algorithm keeps it: a list of non-overlapping partials whose sum is exact, and a
     * count of 2^1024 for the partials that overflowed (the algorithm of the proposal's polyfill).
     * About 20 times faster than a BigDecimal sum on usual values.
     */
    static final class PreciseSum {
        private static final double TWO_1023 = 0x1p1023;
        private static final double MAX_ULP = Math.ulp(Double.MAX_VALUE);

        private double[] partials = new double[8];
        private int size;
        private int overflow;

        void add(double value) {
            double x = value;
            int used = 0;
            for (int i = 0; i < size; i++) {
                double y = partials[i];
                if (Math.abs(x) < Math.abs(y)) {
                    double t = x;
                    x = y;
                    y = t;
                }
                double hi = x + y;
                double lo = y - (hi - x);
                if (Double.isInfinite(hi)) {
                    double sign = hi > 0 ? 1 : -1;
                    overflow += (int) sign;
                    x = (x - sign * TWO_1023) - sign * TWO_1023;
                    if (Math.abs(x) < Math.abs(y)) {
                        double t = x;
                        x = y;
                        y = t;
                    }
                    hi = x + y;
                    lo = y - (hi - x);
                }
                if (lo != 0) {
                    partials[used++] = lo;
                }
                x = hi;
            }
            size = used;
            if (x != 0) {
                if (size == partials.length) {
                    partials = Arrays.copyOf(partials, size * 2);
                }
                partials[size++] = x;
            }
        }

        double result() {
            int n = size - 1;
            double hi = 0;
            double lo = 0;
            if (overflow != 0) {
                double next = n >= 0 ? partials[n] : 0;
                n--;
                if (Math.abs(overflow) > 1
                        || (overflow > 0 && next > 0)
                        || (overflow < 0 && next < 0)) {
                    return overflow > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }
                // halved, so that the arithmetic does not overflow
                double x = overflow * TWO_1023;
                double y = next / 2;
                hi = x + y;
                lo = (y - (hi - x)) * 2;
                if (Double.isInfinite(2 * hi)) {
                    // rounds to infinity, unless the rest of the sum makes it round down
                    if (hi > 0) {
                        return hi == TWO_1023 && lo == -(MAX_ULP / 2) && n >= 0 && partials[n] < 0
                                ? Double.MAX_VALUE
                                : Double.POSITIVE_INFINITY;
                    }
                    return hi == -TWO_1023 && lo == MAX_ULP / 2 && n >= 0 && partials[n] > 0
                            ? -Double.MAX_VALUE
                            : Double.NEGATIVE_INFINITY;
                }
                if (lo != 0) {
                    partials[++n] = lo;
                    lo = 0;
                }
                hi *= 2;
            }
            while (n >= 0) {
                double x = hi;
                double y = partials[n--];
                hi = x + y;
                lo = y - (hi - x);
                if (lo != 0) {
                    break;
                }
            }
            // a round-off of exactly half an ulp: the next partial decides the direction
            if (n >= 0 && ((lo < 0 && partials[n] < 0) || (lo > 0 && partials[n] > 0))) {
                double y = lo * 2;
                double x = hi + y;
                if (y == x - hi) {
                    hi = x;
                }
            }
            return hi;
        }
    }

    private static Object abs(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        // abs(-0.0) should be 0.0, but -0.0 < 0.0 == false
        x = (x == 0.0) ? 0.0 : (x < 0.0) ? -x : x;

        return ScriptRuntime.wrapNumber(x);
    }

    private static Object acos(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (!Double.isNaN(x) && -1.0 <= x && x <= 1.0) {
            x = Math.acos(x);
        } else {
            x = Double.NaN;
        }
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object acosh(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (!Double.isNaN(x)) {
            return Double.valueOf(Math.log(x + Math.sqrt(x * x - 1.0)));
        }
        return ScriptRuntime.NaNobj;
    }

    private static Object asin(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (!Double.isNaN(x) && -1.0 <= x && x <= 1.0) {
            x = Math.asin(x);
        } else {
            x = Double.NaN;
        }
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object asinh(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (Double.isInfinite(x)) {
            return Double.valueOf(x);
        }
        if (!Double.isNaN(x)) {
            if (x == 0) {
                if (1 / x > 0) {
                    return ScriptRuntime.zeroObj;
                }
                return ScriptRuntime.negativeZeroObj;
            }
            return Double.valueOf(Math.log(x + Math.sqrt(x * x + 1.0)));
        }
        return ScriptRuntime.NaNobj;
    }

    private static Object atan(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.atan(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object atanh(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (!Double.isNaN(x) && -1.0 <= x && x <= 1.0) {
            if (x == 0) {
                if (1 / x > 0) {
                    return ScriptRuntime.zeroObj;
                }
                return ScriptRuntime.negativeZeroObj;
            }
            return Double.valueOf(0.5 * Math.log((1.0 + x) / (1.0 - x)));
        }
        return ScriptRuntime.NaNobj;
    }

    private static Object atan2(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.atan2(x, ScriptRuntime.toNumber(args, 1));
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object cbrt(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.cbrt(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object ceil(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.ceil(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object clz32(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (x == 0 || Double.isNaN(x) || Double.isInfinite(x)) {
            return Double32;
        }
        long n = ScriptRuntime.toUint32(x);
        if (n == 0) {
            return Double32;
        }

        int place = 0;
        if ((n & 0xFFFF0000) != 0) {
            place += 16;
            n >>>= 16;
        }
        if ((n & 0xFF00) != 0) {
            place += 8;
            n >>>= 8;
        }
        if ((n & 0xF0) != 0) {
            place += 4;
            n >>>= 4;
        }
        if ((n & 0b1100) != 0) {
            place += 2;
            n >>>= 2;
        }
        if ((n & 0b10) != 0) {
            place += 1;
            n >>>= 1;
        }
        if ((n & 0b1) != 0) {
            place += 1;
        }

        return Double.valueOf(32 - place);
    }

    private static Object cos(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Double.isInfinite(x) ? Double.NaN : Math.cos(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object cosh(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.cosh(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object exp(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x =
                (x == Double.POSITIVE_INFINITY)
                        ? x
                        : (x == Double.NEGATIVE_INFINITY) ? 0.0 : Math.exp(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object expm1(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.expm1(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object floor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.floor(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object f16round(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        // Handle missing arguments
        if (args.length == 0) {
            return ScriptRuntime.NaNobj;
        }
        double x = ScriptRuntime.toNumber(args[0]);

        // Handle special cases
        if (Double.isNaN(x)) return ScriptRuntime.NaNobj;
        if (x == 0.0) return ScriptRuntime.wrapNumber(x); // Preserve sign of zero
        if (Double.isInfinite(x)) return ScriptRuntime.wrapNumber(x);

        // Extract components from double precision
        long bits = Double.doubleToLongBits(x);
        int sign = (int) (bits >>> 63);
        int exponent = (int) ((bits >>> 52) & 0x7FF);
        long mantissa = bits & 0x000FFFFFFFFFFFFFL;

        // Adjust from double bias (1023) to float16 bias (15)
        exponent = exponent - 1023 + 15;

        // Handle overflow to infinity
        if (exponent >= 31) {
            return ScriptRuntime.wrapNumber(
                    (sign != 0) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }

        // Handle underflow and subnormal values
        if (exponent < 0) {
            return handleSubnormalF16(sign, exponent, mantissa);
        }

        // Normal value: round mantissa from 52 to 10 bits
        return handleNormalF16(sign, exponent, mantissa);
    }

    private static Object handleSubnormalF16(int sign, int exponent, long mantissa) {
        // Values below 2^-24 underflow to zero
        if (exponent < -10) {
            return ScriptRuntime.wrapNumber((sign != 0) ? -0.0 : 0.0);
        }

        // Special case: exactly 2^-25 rounds to zero (ties-to-even)
        if (exponent == -10 && mantissa == 0) {
            return ScriptRuntime.wrapNumber((sign != 0) ? -0.0 : 0.0);
        }

        // Special case: slightly above 2^-25 rounds to 2^-24
        if (exponent == -10 && mantissa > 0) {
            double smallestSubnormal = 0x1.0p-24; // 2^-24
            return ScriptRuntime.wrapNumber((sign != 0) ? -smallestSubnormal : smallestSubnormal);
        }

        // Convert to subnormal representation
        int totalShift = 42 + (1 - exponent);
        mantissa = mantissa | (1L << 52); // Add implicit 1 bit

        // Extract rounding information before shift
        long roundBit = (mantissa >> (totalShift - 1)) & 1;
        long stickyBits = mantissa & ((1L << (totalShift - 1)) - 1);

        // Shift to get 10-bit mantissa
        mantissa >>>= totalShift;

        // Apply ties-to-even rounding
        if (roundBit == 1 && (stickyBits != 0 || (mantissa & 1) == 1)) {
            mantissa++;
        }

        // Reconstruct subnormal value
        if (mantissa == 0) {
            return ScriptRuntime.wrapNumber((sign != 0) ? -0.0 : 0.0);
        }

        // Check for overflow to normal range
        if (mantissa >= (1L << 10)) {
            // Smallest normal = 2^-14
            return ScriptRuntime.wrapNumber((sign != 0) ? -6.103515625e-5 : 6.103515625e-5);
        }

        // Subnormal value = 2^-14 * (mantissa / 1024)
        double value = Math.scalb((double) mantissa / 1024.0, -14);
        return ScriptRuntime.wrapNumber((sign != 0) ? -value : value);
    }

    private static Object handleNormalF16(int sign, int exponent, long mantissa) {
        // Add implicit 1 bit for normal values
        long fullMantissa = mantissa | (1L << 52);

        // Extract rounding information
        long roundBit = (fullMantissa >> 41) & 1;
        long stickyBits = fullMantissa & ((1L << 41) - 1);
        fullMantissa >>>= 42;

        // Handle boundary between largest subnormal and smallest normal
        if (exponent == 0) {
            if (fullMantissa == 2046) {
                // Exactly the largest subnormal
                return reconstructSubnormalF16(sign, 1023);
            } else if (fullMantissa == 2047 && roundBit == 0 && stickyBits == 0) {
                // Midpoint: ties-to-even rounds to smallest normal
                return reconstructNormalF16(sign, 1, 0);
            }
        }

        // Extract 10-bit mantissa (remove implicit 1)
        mantissa = fullMantissa & 0x3FF;

        // Apply ties-to-even rounding
        if (roundBit == 1 && (stickyBits != 0 || (mantissa & 1) == 1)) {
            mantissa++;
        }

        // Handle mantissa overflow
        if (mantissa >= (1L << 10)) {
            mantissa = 0;
            exponent++;
            if (exponent >= 31) {
                return ScriptRuntime.wrapNumber(
                        (sign != 0) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            }
        }

        // Reconstruct the value
        if (exponent == 0) {
            return reconstructSubnormalF16(sign, mantissa);
        } else {
            return reconstructNormalF16(sign, exponent, mantissa);
        }
    }

    private static Object reconstructSubnormalF16(int sign, long mantissa) {
        if (mantissa == 0) {
            return ScriptRuntime.wrapNumber((sign != 0) ? -0.0 : 0.0);
        }
        double value = Math.scalb((double) mantissa / 1024.0, -14);
        return ScriptRuntime.wrapNumber((sign != 0) ? -value : value);
    }

    private static Object reconstructNormalF16(int sign, int exponent, long mantissa) {
        long resultBits =
                ((long) sign << 63) | (((long) (exponent + 1023 - 15)) << 52) | (mantissa << 42);
        return ScriptRuntime.wrapNumber(Double.longBitsToDouble(resultBits));
    }

    private static Object fround(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        // Rely on Java to truncate down to a "float" here"
        float fx = (float) x;
        return ScriptRuntime.wrapNumber(fx);
    }

    // Based on code from
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/hypot
    private static Object hypot(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (args == null) {
            return 0.0;
        }
        double y = 0.0;

        // Spec and tests say that any "Infinity" result takes precedence.
        boolean hasNaN = false;
        boolean hasInfinity = false;

        for (Object o : args) {
            double d = ScriptRuntime.toNumber(o);
            if (Double.isNaN(d)) {
                hasNaN = true;
            } else if (Double.isInfinite(d)) {
                hasInfinity = true;
            } else {
                y += d * d;
            }
        }

        if (hasInfinity) {
            return Double.POSITIVE_INFINITY;
        }
        if (hasNaN) {
            return Double.NaN;
        }
        return Math.sqrt(y);
    }

    private static Object imul(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (args == null) {
            return 0;
        }

        int x = ScriptRuntime.toInt32(args, 0);
        int y = ScriptRuntime.toInt32(args, 1);

        return ScriptRuntime.wrapNumber(x * y);
    }

    private static Object log(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        // Java's log(<0) = -Infinity; we need NaN
        x = (x < 0) ? Double.NaN : Math.log(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object log1p(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.log1p(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object log10(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.log10(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object log2(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (cx.isLatestEcmaScript() && x > 0 && x < Double.POSITIVE_INFINITY) {
            // exact for a power of two, which log(x) * LOG2E is not (log2(8) = 2.9999999999999996)
            int exponent = Math.getExponent(x);
            if (exponent >= Double.MIN_EXPONENT && x == Math.scalb(1.0, exponent)) {
                return ScriptRuntime.wrapNumber(exponent);
            }
        }
        // Java's log(<0) = -Infinity; we need NaN
        x = (x < 0) ? Double.NaN : Math.log(x) * LOG2E;
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object max(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = Double.NEGATIVE_INFINITY;
        for (int i = 0; i != args.length; ++i) {
            double d = ScriptRuntime.toNumber(args[i]);
            // if (x < d) x = d; does not work due to -0.0 >= +0.0
            x = Math.max(x, d);
        }
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object min(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = Double.POSITIVE_INFINITY;
        for (int i = 0; i != args.length; ++i) {
            double d = ScriptRuntime.toNumber(args[i]);
            // if (x < d) x = d; does not work due to -0.0 >= +0.0
            x = Math.min(x, d);
        }
        return ScriptRuntime.wrapNumber(x);
    }

    // See Ecma 15.8.2.13
    private static Object pow(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        double y = ScriptRuntime.toNumber(args, 1);
        double result;
        if (Double.isNaN(y)) {
            // y is NaN, result is always NaN
            result = y;
        } else if (y == 0) {
            // Java's pow(NaN, 0) = NaN; we need 1
            result = 1.0;
        } else if (x == 0) {
            // Many differences from Java's Math.pow
            if (1 / x > 0) {
                result = (y > 0) ? 0 : Double.POSITIVE_INFINITY;
            } else {
                // x is -0, need to check if y is an odd integer
                long y_long = (long) y;
                if (y_long == y && (y_long & 0x1) != 0) {
                    result = (y > 0) ? -0.0 : Double.NEGATIVE_INFINITY;
                } else {
                    result = (y > 0) ? 0.0 : Double.POSITIVE_INFINITY;
                }
            }
        } else {
            result = Math.pow(x, y);
            if (Double.isNaN(result)) {
                // Check for broken Java implementations that gives NaN
                // when they should return something else
                if (y == Double.POSITIVE_INFINITY) {
                    if (x < -1.0 || 1.0 < x) {
                        result = Double.POSITIVE_INFINITY;
                    } else if (-1.0 < x && x < 1.0) {
                        result = 0;
                    }
                } else if (y == Double.NEGATIVE_INFINITY) {
                    if (x < -1.0 || 1.0 < x) {
                        result = 0;
                    } else if (-1.0 < x && x < 1.0) {
                        result = Double.POSITIVE_INFINITY;
                    }
                } else if (x == Double.POSITIVE_INFINITY) {
                    result = (y > 0) ? Double.POSITIVE_INFINITY : 0.0;
                } else if (x == Double.NEGATIVE_INFINITY) {
                    long y_long = (long) y;
                    if (y_long == y && (y_long & 0x1) != 0) {
                        // y is odd integer
                        result = (y > 0) ? Double.NEGATIVE_INFINITY : -0.0;
                    } else {
                        result = (y > 0) ? Double.POSITIVE_INFINITY : 0.0;
                    }
                }
            }
        }
        return ScriptRuntime.wrapNumber(result);
    }

    private static Object random(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return ScriptRuntime.wrapNumber(Math.random());
    }

    private static Object round(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (!Double.isNaN(x) && !Double.isInfinite(x)) {
            // Round only finite x
            long l = Math.round(x);
            if (l != 0) {
                x = l;
            } else {
                // We must propagate the sign of d into the result
                if (x < 0.0) {
                    x = ScriptRuntime.negativeZero;
                } else if (x != 0.0) {
                    x = 0.0;
                }
            }
        }
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object sign(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        if (!Double.isNaN(x)) {
            if (x == 0) {
                if (1 / x > 0) {
                    return ScriptRuntime.zeroObj;
                }
                return ScriptRuntime.negativeZeroObj;
            }
            return Double.valueOf(Math.signum(x));
        }
        return ScriptRuntime.NaNobj;
    }

    private static Object sin(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Double.isInfinite(x) ? Double.NaN : Math.sin(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object sinh(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.sinh(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object sqrt(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.sqrt(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object tan(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.tan(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object tanh(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = Math.tanh(x);
        return ScriptRuntime.wrapNumber(x);
    }

    private static Object trunc(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        double x = ScriptRuntime.toNumber(args, 0);
        x = ((x < 0.0) ? Math.ceil(x) : Math.floor(x));
        return ScriptRuntime.wrapNumber(x);
    }
}
