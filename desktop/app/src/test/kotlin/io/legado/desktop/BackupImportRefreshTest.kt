package io.legado.desktop

import io.legado.core.library.CoreSubscriptionPage
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupImportRefreshTest {

    @Test
    fun refreshesSubscriptionPagesAfterBackupImport() {
        val library = InMemoryCoreLibrary()
        val bookshelfModel = BookshelfModel(library)
        val sourceModel = SourceModel(library)
        val subscriptionModel = SubscriptionModel(
            library,
            BookSourceSearchService(library, object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>) = CoreHttpResponse(url, "")
            })
        )
        val subscriptionPageModel = SubscriptionPageModel(library)
        val readerSettingsModel = ReaderSettingsModel(library)

        library.saveSubscriptionPage(
            CoreSubscriptionPage(
                url = "https://example.com/subscriptions",
                title = "示例订阅页"
            )
        )

        refreshAfterBackupImport(
            bookshelfModel = bookshelfModel,
            sourceModel = sourceModel,
            subscriptionModel = subscriptionModel,
            subscriptionPageModel = subscriptionPageModel,
            readerSettingsModel = readerSettingsModel
        )

        assertEquals("示例订阅页", subscriptionPageModel.pages.single().title)
    }
}
