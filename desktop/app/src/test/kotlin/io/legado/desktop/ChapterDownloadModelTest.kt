package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.OnlineBookService
import io.legado.desktop.persistence.DesktopChapterDownloadItemRecord
import io.legado.desktop.persistence.DesktopChapterDownloadStore
import io.legado.desktop.persistence.DesktopChapterDownloadTaskRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ChapterDownloadModelTest {

    @Test
    fun shutdownWaitsForAnActiveDownloadWorkerBeforeReturning() {
        val library = InMemoryCoreLibrary()
        val source = source()
        val book = book(source)
        val chapter = chapter(book, 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val requestStarted = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        val model = ChapterDownloadModel(library, service(library) { url ->
            requestStarted.countDown()
            releaseRequest.await(2, TimeUnit.SECONDS)
            "content for $url"
        })
        val task = model.createTask(book, listOf(chapter))
        val worker = Thread { task.run() }
        val shutdownReturned = CountDownLatch(1)
        val shutdownWorker = Thread {
            model.shutdown()
            shutdownReturned.countDown()
        }

        worker.start()
        assertTrue(requestStarted.await(2, TimeUnit.SECONDS))
        shutdownWorker.start()
        try {
            assertFalse("shutdown must wait for an active download", shutdownReturned.await(100, TimeUnit.MILLISECONDS))
        } finally {
            releaseRequest.countDown()
            worker.join(2_000)
            shutdownWorker.join(2_000)
        }
    }

    @Test
    fun shutdownStopsAWorkerPausedBetweenChapters() {
        val library = InMemoryCoreLibrary()
        val source = source()
        val book = book(source)
        val first = chapter(book, 0)
        val second = chapter(book, 1)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(first)
        library.saveChapter(second)
        lateinit var task: ChapterDownloadTask
        val firstRequested = CountDownLatch(1)
        val model = ChapterDownloadModel(library, service(library) { url ->
            if (url == first.url) {
                task.pause()
                firstRequested.countDown()
            }
            "content for $url"
        })
        task = model.createTask(book, listOf(first, second))
        val worker = Thread { task.run() }

        worker.start()
        assertTrue(firstRequested.await(2, TimeUnit.SECONDS))
        model.shutdown()
        worker.join(2_000)
        val stoppedByShutdown = !worker.isAlive
        if (!stoppedByShutdown) {
            task.resume()
            worker.join(2_000)
        }

        assertTrue("shutdown should stop the download worker", stoppedByShutdown)
        assertEquals(ChapterDownloadTaskStatus.PAUSED, task.state.value.status)
    }

    @Test
    fun restoredPausedTaskOnlyDownloadsChaptersThatWereNotCompleted() {
        val library = InMemoryCoreLibrary()
        val store = InMemoryChapterDownloadStore()
        val source = source()
        val book = book(source)
        val completed = chapter(book, 0)
        val pending = chapter(book, 1)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(completed)
        library.saveChapter(pending)
        library.saveContent(completed, "already downloaded")
        store.saveChapterDownloadTask(
            DesktopChapterDownloadTaskRecord(
                taskId = "task-restore",
                bookUrl = book.bookUrl,
                status = ChapterDownloadTaskStatus.PAUSED.name,
                total = 2,
                completed = 1,
                skipped = 0,
                downloaded = 1,
                failed = 0,
                items = listOf(
                    DesktopChapterDownloadItemRecord(completed, ChapterDownloadItemStatus.DOWNLOADED.name),
                    DesktopChapterDownloadItemRecord(pending, ChapterDownloadItemStatus.PENDING.name)
                ),
                error = null,
                createdAt = 100L,
                updatedAt = 200L
            )
        )
        val requested = mutableListOf<String>()
        val model = ChapterDownloadModel(library, service(library) { url ->
            requested += url
            "content for $url"
        }, store)

        val task = model.taskForBook(book)
        assertNotNull(task)
        task!!.run()

        assertEquals(listOf(pending.url), requested)
        assertEquals(
            listOf(ChapterDownloadItemStatus.DOWNLOADED, ChapterDownloadItemStatus.DOWNLOADED),
            task.state.value.items.map(ChapterDownloadItemState::status)
        )
        assertEquals(2, task.state.value.completed)
        assertEquals(ChapterDownloadTaskStatus.COMPLETED, task.state.value.status)
        assertEquals(ChapterDownloadTaskStatus.COMPLETED.name, store.chapterDownloadTask("task-restore")?.status)
    }

    @Test
    fun restoredPausedTaskRetriesAChapterThatPreviouslyFailed() {
        val library = InMemoryCoreLibrary()
        val store = InMemoryChapterDownloadStore()
        val source = source()
        val book = book(source)
        val failed = chapter(book, 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(failed)
        store.saveChapterDownloadTask(
            DesktopChapterDownloadTaskRecord(
                taskId = "task-retry",
                bookUrl = book.bookUrl,
                status = ChapterDownloadTaskStatus.PAUSED.name,
                total = 1,
                completed = 1,
                skipped = 0,
                downloaded = 0,
                failed = 1,
                items = listOf(
                    DesktopChapterDownloadItemRecord(
                        chapter = failed,
                        status = ChapterDownloadItemStatus.FAILED.name,
                        error = "temporary failure"
                    )
                ),
                error = null,
                createdAt = 100L,
                updatedAt = 200L
            )
        )
        val requested = mutableListOf<String>()
        val model = ChapterDownloadModel(library, service(library) { url ->
            requested += url
            "content for $url"
        }, store)

        val task = model.taskForBook(book)
        assertNotNull(task)
        task!!.run()

        assertEquals(listOf(failed.url), requested)
        assertEquals(ChapterDownloadItemStatus.DOWNLOADED, task.state.value.items.single().status)
        assertEquals(0, task.state.value.failed)
        assertEquals(ChapterDownloadTaskStatus.COMPLETED, task.state.value.status)
    }

    @Test
    fun cachedChaptersAreSkippedAndMissingChaptersAreDownloaded() {
        val library = InMemoryCoreLibrary()
        val source = source()
        val book = book(source)
        val cached = chapter(book, 0)
        val missing = chapter(book, 1)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(cached)
        library.saveChapter(missing)
        library.saveContent(cached, "cached")
        val requested = mutableListOf<String>()
        val model = ChapterDownloadModel(library, service(library) { url ->
            requested += url
            "content for $url"
        })

        val task = model.createTask(book, listOf(cached, missing))
        task.run()

        assertEquals(
            listOf(ChapterDownloadItemStatus.CACHED, ChapterDownloadItemStatus.DOWNLOADED),
            task.state.value.items.map(ChapterDownloadItemState::status)
        )
        assertEquals(2, task.state.value.completed)
        assertEquals(1, task.state.value.skipped)
        assertEquals(1, task.state.value.downloaded)
        assertEquals(listOf(missing.url), requested)
        assertEquals("content for ${missing.url}", library.content(missing))
        assertEquals(ChapterDownloadTaskStatus.COMPLETED, task.state.value.status)
    }

    @Test
    fun failedChapterRemainsInTheCompletedTaskWithItsError() {
        val library = InMemoryCoreLibrary()
        val source = source()
        val book = book(source)
        val chapter = chapter(book, 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val model = ChapterDownloadModel(library, service(library) { error("network unavailable") })

        val task = model.createTask(book, listOf(chapter))
        task.run()

        val item = task.state.value.items.single()
        assertEquals(ChapterDownloadItemStatus.FAILED, item.status)
        assertEquals("network unavailable", item.error)
        assertEquals(1, task.state.value.completed)
        assertEquals(1, task.state.value.failed)
        assertEquals(ChapterDownloadTaskStatus.COMPLETED, task.state.value.status)
    }

    @Test
    fun pausingDownloadTaskPreventsStartingTheNextChapterUntilResumed() {
        val library = InMemoryCoreLibrary()
        val source = source()
        val book = book(source)
        val first = chapter(book, 0)
        val second = chapter(book, 1)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(first)
        library.saveChapter(second)
        lateinit var task: ChapterDownloadTask
        val firstRequested = CountDownLatch(1)
        val requested = mutableListOf<String>()
        val model = ChapterDownloadModel(library, service(library) { url ->
            synchronized(requested) { requested += url }
            if (url == first.url) {
                task.pause()
                firstRequested.countDown()
            }
            "content for $url"
        })
        task = model.createTask(book, listOf(first, second))
        val worker = Thread { task.run() }

        worker.start()
        assertTrue(firstRequested.await(2, TimeUnit.SECONDS))
        Thread.sleep(100)
        synchronized(requested) { assertEquals(listOf(first.url), requested) }
        assertEquals(ChapterDownloadTaskStatus.PAUSED, task.state.value.status)

        task.resume()
        worker.join(2_000)

        assertFalse(worker.isAlive)
        synchronized(requested) { assertEquals(listOf(first.url, second.url), requested) }
        assertEquals(ChapterDownloadTaskStatus.COMPLETED, task.state.value.status)
        assertEquals(2, task.state.value.completed)
    }

    @Test
    fun cancellingDownloadTaskStopsRemainingChaptersAndKeepsCompletedResults() {
        val library = InMemoryCoreLibrary()
        val source = source()
        val book = book(source)
        val first = chapter(book, 0)
        val second = chapter(book, 1)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(first)
        library.saveChapter(second)
        lateinit var task: ChapterDownloadTask
        val firstRequested = CountDownLatch(1)
        val requested = mutableListOf<String>()
        val model = ChapterDownloadModel(library, service(library) { url ->
            synchronized(requested) { requested += url }
            if (url == first.url) {
                task.cancel()
                firstRequested.countDown()
            }
            "content for $url"
        })
        task = model.createTask(book, listOf(first, second))
        val worker = Thread { task.run() }

        worker.start()
        assertTrue(firstRequested.await(2, TimeUnit.SECONDS))
        worker.join(2_000)

        assertFalse(worker.isAlive)
        synchronized(requested) { assertEquals(listOf(first.url), requested) }
        assertEquals(ChapterDownloadItemStatus.DOWNLOADED, task.state.value.items.first().status)
        assertEquals(ChapterDownloadItemStatus.CANCELLED, task.state.value.items[1].status)
        assertEquals(1, task.state.value.completed)
        assertEquals(ChapterDownloadTaskStatus.CANCELLED, task.state.value.status)
    }

    @Test
    fun stoppingForMigrationWaitsForDownloadAndResumeRestartsIt() {
        val library = InMemoryCoreLibrary()
        val source = source()
        val book = book(source)
        val chapter = chapter(book, 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val requestStarted = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        val model = ChapterDownloadModel(library, service(library) {
            requestStarted.countDown()
            while (!releaseRequest.await(50, TimeUnit.MILLISECONDS)) {
                // Keep the request active until migration explicitly releases it.
            }
            "content for ${chapter.url}"
        })
        val task = model.createTask(book, listOf(chapter))
        val worker = Thread(task::run)
        worker.start()

        assertTrue(requestStarted.await(2, TimeUnit.SECONDS))
        val stopReturned = CountDownLatch(1)
        val stopped = AtomicReference<Boolean>()
        Thread {
            stopped.set(model.stopForMigration(timeoutMillis = 2_000L))
            stopReturned.countDown()
        }.start()

        assertFalse(stopReturned.await(100, TimeUnit.MILLISECONDS))
        releaseRequest.countDown()
        assertTrue(stopReturned.await(2, TimeUnit.SECONDS))
        assertEquals(true, stopped.get())
        worker.join(2_000)
        assertFalse(worker.isAlive)
        assertEquals(ChapterDownloadTaskStatus.PAUSED, task.state.value.status)

        model.resumeAfterMigration()
        val resumeDeadline = System.nanoTime() + 2_000_000_000L
        while (task.state.value.status != ChapterDownloadTaskStatus.COMPLETED &&
            System.nanoTime() < resumeDeadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(ChapterDownloadTaskStatus.COMPLETED, task.state.value.status)
    }

    private fun book(source: CoreBookSource) = CoreBook(
        bookUrl = "${source.bookSourceUrl}/book/1",
        name = "Test Book",
        origin = source.bookSourceUrl
    )

    private fun chapter(book: CoreBook, index: Int) = CoreChapter(
        bookUrl = book.bookUrl,
        url = "${book.bookUrl}/chapter/$index",
        title = "Chapter ${index + 1}",
        index = index
    )

    private fun source() = CoreBookSource(
        bookSourceUrl = "https://source.example",
        bookSourceName = "Test Source",
        ruleContent = "{\"content\":\".content\"}"
    )

    private fun service(
        library: InMemoryCoreLibrary,
        content: (String) -> String
    ): OnlineBookService = OnlineBookService(
        library,
        object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                CoreHttpResponse(url, "<div class='content'>${content(url)}</div>")
        }
    )

    private class InMemoryChapterDownloadStore : DesktopChapterDownloadStore {
        private val records = linkedMapOf<String, DesktopChapterDownloadTaskRecord>()

        override fun saveChapterDownloadTask(task: DesktopChapterDownloadTaskRecord) {
            records[task.taskId] = task
        }

        override fun chapterDownloadTask(taskId: String): DesktopChapterDownloadTaskRecord? = records[taskId]

        override fun unfinishedChapterDownloadTasks(): List<DesktopChapterDownloadTaskRecord> = records.values
            .filter { it.status in setOf("IDLE", "RUNNING", "PAUSED") }

        override fun recoverInterruptedChapterDownloadTasks(): List<DesktopChapterDownloadTaskRecord> =
            unfinishedChapterDownloadTasks()
    }
}
