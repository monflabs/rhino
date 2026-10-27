# Proper tail calls

ES2015 requires a call in tail position to reuse the caller's frame, making unbounded tail
recursion safe. Rhino does not implement that, the same choice every major engine but Safari's
made, and how deep a recursion can go depends on the execution mode:

- **Compiled**, each call takes a JVM frame, and a deep enough recursion overflows the stack: the
  call throws a `RangeError` ("Maximum call stack size exceeded"), as in other engines, which the
  script can catch.
- **Interpreted**, a call from a script function to another does not use the JVM stack, so the
  50,000-deep recursion completes. The interpreter stops at 100,000 frames, with the same
  `RangeError`, unless the embedder sets another limit (`Context.setMaximumInterpreterStackDepth`).

Switch the mode in the toolbar and run again to see both.
