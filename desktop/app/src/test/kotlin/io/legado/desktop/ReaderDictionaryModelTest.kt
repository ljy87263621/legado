package io.legado.desktop

import io.legado.core.library.CoreDictRule
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDictionaryModelTest {

    @Test
    fun clipboardTextBecomesTheNormalizedDictionaryQuery() {
        assertEquals(
            "选中的词\n下一行",
            ReaderDictionaryQuery.fromClipboard("  选中的词  \r\n 下一行 \t")
        )
    }

    @Test
    fun blankClipboardTextDoesNotBecomeAQuery() {
        assertEquals(null, ReaderDictionaryQuery.fromClipboard(" \n\t "))
        assertEquals(null, ReaderDictionaryQuery.fromClipboard(null))
    }

    @Test
    fun lookupTrimsQueryAndReturnsResultsFromEnabledRules() {
        val library = InMemoryCoreLibrary()
        library.saveDictRule(
            CoreDictRule(
                name = "释义词典",
                urlRule = "https://dict.example/meaning?q={{key}}",
                sortNumber = 0
            )
        )
        library.saveDictRule(
            CoreDictRule(
                name = "例句词典",
                urlRule = "https://dict.example/example?q={{key}}",
                sortNumber = 1
            )
        )

        val model = ReaderDictionaryModel(
            library,
            object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                    CoreHttpResponse(url, "结果:$url")
            }
        )

        val results = model.lookup("  漂亮  \n")

        assertEquals("漂亮", results.query)
        assertEquals(listOf("释义词典", "例句词典"), results.items.map { it.rule.name })
        assertTrue(results.items.all { it.isSuccess })
        assertEquals(
            "结果:https://dict.example/meaning?q=%E6%BC%82%E4%BA%AE",
            results.items.first().text
        )
    }

    @Test
    fun lookupKeepsRuleErrorsAlongsideSuccessfulResults() {
        val library = InMemoryCoreLibrary()
        library.saveDictRule(
            CoreDictRule(name = "失败", urlRule = "https://dict.example/fail?q={{key}}", sortNumber = 0)
        )
        library.saveDictRule(
            CoreDictRule(name = "成功", urlRule = "https://dict.example/ok?q={{key}}", sortNumber = 1)
        )
        val model = ReaderDictionaryModel(
            library,
            object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                    if (url.contains("/fail")) {
                        CoreHttpResponse(url, "", statusCode = 503)
                    } else {
                        CoreHttpResponse(url, "释义")
                    }
            }
        )

        val results = model.lookup("词")

        assertEquals(listOf("失败", "成功"), results.items.map { it.rule.name })
        assertTrue(results.items.first().error?.contains("503") == true)
        assertEquals("释义", results.items.last().text)
    }

    @Test
    fun lookupRejectsBlankQueriesWithoutMakingRequests() {
        val model = ReaderDictionaryModel(InMemoryCoreLibrary())

        val error = runCatching { model.lookup(" \n\t") }.exceptionOrNull()

        assertEquals("查词内容不能为空", error?.message)
    }
}
