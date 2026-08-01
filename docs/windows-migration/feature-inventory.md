# Legado Windows Feature Inventory

Status values: `baseline` means present in Android, `web-partial` means exposed by `modules:web`, `planned` means a Windows equivalent is required, and `verified` requires executable Windows evidence.

## User-Facing Features

| Feature | Android baseline | Current web surface | Windows target | Status |
| --- | --- | --- | --- | --- |
| Welcome and first-run setup | `ui/welcome/WelcomeActivity.kt` | none | Desktop startup/setup route | planned |
| Bookshelf and groups | `ui/main/MainActivity.kt`, `ui/book/manage` | `BookShelf.vue` | Native bookshelf with groups and batch actions | web-partial |
| Book search and discovery | `ui/book/search`, `ui/book/explore` | partial store/view support | Search, explore and source selection | planned |
| Book information and source switching | `ui/book/info` | chapter/source data only | Full information, source ranking and refresh | planned |
| Text/HTML reading | `ui/book/read/ReadBookActivity.kt` | `BookChapter.vue` partial | Paginated reader, menu, progress, themes and shortcuts | planned |
| Chapter list and navigation | `ui/book/toc/TocActivity.kt` | `BookChapter.vue` | TOC, filters, download and position restore | web-partial |
| Manga/image reading | `ui/book/manga/ReadMangaActivity.kt` | none | Image pages, zoom, long-strip/page modes | planned |
| RSS reading | `ui/book/rss/ReadRssActivity.kt` | routes/source editor only | RSS subscriptions and article reader | planned |
| Audio playback | `ui/book/audio/AudioPlayActivity.kt`, `service/AudioPlayService.kt` | none | Desktop media controls and persistence | planned |
| TTS/read aloud | `service/TTSReadAloudService.kt`, `HttpReadAloudService.kt` | none | Windows speech and HTTP TTS | planned |
| Book/source import and export | `ui/book/import`, `ui/file` | source management partial | File dialogs, drag/drop, backups and batch import | planned |
| Book updates/downloads | `service/UpdateBookService.kt`, `DownloadService.kt` | none | Queue, retries, progress, pause/resume | planned |
| Bookmarks and reading records | `ui/book/bookmark`, `ui/about/ReadRecordActivity.kt` | none | Bookmark panel, history and statistics | planned |
| Replace/dictionary rules | `ui/replace`, `ui/dict` | none | Rule editors and runtime application | planned |
| Book/source/RSS editors | `ui/book/source`, `ui/book/rss` | `SourceEditor.vue` and routes | Native editor plus reusable Vue editor | web-partial |
| Script and source debugging | `ui/association`, `ui/book/source/debug` | source debug route support | Console, request trace and JS runtime | planned |
| Browser verification | `ui/browser`, `help/http/BackstageWebView.kt` | none | WebView2-based source verification | planned |
| Settings and themes | `ui/config`, `constant/Theme.kt` | theme config only | All read/source/network/backup/TTS settings | planned |
| Backup and WebDAV/sync | settings, `data/entities/Server.kt`, web server | server connection store | Portable data, cloud backup and restore | planned |
| Local HTTP/WebSocket management API | `web/HttpServer.kt`, `web/WebSocketServer.kt` | required by current web module | Embedded desktop bridge with compatible JSON contract | planned |

## Core Data and Runtime Areas

| Area | Current evidence | Windows migration concern |
| --- | --- | --- |
| Persistence | `data/entities`, `data/dao`, Room schemas 80-86 | SQLite/JDBC adapter must preserve fields, IDs and migrations |
| Rule engine | `model/analyzeRule`, `model/book`, `help/source` | Must be JVM-only or wrapped behind platform ports |
| HTTP/cookies | `help/http`, OkHttp/Cronet, `CookieManager.kt` | Replace Cronet/Android cookie APIs while preserving headers and cookies |
| JavaScript | `modules:rhino`, `help/JsExtensions.kt` | Preserve source script extensions without exposing unrestricted desktop internals |
| Browser | Android `WebView` and backstage browser | WebView2 runtime/bridge and user-data profile required |
| Images | Glide, Android SVG and touch image view | JVM image decoding/caching and Compose gestures |
| Media | Media3/ExoPlayer | Desktop media backend and formats |
| Background work | Android services and lifecycle | Coroutine scheduler with cancellation and restart persistence |
| Files | Android `DocumentFile`/SAF | Windows path, file picker, drag/drop and portable mode |
| Notifications | Android notifications/tile | Windows toast or in-app task center |

## First Migration Order

1. Data model and rule/parser contracts.
2. SQLite/backup compatibility.
3. Desktop shell and bookshelf read-only view.
4. HTTP/source parsing and source editor.
5. Text reader, TOC, progress and bookmarks.
6. Search/discovery, updates, import/export and RSS.
7. Browser verification, scripts, manga/images, audio and TTS.
8. Settings, sync, notifications, packaging and compatibility audit.
