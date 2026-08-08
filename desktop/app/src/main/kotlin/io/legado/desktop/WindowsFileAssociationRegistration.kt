package io.legado.desktop

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

data class WindowsFileAssociationCommand(
    val extension: String,
    val fileTypeName: String,
    val command: String
)

enum class WindowsFileAssociationStatus {
    REGISTERED,
    FAILED,
    UNSUPPORTED
}

data class WindowsFileAssociationResult(
    val status: WindowsFileAssociationStatus,
    val executable: Path? = null,
    val error: String? = null
)

data class WindowsFileAssociationState(
    val status: WindowsFileAssociationStatus? = null,
    val message: String = "尚未检查文件关联",
    val error: String? = null,
    val executable: Path? = null
)

class WindowsFileAssociationRegistrationModel(
    private val registerAction: () -> WindowsFileAssociationResult =
        WindowsFileAssociationRegistration::registerCurrentExecutable
) {
    var state: WindowsFileAssociationState = WindowsFileAssociationState()
        private set

    val canRegister: Boolean
        get() = true

    fun register(): WindowsFileAssociationState {
        val result = runCatching { registerAction() }
            .getOrElse { error ->
                WindowsFileAssociationResult(
                    status = WindowsFileAssociationStatus.FAILED,
                    error = error.message ?: "未知错误"
                )
            }
        state = when (result.status) {
            WindowsFileAssociationStatus.REGISTERED -> WindowsFileAssociationState(
                status = result.status,
                message = "已注册本地书籍文件关联",
                executable = result.executable
            )
            WindowsFileAssociationStatus.UNSUPPORTED -> WindowsFileAssociationState(
                status = result.status,
                message = "当前环境不是 Windows，无法注册文件关联",
                error = result.error,
                executable = result.executable
            )
            WindowsFileAssociationStatus.FAILED -> WindowsFileAssociationState(
                status = result.status,
                message = "文件关联注册失败",
                error = result.error ?: "未知错误",
                executable = result.executable
            )
        }
        return state
    }
}

object WindowsFileAssociationRegistration {
    val SUPPORTED_EXTENSIONS: List<String> = listOf(
        ".txt",
        ".epub",
        ".bmp",
        ".gif",
        ".jpeg",
        ".jpg",
        ".png",
        ".webp",
        ".cbz",
        ".zip",
        ".wav",
        ".aif",
        ".aiff",
        ".au",
        ".snd"
    )

    fun isSupportedPath(argument: String): Boolean {
        val path = argument.trim()
        if (path.isEmpty() || path.startsWith("-") || path.contains("://")) return false
        val extension = path.substringAfterLast('/', path).substringAfterLast('\\', path)
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        if (".$extension" !in SUPPORTED_EXTENSIONS) return false
        return runCatching {
            val parsed = Path.of(path)
            parsed.isAbsolute || WINDOWS_ABSOLUTE_PATH.matches(path) || UNC_PATH.matches(path)
        }.getOrDefault(false)
    }

    fun registrationCommands(executable: String): List<WindowsFileAssociationCommand> {
        val path = executable.trim()
        require(path.isNotEmpty()) { "可执行文件路径不能为空" }
        val openCommand = "\"${path.replace("\"", "\\\"")}\" \"%1\""
        return SUPPORTED_EXTENSIONS.map { extension ->
            WindowsFileAssociationCommand(
                extension = extension,
                fileTypeName = "Legado${extension.replace('.', '_')}",
                command = openCommand
            )
        }
    }

    fun registerCurrentExecutable(): WindowsFileAssociationResult {
        if (!isWindows()) {
            return WindowsFileAssociationResult(WindowsFileAssociationStatus.UNSUPPORTED)
        }
        val executable = WindowsProtocolRegistration.resolveExecutable(
            processCommand = ProcessHandle.current().info().command().orElse(null),
            javaHome = System.getProperty("java.home")
        ) ?: return WindowsFileAssociationResult(
            status = WindowsFileAssociationStatus.FAILED,
            error = "无法定位 Legado.exe"
        )
        return runCatching {
            withRegistrationLock(registrationLockPath()) {
                register(registrationCommands(executable.toString()))
            }
            WindowsFileAssociationResult(
                status = WindowsFileAssociationStatus.REGISTERED,
                executable = executable
            )
        }.getOrElse { error ->
            WindowsFileAssociationResult(
                status = WindowsFileAssociationStatus.FAILED,
                executable = executable,
                error = error.message ?: "注册文件关联失败"
            )
        }
    }

    fun registryFileContent(commands: List<WindowsFileAssociationCommand>): String = buildString {
        appendLine("Windows Registry Editor Version 5.00")
        commands.forEach { command ->
            appendLine()
            val extensionKey = "HKEY_CURRENT_USER\\Software\\Classes\\${command.extension}"
            val typeKey = "HKEY_CURRENT_USER\\Software\\Classes\\${command.fileTypeName}"
            appendLine("[$extensionKey]")
            appendLine("@=\"${escapeRegistryValue(command.fileTypeName)}\"")
            appendLine()
            appendLine("[$typeKey]")
            appendLine("@=\"Legado ${command.extension} file\"")
            appendLine()
            appendLine("[$typeKey\\shell\\open\\command]")
            appendLine("@=\"${escapeRegistryValue(command.command)}\"")
        }
    }

    fun registryFileBytes(commands: List<WindowsFileAssociationCommand>): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            registryFileContent(commands).toByteArray(StandardCharsets.UTF_16LE)

    private fun register(commands: List<WindowsFileAssociationCommand>) {
        val file = Files.createTempFile("legado-file-association-", ".reg")
        try {
            Files.write(file, registryFileBytes(commands))
            val regExecutable = WindowsProtocolRegistration.resolveRegExecutable(System.getenv("SystemRoot"))
            val process = ProcessBuilder(regExecutable.toString(), "import", file.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "reg.exe import failed with exit code $exitCode$" +
                    output.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    internal fun <T> withRegistrationLock(lockPath: Path, action: () -> T): T =
        synchronized(registrationMonitor) {
            lockPath.toAbsolutePath().parent?.let(Files::createDirectories)
            FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            ).use { channel -> channel.lock().use { action() } }
        }

    private fun escapeRegistryValue(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")

    private fun registrationLockPath(): Path =
        Path.of(System.getProperty("java.io.tmpdir")).resolve("legado-file-association-registration.lock")

    private val registrationMonitor = Any()
    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[\\\\/].+")
    private val UNC_PATH = Regex("^\\\\\\\\.+")
}
