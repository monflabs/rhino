package org.monflabs.mozilla.javascript.tests.es5;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/extensions/getset-null.js")
@LanguageVersion(Context.VERSION_1_8)
public class GetSetNullTest extends ScriptTestsBase {}
