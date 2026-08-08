package io.legado.desktop

import io.legado.core.library.CoreSourceFilterRule
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceFilterRuleModelTest {

    @Test
    fun assignsOrderTogglesDeletesAndRoundTripsJson() {
        val library = InMemoryCoreLibrary()
        val model = SourceFilterRuleModel(library)

        val saved = model.save(
            CoreSourceFilterRule(
                id = "first",
                name = "  屏蔽广告  ",
                pattern = "广告",
                fields = "NAME"
            )
        )

        assertEquals(0, saved.order)
        assertEquals("屏蔽广告", model.rules.single().name)
        model.setEnabled("first", false)
        assertEquals(false, model.rules.single().enabled)

        val imported = model.importJson(
            "{\"id\":\"second\",\"name\":\"屏蔽简介\",\"pattern\":\"广告\",\"fields\":\"INTRO\"}"
        )
        assertEquals(1, imported)
        assertEquals(2, model.rules.size)
        assertEquals(2, model.importJson(model.exportJson()))

        model.delete("first")
        assertEquals(listOf("second"), model.rules.map(CoreSourceFilterRule::id))
    }
}
