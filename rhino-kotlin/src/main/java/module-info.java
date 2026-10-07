import org.monflabs.mozilla.javascript.NullabilityDetector;
import org.monflabs.mozilla.kotlin.KotlinNullabilityDetector;

module org.monflabs.mozilla.rhino.kotlin {
    requires kotlin.metadata.jvm;
    requires kotlin.stdlib;
    requires org.monflabs.mozilla.rhino;

    exports org.monflabs.mozilla.kotlin;

    provides NullabilityDetector with
            KotlinNullabilityDetector;
}
