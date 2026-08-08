package io.legado.desktop

import io.legado.core.library.CoreTxtTocRule
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtTocRuleModelTest {

    @Test
    fun modelAssignsSerialNumberAndCanToggleDeleteAndRefreshRules() {
        val library = InMemoryCoreLibrary()
        val model = TxtTocRuleModel(library)

        val first = model.save(
            CoreTxtTocRule(id = 1L, name = "第一条", rule = "第\\d+章")
        )
        val second = model.save(
            CoreTxtTocRule(id = 2L, name = "第二条", rule = "卷\\d+")
        )

        assertEquals(0, first.serialNumber)
        assertEquals(1, second.serialNumber)
        assertEquals(listOf(1L, 2L), model.rules.map(CoreTxtTocRule::id))

        model.setEnabled(first.id, false)
        assertFalse(model.rules.first().enable)

        model.delete(second.id)
        assertEquals(listOf(first.copy(enable = false)), model.rules)

        library.saveTxtTocRule(second.copy(serialNumber = -1))
        model.refresh()
        assertEquals(listOf(2L, 1L), model.rules.map(CoreTxtTocRule::id))
    }

    @Test
    fun modelPreservesExistingSerialNumberWhenEditingAndRejectsBlankFields() {
        val library = InMemoryCoreLibrary()
        val model = TxtTocRuleModel(library)
        val original = model.save(
            CoreTxtTocRule(id = 3L, name = "原规则", rule = "第\\d+章")
        )

        val edited = model.save(original.copy(name = "新规则", rule = "卷\\d+"))

        assertEquals(original.serialNumber, edited.serialNumber)
        assertEquals("新规则", model.rules.single().name)
        assertTrue(
            runCatching { model.save(CoreTxtTocRule(name = "", rule = "第\\d+章")) }.isFailure
        )
        assertTrue(
            runCatching { model.save(CoreTxtTocRule(name = "无规则", rule = "")) }.isFailure
        )
    }

    @Test
    fun modelImportsSingleRuleAndExportsAllRulesAsJson() {
        val library = InMemoryCoreLibrary()
        val model = TxtTocRuleModel(library)

        assertEquals(
            1,
            model.importJson(
                """{"id":9,"name":"导入规则","rule":"第\\d+章","enable":false}"""
            )
        )

        val exported = model.exportJson()
        assertTrue(exported.contains("导入规则"))
        assertEquals("第\\d+章", model.rules.single().rule)
        assertTrue(exported.contains("""第\\d+章"""))
        assertFalse(model.rules.single().enable)
    }
}
