# Windows Reader Migration Implementation Plan

**Goal:** Migrate the Android Legado reader to a Windows desktop application while preserving the Android reader's user-facing capabilities and data model.

**Architecture:** Keep Android-compatible concepts in `modules:core`, use `desktop:core-persistence` for durable local state, and keep Compose Desktop UI and Windows-specific integration in `desktop:app`. Each migrated capability must have a model/service boundary, a user-visible route or control, and tests against both in-memory and SQLite-backed storage where persistence is involved.

**Tech Stack:** Kotlin/JVM 17, Compose Desktop Material 3, SQLite JDBC, Gson, Jsoup, JSONPath, Java standard library ZIP/HTTP/TTS integrations where suitable.

## Global Constraints

- Preserve Android-compatible book, chapter, source, bookmark, group, progress, and reader-setting semantics.
- Desktop distribution must remain a portable or directory-installable Windows build and must not require MSIX/AppX registration.
- Use the existing `CoreLibrary` boundary instead of coupling UI code to SQLite.
- Every user-visible feature must be reachable from the desktop GUI and have focused automated tests.
- Keep Android source and existing user changes intact; do not remove Android functionality as a migration shortcut.

## Migration Workstreams

1. Reader foundation: bookshelf, local import, source search, details/catalog, reader content, progress, bookmarks, settings, and RSS subscriptions.
2. Data portability: local backup/restore, Android JSON compatibility, optional WebDAV sync, and source/book migration safety.
3. Reader behavior: real paged layout, scroll/paged controls, auto-read, replacement rules, text cleanup, dictionary and TXT TOC rules.
4. Source engine: JavaScript/Rhino execution, cookies/login/source variables, source debug, source filters, and source auto-update.
5. Offline and media: chapter download/cache, update tasks, audio/video/image/comic readers, and media-specific controls.
6. Desktop integrations: file associations, keyboard shortcuts, local server/config management, logs, release packaging, and runtime verification.

## Current Increment

The next independently testable increment is local backup/restore. The archive will contain a versioned manifest plus JSON files for books, chapters, chapter contents, groups, sources, bookmarks, reading records, and reader settings. Restore will upsert records through `CoreLibrary`, validate the manifest before mutation, and expose import/export actions in the desktop settings route.

## Verification Gates

- Run the focused core and desktop tests after each model/service change.
- Run all `modules:core`, `desktop:core-persistence`, and `desktop:app` tests after cross-module changes.
- Build the portable Windows EXE with `:desktop:app:packageExe` before reporting a deliverable.
- Keep a written list of Android capabilities that remain unimplemented; do not claim full parity until each item has GUI and behavioral evidence.
