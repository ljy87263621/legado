package io.legado.core.source

import io.legado.core.library.CoreBookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoreUrlRuleSupportTest {

    private val source = CoreBookSource(bookSourceUrl = "https://source.example/root/")

    @Test
    fun dynamicOptionsUseTheFirstValueByDefault() {
        val resolved = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = "https://source.example/<分类(玄幻:fantasy,都市:city)>"
        )

        assertEquals("https://source.example/fantasy", resolved.url)
        assertEquals("https://source.example/fantasy", resolved.requestUrl)
    }

    @Test
    fun dynamicOptionsUseTheSelectedValue() {
        val resolved = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = "https://source.example/<分类(玄幻:fantasy,都市:city)>",
            selectedOptions = mapOf("分类" to "city")
        )

        assertEquals("https://source.example/city", resolved.url)
    }

    @Test
    fun malformedOrEmptyDynamicOptionsRemainInTheUrl() {
        val empty = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = "https://source.example/<分类()>",
        )
        val malformed = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = "https://source.example/<分类(:)>"
        )

        assertEquals("https://source.example/<分类()>", empty.url)
        assertEquals("https://source.example/<分类(:)>", malformed.url)
    }

    @Test
    fun templatesEncodeQueryValuesButKeepPathValues() {
        val resolved = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = "https://source.example/book/{{key}}?q={{key}}&page={{page}}",
            keyword = "星河 传",
            page = 3
        )

        assertEquals("https://source.example/book/星河 传?q=星河 传&page=3", resolved.url)
        assertEquals(
            "https://source.example/book/星河 传?q=%E6%98%9F%E6%B2%B3%20%E4%BC%A0&page=3",
            resolved.requestUrl
        )
        assertEquals("q=%E6%98%9F%E6%B2%B3%20%E4%BC%A0&page=3", resolved.encodedParams)
    }

    @Test
    fun urlFragmentsRunBeforeEmbeddedTemplatesAndCanBeChained() {
        val resolved = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = "https://source.example/search/<js>result + '-one'</js><js>result + '-two'</js>?q={{key}}&page={{page}}",
            keyword = "星河",
            page = 4
        )

        assertEquals(
            "https://source.example/search/-one-two?q=星河&page=4",
            resolved.url
        )
        assertEquals("https://source.example/search/-one-two?q=%E6%98%9F%E6%B2%B3&page=4", resolved.requestUrl)
    }

    @Test
    fun urlOptionsAddHeadersEvaluateJsAndEncodeFormBody() {
        val resolved = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = "https://source.example/api?name={{key}}," +
                "{\"method\":\"POST\",\"headers\":{\"X-Token\":\"abc\"}," +
                "\"body\":\"name={{key}}&page={{page}}\",\"js\":\"url + '?signed=1'\"}",
            keyword = "星河",
            page = 2
        )

        assertEquals("POST", resolved.method)
        assertEquals("abc", resolved.headers["X-Token"])
        assertEquals("name=%E6%98%9F%E6%B2%B3&page=2", resolved.body)
        assertEquals("https://source.example/api?name=星河?signed=1", resolved.url)
        assertEquals("https://source.example/api?name=%E6%98%9F%E6%B2%B3?signed=1", resolved.requestUrl)
    }

    @Test
    fun malformedUrlOptionsFailClosedAndKeepTheOriginalUrl() {
        val raw = "https://source.example/search?q=星河,{\"headers\":"

        val resolved = CoreUrlRuleSupport.resolve(source = source, rawUrl = raw)

        assertEquals(raw, resolved.url)
        assertEquals("https://source.example/search?q=%E6%98%9F%E6%B2%B3,{%22headers%22:", resolved.requestUrl)
    }

    @Test
    fun browserDependentUrlOptionsAreRejectedExplicitly() {
        assertThrows(CoreUrlRuleException::class.java) {
            CoreUrlRuleSupport.resolve(
                source = source,
                rawUrl = "https://source.example/page,{\"webView\":true}"
            )
        }
    }
}
