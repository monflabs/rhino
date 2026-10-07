module org.monflabs.mozilla.rhino {
    uses org.monflabs.mozilla.javascript.NullabilityDetector;

    exports org.monflabs.mozilla.classfile;
    exports org.monflabs.mozilla.javascript;
    exports org.monflabs.mozilla.javascript.annotations;
    exports org.monflabs.mozilla.javascript.ast;
    exports org.monflabs.mozilla.javascript.commonjs.module;
    exports org.monflabs.mozilla.javascript.commonjs.module.provider;
    exports org.monflabs.mozilla.javascript.debug;
    exports org.monflabs.mozilla.javascript.optimizer;
    exports org.monflabs.mozilla.javascript.serialize;
    exports org.monflabs.mozilla.javascript.sourcemap;
    exports org.monflabs.mozilla.javascript.typedarrays;
    exports org.monflabs.mozilla.javascript.xml;
    exports org.monflabs.mozilla.javascript.config;
    exports org.monflabs.mozilla.javascript.lc.type;

    uses org.monflabs.mozilla.javascript.RegExpLoader;
    uses org.monflabs.mozilla.javascript.xml.XMLLoader;
    uses org.monflabs.mozilla.javascript.config.RhinoPropertiesLoader;

    provides org.monflabs.mozilla.javascript.RegExpLoader with
            org.monflabs.mozilla.javascript.regexp.RegExpLoaderImpl;

    requires java.compiler;
    requires jdk.dynalink;
    requires jdk.jfr;
    requires transitive java.desktop;
    requires java.logging;

    exports org.monflabs.mozilla.javascript.interpreterv2;
    exports org.monflabs.mozilla.javascript.interpreterv2.instruction;
    exports org.monflabs.mozilla.javascript.interpreterv2.operand;
}
