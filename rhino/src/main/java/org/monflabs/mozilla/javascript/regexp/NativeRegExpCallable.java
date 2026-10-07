package org.monflabs.mozilla.javascript.regexp;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.Function;
import org.monflabs.mozilla.javascript.Scriptable;
import org.monflabs.mozilla.javascript.VarScope;

/**
 * Legacy implementation of RegExp was callable, this class exists to preserve this functionality
 */
class NativeRegExpCallable extends NativeRegExp implements Function {

    NativeRegExpCallable(VarScope scope, RECompiled compiled) {
        super(scope, compiled);
    }

    NativeRegExpCallable() {
        super();
    }

    @Override
    public Object call(Context cx, VarScope scope, Object thisObj, Object[] args) {
        return execSub(cx, scope, args, MATCH);
    }

    @Override
    public Scriptable construct(Context cx, Object nt, VarScope scope, Object[] args) {
        return (Scriptable) execSub(cx, scope, args, MATCH);
    }
}
