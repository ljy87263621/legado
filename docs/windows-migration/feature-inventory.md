# Legado Windows Feature Inventory

Status values: `baseline` means present in Android, `web-partial` means exposed by `modules:web`, `desktop-partial` means a tested Windows subset exists but Android parity is incomplete, `planned` means a Windows equivalent is required, and `verified` requires executable Windows evidence.

## User-Facing Features

| Feature | Android baseline | Current web surface | Windows target | Status |
| --- | --- | --- | --- | --- |
| Welcome and first-run setup | `ui/welcome/WelcomeActivity.kt` | none | Desktop startup/setup route with durable completion marker | verified |
| Bookshelf and groups | `ui/main/MainActivity.kt`, `ui/book/manage` | `BookShelf.vue` | Native bookshelf with groups and batch actions | desktop-partial |
| Book search and discovery | `ui/book/search`, `ui/book/explore` | partial store/view support | Search, explore and source selection; result filtering by configured source-discovery rules; source-variable and source-aware Cookie support | desktop-partial |
| Book information and source switching | `ui/book/info` | chapter/source data only | Full information, source ranking and refresh with source variables and source-aware Cookies | desktop-partial |
| Text/HTML reading | `ui/book/read/ReadBookActivity.kt` | `BookChapter.vue` partial | Paginated reader, menu, progress, themes and shortcuts | desktop-partial |
| Chapter list and navigation | `ui/book/toc/TocActivity.kt` | `BookChapter.vue` | TOC, filters, download and position restore | desktop-partial |
| Manga/image reading | `ui/book/manga/ReadMangaActivity.kt` | none | Local image/CBZ/ZIP import plus online image-source routing, image-rule chapter extraction, image pages, disk/memory cache, keyboard navigation, zoom and progress save; long-strip/double-page modes and gesture parity remain future work | desktop-partial |
| RSS reading | `ui/book/rss/ReadRssActivity.kt` | subscription directory, one-click source import, RSS article reader | RSS source refresh, article opening, one-chapter reader routing, `ruleContent` loading, relative URL resolution, cached content, summary fallback when no content rule exists, and SQLite restart recovery; Android WebView/`webJs` and full refresh/cache/read lifecycle remain future work | desktop-partial |
| Audio playback | `ui/book/audio/AudioPlayActivity.kt`, `service/AudioPlayService.kt` | none | Local WAV/AIFF/AU/SND import, audio reader page, Java Sound playback controls, chapter model/navigation and progress persistence; remote/media-service parity remains future work | desktop-partial |
| TTS/read aloud | `service/TTSReadAloudService.kt`, `HttpReadAloudService.kt` | none | Windows speech controls in the reader using PowerShell `System.Speech.Synthesis`; HTTP TTS and Android engine parity remain future work | desktop-partial |
| Book/source import and export | `ui/book/import`, `ui/file` | source management partial | File dialogs, drag/drop, backups and batch import | desktop-partial |
| Book updates/downloads | `service/UpdateBookService.kt`, `DownloadService.kt` | none | Bookshelf update queue and a detail-page chapter download queue with cache skipping, retries at the content-service boundary, progress, pause/resume/cancel and per-chapter results; persisted desktop scheduled updates with restart-time schedule recovery | desktop-partial |
| Bookmarks and reading records | `ui/book/bookmark`, `ui/about/ReadRecordActivity.kt` | none | Bookmark panel, history and statistics, UTF-8 Markdown export, and a monthly reading heatmap with month navigation and day-duration inspection | desktop-partial |
| Replace/dictionary/TXT TOC rules | `ui/replace`, `ui/dict`, `ui/book/toc/rule` | none | Replace rules and TXT TOC rules have shared models, runtime/persistence/backup coverage and desktop management entry points; TXT rules support add/edit/enable/delete and JSON import/export, local chapter byte ranges and delayed missing-cache reads. Dictionary rules now have a tested Windows lookup subset with management UI, `{{key}}` URL expansion, supported URL options (`method`, `body`, `charset`, `headers`), raw response, CSS/JSONPath/basic-regex extraction, enabled-rule ordering, per-rule errors, and all-match CSS text joining; full Android `AnalyzeRule` composition remains separate | desktop-partial |
| Book/source/RSS editors | `ui/book/source`, `ui/book/rss` | `SourceEditor.vue` and routes | Native editor plus reusable Vue editor | web-partial |
| Script and source debugging | `ui/association`, `ui/book/source/debug` | source debug route support | Source editor request check for one resolved HTTP request, including default GET and supported URL-option method/body/charset/headers, with manual/dynamic headers, source variables, persistent/session Cookies, request metadata and response inspection; full trace and JS debugger remain future work | desktop-partial |
| Browser verification | `ui/browser`, `help/http/BackstageWebView.kt` | none | JavaFX embedded browser verification is available from the source editor, with completion-time Cookie capture/persistence and source-aware re-fetch; WebView2 and Android result-bridge parity remain planned | desktop-partial |
| Windows URL protocol association | Android intent/activity routing for `yuedu://` and `legado://` | none | Settings action and startup registration for per-user `yuedu://` and `legado://` links | desktop-partial |
| Windows file-extension association | Android document/open intent handling | none | Settings action and startup registration for supported local book, image, comic and audio files; associated launch imports the file and opens the desktop reader | desktop-partial |
| Settings and themes | `ui/config`, `constant/Theme.kt` | theme config only | All read/source/network/backup/TTS settings | desktop-partial |
| Backup and WebDAV/sync | settings, `data/entities/Server.kt`, web server | server connection store | Portable data, cloud backup and restore; desktop WebDAV ZIP upload/list/download/restore plus remote-book list/upload/download/import/delete subset | desktop-partial |
| Local HTTP/WebSocket management API | `web/HttpServer.kt`, `web/WebSocketServer.kt` | required by current web module | Embedded loopback-only desktop HTTP bridge with compatible JSON contract, static management pages, upload page, local import, image/cover proxy, TOC refresh and replacement-rule routes, plus a WebSocket bridge on the next port | desktop-partial |

## Core Data and Runtime Areas

| Area | Current evidence | Windows migration concern |
| --- | --- | --- |
| Persistence | `data/entities`, `data/dao`, Room schemas 80-86 | SQLite/JDBC adapter must preserve fields, IDs and migrations |
| Rule engine | `model/analyzeRule`, `model/book`, `help/source` | JVM source-rule subset now covers static HTML XPath in book info, TOC, content and search/discovery, including relative paths and attribute extraction; full Android rule composition still requires a separate port |
| HTTP/cookies | `help/http`, OkHttp/Cronet, `CookieManager.kt` | Source-aware JVM HTTP with persistent/session Cookie storage, domain/path matching, `Set-Cookie` capture, manual Cookie preservation and `enabledCookieJar` handling |
| JavaScript | `modules:rhino`, `help/JsExtensions.kt` | Preserve source script extensions without exposing unrestricted desktop internals |
| Browser | Android `WebView` and backstage browser | WebView2 runtime/bridge and user-data profile required |
| Images | Glide, Android SVG and touch image view | JVM image decoding/caching and Compose gestures |
| Media | Media3/ExoPlayer | Desktop media backend and formats |
| Background work | Android services and lifecycle | Coroutine scheduler with cancellation and restart persistence |
| Files | Android `DocumentFile`/SAF | Windows path, file picker, drag/drop and portable mode |
| Notifications | Android notifications/tile | Windows toast or in-app task center |

## Current Migration Plan

The original migration order is complete as a historical roadmap. The active
increment is source browser session sharing: classify login-required responses,
then connect the existing JavaFX Cookie capture to source requests and expose
an explicit re-login path. Detailed interfaces and acceptance criteria live in
`source-browser-session-design.md`.

The next round is staged in `online-source-session-next-round-plan.md`. Online
image reading re-login is now implemented and regression-tested; discovery is
the next target, followed by RSS, each with its own focused tests and commit
gate.

## Verified Windows Evidence

- Portable packaging: `desktop/packaging/windows/portable.ps1` checks the jpackage directory, bundled JVM (`runtime/bin/server/jvm.dll`), application JARs and launcher metadata, then starts the packaged executable with an isolated `LEGADO_DATA_DIR` and waits for `legado.db`.
- Portable verifier regression: `desktop/packaging/windows/test-portable.ps1` rejects a distribution without the bundled JVM and accepts the current directory distribution.
- Web module verification: from `modules/web`, `vue-tsc --build --force` and `vite build` both completed successfully on 2026-08-06; Vite transformed 123 modules and produced the single-file `dist/index.html`.
- Remaining release work: clean-profile user-data migration, copying the verified directory to the user-selected install location, signing, upgrade behavior and a full compatibility audit.

## Welcome and First-Run Setup Subset

The desktop shell now resolves startup through a tested route policy. A new SQLite data directory opens the `欢迎` route, which explains the local data-directory policy and provides a `开始使用` action. Completing the action persists a single `desktop_setup` completion marker and navigates to the bookshelf; the marker survives closing and reopening the database. Existing databases that predate this marker are treated as initialized when they already contain books, so adding the welcome route does not interrupt an existing bookshelf. External subscription-import launch requests continue directly to the subscriptions route.

This is a Windows startup/setup subset, not a claim of Android first-run parity. Android's `WelcomeActivity` preferences, permission prompts, default source initialization, device-specific onboarding and any Android service setup are not imported by this slice. The desktop welcome action only records that the native shell has been acknowledged; it does not silently add sources, request Windows permissions, or alter Android data.

## Source-Discovery Filter Subset

The Windows discovery route applies enabled rules to each loaded page before results are appended and deduplicated. A rule drops a result when its regular expression matches any configured field. Supported fields are `NAME`, `AUTHOR`, `INTRO`, `KIND`, and `WORD_COUNT`; supported scopes are all sources, one source using `source::URL`, or one or more source groups separated by commas. Invalid enabled rules are ignored without aborting discovery, and the page reports their count.

Rules are managed from Settings and persist through the `CoreLibrary` boundary. The desktop implementation supports ordered SQLite CRUD, local ZIP backup/restore, and JSON object/array import/export. This is a Windows result-filter subset: Android dynamic filter scripts, complete source ranking/filter semantics, browser/WebView behavior, and Android cache semantics remain outside the scope.

## Source-Debug Request Inspection Subset

The Windows source editor exposes a focused request-check workflow. It sends one resolved HTTP request, using GET by default and supporting the shared URL-option subset for method, body, charset and headers. It accepts manual headers as a JSON object or `name: value` lines, resolves the current source's dynamic `header` rule and source variables, reuses the source's persistent/session Cookie jar when enabled, and lets manual headers override the resolved values. The GUI shows request method, URL, headers and body together with the final URL, status code, response headers, response body, and transport or non-2xx errors; the behavior is covered by `SourceDebugModelTest` with an injected HTTP client.

This is a diagnostic request-inspection subset, not Android source-debug parity. The management bridge exposes a constrained `/bookSourceDebug` WebSocket request-log route, but Android's complete source validation pipeline, per-rule execution trace, login UI, WebView/WebView2 execution, browser-session sharing, and full dynamic script debugging remain outside the Windows boundary.

## Source Runtime Data Subset

The Windows core stores book-source variables by source URL and stores persistent Cookies by domain, path and name in SQLite. Source scripts can read and update variables; unset reads return an empty string and assigning `null` removes a variable. Source-aware search, book details, chapter refresh, content loading and source debugging resolve source headers and URLs with the same library-backed runtime. Cookies support persistent `Max-Age`/`Expires` entries, in-memory session entries, domain/path matching, manual Cookie preservation and the source's `enabledCookieJar` switch. Persistent source variables and Cookies are included in local ZIP backup/restore; session Cookies are deliberately excluded.

This is a Windows runtime subset, not Android login/WebView parity. The next
increment is specified in `source-browser-session-design.md`; WebView2,
CAPTCHA callbacks, Android WebView cookie sharing and the Android browser-result
bridge remain outside the current target.

## Embedded Browser Verification Subset

The Windows source editor validates an HTTP(S) verification URL and opens it in a JavaFX `WebView`. The action carries the current source URL and source name as testable request metadata, rejects blank, malformed, non-HTTP(S), hostless, or overlong URLs before launching, and has an explicit completion action. Completion captures `document.cookie`, persists the resulting source Cookies, and re-fetches the verification URL through the source-aware HTTP client so follow-up source requests receive the verified session.

Android's `SourceVerificationHelp.startBrowser` launches an in-app `WebViewActivity` and can return a verification result to the source pipeline after browser-side success. The Windows subset does not provide equivalent WebView/WebView2 profile behavior, browser JavaScript APIs, login/CAPTCHA callbacks, or `getVerificationResult` semantics; its re-fetch-after-success behavior is limited to Cookie capture and the source-aware JVM HTTP client.

## Windows URL Protocol Association Subset

The Windows Settings page exposes a `注册协议关联` action for the `yuedu://` and `legado://` schemes. The application also attempts registration during startup, while the Settings action provides a visible success/failure state and a manual retry path. Registration writes per-user `HKCU\Software\Classes` entries for each scheme and its `shell\open\command` handler; it does not require administrator rights. The generated `.reg` content uses UTF-16LE with a BOM and is imported through the Windows System32 `reg.exe` executable. A file lock serializes concurrent registration attempts.

This is optional user/machine integration for the Portable build. The application remains launchable from its Portable directory when protocol registration is unavailable, and a failed registration is shown in Settings rather than preventing startup. The URL slice deliberately covers only `yuedu://` and `legado://`; file extensions are documented separately below.

Android routes these links through intent filters and activity dispatch, with Android package ownership and lifecycle semantics. Windows uses the current executable path and per-user registry classes instead; it does not claim Android intent resolution, activity back-stack behavior, or system-wide administrator registration.

## Windows File-Extension Association Subset

The Windows Settings page exposes a `注册文件关联` action with a visible success/failure state and retry path. Startup also attempts registration. The supported extensions are exactly the formats accepted by the desktop local importer: `.txt`, `.epub`, `.bmp`, `.gif`, `.jpeg`, `.jpg`, `.png`, `.webp`, `.cbz`, `.zip`, `.wav`, `.aif`, `.aiff`, `.au`, and `.snd`. Registration writes per-user `HKCU\Software\Classes` extension and ProgID keys, points each `shell\open\command` handler at the current `Legado.exe` with the selected path as `%1`, uses UTF-16LE/BOM `.reg` content, imports through Windows System32 `reg.exe`, and serializes concurrent attempts with a lock. Administrator-wide registration is not attempted.

When Windows launches the application with one of these associated absolute paths, `DesktopLaunchRequest` turns it into a typed local-file request while excluding option values such as `--data-dir`, URLs, relative paths and unsupported extensions. The startup flow reuses `LocalBookImporter`, persists the same book/chapter/content records as the in-app importer, and opens the imported book in the appropriate text, image or audio reader. A failed import stays in the desktop shell and reports the error through the existing import feedback instead of blocking startup. The registry/model/request boundaries are covered by focused JVM tests; Portable registration remains optional and the executable still starts if `reg.exe` or executable discovery fails.

Android handles document opens through content/file `Intent` dispatch, URI permissions, provider-backed streams and Activity lifecycle/back-stack behavior. The Windows subset accepts filesystem paths from file associations, uses current-user registry classes, and reuses local filesystem parsers; it does not claim Android SAF/provider permissions, share intents, Activity result callbacks, background import scheduling, or Android media/document lifecycle semantics. Network books and unsupported formats remain outside this association slice.

## Reading-Record Markdown Export Subset

The Windows reading-record page provides a save-file action that exports all locally stored reading sessions as UTF-8 Markdown. The document includes total duration, book/session counts, per-book totals and latest-read timestamps, followed by a stable session table containing book name, Android-compatible day key, start/end epoch seconds, and non-negative duration. Book names are escaped for Markdown table cells, and the default file name is `legado-read-records.md`.

This is a desktop reporting artifact, not Android data synchronization or a replacement for the Android reading-record database. It exports the records currently held by the desktop `CoreLibrary`; it does not add a Markdown import path, upload to WebDAV, or reproduce Android-specific statistics and lifecycle aggregation. Epoch seconds and day keys are retained to avoid locale-dependent loss of data.

## Reading-Record Monthly Heatmap Subset

The Windows reading-record page now includes a monthly heatmap. It aggregates locally stored sessions by the Android-compatible `yyyyMMdd` day key, clamps negative session durations to zero, uses the correct calendar length including leap years, lays weeks out Monday-first, and maps each day to the Android-compatible six-level scale with a 12-hour cap. The GUI supports previous/next month navigation (without moving beyond the current month), shows the month's total duration, and lets the user select a day to inspect its accumulated duration. The grid height is calculated from the actual number of rows so a 31-day month cannot be clipped.

`ReadRecordHeatmapModelTest` covers month filtering, duration aggregation, negative-duration clamping, Android-compatible levels, and the 31-day grid layout constraint. This remains a Windows monthly visualization subset. It does not implement Android's long-press deletion, per-day list filtering, Android `Header`/`RecyclerView` interaction, Markdown import/synchronization, WebDAV reading-record upload, lifecycle-driven aggregation, notifications, or the complete Android statistics surface. Windows uses Compose and the `CoreLibrary`/SQLite boundary; Android uses `ReadRecordActivity`, Room, and the native `MonthHeatMapView`.

## Local Image/CBZ Reader Subset

The Windows desktop reader now imports standalone images plus `.cbz`/`.zip` archives containing `bmp`, `gif`, `jpeg`, `jpg`, `png`, or `webp` files. Archive entries are filtered to image files, sorted naturally, grouped into chapters by directory, and root-level images are grouped under `正文`. Imported image chapters intentionally do not create fake text content, and re-importing preserves existing reading progress.

The desktop GUI routes local image books (`origin == loc_book`) and online image-source results (the Android-compatible image type bit) to dedicated image readers. The online reader loads image-source chapter content, preserves images selected directly by CSS/XPath content rules, resolves relative URLs, filters ordinary links, requests images through source-aware Cookies and URL options, caches bytes in memory and on disk, and persists page progress. Both readers support chapter/page navigation and saved position; the local reader additionally provides 50%-300% zoom controls and keyboard navigation. Long-strip and double-page modes, touch gestures, filters, prefetch strategy, SVG parity and complete Android image-rule semantics remain outside this increment.

## Local HTTP Management Bridge Subset

Settings exposes a loopback-only local management service. The GUI accepts a port, starts or stops the service, displays and copies both the HTTP endpoint and the WebSocket endpoint, and lists the supported routes. The default HTTP port is Android's `1122`; tests may use port `0` to obtain an available ephemeral port. The bridge binds only to `127.0.0.1`, uses HTTP port `N` and WebSocket port `N + 1`, and returns UTF-8 JSON using the Android-compatible `isSuccess`, `errorMsg`, and `data` envelope for HTTP responses.

The HTTP bridge serves `/`, `/index.html` and `/favicon.ico` from the embedded web resources, and serves the Android upload page at `/uploadBook/` and `/uploadBook/index.html`. The supported GET API subset is `/health`, `/getBookshelf`, `/getBookSources`, `/getBookSource`, `/getGroups`, `/getChapterList?bookUrl=...` (also accepting Android's `url` parameter), `/getBookContent?url=...&index=...`, `/getReadConfig`, `/getReplaceRules`, `/refreshToc?url=...`, `/cover?path=...`, and `/image?path=...&url=...`. Local books can refresh their catalog by re-importing the source file; online books refresh through the desktop online-book service and replace the old chapter list only after a new list is obtained. Cover and image responses are returned as PNG data, with local/HTTP image loading, resizing and the required book check for `/image`.

The bridge also supports multipart `POST /addLocalBook` for local TXT, EPUB, image/CBZ/ZIP and supported audio files. Uploaded files are stored below the desktop data directory's `uploads` folder with normalized single-level names before import. `POST /saveBook` accepts an Android-compatible full `CoreBook` JSON object, requires a non-blank `bookUrl`, and replaces or inserts the matching desktop book through `CoreLibrary.saveBook`; it does not perform Android WebDAV progress upload. `POST /saveBookProgress` accepts Android-compatible `BookProgress` JSON (`name`, `author`, `durChapterIndex`, `durChapterPos`, `durChapterTime`, and optional `durChapterTitle`), matches an existing desktop `CoreBook` by `name + author`, and persists the four reading-progress fields through `CoreLibrary`. `POST /saveReadConfig` accepts only the `modules:web` `webReadConfig` schema, validates required fields and ranges, serializes the validated value as normalized JSON, and persists it through `CoreLibrary`; the SQLite adapter stores that JSON in the single-row `desktop_web_read_config` table. `POST /saveReplaceRule`, `POST /deleteReplaceRule` and `POST /testReplaceRule` provide the tested desktop replacement-rule CRUD/test subset. `POST /saveBookSource`, `POST /saveBookSources` and `POST /deleteBookSources` provide source management, and `POST /deleteBook` provides book deletion. The configuration survives closing and reopening the desktop database, while the HTTP endpoint is available only while the loopback service is running.

The implementation is covered by real loopback HTTP tests for static pages, upload pages, full-book save, progress save, read-config success, malformed JSON, blank `bookUrl`, invalid fields, unknown books, replacement-rule CRUD/testing, local TOC refresh with stale-chapter replacement, cover/image responses, multipart validation and unsupported routes; WebSocket tests cover port allocation and the flat Android-compatible search payload; persistence tests cover in-memory round-trip, SQLite restart, and management-server restart; model tests cover port validation, lifecycle and Settings route disclosure. Settings is the GUI boundary: it starts/stops the service, validates the port, displays/copies both endpoints, and discloses the supported HTTP/WebSocket routes. HTTP binds only to `127.0.0.1` on port `N`, while WebSocket binds to `N + 1`; random-port startup makes a bounded number of attempts and cleans up both servers when the adjacent WebSocket bind fails, while fixed-port startup fails promptly. The `/bookSourceDebug` route only emits the desktop request-inspection log subset; Android's complete source-debug state machine and per-rule trace, authentication, LAN exposure, full Android `Book.ReadConfig`/`ReadBookConfig` parity, online content fetching, Android WebDAV upload/synchronization side effects, WebView/WebView2, and Android service/notification behavior remain outside this increment. Content is served only from the desktop `CoreLibrary` cache; this slice does not fetch missing online chapters.

## WebDAV Remote Backup Subset

Settings provides a Windows WebDAV backup section with URL, username, password, remote filename, refresh, upload, remote-selection, and restore controls. The URL is restricted to HTTP(S) and normalized to one trailing slash; the URL and credentials are persisted in the single-row SQLite `desktop_webdav_config` table and reload when the database is reopened. Network actions run off the UI dispatcher. The GUI lists remote `backup*.zip` files, uploads a selected local ZIP with `PUT`, downloads a selected file with `GET`, and restores it through the existing local ZIP backup import path. A connection check is performed by `PROPFIND` before the remote list is shown.

The transport uses Java's `HttpClient` with bounded connect/request timeouts, redirects disabled, and Basic Authentication when credentials are configured. Remote names are deliberately limited to safe single-level `backup*.zip` filenames; path traversal and arbitrary remote paths are rejected. XML DAV responses are parsed with external entity and DTD loading disabled. Tests cover URL normalization, configuration round-trip, PROPFIND/PUT/GET listing/upload/download/restore, unsafe names, authentication failures, settings configuration, refresh, selection, and upload state. SQLite persistence is covered across close/reopen.

This is a desktop remote ZIP backup/restore and remote-book list/upload/download/import/delete subset, not Android WebDAV synchronization. It does not synchronize reading progress, images, or other records bidirectionally; it has no background sync, conflict resolution, Android service/notification behavior, or WebDAV side effects in the local HTTP management bridge. Portable packaging remains the distribution boundary; WebDAV credentials are stored in the local desktop database and are not claimed to have Android encrypted-preference parity.

## Local Audio Reader Subset

The Windows desktop importer accepts one local audio file with a `.wav`, `.aif`, `.aiff`, `.au`, or `.snd` extension and records it as an Android-compatible local audio book with one playable chapter. The audio reader GUI displays the current title and resource, exposes play/pause, stop, chapter selection, previous/next chapter controls, save-position, and keyboard navigation. Playback is provided by the JDK `javax.sound.sampled` Java Sound backend; the model closes the active session when changing chapters or leaving the reader and persists the current chapter position through `CoreLibrary`.

This is a deliberately small Windows subset. Actual decodable audio depends on the Java Sound providers available to the JDK, and MP3/M4A are not guaranteed or advertised as supported. Android Media3/ExoPlayer behavior, remote audio URLs, multi-file/remote audio books, background playback services, audio focus, media buttons, system media notifications, wake locks, sleep timers, network cookies, and audio caching remain outside the Windows boundary.

## Static HTML XPath Source-Rule Subset

The Windows core source parser supports a controlled static-HTML XPath subset in book information, table-of-contents, content, search, and discovery rules. Rules may use the explicit `@XPath:` prefix or XPath-shaped paths beginning with `/`, `./`, or `../`. Element and text-node selection, relative paths inside an `init` or list record, `path/@attribute` extraction for values such as `href` and `src`, and basic `%%` interleaving are supported. Invalid XPath expressions fail closed as empty results, and quoted predicate values containing `&&` or similar operators are preserved while parsing combinations.

This is a desktop parser boundary, not Android `AnalyzeRule` parity. It operates on the fetched static HTML document and does not execute JavaScript, `init` scripts, dynamic WebView content, browser verification, complex rule composition, or every XPath function and node type supported by Android's rule engine. The implementation is covered by `CoreXPathRuleSupportTest`, `OnlineBookServiceTest`, and `BookSourceSearchTest`; the migration status remains `desktop-partial`.
