/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.monflabs.mozilla.javascript.modules.Module;
import org.monflabs.mozilla.javascript.modules.ModuleLoader;
import org.monflabs.mozilla.javascript.modules.PathModuleLoader;

/**
 * A module of a realm (ECMA-262 16.2.1.6 Source Text Module Record), or a module of Java values.
 *
 * <p>A source module runs as a generator function compiled from its rewritten source (see {@link
 * ModuleInfo#generatorSource()}), whose scope is a {@link ModuleScope}: the first {@code next()}
 * instantiates the module (its functions are hoisted and the getters of its exported bindings are
 * handed to {@link #init}), the second evaluates its body.
 */
public final class ModuleRecord {
    private static final Object REGISTRY_KEY = ModuleRecord.class;

    private enum Status {
        NEW,
        LINKING,
        LINKED,
        EVALUATING,
        EVALUATED
    }

    /** A resolved binding: a binding name of a module, or its namespace. */
    record Binding(ModuleRecord module, String bindingName) {}

    /** ResolveExport's "ambiguous" answer. */
    private static final Binding AMBIGUOUS = new Binding(null, null);

    private final Module module;
    private final TopLevel realm;
    private final ModuleInfo info;
    private final Map<String, ModuleRecord> requested = new LinkedHashMap<>();
    private final Map<String, Callable> getters = new HashMap<>();
    private Status status = Status.NEW;
    private RuntimeException evaluationError;
    private Callable generatorFunction;
    private Scriptable generator;
    private ModuleNamespace namespace;

    private ModuleRecord(Module module, TopLevel realm, ModuleInfo info) {
        this.module = module;
        this.realm = realm;
        this.info = info;
    }

    /** The name of the module (its identity in the realm). */
    public String getName() {
        return module.name();
    }

    /**
     * Loads a module and the modules it imports, transitively (HostLoadImportedModule). A module
     * already loaded in the realm under the same name is returned as is.
     */
    static ModuleRecord load(Context cx, TopLevel realm, Module module) {
        Map<String, ModuleRecord> registry = registry(realm);
        ModuleRecord record = registry.get(module.name());
        if (record != null) {
            return record;
        }
        if (module.text() == null) {
            record = new ModuleRecord(module, realm, null);
            registry.put(module.name(), record);
            return record;
        }
        CompilerEnvirons env = new CompilerEnvirons();
        env.initFromContext(cx);
        Parser parser = new Parser(env, env.getErrorReporter());
        parser.parseModule(module.text(), module.name(), 1);
        ModuleInfo info = parser.moduleInfo;
        record = new ModuleRecord(module, realm, info);
        registry.put(module.name(), record);
        ModuleScope scope = new ModuleScope(realm);
        record.generatorFunction =
                (Callable) cx.evaluateString(scope, info.generatorSource(), module.name(), 1, null);
        record.scope = scope;
        for (String specifier : info.requestedModules) {
            record.requested.put(specifier, load(cx, realm, resolve(cx, specifier, module)));
        }
        return record;
    }

    private ModuleScope scope;

    private static Module resolve(Context cx, String specifier, Module referrer) {
        List<ModuleLoader> loaders = cx.getModuleLoaders();
        if (loaders.isEmpty()) {
            loaders = List.of(new PathModuleLoader(Path.of("")));
        }
        for (ModuleLoader loader : loaders) {
            Module m = loader.load(specifier, referrer);
            if (m != null) {
                return m;
            }
        }
        throw ScriptRuntime.typeErrorById("msg.module.not.found", specifier, referrer.name());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModuleRecord> registry(TopLevel realm) {
        Map<String, ModuleRecord> registry =
                (Map<String, ModuleRecord>) realm.getAssociatedValue(REGISTRY_KEY);
        if (registry == null) {
            registry = new HashMap<>();
            realm.associateValue(REGISTRY_KEY, registry);
        }
        return registry;
    }

    /**
     * Links the module and the modules it imports (16.2.1.5.3 Link): every import and indirect
     * export is resolved, and every module of the graph is instantiated, before any body runs.
     */
    public void link(Context cx) {
        if (status != Status.NEW) {
            return;
        }
        status = Status.LINKING;
        for (ModuleRecord r : requested.values()) {
            r.link(cx);
        }
        initializeEnvironment(cx);
        status = Status.LINKED;
    }

    /** InitializeEnvironment (16.2.1.6.4). */
    private void initializeEnvironment(Context cx) {
        if (info == null) {
            return;
        }
        for (ModuleInfo.ExportEntry ee : info.indirectExportEntries) {
            Binding resolution = resolveExport(ee.exportName(), new HashSet<>());
            checkResolution(resolution, this, ee.exportName());
        }
        for (ModuleInfo.ImportEntry ie : info.importEntries) {
            ModuleRecord imported = requested.get(ie.moduleRequest());
            if (ModuleInfo.NAMESPACE.equals(ie.importName())) {
                scope.addImport(ie.localName(), new Binding(imported, ModuleInfo.NAMESPACE));
            } else {
                Binding resolution = imported.resolveExport(ie.importName(), new HashSet<>());
                checkResolution(resolution, imported, ie.importName());
                scope.addImport(ie.localName(), resolution);
            }
        }
        Scriptable recordObject = cx.newObject(realm);
        ScriptableObject.putProperty(
                recordObject,
                "init",
                new LambdaFunction(
                        realm,
                        "init",
                        0,
                        (lcx, lscope, thisObj, args) -> {
                            for (int i = 0; i + 1 < args.length; i += 2) {
                                getters.put((String) args[i], (Callable) args[i + 1]);
                            }
                            return Undefined.instance;
                        }));
        generator =
                (Scriptable)
                        generatorFunction.call(
                                cx,
                                realm,
                                Undefined.SCRIPTABLE_UNDEFINED,
                                new Object[] {recordObject});
        ScriptableObject.callMethod(cx, generator, "next", ScriptRuntime.emptyArgs);
        if (info.anonymousDefaultFunction) {
            // export default function () {}: the function is named "default"
            Object fn = readBinding(cx, ModuleInfo.DEFAULT_BINDING);
            if (fn instanceof BaseFunction) {
                ((BaseFunction) fn).setFunctionName("default");
            }
        }
    }

    private static void checkResolution(Binding resolution, ModuleRecord module, String name) {
        if (resolution == null) {
            throw ScriptRuntime.syntaxErrorById("msg.module.unresolvable", module.getName(), name);
        }
        if (resolution == AMBIGUOUS) {
            throw ScriptRuntime.syntaxErrorById("msg.module.ambiguous", module.getName(), name);
        }
    }

    /**
     * ResolveExport (16.2.1.6.3): the binding an export name stands for, null if there is none, or
     * {@link #AMBIGUOUS}.
     */
    Binding resolveExport(String exportName, Set<Binding> resolveSet) {
        if (info == null) {
            return module.exports().containsKey(exportName) ? new Binding(this, exportName) : null;
        }
        Binding visit = new Binding(this, exportName);
        if (!resolveSet.add(visit)) {
            return null; // a circular import request
        }
        for (ModuleInfo.ExportEntry e : info.localExportEntries) {
            if (exportName.equals(e.exportName())) {
                return new Binding(this, e.localName());
            }
        }
        for (ModuleInfo.ExportEntry e : info.indirectExportEntries) {
            if (exportName.equals(e.exportName())) {
                ModuleRecord imported = requested.get(e.moduleRequest());
                if (ModuleInfo.NAMESPACE.equals(e.importName())) {
                    return new Binding(imported, ModuleInfo.NAMESPACE);
                }
                return imported.resolveExport(e.importName(), resolveSet);
            }
        }
        if ("default".equals(exportName)) {
            return null; // export * does not provide a default
        }
        Binding starResolution = null;
        for (ModuleInfo.ExportEntry e : info.starExportEntries) {
            ModuleRecord imported = requested.get(e.moduleRequest());
            Binding resolution = imported.resolveExport(exportName, resolveSet);
            if (resolution == AMBIGUOUS) {
                return AMBIGUOUS;
            }
            if (resolution != null) {
                if (starResolution == null) {
                    starResolution = resolution;
                } else if (resolution.module() != starResolution.module()
                        || !Objects.equals(
                                resolution.bindingName(), starResolution.bindingName())) {
                    return AMBIGUOUS;
                }
            }
        }
        return starResolution;
    }

    /** GetExportedNames (16.2.1.6.2). */
    Set<String> getExportedNames(Set<ModuleRecord> exportStarSet) {
        Set<String> names = new LinkedHashSet<>();
        if (info == null) {
            names.addAll(module.exports().keySet());
            return names;
        }
        if (!exportStarSet.add(this)) {
            return names;
        }
        for (ModuleInfo.ExportEntry e : info.localExportEntries) {
            names.add(e.exportName());
        }
        for (ModuleInfo.ExportEntry e : info.indirectExportEntries) {
            names.add(e.exportName());
        }
        for (ModuleInfo.ExportEntry e : info.starExportEntries) {
            for (String n : requested.get(e.moduleRequest()).getExportedNames(exportStarSet)) {
                if (!"default".equals(n)) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    /** The value of a binding of this module, or its namespace object. */
    Object readBinding(Context cx, String bindingName) {
        if (ModuleInfo.NAMESPACE.equals(bindingName)) {
            return getNamespace();
        }
        if (info == null) {
            return module.exports().get(bindingName);
        }
        Callable getter = getters.get(bindingName);
        if (getter == null) {
            throw ScriptRuntime.constructError(
                    "ReferenceError", ScriptRuntime.getMessageById("msg.module.tdz", bindingName));
        }
        return getter.call(cx, realm, Undefined.SCRIPTABLE_UNDEFINED, ScriptRuntime.emptyArgs);
    }

    /** The namespace object of the module (16.2.1.10 GetModuleNamespace). */
    public Scriptable getNamespace() {
        if (namespace == null) {
            List<String> names = new ArrayList<>();
            Map<String, Binding> bindings = new LinkedHashMap<>();
            for (String name : getExportedNames(new HashSet<>())) {
                Binding resolution = resolveExport(name, new HashSet<>());
                if (resolution != null && resolution != AMBIGUOUS) {
                    names.add(name);
                    bindings.put(name, resolution);
                }
            }
            namespace = new ModuleNamespace(realm, bindings);
        }
        return namespace;
    }

    /**
     * Evaluates the module (16.2.1.5.3 Evaluate): its imported modules first, depth first, then its
     * body, once. An error is remembered and thrown again by a later evaluation.
     */
    public void evaluate(Context cx) {
        if (status == Status.EVALUATED || status == Status.EVALUATING) {
            if (evaluationError != null) {
                throw evaluationError;
            }
            return;
        }
        if (status != Status.LINKED) {
            link(cx);
        }
        status = Status.EVALUATING;
        try {
            for (ModuleRecord r : requested.values()) {
                r.evaluate(cx);
            }
            if (generator != null) {
                ScriptableObject.callMethod(cx, generator, "next", ScriptRuntime.emptyArgs);
            }
        } catch (RuntimeException e) {
            evaluationError = e;
            throw e;
        } finally {
            status = Status.EVALUATED;
        }
    }

    @Override
    public String toString() {
        return "ModuleRecord " + getName();
    }
}
