# importPackage and JavaImporter

`importClass(java.util.concurrent.atomic.AtomicInteger)` defines `AtomicInteger` as a global, and
`importPackage(java.time)` makes every class of the package resolve by its simple name, as a Java
`import` does. Both work in the global scope that Rhino's `ImporterTopLevel` provides, which is
the one the playground and the Rhino shell use.

`new JavaImporter(...)` gathers packages and classes in an object instead, for a `with` block, so
that the names do not leak into the global scope. `with` is not allowed in strict code.
