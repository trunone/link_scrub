package com.linkscrub.data.shlink

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShlinkClientTest {

    @Test
    fun testCreateShortUrlSuccess() = runBlocking {
        val mockEngine = MockEngine { request ->
            val apiKey = request.headers["X-Api-Key"]
            if (apiKey == "valid_key") {
                respond(
                    content = """{"shortUrl":"https://s.example.com/xyz123","shortCode":"xyz123"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"title":"Unauthorized","detail":"Invalid API key","status":401}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val client = ShlinkClient(httpClient)
        val result = client.createShortUrl(
            baseUrl = "https://s.example.com",
            apiKey = "valid_key",
            longUrl = "https://example.com/long-page-url",
            customSlug = null,
            tags = listOf("tag1"),
            allowCleartext = false
        )

        assertTrue(result is ShlinkResult.Success)
        assertEquals("https://s.example.com/xyz123", (result as ShlinkResult.Success).data)
    }

    @Test
    fun testCreateShortUrlUnauthorized() = runBlocking {
        val mockEngine = MockEngine { respond("""{"title":"Unauthorized","detail":"Invalid API key","status":401}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json")) }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val client = ShlinkClient(httpClient)
        val result = client.createShortUrl(
            baseUrl = "https://s.example.com",
            apiKey = "wrong_key",
            longUrl = "https://example.com/test"
        )

        assertTrue(result is ShlinkResult.Error)
        assertEquals("Invalid API key or unauthorized.", (result as ShlinkResult.Error).message)
    }

    @Test
    fun testCreateShortUrlCleartextBlocked() = runBlocking {
        val client = ShlinkClient()
        val result = client.createShortUrl(
            baseUrl = "http://s.example.com",
            apiKey = "valid_key",
            longUrl = "https://example.com/test",
            allowCleartext = false
        )

        assertTrue(result is ShlinkResult.Error)
        assertEquals("Cleartext HTTP is disabled in settings.", (result as ShlinkResult.Error).message)
    }
}
