package io.legado.desktop

import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreUpdateSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class BookUpdateScheduleStatus {
    DISABLED,
    WAITING,
    RUNNING,
    COMPLETED,
    FAILED
}

data class BookUpdateScheduleState(
    val schedule: CoreUpdateSchedule = CoreUpdateSchedule(),
    val status: BookUpdateScheduleStatus = if (schedule.enabled) {
        BookUpdateScheduleStatus.WAITING
    } else {
        BookUpdateScheduleStatus.DISABLED
    },
    val lastResults: List<BookUpdateResult> = emptyList(),
    val error: String? = null
)

class BookUpdateScheduler(
    private val library: CoreLibrary,
    private val updateModel: BookUpdateModel,
    private val now: () -> Long = System::currentTimeMillis,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "legado-book-update-scheduler").apply { isDaemon = true }
    },
    private val runWorker: Boolean = true
) : AutoCloseable {
    private val control = ReentrantLock()
    private val executionChanged = control.newCondition()
    private val stateFlow = MutableStateFlow(loadState())
    private var scheduledRun: ScheduledFuture<*>? = null
    private var started = false
    private var running = false
    private var activeTask: BookUpdateTask? = null
    private var migrationStopped = false
    private val closed = AtomicBoolean(false)

    val state: StateFlow<BookUpdateScheduleState> = stateFlow.asStateFlow()

    fun start() {
        control.withLock {
            check(!closed.get()) { "更新调度器已关闭" }
            if (started) return
            started = true
            val current = stateFlow.value
            if (current.schedule.enabled && current.schedule.nextRunAt <= 0L) {
                persist(current.schedule.copy(nextRunAt = now()))
            }
            if (runWorker) {
                scheduledRun = executor.scheduleWithFixedDelay(
                    { runDueIfNeeded() },
                    1,
                    30,
                    TimeUnit.SECONDS
                )
            }
        }
        if (runWorker) {
            executor.execute { runDueIfNeeded() }
        } else {
            runDueIfNeeded()
        }
    }

    fun configure(enabled: Boolean, intervalMinutes: Int) {
        control.withLock {
            check(!closed.get()) { "更新调度器已关闭" }
            val interval = intervalMinutes.coerceIn(15, 1440)
            val current = stateFlow.value.schedule
            val nextRunAt = if (enabled) now() + interval * 60_000L else 0L
            persist(
                current.copy(
                    enabled = enabled,
                    intervalMinutes = interval,
                    nextRunAt = nextRunAt,
                    lastSummary = if (enabled) current.lastSummary else null
                ),
                status = if (enabled) BookUpdateScheduleStatus.WAITING else BookUpdateScheduleStatus.DISABLED,
                error = null
            )
        }
    }

    fun reload() {
        control.withLock {
            check(!closed.get()) { "更新调度器已关闭" }
            val schedule = library.updateSchedule().normalized()
            val restored = if (schedule.enabled && schedule.nextRunAt <= 0L) {
                schedule.copy(nextRunAt = now())
            } else {
                schedule
            }
            if (restored != schedule) {
                library.saveUpdateSchedule(restored)
            }
            stateFlow.value = BookUpdateScheduleState(
                schedule = restored,
                status = if (restored.enabled) {
                    BookUpdateScheduleStatus.WAITING
                } else {
                    BookUpdateScheduleStatus.DISABLED
                }
            )
        }
    }

    fun runDueIfNeeded(): Boolean {
        val schedule = control.withLock {
            if (closed.get() || migrationStopped) return false
            val current = stateFlow.value
            if (
                running ||
                !current.schedule.enabled ||
                current.schedule.nextRunAt > now() ||
                updateModel.isChecking
            ) {
                return false
            }
            running = true
            val startTime = now()
            val nextRunAt = startTime + current.schedule.intervalMinutes * 60_000L
            val runningSchedule = current.schedule.copy(
                lastRunAt = startTime,
                nextRunAt = nextRunAt
            )
            persist(runningSchedule, BookUpdateScheduleStatus.RUNNING, error = null)
            activeTask = updateModel.createUpdateTask(maxRetries = 2)
            runningSchedule
        }

        return try {
            val task = control.withLock { activeTask }
                ?: error("自动更新任务未创建")
            val results = task.run()
            control.withLock {
                if (!closed.get()) {
                    persist(
                        schedule.copy(lastSummary = scheduleSummary(results)),
                        BookUpdateScheduleStatus.COMPLETED,
                        results = results,
                        error = null
                    )
                }
            }
            true
        } catch (throwable: Throwable) {
            control.withLock {
                if (!closed.get()) {
                    val message = throwable.message ?: "自动更新失败"
                    persist(
                        schedule.copy(lastSummary = message),
                        BookUpdateScheduleStatus.FAILED,
                        error = message
                    )
                }
            }
            true
        } finally {
            control.withLock {
                running = false
                activeTask = null
                executionChanged.signalAll()
            }
        }
    }

    /** Stops scheduled and active updates before a database snapshot is taken. */
    fun stopForMigration(timeoutMillis: Long = 35_000L): Boolean {
        control.withLock {
            if (closed.get()) return true
            migrationStopped = true
            scheduledRun?.cancel(false)
            scheduledRun = null
            activeTask?.cancel()

            var remainingNanos = timeoutMillis.coerceAtLeast(0L) * 1_000_000L
            while (running && remainingNanos > 0L) {
                val startedWaiting = System.nanoTime()
                try {
                    executionChanged.await(remainingNanos, TimeUnit.NANOSECONDS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
                remainingNanos -= System.nanoTime() - startedWaiting
            }
            return !running
        }
    }

    /** Re-enables scheduled updates after a migration that did not complete. */
    fun resumeAfterMigration() {
        control.withLock {
            if (closed.get()) return
            migrationStopped = false
            val current = stateFlow.value
            if (!running) {
                stateFlow.value = current.copy(
                    status = if (current.schedule.enabled) {
                        BookUpdateScheduleStatus.WAITING
                    } else {
                        BookUpdateScheduleStatus.DISABLED
                    }
                )
            }
            if (runWorker && started && scheduledRun == null) {
                scheduledRun = executor.scheduleWithFixedDelay(
                    { runDueIfNeeded() },
                    1,
                    30,
                    TimeUnit.SECONDS
                )
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        control.withLock {
            migrationStopped = true
            scheduledRun?.cancel(true)
            scheduledRun = null
            activeTask?.cancel()
        }
        executor.shutdownNow()
        runCatching { executor.awaitTermination(5, TimeUnit.SECONDS) }
    }

    private fun loadState(): BookUpdateScheduleState {
        val schedule = library.updateSchedule().normalized()
        return BookUpdateScheduleState(
            schedule = schedule,
            status = if (schedule.enabled) BookUpdateScheduleStatus.WAITING else BookUpdateScheduleStatus.DISABLED
        )
    }

    private fun persist(
        schedule: CoreUpdateSchedule,
        status: BookUpdateScheduleStatus = stateFlow.value.status,
        results: List<BookUpdateResult> = stateFlow.value.lastResults,
        error: String? = stateFlow.value.error
    ) {
        val normalized = schedule.normalized()
        library.saveUpdateSchedule(normalized)
        stateFlow.value = BookUpdateScheduleState(normalized, status, results, error)
    }

    private fun scheduleSummary(results: List<BookUpdateResult>): String {
        val updated = results.count { it.status == BookUpdateStatus.UPDATED }
        val failed = results.count { it.status == BookUpdateStatus.FAILED }
        val chapters = results.sumOf(BookUpdateResult::newChapterCount)
        return "自动更新完成：$updated 本有更新，新增 $chapters 章，失败 $failed 本"
    }
}
