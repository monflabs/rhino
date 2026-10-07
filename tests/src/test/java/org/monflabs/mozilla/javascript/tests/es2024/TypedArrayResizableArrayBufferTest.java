package org.monflabs.mozilla.javascript.tests.es2024;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es2024/typedarray-resizablearraybuffer.js")
@LanguageVersion(Context.VERSION_ES6)
public class TypedArrayResizableArrayBufferTest extends ScriptTestsBase {}
