# Interfaces and threads

A script function passed where Java expects an interface with a single method (`Comparator`,
`Runnable`, `Function`, ...) becomes an implementation of that interface. For an interface with
several methods, `new Interface({...})` or `new JavaAdapter(Interface, {...})` take an object
whose properties are the methods.

Such a function may run on another Java thread: Rhino gives that thread a `Context` of its own.
The script's objects are then shared between threads, and `sync(fn, lock)` makes a function
`synchronized` on a lock object, as a Java `synchronized` method is.
