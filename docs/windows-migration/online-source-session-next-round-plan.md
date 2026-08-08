# Online Source Session Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing source-session re-login flow to online image reading, discovery, and RSS workflows without changing Cookie storage or browser behavior.

**Architecture:** Keep `CoreSourceSessionException` as the platform-independent request result. Each desktop model captures `status`, `sourceUrl`, and `loginUrl`; Compose screens expose the same explicit re-login action through `SourceModel.openEmbeddedBrowserVerification`. Online image, discovery, and RSS are separate tasks so each can be tested and committed independently.

**Tech Stack:** Kotlin/JVM, Gradle, Compose Desktop, existing JavaFX WebView verification, `CoreSourceSessionException`, JUnit 4, `InMemoryCoreLibrary`.

## Global Constraints

- Preserve the existing `CoreSourceHttpClient` Cookie isolation and persistence rules.
- Do not add WebView2, password storage, automatic credential filling, CAPTCHA handling, or silent background re-login.
- Re-login buttons require a validated HTTP(S) URL and a non-blank source URL.
- Use TDD: every production change starts with a failing test.
- Use the existing `SourceModel.openEmbeddedBrowserVerification` entry point.
- Keep session Cookies in the current HTTP client instance; do not change backup semantics.
- Use ASCII for newly edited source and Markdown files unless existing content requires otherwise.
- Run Gradle with `--no-daemon --max-workers=1`.

## Current Baseline

The previous commit `38288657a` already provides:

- `CoreSourceSessionStatus` and `CoreSourceSessionException` in `modules:core`.
- Core request checks for search, explore, book details, TOC refresh, and text content.
- `SearchModel`, `BookDetailModel`, and `ReaderModel` session fields.
- Compose re-login actions for search, book detail, and text reader.
- JavaFX Cookie capture, persistent Cookie storage, and source-aware refetch.

This plan only handles remaining desktop consumers of the same core request path.

## Task 1: Online Image Reader Re-login

**Files:**

- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/OnlineImageReaderModel.kt`
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/Main.kt`
- Test: `desktop/app/src/test/kotlin/io/legado/desktop/OnlineImageReaderModelTest.kt`

**Interfaces:**

- Consumes: `OnlineBookService.loadContent`, `CoreSourceSessionException`, and `SourceModel.openEmbeddedBrowserVerification`.
- Produces: `sessionStatus: CoreSourceSessionStatus?`, `loginUrl: String?`, and `loginSourceUrl: String?` on `OnlineImageReaderModel`.

- [ ] **Step 1: Write the failing test.**

Add a test that creates an online image book, one chapter, and an injected `CoreHttpClient` returning HTTP 401 with a final URL of `https://source.example/login`. Call the model's current-page/chapter loading method and assert:

```kotlin
assertEquals(CoreSourceSessionStatus.LOGIN_REQUIRED, model.sessionStatus)
assertEquals("https://source.example/login", model.loginUrl)
assertEquals(source.bookSourceUrl, model.loginSourceUrl)
```

- [ ] **Step 2: Run the focused test and verify the expected red failure.**

```powershell
.\gradlew.bat :desktop:app:test --tests io.legado.desktop.OnlineImageReaderModelTest --no-daemon --max-workers=1
```

Expected: test compilation fails because the model has no session fields, or the new assertion fails because the exception is currently reduced to a plain error string.

- [ ] **Step 3: Implement the minimal model capture.**

In the model's online content loading `catch` block, preserve existing `error` behavior and add:

```kotlin
if (throwable is CoreSourceSessionException) {
    sessionStatus = throwable.status
    loginUrl = throwable.loginUrl
    loginSourceUrl = throwable.sourceUrl
}
```

Clear those fields at the start of a new online load. Do not alter image parsing, memory cache, disk cache, page navigation, or progress persistence.

- [ ] **Step 4: Run the focused test and verify green.**

```powershell
.\gradlew.bat :desktop:app:test --tests io.legado.desktop.OnlineImageReaderModelTest --no-daemon --max-workers=1
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Add the Compose action.**

Pass the existing `sourceModel` into `OnlineImageReaderScreen`. When the model reports `LOGIN_REQUIRED` and both URLs are non-blank, render `重新登录` beside the existing image-reader error. The click handler must call:

```kotlin
sourceModel.openEmbeddedBrowserVerification(
    url = model.loginUrl!!,
    sourceUrl = model.loginSourceUrl!!,
    title = "书源登录"
)
```

On success show `已打开内置浏览器，请完成登录后重试`; on failure show the exception message.

- [ ] **Step 6: Commit the independently tested task.**

```powershell
git add desktop/app/src/main/kotlin/io/legado/desktop/OnlineImageReaderModel.kt desktop/app/src/main/kotlin/io/legado/desktop/Main.kt desktop/app/src/test/kotlin/io/legado/desktop/OnlineImageReaderModelTest.kt
git commit -m "feat(desktop): add online image source re-login"
```

## Task 2: Discovery Re-login

**Files:**

- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/ExploreModel.kt`
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/Main.kt`
- Test: `desktop/app/src/test/kotlin/io/legado/desktop/ExploreModelTest.kt`

**Interfaces:**

- Consumes: `BookSourceSearchService.explore`, `CoreSourceSessionException`, and the selected source already held by `ExploreModel`.
- Produces: `sessionStatus`, `loginUrl`, and `loginSourceUrl` on `ExploreModel`.

- [ ] **Step 1: Write the failing test.**

Configure one enabled source with `enabledExplore = true`, an `exploreUrl`, and a `ruleExplore`; inject a client returning HTTP 403 with final URL `https://source.example/signin`. Select the source, call `load()`, and assert the three session fields.

- [ ] **Step 2: Run the focused test.**

```powershell
.\gradlew.bat :desktop:app:test --tests io.legado.desktop.ExploreModelTest --no-daemon --max-workers=1
```

Expected: red because `ExploreModel` currently stores only the error string.

- [ ] **Step 3: Capture the structured exception.**

In `ExploreModel.load` and `loadNextPage`, clear session fields before the request. In the shared failure path, preserve the existing error and filtered-result behavior, then capture `CoreSourceSessionException` fields. Do not catch or rewrite the exception inside `BookSourceSearchService.explore`; it already uses the core response check.

- [ ] **Step 4: Run the focused test.**

```powershell
.\gradlew.bat :desktop:app:test --tests io.legado.desktop.ExploreModelTest --no-daemon --max-workers=1
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Add the discovery UI action.**

Pass `sourceModel` to `ExploreScreen`. In the error state, when the selected source and validated login URL exist, show `重新登录` and open the selected source's login URL through the existing embedded browser launcher. Keep source selection, pagination, filter counts, and result deduplication unchanged.

- [ ] **Step 6: Commit the independently tested task.**

```powershell
git add desktop/app/src/main/kotlin/io/legado/desktop/ExploreModel.kt desktop/app/src/main/kotlin/io/legado/desktop/Main.kt desktop/app/src/test/kotlin/io/legado/desktop/ExploreModelTest.kt
git commit -m "feat(desktop): add discovery source re-login"
```

## Task 3: RSS Re-login

**Files:**

- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/SubscriptionModel.kt`
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/SubscriptionDirectoryModel.kt`
- Modify: `desktop/app/src/main/kotlin/io/legado/desktop/Main.kt`
- Test: `desktop/app/src/test/kotlin/io/legado/desktop/SubscriptionModelTest.kt`
- Test: `desktop/app/src/test/kotlin/io/legado/desktop/SubscriptionDirectoryModelTest.kt` if the directory model has a dedicated test source in the current tree

**Interfaces:**

- Consumes: RSS source refresh through `BookSourceSearchService.explore`, subscription article loading through `OnlineBookService`, and `CoreSourceSessionException`.
- Produces: session fields on RSS refresh/article models and one explicit re-login action shared by the subscription screen.

- [ ] **Step 1: Write the failing refresh test.**

Configure an RSS source whose explore request returns HTTP 401 at `https://rss.example/login`, call `SubscriptionModel.refresh`, and assert `LOGIN_REQUIRED`, the login URL, and the RSS source URL.

- [ ] **Step 2: Run the focused test.**

```powershell
.\gradlew.bat :desktop:app:test --tests io.legado.desktop.SubscriptionModelTest --no-daemon --max-workers=1
```

Expected: red because the model currently exposes only `error`.

- [ ] **Step 3: Capture refresh and article session failures.**

Add the three session fields to the model that owns the failing RSS operation. Clear them before refresh/article loading. Preserve existing subscription pages, selected source, cached article, and summary fallback behavior for non-authentication errors.

- [ ] **Step 4: Run refresh and article tests.**

```powershell
.\gradlew.bat :desktop:app:test --tests io.legado.desktop.SubscriptionModelTest --tests io.legado.desktop.SubscriptionDirectoryModelTest --no-daemon --max-workers=1
```

Expected: `BUILD SUCCESSFUL`; if `SubscriptionDirectoryModelTest` does not exist, omit that selector and retain the model test.

- [ ] **Step 5: Add the subscription UI action.**

Pass `sourceModel` into `SubscriptionScreen`. Render one `重新登录` action in the refresh/article error area when the active RSS source and validated login URL are available. Open the URL with `title = "订阅源登录"`, then show `已打开内置浏览器，请完成登录后重试`.

- [ ] **Step 6: Commit the independently tested task.**

```powershell
git add desktop/app/src/main/kotlin/io/legado/desktop/SubscriptionModel.kt desktop/app/src/main/kotlin/io/legado/desktop/SubscriptionDirectoryModel.kt desktop/app/src/main/kotlin/io/legado/desktop/Main.kt desktop/app/src/test/kotlin/io/legado/desktop/SubscriptionModelTest.kt
git commit -m "feat(desktop): add RSS source re-login"
```

## Task 4: Documentation and Regression Gate

**Files:**

- Modify: `docs/windows-migration/source-browser-session-design.md`
- Modify: `docs/windows-migration/compatibility-matrix.md`
- Modify: `docs/windows-migration/feature-inventory.md`

- [ ] **Step 1: Update implementation status.**

Change the design status to state that online image, discovery, and RSS re-login are implemented only after their corresponding tests and UI actions pass. Keep the non-goals unchanged.

- [ ] **Step 2: Run the focused suite.**

```powershell
.\gradlew.bat :modules:core:test --tests io.legado.core.source.CoreSourceSessionTest :desktop:app:test --tests io.legado.desktop.OnlineImageReaderModelTest --tests io.legado.desktop.ExploreModelTest --tests io.legado.desktop.SubscriptionModelTest --no-daemon --max-workers=1
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the full JVM regression.**

```powershell
.\gradlew.bat :modules:core:test :desktop:core-persistence:test :desktop:app:test --rerun-tasks --no-daemon --max-workers=1
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Check the diff.**

```powershell
git diff --check
git status --short --branch
```

Expected: no whitespace errors and a clean tree after commit.

- [ ] **Step 5: Commit documentation and push.**

```powershell
git add docs/windows-migration/source-browser-session-design.md docs/windows-migration/compatibility-matrix.md docs/windows-migration/feature-inventory.md
git commit -m "docs(windows): record online source session coverage"
git push origin codex/windows-reader-migration
```

## Scope Gaps After This Round

- Automatic retry after login completion is intentionally not included; the
  user must press the existing retry/refresh action.
- Online image page fetch failures after chapter HTML extraction may still be
  plain image-load errors if they bypass `OnlineBookService`.
- WebView2, browser profile sharing, CAPTCHA callbacks, background re-login,
  full Android `AnalyzeRule` parity, and WebDAV bidirectional synchronization
  remain outside this plan.

## Self-Review Checklist

- [ ] Every task has explicit files, test command, expected failure, minimal implementation, and commit command.
- [ ] No task changes the Cookie schema or backup semantics.
- [ ] The three workflows are independently testable and can be reverted independently.
- [ ] The final gate includes both focused tests and the full JVM regression.
