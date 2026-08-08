package io.legado.desktop

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsFileAssociationRegistrationModelTest {

    @Test
    fun successfulRegistrationShowsTheLocalFileAssociationState() {
        val model = WindowsFileAssociationRegistrationModel {
            WindowsFileAssociationResult(
                status = WindowsFileAssociationStatus.REGISTERED,
                executable = Path.of("D:\\Apps\\Legado\\Legado.exe")
            )
        }

        val state = model.register()

        assertEquals(WindowsFileAssociationStatus.REGISTERED, state.status)
        assertEquals("已注册本地书籍文件关联", state.message)
        assertTrue(state.error.isNullOrBlank())
    }

    @Test
    fun failedRegistrationKeepsTheReasonAndCanBeRetried() {
        var attempts = 0
        val model = WindowsFileAssociationRegistrationModel {
            attempts++
            WindowsFileAssociationResult(
                status = WindowsFileAssociationStatus.FAILED,
                error = "reg.exe 拒绝了文件关联注册"
            )
        }

        val state = model.register()

        assertEquals(WindowsFileAssociationStatus.FAILED, state.status)
        assertEquals("reg.exe 拒绝了文件关联注册", state.error)
        assertEquals("文件关联注册失败", state.message)
        assertTrue(model.canRegister)
        assertEquals(1, attempts)
    }
}
