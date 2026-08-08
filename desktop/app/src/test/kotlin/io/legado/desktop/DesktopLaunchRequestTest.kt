package io.legado.desktop

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopLaunchRequestTest {

    @Test
    fun startupArgumentsTurnOnlineImportUriIntoAnImportRequest() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf(
                "yuedu://rsssource/importonline?src=http%3A%2F%2Fsource.example%2Fsources.json"
            )
        )

        assertEquals("http://source.example/sources.json", request?.onlineImportUrl)
    }

    @Test
    fun legadoSchemeUsesTheSameOnlineImportContract() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf(
                "legado://rsssource/importonline?src=https%3A%2F%2Fsource.example%2Fsources.json"
            )
        )

        assertEquals("https://source.example/sources.json", request?.onlineImportUrl)
    }

    @Test
    fun booksourceProtocolIsAcceptedAsAnOnlineImportRequest() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf(
                "yuedu://booksource/importonline?src=https%3A%2F%2Fsource.example%2Fsources.json"
            )
        )

        assertEquals("https://source.example/sources.json", request?.onlineImportUrl)
    }

    @Test
    fun ordinaryWebPageLaunchArgumentBecomesAWebPageRequest() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf("http://yuedu.miaogongzi.net/gx.html")
        )

        assertEquals("http://yuedu.miaogongzi.net/gx.html", request?.subscriptionPageUrl)
        assertNull(request?.onlineImportUrl)
    }

    @Test
    fun startupWithoutARecognizedUriDoesNotCreateAnImportRequest() {
        assertNull(DesktopLaunchRequest.fromArgs(arrayOf("--other-option")))
    }

    @Test
    fun startupValidationArgumentCreatesAnAutomaticExitRequest() {
        val request = DesktopLaunchRequest.fromArgs(arrayOf("--validate-startup"))

        assertTrue(request?.startupValidation == true)
    }

    @Test
    fun portableArgumentCreatesAPortableStartupRequest() {
        val request = DesktopLaunchRequest.fromArgs(arrayOf("--portable"))

        assertTrue(request?.portable == true)
    }

    @Test
    fun dataDirectoryArgumentIsCapturedForStartup() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf("--data-dir", "D:\\LegadoData")
        )

        assertEquals("D:\\LegadoData", request?.dataDirectory)
    }

    @Test
    fun equalsDataDirectoryArgumentIsCapturedForStartup() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf("--data-dir=D:\\LegadoData")
        )

        assertEquals("D:\\LegadoData", request?.dataDirectory)
    }

    @Test
    fun windowsFileAssociationArgumentBecomesALocalFileImportRequest() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf("D:\\Books\\story.epub")
        )

        assertEquals("D:\\Books\\story.epub", request?.localFilePath)
        assertNull(request?.onlineImportUrl)
        assertNull(request?.subscriptionPageUrl)
    }

    @Test
    fun unsupportedFileAssociationArgumentIsIgnored() {
        assertNull(
            DesktopLaunchRequest.fromArgs(
                arrayOf("D:\\Books\\story.pdf")
            )
        )
    }

    @Test
    fun supportedFileAssociationMatchingIsCaseInsensitiveAndRejectsUrls() {
        assertTrue(WindowsFileAssociationRegistration.isSupportedPath("C:\\Books\\STORY.TXT"))
        assertTrue(WindowsFileAssociationRegistration.isSupportedPath("\\\\server\\share\\comic.CBZ"))
        assertFalse(WindowsFileAssociationRegistration.isSupportedPath("https://example.com/story.txt"))
        assertFalse(WindowsFileAssociationRegistration.isSupportedPath("story.txt"))
    }

    @Test
    fun dataDirectoryValueIsNotMistakenForAFileImport() {
        val request = DesktopLaunchRequest.fromArgs(
            arrayOf("--data-dir", "D:\\Data\\library.txt")
        )

        assertEquals("D:\\Data\\library.txt", request?.dataDirectory)
        assertNull(request?.localFilePath)
    }

    @Test
    fun protocolRegistrationCommandsPointBothSupportedSchemesToTheExecutable() {
        val commands = WindowsProtocolRegistration.registrationCommands(
            executable = "D:\\Apps\\Legado\\Legado.exe"
        )

        assertEquals(
            listOf("yuedu", "legado"),
            commands.map { it.scheme }
        )
        assertEquals(
            "\"D:\\Apps\\Legado\\Legado.exe\" \"%1\"",
            commands.first().command
        )
    }

    @Test
    fun registryFileEscapesQuotesAndBackslashesForWindowsImport() {
        assertEquals(
            listOf(
                "Windows Registry Editor Version 5.00",
                "",
                "[HKEY_CURRENT_USER\\Software\\Classes\\yuedu]",
                "@=\"URL:Legado yuedu link\"",
                "\"URL Protocol\"=\"\"",
                "",
                "[HKEY_CURRENT_USER\\Software\\Classes\\yuedu\\shell\\open\\command]",
                "@=\"\\\"D:\\\\Apps\\\\Legado\\\\Legado.exe\\\" \\\"%1\\\"\""
            ),
            WindowsProtocolRegistration.registryFileContent(
                WindowsProtocolRegistration.registrationCommands(
                    "D:\\Apps\\Legado\\Legado.exe"
                ).first()
            ).trimEnd().lines()
        )
    }

    @Test
    fun registryFileContainsBothSupportedSchemes() {
        val content = WindowsProtocolRegistration.registryFileContent(
            WindowsProtocolRegistration.registrationCommands(
                "D:\\Apps\\Legado\\Legado.exe"
            )
        )

        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\yuedu]"))
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\legado]"))
        assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\legado\\shell\\open\\command]"))
    }

    @Test
    fun registryFileBytesUseUtf16LittleEndianWithBom() {
        val content = WindowsProtocolRegistration.registryFileContent(
            WindowsProtocolRegistration.registrationCommands(
                "D:\\Apps\\Legado\\Legado.exe"
            )
        )

        val bytes = WindowsProtocolRegistration.registryFileBytes(
            WindowsProtocolRegistration.registrationCommands(
                "D:\\Apps\\Legado\\Legado.exe"
            )
        )

        assertEquals(listOf(0xFF.toByte(), 0xFE.toByte()), bytes.take(2))
        assertEquals(content, bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE))
    }

    @Test
    fun registrationLockSerializesConcurrentRegistrationsInOneProcess() {
        val lockPath = Files.createTempDirectory("legado-registration-test-").resolve("registration.lock")
        val executor = Executors.newFixedThreadPool(2)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val activeRegistrations = AtomicInteger(0)
        val overlapped = AtomicBoolean(false)

        try {
            val first = executor.submit {
                WindowsProtocolRegistration.withRegistrationLock(lockPath) {
                    activeRegistrations.incrementAndGet()
                    firstEntered.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                    activeRegistrations.decrementAndGet()
                }
            }

            assertTrue(firstEntered.await(5, TimeUnit.SECONDS))
            val second = executor.submit {
                WindowsProtocolRegistration.withRegistrationLock(lockPath) {
                    if (activeRegistrations.incrementAndGet() > 1) {
                        overlapped.set(true)
                    }
                    secondEntered.countDown()
                    activeRegistrations.decrementAndGet()
                }
            }

            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertFalse(overlapped.get())
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
            Files.deleteIfExists(lockPath)
            Files.deleteIfExists(lockPath.parent)
        }
    }

    @Test
    fun packagedRuntimeResolvesTheInstalledExecutableWhenProcessCommandIsJava() {
        val appDirectory = Files.createTempDirectory("legado-packaged-app-")
        val runtimeDirectory = Files.createDirectories(appDirectory.resolve("runtime"))
        val executable = Files.createFile(appDirectory.resolve("Legado.exe"))

        try {
            assertEquals(
                executable,
                WindowsProtocolRegistration.resolveExecutable(
                    processCommand = runtimeDirectory.resolve("bin").resolve("java.exe").toString(),
                    javaHome = runtimeDirectory.toString()
                )
            )
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(runtimeDirectory)
            Files.deleteIfExists(appDirectory)
        }
    }

    @Test
    fun registryImportUsesTheWindowsSystemRegExecutable() {
        assertEquals(
            java.nio.file.Path.of("C:\\Windows\\System32\\reg.exe"),
            WindowsProtocolRegistration.resolveRegExecutable("C:\\Windows")
        )
    }

    @Test
    fun fileAssociationRegistryContainsEveryImporterExtensionAndOpenCommand() {
        val executable = "D:\\Apps\\Legado\\Legado.exe"
        val commands = WindowsFileAssociationRegistration.registrationCommands(executable)
        val content = WindowsFileAssociationRegistration.registryFileContent(commands)

        assertEquals(WindowsFileAssociationRegistration.SUPPORTED_EXTENSIONS, commands.map { it.extension })
        commands.forEach { command ->
            assertTrue(content.contains("[HKEY_CURRENT_USER\\Software\\Classes\\${command.extension}]"))
            assertTrue(content.contains("${command.fileTypeName}\\shell\\open\\command"))
            val escapedCommand = command.command.replace("\\", "\\\\").replace("\"", "\\\"")
            assertTrue(content.contains("@=\"$escapedCommand\""))
        }
    }
}
