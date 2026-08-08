package io.legado.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreSourceRuntimeDataTest {

    @Test
    fun sourceVariablesCanBeSavedReadAndDeleted() {
        val library = InMemoryCoreLibrary()

        assertNull(library.sourceVariable("https://books.example"))
        library.saveSourceVariable("https://books.example", "{\"token\":\"abc\"}")
        assertEquals("{\"token\":\"abc\"}", library.sourceVariable("https://books.example"))

        library.saveSourceVariable("https://books.example", null)

        assertNull(library.sourceVariable("https://books.example"))
    }

    @Test
    fun cookiesAreReplacedByDomainNameAndPath() {
        val library = InMemoryCoreLibrary()
        val first = CoreCookie(domain = "books.example", path = "/", name = "sid", value = "one")
        val second = first.copy(value = "two")

        library.saveCookie(first)
        library.saveCookie(second)

        assertEquals(listOf(second), library.cookies())
        library.deleteCookie(second.domain, second.path, second.name)
        assertEquals(emptyList<CoreCookie>(), library.cookies())
    }
}
