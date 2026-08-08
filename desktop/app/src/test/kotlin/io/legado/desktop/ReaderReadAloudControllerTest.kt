package io.legado.desktop

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Test

class ReaderReadAloudControllerTest {

    @Test
    fun syncingAChapterReplacesThePreviousReaderAndClearDisposesIt() {
        val sessions = CopyOnWriteArrayList<TrackingWindowsSpeechSession>()
        val controller = ReaderReadAloudController { text ->
            WindowsReadAloudModel(
                text = text,
                sessionFactory = WindowsSpeechSessionFactory { _, _ ->
                    TrackingWindowsSpeechSession().also(sessions::add)
                }
            )
        }

        val first = controller.sync("chapter-1", "第一章")
        val second = controller.sync("chapter-2", "第二章")

        assertNotSame(first, second)
        assertEquals(WindowsReadAloudState.STOPPED, first?.state)
        assertNull(controller.sync("chapter-2", ""))
        assertEquals(WindowsReadAloudState.STOPPED, second?.state)

        controller.close()
    }

    private class TrackingWindowsSpeechSession : WindowsSpeechSession {
        override fun awaitCompletion(): Int = 0

        override fun stop() = Unit
    }
}
