# Windows Migration Core Boundaries

This document defines the first ownership boundary for the Windows migration. It is intentionally based on the current source tree, not on a new greenfield application model.

## Target Layers

| Layer | Responsibility | May depend on | Must not depend on |
| --- | --- | --- | --- |
| `modules:core` | Books, chapters, sources, rules, parsing, progress and domain events | Kotlin/JVM libraries and platform ports | AndroidX, `Context`, `Activity`, ViewBinding, Room, Android `WebView` |
| `desktop/core-persistence` | SQLite storage, backup import/export, migrations and path policy | `modules:core`, JDBC/SQLite | Android Room, Android file picker APIs |
| `desktop/platform-windows` | HTTP, cookies, WebView2, files, images, speech, media, scheduling and notifications | Windows/JVM APIs and `modules:core` ports | Android lifecycle and services |
| `desktop/app` | Compose Desktop shell, reader, settings, navigation and desktop commands | `modules:core`, persistence, Windows platform adapters | Direct Android implementation classes |
| `modules:web` | Source editor, management views and reusable reader surfaces | JSON API/bridge contract | Android server-specific message details |
| `app` | Existing Android product and compatibility baseline | Current Android implementation | Windows desktop modules at runtime |

## Platform Ports

The shared core should depend on interfaces with these responsibilities:

```kotlin
interface Storage {
    suspend fun <T> read(block: suspend StorageSession.() -> T): T
    suspend fun <T> transaction(block: suspend StorageSession.() -> T): T
}

interface HttpClient {
    suspend fun execute(request: HttpRequest): HttpResponse
}

interface ScriptRuntime {
    suspend fun evaluate(source: String, bindings: Map<String, Any?>): Any?
}

interface BrowserRuntime {
    suspend fun load(request: BrowserRequest): BrowserResult
}

interface TaskScheduler {
    fun schedule(task: ScheduledTask): TaskHandle
    fun cancel(handle: TaskHandle)
}

interface SpeechEngine {
    suspend fun speak(text: String, options: SpeechOptions)
    fun stop()
}
```

The exact package and types are introduced in the shared-core task after the current domain dependencies have been measured. These ports are contracts, not permission to move Android UI code into the shared module.

## Migration Rules

1. Move pure domain behavior first: serialization, rule evaluation, book/source/chapter transformations and progress calculations.
2. Replace Android global access with injected ports before moving a class across modules.
3. Preserve JSON field names, database table names, backup formats and source-rule semantics unless a compatibility test proves an equivalent.
4. Keep Android behavior compiling while each extraction is introduced; delete duplicated Android code only after Windows and Android tests cover the same contract.
5. Treat WebView-dependent JavaScript and verification as a browser port. A desktop browser engine is required for source compatibility; plain HTTP is not an equivalent replacement.
