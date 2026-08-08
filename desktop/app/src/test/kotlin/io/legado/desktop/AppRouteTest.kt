package io.legado.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.legado.desktop.persistence.DesktopSetupStore

class AppRouteTest {

    @Test
    fun firstRunUsesWelcomeButExternalSubscriptionLaunchKeepsItsRoute() {
        assertEquals(
            AppRoute.WELCOME,
            DesktopStartupRoute.resolve(setupComplete = false, hasLaunchRequest = false)
        )
        assertEquals(
            AppRoute.SUBSCRIPTIONS,
            DesktopStartupRoute.resolve(setupComplete = false, hasLaunchRequest = true)
        )
    }

    @Test
    fun localFileLaunchStartsOnTheDesktopShellBeforeOpeningTheImportedBook() {
        assertEquals(
            AppRoute.BOOKSHELF,
            DesktopStartupRoute.resolve(
                setupComplete = true,
                hasLaunchRequest = true,
                hasLocalFileLaunch = true
            )
        )
    }

    @Test
    fun completedSetupStartsOnBookshelf() {
        assertEquals(
            AppRoute.BOOKSHELF,
            DesktopStartupRoute.resolve(setupComplete = true, hasLaunchRequest = false)
        )
    }

    @Test
    fun missingSetupStoreKeepsTheDesktopShellUsable() {
        assertEquals(
            AppRoute.BOOKSHELF,
            DesktopStartupRoute.resolve(
                setupComplete = null,
                hasLaunchRequest = false,
                setupStoreAvailable = false
            )
        )
        assertEquals(
            AppRoute.SUBSCRIPTIONS,
            DesktopStartupRoute.resolve(
                setupComplete = null,
                hasLaunchRequest = true,
                setupStoreAvailable = false
            )
        )
    }

    @Test
    fun welcomeCompletionPersistsThroughItsSetupStore() {
        val store = RecordingDesktopSetupStore()
        val model = WelcomeModel(store)

        model.completeSetup()

        assertTrue(store.isSetupComplete())
    }

    @Test
    fun appStartsOnBookshelf() {
        assertEquals(AppRoute.BOOKSHELF, AppState().route)
    }

    @Test
    fun navigationChangesTheCurrentRoute() {
        val state = AppState()

        state.navigate(AppRoute.SOURCES)
        assertEquals(AppRoute.SOURCES, state.route)

        state.navigate(AppRoute.SETTINGS)
        assertEquals(AppRoute.SETTINGS, state.route)
    }

    @Test
    fun themeStartsLightAndCanBeToggled() {
        val state = AppState()

        assertFalse(state.isDarkTheme)

        state.toggleTheme()
        assertTrue(state.isDarkTheme)

        state.toggleTheme()
        assertFalse(state.isDarkTheme)
    }

    @Test
    fun everyPrimaryRouteHasAStableLabel() {
        assertEquals(
            listOf("书架", "搜索", "发现", "订阅", "书源", "设置"),
            AppRoute.primary.map(AppRoute::label)
        )
    }

    @Test
    fun exploreIsAPrimaryDiscoveryRoute() {
        assertEquals("发现", AppRoute.EXPLORE.label)
        assertTrue(AppRoute.EXPLORE in AppRoute.primary)
    }

    @Test
    fun bookDetailIsAnInternalRouteOutsidePrimaryNavigation() {
        assertEquals("详情", AppRoute.BOOK_DETAIL.label)
        assertFalse(AppRoute.BOOK_DETAIL in AppRoute.primary)
    }

    @Test
    fun readingRecordsIsAnInternalRouteOutsidePrimaryNavigation() {
        assertEquals("阅读记录", AppRoute.READ_RECORDS.label)
        assertFalse(AppRoute.READ_RECORDS in AppRoute.primary)
    }

    @Test
    fun replacementRulesAreAnInternalRouteOutsidePrimaryNavigation() {
        assertEquals("替换规则", AppRoute.REPLACE_RULES.label)
        assertFalse(AppRoute.REPLACE_RULES in AppRoute.primary)
    }

    @Test
    fun dictionaryRulesAreAnInternalRouteOutsidePrimaryNavigation() {
        assertEquals("字典规则", AppRoute.DICT_RULES.label)
        assertFalse(AppRoute.DICT_RULES in AppRoute.primary)
    }

    @Test
    fun txtTocRulesAreAnInternalRouteOutsidePrimaryNavigation() {
        assertEquals("TXT目录规则", AppRoute.TXT_TOC_RULES.label)
        assertFalse(AppRoute.TXT_TOC_RULES in AppRoute.primary)
    }

    @Test
    fun sourceFilterRulesAreAnInternalRouteOutsidePrimaryNavigation() {
        assertEquals("书源发现筛选", AppRoute.SOURCE_FILTER_RULES.label)
        assertFalse(AppRoute.SOURCE_FILTER_RULES in AppRoute.primary)
    }

    private class RecordingDesktopSetupStore : DesktopSetupStore {
        private var complete = false

        override fun isSetupComplete(): Boolean = complete

        override fun markSetupComplete() {
            complete = true
        }
    }
}
