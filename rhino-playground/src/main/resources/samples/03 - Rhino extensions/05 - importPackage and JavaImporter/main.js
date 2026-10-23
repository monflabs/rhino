// importClass and importPackage make Java names globals of the script
importClass(java.util.concurrent.atomic.AtomicInteger);
importPackage(java.time);

var hits = new AtomicInteger(41);
print(hits.incrementAndGet(), Duration.ofMinutes(90), Month.of(3));

// JavaImporter keeps the names in an object instead, to use with `with`
var io = new JavaImporter(java.io, java.nio.charset);
with (io) {
    var out = new ByteArrayOutputStream();
    var writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    writer.print('written through java.io');
    writer.close();
    print(out.toString('UTF-8'));
}
print(typeof io.File, typeof File);
