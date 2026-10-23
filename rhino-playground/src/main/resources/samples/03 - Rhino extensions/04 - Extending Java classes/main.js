// JavaAdapter extends a Java class: the object's functions override its methods
var colors = ['red', 'green', 'blue'];
var collection = new JavaAdapter(java.util.AbstractCollection, {
    iterator: function () {
        var i = 0;
        return new java.util.Iterator({
            hasNext: function () { return i < colors.length; },
            next: function () { return colors[i++]; }
        });
    },
    size: function () { return colors.length; }
});
// the methods inherited from AbstractCollection work on top of the two overridden ones
print(collection, collection.contains('green'), collection.isEmpty());
print(new java.util.TreeSet(collection));

// A class and an interface together: extend Thread, and implement Comparable so that a
// PriorityQueue takes the threads of the highest priority first
function Task(name, priority) {
    var task = new JavaAdapter(java.lang.Thread, java.lang.Comparable, {
        run: function () { print('running', name, 'at priority', task.getPriority()); },
        compareTo: function (other) { return other.getPriority() - task.getPriority(); }
    });
    task.setName(name);
    task.setPriority(priority);
    return task;
}
var tasks = new java.util.PriorityQueue();
tasks.add(Task('backup', 3));
tasks.add(Task('email', 7));
tasks.add(Task('report', 5));
while (!tasks.isEmpty()) {
    var task = tasks.poll();
    task.start();
    task.join();
}
