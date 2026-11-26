# ListenBrainz Scrobbling & Account Integration

## Overview
Manages opt-in ListenBrainz (and compatible self-hosted servers such as Maloja) authentication, custom endpoint URL rewriting, listen threshold evaluation, persistent offline scrobble queueing, and WorkManager-backed submission.

## Interface
- `[ListenBrainzApiService.kt:ListenBrainzApiService]` - Retrofit interface defining `submitListens` (`POST 1/submit-listens`), `validateToken` (`GET 1/validate-token`), `getListenCount` (`GET 1/user/{userName}/listen-count`), and `getPlayingNow` (`GET 1/user/{userName}/playing-now`) using `Authorization: Token <token>`.
- `[ListenBrainzEndpoint.kt:ListenBrainzEndpoint]` - `@Singleton` holder for `customBaseUrl` with `setCustom`, `rewrite`, `DEFAULT_BASE_URL` (`"https://api.listenbrainz.org/"`), and `parseBaseUrl`.
- `[ListenBrainzModels.kt:ListenBrainzSubmission]` / `ListenBrainzListen` / `ListenBrainzTrackMetadata` / `ListenBrainzAdditionalInfo` / `ListenBrainzTokenValidation` / `ListenBrainzListenCountResponse` / `ListenBrainzListenCountPayload` / `ListenBrainzPlayingNowResponse` / `ListenBrainzPlayingNowPayload` / `ListenBrainzProfileStats` / `ListenBrainzAccountState` / `ListenBrainzSubmitResult` / `buildProfileStats` - Network DTOs, profile/account state models, and sealed submission outcomes (`Success`, `AuthFailed`, `InvalidPayload`, `TransientError`).
- `[ListenBrainzRepository.kt:ListenBrainzRepository]` - `@Singleton` repository exposing `accountState`, `pendingListenCount`, `hasToken`, `isAuthorized`, `enqueueListen`, `connect`, `disconnect`, `fetchProfileStats`, `submitListens`, `submitPlayingNow`, `scheduleFlush`, and `scheduleFlushAfter`, plus `[ListenBrainzRepository.kt:InvalidServerUrlException]`.
- `[ScrobbleManager.kt:ScrobbleManager]` - `@Singleton` bridge from finalized playback sessions to ListenBrainz (`onSessionFinalized`, `onPlayingNow`, `ScrobbleManager.Companion.meetsListenThreshold`).

## Invariants
- `[ListenBrainzRepository.kt:ListenBrainzRepository.queueMutex]` serializes `enqueueListen` and `disconnect` so consent revocation clears credentials and pending listens cleanly without admitting post-disconnect items.
- `[ListenBrainzRepository.kt:ListenBrainzRepository.enqueueListen]` caps `ListenBrainzDao` at `MAX_QUEUE_SIZE = 3000`, dropping the oldest rows on overflow.
- `[ListenBrainzRepository.kt:ListenBrainzRepository.isAuthorized]` requires both a stored token and `needsReauth == false`; HTTP `401` in `submit` sets `KEY_AUTH_INVALID = true` so listens continue queueing locally while outbound flushes pause until reconnect.
- `[ListenBrainzEndpoint.kt:ListenBrainzEndpoint.parseBaseUrl]` guarantees a trailing `/` and defaults scheme-less inputs to `https://` so relative API paths preserve sub-path prefixes (e.g., Maloja's `/apis/listenbrainz/`).
- `[ScrobbleManager.kt:ScrobbleManager.Companion.meetsListenThreshold]` enforces the ListenBrainz rule: `minOf(4 minutes, trackDurationMs / 2)` when `trackDurationMs > 0`, otherwise `4 minutes`; `[ScrobbleManager.kt:ScrobbleManager.onPlayingNow]` debounces duplicate `songId` reports within `PLAYING_NOW_DEBOUNCE_MS` (`30_000L` ms) and requires non-blank `title`, `artistName`, and enabled `ListenBrainzSource` toggle.
- `[ListenBrainzModels.kt:ListenBrainzListen.listenedAt]` must remain `null` for `playing_now` payloads and is converted to epoch seconds (`listenedAtMs / 1000`) for `import` submissions.

## Side Effects
- Encrypted SharedPreferences (`listenbrainz_prefs`, falling back to `listenbrainz_prefs_plain`) storing token, username, `auth_invalid`, and optional `server_url` (`[ListenBrainzRepository.kt:ListenBrainzRepository.createCredentialPrefs]`).
- Room mutations via `ListenBrainzDao` (`insert`, `deleteOldest`, `clear`) and WorkManager unique work scheduling for `ScrobbleFlushWorker` (`[ListenBrainzRepository.kt:ListenBrainzRepository.scheduleFlush]`, `scheduleFlushAfter`).
- Network HTTP requests via `ListenBrainzApiService` for token validation, profile stats, `playing_now`, and listen batch imports; mutates `ListenBrainzEndpoint.customBaseUrl`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
