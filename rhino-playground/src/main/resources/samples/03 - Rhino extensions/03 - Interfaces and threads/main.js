// A function where Java expects an interface with one method: Rhino implements the interface
var names = new java.util.ArrayList(java.util.Arrays.asList('Zoe', 'adam', 'Bea'));
java.util.Collections.sort(names, function (a, b) {
    return a.toLowerCase().localeCompare(b.toLowerCase());
});
print(names);

// An object whose properties are the methods, for an interface with several
var i = 0;
var iterator = new java.util.Iterator({
    hasNext: function () { return i < 3; },
    next: function () { return 'item ' + i++; }
});
while (iterator.hasNext()) {
    print(iterator.next());
}

// JavaAdapter: the same, naming the interfaces
var runnable = new JavaAdapter(java.lang.Runnable, {
    run: function () { print('run by', java.lang.Thread.currentThread().getName()); }
});
var thread = new java.lang.Thread(runnable, 'worker-1');
thread.start();
thread.join();

// Threads share the script's objects: sync() makes a function synchronized, like a Java
// synchronized method, on its this object (or on the object given)
var counter = { n: 0 };
var increment = sync(function () { this.n++; }, counter);
var threads = [];
for (var t = 0; t < 4; t++) {
    var th = new java.lang.Thread(function () {
        for (var i = 0; i < 1000; i++) {
            increment.call(counter);
        }
    });
    th.start();
    threads.push(th);
}
threads.forEach(th => th.join());
print('counter:', counter.n);
