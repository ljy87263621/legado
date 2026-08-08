package io.legado.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopApplicationLifecycleTest {

    @Test
    fun failedMigrationStopResumesBothWorkerGroups() {
        val events = mutableListOf<String>()
        val lifecycle = lifecycle(
            events = events,
            stopDownloads = { _ ->
                events += "stop-downloads"
                false
            }
        )

        assertFalse(lifecycle.stopForMigration(timeoutMillis = 2_000L))
        assertEquals(
            listOf(
                "stop-updates",
                "stop-downloads",
                "resume-downloads",
                "resume-updates"
            ),
            events
        )
    }

    @Test
    fun updateStopExceptionStillStopsAndRestoresBothWorkerGroups() {
        val events = mutableListOf<String>()
        val lifecycle = lifecycle(
            events = events,
            stopUpdates = {
                error("update worker stop failed")
            }
        )

        assertFalse(lifecycle.stopForMigration(timeoutMillis = 2_000L))
        assertEquals(
            listOf(
                "stop-updates",
                "stop-downloads",
                "resume-downloads",
                "resume-updates"
            ),
            events
        )
    }

    @Test
    fun downloadStopExceptionRestoresBothWorkerGroups() {
        val events = mutableListOf<String>()
        val lifecycle = lifecycle(
            events = events,
            stopDownloads = {
                events += "stop-downloads"
                error("download worker stop failed")
            }
        )

        assertFalse(lifecycle.stopForMigration(timeoutMillis = 2_000L))
        assertEquals(
            listOf(
                "stop-updates",
                "stop-downloads",
                "resume-downloads",
                "resume-updates"
            ),
            events
        )
    }

    @Test
    fun successfulMigrationStopIsKeptUntilIdempotentClose() {
        val events = mutableListOf<String>()
        var disposed = 0
        val lifecycle = lifecycle(
            events = events,
            onDisposed = { disposed++ }
        )

        assertTrue(lifecycle.stopForMigration(timeoutMillis = 2_000L))
        lifecycle.close()
        lifecycle.close()

        assertEquals(
            listOf(
                "stop-updates",
                "stop-downloads",
                "close-downloads",
                "close-updates"
            ),
            events
        )
        assertEquals(1, disposed)
    }

    @Test
    fun migrationFailureAfterWorkersStoppedResumesBothWorkerGroups() {
        val events = mutableListOf<String>()
        val lifecycle = lifecycle(events)

        assertTrue(lifecycle.stopForMigration(timeoutMillis = 2_000L))
        lifecycle.resumeAfterMigration()

        assertEquals(
            listOf(
                "stop-updates",
                "stop-downloads",
                "resume-downloads",
                "resume-updates"
            ),
            events
        )
    }

    @Test
    fun closeRefusesDisposalWhenWorkersCannotStop() {
        val events = mutableListOf<String>()
        var disposed = 0
        val lifecycle = lifecycle(
            events = events,
            stopDownloads = { _ ->
                events += "stop-downloads"
                false
            },
            onDisposed = { disposed++ }
        )

        assertFalse(lifecycle.close())
        assertEquals(0, disposed)
        assertEquals(
            listOf(
                "stop-updates",
                "stop-downloads",
                "resume-downloads",
                "resume-updates"
            ),
            events
        )
    }

    private fun lifecycle(
        events: MutableList<String>,
        stopUpdates: (() -> Boolean)? = null,
        stopDownloads: ((Long) -> Boolean)? = null,
        onDisposed: () -> Unit = {}
    ): DesktopApplicationLifecycle = DesktopApplicationLifecycle(
        stopUpdates = { _ ->
            events += "stop-updates"
            stopUpdates?.invoke() ?: true
        },
        stopDownloads = stopDownloads ?: { _ ->
            events += "stop-downloads"
            true
        },
        resumeUpdates = { events += "resume-updates" },
        resumeDownloads = { events += "resume-downloads" },
        closeUpdates = { events += "close-updates" },
        closeDownloads = { events += "close-downloads" },
        onDisposed = onDisposed
    )
}
