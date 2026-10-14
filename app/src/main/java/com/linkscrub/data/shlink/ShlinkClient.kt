package com.linkscrub.data.shlink

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

@Serializable
data class CreateShortUrlRequest(
    val longUrl: String,
    val customSlug: String? = null,
    val tags: List<String>? = null
)

@Serializable
data class CreateShortUrlResponse(
    val shortUrl: String? = null,
    val shortCode: String? = null
)

@Serializable
data class ShlinkErrorResponse(
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
    val type: String? = null
)

sealed class ShlinkResult<out T> {
    data class Success<out T>(val data: T) : ShlinkResult<T>()
    data class Error(val message: String, val statusCode: Int? = null) : ShlinkResult<Nothing>()
}

class ShlinkClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        const val API_VERSION = "v3"

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }
        }
    }

    suspend fun createShortUrl(
        baseUrl: String,
        apiKey: String,
        longUrl: String,
        customSlug: String? = null,
        tags: List<String>? = null,
        allowCleartext: Boolean = false
    ): ShlinkResult<String> {
        if (baseUrl.isBlank()) {
            return ShlinkResult.Error("Server base URL is not configured.")
        }
        if (apiKey.isBlank()) {
            return ShlinkResult.Error("API key is not configured.")
        }

        var normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
        if (!normalizedBaseUrl.startsWith("http://") && !normalizedBaseUrl.startsWith("https://")) {
            normalizedBaseUrl = "https://$normalizedBaseUrl"
        }

        if (normalizedBaseUrl.startsWith("http://") && !allowCleartext) {
            return ShlinkResult.Error("Cleartext HTTP is disabled in settings.")
        }

        val endpoint = "$normalizedBaseUrl/rest/$API_VERSION/short-urls"

        return try {
            val response = httpClient.post(endpoint) {
                header("X-Api-Key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(CreateShortUrlRequest(
                    longUrl = longUrl,
                    customSlug = customSlug.takeIf { !it.isNullOrEmpty() },
                    tags = tags?.filter { it.isNotBlank() }.takeIf { !it.isNullOrEmpty() }
                ))
            }

            val status = response.status.value
            if (status in 200..299) {
                val resBody = response.body<CreateShortUrlResponse>()
                val shortUrl = resBody.shortUrl
                if (!shortUrl.isNullOrEmpty()) {
                    ShlinkResult.Success(shortUrl)
                } else {
                    ShlinkResult.Error("Invalid response from Shlink server.")
                }
            } else {
                val errorBody = try {
                    response.body<ShlinkErrorResponse>()
                } catch (e: Exception) {
                    null
                }

                val msg = when {
                    status == 401 || status == 403 -> "Invalid API key or unauthorized."
                    status == 400 && errorBody?.type?.contains("INVALID_SLUG", ignoreCase = true) == true -> "Duplicate or invalid custom slug."
                    errorBody?.detail != null -> errorBody.detail
                    errorBody?.title != null -> errorBody.title
                    else -> "Server error (HTTP $status)."
                }
                ShlinkResult.Error(msg, statusCode = status)
            }
        } catch (e: Exception) {
            ShlinkResult.Error(e.localizedMessage ?: "Server unreachable or network error.")
        }
    }
}
