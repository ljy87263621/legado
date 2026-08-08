package io.legado.desktop

import io.legado.core.library.CoreSubscriptionPage
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionPageIconModelTest {
    @Test
    fun prefersPersistedIconAndFallsBackToSiteFavicon() {
        val page = CoreSubscriptionPage(
            url = "https://directory.example/catalog/gx.html",
            title = "阅读书源",
            iconUrl = "https://cdn.example/icon.png"
        )

        assertEquals(
            listOf(
                "https://cdn.example/icon.png",
                "https://directory.example/favicon.ico"
            ),
            SubscriptionPageIconModel.candidateUrls(page)
        )
    }

    @Test
    fun usesOnlySiteFaviconWhenPageHasNoPersistedIcon() {
        val page = CoreSubscriptionPage(
            url = "http://yuedu.miaogongzi.net/",
            title = "阅读书源"
        )

        assertEquals(
            listOf("http://yuedu.miaogongzi.net/favicon.ico"),
            SubscriptionPageIconModel.candidateUrls(page)
        )
    }
}
