# rhino-debugger-testkit

The contract of the neutral debugger API (`org.monflabs.mozilla.api.debugger`), as JUnit 5 tests, for
any debugger that implements it: Rhino's own, or the one of another runtime.

Depend on this module from the test scope, extend `DebuggerContractTest` and implement
`createFixture()`: the debugger attached to your engine, and a way to run a script under a name.

```java
class MyDebuggerContractTest extends DebuggerContractTest {
    @Override
    protected Fixture createFixture() { /* engine + Debugger, one per test */ }
}
```

`RhinoDebuggerContractTest` (in this module's tests) is the example. The contract covers breakpoints
(by script name and line, conditional, active or not), pausing at the start, stepping, pausing on an
uncaught exception, terminating a paused script, the script list, the statement trace and the scopes
of a paused frame. Lines are zero-based.
