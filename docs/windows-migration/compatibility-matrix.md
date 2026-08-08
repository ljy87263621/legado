# Legado Windows Compatibility Matrix

Date: 2026-08-06

This matrix describes the current Windows migration boundary. `verified` means
there is executable test or packaged-launch evidence. `desktop-partial` means a
tested Windows subset exists but it is not Android behavior-for-behavior parity.
`planned` means no equivalent is currently shipped.

The Android application remains the compatibility baseline. The Windows build
is a Compose Desktop application backed by `modules:core` and
`desktop:core-persistence`; it does not replace or remove the Android app.

## User Workflows

| Android area | Windows behavior | Evidence | Status | Known difference |
| --- | --- | --- | --- | --- |
| Welcome and first-run setup (`WelcomeActivity`) | Native welcome route, durable `desktop_setup` marker, legacy-book compatibility | `AppRouteTest`, `SqliteCoreLibraryTest`, Portable launch | verified | Android permissions, default-source onboarding and service setup are not imported |
| Bookshelf and groups (`MainActivity`, book manage UI) | Bookshelf, groups, selection and batch actions through `CoreLibrary` | `BookshelfModelTest`, `SqliteCoreLibraryTest` | desktop-partial | Android lifecycle, notifications and every management action are not identical |
| Search and discovery (`SearchActivity`, explore UI) | Source selection, `exploreUrl`, static categories, pagination, deduplication, source-filter rules, source variables and source-aware Cookies | `ExploreModelTest`, `BookSourceSearchTest`, `CoreSourceFilterServiceTest`, `CoreSourceRuntimeDataTest` | desktop-partial | Login UI/WebView session sharing, dynamic explore scripts, source ranking and full Android cache behavior remain absent |
| Book details and source switching (`BookInfoActivity`) | Same-title/author source candidates, source switch with rollback, chapter refresh with source variables and source-aware Cookies | `BookDetailModelTest`, `OnlineBookServiceTest`, `CoreSourceHttpClientTest` | desktop-partial | Full Android source ranking, login UI and WebView session sharing remain absent |
| Text/HTML reader (`ReadBookActivity`) | Scroll and fixed-capacity pagination, progress restore, TOC navigation, themes, keyboard commands and auto-read interval | `ReaderModelTest`, `ReaderNavigationTest`, `ReaderSettingsModelTest` | desktop-partial | Full Android HTML/WebView behavior, all auto-read semantics and touch interaction are not equivalent |
| Bookmarks and records | Search/delete/restore bookmarks, history/statistics, monthly heatmap and UTF-8 Markdown export | `BookmarkModelTest`, `ReadRecordModelTest`, `ReadRecordHeatmapModelTest` | desktop-partial | Android long-press/day filtering, import/sync and notification behavior are absent |
| Manga/image reader (`ReadMangaActivity`) | Local images and CBZ/ZIP archives plus image-source search routing, online chapter image extraction, page navigation, image caching, zoom and saved position | `LocalImageBookParserTest`, `ImageReaderModelTest`, `OnlineImageReaderModelTest`, `BookSourceSearchTest`, `OnlineBookServiceTest` | desktop-partial | Long-strip/double-page modes, touch gestures, SVG parity, image prefetch strategy and complete Android image-rule semantics are absent |
| Audio reader (`AudioPlayActivity`, `AudioPlayService`) | Local WAV/AIFF/AU/SND import and Java Sound playback controls | `LocalAudioBookParserTest`, `AudioReaderModelTest` | desktop-partial | Media3/ExoPlayer, MP3/M4A guarantee, remote audio, background playback and media controls are absent |
| RSS subscriptions/articles | Subscription directory, page icons, source import, source refresh, article opening, one-chapter reader routing, `ruleContent` content loading, relative URL resolution, cached content, summary fallback without a content rule, and SQLite restart recovery | `SubscriptionModelTest`, `SqliteCoreLibraryTest`, `OnlineBookServiceTest`, `ReaderModelTest` | desktop-partial | Android WebView/`webJs`, summary-first rendering strategy and complete RSS refresh/cache/read lifecycle are not claimed |
| Local import/export | TXT, image, CBZ/ZIP and supported audio import; local ZIP backup/restore and WebDAV remote-library download/import | `LocalBookImporterTest`, parser tests, `CoreBackupServiceTest`, `BackupImportRefreshTest`, `WebDavSettingsModelTest` | desktop-partial | Full batch import, drag/drop and all Android SAF/provider semantics are absent |
| Chapter downloads | Persistent chapter download queue with cache skip, progress, pause/resume/cancel and restart recovery | `ChapterDownloadModelTest`, `SqliteChapterDownloadStoreTest` | desktop-partial | Android download records, notifications and background-service lifecycle are absent |
| Book updates | Scheduled and manual bookshelf updates with persistence, retries and pause/resume/cancel | `BookUpdateModelTest`, `BookUpdateSchedulerTest` | desktop-partial | Android service/notification semantics are absent |

## Rules, Sources and Runtime

| Android area | Windows behavior | Evidence | Status | Known difference |
| --- | --- | --- | --- | --- |
| Replace rules | Title/content scope, book/source include/exclude scope, timeout, CRUD and backup/restore | `CoreReplacementServiceTest`, `ReplaceRuleModelTest`, `SqliteCoreLibraryTest` | desktop-partial | Complete Android rule composition and every Android `AnalyzeRule` edge case are not claimed |
| TXT TOC rules | Enable/edit/delete, JSON import/export, default rules, byte-range chapter reads and delayed cache reads | `TxtTocRuleModelTest`, `LocalBookParserTest`, `CoreBackupServiceTest` | desktop-partial | Very-large-file performance and all Android chapter-splitting details remain different |
| Dictionary rules | Ordered enabled rules, `{{key}}` URL expansion, supported URL options (`method`, `body`, `charset`, `headers`), raw response, CSS/JSONPath/basic regex extraction and per-rule errors | `CoreDictRuleServiceTest`, `DictRuleModelTest`, `ReaderDictionaryModelTest` | desktop-partial | Full Android `AnalyzeRule`, XPath/JS/init, complex dynamic URL and WebView behavior are absent |
| Online source XPath rules | Static HTML XPath for book info, TOC, content, search and discovery; `@XPath:` and XPath-shaped paths, relative record/init paths, `@href`/`@src`-style attribute extraction and basic `%%` interleaving | `CoreXPathRuleSupportTest`, `OnlineBookServiceTest`, `BookSourceSearchTest` | desktop-partial | Controlled parser subset only; no complete Android `AnalyzeRule` composition, JavaScript/init execution, WebView-rendered content, browser verification, or full XPath function/node parity |
| Source scripts | Restricted Rhino JVM subset for supported `@js:`/`<js>` rules, dynamic URL/header evaluation, source variables and source-aware Cookie injection | `CoreScriptRuntimeTest`, `CoreSourceScriptServiceTest`, `BookSourceSearchTest`, `SourceDebugModelTest`, `CoreSourceHttpClientTest` | desktop-partial | Login UI/WebView session sharing, full script extensions and browser-dependent rules remain absent; URL options are limited to the shared non-browser subset |
| Source request debugging | One resolved HTTP request with default GET or supported URL-option method/body/charset/headers, dynamic/manual headers, request metadata, response inspection and error reporting; management bridge also exposes a constrained `/bookSourceDebug` WebSocket request-log route | `SourceDebugModelTest`, `DesktopManagementWebSocketTest` | desktop-partial | No complete per-rule trace or full Android debugger; the WebSocket route is only a desktop request-log subset |
| Browser verification | Validated HTTP(S) URL opens a JavaFX embedded browser; explicit completion stores cookies and re-fetches through the source-aware HTTP client | `EmbeddedBrowserVerificationModelTest`, `SourceModelTest`, portable distribution verification | desktop-partial | This is not Android WebView/WebView2 parity: no complete browser profile bridge, CAPTCHA callback, `webJs` runtime or Android result-return semantics |
| Network HTTP | JVM `HttpClient` boundary used by source/search/debug/update/download/WebDAV subsets, with persistent/session source-aware Cookie storage and source-variable headers/URLs | Core and desktop model tests | desktop-partial | Login UI/WebView session sharing, proxy parity, retries and all Android network extensions are incomplete |

## Desktop Integration and Data

| Android area | Windows behavior | Evidence | Status | Known difference |
| --- | --- | --- | --- | --- |
| SQLite data | JDBC SQLite adapter preserves the desktop core records and migrates older desktop settings tables | `SqliteCoreLibraryTest`, `DesktopDataDirectoryTest` | verified | Full Room schema 80-86 import and every Android migration path still require dedicated fixtures |
| Backup/restore | Local ZIP backup includes supported books, sources, source variables, persistent Cookies, rules, schedules and download state | `CoreBackupServiceTest`, `BackupImportRefreshTest` | desktop-partial | Session Cookies are intentionally excluded; Android encrypted preferences, complete sync side effects and every Android entity are not guaranteed |
| Data directory migration | User-selected directory copy with pre-migration snapshot and overwrite protection | `DataDirectoryMigrationModelTest`, persistence migration tests | verified | Clean-profile upgrade and rollback UX remain future work |
| WebDAV | HTTP(S) PROPFIND/PUT/GET/DELETE for safe `backup*.zip` files, configuration persistence, restore, and remote-book list/upload/download/import/delete | `WebDavBackupModelTest`, `WebDavSettingsModelTest`, SQLite restart tests | desktop-partial | No bidirectional progress/image synchronization or conflict resolution |
| URL associations | Per-user `yuedu://` and `legado://` registry registration and startup handling | `WindowsProtocolRegistrationModelTest`, Portable startup | desktop-partial | Windows current-user registry behavior is not Android intent/activity behavior |
| File associations | Per-user associations for supported local book/image/comic/audio extensions; startup import and reader routing | `WindowsFileAssociationRegistrationModelTest`, `DesktopLaunchRequestTest`, importer tests | desktop-partial | No SAF/provider permissions, share intents, network files or background import |
| Windows speech | Reader read-aloud controls using PowerShell `System.Speech.Synthesis` | `WindowsReadAloudModelTest`, `ReaderReadAloudControllerTest` | desktop-partial | HTTP TTS, multiple engines and Android service semantics are absent |
| Local management API | Loopback HTTP JSON bridge on port `N` with embedded `/`, `/index.html`, `/favicon.ico` and `/uploadBook/` pages; compatible GET routes for books, sources, groups, chapters, cached content, read config, replacement rules, TOC refresh and PNG cover/image responses; multipart `/addLocalBook` plus book/progress/config/source/replacement-rule POST routes; WebSocket bridge on `N + 1` with `/searchBook` and `/bookSourceDebug` | `DesktopManagementServerTest`, `DesktopManagementServerModelTest`, `DesktopManagementWebSocketTest` | desktop-partial | HTTP and WebSocket are loopback-only; random-port startup has bounded retry and cleans up a failed adjacent WebSocket bind; WebSocket debug output is a desktop request-log subset, uploaded files stay in the desktop data directory, and there is no LAN exposure, authentication, full Android API or online content fetch |
| Settings and themes | Reader/source/network/backup/TTS, update schedule, management server and association controls | `ReaderSettingsModelTest`, `WebDavSettingsModelTest`, desktop route tests | desktop-partial | Full Android setting groups and system notification settings are not all migrated |

## Packaging and Verification

| Gate | Evidence | Status | Remaining work |
| --- | --- | --- | --- |
| JVM core and desktop tests | `./gradlew.bat :modules:core:test :desktop:core-persistence:test :desktop:app:test --rerun-tasks --no-daemon --max-workers=1` completed successfully on 2026-08-06 | verified | Keep this command in CI |
| Windows directory build | `./gradlew.bat :desktop:app:createDistributable --no-daemon --max-workers=1` completed successfully | verified | Add clean-profile and upgrade fixtures |
| Portable launch | `desktop/packaging/windows/portable.ps1 -Build -LaunchTimeoutSeconds 45` completed successfully and created `legado.db` in isolated data | verified | Sign release and test copy to a clean user-selected directory |
| Portable regression | `desktop/packaging/windows/test-portable.ps1` completed successfully | verified | Test upgrade behavior and a clean Windows profile |
| Web type-check/build | From `modules/web`, `vue-tsc --build --force` and `vite build` both completed successfully on 2026-08-06; Vite transformed 123 modules and produced the single-file `dist/index.html` | verified | Keep this command in CI; validate runtime integration against the desktop management bridge separately |
| Android regression | Android unit/instrumentation evidence was not part of this Windows-only verification run | planned | Run the Android test/build gates before declaring cross-product compatibility |

## Overall Result

The current branch has a verified, distributable Windows reader and a broad set
of tested desktop subsets. It is **not full Android parity**. The highest-risk
remaining areas are login UI and WebView session sharing, complete Android rule
and browser semantics, remote/media services, full WebDAV synchronization, the
complete Android HTTP/WebSocket API, Android regression evidence, clean-profile upgrade
behavior, and signing. Source variables
and source-aware persistent/session Cookie storage are supported by the desktop
runtime and included in persistent backup data; session Cookies are not backed
up by design.
