# Java arrays and collections

Java arrays are indexed and have a `length` as in JavaScript; `java.lang.reflect.Array` makes new
ones of any element type. A JavaScript array passed to a Java method that takes an array or a
`List` is converted on the way.

Java collections keep their Java methods, and a Java `Iterable` works with `for-of`, so a list, a
set or the entries of a map iterate as in a script; `Array.from` turns one into a JavaScript
array when the array methods are wanted. The values taken out of a Java collection stay Java
objects (a `java.lang.String` has a `length()` method, not a `length` property) until
`String(value)` or `Number(value)` converts them.
