# Modules

The ES2015 module system - `import`/`export`, default and named exports, namespace imports,
**live bindings**, one evaluation per realm, module scope, cycles - is implemented in full and
held to the module slice of the conformance suite.

`main.js` here **is a module**: `import` and `export` are reserved words, so a source that parses
as a module runs as one - no setup, no wrapper. In the playground, an `import`'s specifier
resolves to the sample's own tabs (the playground registers a module loader over them), so
`./app.js` and `./counter.js` are the files next to this one; watch the live bindings move as
`bump()` and `increment()` run, and the default export arrive from `counter.js`.

Where imports come from is the engine's **module-loading chain**, the `ModuleLoader`s set on the
`ContextFactory` or the `Context`: files under a directory (`PathModuleLoader`), class-path
resources (`ResourceModuleLoader`), modules whose exports are Java values (`JavaModuleLoader`),
or a loader of your own. Modules are a feature of the ES2026 version: under ES6, this sample
fails.
