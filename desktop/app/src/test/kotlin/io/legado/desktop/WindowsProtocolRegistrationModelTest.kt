package io.legado.desktop

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsProtocolRegistrationModelTest {

    @Test
    fun successfulRegistrationShowsTheRegisteredProtocols() {
        val model = WindowsProtocolRegistrationModel {
            WindowsProtocolRegistrationResult(
                status = WindowsProtocolRegistrationStatus.REGISTERED,
                executable = Path.of("D:\\Apps\\Legado\\Legado.exe")
            )
        }

        val state = model.register()

        assertEquals(WindowsProtocolRegistrationStatus.REGISTERED, state.status)
        assertEquals("已注册 yuedu:// 和 legado:// 协议", state.message)
        assertTrue(state.error.isNullOrBlank())
    }

    @Test
    fun failedRegistrationKeepsTheReasonAndCanBeRetried() {
        var attempts = 0
        val model = WindowsProtocolRegistrationModel {
            attempts++
            WindowsProtocolRegistrationResult(
                status = WindowsProtocolRegistrationStatus.FAILED,
                error = "reg.exe 拒绝了注册操作"
            )
        }

        val state = model.register()

        assertEquals(WindowsProtocolRegistrationStatus.FAILED, state.status)
        assertEquals("reg.exe 拒绝了注册操作", state.error)
        assertEquals("协议注册失败", state.message)
        assertTrue(model.canRegister)
        assertEquals(1, attempts)
    }
}
