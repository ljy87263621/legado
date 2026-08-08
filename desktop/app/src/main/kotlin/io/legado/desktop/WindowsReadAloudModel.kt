package io.legado.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Comparator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

enum class WindowsReadAloudState {
    STOPPED,
    PLAYING,
    PAUSED
}

interface WindowsSpeechSession : AutoCloseable {
    fun awaitCompletion(): Int

    fun stop()

    override fun close() = stop()
}

fun interface WindowsSpeechSessionFactory {
    fun open(text: String, speechRate: Int): WindowsSpeechSession

    companion object {
        fun system(): WindowsSpeechSessionFactory = WindowsSpeechSessionFactory { text, speechRate ->
            WindowsPowerShellSpeechSession.start(text, speechRate)
        }
    }
}

class WindowsReadAloudModel(
    text: String,
    private val sessionFactory: WindowsSpeechSessionFactory = WindowsSpeechSessionFactory.system(),
    private val speechRate: Int = 0,
    private val executor: ExecutorService = newReadAloudExecutor()
) : AutoCloseable {

    private val lock = Any()
    private val paragraphs = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .map(String::trim)
        .filter(String::isNotBlank)

    @Volatile
    var state: WindowsReadAloudState = WindowsReadAloudState.STOPPED
        private set

    @Volatile
    var currentParagraphIndex: Int = 0
        private set

    @Volatile
    var error: String? = null
        private set

    private var activeSession: WindowsSpeechSession? = null
    private var worker: Future<*>? = null
    private var generation = 0L
    private var closed = false

    val paragraphCount: Int
        get() = paragraphs.size

    fun play() {
        synchronized(lock) {
            check(!closed) { "朗读控制器已关闭" }
            if (state == WindowsReadAloudState.PLAYING) return
            if (paragraphs.isEmpty()) {
                state = WindowsReadAloudState.STOPPED
                return
            }
            if (currentParagraphIndex !in paragraphs.indices) currentParagraphIndex = 0
            error = null
            state = WindowsReadAloudState.PLAYING
            val token = ++generation
            worker = executor.submit { speakFrom(token) }
        }
    }

    fun pause() {
        val session = synchronized(lock) {
            if (state != WindowsReadAloudState.PLAYING) return
            state = WindowsReadAloudState.PAUSED
            generation++
            activeSession
        }
        stopAsync(session)
    }

    fun stop() {
        val session = synchronized(lock) {
            state = WindowsReadAloudState.STOPPED
            generation++
            currentParagraphIndex = 0
            val current = activeSession
            activeSession = null
            current
        }
        stopAsync(session)
    }

    override fun close() {
        val session = synchronized(lock) {
            if (closed) return
            closed = true
            state = WindowsReadAloudState.STOPPED
            generation++
            activeSession.also { activeSession = null }
        }
        stopAsync(session)
        worker?.cancel(true)
        executor.shutdownNow()
    }

    private fun speakFrom(token: Long) {
        while (true) {
            val index = synchronized(lock) {
                if (closed || state != WindowsReadAloudState.PLAYING || generation != token) return
                currentParagraphIndex
            }
            val session = try {
                sessionFactory.open(paragraphs[index], speechRate)
            } catch (throwable: Throwable) {
                synchronized(lock) {
                    if (generation == token) {
                        error = throwable.message ?: "Windows 朗读启动失败"
                        state = WindowsReadAloudState.STOPPED
                    }
                }
                return
            }

            val shouldStop = synchronized(lock) {
                if (closed || state != WindowsReadAloudState.PLAYING || generation != token) {
                    true
                } else {
                    activeSession = session
                    false
                }
            }
            if (shouldStop) {
                session.stop()
                return
            }

            val exitCode = runCatching { session.awaitCompletion() }
                .getOrElse { throwable ->
                    synchronized(lock) {
                        if (generation == token) {
                            error = throwable.message ?: "Windows 朗读执行失败"
                            state = WindowsReadAloudState.STOPPED
                        }
                    }
                    -1
                }

            val continueReading = synchronized(lock) {
                if (activeSession === session) activeSession = null
                if (closed || state != WindowsReadAloudState.PLAYING || generation != token) {
                    false
                } else if (exitCode != 0) {
                    error = "Windows 朗读进程退出：$exitCode"
                    state = WindowsReadAloudState.STOPPED
                    false
                } else if (index >= paragraphs.lastIndex) {
                    currentParagraphIndex = 0
                    state = WindowsReadAloudState.STOPPED
                    false
                } else {
                    currentParagraphIndex = index + 1
                    true
                }
            }
            if (!continueReading) return
        }
    }

    private fun stopAsync(session: WindowsSpeechSession?) {
        session ?: return
        WindowsReadAloudCleanup.submit(session)
    }

    private companion object {
        fun newReadAloudExecutor(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "legado-windows-read-aloud").apply { isDaemon = true }
        }
    }
}

private object WindowsReadAloudCleanup {
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "legado-windows-read-aloud-cleanup").apply { isDaemon = true }
    }

    fun submit(session: WindowsSpeechSession) {
        executor.execute { runCatching { session.stop() } }
    }
}

private class WindowsPowerShellSpeechSession private constructor(
    private val process: Process,
    private val temporaryDirectory: Path
) : WindowsSpeechSession {

    private val cleaned = AtomicBoolean(false)

    override fun awaitCompletion(): Int = try {
        process.waitFor()
    } finally {
        cleanup()
    }

    override fun stop() {
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
        cleanup()
    }

    private fun cleanup() {
        if (!cleaned.compareAndSet(false, true)) return
        Files.walk(temporaryDirectory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    companion object {
        fun start(text: String, speechRate: Int): WindowsSpeechSession {
            check(isWindows()) { "Windows 本地朗读仅支持 Windows 系统" }
            val temporaryDirectory = Files.createTempDirectory("legado-windows-tts-")
            try {
                val textPath = temporaryDirectory.resolve("speech.txt")
                val scriptPath = temporaryDirectory.resolve("speak.ps1")
                Files.writeString(textPath, text, StandardCharsets.UTF_8)
                Files.writeString(scriptPath, SCRIPT, StandardCharsets.US_ASCII)
                val process = ProcessBuilder(
                    powerShellExecutable(),
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-WindowStyle",
                    "Hidden",
                    "-File",
                    scriptPath.toString(),
                    textPath.toString(),
                    speechRate.coerceIn(-10, 10).toString()
                ).redirectErrorStream(true).start()
                return WindowsPowerShellSpeechSession(process, temporaryDirectory)
            } catch (throwable: Throwable) {
                Files.walk(temporaryDirectory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
                throw throwable
            }
        }

        private val SCRIPT = """
param([string]${'$'}TextPath, [int]${'$'}SpeechRate)
Add-Type -AssemblyName System.Speech
${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    ${'$'}synth.Rate = [Math]::Max(-10, [Math]::Min(10, ${'$'}SpeechRate))
    ${'$'}text = [System.IO.File]::ReadAllText(${ '$' }TextPath, [System.Text.Encoding]::UTF8)
    ${'$'}synth.Speak(${ '$' }text)
} finally {
    ${'$'}synth.Dispose()
}
""".trimIndent()

        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")

        private fun powerShellExecutable(): String {
            val root = System.getenv("SystemRoot")?.takeIf(String::isNotBlank)
            val packaged = root?.let {
                Path.of(it, "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
            }
            return packaged?.takeIf(Files::isRegularFile)?.toString() ?: "powershell.exe"
        }
    }
}
