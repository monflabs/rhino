# Rhino playground

A desktop application to try the engine: a library of samples, an editor, a console, and the
language version and the execution mode to choose. It follows the Nashorn playground
(`../nashorn/playground`). Not published.

```sh
./gradlew :rhino-playground:run      # or, after ./gradlew :rhino-playground:shadowJar:
java -jar rhino-playground/build/libs/rhino-playground-all.jar   # --dark or --light to force the theme
```

The same jar runs in the browser, on the Java 21 runtime of [CheerpJ](https://cheerpj.com): the
"Playground in the browser" page of the documentation site (`../rhino-doc`) starts its
`cheerpj.PlaygroundCheerpJ` entry point, the playground undecorated and maximized to fill the
page, in the dark or light mode of the site, and interpreted: the call sites of the compiled
classes (`invokedynamic`, linked by Dynalink) fail in CheerpJ with "uninitialized call site", so the
mode choice is disabled there.

## The window

- **Samples** on the left, from `src/main/resources/samples`: a folder holding a
  `main.js` is a sample, any other folder a category; the number in front of a folder name gives
  the order. A sample may have a `README.md`, shown under the editor, and other files, shown in
  tabs: `import './x.js'` resolves to them, and `snippet.text('data.json')` reads them. The
  **Scratchpad** at the top is your own code, kept in `~/.rhino-playground/scratch.js`.
- **Run** (⌘/Ctrl-Enter) and **Stop** (Esc). Every run gets a new `Context` and a new global
  (`ImporterTopLevel`, with `print`, `console`, `sync`, `setTimeout`/`setInterval` and their
  `clear` functions). Stop works through the instruction observer of the `Context`, and
  interrupts a script that waits on a timer or in Java.
- **Version**: ES2026 (`Context.VERSION_ECMASCRIPT`) or ES6 (`Context.VERSION_ES6`, the
  behaviour of stock Rhino).
- **Compiled** or **Interpreted**: Rhino's class compiler or its interpreter.
- **Auto-run** runs again half a second after an edit.

A source with `import` or `export` statements, or a top-level `await`, runs as a module (ES2026
only).

## Samples

- **Getting started** and **ECMAScript support** (ES2015 to ES2026, and Annex B) are the samples
  of the Nashorn playground, adapted where Rhino differs.
- **Rhino extensions**: Java classes, arrays and collections, interfaces and threads,
  `JavaAdapter`, `importPackage` and `JavaImporter`, the language versions, and the two execution
  modes.

`SampleRunTest` runs every sample compiled and interpreted, so the library cannot drift from what
the engine supports.
