package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreModelTest {

    @Test
    fun modelLoadsSelectedSourceOptionsAndAddsResultsToBookshelf() {
        val library = InMemoryCoreLibrary()
        val source = source()
        library.saveSource(source)
        val model = ExploreModel(library, BookSourceSearchService(library, FakeHttpClient()))

        model.refreshSources()
        assertEquals(listOf("分类"), model.options.map { it.name })
        model.setOption("分类", "city")
        model.load()

        assertEquals("都市书", model.results.single().book.name)
        model.addToBookshelf(model.results.single())
        assertTrue(library.book("https://books.example/book/city") != null)
        assertEquals(1, model.page)
    }

    @Test
    fun modelReportsMissingExploreConfigurationAndDoesNotRequest() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://empty.example", bookSourceName = "空源")
        library.saveSource(source)
        val client = CountingHttpClient()
        val model = ExploreModel(library, BookSourceSearchService(library, client))

        model.selectSource(source)
        model.load()

        assertEquals("该书源没有发现配置", model.error)
        assertEquals(0, client.requestCount)
        assertTrue(model.results.isEmpty())
    }

    @Test
    fun nextPageAppendsResultsAndDeduplicatesByBookUrl() {
        val library = InMemoryCoreLibrary()
        val source = source()
        library.saveSource(source)
        val client = SequenceHttpClient(
            listOf(
                htmlResponse(
                    "https://books.example/book/1" to "第一本",
                    "https://books.example/book/2" to "第二本"
                ),
                htmlResponse(
                    "https://books.example/book/2" to "第二本（重复）",
                    "https://books.example/book/3" to "第三本"
                )
            )
        )
        val model = ExploreModel(library, BookSourceSearchService(library, client))

        model.refreshSources()
        model.load()
        model.loadNextPage()

        assertEquals(
            listOf(
                "https://books.example/book/1",
                "https://books.example/book/2",
                "https://books.example/book/3"
            ),
            model.results.map { it.book.bookUrl }
        )
        assertEquals(2, model.page)
        assertEquals(
            listOf(
                "https://books.example/explore/fantasy?page=1",
                "https://books.example/explore/fantasy?page=2"
            ),
            client.requestedUrls
        )
    }

    @Test
    fun changingAnExploreOptionResetsResultsAndPageBeforeReload() {
        val library = InMemoryCoreLibrary()
        val source = source()
        library.saveSource(source)
        val client = SequenceHttpClient(
            listOf(
                htmlResponse("https://books.example/book/fantasy" to "玄幻书"),
                htmlResponse("https://books.example/book/city" to "都市书")
            )
        )
        val model = ExploreModel(library, BookSourceSearchService(library, client))

        model.refreshSources()
        model.load()
        model.setOption("分类", "city")

        assertEquals(1, model.page)
        assertTrue(model.results.isEmpty())

        model.load()

        assertEquals("都市书", model.results.single().book.name)
        assertEquals("https://books.example/explore/city?page=1", client.requestedUrls.last())
    }

    @Test
    fun selectingAnotherSourceClearsResultsAndPreservesItsExploreOptions() {
        val library = InMemoryCoreLibrary()
        val first = source().copy(bookSourceName = "第一源")
        val second = source().copy(
            bookSourceUrl = "https://second.example",
            bookSourceName = "第二源",
            exploreUrl = "https://second.example/explore/<类型(完结:finished)>?page={{page}}"
        )
        library.saveSource(first)
        library.saveSource(second)
        val client = SequenceHttpClient(
            listOf(
                htmlResponse("https://books.example/book/1" to "第一源书"),
                htmlResponse("https://second.example/book/1" to "第二源书")
            )
        )
        val model = ExploreModel(library, BookSourceSearchService(library, client))

        model.selectSource(first)
        model.load()
        model.selectSource(second)

        assertEquals("第二源", model.selectedSource?.bookSourceName)
        assertEquals(listOf("类型"), model.options.map { it.name })
        assertEquals(1, model.page)
        assertTrue(model.results.isEmpty())
    }

    @Test
    fun firstPageFailureClearsPreviousResults() {
        val library = InMemoryCoreLibrary()
        val source = source()
        library.saveSource(source)
        val client = SequenceHttpClient(
            listOf(
                htmlResponse("https://books.example/book/1" to "已有结果"),
                TestResponse.Failure(IllegalStateException("网络不可用"))
            )
        )
        val model = ExploreModel(library, BookSourceSearchService(library, client))

        model.refreshSources()
        model.load()
        model.load()

        assertTrue(model.results.isEmpty())
        assertEquals("网络不可用", model.error)
    }

    @Test
    fun nextPageFailureKeepsPreviousResults() {
        val library = InMemoryCoreLibrary()
        val source = source()
        library.saveSource(source)
        val client = SequenceHttpClient(
            listOf(
                htmlResponse("https://books.example/book/1" to "已有结果"),
                TestResponse.Failure(IllegalStateException("下一页不可用"))
            )
        )
        val model = ExploreModel(library, BookSourceSearchService(library, client))

        model.refreshSources()
        model.load()
        model.loadNextPage()

        assertEquals(listOf("已有结果"), model.results.map { it.book.name })
        assertEquals(1, model.page)
        assertEquals("下一页不可用", model.error)
    }

    @Test
    fun refreshSourcesFiltersDisabledExploreSourcesAndKeepsCurrentSelection() {
        val library = InMemoryCoreLibrary()
        val selected = source().copy(bookSourceName = "当前源", customOrder = 2)
        val hidden = source().copy(
            bookSourceUrl = "https://hidden.example",
            bookSourceName = "隐藏源",
            enabledExplore = false,
            customOrder = 1
        )
        val other = source().copy(
            bookSourceUrl = "https://other.example",
            bookSourceName = "其他源",
            customOrder = 3
        )
        library.saveSource(selected)
        library.saveSource(hidden)
        library.saveSource(other)
        val model = ExploreModel(library, BookSourceSearchService(library, CountingHttpClient()))

        model.selectSource(selected)
        model.refreshSources()

        assertEquals(listOf("当前源", "其他源"), model.sources.map { it.bookSourceName })
        assertEquals("当前源", model.selectedSource?.bookSourceName)
    }

    @Test
    fun appliesEnabledSourceFilterRulesToEachExplorePageAndReportsInvalidRules() {
        val library = InMemoryCoreLibrary()
        val source = source()
        library.saveSource(source)
        library.saveSourceFilterRule(
            io.legado.core.library.CoreSourceFilterRule(
                id = "ad",
                name = "屏蔽广告",
                pattern = "广告",
                fields = "NAME"
            )
        )
        library.saveSourceFilterRule(
            io.legado.core.library.CoreSourceFilterRule(
                id = "invalid",
                name = "坏规则",
                pattern = "[",
                fields = "NAME"
            )
        )
        val client = SequenceHttpClient(
            listOf(
                htmlResponse(
                    "https://books.example/book/keep" to "保留",
                    "https://books.example/book/ad" to "广告书"
                ),
                htmlResponse(
                    "https://books.example/book/ad" to "广告书（重复）",
                    "https://books.example/book/next" to "下一本"
                )
            )
        )
        val model = ExploreModel(library, BookSourceSearchService(library, client))

        model.refreshSources()
        model.load()
        model.loadNextPage()

        assertEquals(
            listOf("https://books.example/book/keep", "https://books.example/book/next"),
            model.results.map { it.book.bookUrl }
        )
        assertEquals(2, model.filteredCount)
        assertEquals(1, model.invalidRuleCount)
    }

    private fun source() = CoreBookSource(
        bookSourceUrl = "https://books.example",
        bookSourceName = "分类源",
        exploreUrl = "https://books.example/explore/<分类(玄幻:fantasy,都市:city)>?page={{page}}",
        ruleExplore = "{\"bookList\":\".book\",\"name\":\"h2\",\"bookUrl\":\"a@href\"}"
    )

    private open class CountingHttpClient : CoreHttpClient {
        var requestCount = 0

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            requestCount++
            return CoreHttpResponse(url, "")
        }
    }

    private class FakeHttpClient : CountingHttpClient() {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            requestCount++
            assertEquals("https://books.example/explore/city?page=1", url)
            return CoreHttpResponse(
                url,
                "<div class='book'><h2>都市书</h2><a href='/book/city'>详情</a></div>"
            )
        }
    }

    private sealed interface TestResponse {
        data class Body(val factory: (String) -> CoreHttpResponse) : TestResponse

        data class Failure(val throwable: Throwable) : TestResponse
    }

    private class SequenceHttpClient(
        private val responses: List<TestResponse>
    ) : CoreHttpClient {
        val requestedUrls = mutableListOf<String>()
        private var index = 0

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            requestedUrls += url
            val response = responses.getOrNull(index++) ?: error("没有为请求准备响应: $url")
            return when (response) {
                is TestResponse.Body -> response.factory(url)
                is TestResponse.Failure -> throw response.throwable
            }
        }
    }

    private fun htmlResponse(vararg books: Pair<String, String>): TestResponse = TestResponse.Body { url ->
        val body = books.joinToString("\n") { (bookUrl, name) ->
            "<div class='book'><h2>$name</h2><a href='$bookUrl'>详情</a></div>"
        }
        CoreHttpResponse(url, body)
    }
}
