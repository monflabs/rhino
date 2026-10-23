// A Java array: made through java.lang.reflect.Array, indexed like a JavaScript array
var ints = java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 3);
ints[0] = 10;
ints[2] = 30;
print(ints.length, ints[0], ints[1], ints[2], java.util.Arrays.toString(ints));

// A JavaScript array passed where Java expects an array or a List is converted
var words = java.util.Arrays.asList('kiwi', 'apple', 'fig');
print(words, words.size());
var sorted = new java.util.ArrayList(words);
java.util.Collections.sort(sorted);
print(sorted);

// Iterating over a Java collection: its iterator, or for-of over an Iterable
for (var it = sorted.iterator(); it.hasNext(); ) {
    print(' -', it.next());
}
for (const w of sorted) {
    print(' *', w);
}

// A Java Map: get and put, and its entries
var ages = new java.util.TreeMap();
ages.put('Ann', 34);
ages.put('Bob', 27);
print(ages.get('Ann'), ages.containsKey('Bob'), ages);
for (const e of ages.entrySet()) {
    print(e.getKey(), '->', e.getValue());
}

// Back to JavaScript: Array.from over the Java list, then the JavaScript methods. The elements
// are still Java strings, whose length is a method: String() makes JavaScript strings of them
var lengths = Array.from(sorted).map(w => String(w).length);
print(lengths, Array.isArray(lengths));
