package io.legado.desktop

import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPageModelTest {

    @Test
    fun savesRefreshesAndDeletesSubscriptionPageEntries() {
        val library = InMemoryCoreLibrary()
        val model = SubscriptionPageModel(library, clock = { 123L })

        val page = model.savePage(
            url = "http://yuedu.miaogongzi.net/gx.html",
            title = "阅读书源",
            iconUrl = "http://yuedu.miaogongzi.net/favicon.ico"
        )

        assertEquals(page, model.pages.single())
        assertEquals(123L, page.lastUpdatedAt)
        assertEquals(page, library.subscriptionPages().single())

        model.deletePage(page.url)

        assertTrue(model.pages.isEmpty())
        assertTrue(library.subscriptionPages().isEmpty())
    }

    @Test
    fun savesPageWithFallbackTitleWhenOnlyUrlIsProvided() {
        val model = SubscriptionPageModel(InMemoryCoreLibrary(), clock = { 456L })

        val page = model.savePage("https://example.com/subscriptions")

        assertEquals("subscriptions", page.title)
        assertEquals("订阅页面", page.category)
    }

    @Test
    fun savesLoadedDirectoryPageMetadataAsTheWebPageEntry() {
        val model = SubscriptionPageModel(InMemoryCoreLibrary(), clock = { 789L })
        val loadedPage = SubscriptionDirectoryPage(
            url = "https://example.com/gx.html",
            title = "阅读书源",
            iconUrl = "https://example.com/favicon.ico",
            entries = listOf(SubscriptionDirectoryModel.pageEntry("https://example.com/source.json", "书源"))
        )

        val saved = model.savePage(loadedPage)

        assertEquals("https://example.com/gx.html", saved.url)
        assertEquals("阅读书源", saved.title)
        assertEquals("https://example.com/favicon.ico", saved.iconUrl)
        assertEquals(789L, saved.lastUpdatedAt)
        assertEquals(listOf(saved), model.pages)
    }
}
