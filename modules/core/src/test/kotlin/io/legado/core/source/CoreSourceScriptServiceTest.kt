package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoreSourceScriptServiceTest {

    @Test
    fun sourceBindingReadsAndUpdatesTheStoredSourceVariable() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://source.example")
        library.saveSourceVariable(source.bookSourceUrl, "before")

        val result = CoreSourceScriptService(library = library).test(
            source = source,
            ruleField = "test",
            script = "source.setVariable(source.getVariable() + '-after'); source.getVariable()"
        )

        assertEquals("before-after", result)
        assertEquals("before-after", library.sourceVariable(source.bookSourceUrl))
    }

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

    @Test
    fun expandsPlainSearchUrlTemplates() {
        val service = CoreSourceDebugService()
        val source = CoreBookSource(bookSourceUrl = "https://source.example")

        assertEquals(
            "https://source.example/search?q=hello+world&page=3",
            service.expandUrl(
                source = source,
                template = "https://source.example/search?q={{key}}&page={{page}}",
                keyword = "hello world",
                page = 3
            )
        )
    }

    @Test
    fun expandsSearchUrlScriptWithLibrarySourceBinding() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://source.example")
        library.saveSourceVariable(source.bookSourceUrl, "stored")

        assertEquals(
            "stored/keyword/2",
            CoreSourceDebugService().expandUrl(
                source = source,
                template = "@js: return source.getVariable() + '/' + key + '/' + page;",
                keyword = "keyword",
                page = 2,
                library = library
            )
        )
    }
}
