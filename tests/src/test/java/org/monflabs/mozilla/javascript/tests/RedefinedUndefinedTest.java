package org.monflabs.mozilla.javascript.tests;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/redefined-undefined.js")
@LanguageVersion(Context.VERSION_ES6)
public class RedefinedUndefinedTest extends ScriptTestsBase {}
