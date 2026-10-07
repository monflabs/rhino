package org.monflabs.mozilla.javascript.tests.backwardcompat;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/backwardcompat/backward-proto-property.js")
@LanguageVersion(Context.VERSION_1_8)
public class BackwardProtoPropertyTest extends ScriptTestsBase {}
