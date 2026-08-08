package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.OnlineBookService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BookUpdateSchedulerTest {

    @Test
    fun scheduleIsSavedAndReloadedAcrossSchedulerInstances() {
        val library = libraryWithBook()
        val clock = { 1_000_000L }
        val first = scheduler(library, clock)

        first.configure(enabled = true, intervalMinutes = 60)
        first.close()

        val second = scheduler(library, clock)
        assertEquals(true, second.state.value.schedule.enabled)
        assertEquals(60, second.state.value.schedule.intervalMinutes)
        assertEquals(1_000_000L + 60 * 60_000L, second.state.value.schedule.nextRunAt)
        second.close()
    }

    @Test
    fun dueScheduleRunsOnceAndPersistsTheNextRun() {
        val library = libraryWithBook()
        var currentTime = 1_000_000L
        val scheduler = scheduler(library) { currentTime }
        scheduler.configure(enabled = true, intervalMinutes = 15)
        currentTime += 15 * 60_000L

        assertTrue(scheduler.runDueIfNeeded())
        assertEquals(BookUpdateScheduleStatus.COMPLETED, scheduler.state.value.status)
        assertEquals(2, library.book("book-1")?.totalChapterNum)
        assertFalse(scheduler.runDueIfNeeded())
        assertEquals(currentTime + 15 * 60_000L, library.updateSchedule().nextRunAt)
        scheduler.close()
    }

    @Test
    fun scheduleDoesNotRunBeforeItsNextRunTime() {
        val library = libraryWithBook()
        var currentTime = 1_000_000L
        val scheduler = scheduler(library) { currentTime }
        scheduler.configure(enabled = true, intervalMinutes = 15)

        assertFalse(scheduler.runDueIfNeeded())
        assertEquals(1, library.book("book-1")?.totalChapterNum)
        currentTime += 15 * 60_000L
        assertTrue(scheduler.runDueIfNeeded())
        scheduler.close()
    }

    @Test
    fun disablingScheduleStopsFutureRuns() {
        val library = libraryWithBook()
        var currentTime = 1_000_000L
        val scheduler = scheduler(library) { currentTime }
        scheduler.configure(enabled = true, intervalMinutes = 15)
        scheduler.configure(enabled = false, intervalMinutes = 15)
        currentTime += 60 * 60_000L

        assertFalse(scheduler.runDueIfNeeded())
        assertEquals(BookUpdateScheduleStatus.DISABLED, scheduler.state.value.status)
        assertEquals(1, library.book("book-1")?.totalChapterNum)
        scheduler.close()
    }

    @Test
    fun reloadRefreshesScheduleAfterBackupImport() {
        val library = libraryWithBook()
        val scheduler = scheduler(library) { 2_000_000L }
        library.saveUpdateSchedule(
            io.legado.core.library.CoreUpdateSchedule(
                enabled = true,
                intervalMinutes = 120,
                nextRunAt = 2_600_000L,
                lastRunAt = 1_800_000L,
                lastSummary = "备份中的状态"
            )
        )

        scheduler.reload()

        assertEquals(120, scheduler.state.value.schedule.intervalMinutes)
        assertEquals(2_600_000L, scheduler.state.value.schedule.nextRunAt)
        assertEquals("备份中的状态", scheduler.state.value.schedule.lastSummary)
        scheduler.close()
    }

    @Test
    fun startRunsDueWorkOnTheSchedulerThread() {
        val library = libraryWithBook()
        library.saveUpdateSchedule(
            io.legado.core.library.CoreUpdateSchedule(
                enabled = true,
                intervalMinutes = 15,
                nextRunAt = 0L
            )
        )
        val workerThread = AtomicReference<String>()
        val completed = CountDownLatch(1)
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "test-book-update-scheduler")
        }
        val scheduler = BookUpdateScheduler(
            library = library,
            updateModel = BookUpdateModel(
                library,
                OnlineBookService(
                    library,
                    object : CoreHttpClient {
                        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                            workerThread.set(Thread.currentThread().name)
                            completed.countDown()
                            return CoreHttpResponse(
                                url,
                                "<div class='chapter'><a href='https://source.example/chapter/1'>第一章</a></div>"
                            )
                        }
                    }
                )
            ),
            now = { 0L },
            executor = executor,
            runWorker = true
        )

        scheduler.start()
        try {
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals("test-book-update-scheduler", workerThread.get())
        } finally {
            scheduler.close()
        }
    }

    @Test
    fun stoppingForMigrationWaitsForRunningUpdateAndBlocksNewRuns() {
        val library = libraryWithBook()
        library.saveUpdateSchedule(
            io.legado.core.library.CoreUpdateSchedule(
                enabled = true,
                intervalMinutes = 15,
                nextRunAt = 0L
            )
        )
        val requestStarted = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "test-book-update-migration")
        }
        val scheduler = BookUpdateScheduler(
            library = library,
            updateModel = BookUpdateModel(
                library,
                OnlineBookService(
                    library,
                    object : CoreHttpClient {
                        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                            requestStarted.countDown()
                            while (!releaseRequest.await(50, TimeUnit.MILLISECONDS)) {
                                // Keep the request active until migration explicitly releases it.
                            }
                            return CoreHttpResponse(
                                url,
                                "<div class='chapter'><a href='https://source.example/chapter/1'>第一章</a></div>"
                            )
                        }
                    }
                )
            ),
            now = { 0L },
            executor = executor,
            runWorker = true
        )

        scheduler.start()
        try {
            assertTrue(requestStarted.await(2, TimeUnit.SECONDS))

            val stopReturned = CountDownLatch(1)
            val stopped = AtomicReference<Boolean>()
            Thread {
                stopped.set(scheduler.stopForMigration(timeoutMillis = 2_000L))
                stopReturned.countDown()
            }.start()

            assertFalse(
                "migration stop must wait for the active update",
                stopReturned.await(100, TimeUnit.MILLISECONDS)
            )
            releaseRequest.countDown()

            assertTrue(stopReturned.await(2, TimeUnit.SECONDS))
            assertEquals(true, stopped.get())
            assertFalse(scheduler.runDueIfNeeded())
        } finally {
            releaseRequest.countDown()
            scheduler.close()
        }
    }

    @Test
    fun migrationStopCanBeResumedAfterMigrationFailure() {
        val library = libraryWithBook()
        var currentTime = 1_000_000L
        val scheduler = scheduler(library) { currentTime }
        scheduler.configure(enabled = true, intervalMinutes = 15)

        assertTrue(scheduler.stopForMigration(timeoutMillis = 2_000L))
        currentTime += 15 * 60_000L
        assertFalse(scheduler.runDueIfNeeded())

        scheduler.resumeAfterMigration()

        assertTrue(scheduler.runDueIfNeeded())
        scheduler.close()
    }

    private fun scheduler(library: InMemoryCoreLibrary, now: () -> Long): BookUpdateScheduler =
        BookUpdateScheduler(
            library = library,
            updateModel = BookUpdateModel(library, service(library)),
            now = now,
            executor = Executors.newSingleThreadScheduledExecutor(),
            runWorker = false
        )

    private fun libraryWithBook(): InMemoryCoreLibrary = InMemoryCoreLibrary().apply {
        saveSource(
            CoreBookSource(
                bookSourceUrl = "https://source.example",
                bookSourceName = "测试书源",
                ruleToc = "{\"chapterList\":\".chapter\",\"chapterName\":\"a\",\"chapterUrl\":\"a@href\"}"
            )
        )
        saveBook(
            CoreBook(
                bookUrl = "book-1",
                name = "测试书",
                origin = "https://source.example",
                tocUrl = "https://source.example/book-1/toc",
                totalChapterNum = 1
            )
        )
    }

    private fun service(library: InMemoryCoreLibrary): OnlineBookService = OnlineBookService(
        library,
        object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                CoreHttpResponse(
                    url,
                    "<div class='chapter'><a href='https://source.example/chapter/1'>第一章</a></div>\n" +
                        "<div class='chapter'><a href='https://source.example/chapter/2'>第二章</a></div>"
                )
        }
    )
}
