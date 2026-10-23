// The Java packages are globals: java, javax, org and com directly, any other through Packages.
// A class is a constructor, with its static members as properties.
var list = new java.util.ArrayList();
list.add('one');
list.add('two');
print(list, list.size(), list.get(1));

var StringBuilder = Packages.java.lang.StringBuilder;
print(new StringBuilder('abc').reverse());

// Static fields and methods
var Integer = java.lang.Integer;
print(Integer.MAX_VALUE, Integer.parseInt('42'), Integer.toHexString(255));

// Overloads are chosen from the arguments: a JavaScript number is a double, a boolean a boolean
print(java.lang.String.valueOf(3), java.lang.String.valueOf(true), java.lang.Math.max(2, 7));

// A Java string returned by a method stays a Java object, whose length is a method;
// String() makes a JavaScript string of it
var s = java.lang.String.format('%s weighs %.1f tons', 'A rhino', 2.3);
print(s, typeof s, s.length(), typeof String(s), String(s).length);

// java.time works as from Java
var date = java.time.LocalDate.of(2024, 2, 29);
print(date.plusDays(1), date.getDayOfWeek(), date.isLeapYear());

// Which class is a value?
var map = new java.util.HashMap();
print(map.getClass().getName(), map instanceof java.util.Map, map instanceof java.util.List);

// A Java exception is catchable, and keeps its Java side in javaException
try {
    Integer.parseInt('not a number');
} catch (e) {
    print(e.name, e.javaException.getClass().getSimpleName(), e.javaException.getMessage());
}
