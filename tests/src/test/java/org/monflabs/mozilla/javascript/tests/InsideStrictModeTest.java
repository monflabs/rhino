package org.monflabs.mozilla.javascript.tests;

import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest(value = "testsrc/jstests/inside-strict-mode.js")
public class InsideStrictModeTest extends ScriptTestsBase {}
