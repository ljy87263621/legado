package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.OnlineBookService
import io.legado.core.source.CoreSourceSessionStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailModelTest {

    @Test
    fun openingSearchResultLoadsBookInfoAndChapterList() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例源",
            ruleBookInfo = "{\"name\":\"h1\",\"author\":\".author\"}",
            ruleToc = "{\"chapterList\":\".chapter\",\"chapterName\":\"a\",\"chapterUrl\":\"a@href\"}"
        )
        library.saveSource(source)
        val service = OnlineBookService(library, FakeHttpClient())
        val model = BookDetailModel(library, service)

        model.open(
            CoreBook(
                bookUrl = "https://source.example/book/1",
                tocUrl = "https://source.example/book/1",
                origin = source.bookSourceUrl
            )
        )

        assertEquals("星河", model.book?.name)
        assertEquals("甲作者", model.book?.author)
        assertEquals("第一章", model.chapters.single().title)
        assertTrue(library.book("https://source.example/book/1") != null)
        assertTrue(model.error == null)
    }

    @Test
    fun detailModelExposesLoadFailures() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            ruleBookInfo = "{\"name\":\"h1\"}"
        )
        val book = CoreBook("https://source.example/book/1", origin = source.bookSourceUrl)
        library.saveSource(source)
        val model = BookDetailModel(
            library,
            OnlineBookService(library, object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                    error("连接失败")
            })
        )

        model.open(book)

        assertEquals("连接失败", model.error)
        assertTrue(model.chapters.isEmpty())
    }

    @Test
    fun detailModelExposesLoginUrlWhenTheSourceSessionExpires() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            ruleBookInfo = "{\"name\":\"h1\"}"
        )
        val book = CoreBook("https://source.example/book/1", origin = source.bookSourceUrl)
        library.saveSource(source)
        val model = BookDetailModel(
            library,
            OnlineBookService(library, object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                    CoreHttpResponse("https://source.example/login", "", 401)
            })
        )

        model.open(book)

        assertEquals(CoreSourceSessionStatus.LOGIN_REQUIRED, model.sessionStatus)
        assertEquals("https://source.example/login", model.loginUrl)
    }

    @Test
    fun sourceCandidatesKeepMatchingBooksFromDistinctSources() {
        val library = InMemoryCoreLibrary()
        val firstSource = source("https://source-one.example", "源一")
        val secondSource = source("https://source-two.example", "源二")
        val unrelatedSource = source("https://source-three.example", "源三")
        library.saveSource(firstSource)
        library.saveSource(secondSource)
        library.saveSource(unrelatedSource)
        val current = CoreBook(
            bookUrl = "https://source-one.example/book/1",
            name = "星河",
            author = "甲作者",
            origin = firstSource.bookSourceUrl,
            group = 2L
        )
        library.saveBook(current)

        val model = BookDetailModel(
            library,
            OnlineBookService(library, CandidateHttpClient()),
            BookSourceSearchService(library, CandidateHttpClient())
        )
        model.open(current)
        model.loadSourceCandidates()

        assertEquals(
            listOf(firstSource.bookSourceUrl, secondSource.bookSourceUrl),
            model.sourceCandidates.map { it.source.bookSourceUrl }
        )
        assertTrue(model.sourceCandidates.none { it.source.bookSourceUrl == unrelatedSource.bookSourceUrl })
        assertEquals(2L, model.sourceCandidates.first().book.group)
    }

    @Test
    fun sourceCandidatesAlwaysKeepTheCurrentSourceWhenItsSearchResultIsMissing() {
        val library = InMemoryCoreLibrary()
        val firstSource = source("https://source-one.example", "源一")
        val secondSource = source("https://source-two.example", "源二")
        library.saveSource(firstSource)
        library.saveSource(secondSource)
        val current = CoreBook(
            bookUrl = "https://source-one.example/book/1",
            name = "星河",
            author = "甲作者",
            origin = firstSource.bookSourceUrl
        )
        library.saveBook(current)
        val client = object : CandidateHttpClient() {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                if (url.startsWith("https://source-one.example/search")) {
                    return CoreHttpResponse(
                        url,
                        "<div class='book'><h2>另一部书</h2><span class='author'>乙作者</span><a href='/book/3'>详情</a></div>"
                    )
                }
                return super.get(url, headers)
            }
        }
        val model = BookDetailModel(
            library,
            OnlineBookService(library, client),
            BookSourceSearchService(library, client)
        )

        model.open(current)
        model.loadSourceCandidates()

        assertEquals(
            listOf(firstSource.bookSourceUrl, secondSource.bookSourceUrl),
            model.sourceCandidates.map { it.source.bookSourceUrl }
        )
        assertEquals(current.bookUrl, model.sourceCandidates.first().book.bookUrl)
    }

    @Test
    fun switchingSourceReplacesBookAndRefreshesItsChapters() {
        val library = InMemoryCoreLibrary()
        val firstSource = source("https://source-one.example", "源一")
        val secondSource = source("https://source-two.example", "源二")
        library.saveSource(firstSource)
        library.saveSource(secondSource)
        val current = CoreBook(
            bookUrl = "https://source-one.example/book/1",
            name = "星河",
            author = "甲作者",
            origin = firstSource.bookSourceUrl,
            group = 2L
        )
        library.saveBook(current)
        library.saveChapter(io.legado.core.library.CoreChapter(current.bookUrl, "old-chapter", "旧目录", 0))

        val client = CandidateHttpClient()
        val model = BookDetailModel(
            library,
            OnlineBookService(library, client),
            BookSourceSearchService(library, client)
        )
        model.open(current)
        model.loadSourceCandidates()
        val alternative = model.sourceCandidates.single { it.source.bookSourceUrl == secondSource.bookSourceUrl }

        assertNotNull(model.switchSource(alternative))

        val switched = requireNotNull(model.book)
        assertEquals(secondSource.bookSourceUrl, switched.origin)
        assertEquals(2L, switched.group)
        assertEquals(listOf("第二章"), model.chapters.map { it.title })
        assertTrue(library.book(current.bookUrl) == null)
        assertEquals(switched, library.book(switched.bookUrl))
    }

    @Test
    fun failedSourceSwitchKeepsTheCurrentBookAndChapters() {
        val library = InMemoryCoreLibrary()
        val firstSource = source("https://source-one.example", "源一")
        val secondSource = source("https://source-two.example", "源二")
        library.saveSource(firstSource)
        library.saveSource(secondSource)
        val current = CoreBook(
            bookUrl = "https://source-one.example/book/1",
            name = "星河",
            author = "甲作者",
            origin = firstSource.bookSourceUrl
        )
        library.saveBook(current)
        val client = object : CandidateHttpClient() {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                if (url == "https://source-two.example/book/2") error("新书源不可用")
                return super.get(url, headers)
            }
        }
        val model = BookDetailModel(
            library,
            OnlineBookService(library, client),
            BookSourceSearchService(library, client)
        )
        model.open(current)
        model.loadSourceCandidates()
        val alternative = model.sourceCandidates.single { it.source.bookSourceUrl == secondSource.bookSourceUrl }
        val persistedCurrent = requireNotNull(model.book)

        assertTrue(model.switchSource(alternative) == null)
        assertEquals(persistedCurrent.bookUrl, model.book?.bookUrl)
        assertEquals(firstSource.bookSourceUrl, model.book?.origin)
        assertEquals(persistedCurrent, library.book(persistedCurrent.bookUrl))
        assertTrue(model.sourceError?.contains("新书源不可用") == true)
    }

    @Test
    fun failedChapterRefreshDoesNotLeaveAnOrphanedReplacementBook() {
        val library = InMemoryCoreLibrary()
        val firstSource = source("https://source-one.example", "源一")
        val secondSource = source("https://source-two.example", "源二")
        library.saveSource(firstSource)
        library.saveSource(secondSource)
        val current = CoreBook(
            bookUrl = "https://source-one.example/book/1",
            name = "星河",
            author = "甲作者",
            origin = firstSource.bookSourceUrl
        )
        library.saveBook(current)
        val client = object : CandidateHttpClient() {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                if (url == "https://source-two.example/book/2") {
                    return CoreHttpResponse(url, "<h1>星河</h1><span class='author'>甲作者</span>")
                }
                return super.get(url, headers)
            }
        }
        val model = BookDetailModel(
            library,
            OnlineBookService(library, client),
            BookSourceSearchService(library, client)
        )
        model.open(current)
        model.loadSourceCandidates()
        val alternative = model.sourceCandidates.single { it.source.bookSourceUrl == secondSource.bookSourceUrl }

        assertTrue(model.switchSource(alternative) == null)
        assertTrue(library.book(alternative.book.bookUrl) == null)
        assertTrue(library.book(current.bookUrl) != null)
    }

    private fun source(url: String, name: String): CoreBookSource = CoreBookSource(
        bookSourceUrl = url,
        bookSourceName = name,
        searchUrl = "$url/search?q={{key}}",
        ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\",\"author\":\".author\",\"bookUrl\":\"a@href\"}",
        ruleBookInfo = "{\"name\":\"h1\",\"author\":\".author\"}",
        ruleToc = "{\"chapterList\":\".chapter\",\"chapterName\":\"a\",\"chapterUrl\":\"a@href\"}"
    )

    private open class CandidateHttpClient : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = when {
            url == "https://source-one.example/book/1" -> CoreHttpResponse(
                url,
                "<h1>星河</h1><span class='author'>甲作者</span><div class='chapter'><a href='/chapter/1'>第一章</a></div>"
            )
            url == "https://source-two.example/book/2" -> CoreHttpResponse(
                url,
                "<h1>星河</h1><span class='author'>甲作者</span><div class='chapter'><a href='/chapter/2'>第二章</a></div>"
            )
            url.startsWith("https://source-one.example/search") -> CoreHttpResponse(
                url,
                "<div class='book'><h2>星河</h2><span class='author'>甲作者</span><a href='/book/1'>详情</a></div>"
            )
            url.startsWith("https://source-two.example/search") -> CoreHttpResponse(
                url,
                "<div class='book'><h2>星河</h2><span class='author'>甲作者</span><a href='/book/2'>详情</a></div>"
            )
            url.startsWith("https://source-three.example/search") -> CoreHttpResponse(
                url,
                "<div class='book'><h2>另一部书</h2><span class='author'>乙作者</span><a href='/book/3'>详情</a></div>"
            )
            else -> error("unexpected url: $url")
        }
    }

    private class FakeHttpClient : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
            when (url) {
                "https://source.example/book/1" -> CoreHttpResponse(
                    url,
                    "<h1>星河</h1><span class='author'>甲作者</span><div class='chapter'><a href='/chapter/1'>第一章</a></div>"
                )
                else -> error("unexpected url: $url")
            }
    }
}
