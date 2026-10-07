package org.monflabs.mozilla.javascript.testutils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class is used to locate the "testsrc" directory and other testing-related resources in the
 * different test environments where Rhino runs.
 */
public class TestSource {
    private final Resolver resolver;

    private static final TestSource SELF = new TestSource();

    private TestSource() {
        resolver = new NativeResolver();
    }

    /**
     * Return the actual location of the specified file, or throw an AssertionError if the file
     * cannot be found. This method must be used so that file lookup is portable across platforms
     * and build systems.
     */
    public static String resolve(String path) {
        return SELF.resolver.resolve(path);
    }

    /**
     * Return the parent directory of the specified file, or throw an AssertionError if the file
     * cannot be found. Callers should use this method by resolving a well-known file, and then this
     * method will return the directory that contains the file. The directory may then be safely
     * traversed to find other files that are known to resolve there.
     */
    public static String resolveDirectory(String path) {
        return Path.of(SELF.resolver.resolve(path)).getParent().toString();
    }

    /**
     * Return the prefix that should be added to methods like "load" so that they can put this
     * before a file name like "testsrc/assert.js" and end up in the right place.
     */
    public static String getPrefix() {
        return SELF.resolver.getPrefix();
    }

    private abstract static class Resolver {
        abstract String resolve(String path);

        abstract String getPrefix();
    }

    private static class NativeResolver extends Resolver {
        private final String prefix;

        NativeResolver() {
            var testSrc = Path.of("./testsrc");
            if (Files.isDirectory(testSrc)) {
                prefix = null;
                return;
            }
            testSrc = Path.of("./tests/testsrc");
            if (Files.isDirectory(testSrc)) {
                prefix = "./tests";
                return;
            }
            testSrc = Path.of("../tests/testsrc");
            if (Files.isDirectory(testSrc)) {
                prefix = "../tests";
                return;
            }
            prefix = "NOTFOUND";
        }

        @Override
        String resolve(String path) {
            if (prefix == null) {
                return path;
            }
            return prefix + '/' + path;
        }

        @Override
        String getPrefix() {
            return prefix;
        }
    }
}
