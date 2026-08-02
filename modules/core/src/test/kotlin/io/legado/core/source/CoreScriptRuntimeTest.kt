package io.legado.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoreScriptRuntimeTest {

    @Test
    fun evaluatesExpressionsAndFunctionsFromSharedLibrary() {
        val runtime = RhinoCoreScriptRuntime()

        assertEquals(
            "星河-3",
            runtime.evaluate(
                script = "format(title, index)",
                bindings = mapOf("title" to "星河", "index" to 3),
                sharedLibrary = "function format(value, number) { return value + '-' + number; }"
            )
        )
    }

    @Test
    fun blocksDirectFileAndProcessAccess() {
        val runtime = RhinoCoreScriptRuntime()

        assertThrows(CoreScriptException::class.java) {
            runtime.evaluate("java.lang.Runtime.getRuntime().exec('whoami')")
        }
    }
}
