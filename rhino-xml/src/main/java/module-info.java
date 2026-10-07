module org.monflabs.mozilla.javascript.xml {
    exports org.monflabs.mozilla.javascript.xmlimpl;

    provides org.monflabs.mozilla.javascript.xml.XMLLoader with
            org.monflabs.mozilla.javascript.xmlimpl.XMLLoaderImpl;

    requires transitive org.monflabs.mozilla.rhino;
    requires transitive java.xml;
}
