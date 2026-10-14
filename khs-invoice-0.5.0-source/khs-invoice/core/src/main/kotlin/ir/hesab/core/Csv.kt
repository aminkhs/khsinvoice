package ir.hesab.core

object Csv {
    fun cell(value: String): String {
        val safe = if(value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r', '\n')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    fun encode(rows: List<List<String>>) = "\uFEFF" + rows.joinToString("\r\n") { it.joinToString(",", transform = ::cell) } + "\r\n"
}
