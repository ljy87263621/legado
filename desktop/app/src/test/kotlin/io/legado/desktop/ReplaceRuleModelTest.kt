package io.legado.desktop

import io.legado.core.library.CoreReplaceRule
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceRuleModelTest {

    @Test
    fun modelAssignsOrderAndCanToggleDeleteAndRefreshRules() {
        val library = InMemoryCoreLibrary()
        val model = ReplaceRuleModel(library)

        val first = model.save(
            CoreReplaceRule(id = 1L, name = "第一条", pattern = "a", replacement = "b")
        )
        val second = model.save(
            CoreReplaceRule(id = 2L, name = "第二条", pattern = "b", replacement = "c")
        )

        assertEquals(0, first.order)
        assertEquals(1, second.order)
        assertEquals(listOf(1L, 2L), model.rules.map(CoreReplaceRule::id))

        model.setEnabled(first.id, false)
        assertFalse(model.rules.first().enabled)

        model.delete(second.id)
        assertEquals(listOf(first.copy(enabled = false)), model.rules)

        library.saveReplaceRule(second.copy(order = -1))
        model.refresh()
        assertEquals(listOf(2L, 1L), model.rules.map(CoreReplaceRule::id))
    }

    @Test
    fun modelTestsOneRuleWithoutApplyingOtherStoredRules() {
        val library = InMemoryCoreLibrary()
        val model = ReplaceRuleModel(library)
        val rule = CoreReplaceRule(
            id = 3L,
            pattern = "第(\\d+)章",
            replacement = "章节$1"
        )

        val result = model.test(rule, "第12章")

        assertTrue(result.isSuccess)
        assertEquals("章节12", result.text)
        assertEquals(null, result.error)
    }

    @Test
    fun modelRejectsInvalidRuleInSingleRuleTest() {
        val result = ReplaceRuleModel(InMemoryCoreLibrary()).test(
            CoreReplaceRule(pattern = "(", replacement = "x"),
            "text"
        )

        assertFalse(result.isSuccess)
        assertEquals(null, result.text)
        assertTrue(result.error?.isNotBlank() == true)
    }
}
