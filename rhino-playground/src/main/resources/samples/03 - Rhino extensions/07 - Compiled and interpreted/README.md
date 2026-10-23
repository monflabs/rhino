# Compiled and interpreted

Rhino compiles a script to JVM classes by default: after a warm-up, the JIT makes it fast. It can
also run the script in its interpreter (`cx.setInterpretedMode(true)`), which starts faster, needs
no class generation, does not use the JVM stack for calls between script functions, and is what
Rhino's debugger and continuations require.

Both modes run the same language: the playground's tests run every sample in each.
