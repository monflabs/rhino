/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.engine;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import javax.script.ScriptContext;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ScriptRuntime;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.Undefined;
import org.monflabs.mozilla.javascript.VarScope;

/**
 * This class defines the following built-in functions for the RhinoScriptEngine.
 *
 * <ul>
 *   <li>print(arg, arg, ...): Write each argument, concatenated to the ScriptEngine's "standard
 *       output" as a string.
 * </ul>
 */
public class Builtins {

    static final Object BUILTIN_KEY = new Object();

    private Writer stdout;

    void register(Context cx, TopLevel scope, ScriptContext sc) {
        if (sc.getWriter() == null) {
            stdout = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        } else {
            stdout = sc.getWriter();
        }

        scope.getGlobalThis()
                .defineProperty(
                        scope,
                        "print",
                        0,
                        Builtins::print,
                        ScriptableObject.PERMANENT | ScriptableObject.DONTENUM,
                        ScriptableObject.DONTENUM | ScriptableObject.READONLY);
    }

    private static Object print(Context cx, VarScope scope, Object thisObj, Object[] args) {
        try {
            Builtins self = getSelf(scope);
            for (Object arg : args) {
                self.stdout.write(ScriptRuntime.toString(arg));
            }
            self.stdout.write('\n');
            // ref bug https://github.com/mozilla/rhino/issues/1356
            self.stdout.flush();
        } catch (IOException e) {
            throw Context.throwAsScriptRuntimeEx(e);
        }
        return Undefined.instance;
    }

    private static Builtins getSelf(VarScope scope) {
        // Since this class is invoked as a set of anonymous functions, "this"
        // in JavaScript does not point to "this" in Java. We set a key on the
        // top-level scope to address this.
        return (Builtins) ScriptableObject.getTopScopeValue(scope, BUILTIN_KEY);
    }
}
