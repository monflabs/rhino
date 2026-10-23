# Legacy built-ins (Annex B)

Annex B is the part of the specification that keeps the web's legacy: `escape`, `String.prototype.substr` and the HTML methods, `__proto__`, `Date.prototype.getYear`, `RegExp.prototype.compile`, and `<!--` as a comment.

Rhino implements all of it, in every language version: unlike Nashorn, it has no option to turn
Annex B off.
