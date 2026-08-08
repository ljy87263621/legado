package io.legado.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNavigationTest {

    @Test
    fun openingReaderRemembersItsReturnRoute() {
        val state = AppState()

        state.openReader(AppRoute.SUBSCRIPTIONS)

        assertEquals(AppRoute.READER, state.route)
        assertEquals(AppRoute.SUBSCRIPTIONS, state.readerReturnRoute)
        assertEquals("返回订阅", state.readerReturnLabel)
    }

    @Test
    fun closingReaderReturnsToTheRememberedRoute() {
        val state = AppState()
        state.openReader(AppRoute.READ_RECORDS)

        assertEquals(AppRoute.READ_RECORDS, state.closeReader())
        assertEquals(AppRoute.READ_RECORDS, state.route)
    }

    @Test
    fun readerDefaultsToReturningToTheBookshelf() {
        val state = AppState()

        assertEquals(AppRoute.BOOKSHELF, state.readerReturnRoute)
        assertEquals("返回书架", state.readerReturnLabel)
    }
}
