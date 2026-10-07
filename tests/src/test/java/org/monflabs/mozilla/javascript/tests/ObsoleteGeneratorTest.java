package org.monflabs.mozilla.javascript.tests;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/extensions/obsolete-generators.js")
@LanguageVersion(Context.VERSION_1_8)
public class ObsoleteGeneratorTest extends ScriptTestsBase {}
