package io.legado.desktop

import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDirectoryModelTest {

    @Test
    fun classifiesBooksourceAndGenericImportLinksSeparately() {
        val entries = SubscriptionDirectoryModel.parseHtml(
            html = """
                <div class="aui-flex">
                  <div class="aui-flex-box"><h1>书源</h1></div>
                  <a href="yuedu://booksource/importonline?src=https://example.com/booksource.json">
                    <button>一键导入</button>
                  </a>
                </div>
                <div class="aui-flex">
                  <div class="aui-flex-box"><h1>净化规则</h1></div>
                  <a href="legado://import/auto?src=https://example.com/rules.json">
                    <button>一键导入</button>
                  </a>
                </div>
            """.trimIndent(),
            baseUrl = "https://directory.example/"
        )

        assertEquals(SubscriptionEntryKind.BOOK_SOURCE, entries[0].kind)
        assertEquals("https://example.com/booksource.json", entries[0].importUrl?.let(SourceImportUrl::resolve))
        assertEquals(SubscriptionEntryKind.RESOURCE, entries[1].kind)
        assertEquals("https://example.com/rules.json", entries[1].externalUrl)
        assertFalse(entries[1].isImportable)
    }

    @Test
    fun ignoresFooterNavigationWhenParsingDirectoryResources() {
        val entries = SubscriptionDirectoryModel.parseHtml(
            html = """
                <div class="aui-flex">
                  <div class="aui-flex-box"><h1>源仓库书源</h1></div>
                  <a href="yuedu://booksource/importonline?src=https://example.com/sources.json">
                    <button>一键导入</button>
                  </a>
                </div>
                <footer>
                  <a href="https://directory.example/">首页</a>
                  <a href="https://directory.example/gx.html">阅读书源</a>
                  <a href="https://directory.example/jh.html">净化规则/TTS</a>
                </footer>
            """.trimIndent(),
            baseUrl = "https://directory.example/gx.html"
        )

        assertEquals(listOf("源仓库书源"), entries.map { it.title })
    }

    @Test
    fun ordinaryDirectoryPageIsRepresentedAsAWebPageEntry() {
        val entry = SubscriptionDirectoryModel.pageEntry(
            "http://yuedu.miaogongzi.net/gx.html",
            title = "喵公子阅读书源"
        )

        assertEquals(SubscriptionEntryKind.WEB_PAGE, entry.kind)
        assertFalse(entry.isImportable)
        assertEquals("http://yuedu.miaogongzi.net/gx.html", entry.externalUrl)
    }

    @Test
    fun parsesDirectoryCardsAndRecognizesOneClickImportLinks() {
        val entries = SubscriptionDirectoryModel.parseHtml(
            html = """
                <div class="aui-flex b-line">
                  <div class="aui-flex-box">
                    <h1>喵公子书源</h1>
                    <span><em>简洁</em><em>懒人必备</em></span>
                    <div>精选书源，定期更新</div>
                  </div>
                  <a href="yuedu://rsssource/importonline?src=https://example.com/sources.json">
                    <button>一键导入</button>
                  </a>
                </div>
            """.trimIndent(),
            baseUrl = "https://directory.example/"
        )

        val entry = entries.single()
        assertEquals("喵公子书源", entry.title)
        assertEquals(listOf("简洁", "懒人必备"), entry.tags)
        assertEquals("精选书源，定期更新", entry.description)
        assertEquals(
            "yuedu://rsssource/importonline?src=https://example.com/sources.json",
            entry.importUrl
        )
        assertTrue(entry.isImportable)
    }

    @Test
    fun resolvesRelativeJsonImportLinksAgainstDirectoryUrl() {
        val entries = SubscriptionDirectoryModel.parseHtml(
            """
                <div class="aui-flex">
                  <div class="aui-flex-box"><h1>本地书源</h1></div>
                  <a href="sources/latest.json"><button>导入</button></a>
                </div>
            """.trimIndent(),
            "https://directory.example/catalog/index.html"
        )

        assertEquals("https://directory.example/catalog/sources/latest.json", entries.single().importUrl)
    }

    @Test
    fun keepsNormalExternalLinksAsLinksWithoutMarkingThemImportable() {
        val entries = SubscriptionDirectoryModel.parseHtml(
            """
                <div class="aui-flex">
                  <div class="aui-flex-box">
                    <h1>书源管理</h1>
                    <span><em>在线工具</em></span>
                  </div>
                  <a href="http://tools.example.com"><button>点击前往</button></a>
                </div>
            """.trimIndent(),
            "https://directory.example/"
        )

        val entry = entries.single()
        assertFalse(entry.isImportable)
        assertNull(entry.importUrl)
        assertEquals("http://tools.example.com", entry.externalUrl)
    }

    @Test
    fun ordinaryExternalCardsStayWebPagesInsteadOfUpdateableResources() {
        val entry = SubscriptionDirectoryModel.parseHtml(
            """
                <div class="aui-flex">
                  <div class="aui-flex-box"><h1>B站</h1></div>
                  <a href="https://space.bilibili.com/188144093"><button>点击前往</button></a>
                </div>
            """.trimIndent(),
            "https://directory.example/"
        ).single()

        assertEquals(SubscriptionEntryKind.WEB_PAGE, entry.kind)
        assertEquals(SubscriptionResourceCategory.WEB_PAGE, entry.resourceCategory)
        assertFalse(entry.isImportable)
    }

    @Test
    fun loadFetchesAndParsesDirectoryHtml() {
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>) = CoreHttpResponse(
                url = url,
                body = "<div class='aui-flex'><div class='aui-flex-box'><h1>在线书源</h1></div></div>"
            )
        }

        val entries = SubscriptionDirectoryModel.load(client, "https://directory.example/")

        assertEquals(listOf("在线书源"), entries.map { it.title })
    }

    @Test
    fun pageParsingExposesMetadataAndSeparatesBookSourcesFromRules() {
        val page = SubscriptionDirectoryModel.parsePage(
            html = """
                <html>
                  <head>
                    <title>阅读书源</title>
                    <link rel="icon" href="/favicon.ico">
                  </head>
                  <body>
                    <div class="aui-flex">
                      <div class="aui-flex-box"><h1>源仓库书源</h1></div>
                      <a href="yuedu://rsssource/importonline?src=https://example.com/sources.json">
                        <button>一键导入</button>
                      </a>
                    </div>
                    <div class="aui-flex">
                      <div class="aui-flex-box"><h1>净化规则</h1></div>
                      <a href="legado://import/auto?src=https://example.com/rules.json">
                        <button>一键导入</button>
                      </a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
            baseUrl = "https://directory.example/gx.html"
        )

        assertEquals("阅读书源", page.title)
        assertEquals("https://directory.example/favicon.ico", page.iconUrl)
        assertEquals(
            listOf(SubscriptionResourceCategory.BOOK_SOURCE, SubscriptionResourceCategory.RULE),
            page.entries.map { it.resourceCategory }
        )
    }

    @Test
    fun pageParsingKeepsFooterNavigationAsSeparateSubscriptionPageLinks() {
        val page = SubscriptionDirectoryModel.parsePage(
            html = """
                <html>
                  <head><title>阅读书源</title></head>
                  <body>
                    <div class="aui-flex">
                      <div class="aui-flex-box"><h1>源仓库书源</h1></div>
                      <a href="yuedu://booksource/importonline?src=https://example.com/sources.json">
                        <button>一键导入</button>
                      </a>
                    </div>
                    <footer>
                      <a href="https://directory.example/">首页</a>
                      <a href="https://directory.example/gx.html">阅读书源</a>
                      <a href="https://directory.example/jh.html">净化规则/TTS</a>
                    </footer>
                  </body>
                </html>
            """.trimIndent(),
            baseUrl = "https://directory.example/gx.html"
        )

        assertEquals(
            listOf("首页", "阅读书源", "净化规则/TTS"),
            page.navigationEntries.map { it.title }
        )
        assertEquals(
            listOf(
                "https://directory.example/",
                "https://directory.example/gx.html",
                "https://directory.example/jh.html"
            ),
            page.navigationEntries.map { it.externalUrl }
        )
        assertEquals(
            listOf(SubscriptionEntryKind.WEB_PAGE, SubscriptionEntryKind.WEB_PAGE, SubscriptionEntryKind.WEB_PAGE),
            page.navigationEntries.map { it.kind }
        )
        assertEquals(
            listOf(false, false, false),
            page.navigationEntries.map { it.isImportable }
        )
    }

    @Test
    fun directRuleJsonLinksAreListedAsRulesWithoutBookSourceImportAction() {
        val entry = SubscriptionDirectoryModel.parseHtml(
            html = """
                <div class="aui-flex">
                  <div class="aui-flex-box"><h1>净化规则</h1></div>
                  <a href="https://example.com/rules.json"><button>下载</button></a>
                </div>
            """.trimIndent(),
            baseUrl = "https://directory.example/"
        ).single()

        assertEquals(SubscriptionResourceCategory.RULE, entry.resourceCategory)
        assertFalse(entry.isImportable)
        assertEquals("https://example.com/rules.json", entry.externalUrl)
    }

    @Test
    fun ruleNamedHttpJsonImportLinksAreNotPresentedAsBookSourceImports() {
        val entry = SubscriptionDirectoryModel.parseHtml(
            html = """
                <div class="aui-flex">
                  <div class="aui-flex-box"><h1>净化规则</h1></div>
                  <a href="https://example.com/rules.json"><button>一键导入</button></a>
                </div>
            """.trimIndent(),
            baseUrl = "https://directory.example/"
        ).single()

        assertEquals(SubscriptionResourceCategory.RULE, entry.resourceCategory)
        assertFalse(entry.isImportable)
        assertEquals("https://example.com/rules.json", entry.externalUrl)
    }

    @Test
    fun parsesMiaogongziStyleBooksourcePageIntoBookSourcesAndNavigation() {
        val page = SubscriptionDirectoryModel.parsePage(
            html = """
                <html>
                  <head><title>阅读书源</title></head>
                  <body>
                    <section class="aui-scrollView">
                      <div class="aui-flex b-line">
                        <div class="aui-flex-box">
                          <h1>源仓库书源</h1>
                          <span><em>2025年11月25日更新</em><em>463个</em></span>
                        </div>
                        <a href="yuedu://booksource/importonline?src=https://source.example/latest.json">
                          <div class="aui-film-button"><button>一键导入</button></div>
                        </a>
                      </div>
                    </section>
                    <footer class="aui-footer aui-footer-fixed">
                      <a href="/"><span class="aui-tabBar-item-text">首页</span></a>
                      <a href="/gx.html"><span class="aui-tabBar-item-text">阅读书源</span></a>
                      <a href="/jh.html"><span class="aui-tabBar-item-text">净化规则/TTS</span></a>
                    </footer>
                  </body>
                </html>
            """.trimIndent(),
            baseUrl = "http://yuedu.miaogongzi.net/gx.html"
        )

        assertEquals(listOf("源仓库书源"), page.entries.map { it.title })
        assertEquals(
            listOf(SubscriptionResourceCategory.BOOK_SOURCE),
            page.entries.map { it.resourceCategory }
        )
        assertEquals(
            "yuedu://booksource/importonline?src=https://source.example/latest.json",
            page.entries.single().importUrl
        )
        assertTrue(page.entries.single().isImportable)
        assertEquals(listOf("首页", "阅读书源", "净化规则/TTS"), page.navigationEntries.map { it.title })
        assertEquals(
            listOf(
                "http://yuedu.miaogongzi.net/",
                "http://yuedu.miaogongzi.net/gx.html",
                "http://yuedu.miaogongzi.net/jh.html"
            ),
            page.navigationEntries.map { it.externalUrl }
        )
    }

    @Test
    fun parsesMiaogongziStyleRulesAndTtsAsSeparateNonBookSourceResources() {
        val entries = SubscriptionDirectoryModel.parseHtml(
            html = """
                <div class="aui-flex b-line">
                  <div class="aui-flex-box">
                    <h1>乌云净化</h1>
                    <span><em>26个</em></span>
                  </div>
                  <a href="legado://import/auto?src=http://source.example/rules.json">
                    <div class="aui-film-button"><button>一键导入</button></div>
                  </a>
                </div>
                <div class="aui-flex b-line">
                  <div class="aui-flex-box">
                    <h1>TTS引擎合集</h1>
                    <span><em>10个</em></span>
                  </div>
                  <a href="legado://import/auto?src=http://source.example/tts.json">
                    <div class="aui-film-button"><button>一键导入</button></div>
                  </a>
                </div>
            """.trimIndent(),
            baseUrl = "http://yuedu.miaogongzi.net/jh.html"
        )

        assertEquals(
            listOf(SubscriptionResourceCategory.RULE, SubscriptionResourceCategory.TTS),
            entries.map { it.resourceCategory }
        )
        assertEquals(listOf(false, false), entries.map { it.isImportable })
        assertEquals(
            listOf("http://source.example/rules.json", "http://source.example/tts.json"),
            entries.map { it.externalUrl }
        )
    }

    @Test
    fun separatesMiaogongziResourcesFromOrdinaryWebPagesAndNotices() {
        val page = SubscriptionDirectoryModel.parsePage(
            html = """
                <html>
                  <head><title>阅读书源</title></head>
                  <body>
                    <div class="aui-flex b-line">
                      <div class="aui-flex-box">
                        <h1>公告</h1>
                        <div>书源收集自网络，仅支持3.0版本</div>
                      </div>
                    </div>
                    <div class="aui-flex b-line">
                      <div class="aui-flex-box"><h1>B站</h1></div>
                      <a href="https://space.example/bilibili"><button>点击前往</button></a>
                    </div>
                    <div class="aui-flex b-line">
                      <div class="aui-flex-box"><h1>源仓库书源</h1></div>
                      <a href="yuedu://booksource/importonline?src=https://source.example/sources.json">
                        <button>一键导入</button>
                      </a>
                    </div>
                    <div class="aui-flex b-line">
                      <div class="aui-flex-box"><h1>乌云净化</h1></div>
                      <a href="legado://import/auto?src=https://source.example/rules.json">
                        <button>一键导入</button>
                      </a>
                    </div>
                    <footer>
                      <a href="/">首页</a>
                      <a href="/gx.html">阅读书源</a>
                      <a href="/jh.html">净化规则/TTS</a>
                    </footer>
                  </body>
                </html>
            """.trimIndent(),
            baseUrl = "http://directory.example/gx.html"
        )

        val sections = page.resourceSections()
        assertEquals(listOf("源仓库书源"), sections.bookSources.map { it.title })
        assertEquals(listOf("乌云净化"), sections.rules.map { it.title })
        assertEquals(listOf("B站"), sections.webPages.map { it.title })
        assertTrue(sections.resources.isEmpty())
        assertEquals(listOf("首页", "阅读书源", "净化规则/TTS"), page.navigationEntries.map { it.title })
    }

    @Test
    fun resolvesAppleTouchIconAndOpenGraphImageMetadata() {
        val appleIconPage = SubscriptionDirectoryModel.parsePage(
            html = """
                <html><head>
                  <title>带图标订阅页</title>
                  <link rel="apple-touch-icon" href="/touch-icon.png">
                </head><body></body></html>
            """.trimIndent(),
            baseUrl = "https://directory.example/catalog/page.html"
        )
        val openGraphPage = SubscriptionDirectoryModel.parsePage(
            html = """
                <html><head>
                  <title>OpenGraph 订阅页</title>
                  <meta property="og:image" content="/preview.png">
                </head><body></body></html>
            """.trimIndent(),
            baseUrl = "https://directory.example/catalog/page.html"
        )

        assertEquals("https://directory.example/touch-icon.png", appleIconPage.iconUrl)
        assertEquals("https://directory.example/preview.png", openGraphPage.iconUrl)
    }
}
