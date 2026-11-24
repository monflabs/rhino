/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.debugger.ui.test;

import java.util.Arrays;
import org.monflabs.mozilla.api.debugger.Debugger;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.LambdaFunction;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;

/** A Rhino engine with a debugger, a {@code console} and a {@code print}, for the tests. */
public final class TestEngine {
    public static final String FILENAME = "filename";

    private final ContextFactory factory = new ContextFactory();
    private final Debugger debugger = Debugger.of(factory);
    private final TopLevel scope;
    private volatile String fileName = "script.js";

    public TestEngine() {
        scope =
                factory.call(
                        cx -> {
                            final TopLevel top = cx.initStandardObjects();
                            final Scriptable console = cx.newObject(top);
                            for (final String type :
                                    new String[] {"log", "error", "warn", "info", "debug"}) {
                                ScriptableObject.putProperty(console, type, logger(top, type));
                            }
                            ScriptableObject.putProperty(top, "console", console);
                            ScriptableObject.putProperty(top, "print", logger(top, "log"));
                            return top;
                        });
        debugger.registerContext(scope, "global");
    }

    private LambdaFunction logger(final TopLevel top, final String type) {
        return new LambdaFunction(
                top,
                type,
                0,
                (c, s, thisObj, args) -> {
                    debugger.consoleCalled(type, Arrays.asList(args));
                    return Undefined.instance;
                });
    }

    public Debugger debugger() {
        return debugger;
    }

    public void put(final String key, final Object value) {
        if (FILENAME.equals(key)) {
            final String name = String.valueOf(value);
            fileName = name.contains("://") ? name : "file:///work/" + name;
        }
    }

    public Object eval(final String source) {
        final String name = fileName;
        return factory.call(cx -> cx.evaluateString(scope, source, name, 1, null));
    }
}
