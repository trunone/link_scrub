package com.linkscrub.cleaner.model

import kotlinx.serialization.Serializable

@Serializable
data class RulesConfig(
    val globalRules: GlobalRuleConfig = GlobalRuleConfig(),
    val domainRules: List<DomainRuleConfig> = emptyList()
)

@Serializable
data class GlobalRuleConfig(
    val removeParams: List<String> = emptyList()
)

@Serializable
data class DomainRuleConfig(
    val domains: List<String> = emptyList(),
    val removeParams: List<String> = emptyList(),
    val keepOnlyParams: List<String>? = null,
    val stripPathAfterPattern: String? = null,
    val removeAllParams: Boolean = false
)

data class RemovedParam(
    val name: String,
    val value: String
)

data class CleanResult(
    val originalUrl: String,
    val cleanedUrl: String,
    val removedParams: List<RemovedParam>,
    val matchedRule: String?
)

data class CleanAllResult(
    val originalText: String,
    val cleanedText: String,
    val totalParamsRemoved: Int,
    val cleanResults: List<CleanResult>
)
