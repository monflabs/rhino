package org.monflabs.mozilla.javascript;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * A lambda that can provide the source code for a given script.
 *
 * <p>It is expected that this is implemented externally, from Rhino users.
 *
 * @see CompileSpec.Builder#sourceCodeSupplier(SourceCodeSupplier)
 */
@FunctionalInterface
public interface SourceCodeSupplier extends Supplier<String>, Serializable {}
