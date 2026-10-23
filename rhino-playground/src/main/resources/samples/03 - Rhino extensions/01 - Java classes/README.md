# Java classes

Rhino's LiveConnect makes the JVM's classes part of the script: `java`, `javax`, `org` and `com`
are global packages, and `Packages` reaches every other one (`Packages.my.company.Tool`). A class
is a constructor whose static fields and methods are its properties, instances expose their
public methods and bean properties, and overloaded methods are chosen from the types of the
arguments. The values that Java methods return stay Java objects, a `java.lang.String` included:
`String(value)` converts one to a JavaScript string.

A Java exception thrown into the script is an ordinary JavaScript error that `catch` sees; its
`javaException` property is the Java exception itself.

An embedder can restrict which classes a script sees with a `ClassShutter` on the `Context`; the
playground does not.
