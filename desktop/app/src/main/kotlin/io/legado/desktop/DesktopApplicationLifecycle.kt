package io.legado.desktop

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Coordinates worker shutdown before database migration and final application disposal. */
class DesktopApplicationLifecycle(
    private val stopUpdates: (Long) -> Boolean,
    private val stopDownloads: (Long) -> Boolean,
    private val resumeUpdates: () -> Unit,
    private val resumeDownloads: () -> Unit,
    private val closeUpdates: () -> Unit,
    private val closeDownloads: () -> Unit,
    private val onDisposed: () -> Unit
) {
    private val control = ReentrantLock()
    private val disposed = AtomicBoolean(false)
    private var workersStopped = false

    fun stopForMigration(timeoutMillis: Long): Boolean = control.withLock {
        if (disposed.get()) return true
        if (workersStopped) return true

        val deadline = System.nanoTime() + timeoutMillis.coerceAtLeast(0L) * 1_000_000L
        val updatesStopped = runCatching {
            stopUpdates(remainingMillis(deadline))
        }.getOrDefault(false)
        val downloadsStopped = runCatching {
            stopDownloads(remainingMillis(deadline))
        }.getOrDefault(false)
        if (updatesStopped && downloadsStopped) {
            workersStopped = true
            true
        } else {
            resumeWorkers()
            false
        }
    }

    fun close(): Boolean = control.withLock {
        if (disposed.get()) return@withLock true
        if (!workersStopped) {
            val updatesStopped = runCatching { stopUpdates(35_000L) }.getOrDefault(false)
            val downloadsStopped = runCatching { stopDownloads(35_000L) }.getOrDefault(false)
            if (!updatesStopped || !downloadsStopped) {
                resumeWorkers()
                return@withLock false
            }
        }
        disposed.set(true)
        closeDownloads()
        closeUpdates()
        onDisposed()
        true
    }

    fun resumeAfterMigration() = control.withLock {
        if (disposed.get() || !workersStopped) return@withLock
        resumeWorkers()
        workersStopped = false
    }

    private fun resumeWorkers() {
        runCatching { resumeDownloads() }
        runCatching { resumeUpdates() }
    }

    private fun remainingMillis(deadlineNanos: Long): Long =
        ((deadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
}
