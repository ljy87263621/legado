package io.legado.desktop

import java.util.Locale
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.charset.StandardCharsets
import java.nio.file.Files

data class WindowsProtocolRegistrationCommand(
    val scheme: String,
    val command: String
)

enum class WindowsProtocolRegistrationStatus {
    REGISTERED,
    FAILED,
    UNSUPPORTED
}

data class WindowsProtocolRegistrationResult(
    val status: WindowsProtocolRegistrationStatus,
    val executable: Path? = null,
    val error: String? = null
)

data class WindowsProtocolRegistrationState(
    val status: WindowsProtocolRegistrationStatus? = null,
    val message: String = "尚未检查协议关联",
    val error: String? = null,
    val executable: Path? = null
)

class WindowsProtocolRegistrationModel(
    private val registerAction: () -> WindowsProtocolRegistrationResult =
        WindowsProtocolRegistration::registerCurrentExecutable
) {
    var state: WindowsProtocolRegistrationState = WindowsProtocolRegistrationState()
        private set

    val canRegister: Boolean
        get() = true

    fun register(): WindowsProtocolRegistrationState {
        val result = runCatching { registerAction() }
            .getOrElse { error ->
                WindowsProtocolRegistrationResult(
                    status = WindowsProtocolRegistrationStatus.FAILED,
                    error = error.message ?: "未知错误"
                )
            }
        state = when (result.status) {
            WindowsProtocolRegistrationStatus.REGISTERED -> WindowsProtocolRegistrationState(
                status = result.status,
                message = "已注册 yuedu:// 和 legado:// 协议",
                executable = result.executable
            )
            WindowsProtocolRegistrationStatus.UNSUPPORTED -> WindowsProtocolRegistrationState(
                status = result.status,
                message = "当前环境不是 Windows，无法注册协议",
                error = result.error,
                executable = result.executable
            )
            WindowsProtocolRegistrationStatus.FAILED -> WindowsProtocolRegistrationState(
                status = result.status,
                message = "协议注册失败",
                error = result.error ?: "未知错误",
                executable = result.executable
            )
        }
        return state
    }
}

object WindowsProtocolRegistration {

    fun registrationCommands(executable: String): List<WindowsProtocolRegistrationCommand> {
        val path = executable.trim()
        require(path.isNotEmpty()) { "可执行文件路径不能为空" }
        val command = "\"${path.replace("\"", "\\\"")}\" \"%1\""
        return SUPPORTED_SCHEMES.map { scheme ->
            WindowsProtocolRegistrationCommand(scheme = scheme, command = command)
        }
    }

    fun registryFileContent(command: WindowsProtocolRegistrationCommand): String =
        registryFileContent(listOf(command))

    fun registerCurrentExecutable(): WindowsProtocolRegistrationResult {
        if (!isWindows()) {
            return WindowsProtocolRegistrationResult(
                status = WindowsProtocolRegistrationStatus.UNSUPPORTED
            )
        }
        val processCommand = ProcessHandle.current().info().command().orElse(null)
        val javaHome = System.getProperty("java.home")
        val executable = resolveExecutable(
            processCommand = processCommand,
            javaHome = javaHome
        ) ?: return WindowsProtocolRegistrationResult(
            status = WindowsProtocolRegistrationStatus.FAILED,
            error = "无法定位 Legado.exe"
        )
        return runCatching {
            withRegistrationLock(registrationLockPath()) {
                register(registrationCommands(executable.toString()))
            }
            WindowsProtocolRegistrationResult(
                status = WindowsProtocolRegistrationStatus.REGISTERED,
                executable = executable
            )
        }.getOrElse { error ->
            System.err.println("Legado protocol registration failed: ${error.message}")
            WindowsProtocolRegistrationResult(
                status = WindowsProtocolRegistrationStatus.FAILED,
                executable = executable,
                error = error.message ?: "注册协议失败"
            )
        }
    }

    internal fun resolveExecutable(processCommand: String?, javaHome: String?): Path? {
        val commandPath = processCommand
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Path::of)
        if (commandPath?.fileName?.toString()?.equals("Legado.exe", ignoreCase = true) == true) {
            return commandPath.toAbsolutePath().normalize()
        }

        val runtimeHome = javaHome
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Path::of)
        val packagedExecutable = runtimeHome
            ?.parent
            ?.resolve("Legado.exe")
            ?.toAbsolutePath()
            ?.normalize()
        return packagedExecutable?.takeIf { Files.isRegularFile(it) }
    }

    private fun register(commands: List<WindowsProtocolRegistrationCommand>) {
        val file = Files.createTempFile("legado-protocol-", ".reg")
        try {
            Files.write(file, registryFileBytes(commands))
            runReg("import", file.toString())
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
            ).use { channel ->
                channel.lock().use { action() }
            }
        }

    fun registryFileContent(commands: List<WindowsProtocolRegistrationCommand>): String =
        buildString {
            appendLine("Windows Registry Editor Version 5.00")
            commands.forEach { command ->
                appendLine()
                val key = "HKEY_CURRENT_USER\\Software\\Classes\\${command.scheme}"
                appendLine("[$key]")
                appendLine("@=\"URL:Legado ${command.scheme} link\"")
                appendLine("\"URL Protocol\"=\"\"")
                appendLine()
                appendLine("[$key\\shell\\open\\command]")
                appendLine("@=\"${escapeRegistryValue(command.command)}\"")
            }
        }

    fun registryFileBytes(commands: List<WindowsProtocolRegistrationCommand>): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            registryFileContent(commands).toByteArray(StandardCharsets.UTF_16LE)

    private fun escapeRegistryValue(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun runReg(vararg arguments: String) {
        val regExecutable = resolveRegExecutable(System.getenv("SystemRoot"))
        val process = ProcessBuilder(listOf(regExecutable.toString()) + arguments)
            .redirectErrorStream(true)
            .start()
        try {
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            val exitCode = process.waitFor()
            check(exitCode == 0) {
                "reg.exe ${arguments.joinToString(" ")} failed with exit code $exitCode${
                    output.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
                }"
            }
        } finally {
            process.destroy()
        }
    }

    internal fun resolveRegExecutable(systemRoot: String?): Path {
        val root = systemRoot
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Path::of)
        return root?.resolve("System32")?.resolve("reg.exe") ?: Path.of("reg.exe")
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")

    private fun registrationLockPath(): Path =
        Path.of(System.getProperty("java.io.tmpdir")).resolve("legado-protocol-registration.lock")

    private val registrationMonitor = Any()
    private val SUPPORTED_SCHEMES = listOf("yuedu", "legado")
}
