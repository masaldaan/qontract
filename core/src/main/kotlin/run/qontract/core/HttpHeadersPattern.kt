package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

data class HttpHeadersPattern(val pattern: Map<String, Pattern> = emptyMap(), val ancestorHeaders: Map<String, Pattern>? = null) {
    fun matches(headers: Map<String, String>, resolver: Resolver): Result {
        val result = headers to resolver.copy(newPatterns = resolver.newPatterns.plus("(number)" to NumericStringPattern)) to
                ::matchEach otherwise
                ::handleError toResult
                ::returnResult

        return when (result) {
            is Result.Failure -> result.breadCrumb("HEADERS")
            else -> result
        }
    }

    private fun matchEach(parameters: Pair<Map<String, String>, Resolver>): MatchingResult<Pair<Map<String, String>, Resolver>> {
        val (headers, resolver) = parameters

        val headersWithRelevantKeys = ancestorHeaders?.let {
            headers.filterKeys { key ->
                val keyWithoutOptionality = withoutOptionality(key)
                it.containsKey(keyWithoutOptionality) || it.containsKey("$keyWithoutOptionality?")
            }
        } ?: headers

        val missingKey = resolver.findMissingKey(pattern, headersWithRelevantKeys.mapValues { StringValue(it.value) } )
        if(missingKey != null) {
            return MatchFailure(Result.Failure("Header $missingKey was missing", null, missingKey))
        }

        this.pattern.forEach { (key, pattern) ->
            val keyWithoutOptionality = withoutOptionality(key)
            val sampleValue = headersWithRelevantKeys[keyWithoutOptionality]

            when {
                sampleValue != null -> try {
                    when (val result = resolver.matchesPattern(keyWithoutOptionality, pattern, attempt(breadCrumb = keyWithoutOptionality) { pattern.parse(sampleValue, resolver) })) {
                        is Result.Failure -> {
                            return MatchFailure(result.breadCrumb(keyWithoutOptionality))
                        }
                    }
                } catch(e: ContractException) {
                    return MatchFailure(e.result())
                }
                !key.endsWith("?") ->
                    return MatchFailure(Result.Failure(message = """Header $key was missing""", breadCrumb = key))
            }
        }

        return MatchSuccess(parameters)
    }

    fun generate(resolver: Resolver): Map<String, String> {
        return attempt(breadCrumb = "HEADERS") {
            pattern.mapValues { (key, pattern) ->
                attempt(breadCrumb = key) {
                    resolver.generate(key, pattern).toStringValue()
                }
            }
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpHeadersPattern> =
        multipleValidKeys(pattern, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { HttpHeadersPattern(it.mapKeys { withoutOptionality(it.key) }) }
}