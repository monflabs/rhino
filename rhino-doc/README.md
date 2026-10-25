# Documentation site

The documentation of the fork, a [docsify](https://docsify.js.org) site like the Nashorn one
(`../nashorn/doc/nashorn`), published to GitHub Pages by `.github/workflows/doc.yml` on every push
to `monflabs_enhancements`: https://monflabs.github.io/rhino/

```sh
buildtools/build-doc.sh            # generates the site in rhino-doc/build/site
buildtools/build-doc.sh --serve    # and serves it on http://localhost:8000/ (--serve <port>)
```

- `site/README.md` is the home page; `features.md`, `changes.md` and `performance.md` summarize
  the records of the fork: `CHANGELOG_MONFLABS.md`, `BACKWARD_COMPATIBILITY.md` and
  `OPTIMIZATION.md`. `async.md` explains asynchronous code and the event loop to embedders.
  `validation.md` lists the test harnesses, from `tests/README.md`,
  `benchmarks/README.md` and the JavascriptPerformance project. `site/_sidebar.md` is the navigation.
- `site/playground/index.html` runs the playground (`../rhino-playground`) in the browser with
  [CheerpJ](https://cheerpj.com), on its Java 21 runtime: the generated site has the playground's
  jar next to it, as `rhino-playground.jar`. CheerpJ maps `/app/` to the root of the web server,
  so the page builds the jar's path from its own location, and needs Range requests, which GitHub
  Pages and `build-doc.sh --serve` answer. The page loads CheerpJ from
  `cjrtnc.leaningtech.com/20260907_3546`: keep the loader and `cheerpjInit({ version: 21 })` in
  step.
- Everything else the site uses is in `site/vendor/` (docsify, its search, the dark/light theme of
  the Nashorn site and the Prism languages), so it works offline.
