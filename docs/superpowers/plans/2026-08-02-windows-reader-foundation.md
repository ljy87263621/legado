# Windows Reader Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development when changing production code. This plan covers one independently testable migration slice of the larger Android-to-Windows migration.

**Goal:** Preserve the core Android reading workflow on Windows by adding persistent reader settings, bookmarks, reading-session records, and bookshelf groups to the existing Compose Desktop reader.

**Architecture:** Extend `modules:core` with platform-neutral data contracts and `CoreLibrary` operations. Implement those operations in both `InMemoryCoreLibrary` and `SqliteCoreLibrary`; keep Android-compatible table names and columns for data that already exists in the Android database. The desktop UI will expose group filtering, reader settings, chapter bookmarks, and a reading-record view through the existing navigation shell.

**Tech Stack:** Kotlin/JVM 17, SQLite JDBC, Kotlin serialization-by-explicit-JSON via the existing Gson dependency, Compose Desktop Material 3, JUnit 4.

## Global Constraints

- Preserve all existing uncommitted user changes and existing Android-compatible schema columns.
- Use ASCII for new source unless Chinese UI strings are required by the existing desktop UI.
- Keep Portable/EXE distribution as the eventual Windows release direction; do not introduce MSIX registration.
- Follow TDD: each production behavior gets a failing test before implementation.
- Do not claim the complete Android feature set is migrated while RSS, WebDAV, TTS, scripts, advanced source rules, backup/restore, and media readers remain unverified.

---

### Task 1: Core Reading Data Contracts

**Files:**
- Create: `modules/core/src/main/kotlin/io/legado/core/library/ReaderModels.kt`
- Modify: `modules/core/src/main/kotlin/io/legado/core/library/CoreLibrary.kt`
- Test: `modules/core/src/test/kotlin/io/legado/core/library/CoreLibraryTest.kt`

**Interfaces:**
- Produces `CoreBookGroup`, `CoreBookmark`, `CoreReadRecord`, `CoreReaderSettings`, and `CoreReaderTheme`.
- Produces `CoreLibrary.groups`, `saveGroup`, `deleteGroup`, `bookmarks`, `saveBookmark`, `deleteBookmark`, `readRecords`, `saveReadRecord`, `deleteReadRecords`, `readerSettings`, and `saveReaderSettings`.

- [ ] Write tests proving in-memory save/query/delete behavior and reader-settings defaults.
- [ ] Run the focused core test and confirm it fails because the APIs do not exist.
- [ ] Add the minimal models and in-memory implementations.
- [ ] Run the focused core test and confirm it passes.

### Task 2: SQLite Persistence and Android Schema Compatibility

**Files:**
- Modify: `desktop/core-persistence/src/main/kotlin/io/legado/desktop/persistence/SqliteCoreLibrary.kt`
- Test: `desktop/core-persistence/src/test/kotlin/io/legado/desktop/persistence/SqliteCoreLibraryTest.kt`

**Interfaces:**
- Persists the Task 1 contracts in `book_groups`, `bookmarks`, `readRecord`, and `desktop_settings`.
- Keeps existing Android table names and column names for groups, bookmarks, and read records.

- [ ] Add reopen, cascade, and Android-schema tests first.
- [ ] Run the persistence tests and confirm the new tests fail.
- [ ] Add schema creation, seed standard groups, bind/read helpers, and JSON settings persistence.
- [ ] Run the persistence module tests and confirm they pass.

### Task 3: Desktop Models and Reader Controls

**Files:**
- Create: `desktop/app/src/main/kotlin/io/legado/desktop/ReaderSettingsModel.kt`
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/BookshelfModel.kt`
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/ReaderModel.kt`
- Test: `desktop/app/src/test/kotlin/io/legado/desktop/BookshelfModelTest.kt`
- Test: `desktop/app/src/test/kotlin/io/legado/desktop/ReaderModelTest.kt`

**Interfaces:**
- Supports group filtering and assigning a book to a group.
- Supports reader settings updates and bookmark creation/removal.
- Saves reading sessions when the reader saves progress.

- [ ] Add failing model tests.
- [ ] Run focused desktop tests and confirm failure.
- [ ] Implement the smallest model behavior and verify green.

### Task 4: Compose Desktop UI Integration

**Files:**
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/Main.kt`
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/AppRoute.kt`
- Test: `desktop/app/src/test/kotlin/io/legado/desktop/AppRouteTest.kt`

**Interfaces:**
- Adds a bookshelf group selector, settings screen controls, reader settings controls, bookmark actions, and a reading-record screen.
- Keeps existing search, source, detail, local-import, online-reading, and theme workflows intact.

- [ ] Add route tests for the new internal/primary navigation behavior.
- [ ] Run focused tests before implementation and verify failure.
- [ ] Add UI controls using existing Compose Material 3 patterns.
- [ ] Compile the desktop app and run all desktop tests.

### Task 5: Migration Audit

**Files:**
- Modify: `docs/` only when audit documentation is needed.

- [ ] Run the full relevant Gradle test suite.
- [ ] Build the desktop distribution task without changing the release packaging policy.
- [ ] Audit Android modules for remaining unimplemented capabilities and record residual gaps.
- [ ] Keep the overall migration goal active until the audit proves all required Android capabilities work on Windows.
