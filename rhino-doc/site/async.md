# Asynchronous code

This page explains how Monflabs Rhino runs code that does not finish in one go, from generators to promises and
async functions, and how an embedder adds the host side: an event loop, and Java functions such as `setTimeout()`
or `fetch()` that complete later.

The short version: **the engine never starts a thread**. Everything a script does, including every promise
reaction and every resumed `async` function, runs on the thread of its `Context`, at a point the embedder controls.
Asynchrony comes in three layers, and each one only exists if the one below it is used:

| Layer | What runs there | Who runs it | Present |
|---|---|---|---|
| **Suspended frames** | Generators, iterators | The caller, synchronously, at each `next()` | Always |
| **Microtasks** | Promise reactions, the steps of `async` functions and async generators, `FinalizationRegistry` callbacks | `Context.processMicrotasks()`, which every `evaluate` call runs before it returns | Always |
| **Host jobs** | Work that completes later: timers, I/O, `Atomics.waitAsync` | A `HostJobScheduler`, such as `EventLoop` | Only when the embedder sets one |

Generators, promises, `async`/`await`, async generators and `for await` are described with the other language
features in [Features on top of stock Rhino](features.md#async-functions-and-async-iteration). The async syntax is
part of `Context.VERSION_ECMASCRIPT`, the default language version.

## Generators: suspension without a queue

A generator is a function that can stop in the middle and continue later. `yield` saves its frame and returns to
the caller; the next call to `next()` resumes the frame where it stopped. Nothing is queued and nothing is deferred:
the generator runs only while somebody calls `next()`, on the caller's thread.

```js
function* ids() {
  let id = 0;
  while (true) yield ++id;     // suspended here between two next() calls
}
const gen = ids();
gen.next().value;              // 1
gen.next().value;              // 2
```

Both backends implement this: the interpreter keeps the frame as data, and the class compiler turns the generator
into a resumable state machine. Iterators, `for`-`of`, spread and destructuring all work on the same protocol, so a
Java caller can drive a generator too, by calling its `next` method:

```java
Scriptable gen = (Scriptable) cx.evaluateString(scope, "ids()", "ids", 1, null);
Scriptable step = (Scriptable) ScriptableObject.callMethod(cx, gen, "next", new Object[0]);
Object value = step.get("value", step);   // 1
```

Generators are the mechanism, not the source of asynchrony: an `async` function is a generator-like frame whose
resumption is triggered by a promise instead of a `next()` call.

## Promises and the microtask queue

Each `Context` owns a **microtask queue**. A promise does not call its reactions when it settles; it enqueues them,
and they run when the queue is processed. `await` is built on that: an `async` function suspends its frame, and
the reaction of the awaited promise resumes it.

```js
async function total(a, b) {
  const x = await a;          // suspends; resumed by a microtask when a settles
  const y = await b;
  return x + y;               // settles the promise that total() returned
}
```

The queue is processed by `Context.processMicrotasks()`. It runs until the queue is empty, including the microtasks
that running microtasks enqueue. Three things to know as an embedder:

- **Every `evaluate` call processes the queue before it returns** (`evaluateString`, `evaluateReader`,
  `Script.exec`, ...). A script whose promises all settle synchronously, like `Promise.resolve(1).then(...)`, has
  run completely when the call returns.
- **A Java call into a script function does not.** After calling a `Function` from Java, call
  `cx.processMicrotasks()` if the call may have started promise work.
- **The queue belongs to one thread.** It is not thread-safe, like the `Context` itself. Another thread must
  never settle a promise or enqueue a microtask directly: it hands a job to the scheduler, described below.

`cx.enqueueMicrotask(Runnable)` adds a task to the queue from Java, which is all a `queueMicrotask()` global needs.
`suspendMicrotaskProcessing()` and `resumeMicrotaskProcessing()` delay the queue, for a host that runs several
scripts before letting promises settle, as some browsers do. And with `setTrackUnhandledPromiseRejections(true)`,
`getUnhandledPromiseTracker()` lists the rejected promises that nothing handled, which the host should then
`process(...)` regularly (log them, for example).

### What happens when nothing settles the promise

Microtasks only move work that is already ready. If a script awaits something that nothing will ever settle, such
as a timer when no timer exists, the frame stays suspended and the promise stays pending: the `evaluate` call
returns normally, and the script never continues. This is the default, and it is the right one for a purely
synchronous embedder: there is no event loop to run, and no thread to stop.

A script reaching for an operation that needs one fails loudly instead of hanging. Without a scheduler,
`Atomics.waitAsync` throws a `TypeError` when it would have to wait:

```text
TypeError: Atomics.waitAsync needs a HostJobScheduler on the Context to wait
```

A host function such as `setTimeout()` should do the same when it finds no scheduler.

### Modules and top-level `await`

A module graph that uses top-level `await` evaluates asynchronously, as the specification requires. To keep the
Java call synchronous, `Context.evaluateModule` and `ModuleRecord.evaluate` run the microtasks, then return the
namespace or throw the graph's error. That settles every graph that awaits only promises.

A graph that awaits **host work** (a timer, a `fetch`) is still evaluating when the call returns: the rest of its
body runs from the event loop. Run the loop, then call `evaluate` again on the same `ModuleRecord`, which throws
the error if the evaluation failed in the meantime:

```java
ModuleRecord record = cx.loadModule(scope, Module.source("main.mjs", source));
record.link(cx);
record.evaluate(cx);      // runs up to the first await on host work
loop.run(cx);             // the rest of the module
record.evaluate(cx);      // throws if it failed
Scriptable exports = record.getNamespace();
```

## The event loop is optional

Work that completes **later**, after a delay or on another thread, cannot be a microtask: by the time it is ready,
the queue has long been drained. The engine hands such work to a `HostJobScheduler`, set on the context:

```java
cx.setHostJobScheduler(scheduler);   // null, the default, means no event loop
```

The interface has two entry points, both called on the context's thread:

| Method | For | Returns |
|---|---|---|
| `schedule(job, delayMillis)` | A job to run once a delay has passed (a timer) | A `Cancelable` |
| `expect()` | A job that does not exist yet, which some other thread will `post` when its work completes (I/O, a notification) | A `Pending`: `post(job)` from any thread, at most once, or `cancel()` |

`expect()` exists so that the loop knows something is still coming: a loop with an expected job keeps waiting
even when its queues are empty, and stops once nothing is posted, expected or scheduled.

Whatever the implementation, the scheduler guarantees that **the jobs of a context run one at a time, on a thread
where that context is current, and never while one of its scripts is running**. A job is therefore the one place
where host code may touch the script's objects again. A job does two things: it settles what the host operation
started (it calls a callback, resolves or rejects a promise), then it runs `cx.processMicrotasks()`, so that the
reactions it triggered run before the next job.

### `EventLoop`: the ready-made scheduler

`EventLoop` is a simple implementation, run by the context's own thread, with no timer thread:

```java
try (Context cx = Context.enter()) {
    TopLevel scope = cx.initStandardObjects();
    EventLoop loop = new EventLoop();
    cx.setHostJobScheduler(loop);

    cx.evaluateString(scope, source, "main.js", 1, null);   // runs the script and its microtasks
    loop.run(cx);                                            // then the jobs, until none is left
    // or: boolean idle = loop.run(cx, timeoutMillis);
}
```

`run` processes the microtasks, then waits for posted jobs and for timers that fall due, and returns when no job is
posted, expected or scheduled, after the timeout, or when the thread is interrupted. Timers that fall due while a
script is busy with microtasks (polling through promise reactions, for example) run between those microtasks.

A server that already has an executor, or an event loop of its own (Netty, Vert.x, a virtual-thread scheduler...),
implements `HostJobScheduler` over it instead. It must keep the guarantee above: one job at a time per context,
entered on that context, never during a script.

## Writing host functions in Java

The examples below are **pseudo-code**: they show the shape of a host function and where the asynchrony goes, not
a complete implementation (argument checking, `clearTimeout`, headers and response objects are left out). Each
is a `LambdaFunction` defined on the global scope:

```java
scope.defineProperty("setTimeout", new LambdaFunction(scope, "setTimeout", 2, Timers::setTimeout),
        ScriptableObject.DONTENUM);
```

### `setTimeout()`: a job after a delay

A timer is the simplest host job: nothing runs on another thread, the scheduler only has to call back later.

```java
static Object setTimeout(Context cx, VarScope scope, Object thisObj, Object[] args) {
    Callable callback = (Callable) args[0];
    long delay = (long) Context.toNumber(args[1]);

    HostJobScheduler scheduler = cx.getHostJobScheduler();
    if (scheduler == null) {
        throw ScriptRuntime.typeError("setTimeout needs an event loop");   // fail, do not hang
    }

    // 1. Nothing happens now: the job is only registered.
    HostJobScheduler.Cancelable timer = scheduler.schedule(jcx -> {
        // 2. Later, on the context's thread, between two scripts:
        callback.call(jcx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[0]);
        jcx.processMicrotasks();       // 3. let the promises the callback settled react
    }, delay);

    return registerTimer(timer);       // an id, for clearTimeout(id) -> timer.cancel()
}
```

With it, the script side can build the usual helpers on promises:

```js
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function retry(task, attempts) {
  for (let i = 1; ; i++) {
    try { return await task(); }
    catch (e) { if (i === attempts) throw e; await sleep(100 * i); }
  }
}
```

### `fetch()`: a promise settled from another thread

A function that returns a promise follows three steps:

1. **On the context's thread, create a pending promise** and keep its `resolve` and `reject` functions. Ask the
   scheduler to `expect()` a job, so the event loop keeps running until the work is done.
2. **Do the slow work elsewhere**, on an I/O thread, an `HttpClient`, a database driver. That thread must not touch
   any script object: it only computes plain Java values.
3. **Come back through the scheduler.** When the work completes, `post` a job; the job, on the context's thread,
   converts the result to script values, resolves or rejects the promise, and runs the microtasks.

The first step uses only the public API: constructing a `Promise` with an executor written in Java, which captures
the two resolving functions.

```java
record Deferred(Object promise, Callable resolve, Callable reject) {}

static Deferred newPromise(Context cx, VarScope scope) {
    Callable[] fns = new Callable[2];
    Object promise = cx.newObject(scope, "Promise", new Object[] {
        new LambdaFunction(scope, 2, (c, s, t, args) -> {     // the executor, run immediately
            fns[0] = (Callable) args[0];                       // resolve
            fns[1] = (Callable) args[1];                       // reject
            return Undefined.instance;
        })
    });
    return new Deferred(promise, fns[0], fns[1]);
}
```

`fetch` itself then reads like the three steps:

```java
static Object fetch(Context cx, VarScope scope, Object thisObj, Object[] args) {
    String url = Context.toString(args[0]);

    // 1. On the context's thread: a pending promise, and a job announced to the loop.
    Deferred deferred = newPromise(cx, scope);
    HostJobScheduler.Pending pending = cx.getHostJobScheduler().expect();

    // 2. Elsewhere: plain Java, no script object.
    httpClient.sendAsync(request(url), BodyHandlers.ofString())
        .whenComplete((response, failure) ->

            // 3. Back on the context's thread, between two scripts.
            pending.post(jcx -> {
                if (failure == null) {
                    Object value = toResponseObject(jcx, scope, response);    // build script values here
                    deferred.resolve().call(jcx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {value});
                } else {
                    Object error = jcx.newObject(scope, "TypeError", new Object[] {failure.getMessage()});
                    deferred.reject().call(jcx, scope, Undefined.SCRIPTABLE_UNDEFINED, new Object[] {error});
                }
                jcx.processMicrotasks();       // resumes every `await fetch(...)` waiting on it
            }));

    return deferred.promise();                 // returned at once, still pending
}
```

If the request is abandoned before it completes (a script-level `AbortSignal`, a server shutting down), call
`pending.cancel()` instead of posting, so the loop does not wait for it forever.

From the script, it is an ordinary promise, and every promise feature composes with it:

```js
async function main() {
  const page = await fetch('https://example.com/');
  const [a, b] = await Promise.all([fetch(urlA), fetch(urlB)]);   // both requests in flight at once
  for await (const chunk of stream(page)) consume(chunk);          // async generators work the same way
}
main().catch(e => print('failed: ' + e));
```

### The order things run in

With the two functions above and an `EventLoop`, this script:

```js
async function main() {
  print('start');
  await new Promise(r => setTimeout(r, 30));
  print('slept');
  print(await fetch('http://x/1'));
}
main().then(() => print('done'));
print('script end');
```

prints, with a `System.out.println` after each Java call:

```text
start                  main() runs synchronously up to its first await
script end             the rest of the script
evaluate returned      evaluateString has drained the microtasks; main() is suspended on the timer
slept                  loop.run(cx): the timer job resolves the promise, its microtasks resume main()
body of http://x/1     the posted fetch job resolves the second promise
done
loop idle              nothing is posted, expected or scheduled: run() returns
```

The two awaited operations ran on the context's thread, in jobs; only the HTTP exchange ran elsewhere.

## Consuming a promise from Java

The other direction, Java waiting for a script's promise, uses the same queue. Attach the reactions with `then`,
and process the microtasks (or run the event loop, if the promise waits on host work):

```java
Scriptable promise = (Scriptable) ScriptableObject.callMethod(cx, module, "load", new Object[] {name});
ScriptableObject.callMethod(cx, promise, "then", new Object[] {
    new LambdaFunction(scope, 1, (c, s, t, args) -> { onValue(args[0]); return Undefined.instance; }),
    new LambdaFunction(scope, 1, (c, s, t, args) -> { onError(args[0]); return Undefined.instance; })
});
loop.run(cx);     // or cx.processMicrotasks() when no host work is involved
```

To hand the result to other Java code as a `CompletableFuture`, complete the future in those two reactions. Do not
block the context's thread on the future: the promise can only settle while that thread runs the queue.

## Summary

- Generators and iterators are synchronous: a suspended frame, resumed by `next()`.
- Promises, `async` functions and async generators run on the context's **microtask queue**, which every
  `evaluate` call drains before it returns; call `processMicrotasks()` after calling script functions from Java.
- **The event loop is optional.** Without a `HostJobScheduler`, nothing can complete later, and the operations that
  would wait throw a `TypeError`. With one, `schedule` runs timers and `expect`/`post` brings work back from other
  threads. `EventLoop` is the ready-made one.
- A host function that completes later creates its promise on the context's thread, does the work elsewhere with
  plain Java values, and settles the promise in a job, followed by `processMicrotasks()`.
