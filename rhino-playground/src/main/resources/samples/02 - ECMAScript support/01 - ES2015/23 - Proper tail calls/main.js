// Proper tail calls are in the specification (ES2015), but Rhino does not implement them, the
// choice every major JavaScript engine except Safari's made. Compiled, each call takes a JVM
// frame, so a deep enough recursion in tail position overflows the stack; interpreted, calls
// between script functions do not use the JVM stack.
function factorial(n, acc = 1) {
    if (n === 0) {
        return acc
    }
    return factorial(n - 1, n * acc)
}
console.log(factorial(5)); //120

console.log(factorial(10));
console.log(factorial(100));
console.log(factorial(1000));

// Deep enough, and the recursion overflows the stack in the compiled mode: the run ends with a
// java.lang.StackOverflowError, which the script cannot catch. Interpreted, it prints Infinity
console.log(factorial(1000000));
