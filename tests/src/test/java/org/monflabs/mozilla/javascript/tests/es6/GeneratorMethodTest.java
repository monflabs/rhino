package org.monflabs.mozilla.javascript.tests.es6;

import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.drivers.LanguageVersion;
import org.monflabs.mozilla.javascript.drivers.RhinoTest;
import org.monflabs.mozilla.javascript.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es6/generator-method.js")
@LanguageVersion(Context.VERSION_ES6)
public class GeneratorMethodTest extends ScriptTestsBase {}
