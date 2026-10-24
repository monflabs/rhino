/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.monflabs.mozilla.javascript;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * handed to its {@code init}), the second evaluates its body.
 */
public final class ModuleRecord {
    private static final Object REGISTRY_KEY = ModuleRecord.class;

    private enum Status {
        NEW,
        LINKING,
        LINKED,
        EVALUATING,
        /** its synchronous part has run; it waits on an await, its own or a dependency's */
        EVALUATING_ASYNC,
        EVALUATED
    }

    /** A resolved binding: a binding name of a module, or its namespace. */
    record Binding(ModuleRecord module, String bindingName) {}

    /** ResolveExport's "ambiguous" answer. */
    private static final Binding AMBIGUOUS = new Binding(null, null);

    private final Module module;
    private final TopLevel realm;
    private final ModuleInfo info;
    private final Map<ModuleInfo.ModuleRequest, ModuleRecord> requested = new LinkedHashMap<>();
    private final Map<String, Callable> getters = new HashMap<>();
    private Status status = Status.NEW;
    private RuntimeException evaluationError;
    private Callable generatorFunction;
    private Scriptable generator;
    private ModuleNamespace namespace;

    // the state of an evaluation (16.2.1.6 Cyclic Module Records)
    private int dfsIndex;
    private int dfsAncestorIndex;
    private ModuleRecord cycleRoot;
    private boolean asyncEvaluation;
    private long asyncEvaluationOrder;
    private int pendingAsyncDependencies;
    private final List<ModuleRecord> asyncParentModules = new ArrayList<>();
    private NativePromise.Capability topLevelCapability;
    private static final java.util.concurrent.atomic.AtomicLong ASYNC_EVALUATION_ORDER =
            new java.util.concurrent.atomic.AtomicLong(1);

    private ModuleRecord(Module module, TopLevel realm, ModuleInfo info) {
        this.module = module;
        this.realm = realm;
        this.info = info;
    }

    /**
     * The module that import(specifier) names in the script or module of the given name, loaded and
     * linked; its evaluation follows (see {@link #evaluateToPromise}).
     */
    static ModuleRecord importDynamic(
            Context cx,
            TopLevel realm,
            String referrerName,
            String specifier,
            Map<String, String> attributes) {
        ModuleRecord referrerRecord = registry(realm).get(referrerName);
        int generated = referrerName.indexOf('#');
        if (referrerRecord == null && generated > 0) {
            // eval code (file#line(eval)): the script or module that contains it is the referrer
            referrerName = referrerName.substring(0, generated);
            referrerRecord = registry(realm).get(referrerName);
        }
        Module referrer;
        if (referrerRecord != null) {
            referrer = referrerRecord.module;
        } else {
            Path origin = null;
            try {
                Path path = Path.of(referrerName);
                if (Files.isRegularFile(path)) {
                    origin = path.toAbsolutePath();
                }
            } catch (InvalidPathException notAPath) {
                // a script that is not a file: specifiers resolve against the loaders' roots
            }
            referrer = origin == null ? null : Module.referrer(referrerName, origin);
        }
        ModuleRecord record = load(cx, realm, specifier, attributes, referrer);
        record.link(cx);
        return record;
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
        try {
            ModuleScope scope = new ModuleScope(realm);
            record.generatorFunction =
                    (Callable)
                            cx.evaluateString(
                                    scope, info.generatorSource(), module.name(), 1, null);
            record.scope = scope;
            for (ModuleInfo.ModuleRequest request : info.requestedModules) {
                record.requested.put(
                        request,
                        load(cx, realm, request.specifier(), request.attributes(), module));
            }
        } catch (RuntimeException e) {
            // a module that could not be loaded is not kept: a later import tries again
            registry.remove(module.name());
            throw e;
        }
        return record;
    }

    private ModuleScope scope;

    /**
     * HostLoadImportedModule for an import with attributes: {@code type: "json"} makes a JSON
     * module of the text the loaders return (16.2.1.8 CreateDefaultExportSyntheticModule), kept
     * apart from the same module imported as JavaScript; other keys are a SyntaxError
     * (AllImportAttributesSupported), other types a TypeError.
     */
    private static ModuleRecord load(
            Context cx,
            TopLevel realm,
            String specifier,
            Map<String, String> attributes,
            Module referrer) {
        for (String key : attributes.keySet()) {
            if (!"type".equals(key)) {
                throw ScriptRuntime.constructError(
                        "SyntaxError",
                        ScriptRuntime.getMessageById("msg.import.attribute.unsupported", key));
            }
        }
        String type = attributes.get("type");
        if (type != null && !"json".equals(type)) {
            throw ScriptRuntime.typeErrorById("msg.import.type.unsupported", type);
        }
        Module module = resolve(cx, specifier, referrer);
        if (type == null || module.text() == null) {
            return load(cx, realm, module);
        }
        String name = "[json] " + module.name();
        Map<String, ModuleRecord> registry = registry(realm);
        ModuleRecord record = registry.get(name);
        if (record == null) {
            Object value = NativeJSON.parse(cx, realm, module.text());
            record =
                    new ModuleRecord(
                            Module.values(name, Collections.singletonMap("default", value)),
                            realm,
                            null);
            registry.put(name, record);
        }
        return record;
    }

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
        throw ScriptRuntime.typeErrorById(
                "msg.module.not.found", specifier, referrer == null ? "" : referrer.name());
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
        // import.meta (13.3.12.1): an ordinary object with a null prototype
        NativeObject meta = new NativeObject();
        meta.setParentScope(realm);
        meta.setPrototype(null);
        ScriptableObject.putProperty(
                meta, "url", module.origin() == null ? getName() : module.origin().toString());
        ScriptableObject.putProperty(
                recordObject,
                "importMeta",
                new LambdaFunction(realm, "importMeta", 0, (lcx, ls, t, a) -> meta));
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
        if (generator instanceof NativeAsyncGenerator) {
            // a module with a top-level await: its body is driven by evaluation
            ES6Generator body = ((NativeAsyncGenerator) generator).body();
            inTopCall(cx, lcx -> body.resumeLocal(lcx, realm, Undefined.instance));
        } else {
            ScriptableObject.callMethod(cx, generator, "next", ScriptRuntime.emptyArgs);
        }
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
     * body, once. With a top-level await in the graph, the evaluation completes in promise jobs,
     * which this method runs (unless microtask processing is suspended); an evaluation error is
     * thrown, also by a later evaluation.
     */
    public void evaluate(Context cx) {
        evaluateToPromise(cx);
        ModuleRecord root = evaluationRoot();
        if (root.status == Status.EVALUATING_ASYNC) {
            cx.processMicrotasks();
        }
        if (evaluationError != null) {
            throw evaluationError;
        }
        if (root.evaluationError != null) {
            throw root.evaluationError;
        }
    }

    private ModuleRecord evaluationRoot() {
        if ((status == Status.EVALUATING_ASYNC || status == Status.EVALUATED)
                && cycleRoot != null) {
            return cycleRoot;
        }
        return this;
    }

    /**
     * Evaluate (16.2.1.5.3) without running the promise jobs: the promise that settles once the
     * module graph is evaluated.
     */
    Object evaluateToPromise(Context cx) {
        if (status == Status.NEW || status == Status.LINKING) {
            link(cx);
        }
        ModuleRecord module = evaluationRoot();
        if (module.topLevelCapability != null) {
            return module.topLevelCapability.promise;
        }
        NativePromise.Capability capability = NativePromise.newCapability(cx, realm);
        module.topLevelCapability = capability;
        // evaluate() throws a rejection: it is handled
        NativePromise.await(
                cx,
                realm,
                capability.promise,
                (lcx, ls, t, args) -> Undefined.instance,
                (lcx, ls, t, args) -> Undefined.instance);
        java.util.ArrayDeque<ModuleRecord> stack = new java.util.ArrayDeque<>();
        try {
            module.innerModuleEvaluation(cx, stack, 0);
        } catch (RhinoException e) {
            for (ModuleRecord m : stack) {
                m.status = Status.EVALUATED;
                m.evaluationError = e;
            }
            module.status = Status.EVALUATED;
            module.evaluationError = e;
            Object error = ScriptRuntime.moduleErrorValue(cx, realm, e);
            capability.reject.call(cx, realm, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {error});
            return capability.promise;
        }
        if (!module.asyncEvaluation) {
            capability.resolve.call(
                    cx, realm, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {Undefined.instance});
        }
        return capability.promise;
    }

    private boolean hasTopLevelAwait() {
        return info != null && info.hasTopLevelAwait;
    }

    /** InnerModuleEvaluation (16.2.1.5.3.1). */
    private int innerModuleEvaluation(Context cx, java.util.Deque<ModuleRecord> stack, int index) {
        if (info == null) {
            // a module of Java values: nothing to run
            if (status != Status.EVALUATED) {
                status = Status.EVALUATED;
                cycleRoot = this;
            }
            return index;
        }
        if (status == Status.EVALUATING_ASYNC || status == Status.EVALUATED) {
            if (evaluationError != null) {
                throw evaluationError;
            }
            return index;
        }
        if (status == Status.EVALUATING) {
            return index;
        }
        status = Status.EVALUATING;
        dfsIndex = index;
        dfsAncestorIndex = index;
        pendingAsyncDependencies = 0;
        index++;
        stack.addLast(this);
        for (ModuleRecord required : requested.values()) {
            index = required.innerModuleEvaluation(cx, stack, index);
            if (required.status == Status.EVALUATING) {
                dfsAncestorIndex = Math.min(dfsAncestorIndex, required.dfsAncestorIndex);
            } else {
                required = required.cycleRoot != null ? required.cycleRoot : required;
                if (required.evaluationError != null) {
                    throw required.evaluationError;
                }
            }
            if (required.asyncEvaluation) {
                pendingAsyncDependencies++;
                required.asyncParentModules.add(this);
            }
        }
        if (pendingAsyncDependencies > 0 || hasTopLevelAwait()) {
            asyncEvaluation = true;
            asyncEvaluationOrder = ASYNC_EVALUATION_ORDER.getAndIncrement();
            if (pendingAsyncDependencies == 0) {
                executeAsyncModule(cx);
            }
        } else {
            executeModule(cx);
        }
        if (dfsAncestorIndex == dfsIndex) {
            ModuleRecord m;
            do {
                m = stack.removeLast();
                m.status = m.asyncEvaluation ? Status.EVALUATING_ASYNC : Status.EVALUATED;
                m.cycleRoot = this;
            } while (m != this);
        }
        return index;
    }

    /** ExecuteModule (16.2.1.6.5) of a module without a top-level await. */
    private void executeModule(Context cx) {
        if (generator != null) {
            ScriptableObject.callMethod(cx, generator, "next", ScriptRuntime.emptyArgs);
        }
    }

    /** ExecuteAsyncModule (16.2.1.5.3.2): runs the body of a module with a top-level await. */
    private void executeAsyncModule(Context cx) {
        ES6Generator body = ((NativeAsyncGenerator) generator).body();
        Object promise = inTopCall(cx, lcx -> AsyncFunctionDriver.runModule(lcx, realm, body));
        NativePromise.await(
                cx,
                realm,
                promise,
                (lcx, ls, t, args) -> {
                    asyncModuleExecutionFulfilled(lcx);
                    return Undefined.instance;
                },
                (lcx, ls, t, args) -> {
                    asyncModuleExecutionRejected(lcx, args.length > 0 ? args[0] : null);
                    return Undefined.instance;
                });
    }

    /** AsyncModuleExecutionFulfilled (16.2.1.5.3.4). */
    private void asyncModuleExecutionFulfilled(Context cx) {
        if (status == Status.EVALUATED) {
            return;
        }
        asyncEvaluation = false;
        status = Status.EVALUATED;
        if (topLevelCapability != null) {
            topLevelCapability.resolve.call(
                    cx, realm, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {Undefined.instance});
        }
        List<ModuleRecord> execList = new ArrayList<>();
        gatherAvailableAncestors(execList);
        execList.sort((a, b) -> Long.compare(a.asyncEvaluationOrder, b.asyncEvaluationOrder));
        for (ModuleRecord m : execList) {
            if (m.status == Status.EVALUATED) {
                continue;
            }
            if (m.hasTopLevelAwait()) {
                m.executeAsyncModule(cx);
                continue;
            }
            try {
                inTopCall(
                        cx,
                        lcx -> {
                            m.executeModule(lcx);
                            return null;
                        });
            } catch (RhinoException e) {
                m.asyncModuleExecutionRejected(cx, ScriptRuntime.moduleErrorValue(cx, realm, e));
                continue;
            }
            m.asyncEvaluation = false;
            m.status = Status.EVALUATED;
            if (m.topLevelCapability != null) {
                m.topLevelCapability.resolve.call(
                        cx,
                        realm,
                        Undefined.SCRIPTABLE_UNDEFINED,
                        new Object[] {Undefined.instance});
            }
        }
    }

    /** GatherAvailableAncestors (16.2.1.5.3.3). */
    private void gatherAvailableAncestors(List<ModuleRecord> execList) {
        for (ModuleRecord parent : asyncParentModules) {
            if (execList.contains(parent)
                    || (parent.cycleRoot != null && parent.cycleRoot.evaluationError != null)) {
                continue;
            }
            parent.pendingAsyncDependencies--;
            if (parent.pendingAsyncDependencies == 0) {
                execList.add(parent);
                if (!parent.hasTopLevelAwait()) {
                    parent.gatherAvailableAncestors(execList);
                }
            }
        }
    }

    /** AsyncModuleExecutionRejected (16.2.1.5.3.5). */
    private void asyncModuleExecutionRejected(Context cx, Object reason) {
        if (status == Status.EVALUATED) {
            return;
        }
        evaluationError = new JavaScriptException(reason, getName(), 0);
        status = Status.EVALUATED;
        asyncEvaluation = false;
        if (topLevelCapability != null) {
            topLevelCapability.reject.call(
                    cx, realm, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {reason});
        }
        for (ModuleRecord parent : asyncParentModules) {
            parent.asyncModuleExecutionRejected(cx, reason);
        }
    }

    private Object inTopCall(Context cx, java.util.function.Function<Context, Object> action) {
        if (ScriptRuntime.hasTopCall(cx)) {
            return action.apply(cx);
        }
        return ScriptRuntime.doTopCall(
                (lcx, ls, t, args) -> action.apply(lcx),
                cx,
                realm,
                Undefined.SCRIPTABLE_UNDEFINED,
                ScriptRuntime.emptyArgs,
                true);
    }

    @Override
    public String toString() {
        return "ModuleRecord " + getName();
    }
}
