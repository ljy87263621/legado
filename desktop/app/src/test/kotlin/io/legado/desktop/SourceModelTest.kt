package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
