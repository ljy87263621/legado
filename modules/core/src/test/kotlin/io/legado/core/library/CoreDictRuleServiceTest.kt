package io.legado.core.library

import io.legado.core.source.CoreDictRuleService
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.CoreHttpRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDictRuleServiceTest {

    @Test
    fun searchReplacesKeyAndReturnsTheRawResponseWhenShowRuleIsEmpty() {
        val requestedUrls = mutableListOf<String>()
        val client = recordingClient(requestedUrls) { url ->
            CoreHttpResponse(url, "definition for the word")
        }
        val rule = CoreDictRule(
            name = "示例词典",
            urlRule = "https://dict.example/search?q={{key}}"
        )

        val result = CoreDictRuleService(InMemoryCoreLibrary(), client).search(rule, "你好 世界")

        assertEquals("definition for the word", result)
        assertEquals(
            listOf("https://dict.example/search?q=%E4%BD%A0%E5%A5%BD%20%E4%B8%96%E7%95%8C"),
            requestedUrls
        )
    }

    @Test
    fun searchSendsUrlOptionsAsAnHttpRequest() {
        var receivedRequest: CoreHttpRequest? = null
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                error("legacy get must not be used")

            override fun request(request: CoreHttpRequest): CoreHttpResponse {
                receivedRequest = request
                return CoreHttpResponse(request.url, "definition")
            }
        }
        val rule = CoreDictRule(
            name = "POST词典",
            urlRule = "https://dict.example/lookup?q={{key}}," +
                "{\"method\":\"POST\",\"body\":\"query=test\",\"headers\":{\"X-Token\":\"url-token\"}}"
        )

        val result = CoreDictRuleService(InMemoryCoreLibrary(), client).search(rule, "你好 世界")

        assertEquals("definition", result)
        assertEquals("https://dict.example/lookup?q=%E4%BD%A0%E5%A5%BD%20%E4%B8%96%E7%95%8C", receivedRequest?.url)
        assertEquals("POST", receivedRequest?.method)
        assertEquals("query=test", receivedRequest?.body)
        assertEquals("url-token", receivedRequest?.headers?.get("X-Token"))
    }

    @Test
    fun searchExtractsCssTextAndJsonPathFromTheResponse() {
        val cssRule = CoreDictRule(
            name = "CSS词典",
            urlRule = "https://dict.example/css?key={{key}}",
            showRule = "@CSS:.meaning@text"
        )
        val jsonRule = CoreDictRule(
            name = "JSON词典",
            urlRule = "https://dict.example/json?key={{key}}",
            showRule = "@Json:$.data.meaning"
        )
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = when {
                url.contains("/css") -> CoreHttpResponse(
                    url,
                    "<main><p class=\"meaning\">CSS 释义</p></main>"
                )
                else -> CoreHttpResponse(url, "{\"data\":{\"meaning\":\"JSON 释义\"}}")
            }
        }
        val service = CoreDictRuleService(InMemoryCoreLibrary(), client)

        assertEquals("CSS 释义", service.search(cssRule, "词"))
        assertEquals("JSON 释义", service.search(jsonRule, "词"))
    }

    @Test
    fun searchExtractsAllCssMatchesAndJoinsTheirText() {
        val rule = CoreDictRule(
            name = "多结果词典",
            urlRule = "https://dict.example/multiple?key={{key}}",
            showRule = "@CSS:.meaning@text"
        )
        val client = recordingClient(mutableListOf()) { url ->
            CoreHttpResponse(
                url,
                "<main><p class=\"meaning\">第一义</p><p class=\"meaning\">第二义</p></main>"
            )
        }

        val result = CoreDictRuleService(InMemoryCoreLibrary(), client).search(rule, "词")

        assertEquals("第一义\n第二义", result)
    }

    @Test
    fun searchEnabledUsesOnlyEnabledRulesInSortOrder() {
        val library = InMemoryCoreLibrary()
        library.saveDictRule(
            CoreDictRule(
                name = "停用",
                urlRule = "https://dict.example/disabled?key={{key}}",
                enabled = false,
                sortNumber = 0
            )
        )
        library.saveDictRule(
            CoreDictRule(
                name = "第二",
                urlRule = "https://dict.example/second?key={{key}}",
                sortNumber = 2
            )
        )
        library.saveDictRule(
            CoreDictRule(
                name = "第一",
                urlRule = "https://dict.example/first?key={{key}}",
                sortNumber = 1
            )
        )
        val service = CoreDictRuleService(
            library,
            recordingClient(mutableListOf()) { url -> CoreHttpResponse(url, url.substringAfterLast('/')) }
        )

        val results = service.searchEnabled("词")

        assertEquals(listOf("第一", "第二"), results.map { it.rule.name })
        assertEquals(listOf("first?key=%E8%AF%8D", "second?key=%E8%AF%8D"), results.map { it.text })
        assertTrue(results.all { it.error == null })
    }

    private fun recordingClient(
        requestedUrls: MutableList<String>,
        response: (String) -> CoreHttpResponse
    ): CoreHttpClient = object : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            requestedUrls += url
            return response(url)
        }
    }
}
