# Extending Java classes

`new JavaAdapter(javaClass, interface..., object)` creates an instance of a new Java class that
extends `javaClass`, implements the interfaces, and whose methods are the functions of the
object: a method the object does not define keeps the inherited behaviour. `AbstractCollection`
with only `iterator` and `size` overridden is a full `Collection`.

The superclass must be open to Rhino: a class of the JDK with protected fields, such as
`java.util.AbstractList`, cannot be extended unless its package is opened to the engine
(`--add-opens java.base/java.util=ALL-UNNAMED`).

Rhino generates the adapter class with its class-file writer and loads it in the JVM, in the
compiled mode as in the interpreted one.
