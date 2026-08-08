package io.legado.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreReplacementServiceTest {

    private val book = CoreBook(
        bookUrl = "book-1",
        name = "星河旅人",
        origin = "source-1"
    )

    @Test
    fun appliesEnabledRulesInOrderAndReportsRulesThatChangedTheText() {
        val library = InMemoryCoreLibrary().apply {
            saveReplaceRule(
                CoreReplaceRule(
                    id = 1L,
                    name = "先替换",
                    pattern = "新旧",
                    replacement = "新词",
                    isRegex = false,
                    order = 2
                )
            )
            saveReplaceRule(
                CoreReplaceRule(
                    id = 2L,
                    name = "再清理",
                    pattern = "旧",
                    replacement = "新旧",
                    isRegex = false,
                    order = 1
                )
            )
            saveReplaceRule(
                CoreReplaceRule(
                    id = 3L,
                    name = "停用",
                    pattern = "词",
                    replacement = "错误",
                    isRegex = false,
                    enabled = false,
                    order = 0
                )
            )
        }

        val result = CoreReplacementService(library).processContent(book, "旧")

        assertEquals("新词", result.text)
        assertEquals(listOf(2L, 1L), result.appliedRuleIds)
    }

    @Test
    fun appliesRegexBackreferencesAndLiteralRulesToTheirDeclaredScopes() {
        val library = InMemoryCoreLibrary().apply {
            saveReplaceRule(
                CoreReplaceRule(
                    id = 10L,
                    name = "标题编号",
                    pattern = "第(\\d+)章",
                    replacement = "章节$1",
                    scopeTitle = true,
                    scopeContent = false
                )
            )
            saveReplaceRule(
                CoreReplaceRule(
                    id = 11L,
                    name = "正文标记",
                    pattern = "[标记]",
                    replacement = "",
                    isRegex = false,
                    scopeTitle = false,
                    scopeContent = true
                )
            )
        }
        val service = CoreReplacementService(library)

        assertEquals("章节12", service.processTitle(book, "第12章").text)
        assertEquals("正文", service.processContent(book, "[标记]正文").text)
    }

    @Test
    fun filtersRulesByBookOrSourceScopeAndHonorsExcludeScope() {
        val library = InMemoryCoreLibrary().apply {
            saveReplaceRule(
                CoreReplaceRule(
                    id = 20L,
                    name = "书名范围",
                    pattern = "A",
                    replacement = "B",
                    isRegex = false,
                    scope = "星河旅人",
                )
            )
            saveReplaceRule(
                CoreReplaceRule(
                    id = 21L,
                    name = "排除书源",
                    pattern = "A",
                    replacement = "C",
                    isRegex = false,
                    scope = "source-1",
                    excludeScope = "source-1"
                )
            )
            saveReplaceRule(
                CoreReplaceRule(
                    id = 22L,
                    name = "其他书源",
                    pattern = "A",
                    replacement = "D",
                    isRegex = false,
                    scope = "source-2"
                )
            )
        }

        assertEquals("B", CoreReplacementService(library).processContent(book, "A").text)
    }

    @Test
    fun disabledBookReplacementSkipsAllRules() {
        val library = InMemoryCoreLibrary().apply {
            saveReplaceRule(
                CoreReplaceRule(
                    id = 30L,
                    name = "正文规则",
                    pattern = "A",
                    replacement = "B",
                    isRegex = false
                )
            )
        }
        val disabledBook = book.copy(readConfigJson = "{\"useReplaceRule\":false}")

        val result = CoreReplacementService(library).processContent(disabledBook, "A")

        assertEquals("A", result.text)
        assertTrue(result.appliedRuleIds.isEmpty())
    }

    @Test
    fun validatesEmptyPatternsMalformedRegexAndUsesDefaultForInvalidTimeout() {
        assertFalse(CoreReplaceRule(pattern = "").isValid())
        assertFalse(CoreReplaceRule(pattern = "(").isValid())
        assertFalse(CoreReplaceRule(pattern = "a|").isValid())
        assertTrue(CoreReplaceRule(pattern = "a", timeoutMillisecond = 0).isValid())
        assertEquals(3000L, CoreReplaceRule(pattern = "a", timeoutMillisecond = 0).validTimeoutMillisecond())
    }
}
