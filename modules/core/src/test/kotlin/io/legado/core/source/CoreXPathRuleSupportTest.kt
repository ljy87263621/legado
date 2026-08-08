package io.legado.core.source

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreXPathRuleSupportTest {

    @Test
    fun combinesXPathResultsWithoutSplittingQuotedPredicateOperators() {
        val document = Jsoup.parse(
            """
                <ul id="first"><li>A</li><li>B</li></ul>
                <ul id="second"><li>C</li><li>D</li></ul>
                <div data-value="a&amp;&amp;b">保留</div>
            """.trimIndent()
        )

        val interleaved = CoreXPathRuleSupport.select(
            document,
            "//ul[@id='first']/li %% //ul[@id='second']/li"
        )
        val quotedPredicate = CoreXPathRuleSupport.select(
            document,
            "//div[@data-value='a&&b']"
        )

        assertEquals(listOf("A", "C", "B", "D"), interleaved.map(CoreXPathRuleSupport::nodeText))
        assertEquals(listOf("保留"), quotedPredicate.map(CoreXPathRuleSupport::nodeText))
    }

    @Test
    fun usesFirstNonEmptyBranchForOrCombination() {
        val document = Jsoup.parse(
            """
                <section><p>首选</p></section>
                <aside><p>备用</p></aside>
            """.trimIndent()
        )

        val result = CoreXPathRuleSupport.select(
            document,
            "//section/p || //aside/p"
        )

        assertEquals(listOf("首选"), result.map(CoreXPathRuleSupport::nodeText))
    }

    @Test
    fun invalidXPathReturnsNoNodes() {
        val document = Jsoup.parse("<div>内容</div>")

        assertEquals(emptyList<Any>(), CoreXPathRuleSupport.select(document, "@XPath://div["))
    }
}
