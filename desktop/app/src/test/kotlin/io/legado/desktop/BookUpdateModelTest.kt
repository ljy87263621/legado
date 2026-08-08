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
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BookUpdateModelTest {

    @Test
    fun checkingBooksReportsNewChapterCountAndPersistsUpdatedBook() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 1))
        val model = BookUpdateModel(library, service(library) { _, _ ->
            CoreHttpResponse(
                "https://source.example/book/1/toc",
                chaptersHtml("第一章", "第二章")
            )
        })

        val result = model.checkAll().single()

        assertEquals(BookUpdateStatus.UPDATED, result.status)
        assertEquals(1, result.newChapterCount)
        assertEquals(2, library.book("book-1")?.totalChapterNum)
        assertEquals("第二章", library.book("book-1")?.latestChapterTitle)
        assertFalse(model.isChecking)
    }

    @Test
    fun checkingAnUnchangedBookReportsNoUpdate() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 2))
        val model = BookUpdateModel(library, service(library) { _, _ ->
            CoreHttpResponse(
                "https://source.example/book/1/toc",
                chaptersHtml("第一章", "第二章")
            )
        })

        val result = model.checkAll().single()

        assertEquals(BookUpdateStatus.NO_UPDATE, result.status)
        assertEquals(0, result.newChapterCount)
    }

    @Test
    fun oneBookFailureDoesNotPreventOtherBooksFromBeingChecked() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 1))
        library.saveBook(book("book-2", totalChapterNum = 1))
        val model = BookUpdateModel(library, service(library) { url, _ ->
            if (url.contains("book-1")) error("网络不可用")
            CoreHttpResponse(url, chaptersHtml("第一章", "第二章"))
        })

        val results = model.checkAll()

        assertEquals(
            listOf(BookUpdateStatus.FAILED, BookUpdateStatus.UPDATED),
            results.map(BookUpdateResult::status)
        )
        assertEquals("网络不可用", results.first().error)
        assertEquals(2, library.book("book-2")?.totalChapterNum)
    }

    @Test
    fun localDisabledAndNonUpdatableBooksAreExcludedFromAllChecks() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("online", totalChapterNum = 1))
        library.saveBook(book("local", origin = "local", totalChapterNum = 1))
        library.saveBook(book("disabled", totalChapterNum = 1).copy(canUpdate = false))
        library.saveBook(book("disabled-source", totalChapterNum = 1).copy(origin = "https://disabled.example"))
        library.saveSource(source().copy(enabled = false, bookSourceUrl = "https://disabled.example"))
        val requested = mutableListOf<String>()
        val model = BookUpdateModel(library, service(library) { url, _ ->
            requested += url
            CoreHttpResponse(url, chaptersHtml("第一章", "第二章"))
        })

        val results = model.checkAll()

        assertEquals(listOf("online"), results.map { it.book.bookUrl })
        assertTrue(requested.all { it.contains("source.example") })
    }

    @Test
    fun updateTaskRetriesFailedBookAndReportsAttemptProgress() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 1))
        val attempts = AtomicInteger(0)
        val model = BookUpdateModel(library, service(library) { url, _ ->
            if (attempts.incrementAndGet() < 3) error("暂时不可用")
            CoreHttpResponse(url, chaptersHtml("第一章", "第二章"))
        })

        val task = model.createUpdateTask(maxRetries = 2)
        val results = task.run()

        assertEquals(BookUpdateStatus.UPDATED, results.single().status)
        assertEquals(3, attempts.get())
        assertEquals(3, task.state.value.items.single().attempt)
        assertEquals(BookUpdateTaskStatus.COMPLETED, task.state.value.status)
    }

    @Test
    fun updateTaskKeepsFailedResultAfterRetriesAreExhausted() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 1))
        val attempts = AtomicInteger(0)
        val model = BookUpdateModel(library, service(library) { _, _ ->
            attempts.incrementAndGet()
            error("持续不可用")
        })

        val task = model.createUpdateTask(maxRetries = 2)
        val results = task.run()

        assertEquals(BookUpdateStatus.FAILED, results.single().status)
        assertEquals(3, attempts.get())
        assertEquals(BookUpdateItemStatus.FAILED, task.state.value.items.single().status)
        assertEquals(BookUpdateTaskStatus.COMPLETED, task.state.value.status)
    }

    @Test
    fun updateTaskPublishesQueueProgressWhileCurrentBookIsRunning() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 1))
        library.saveBook(book("book-2", totalChapterNum = 1))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val model = BookUpdateModel(library, service(library) { url, _ ->
            started.countDown()
            check(release.await(2, TimeUnit.SECONDS)) { "测试等待超时" }
            CoreHttpResponse(url, chaptersHtml("第一章", "第二章"))
        })
        val task = model.createUpdateTask(maxRetries = 0)
        val worker = Thread { task.run() }

        worker.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertEquals(BookUpdateTaskStatus.RUNNING, task.state.value.status)
        assertEquals(2, task.state.value.total)
        assertEquals(0, task.state.value.completed)
        assertEquals("book-1", task.state.value.currentBook?.bookUrl)
        assertEquals(1, task.state.value.currentAttempt)

        release.countDown()
        worker.join(2_000)
        assertFalse(worker.isAlive)
        assertEquals(2, task.state.value.completed)
    }

    @Test
    fun pausingUpdateTaskPreventsStartingNextBookUntilResumed() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 1))
        library.saveBook(book("book-2", totalChapterNum = 1))
        val requested = mutableListOf<String>()
        lateinit var task: BookUpdateTask
        val pausedAfterFirst = CountDownLatch(1)
        val model = BookUpdateModel(library, service(library) { url, _ ->
            synchronized(requested) { requested += url }
            if (url.contains("book-1")) {
                task.pause()
                pausedAfterFirst.countDown()
            }
            CoreHttpResponse(url, chaptersHtml("第一章", "第二章"))
        })
        task = model.createUpdateTask(maxRetries = 0)
        val worker = Thread { task.run() }

        worker.start()
        assertTrue(pausedAfterFirst.await(2, TimeUnit.SECONDS))
        Thread.sleep(100)
        synchronized(requested) { assertEquals(1, requested.size) }
        assertEquals(BookUpdateTaskStatus.PAUSED, task.state.value.status)

        task.resume()
        worker.join(2_000)
        assertFalse(worker.isAlive)
        synchronized(requested) { assertEquals(2, requested.size) }
        assertEquals(BookUpdateTaskStatus.COMPLETED, task.state.value.status)
    }

    @Test
    fun cancellingUpdateTaskStopsRemainingBooksAndKeepsCompletedResults() {
        val library = InMemoryCoreLibrary()
        library.saveSource(source())
        library.saveBook(book("book-1", totalChapterNum = 1))
        library.saveBook(book("book-2", totalChapterNum = 1))
        val requested = mutableListOf<String>()
        lateinit var task: BookUpdateTask
        val cancelledAfterFirst = CountDownLatch(1)
        val model = BookUpdateModel(library, service(library) { url, _ ->
            synchronized(requested) { requested += url }
            if (url.contains("book-1")) {
                task.cancel()
                cancelledAfterFirst.countDown()
            }
            CoreHttpResponse(url, chaptersHtml("第一章", "第二章"))
        })
        task = model.createUpdateTask(maxRetries = 0)
        val worker = Thread { task.run() }

        worker.start()
        assertTrue(cancelledAfterFirst.await(2, TimeUnit.SECONDS))
        worker.join(2_000)
        assertFalse(worker.isAlive)
        synchronized(requested) { assertEquals(1, requested.size) }
        assertEquals(1, task.state.value.results.size)
        assertEquals(BookUpdateTaskStatus.CANCELLED, task.state.value.status)
    }

    private fun book(
        url: String,
        origin: String = "https://source.example",
        totalChapterNum: Int
    ) = CoreBook(
        bookUrl = url,
        name = url,
        origin = origin,
        tocUrl = "https://source.example/$url/toc",
        totalChapterNum = totalChapterNum
    )

    private fun source(bookSourceUrl: String = "https://source.example") = CoreBookSource(
        bookSourceUrl = bookSourceUrl,
        bookSourceName = "测试书源",
        ruleToc = "{\"chapterList\":\".chapter\",\"chapterName\":\"a\",\"chapterUrl\":\"a@href\"}"
    )

    private fun service(
        library: InMemoryCoreLibrary,
        response: (String, Map<String, String>) -> CoreHttpResponse
    ): OnlineBookService = OnlineBookService(
        library,
        object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = response(url, headers)
        }
    )

    private fun chaptersHtml(vararg titles: String): String = titles.mapIndexed { index, title ->
        "<div class='chapter'><a href='https://source.example/chapter/$index'>$title</a></div>"
    }.joinToString("\n")
}
