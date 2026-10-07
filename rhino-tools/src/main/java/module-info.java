module org.monflabs.mozilla.rhino.tools {
    requires transitive org.monflabs.mozilla.rhino;
    requires transitive java.desktop;
    requires static org.jline.terminal;
    requires static org.jline.reader;

    exports org.monflabs.mozilla.javascript.tools;
    exports org.monflabs.mozilla.javascript.tools.debugger;
    exports org.monflabs.mozilla.javascript.tools.jsc;
    exports org.monflabs.mozilla.javascript.tools.shell;

    uses org.monflabs.mozilla.javascript.tools.ConsoleProvider;

    provides org.monflabs.mozilla.javascript.tools.ConsoleProvider with
            org.monflabs.mozilla.javascript.tools.shell.BasicConsoleProvider,
            org.monflabs.mozilla.javascript.tools.shell.JLineConsoleProvider;
}
