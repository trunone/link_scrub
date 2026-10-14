package com.linkscrub.cleaner

import com.linkscrub.cleaner.model.CleanAllResult
import com.linkscrub.cleaner.model.CleanResult
import com.linkscrub.cleaner.model.RemovedParam
import com.linkscrub.cleaner.model.RulesConfig
import kotlinx.serialization.json.Json
import java.net.URI

class UrlCleaner(rulesJsonString: String? = null) {

    private val rulesConfig: RulesConfig

    init {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val jsonContent = rulesJsonString ?: javaClass.classLoader.getResourceAsStream("rules.json")?.use { stream ->
            stream.bufferedReader().readText()
        } ?: "{}"
        rulesConfig = json.decodeFromString(RulesConfig.serializer(), jsonContent)
    }

    companion object {
        private val URL_REGEX = Regex(
            "https?://[a-zA-Z0-9\\-\\._~:%!$&'()*+,;=]+(?::\\d+)?(?:/[a-zA-Z0-9\\-\\._~:%!$&'()*+,;=]*)*(?:\\?[a-zA-Z0-9\\-\\._~:%!$&'()*+,;=/?#@]*)?(?:#[a-zA-Z0-9\\-\\._~:%!$&'()*+,;=/?@]*)?",
            RegexOption.IGNORE_CASE
        )

        fun extractFirstUrl(text: String): String? {
            val trimmed = text.trim()
            val match = URL_REGEX.find(trimmed) ?: return null
            var url = match.value
            while (url.isNotEmpty() && url.last() in trailingPunctuationToStrip) {
                if (url.last() == ')') {
                    val openCount = url.count { it == '(' }
                    val closeCount = url.count { it == ')' }
                    if (closeCount > openCount) {
                        url = url.substring(0, url.length - 1)
                        continue
                    } else {
                        break
                    }
                } else {
                    url = url.substring(0, url.length - 1)
                }
            }
            return if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else null
        }

        private val trailingPunctuationToStrip = setOf('.', ',', ';', '!', '?', ')', ']')
    }

    fun clean(input: String): CleanResult {
        val extractedUrl = extractFirstUrl(input) ?: return CleanResult(
            originalUrl = input,
            cleanedUrl = input,
            removedParams = emptyList(),
            matchedRule = null
        )

        return cleanSingleUrl(extractedUrl)
    }

    fun cleanAll(text: String): CleanAllResult {
        val matches = URL_REGEX.findAll(text).toList()
        if (matches.isEmpty()) {
            return CleanAllResult(
                originalText = text,
                cleanedText = text,
                totalParamsRemoved = 0,
                cleanResults = emptyList()
            )
        }

        val sb = java.lang.StringBuilder()
        var lastIdx = 0
        val results = mutableListOf<CleanResult>()
        var totalRemoved = 0

        for (match in matches) {
            var rawUrl = match.value
            var trailingPunct = ""
            while (rawUrl.isNotEmpty() && rawUrl.last() in trailingPunctuationToStrip) {
                if (rawUrl.last() == ')') {
                    val openCount = rawUrl.count { it == '(' }
                    val closeCount = rawUrl.count { it == ')' }
                    if (closeCount > openCount) {
                        trailingPunct = rawUrl.last() + trailingPunct
                        rawUrl = rawUrl.substring(0, rawUrl.length - 1)
                    } else {
                        break
                    }
                } else {
                    trailingPunct = rawUrl.last() + trailingPunct
                    rawUrl = rawUrl.substring(0, rawUrl.length - 1)
                }
            }

            if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
                sb.append(text.substring(lastIdx, match.range.last + 1))
                lastIdx = match.range.last + 1
                continue
            }

            sb.append(text.substring(lastIdx, match.range.first))
            val cleanResult = cleanSingleUrl(rawUrl)
            sb.append(cleanResult.cleanedUrl)
            sb.append(trailingPunct)
            lastIdx = match.range.last + 1

            results.add(cleanResult)
            totalRemoved += cleanResult.removedParams.size
        }

        sb.append(text.substring(lastIdx))

        return CleanAllResult(
            originalText = text,
            cleanedText = sb.toString(),
            totalParamsRemoved = totalRemoved,
            cleanResults = results
        )
    }

    private fun cleanSingleUrl(urlStr: String): CleanResult {
        val uri = try {
            URI(urlStr)
        } catch (e: Exception) {
            return CleanResult(
                originalUrl = urlStr,
                cleanedUrl = urlStr,
                removedParams = emptyList(),
                matchedRule = null
            )
        }

        val host = uri.host?.lowercase() ?: return CleanResult(
            originalUrl = urlStr,
            cleanedUrl = urlStr,
            removedParams = emptyList(),
            matchedRule = null
        )

        var currentPath = uri.rawPath ?: ""
        val matchedRuleNames = mutableListOf<String>()

        val matchingDomainRules = rulesConfig.domainRules.filter { domainRule ->
            domainRule.domains.any { domain ->
                host == domain.lowercase() || host.endsWith("." + domain.lowercase())
            }
        }

        for (domainRule in matchingDomainRules) {
            val patternStr = domainRule.stripPathAfterPattern
            if (!patternStr.isNullOrEmpty()) {
                val regex = Regex(patternStr)
                val match = regex.find(currentPath)
                if (match != null) {
                    currentPath = match.value
                    matchedRuleNames.add("path_strip:${domainRule.domains.joinToString(",")}")
                }
            }
        }

        val rawQuery = uri.rawQuery
        val removedParams = mutableListOf<RemovedParam>()
        val keptQueryParams = mutableListOf<Pair<String, String>>()

        if (!rawQuery.isNullOrEmpty()) {
            val queryPairs = parseRawQuery(rawQuery)

            for ((paramName, paramVal) in queryPairs) {
                var shouldRemove = false
                var ruleReason = ""

                if (matchingDomainRules.any { it.removeAllParams }) {
                    shouldRemove = true
                    ruleReason = "domain:removeAllParams"
                }

                if (!shouldRemove) {
                    val keepOnlyRule = matchingDomainRules.firstOrNull { it.keepOnlyParams != null }
                    if (keepOnlyRule != null) {
                        val allowed = keepOnlyRule.keepOnlyParams ?: emptyList()
                        if (paramName !in allowed) {
                            shouldRemove = true
                            ruleReason = "domain:keepOnlyParams"
                        }
                    }
                }

                if (!shouldRemove) {
                    for (domainRule in matchingDomainRules) {
                        if (domainRule.removeParams.any { matchesParamPattern(paramName, it) }) {
                            shouldRemove = true
                            ruleReason = "domain:removeParams"
                            break
                        }
                    }
                }

                if (!shouldRemove) {
                    if (rulesConfig.globalRules.removeParams.any { matchesParamPattern(paramName, it) }) {
                        shouldRemove = true
                        ruleReason = "global:removeParams"
                    }
                }

                if (shouldRemove) {
                    removedParams.add(RemovedParam(paramName, decodeQueryValue(paramVal)))
                    if (!matchedRuleNames.contains(ruleReason)) {
                        matchedRuleNames.add(ruleReason)
                    }
                } else {
                    keptQueryParams.add(paramName to paramVal)
                }
            }
        }

        val newQueryStr = if (keptQueryParams.isNotEmpty()) {
            "?" + keptQueryParams.joinToString("&") { (k, v) ->
                if (v.isEmpty()) k else "$k=$v"
            }
        } else {
            ""
        }

        val rawFragment = if (uri.rawFragment != null) "#" + uri.rawFragment else ""
        val scheme = if (uri.scheme != null) uri.scheme + "://" else ""
        val authority = uri.rawAuthority ?: ""

        val newUrl = "$scheme$authority$currentPath$newQueryStr$rawFragment"

        return CleanResult(
            originalUrl = urlStr,
            cleanedUrl = newUrl,
            removedParams = removedParams,
            matchedRule = if (matchedRuleNames.isNotEmpty()) matchedRuleNames.joinToString("; ") else null
        )
    }

    private fun parseRawQuery(rawQuery: String): List<Pair<String, String>> {
        if (rawQuery.isEmpty()) return emptyList()
        val parts = rawQuery.split("&")
        val result = mutableListOf<Pair<String, String>>()
        for (part in parts) {
            if (part.isEmpty()) continue
            val eqIdx = part.indexOf('=')
            if (eqIdx != -1) {
                val k = part.substring(0, eqIdx)
                val v = part.substring(eqIdx + 1)
                result.add(k to v)
            } else {
                result.add(part to "")
            }
        }
        return result
    }

    private fun decodeQueryValue(value: String): String {
        return try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }
    }

    private fun matchesParamPattern(paramName: String, pattern: String): Boolean {
        if (pattern.endsWith("*")) {
            val prefix = pattern.dropLast(1)
            return paramName.startsWith(prefix, ignoreCase = true)
        }
        return paramName.equals(pattern, ignoreCase = true)
    }
}
