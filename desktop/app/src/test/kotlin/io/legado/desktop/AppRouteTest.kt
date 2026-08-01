package io.legado.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {

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
            listOf("书架", "搜索", "订阅", "书源", "设置"),
            AppRoute.primary.map(AppRoute::label)
        )
    }
}
