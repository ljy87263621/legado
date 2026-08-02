package io.legado.core.source

import io.legado.core.library.CoreBookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoreSourceScriptServiceTest {

    @Test
    fun evaluatesScriptWithSourceAndInputBindings() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例源",
            jsLib = "function decorate(value) { return value + '-' + source.bookSourceName; }"
        )

        assertEquals(
            "body-示例源@https://source.example/page",
            CoreSourceScriptService().test(
                source = source,
                ruleField = "ruleSearch.searchUrl",
                script = "return decorate(result) + '@' + baseUrl;",
                input = "body",
                baseUrl = "https://source.example/page"
            )
        )
    }

    @Test
    fun reportsSourceAndRuleFieldWhenScriptFails() {
        val source = CoreBookSource(bookSourceUrl = "https://source.example")

        val error = assertThrows(CoreScriptException::class.java) {
            CoreSourceScriptService().test(
                source = source,
                ruleField = "ruleContent.content",
                script = "throw new Error('bad rule');",
                input = "body",
                baseUrl = source.bookSourceUrl
            )
        }

        assertEquals(source.bookSourceUrl, error.sourceUrl)
        assertEquals("ruleContent.content", error.ruleField)
    }
}
