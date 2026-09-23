package com.saubh.deskbuddy.transfer

/** Makes a sender-supplied file name safe to use as a single path segment. */
object FileNames {
    private const val FALLBACK = "file"
    private const val MAX_LENGTH = 200
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    fun sanitize(name: String): String {
        val cleaned = illegal.replace(name, "_").trim().trim('.')
        val bounded = if (cleaned.length > MAX_LENGTH) cleaned.takeLast(MAX_LENGTH) else cleaned
        return bounded.ifEmpty { FALLBACK }
    }

    /** Splits "report.final.pdf" into ("report.final", ".pdf"); no extension → ("name", ""). */
    fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
    }
}
