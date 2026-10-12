/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.EcmaError;
import org.monflabs.mozilla.javascript.EvaluatorException;
import org.monflabs.mozilla.javascript.ModuleRecord;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.TopLevel;
import org.monflabs.mozilla.javascript.modules.JavaModuleLoader;
import org.monflabs.mozilla.javascript.modules.Module;
import org.monflabs.mozilla.javascript.modules.ModuleLoader;
import org.monflabs.mozilla.javascript.modules.PathModuleLoader;
import org.monflabs.mozilla.javascript.testutils.Utils;

/** ES modules and the module-loader API (plan 1.12). */
class ModuleTest {

    @TempDir Path dir;

    private void write(String name, String text) throws IOException {
        Files.writeString(dir.resolve(name), text);
    }

    private static Object get(Scriptable ns, String name) {
        return ScriptableObject.getProperty(ns, name);
    }

    private static void inAllModes(ModuleLoader loader, ContextAction action) {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
                    if (loader != null) {
                        cx.setModuleLoaders(loader);
                    }
                    try {
                        action.run(cx, cx.initStandardObjects());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
    }

    private interface ContextAction {
        void run(Context cx, TopLevel scope) throws IOException;
    }

    @Test
    void liveBindingsDefaultAndNamespace() throws IOException {
        write(
                "counter.js",
                "export let count = 0;\n"
                        + "export function increment() { count++; }\n"
                        + "export default 'the default';\n");
        write(
                "main.js",
                "import theDefault, { count, increment } from './counter.js';\n"
                        + "import * as counter from './counter.js';\n"
                        + "increment();\n"
                        + "export const r = [count, theDefault, counter.count,"
                        + " Object.keys(counter).join('|'), typeof this,"
                        + " Object.prototype.toString.call(counter)].join();\n");
        inAllModes(
                new PathModuleLoader(dir),
                (cx, scope) -> {
                    Path main = dir.resolve("main.js");
                    Scriptable ns =
                            cx.evaluateModule(
                                    scope,
                                    Module.source(main.toString(), Files.readString(main), main));
                    assertEquals(
                            "1,the default,1,count|default|increment,undefined,[object Module]",
                            get(ns, "r"));
                });
    }

    @Test
    void javaValuesModulesAndModuleScope() {
        JavaModuleLoader loader =
                new JavaModuleLoader().add("config", Map.of("default", "prod", "port", 8080));
        inAllModes(
                loader,
                (cx, scope) -> {
                    Scriptable ns =
                            cx.evaluateModule(
                                    scope,
                                    "import env, { port } from 'config';\n"
                                            + "var local = 1;\n"
                                            + "export const r = env + ':' + port;\n",
                                    "main");
                    assertEquals("prod:8080", get(ns, "r"));
                    // module declarations are not globals
                    assertEquals(
                            "undefined",
                            cx.evaluateString(scope, "typeof local", "check", 1, null));
                });
    }

    @Test
    void cyclesAndHoistedFunctions() {
        JavaModuleLoader none = new JavaModuleLoader();
        ModuleLoader loader =
                (specifier, referrer) ->
                        switch (specifier) {
                            case "a" ->
                                    Module.source(
                                            "a",
                                            "import { b } from 'b';\n"
                                                    + "export function a() { return 'a'; }\n"
                                                    + "export const r = b();\n");
                            case "b" ->
                                    Module.source(
                                            "b",
                                            "import { a } from 'a';\n"
                                                    + "export function b() { return a() + 'b'; }\n");
                            default -> none.load(specifier, referrer);
                        };
        inAllModes(
                loader,
                (cx, scope) -> {
                    Scriptable ns =
                            cx.evaluateModule(scope, Module.source("main", "export * from 'a';"));
                    assertEquals("ab", get(ns, "r"));
                });
    }

    @Test
    void linkErrorsAndImmutableImports() {
        ModuleLoader loader =
                (specifier, referrer) ->
                        "m".equals(specifier) ? Module.source("m", "export let x = 1;") : null;
        inAllModes(
                loader,
                (cx, scope) -> {
                    EcmaError missing =
                            assertThrows(
                                    EcmaError.class,
                                    () -> cx.evaluateModule(scope, "import { y } from 'm';", "e1"));
                    assertEquals("SyntaxError", missing.getName());
                    EcmaError assign =
                            assertThrows(
                                    EcmaError.class,
                                    () ->
                                            cx.evaluateModule(
                                                    scope, "import { x } from 'm'; x = 2;", "e2"));
                    assertEquals("TypeError", assign.getName());
                    assertThrows(
                            EvaluatorException.class,
                            () -> cx.evaluateModule(scope, "export { undeclared };", "e3"));
                    EcmaError notFound =
                            assertThrows(
                                    EcmaError.class,
                                    () -> cx.evaluateModule(scope, "import 'nowhere';", "e4"));
                    assertTrue(notFound.getErrorMessage().contains("nowhere"));
                });
    }

    @Test
    void moduleEarlyErrors() {
        inAllModes(
                null,
                (cx, scope) -> {
                    for (String source :
                            new String[] {
                                "function f() {} function f() {}",
                                "var f; function f() {}",
                                "x\n-->;",
                                "export d\\u0065fault 0;",
                                "export var a; export { a };",
                                "import { x } from 'm'; let x;",
                                "{ export var b; }",
                                "new.target;"
                            }) {
                        assertThrows(
                                EvaluatorException.class,
                                () -> cx.evaluateModule(scope, source, "early"),
                                source);
                    }
                });
    }

    @Test
    void superSetWithNamespaceReceiver() {
        ModuleLoader loader =
                (specifier, referrer) ->
                        "self".equals(specifier)
                                ? Module.source(
                                        "self",
                                        "import * as ns from 'self';\n"
                                                + "class A { constructor() { return ns; } }\n"
                                                + "class B extends A {"
                                                + " constructor() { super(); super.x = 1; } }\n"
                                                + "export let r = [];\n"
                                                + "try { new B(); } catch (e) { r.push(e.name); }\n"
                                                + "export let x = 0;\n"
                                                + "try { new B(); } catch (e) { r.push(e.name); }\n"
                                                + "r = r.join();\n")
                                : null;
        inAllModes(
                loader,
                (cx, scope) -> {
                    Scriptable ns = cx.evaluateModule(scope, "export * from 'self';", "main");
                    assertEquals("ReferenceError,TypeError", get(ns, "r"));
                });
    }

    @Test
    void evaluatedOnceAndFactoryLoaders() {
        ModuleLoader loader =
                (specifier, referrer) -> {
                    if (!"once".equals(specifier)) return null;
                    return Module.source(
                            "once", "globalThis.runs = (globalThis.runs || 0) + 1; export {};");
                };
        inAllModes(
                loader,
                (cx, scope) -> {
                    cx.evaluateModule(scope, "import 'once'; import 'once';", "x1");
                    cx.evaluateModule(scope, "import 'once';", "x2");
                    ModuleRecord record =
                            cx.loadModule(scope, Module.source("x3", "import 'once';"));
                    record.link(cx);
                    record.evaluate(cx);
                    assertEquals(
                            1,
                            ((Number) ScriptableObject.getProperty(scope.getGlobalThis(), "runs"))
                                    .intValue());
                });
    }
}
