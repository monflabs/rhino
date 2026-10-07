module org.monflabs.mozilla.rhino.testutils {
    exports org.monflabs.mozilla.javascript.testutils;

    requires org.monflabs.mozilla.rhino;
    requires transitive org.junit.jupiter.api;
}
