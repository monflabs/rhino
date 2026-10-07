package org.monflabs.mozilla.javascript.tests.harmony;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/harmony/promise-prototype-finally.js")
@LanguageVersion(Context.VERSION_ES6)
public class PromiseFinallyTest extends ScriptTestsBase {}
