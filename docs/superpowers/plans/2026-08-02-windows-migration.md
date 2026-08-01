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
- [ ] Implement and verify source-driven content, replacement rules, bookmarks and themes.
- [ ] Implement and verify manga/image, RSS, audio/TTS and browser verification adapters.
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
