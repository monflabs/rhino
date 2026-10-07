package org.monflabs.mozilla.javascript.tests;

import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest(value = "testsrc/jstests/redefine-properties.js")
public class RedefinePropertyTest extends ScriptTestsBase {}
