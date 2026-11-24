# Debugging scripts

Monflabs Rhino has a debugger with four parts. Each one builds on the one before.

| Part | Module | What it is |
|---|---|---|
| **Neutral API** | `rhino` (`org.monflabs.mozilla.api.debugger`) | `Debugger`, `PausedEvent`, `Breakpoint`, `DebugFrame`... The same API as Nashorn's, so a front end works with either engine. Lines are zero-based. |
| **CDP server** | `rhino-debugger` | The Chrome DevTools Protocol over a WebSocket or in memory (`Debugger` and `Runtime` domains). |
| **Swing debugger** | `rhino-debugger-ui` | A `DebuggerPanel` for your own application: source, breakpoints, call stack, scopes, console. |
| **Contract tests** | `rhino-debugger-testkit` | `DebuggerContractTest`: the tests any implementation of the API has to pass. |

The debugger works on **interpreted code only**: `Debugger.of(factory)` makes the contexts of that factory interpreted.
It needs no language version; it is a host choice, not a behavior change.

## Attach a debugger

```java
ContextFactory factory = new ContextFactory();
Debugger debugger = Debugger.of(factory);          // before any Context is created
TopLevel scope = factory.call(Context::initStandardObjects);
debugger.registerContext(scope, "global");         // lets a client evaluate before a script runs

try (CdpServer.Handle handle =
        CdpServer.open(debugger, InspectOptions.parse("127.0.0.1:9229", false))) {
    debugger.pauseOnStart();                       // wait at the first statement
    factory.call(cx -> cx.evaluateString(scope, source, "app.js", 1, null));
}
```

- Chrome (`chrome://inspect`, Configure, `127.0.0.1:9229`), VS Code (an attach configuration) and any CDP client can attach.
- A script is known by the source name given to the engine (`app.js` above); a breakpoint by url matches that name.
  Rhino does not read `//# sourceURL`.
- A host `console` is reported to the debugger by calling `debugger.consoleCalled(type, args)`.
- A `Debugger` belongs to a `ContextFactory`: create it before the contexts you want to debug.
- Pausing, stepping, conditional breakpoints, pause on exceptions, evaluation in a frame and terminating a paused script
  are all in the API; `Runtime.terminateExecution` stops a script that is running.

## Debug in a window of your application

```java
DebuggerPanel panel = new DebuggerPanel(new Font(Font.MONOSPACED, Font.PLAIN, 13), false);
InProcessCdpServer.Handle handle = InProcessCdpServer.open(debugger, InspectOptions.parse("", false));
panel.attach(handle.clientChannel());              // in memory: no socket
```

Ask for `debugger.pauseOnStart()` and start the script once the panel is connected (`panel.onConnectionChange`).

## In the playground

`./gradlew :rhino-playground:run`. **Debug** runs the script in the interpreter under the debugger, paused at its
first statement, in a window attached in memory. **External debugger** does the same and serves CDP on a port (9229 by
default) for Chrome DevTools or an IDE. **Stop** also ends a script paused in the debugger.

## Implementing the API for another runtime

Depend on `rhino-debugger-testkit` in the test scope, extend `DebuggerContractTest` and implement `createFixture()`:
the debugger attached to your engine, and a way to run a script under a name. The CDP server and the Swing debugger work
with any implementation of `Debugger`.
