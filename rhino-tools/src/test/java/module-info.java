module org.monflabs.mozilla.rhino.tools.test {
    requires org.monflabs.mozilla.rhino;
    requires org.monflabs.mozilla.rhino.testutils;
    requires org.monflabs.mozilla.rhino.tools;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;
    requires org.jline.terminal;

    exports org.monflabs.mozilla.javascript.tools.tests;
}
