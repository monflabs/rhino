package org.monflabs.mozilla.javascript.regexp;

import org.monflabs.mozilla.javascript.RegExpLoader;
import org.monflabs.mozilla.javascript.RegExpProxy;

/** This class loads the default RegExp implementation. */
public class RegExpLoaderImpl implements RegExpLoader {
    @Override
    public RegExpProxy newProxy() {
        return new RegExpImpl();
    }
}
