package run.qontract.core

import run.qontract.core.Result.Failure
import run.qontract.core.Result.Success
import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import java.net.URI

data class HttpRequestPattern(val headersPattern: HttpHeadersPattern = HttpHeadersPattern(), val urlMatcher: URLMatcher? = null, val method: String? = null, val body: Pattern = EmptyStringPattern, val formFieldsPattern: Map<String, Pattern> = emptyMap(), val multiPartFormDataPattern: List<MultiPartFormDataPattern> = emptyList()) {
    fun matches(incomingHttpRequest: HttpRequest, resolver: Resolver, headersResolver: Resolver? = null): Result {
        val result = incomingHttpRequest to resolver to
                ::matchUrl then
                ::matchMethod then
                { (request, defaultResolver) ->
                    matchHeaders(Triple(request, headersResolver, defaultResolver))
                } then
                ::matchFormFields then
                ::matchMultiPartFormData then
                ::matchBody otherwise
                ::handleError toResult
                ::returnResult

        return when(result) {
            is Failure -> result.breadCrumb("REQUEST")
            else -> result
        }
    }

    private fun matchMultiPartFormData(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        if(multiPartFormDataPattern.isEmpty() && httpRequest.multiPartFormData.isEmpty())
            return MatchSuccess(parameters)

        if (multiPartFormDataPattern.isEmpty() && httpRequest.multiPartFormData.isNotEmpty()) {
            return MatchFailure(Failure("The contract expected no multipart data, but the request contained ${httpRequest.multiPartFormData.size} parts.", breadCrumb = "MULTIPART-FORMDATA"))
        }

        val results = multiPartFormDataPattern.map { type ->
            val results = httpRequest.multiPartFormData.map { value ->
                type.matches(value, resolver)
            }

            val result = results.find { it is Success } ?: results.find { it is Failure && it.failureReason != FailureReason.PartNameMisMatch }?.breadCrumb(type.name)
            result ?: when {
                    isOptional(type.name) -> Success()
                    else -> Failure("The part named ${type.name} was not found.").breadCrumb(type.name)
                }
        }

        if (results.any { it !is Success }) {
            val reason = results.filter { it !is Success }.joinToString("\n\n") {
                resultReport(it).prependIndent("  ")
            }

            return MatchFailure(Failure("The multipart data in the request did not match the contract:\n$reason", breadCrumb = "MULTIPART-FORMDATA"))
        }

        val typeKeys = multiPartFormDataPattern.map { withoutOptionality(it.name) }.sorted()
        val valueKeys = httpRequest.multiPartFormData.map { it.name }.sorted()

        val missingInType = valueKeys.filter { it !in typeKeys }
        if(missingInType.isNotEmpty())
            return MatchFailure(Failure("Some parts in the request were missing from the contract, and their names are $missingInType.", breadCrumb = "MULTIPART-FORMDATA"))

        val originalTypeKeys = multiPartFormDataPattern.map { it.name }.sorted()
        val missingInValue = originalTypeKeys.filter { !isOptional(it) }.filter { withoutOptionality(it) !in valueKeys }.joinToString(", ")
        if(missingInValue.isNotEmpty())
            return MatchFailure(Failure("Some parts in the contract were missing from the request, and their names are $missingInValue", breadCrumb = "MULTIPART-FORMDATA"))

        return MatchSuccess(parameters)
    }

    fun matchFormFields(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val keys: List<String> = formFieldsPattern.keys.filter { key -> isOptional(key) && withoutOptionality(key) !in httpRequest.formFields }
        if(keys.isNotEmpty())
            return MatchFailure(Failure(message = "Fields $keys not found", breadCrumb = "FORM-FIELDS"))

        val keyError = resolver.findMissingKey(formFieldsPattern, httpRequest.formFields, ::validateUnexpectedKeys)
        if(keyError != null)
            return MatchFailure(missingKeyToResult(keyError, "form field"))

        val result: Result? = formFieldsPattern
            .filterKeys { key -> withoutOptionality(key) in httpRequest.formFields }
            .map { (key, pattern) -> Triple(withoutOptionality(key), pattern, httpRequest.formFields.getValue(key)) }
            .map { (key, pattern, value) ->
                try {
                    when (val result = resolver.matchesPattern(key, pattern, try { pattern.parse(value, resolver) } catch (e: Throwable) { StringValue(value) } )) {
                        is Failure -> result.breadCrumb(key).breadCrumb("FORM-FIELDS")
                        else -> result
                    }
                } catch(e: ContractException) {
                    e.failure().breadCrumb(key).breadCrumb("FORM-FIELDS")
                } catch(e: Throwable) {
                    mismatchResult(pattern, value).breadCrumb(key).breadCrumb("FORM-FIELDS")
                }
            }
            .firstOrNull { it is Failure }

        return when(result) {
            is Failure -> MatchFailure(result)
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchHeaders(parameters: Triple<HttpRequest, Resolver?, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, headersResolver, defaultResolver) = parameters
        val headers = httpRequest.headers
        when (val result = this.headersPattern.matches(headers, headersResolver ?: defaultResolver)) {
            is Failure -> return MatchFailure(result)
        }
        return MatchSuccess(Pair(httpRequest, defaultResolver))
    }

    private fun matchBody(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val bodyValue = try {
            if (isPatternToken(httpRequest.bodyString)) StringValue(httpRequest.bodyString) else body.parse(httpRequest.bodyString, resolver)
        } catch (e: ContractException) {
            return MatchFailure(e.failure().breadCrumb("BODY"))
        }

        return when (val result = resolver.matchesPattern(null, body, bodyValue)) {
            is Failure -> MatchFailure(result.breadCrumb("BODY"))
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchMethod(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, _) = parameters
        method.let {
            return if (it != httpRequest.method)
                MatchFailure(mismatchResult(method ?: "", httpRequest.method ?: "").breadCrumb("METHOD"))
            else
                MatchSuccess(parameters)
        }
    }

    private fun matchUrl(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        urlMatcher.let {
            val result = urlMatcher!!.matches(URI(httpRequest.path!!),
                    httpRequest.queryParams,
                    resolver)
            return if (result is Failure)
                MatchFailure(result.breadCrumb("URL"))
            else
                MatchSuccess(parameters)
        }
    }

    fun generate(request: HttpRequest, resolver: Resolver): HttpRequestPattern {
        var requestType = HttpRequestPattern()

        return attempt(breadCrumb = "REQUEST") {
            if (method == null) {
                throw missingParam("HTTP method")
            }
            if (urlMatcher == null) {
                throw missingParam("URL path")
            }

            requestType = requestType.copy(method = request.method)

            requestType = attempt(breadCrumb = "URL") {
                val path = request.path ?: ""
                val pathTypes = pathToPattern(path)
                val queryParamTypes = toTypeMap(request.queryParams, urlMatcher.queryPattern, resolver).mapKeys { it.key.removeSuffix("?") }

                requestType.copy(urlMatcher = URLMatcher(queryParamTypes, pathTypes, path))
            }

            requestType = attempt(breadCrumb = "HEADERS") {
                requestType.copy(headersPattern = HttpHeadersPattern(toTypeMap(request.headers, headersPattern.pattern, resolver)))
            }

            requestType = attempt(breadCrumb = "BODY") {
                requestType.copy(body = when(request.body) {
                    is StringValue -> encompassedType(request.bodyString, null, body, resolver)
                    else -> request.body.exactMatchElseType()
                })
            }

            requestType = attempt(breadCrumb = "FORM FIELDS") {
                requestType.copy(formFieldsPattern = toTypeMap(request.formFields, formFieldsPattern, resolver))
            }

            val multiPartFormDataRequestMap = request.multiPartFormData.fold(emptyMap<String, MultiPartFormDataValue>()) { acc, part ->
                acc.plus(part.name to part)
            }

            attempt(breadCrumb = "MULTIPART DATA") {
                requestType.copy(multiPartFormDataPattern = multiPartFormDataPattern.filter {
                    withoutOptionality(it.name) in multiPartFormDataRequestMap
                }.map {
                    val key = withoutOptionality(it.name)
                    multiPartFormDataRequestMap.getValue(key).inferType()
                })
            }
        }
    }

    private fun toTypeMap(values: Map<String, String>, types: Map<String, Pattern>, resolver: Resolver): Map<String, Pattern> {
        return types.filterKeys { withoutOptionality(it) in values }.mapValues {
            val key = withoutOptionality(it.key)
            val type = it.value

            attempt(breadCrumb = key) {
                val valueString = values.getValue(key)
                encompassedType(valueString, key, type, resolver)
            }
        }
    }

    private fun encompassedType(valueString: String, key: String?, type: Pattern, resolver: Resolver): Pattern {
        return when {
            isPatternToken(valueString) -> parsedPattern(valueString, key).let { parsedType ->
                when (val result = type.encompasses(parsedType, resolver, resolver)) {
                    is Success -> parsedType
                    else -> throw ContractException(resultReport(result))
                }
            }
            else -> type.parse(valueString, resolver).exactMatchElseType()
        }
    }

    fun generate(resolver: Resolver): HttpRequest {
        var newRequest = HttpRequest()

        return attempt(breadCrumb = "REQUEST") {
            if (method == null) {
                throw missingParam("HTTP method")
            }
            if (urlMatcher == null) {
                throw missingParam("URL path")
            }
            newRequest = newRequest.updateMethod(method)
            attempt(breadCrumb = "URL") {
                newRequest = newRequest.updatePath(urlMatcher.generatePath(resolver))
                val queryParams = urlMatcher.generateQuery(resolver)
                for (key in queryParams.keys) {
                    newRequest = newRequest.updateQueryParam(key, queryParams[key] ?: "")
                }
            }
            val headers = headersPattern.generate(resolver)

            val body = body
            attempt(breadCrumb = "BODY") {
                body.generate(resolver).let { value ->
                    newRequest = newRequest.updateBody(value)
                    newRequest = newRequest.updateHeader("Content-Type", value.httpContentType)
                }
            }

            newRequest = newRequest.copy(headers = headers)

            val formFieldsValue = attempt(breadCrumb = "FORM FIELDS") { formFieldsPattern.mapValues { (key, pattern) -> attempt(breadCrumb = key) { resolver.generate(key, pattern).toString() } } }
            newRequest = when (formFieldsValue.size) {
                0 -> newRequest
                else -> newRequest.copy(
                        formFields = formFieldsValue,
                        headers = newRequest.headers.plus("Content-Type" to "application/x-www-form-urlencoded"))
            }

            val multipartData = attempt(breadCrumb = "MULTIPART DATA") { multiPartFormDataPattern.mapIndexed { index, multiPartFormDataPattern -> attempt(breadCrumb = "[$index]") { multiPartFormDataPattern.generate(resolver) } } }
            when(multipartData.size) {
                0 -> newRequest
                else -> newRequest.copy(
                        multiPartFormData = multipartData,
                        headers = newRequest.headers.plus("Content-Type" to "multipart/form-data")
                )
            }
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpRequestPattern> {
        return attempt(breadCrumb = "REQUEST") {
            val newURLMatchers = urlMatcher?.newBasedOn(row, resolver) ?: listOf<URLMatcher?>(null)
            val newBodies = attempt(breadCrumb = "BODY") { body.newBasedOn(row, resolver) }
            val newHeadersPattern = headersPattern.newBasedOn(row, resolver)
            val newFormFieldsPatterns = newBasedOn(formFieldsPattern, row, resolver)
            val newFormDataPartLists = newMultiPartBasedOn(multiPartFormDataPattern, row, resolver)

            newURLMatchers.flatMap { newURLMatcher ->
                newBodies.flatMap { newBody ->
                    newHeadersPattern.flatMap { newHeadersPattern ->
                        newFormFieldsPatterns.flatMap { newFormFieldsPattern ->
                            newFormDataPartLists.map { newFormDataPartList ->
                                HttpRequestPattern(
                                        headersPattern = newHeadersPattern,
                                        urlMatcher = newURLMatcher,
                                        method = method,
                                        body = newBody,
                                        formFieldsPattern = newFormFieldsPattern,
                                        multiPartFormDataPattern = newFormDataPartList)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "$method ${urlMatcher.toString()}"
    }
}

fun missingParam(missingValue: String): ContractException {
    return ContractException("$missingValue is missing. Can't generate the contract test.")
}

fun newMultiPartBasedOn(partList: List<MultiPartFormDataPattern>, row: Row, resolver: Resolver): List<List<MultiPartFormDataPattern>> {
    val values = partList.map { part ->
        attempt(breadCrumb = part.name) {
            part.newBasedOn(row, resolver)
        }
    }

    return multiPartListCombinations(values)
}

fun multiPartListCombinations(values: List<List<MultiPartFormDataPattern?>>): List<List<MultiPartFormDataPattern>> {
    if(values.isEmpty())
        return listOf(emptyList())

    val value: List<MultiPartFormDataPattern?> = values.last()
    val subLists = multiPartListCombinations(values.dropLast(1))

    return subLists.flatMap { list ->
        value.map { type ->
            when(type) {
                null -> list
                else -> list.plus(type)
            }
        }
    }
}
