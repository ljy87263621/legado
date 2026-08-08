# Legado Windows Reader Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the coding-change-closed-loop workflow and execute each task with a fresh verification cycle.

**Goal:** 将 Legado Android 阅读器迁移为可在原生 Windows 上运行的桌面阅读器，同时保留手机端的书架、书源、规则解析、阅读、导入导出、订阅、朗读、备份同步、脚本和设置能力。

**Architecture:** 采用 Kotlin/JVM 业务核心、Compose Multiplatform Windows UI 和独立的 Windows 平台适配层。现有 Android `app` 继续作为兼容基线；迁移出来的纯业务代码进入共享模块，Windows 端通过文件系统、SQLite、HTTP、WebView2、媒体/TTS 和任务调度适配器提供平台能力。现有 Vue Web 模块先作为书源编辑和调试工具复用，并通过统一的本地 API/桥接层接入 Windows 核心，最终逐步扩展为完整阅读界面。

**Tech Stack:** Kotlin/JVM, Compose Multiplatform Desktop, SQLite/JDBC, OkHttp, Jsoup, Rhino-compatible JavaScript runtime, WebView2 bridge, existing Vue 3/TypeScript web module, Gradle Kotlin DSL/Groovy build, Windows portable distribution.

## Global Constraints

- Windows 分发默认采用 Portable 目录，不能把 MSIX/AppX 注册作为唯一运行方式。
- 现有 Android 功能和用户数据格式是兼容基线，不能因 Windows UI 迁移删除功能。
- 默认使用系统 Python；文档处理默认使用 LibreOffice；新增开发工具安装在 `D:\Develope`。
- 不覆盖用户已有工作树改动；每个迁移阶段必须有可重复的构建或测试验证。
- 共享核心必须与 Android UI、`Activity`、`Context`、Android `WebView` 和 Android `Service` 解耦。

## Current Baseline

- Android 主模块位于 `app`，包含约 847 个 `io/legado/app` 源文件、Room 数据库、规则解析、书籍模型、阅读器、服务和 Android UI。
- `modules:rhino` 是现有脚本引擎适配模块。
- `modules:web` 是 Vue 3 管理端，当前包含书架、章节和书源/RSS 编辑页面；它依赖 Android `HttpServer` 与 `WebSocketServer`，不能单独提供完整桌面功能。
- Android 依赖包括 AndroidX/Material/Room/WebView/Media3/Glide/NanoHTTPD/Cronet 等，不能直接作为 Windows JVM UI 依赖。
- 本机当前有 JDK 21 和 Gradle 8.13；没有可用的 .NET SDK，因此本计划不把 WinUI 作为首要实现路径。

## Migration Gates

1. Core gate: Windows JVM 可以读写兼容的 Legado 数据库/备份，并通过测试加载书架、书源和章节。
2. Network gate: Windows 可以执行 HTTP、Cookie、代理、重试、WebView 验证和书源规则解析。
3. Reader gate: Windows 阅读器支持文本、HTML、图片/漫画、目录、进度、书签、替换规则、主题和键盘操作。
4. Feature gate: 搜索、发现、RSS、导入导出、更新下载、朗读/TTS、脚本、同步、设置和调试工具完成迁移。
5. Distribution gate: 生成无需预装 Java/Node/开发环境的 Windows Portable 目录，并在干净用户目录启动验证。

## Verified Current Increment

阅读记录现在提供桌面 GUI 的月度热力图和 Markdown 导出动作。热力图按 Android 兼容的 `yyyyMMdd` day key 聚合指定月份，负时长钳制为零，使用正确的月份天数和周一首列布局，按 12 小时封顶映射 Android 兼容的 0-5 级，并支持上/下月导航（不超过当前月）、本月总时长和点击日期查看累计时长；网格高度按实际行数计算，31 天月份不会被裁剪。`ReadRecordHeatmapModelTest` 覆盖月份过滤、时长聚合、负时长钳制、等级映射和 31 天网格布局约束。Markdown 导出仍以 UTF-8 写出总览、按书汇总和会话明细，并保留 day key 与 epoch seconds。该 Windows 子集只覆盖桌面 `CoreLibrary` 当前记录，不实现 Android 的长按日期删除、按日期筛选阅读列表、原生 Header/RecyclerView 交互、Markdown 导入/同步、WebDAV 阅读记录上传、生命周期聚合、通知或完整统计语义；Windows 使用 Compose/SQLite/CoreLibrary，Android 使用 Room、`ReadRecordActivity` 和原生 `MonthHeatMapView`。

当前 Windows 分支已验证一组可独立运行的阅读器能力：滚动与固定容量分页、章节前后导航、阅读位置恢复、方向键/空格/Ctrl+方向键/S 键命令、自动阅读的页内和章节推进，以及 1-120 秒自动阅读间隔设置。阅读设置已写入 SQLite，旧 `desktop_settings` 表会自动补列，旧版 JSON 备份缺少自动阅读间隔时保留默认值 10 秒。书签页面支持搜索、摘录、删除和恢复到章节正文位置；阅读记录页面支持按书汇总、时长/书籍/会话统计、搜索、排序、打开、删除、月度热力图和 UTF-8 Markdown 导出。热力图 Windows 子集按 `yyyyMMdd` 聚合月份、使用周一首列和闰年月份长度、执行负时长钳制与 Android 兼容 0-5 级/12 小时封顶、支持当前月以前的月份导航和日期时长查看；模型测试覆盖这些规则以及 31 天月份的网格高度约束。Android 的长按日期删除、按日期筛选、原生 Header/RecyclerView 交互、Markdown 导入/同步、WebDAV 阅读记录上传、生命周期聚合、通知和完整统计仍未实现。替换规则已具备核心规则模型、按标题/正文和书籍范围执行、超时保护、SQLite 持久化、备份恢复、单条测试以及桌面规则管理入口。TXT 导入已支持 Android 目录规则模型、启用规则筛选、正则匹配数量选择、匹配间距过滤、前言合并、无匹配正文兜底、SQLite 持久化和备份恢复，并随桌面包携带完整默认规则集；TXT 规则现在还有桌面增删改、启用切换、JSON 导入导出和设置入口，本地章节保存原始字节范围，缓存缺失时可按范围延迟读取；超大文件性能和 Android 全部拆章细节仍未实现。字典查词现在具备可测试的 Windows 子集：字典规则可在桌面管理，查词支持 `{{key}}` URL 替换、原始响应、共享 URL options 的 `method`、`body`、`charset`、`headers` 子集、CSS/JSONPath/基础正则提取、启用规则排序、逐条错误保留，以及 CSS 选择器多命中结果按换行合并；完整 Android `AnalyzeRule` 组合语义、复杂动态 URL、嵌入 JavaScript、完整 XPath/JSON/JS `init` 和 WebView/browser 能力仍未实现。Windows 阅读页已接入基于 PowerShell `System.Speech.Synthesis.SpeechSynthesizer` 的系统朗读控制，支持播放、暂停、继续、停止、换章清理和错误显示；HTTP TTS、多引擎配置和 Android 完整朗读语义仍未实现。书籍详情现在可搜索并按同名同作者筛选来源，保留当前来源，切换前加载新书详情和目录，成功后保留书架分组，失败时回滚临时替代书；该功能通过模型测试并接入桌面详情页。书籍发现现在支持发现书源筛选与选择、`exploreUrl`、`ruleExplore`、静态分类选项、刷新、页码加载、下一页追加去重、加入书架和进入详情；动态脚本、来源排名和完整 Android explore/review 语义仍未迁移。章节详情页支持勾选章节、下载未缓存章节或选中章节，显示缓存/成功/失败结果，并提供持久化任务的进度、暂停、继续和取消控制；应用启动会把遗留的 `IDLE/RUNNING` 任务恢复为 `PAUSED`，详情页重新打开同一本书时可继续未完成章节，失败章节会重新尝试，退出时会等待下载 worker 收尾。`modules:core`、`desktop:core-persistence` 和 `desktop:app` 测试通过，Compose Desktop `packageExe` 生成了 `desktop/app/build/compose/binaries/main/exe/Legado-0.1.1.exe`。新增 `desktop/packaging/windows/portable.ps1` 可构建并检查目录版，验证 `runtime/bin/server/jvm.dll`、JAR 和 jpackage 元数据，并在临时 `LEGADO_DATA_DIR` 中启动后等待 `legado.db` 创建；`test-portable.ps1` 覆盖不完整目录拒绝和完整目录通过。

这只是迁移增量，不代表 Android 功能完整迁移。登录 UI、WebView/CAPTCHA、Android WebView Cookie 共享、完整脚本扩展、下载更新、媒体阅读器、HTTP TTS、多引擎朗读配置、WebDAV 全量同步、替换规则和 TXT 目录规则的 Android 完整语义、字典的完整 Android `AnalyzeRule` 语义、干净用户配置升级和签名发布仍需后续工作；桌面书源变量、source-aware HTTP、持久 Cookie、会话 Cookie 和 `enabledCookieJar` 子集已补齐，并在本地 ZIP 备份中保存持久变量与 Cookie（会话 Cookie 不导出）。`yuedu://` 和 `legado://` 的 Windows 当前用户协议关联子集，以及支持的本地书籍、图片、漫画和音频文件扩展名关联子集已补齐，但不等同于 Android intent/activity/SAF/Provider 语义。TXT 桌面管理和本地字节范围读取、字典查词和本地音频阅读 Windows 子集已补齐，但超大文件性能、Android 全部拆章细节、字典高级规则语义和 Android 音频媒体语义仍未完成。章节下载任务已经支持 SQLite 持久化、退出恢复、重新打开书籍后续传，以及本地 ZIP 备份/恢复；WebDAV 目前只覆盖远程 ZIP 备份/恢复，不覆盖进度/图片同步、后台同步、远程书库或冲突解决；Android 原生下载记录兼容和后台通知仍未完成。

本阶段还完成了书源编辑器中的请求检查子集：可异步执行一次解析后的 HTTP 请求，默认 GET，并支持共享 URL options 的 `method`、`body`、`charset`、`headers` 子集；同时解析当前书源动态 `header` 和 source variables，按书源复用持久/会话 Cookie，接受 JSON 或逐行 `name: value` 手工 headers，并展示请求元数据、最终 URL、状态码、响应 headers、响应正文和传输/非 2xx 错误。该能力由 `SourceDebugModelTest`、`CoreSourceHttpClientTest` 和运行时数据测试覆盖；管理桥另提供受限的 `/bookSourceDebug` WebSocket 请求日志。整体仍不宣称 Android 完整 source debug：逐规则 trace、登录 UI、WebView/WebView2 验证、浏览器会话共享和完整动态脚本调试仍是后续差异。

本阶段还实现了书源编辑器的外部浏览器验证启动子集：模型对验证地址执行 HTTP(S)、主机名和长度校验，再通过可注入 launcher 打开系统默认浏览器；GUI 提供对应的“在浏览器中打开验证”动作。它不等同于 Android `WebViewActivity`：不嵌入 WebView2、不接收浏览器结果、不共享 Cookie/登录状态、不执行 CAPTCHA 回调，也不实现 `getVerificationResult` 或 `refetchAfterSuccess`。

本阶段还补齐了本地图片/CBZ 阅读的 Windows 子集：桌面文件导入支持单张图片和 `.cbz`/`.zip`，会过滤受支持的图片条目、自然排序、按目录生成章节、将根目录图片归入 `正文`，并保存不含伪造正文内容的图片章节；重复导入会保留已有阅读进度。阅读路由只把 `loc_book` 图片书送入本地图片阅读器，避免把在线图片书 URL 当作本地路径解析。GUI 支持图片显示、章节/文件名和全局页码、上一页/下一页、上一章/下一章、50%-300% 缩放、重置缩放、保存阅读位置，以及左/右/空格/Ctrl+方向键/S 键操作。网络漫画源、完整图片规则、WebView/WebView2、长条/双页模式、触控手势、滤镜、预取/缓存和 WebDAV/远程图片库仍未迁移。

本阶段还扩展了本地 HTTP/WebSocket 管理桥接的 Windows 子集：设置页可配置 HTTP 端口并启动/停止服务，显示并复制 HTTP 与 WebSocket 两个 loopback 地址，同时列出当前支持的路由；HTTP 服务使用端口 `N`，WebSocket 服务使用 `N + 1`，两者只绑定 `127.0.0.1`。HTTP 使用 Android `ReturnData` 兼容的 `isSuccess`、`errorMsg`、`data` JSON 包，提供 `/health`、`/getBookshelf`、`/getBookSources`、`/getGroups`、兼容 `bookUrl`/`url` 参数的 `/getChapterList`、`/getBookContent?url=...&index=...` 和 `/getReadConfig`；WebSocket 提供 `/searchBook` 和 `/bookSourceDebug`，前者返回扁平的 Android/Web 兼容 `SeachBook` 数组，后者输出书源、请求 URL、请求 method、请求 headers、请求 body、状态码、响应 headers、响应正文和错误等桌面可提供的调试日志后关闭连接。正文端点只读取桌面 `CoreLibrary` 中已经缓存的章节正文，不触发在线抓取；章节或正文缺失时返回错误。`POST /saveBook` 新增接受 Android-compatible 的完整 `CoreBook` JSON，要求非空白 `bookUrl` 后通过 `CoreLibrary.saveBook` 替换或写入书籍；桌面实现不执行 Android `AppWebDav.uploadBookProgress` 副作用。`POST /saveBookProgress` 接受 Android `BookProgress` JSON（`name`、`author`、`durChapterIndex`、`durChapterPos`、`durChapterTime`、可选 `durChapterTitle`），按 `name + author` 匹配已有桌面书籍，并通过 `CoreLibrary.saveBook` 保存四个阅读进度字段。另一个受限写接口 `POST /saveReadConfig` 仅接受 `modules:web` 的 `webReadConfig` schema，校验必填字段和范围后以规范化 JSON 保存；`CoreLibrary` 通过 SQLite 的独立 `desktop_web_read_config` 单行表持久化，因此配置可跨数据库重开和管理服务重启保留，但 HTTP 端点只在本地服务运行期间可用。该能力的 GUI 边界仍是设置页的管理服务区：端口、启动/停止、HTTP/WebSocket endpoint 复制和支持路由披露均可见。服务与模型分别由真实 loopback 请求、WebSocket 协议/搜索响应测试、SQLite/CoreLibrary 重启测试以及端口/生命周期/设置路由披露测试覆盖，包含完整书籍成功保存、畸形 JSON、空白 `bookUrl`、缺少/越界字段、未知书籍和未支持 POST 路由拒绝。`/bookSourceDebug` 只是桌面调试日志子集，不等价于 Android 完整 WebView/XPath/JS/CAPTCHA 调试状态机；其他 POST/导入写接口、静态 web 资源、认证、局域网暴露、图片/封面、完整 Android `Book.ReadConfig`/`ReadBookConfig` 语义、替换规则接口、在线正文抓取、Android WebDAV 上传/同步副作用、WebView/WebView2 以及 Android 服务通知行为仍未迁移。

本阶段还完成了本地音频阅读的 Windows 子集：桌面导入器识别单个本地 `.wav`、`.aif`、`.aiff`、`.au` 或 `.snd` 文件，将其保存为兼容 Android 概念的本地音频书和一个可播放章节；音频阅读页提供播放/暂停、停止、章节选择、前后章节、保存进度和键盘操作。`AudioReaderModel` 通过可注入的播放会话边界进行测试，生产环境使用 JDK `javax.sound.sampled` Java Sound；换章和退出阅读页会释放当前播放会话，进度通过 `CoreLibrary` 保存。该切片不承诺 MP3/M4A，实际可解码编码取决于 JDK 的 Java Sound provider；Android Media3/ExoPlayer、远程音频、后台播放服务、系统媒体通知、音频焦点、媒体按键、Wake Lock、睡眠定时器、网络 Cookie 和音频缓存仍未迁移。

本阶段新增了欢迎页和首次运行设置的 Windows 子集。启动路由由 `DesktopStartupRoute` 决定：未完成设置的新 SQLite 数据目录进入 `欢迎` 页面，点击“开始使用”后由 `WelcomeModel` 写入 `desktop_setup` 完成标记并进入书架；关闭并重开数据库后标记仍然有效。没有该标记但已有书籍的旧数据库会按已初始化处理，外部订阅导入启动请求仍直接进入订阅页。`AppRouteTest` 覆盖路由和完成动作，`SqliteCoreLibraryTest` 覆盖首次状态、重启持久化和旧书架兼容；Compose GUI 真实挂载 `WelcomeScreen`，Portable 启动验证作为最终可执行证据。该切片只确认 Windows 原生壳已被用户初始化，不迁移 Android 欢迎页中的偏好、权限提示、默认书源初始化或服务设置。

本阶段还完成了 WebDAV 远程备份/恢复的 Windows 子集。设置页提供 URL、用户名、密码、远端文件名、刷新远端列表、上传本地 ZIP、选择远端备份和恢复动作；配置保存到 SQLite 的单行 `desktop_webdav_config` 表，重新打开数据库后仍可加载。实现使用 Java `HttpClient` 执行带超时且禁用重定向的 `PROPFIND`、`PUT` 和 `GET`，凭据使用 Basic Authentication；DAV XML 解析关闭外部实体/DTD。远端文件名仅允许安全的单层 `backup*.zip`，避免把备份动作扩展成任意路径写入；恢复复用已有本地 ZIP 导入逻辑。`WebDavBackupModelTest`、`WebDavSettingsModelTest` 和 `SqliteCoreLibraryTest` 覆盖 URL 规范化、配置持久化、列表/上传/下载/恢复、危险文件名、认证失败以及设置页操作状态。该子集不实现 Android 的双向阅读进度同步、后台同步、远程书库、冲突解决、图片同步、Android 服务/通知语义或凭据加密偏好。

本阶段还完成了 Windows URL 协议关联的桌面子集。启动时尝试注册 `yuedu://` 和 `legado://`，设置页提供“注册协议关联”按钮，可手动重试并显示成功状态或 `reg.exe` 错误。实现按当前 `Legado.exe` 路径生成 UTF-16LE/BOM `.reg` 内容，通过 Windows System32 `reg.exe import` 写入当前用户 `HKCU\Software\Classes` 协议项，并使用锁避免并发注册；模型测试覆盖成功和失败状态保留。该能力不需要管理员权限，协议注册失败不会阻止 Portable 应用启动。

Android 通过 intent filter 和 Activity 路由处理这两个 URI；Windows 使用当前可执行文件与每用户注册表项，因此不承诺 Android 的 intent 解析、Activity 返回栈和生命周期语义。支持的本地文件扩展名关联已作为独立 Windows 桌面集成切片完成，Android 文件/内容 Intent、URI 权限和 Provider 语义仍按下一段说明保留为差异。

本阶段还完成了 Windows 本地文件扩展名关联的桌面子集。启动时尝试为 `.txt`、`.epub`、`.bmp`、`.gif`、`.jpeg`、`.jpg`、`.png`、`.webp`、`.cbz`、`.zip`、`.wav`、`.aif`、`.aiff`、`.au` 和 `.snd` 注册当前用户 `HKCU\Software\Classes` 关联，设置页提供“注册文件关联”按钮、成功/失败状态和重试。每个扩展名写入扩展名键、Legado ProgID 及 `shell\open\command`，命令使用当前 `Legado.exe` 和 `%1` 文件路径；`.reg` 使用 UTF-16LE/BOM 并通过 System32 `reg.exe import` 导入，不需要管理员权限，失败不阻止 Portable 启动。

关联文件启动参数会被解析为独立的本地文件请求，排除 `--data-dir` 等选项值、URL、相对路径和不支持的扩展名。启动导入复用 `LocalBookImporter`，成功后打开对应的文本、图片或音频阅读器，失败则保留桌面壳并显示导入错误。请求解析、注册表命令/扩展名清单、注册模型状态、启动路由和导入模型均有聚焦测试。Android 通过文件/内容 Intent、URI 权限、Provider 流和 Activity 生命周期处理文件打开；Windows 只实现当前用户文件系统路径和桌面解析器边界，不承诺 Android SAF、Provider 权限、分享 Intent、Activity 返回栈、后台导入或网络书籍文件语义。

## Task 1: Establish Migration Inventory and Shared-Core Boundaries

**Files:**
- Create: `docs/windows-migration/feature-inventory.md`
- Create: `docs/windows-migration/core-boundaries.md`
- Modify: `docs/superpowers/plans/2026-08-02-windows-migration.md`
- Inspect: `app/src/main/java/io/legado/app/data`, `app/src/main/java/io/legado/app/model`, `app/src/main/java/io/legado/app/help`, `app/src/main/java/io/legado/app/service`, `app/src/main/java/io/legado/app/ui`, `modules/web/src`

**Interfaces:**
- Produces a checked inventory mapping Android activities/services/data/model packages to Windows modules and migration gates.
- Defines platform interfaces for storage, networking, JavaScript, browser verification, file picking, media/TTS, notifications and background work before production extraction begins.

- [ ] Count current activity/service/data/model entry points with repository search and group them by feature.
- [ ] Trace `modules:web` HTTP and WebSocket messages to the Android server implementation.
- [ ] Record which existing model/rule/parser classes are not Android-dependent and can move unchanged.
- [ ] Write the feature inventory and boundary interfaces with exact source paths and first migration order.
- [ ] Verify the inventory against the current source tree and `git diff`.

## Task 2: Add a JVM Shared-Core Module

**Files:**
- Create: `modules:core/build.gradle`
- Create: `modules/core/src/main/kotlin/io/legado/core/...`
- Create: `modules/core/src/test/kotlin/io/legado/core/...`
- Modify: `settings.gradle`, root dependency declarations, and selected Android source ownership after tests are green.

**Interfaces:**
- Produces `Book`, `BookSource`, `BookChapter`, `BookShelfRepository`, `BookSourceRepository`, `ChapterRepository`, `RuleEngine`, `BookParser` and `CoreRuntime` APIs without Android types.
- Consumes platform ports from `io.legado.core.platform`: `Storage`, `HttpClient`, `ScriptRuntime`, `BrowserRuntime`, `TaskScheduler`, `SpeechEngine` and `ImageStore`.

- [ ] Write failing tests for loading a book, source and chapter through repository ports.
- [ ] Run the focused Gradle test and confirm failure is caused by the missing shared API.
- [ ] Implement the smallest JVM-only models and in-memory repository adapters.
- [ ] Run focused tests, then Android module compilation to ensure no accidental Android dependency is introduced.
- [ ] Move or copy one pure rule/parser path behind the shared interfaces and add compatibility tests using existing JSON fixtures.

## Task 3: Implement Windows Persistence and Compatibility Import

**Files:**
- Create: `desktop/core-persistence/...`
- Create: `desktop/core-persistence/src/test/...`
- Create: `desktop/migrations/...`
- Inspect and reuse schemas from `app/schemas/io.legado.app.data.AppDatabase`

**Interfaces:**
- Produces `WindowsStorage` backed by SQLite and `BackupImporter`/`BackupExporter` for existing Legado data.
- Consumes shared repository contracts from Task 2 and preserves entity names, primary keys and serialized settings.

- [ ] Write failing tests for opening a copied Legado database, preserving book/source/chapter records, and round-tripping a backup.
- [ ] Run tests and verify they fail before the SQLite adapter exists.
- [ ] Implement schema migration and transactions with explicit UTF-8 and path handling.
- [ ] Run the persistence test suite and compare imported counts against fixture expectations.
- [ ] Add a user-data directory policy under `%LOCALAPPDATA%` with an explicit portable override.

## Task 4: Build the Windows Desktop Shell

**Files:**
- Create: `desktop/app/build.gradle`
- Create: `desktop/app/src/jvmMain/kotlin/io/legado/desktop/Main.kt`
- Create: `desktop/app/src/jvmMain/kotlin/io/legado/desktop/AppShell.kt`
- Create: `desktop/app/src/jvmMain/resources/...`
- Create: `desktop/app/src/test/...`
- Modify: `settings.gradle`

**Interfaces:**
- Produces a Compose Desktop application with navigation for bookshelf, search/discovery, subscriptions, settings, source management and reader routes.
- Consumes `CoreRuntime` and `WindowsStorage`; no UI screen may access Android `Context`, `Activity`, ViewBinding or Room directly.

- [ ] Write a failing startup test for the shell route model and default bookshelf route.
- [ ] Run the test and verify the route model is absent.
- [ ] Implement the minimal desktop app with light/dark theme support, keyboard navigation and a visible empty-state bookshelf.
- [ ] Build and launch the JVM desktop app, confirming a real top-level Windows window.
- [ ] Keep the first verified desktop instance running for manual inspection.

## Task 5: Reuse and Expand the Vue Web Surface

**Files:**
- Modify: `modules/web/src/store/connectionStore.ts`, `modules/web/src/store/bookStore.ts`, `modules/web/src/store/sourceStore.ts`
- Modify: `modules/web/src/views/BookShelf.vue`, `BookChapter.vue`, `SourceEditor.vue`
- Create: `modules/web/src/api/desktopBridge.ts`
- Create: `modules/web/src/types/core.ts`
- Add focused tests under `modules/web/src/**/*.spec.ts`

**Interfaces:**
- Produces a transport-neutral API client supporting local HTTP/WebSocket in development and an embedded desktop bridge in release builds.
- Consumes the same JSON contract exposed by `CoreRuntime` and keeps existing Android web-server compatibility during transition.

- [ ] Write failing tests for bookshelf load, chapter load and source save using a fake transport.
- [ ] Run the web test/type-check command and confirm failure for the missing transport abstraction.
- [ ] Implement the API client and adapt stores/views without changing user-visible data semantics.
- [ ] Run `pnpm type-check` and the focused tests, then build the web assets.
- [ ] Embed the built assets into the desktop shell and verify navigation and reconnect behavior.

## Task 6: Migrate the Reader and Platform Features

**Files:**
- Create/modify desktop reader screens and platform adapters under `desktop/app` and `modules/core`.
- Add tests for pagination, progress, bookmarks, replacements, theme settings, keyboard commands, manga/image pages and RSS.
- Port feature-specific logic from the Android packages listed in `docs/windows-migration/feature-inventory.md`.

**Interfaces:**
- Produces complete reader workflows for text, HTML, image/manga, audio and RSS content.
- Consumes shared parsing and persistence APIs, with platform adapters for WebView2, media playback, speech, downloads and notifications.

- [ ] Add one failing test per reader capability before implementation.
- [ ] Implement and verify text reader pagination, progress and TOC first.
- [ ] Implement and verify source-driven content, Android-compatible replacement-rule edge cases, bookmarks and themes.
- [ ] Continue manga/image parity beyond the verified local image/CBZ subset, and implement RSS, audio/TTS and browser verification adapters.
- [ ] Run the full desktop/core test suites and perform keyboard/mouse smoke tests.

## Task 7: Package a Portable Windows Release

**Files:**
- Create: `desktop/packaging/windows/portable.ps1`
- Create: `desktop/packaging/windows/README.md`
- Modify: Gradle distribution configuration and CI workflow under `.github/workflows`

**Interfaces:**
- Produces a self-contained Windows x64 Portable directory containing the app executable, bundled JVM/runtime, web assets, native libraries and default resources.
- Does not require the user to install Java, Node.js, Android SDK, Visual Studio or register an MSIX package.

- [ ] Write a packaging verification script that checks required files and starts the packaged executable with an isolated data directory.
- [ ] Run it against a deliberately incomplete staging directory and confirm it reports the missing runtime.
- [ ] Implement the packaging task and include all runtime dependencies and licenses.
- [ ] Run the script on the produced distribution and verify startup, database creation and clean shutdown.
- [ ] Document upgrade, backup and portable-data behavior.

## Task 8: Full Compatibility Audit

**Files:**
- Create: `docs/windows-migration/compatibility-matrix.md`
- Create: `desktop/tests/compatibility/...`
- Modify: CI workflows and migration documents.

**Interfaces:**
- Produces a requirement-by-requirement matrix showing Android behavior, Windows behavior, evidence, known differences and migration status.

- [ ] Enumerate every Android activity, service, data entity, import/export path and settings group.
- [ ] Add automated compatibility tests for data formats and core rule behavior.
- [ ] Run full Android regression tests, shared-core tests, desktop tests, web type-check/build and portable launch tests.
- [ ] Run the packaged app in a clean Windows user profile and inspect logs for startup errors.
- [ ] Mark the overall migration complete only when every required feature has direct evidence or an explicitly accepted equivalent.
