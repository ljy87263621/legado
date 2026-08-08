package io.legado.desktop.persistence

import com.google.gson.Gson
import io.legado.core.library.CoreChapter

data class DesktopChapterDownloadItemRecord(
    val chapter: CoreChapter,
    val status: String,
    val error: String? = null
)

data class DesktopChapterDownloadTaskRecord(
    val taskId: String,
    val bookUrl: String,
    val status: String,
    val total: Int,
    val completed: Int,
    val skipped: Int,
    val downloaded: Int,
    val failed: Int,
    val items: List<DesktopChapterDownloadItemRecord>,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

interface DesktopChapterDownloadStore {
    fun saveChapterDownloadTask(task: DesktopChapterDownloadTaskRecord)

    fun chapterDownloadTask(taskId: String): DesktopChapterDownloadTaskRecord?

    fun unfinishedChapterDownloadTasks(): List<DesktopChapterDownloadTaskRecord>

    /** Converts a task interrupted by process exit into a resumable paused task. */
    fun recoverInterruptedChapterDownloadTasks(): List<DesktopChapterDownloadTaskRecord>
}

internal class ChapterDownloadRecordCodec(
    private val gson: Gson = Gson()
) {
    fun encode(items: List<DesktopChapterDownloadItemRecord>): String = gson.toJson(items)

    fun decode(json: String): List<DesktopChapterDownloadItemRecord> = gson.fromJson(
        json,
        Array<DesktopChapterDownloadItemRecord>::class.java
    )?.toList() ?: emptyList()
}
