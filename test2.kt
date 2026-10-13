import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.charset.StandardCharsets

fun main() {
    val process = ProcessBuilder("/usr/bin/osascript", "-l", "JavaScript", "-")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    
    val script = """
ObjC.import('Foundation');
var out = $.NSFileHandle.fileHandleWithStandardOutput;
out.writeData($('hello\n' + ' '.repeat(4096)).dataUsingEncoding($.NSUTF8StringEncoding));
""".trimIndent()

    process.outputStream.write(script.toByteArray(StandardCharsets.UTF_8))
    process.outputStream.close()
    
    val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
    println("GOT: " + reader.readLine())
}
