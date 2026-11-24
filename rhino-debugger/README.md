# rhino-debugger

A Chrome DevTools Protocol (CDP) server for the neutral debugger API (`org.monflabs.mozilla.api.debugger`).

```java
ContextFactory factory = new ContextFactory();
Debugger debugger = Debugger.of(factory);          // switches the factory's contexts to interpreted mode
debugger.registerContext(scope, "global");         // optional: lets a client evaluate before any script ran
try (CdpServer.Handle handle =
        CdpServer.open(debugger, InspectOptions.parse("9229", false))) {
    factory.call(cx -> cx.evaluateString(scope, source, "app.js", 1, null));
}
```

Chrome (`chrome://inspect`), VS Code (an "attach" configuration on port 9229) and any other CDP client can attach.
The debugger works on interpreted code only; `Debugger.of` sets that up for the contexts created afterwards.
