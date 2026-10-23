# Proper tail calls

ES2015 requires a call in tail position to reuse the caller's frame, making unbounded tail
recursion safe. Rhino does not implement that, the same choice every major engine but Safari's
made, and how deep a recursion can go depends on the execution mode:

- **Compiled**, each call takes a JVM frame, and a deep enough recursion overflows the stack: the
  run ends with a Java `StackOverflowError`, which the script cannot catch.
- **Interpreted**, a call from a script function to another does not use the JVM stack, so the
  million-deep recursion completes.

Switch the mode in the toolbar and run again to see both.
