# Source Browser Session Design

**Status:** in progress; search, detail, TOC and text-reader re-login increment implemented

**Goal:** Make a desktop source login or verification session reusable by the
source-aware JVM HTTP requests used for search, book details, TOC refresh and
content loading.

## Scope

This increment covers the existing JavaFX browser verification flow, explicit
login/verification entry points, source-scoped Cookie capture and persistence,
request reuse, and detectable session expiry. It does not claim WebView2
parity, Android `getVerificationResult` parity, CAPTCHA callbacks, LAN browser
access, or a complete JavaScript debugger.

## Existing Boundaries

- `SourceModel` validates source URLs and opens the embedded browser.
- `EmbeddedBrowserVerificationModel` captures browser Cookies, persists them
  through `CoreLibrary`, and can refetch the verification URL.
- `CoreSourceHttpClient` merges persistent and in-memory session Cookies into
  source requests and accepts `Set-Cookie` responses.
- `SqliteCoreLibrary` stores persistent Cookies; session Cookies remain in the
  HTTP client instance and are intentionally excluded from backups.

The migration must preserve these boundaries. The browser is responsible for
interactive login; the source-aware HTTP client is responsible for subsequent
source requests.

## Data Flow

```text
Source editor or debug page
    -> validate source login/verification URL
    -> JavaFX WebView loads the page
    -> user completes login
    -> capture document.cookie and browser CookieManager values
    -> save Cookies scoped to the source host/path
    -> source-aware HTTP request merges the same Cookies
    -> classify the response as authenticated, login-required, or failed
```

## Session State

The desktop request boundary exposes one assessment for a source request:

- `AUTHENTICATED`: a 2xx/3xx response without a login-page signal.
- `LOGIN_REQUIRED`: HTTP 401/403, a redirect to a login/sign-in path, or a
  response containing a password form.
- `HTTP_ERROR`: another non-2xx/3xx response.
- `TRANSPORT_ERROR`: the request did not produce an HTTP response.

Classification is conservative. A normal page containing the word "login"
does not invalidate a session; only status codes, login-oriented URL paths, or
password form markers do.

## Cookie Rules

1. Cookies must match both the request host and the source host before they
   are injected.
2. Manual `Cookie` headers remain authoritative for duplicate cookie names.
3. `enabledCookieJar = false` disables source Cookie injection and persistence.
4. Persistent Cookies are stored in SQLite and included in local backups.
5. Session Cookies remain in the current client instance and are not backed up.
6. Completing a failed login must not delete an existing valid Cookie unless a
   server response explicitly expires that Cookie.

## Incremental Interfaces

The first implementation increment adds a pure classifier, exposes it on
source request-debug results, and offers the detected login URL to the existing
embedded browser action:

```kotlin
enum class SourceSessionStatus { AUTHENTICATED, LOGIN_REQUIRED, HTTP_ERROR, TRANSPORT_ERROR }

object SourceSessionAssessment {
    fun classify(statusCode: Int?, finalUrl: String?, body: String): SourceSessionStatus
}
```

The classifier is consumed by `SourceDebugModel`, online search, book details,
TOC and text-content services. The core services throw a structured
`CoreSourceSessionException` containing the source URL and validated login URL;
desktop search, detail and text-reader models preserve that state for the UI.

When a request debug result is `LOGIN_REQUIRED`, the source editor displays an
explicit re-login state and uses the final HTTP(S) login URL for the embedded
browser. Browser completion continues to reuse the existing Cookie capture and
source-aware refetch path.

Search, book detail/TOC refresh and text reading expose the same explicit
re-login action when their source request requires authentication. Online image
reading, discovery and RSS flows remain follow-up integrations.

## Test and Acceptance Plan

- 401 and 403 classify as `LOGIN_REQUIRED`.
- A redirect whose path contains `/login`, `/signin` or `/auth` classifies as
  `LOGIN_REQUIRED`.
- A response containing a password input classifies as `LOGIN_REQUIRED`.
- Ordinary 2xx/3xx content containing unrelated login text remains
  `AUTHENTICATED`.
- Other HTTP errors classify as `HTTP_ERROR`.
- Missing status codes classify as `TRANSPORT_ERROR`.
- `SourceDebugModel` exposes the classification while preserving response
  status, headers and body for inspection.
- A login-required debug result exposes only a validated HTTP(S) final URL for
  the re-login action; authenticated results expose no login URL.
- Existing Cookie isolation, persistence and browser verification tests remain
  passing.

## Explicit Non-Goals

- Bidirectional WebDAV reading-progress synchronization.
- Automatic credential storage or password autofill.
- CAPTCHA solving or bypass.
- Silent background re-login.
- Replacing JavaFX WebView with WebView2 in this increment.
