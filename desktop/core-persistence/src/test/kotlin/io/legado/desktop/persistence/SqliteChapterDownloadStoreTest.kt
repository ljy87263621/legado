package io.legado.desktop.persistence

import io.legado.core.library.CoreChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SqliteChapterDownloadStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun chapterDownloadTaskSurvivesLibraryReopen() {
        val database = temporaryFolder.newFile("legado.db").toPath()
        val firstChapter = chapter(0)
        val secondChapter = chapter(1)
        val task = DesktopChapterDownloadTaskRecord(
            taskId = "task-1",
            bookUrl = "book-1",
            status = "PAUSED",
            total = 2,
            completed = 1,
            skipped = 0,
            downloaded = 1,
            failed = 0,
            items = listOf(
                DesktopChapterDownloadItemRecord(
                    chapter = firstChapter,
                    status = "DOWNLOADED"
                ),
                DesktopChapterDownloadItemRecord(
                    chapter = secondChapter,
                    status = "PENDING",
                    error = "等待重试"
                )
            ),
            error = null,
            createdAt = 100L,
            updatedAt = 200L
        )

        SqliteCoreLibrary(database).use { library ->
            library.saveChapterDownloadTask(task)
        }

        SqliteCoreLibrary(database).use { library ->
            val restored = library.chapterDownloadTask("task-1")
            assertEquals(task, restored)
            assertEquals(listOf(firstChapter.url, secondChapter.url), restored?.items?.map { it.chapter.url })
            assertTrue(library.unfinishedChapterDownloadTasks().contains(task))
        }
    }

    @Test
    fun completedAndCancelledTasksAreNotReturnedAsUnfinished() {
        val database = temporaryFolder.newFile("legado.db").toPath()
        SqliteCoreLibrary(database).use { library ->
            library.saveChapterDownloadTask(task("completed", "COMPLETED"))
            library.saveChapterDownloadTask(task("cancelled", "CANCELLED"))
            library.saveChapterDownloadTask(task("running", "RUNNING"))

            assertNull(library.chapterDownloadTask("missing"))
            assertEquals(listOf("running"), library.unfinishedChapterDownloadTasks().map { it.taskId })
        }
    }

    @Test
    fun recoveryTurnsIdleAndRunningTasksIntoPausedTasks() {
        val database = temporaryFolder.newFile("legado.db").toPath()
        SqliteCoreLibrary(database).use { library ->
            library.saveChapterDownloadTask(task("idle", "IDLE"))
            library.saveChapterDownloadTask(task("running", "RUNNING"))

            val recovered = library.recoverInterruptedChapterDownloadTasks()

            assertEquals(listOf("idle", "running"), recovered.map { it.taskId })
            assertEquals(listOf("PAUSED", "PAUSED"), recovered.map { it.status })
            assertEquals(listOf("PAUSED", "PAUSED"), library.unfinishedChapterDownloadTasks().map { it.status })
        }
    }

    private fun task(taskId: String, status: String) = DesktopChapterDownloadTaskRecord(
        taskId = taskId,
        bookUrl = "book-1",
        status = status,
        total = 1,
        completed = 0,
        skipped = 0,
        downloaded = 0,
        failed = 0,
        items = listOf(DesktopChapterDownloadItemRecord(chapter(0), "PENDING")),
        error = null,
        createdAt = 100L,
        updatedAt = 100L
    )

    private fun chapter(index: Int) = CoreChapter(
        bookUrl = "book-1",
        url = "chapter-$index",
        title = "Chapter ${index + 1}",
        index = index,
        resourceUrl = "resource-$index",
        tag = "tag-$index",
        wordCount = "${index + 1}00"
    )
}
