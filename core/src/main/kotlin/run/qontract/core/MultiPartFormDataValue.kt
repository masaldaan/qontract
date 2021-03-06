package run.qontract.core

import run.qontract.core.value.Value

sealed class MultiPartFormDataValue(open val name: String) {
    abstract fun inferType(): MultiPartFormDataPattern
    abstract fun toDisplayableValue(): String
}

data class MultiPartContentValue(override val name: String, val content: Value, val boundary: String = "#####") : MultiPartFormDataValue(name) {
    override fun inferType(): MultiPartFormDataPattern {
        return MultiPartContentPattern(name, content.exactMatchElseType())
    }

    override fun toDisplayableValue(): String = """
--$boundary
Content-Disposition: form-data; name="$name"
Content-Type: ${content.httpContentType}

$content
""".trim()
}

data class MultiPartFileValue(override val name: String, val filename: String, val contentType: String? = null, val contentEncoding: String? = null, val content: String? = null, val boundary: String = "#####") : MultiPartFormDataValue(name) {
    override fun inferType(): MultiPartFormDataPattern {
        return MultiPartFilePattern(name, filename, contentType, contentEncoding)
    }

    override fun toDisplayableValue(): String {
        val headers = mapOf (
                "Content-Disposition" to """form-data; name="$name"; filename="$filename"""",
                "Content-Type" to (contentType ?: ""),
                "Content-Encoding" to (contentEncoding ?: "")
        ).filter { it.value.isNotBlank() }

        val headerString = headers.entries.joinToString {
            "${it.key}: ${it.value}"
        }

        return """
--$boundary
$headerString

(File content not shown here.  Please examine the file ${filename.removePrefix("@")})
""".trim()
    }
}
