package org.monflabs.mozilla.javascript.xml;

import org.monflabs.mozilla.javascript.ScopeObject;

/** This interface is used to load the XML implementation using the ServiceLoader pattern. */
public interface XMLLoader {
    void load(ScopeObject scope, boolean sealed);

    XMLLib.Factory getFactory();
}
