/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.lang.reflect.Member;

/**
 * Wraps the calls that scripts make to Java methods and constructors, for tracing, call quotas,
 * deprecation warnings or exception translation. Set it with {@link
 * ContextFactory#setInvocationInterceptor}; with none set, a call costs one null check.
 *
 * <p>It sees the calls made through {@code NativeJavaMethod} and {@code new} on a Java class, not
 * the calls made by built-ins ({@code FunctionObject}) or by Java adapters.
 */
@FunctionalInterface
public interface InvocationInterceptor {

    /** The call being intercepted. */
    @FunctionalInterface
    interface Invocation {
        /** Performs the call, with the target and the arguments given (possibly replaced). */
        Object proceed(Object target, Object[] args);
    }

    /**
     * @param member the {@link java.lang.reflect.Method} or {@link java.lang.reflect.Constructor}
     * @param target the receiver, null for a static method or a constructor
     * @param args the arguments, already converted to Java
     * @param next the call itself; the result is what the script gets (before wrapping)
     */
    Object invoke(Context cx, Member member, Object target, Object[] args, Invocation next);
}
