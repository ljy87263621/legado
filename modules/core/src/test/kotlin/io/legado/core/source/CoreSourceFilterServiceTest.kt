package io.legado.core.source

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreSourceFilterRule
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreSourceFilterServiceTest {

    @Test
    fun dropsResultsWhenAnyEnabledRuleMatchesAnySelectedField() {
        val firstSource = CoreBookSource(
            bookSourceUrl = "source-1",
            bookSourceName = "源一",
            bookSourceGroup = "玄幻, 精品"
        )
        val secondSource = CoreBookSource(
            bookSourceUrl = "source-2",
            bookSourceName = "源二",
            bookSourceGroup = "都市"
        )
        val library = InMemoryCoreLibrary().apply {
            saveSource(firstSource)
            saveSource(secondSource)
            saveSourceFilterRule(
                CoreSourceFilterRule(
                    id = "intro-ad",
                    pattern = "广告",
                    fields = "NAME,INTRO"
                )
            )
            saveSourceFilterRule(
                CoreSourceFilterRule(
                    id = "urban-author",
                    pattern = "乙作者",
                    fields = "AUTHOR",
                    scope = "都市"
                )
            )
        }
        val results = listOf(
            result(firstSource, CoreBook("book-1", name = "保留", intro = "干净简介")),
            result(firstSource, CoreBook("book-2", name = "广告书", intro = "干净简介")),
            result(secondSource, CoreBook("book-3", author = "乙作者")),
            result(secondSource, CoreBook("book-4", author = "甲作者"))
        )

        val report = CoreSourceFilterService(library).apply(results)

        assertEquals(listOf("book-1", "book-4"), report.results.map { it.book.bookUrl })
        assertEquals(2, report.filteredCount)
        assertEquals(0, report.invalidRuleCount)
    }

    @Test
    fun disabledRulesDoNotFilterAndInvalidRulesDoNotBreakDiscovery() {
        val source = CoreBookSource(bookSourceUrl = "source")
        val library = InMemoryCoreLibrary().apply {
            saveSource(source)
            saveSourceFilterRule(
                CoreSourceFilterRule(
                    id = "disabled",
                    enabled = false,
                    pattern = "广告",
                    fields = "NAME"
                )
            )
            saveSourceFilterRule(
                CoreSourceFilterRule(
                    id = "bad-regex",
                    pattern = "[",
                    fields = "NAME"
                )
            )
            saveSourceFilterRule(
                CoreSourceFilterRule(
                    id = "bad-fields",
                    pattern = "广告",
                    fields = "UNKNOWN"
                )
            )
            saveSourceFilterRule(
                CoreSourceFilterRule(
                    id = "bad-scope",
                    pattern = "广告",
                    fields = "NAME",
                    scope = "源::"
                )
            )
        }

        val report = CoreSourceFilterService(library).apply(
            listOf(result(source, CoreBook("book", name = "广告书")))
        )

        assertEquals(listOf("book"), report.results.map { it.book.bookUrl })
        assertEquals(0, report.filteredCount)
        assertEquals(3, report.invalidRuleCount)
    }

    private fun result(source: CoreBookSource, book: CoreBook) = CoreSearchResult(book, source)
}
