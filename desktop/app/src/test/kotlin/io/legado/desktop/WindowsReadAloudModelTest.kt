package io.legado.desktop

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsReadAloudModelTest {

    @Test
    fun playSpeaksNonBlankParagraphsInOrderAndStopsAtEnd() {
        val spoken = CopyOnWriteArrayList<String>()
        val factory = WindowsSpeechSessionFactory { text, _ ->
            spoken += text
            ImmediateWindowsSpeechSession()
        }

        WindowsReadAloudModel("第一段\n\n第二段\r\n第三段", factory).use { model ->
            model.play()

            assertTrue(awaitUntil { model.state == WindowsReadAloudState.STOPPED })
            assertEquals(listOf("第一段", "第二段", "第三段"), spoken)
            assertEquals(0, model.currentParagraphIndex)
        }
    }

    @Test
    fun pauseStopsCurrentSpeechAndResumeRereadsThatParagraph() {
        val firstStarted = CountDownLatch(1)
        val sessions = CopyOnWriteArrayList<BlockingWindowsSpeechSession>()
        val factory = WindowsSpeechSessionFactory { text, _ ->
            BlockingWindowsSpeechSession(text).also {
                sessions += it
                firstStarted.countDown()
            }
        }

        WindowsReadAloudModel("第一段\n第二段", factory).use { model ->
            model.play()
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

            model.pause()
            assertEquals(WindowsReadAloudState.PAUSED, model.state)
            assertTrue(awaitUntil { sessions.first().stopped })

            model.play()
            assertTrue(awaitUntil { sessions.size == 2 })
            assertEquals("第一段", sessions[1].text)

            model.stop()
            assertEquals(WindowsReadAloudState.STOPPED, model.state)
        }
    }

    @Test
    fun pauseReturnsBeforeABlockingSpeechSessionFinishesStopping() {
        val firstStarted = CountDownLatch(1)
        val stopStarted = CountDownLatch(1)
        val allowStop = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val factory = WindowsSpeechSessionFactory { _, _ ->
            firstStarted.countDown()
            object : WindowsSpeechSession {
                override fun awaitCompletion(): Int {
                    Thread.sleep(5_000)
                    return 0
                }

                override fun stop() {
                    stopStarted.countDown()
                    allowStop.await(5, TimeUnit.SECONDS)
                }
            }
        }

        val model = WindowsReadAloudModel("第一段", factory)
        try {
            model.play()
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

            val pause = caller.submit { model.pause() }
            assertTrue(stopStarted.await(5, TimeUnit.SECONDS))
            pause.get(1, TimeUnit.SECONDS)
            assertEquals(WindowsReadAloudState.PAUSED, model.state)
        } finally {
            allowStop.countDown()
            model.close()
            caller.shutdownNow()
        }
    }

    private fun awaitUntil(timeoutSeconds: Long = 5, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private class ImmediateWindowsSpeechSession : WindowsSpeechSession {
        override fun awaitCompletion(): Int = 0

        override fun stop() = Unit
    }

    private class BlockingWindowsSpeechSession(
        val text: String
    ) : WindowsSpeechSession {
        private val release = CountDownLatch(1)
        @Volatile
        var stopped: Boolean = false
            private set

        override fun awaitCompletion(): Int {
            release.await(5, TimeUnit.SECONDS)
            return 0
        }

        override fun stop() {
            stopped = true
            release.countDown()
        }
    }
}
