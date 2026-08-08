package io.legado.desktop

import io.legado.core.library.CoreDictRule
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictRuleModelTest {

    @Test
    fun modelAssignsSortNumberAndCanToggleDeleteAndRefreshRules() {
        val library = InMemoryCoreLibrary()
        val model = DictRuleModel(library, recordingClient())

        val first = model.save(
            CoreDictRule(name = "第一条", urlRule = "https://dict.example/1?key={{key}}")
        )
        val second = model.save(
            CoreDictRule(name = "第二条", urlRule = "https://dict.example/2?key={{key}}")
        )

        assertEquals(0, first.sortNumber)
        assertEquals(1, second.sortNumber)
        assertEquals(listOf("第一条", "第二条"), model.rules.map(CoreDictRule::name))

        model.setEnabled(first.name, false)
        assertFalse(model.rules.first().enabled)

        model.delete(second.name)
        assertEquals(listOf(first.copy(enabled = false)), model.rules)

        library.saveDictRule(second.copy(sortNumber = -1))
        model.refresh()
        assertEquals(listOf("第二条", "第一条"), model.rules.map(CoreDictRule::name))
    }

    @Test
    fun modelTestsOneRuleAndReturnsErrorsWithoutStoppingOtherRules() {
        val model = DictRuleModel(
            InMemoryCoreLibrary(),
            object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                    if (url.contains("broken")) {
                        CoreHttpResponse(url, "失败", statusCode = 500)
                    } else {
                        CoreHttpResponse(url, "释义：$url")
                    }
            }
        )
        val rule = CoreDictRule("正常", "https://dict.example/ok?q={{key}}")
        val broken = CoreDictRule("失败", "https://dict.example/broken?q={{key}}")

        assertEquals("释义：https://dict.example/ok?q=%E8%AF%8D", model.test(rule, "词").text)
        assertTrue(model.test(rule, "词").isSuccess)

        val results = model.testEnabled(
            listOf(rule, broken),
            "词"
        )
        assertEquals(listOf("正常", "失败"), results.map { it.rule.name })
        assertTrue(results.first().isSuccess)
        assertFalse(results.last().isSuccess)
    }

    @Test
    fun modelImportsObjectAndArrayAndExportsThemAsJson() {
        val library = InMemoryCoreLibrary()
        val model = DictRuleModel(library, recordingClient())

        assertEquals(1, model.importJson("""{"name":"对象","urlRule":"https://dict.example/{{key}}"}"""))
        assertEquals(
            2,
            model.importJson(
                """[{"name":"数组一","urlRule":"https://dict.example/1/{{key}}"},{"name":"数组二","urlRule":"https://dict.example/2/{{key}}"}]"""
            )
        )
        assertEquals(3, model.rules.size)

        val exported = model.exportJson()
        val restored = InMemoryCoreLibrary()
        val restoredModel = DictRuleModel(restored, recordingClient())
        assertEquals(3, restoredModel.importJson(exported))
        assertEquals(model.rules, restoredModel.rules)
    }

    @Test
    fun savingAnEditedRuleWithANewNameRemovesTheOldName() {
        val library = InMemoryCoreLibrary()
        val model = DictRuleModel(library, recordingClient())
        val original = model.save(
            CoreDictRule(name = "旧名称", urlRule = "https://dict.example/{{key}}")
        )
        model.save(
            CoreDictRule(name = "第二条", urlRule = "https://dict.example/second/{{key}}")
        )

        model.save(
            original.copy(name = "新名称", showRule = "@CSS:.meaning@text"),
            previousName = original.name
        )

        assertEquals(listOf("新名称", "第二条"), model.rules.map(CoreDictRule::name))
        assertEquals(null, library.dictRule("旧名称"))
        assertEquals("@CSS:.meaning@text", library.dictRule("新名称")?.showRule)
    }

    @Test
    fun savingANewRuleWithAnEmptyPreviousNameAssignsTheNextSortNumber() {
        val model = DictRuleModel(InMemoryCoreLibrary(), recordingClient())
        model.save(CoreDictRule(name = "第一条"), previousName = "")

        val second = model.save(CoreDictRule(name = "第二条"), previousName = "")

        assertEquals(1, second.sortNumber)
        assertEquals(listOf("第一条", "第二条"), model.rules.map(CoreDictRule::name))
    }

    private fun recordingClient(): CoreHttpClient = object : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
            CoreHttpResponse(url, "response")
    }
}
