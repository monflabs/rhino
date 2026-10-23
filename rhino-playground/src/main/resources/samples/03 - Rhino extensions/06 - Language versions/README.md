# Language versions

Rhino runs a script in the language version of its `Context`, set with `setLanguageVersion`. This
fork targets `Context.VERSION_ECMASCRIPT`, the **ES2026** choice of the toolbar and the fork's
default. `Context.VERSION_ES6`, the **ES6** choice, keeps the exact behaviour of stock Rhino, bugs
included, so that existing embeddings can upgrade the engine without changing what their scripts
do.

Every behaviour that differs between the two is listed, with an example, in the fork's
`BACKWARD_COMPATIBILITY.md`; the sample shows a few. The syntax stock Rhino did not have
(classes, async functions and generators, private names, modules) is ES2026 only, while the newer
built-ins are there in both. Switch the version and run this sample, or any other, to compare.
