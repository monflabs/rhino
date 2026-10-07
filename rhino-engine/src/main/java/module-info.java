module org.monflabs.mozilla.rhino.engine {
    exports org.monflabs.mozilla.javascript.engine;

    provides javax.script.ScriptEngineFactory with
            org.monflabs.mozilla.javascript.engine.RhinoScriptEngineFactory;

    requires transitive org.monflabs.mozilla.rhino;
    requires transitive java.scripting;
}
