package io.legado.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSourceFilterRuleTest {

    @Test
    fun parsesAndroidCompatibleFieldsAndScopes() {
        assertEquals(
            setOf(
                CoreSourceFilterRule.Field.NAME,
                CoreSourceFilterRule.Field.WORD_COUNT
            ),
            CoreSourceFilterRule.parseFields("NAME, unknown, WORD_COUNT")
        )
        assertEquals(
            CoreSourceFilterRule.Scope.All,
            CoreSourceFilterRule.parseScope("")
        )
        assertEquals(
            CoreSourceFilterRule.Scope.Source("https://source.example"),
            CoreSourceFilterRule.parseScope("示例源::https://source.example")
        )
        assertEquals(
            CoreSourceFilterRule.Scope.Groups(setOf("玄幻", "女生")),
            CoreSourceFilterRule.parseScope("玄幻,女生")
        )
    }

    @Test
    fun invalidScopeAndEmptyFieldsAreRepresentedAsNonApplicable() {
        assertEquals(CoreSourceFilterRule.Scope.None, CoreSourceFilterRule.parseScope("源::"))
        assertTrue(CoreSourceFilterRule.parseFields(" ").isEmpty())
        assertTrue(CoreSourceFilterRule.formatFields(CoreSourceFilterRule.Field.entries).contains("AUTHOR"))
    }
}
