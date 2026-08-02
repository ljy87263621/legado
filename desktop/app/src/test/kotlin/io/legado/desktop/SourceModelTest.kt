package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceModelTest {

    @Test
    fun sourceModelImportsExportsAndManagesSources() {
        val library = InMemoryCoreLibrary()
        val model = SourceModel(library)
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例源"
        )

        assertEquals(1, model.importJson("[{\"bookSourceUrl\":\"https://source.example\",\"bookSourceName\":\"示例源\"}]"))
        assertEquals(source, model.sources.single())

        model.setEnabled(source.bookSourceUrl, false)
        assertFalse(model.sources.single().enabled)
        model.delete(source.bookSourceUrl)
        assertTrue(model.sources.isEmpty())
    }

    @Test
    fun sourceModelExportsCurrentSourcesAsJson() {
        val library = InMemoryCoreLibrary()
        val model = SourceModel(library)
        model.save(CoreBookSource(bookSourceUrl = "source-1", bookSourceName = "源一"))

        val imported = SourceModel(InMemoryCoreLibrary())
        assertEquals(1, imported.importJson(model.exportJson()))
        assertEquals("源一", imported.sources.single().bookSourceName)
    }

    @Test
    fun sourceModelSavesCompleteSourceJson() {
        val model = SourceModel(InMemoryCoreLibrary())

        val saved = model.saveJson(
            """
            {
              "bookSourceUrl": "https://source.example",
              "bookSourceName": "完整书源",
              "searchUrl": "https://source.example/search?key={{key}}",
              "ruleSearch": {"bookList":".book","name":".name@text"},
              "jsLib": "function normalize(value) { return value.trim(); }"
            }
            """.trimIndent()
        )

        assertEquals("完整书源", saved.bookSourceName)
        assertEquals("https://source.example", model.sources.single().bookSourceUrl)
        assertTrue(model.sources.single().ruleSearch!!.contains("bookList"))
    }

    @Test
    fun sourceModelRejectsSourceWithoutUrl() {
        val model = SourceModel(InMemoryCoreLibrary())

        assertThrows(IllegalArgumentException::class.java) {
            model.saveJson("{\"bookSourceName\":\"无地址\"}")
        }
    }

    @Test
    fun sourceModelRemovesOldSourceWhenEditingItsUrl() {
        val library = InMemoryCoreLibrary()
        val model = SourceModel(library)
        model.save(CoreBookSource(bookSourceUrl = "https://old.example", bookSourceName = "旧地址"))

        model.saveJson(
            """
            {
              "bookSourceUrl": "https://new.example",
              "bookSourceName": "新地址"
            }
            """.trimIndent(),
            originalBookSourceUrl = "https://old.example"
        )

        assertEquals(listOf("https://new.example"), model.sources.map(CoreBookSource::bookSourceUrl))
        assertEquals(null, library.source("https://old.example"))
    }
}
