package com.linkscrub.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCleanerTest {

    private val cleaner = UrlCleaner()

    @Test
    fun test01_simpleUtmParams() {
        val input = "https://example.com/page?utm_source=google&utm_medium=cpc&id=123"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/page?id=123", res.cleanedUrl)
        assertEquals(2, res.removedParams.size)
        assertEquals("utm_source", res.removedParams[0].name)
        assertEquals("google", res.removedParams[0].value)
    }

    @Test
    fun test02_fbclidAndGclid() {
        val input = "https://example.com/?fbclid=XYZ123&gclid=ABC456"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/", res.cleanedUrl)
        assertEquals(2, res.removedParams.size)
    }

    @Test
    fun test03_twitterShareParamS() {
        val input = "https://x.com/user/status/123456789?s=20&t=abcdef"
        val res = cleaner.clean(input)
        assertEquals("https://x.com/user/status/123456789?t=abcdef", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
        assertEquals("s", res.removedParams[0].name)
    }

    @Test
    fun test04_twitterShareParamNonTwitterDomain() {
        val input = "https://example.com/search?s=query&utm_source=twitter"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/search?s=query", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
        assertEquals("utm_source", res.removedParams[0].name)
    }

    @Test
    fun test05_youtubeKeepOnlyParams() {
        val input = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share&si=123456&t=10"
        val res = cleaner.clean(input)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10", res.cleanedUrl)
        val removedNames = res.removedParams.map { it.name }
        assertTrue(removedNames.contains("feature"))
        assertTrue(removedNames.contains("si"))
    }

    @Test
    fun test06_amazonDpPathStrippingAndParams() {
        val input = "https://www.amazon.com/Product-Name/dp/B08N5WRWNW/ref=sr_1_1?crid=123&keywords=test&qid=15000"
        val res = cleaner.clean(input)
        assertEquals("https://www.amazon.com/Product-Name/dp/B08N5WRWNW", res.cleanedUrl)
        assertTrue(res.removedParams.isNotEmpty())
    }

    @Test
    fun test07_noQueryParams() {
        val input = "https://example.com/about"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/about", res.cleanedUrl)
        assertTrue(res.removedParams.isEmpty())
    }

    @Test
    fun test08_preserveFragment() {
        val input = "https://example.com/doc?utm_source=news#section2"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/doc#section2", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test09_encodedValues() {
        val input = "https://example.com/search?q=hello%20world&utm_campaign=summer%20sale"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/search?q=hello%20world", res.cleanedUrl)
        assertEquals("utm_campaign", res.removedParams[0].name)
        assertEquals("summer sale", res.removedParams[0].value)
    }

    @Test
    fun test10_extractedUrlFromText() {
        val input = "Check this link: https://example.com/item?fbclid=12345 and let me know!"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/item", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test11_cleanAllMultipleUrlsInText() {
        val text = "First https://example.com/?utm_source=a second https://x.com/status/1?s=123 end."
        val result = cleaner.cleanAll(text)
        assertEquals("First https://example.com/ second https://x.com/status/1 end.", result.cleanedText)
        assertEquals(2, result.totalParamsRemoved)
        assertEquals(2, result.cleanResults.size)
    }

    @Test
    fun test12_nonUrlText() {
        val text = "Just a simple text message with no links at all."
        val result = cleaner.cleanAll(text)
        assertEquals(text, result.cleanedText)
        assertEquals(0, result.totalParamsRemoved)
        assertTrue(result.cleanResults.isEmpty())
    }

    @Test
    fun test13_malformedUrl() {
        val input = "not_a_valid_url"
        val res = cleaner.clean(input)
        assertEquals(input, res.cleanedUrl)
        assertTrue(res.removedParams.isEmpty())
    }

    @Test
    fun test14_igshidAndIgshParams() {
        val input = "https://www.instagram.com/p/C1234/?igshid=abc1234&igsh=def567"
        val res = cleaner.clean(input)
        assertEquals("https://www.instagram.com/p/C1234/", res.cleanedUrl)
        assertEquals(2, res.removedParams.size)
    }

    @Test
    fun test15_hubspotParams() {
        val input = "https://example.com/?_hsenc=p2234&_hsmi=9876"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/", res.cleanedUrl)
        assertEquals(2, res.removedParams.size)
    }

    @Test
    fun test16_mailchimpParams() {
        val input = "https://example.com/newsletter?mc_eid=123456&mc_cid=78910"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/newsletter", res.cleanedUrl)
        assertEquals(2, res.removedParams.size)
    }

    @Test
    fun test17_refSrcAndRefUrlParams() {
        val input = "https://example.com/article?ref_src=twsrc%5Etfw&ref_url=https%3A%2F%2Ftwitter.com"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/article", res.cleanedUrl)
        assertEquals(2, res.removedParams.size)
    }

    @Test
    fun test18_siParamGlobal() {
        val input = "https://open.spotify.com/track/12345?si=abcdef123456"
        val res = cleaner.clean(input)
        assertEquals("https://open.spotify.com/track/12345", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test19_msclkidAndDclid() {
        val input = "https://example.com/?msclkid=bing123&dclid=double456"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/", res.cleanedUrl)
        assertEquals(2, res.removedParams.size)
    }

    @Test
    fun test20_paramOrderPreserved() {
        val input = "https://example.com/search?b=2&utm_source=test&a=1&c=3"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/search?b=2&a=1&c=3", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test21_urlEndingWithPeriodInText() {
        val text = "Visit https://example.com/page?utm_medium=email."
        val result = cleaner.cleanAll(text)
        assertEquals("Visit https://example.com/page.", result.cleanedText)
        assertEquals(1, result.totalParamsRemoved)
    }

    @Test
    fun test22_urlWithParentheses() {
        val input = "https://en.wikipedia.org/wiki/Main_Page_(article)?utm_source=wiki"
        val res = cleaner.clean(input)
        assertEquals("https://en.wikipedia.org/wiki/Main_Page_(article)", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test23_repeatedQueryParams() {
        val input = "https://example.com/list?tag=kotlin&utm_source=a&tag=android"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/list?tag=kotlin&tag=android", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test24_emptyParamValue() {
        val input = "https://example.com/page?flag&utm_source=b"
        val res = cleaner.clean(input)
        assertEquals("https://example.com/page?flag", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test25_httpProtocol() {
        val input = "http://example.com/page?utm_source=google"
        val res = cleaner.clean(input)
        assertEquals("http://example.com/page", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test26_customRulesJson() {
        val customRules = """
            {
              "globalRules": {
                "removeParams": ["custom_tracker"]
              },
              "domainRules": []
            }
        """.trimIndent()
        val customCleaner = UrlCleaner(customRules)
        val res = customCleaner.clean("https://example.com/?custom_tracker=123&keep=yes")
        assertEquals("https://example.com/?keep=yes", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test27_cleanAllTextWithNoTrackersInUrls() {
        val text = "Check https://example.com/clean and https://google.com/search?q=kotlin"
        val result = cleaner.cleanAll(text)
        assertEquals(text, result.cleanedText)
        assertEquals(0, result.totalParamsRemoved)
    }

    @Test
    fun test28_youtubeShortenerLink() {
        val input = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123&t=30"
        val res = cleaner.clean(input)
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=30", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }

    @Test
    fun test29_extractFirstUrlWhenMultiplePresent() {
        val input = "First https://example.com/?utm_source=a then https://example.com/?utm_medium=b"
        val extracted = UrlCleaner.extractFirstUrl(input)
        assertEquals("https://example.com/?utm_source=a", extracted)
    }

    @Test
    fun test30_urlWithPortNumber() {
        val input = "https://localhost:8080/api/data?utm_source=dev&id=42"
        val res = cleaner.clean(input)
        assertEquals("https://localhost:8080/api/data?id=42", res.cleanedUrl)
        assertEquals(1, res.removedParams.size)
    }
}
