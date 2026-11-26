# Stream

## Overview
Provides shared cloud audio streaming proxy infrastructure, HTTP security/validation utilities, and artist/disc normalization helpers under `com.drliki.MusicLi.data.stream` for Navidrome and Jellyfin playback.

## Interface
- `[CloudStreamProxy.kt:CloudStreamProxy]` — Abstract loopback Ktor CIO HTTP proxy base class (`<K : Any>`) exposing lifecycle and URI-resolution APIs: `[CloudStreamProxy.kt:CloudStreamProxy.isReady]`, `[CloudStreamProxy.kt:CloudStreamProxy.startIfNeeded]`, `[CloudStreamProxy.kt:CloudStreamProxy.awaitReady]`, `[CloudStreamProxy.kt:CloudStreamProxy.ensureReady]`, `[CloudStreamProxy.kt:CloudStreamProxy.getProxyUrl]`, `[CloudStreamProxy.kt:CloudStreamProxy.resolveUri]`, `[CloudStreamProxy.kt:CloudStreamProxy.start]`, `[CloudStreamProxy.kt:CloudStreamProxy.stop]`, and protected template hooks (`allowedHostSuffixes`, `cacheExpirationMs`, `proxyTag`, `routePath`, `routeParamName`, `uriScheme`, `routePrefix`, `parseRouteParam`, `validateId`, `formatIdForUrl`, `resolveStreamUrl`, `extractIdFromUri`, `upstreamHeaders`, `getOrFetchStreamUrl`).
- `[CloudStreamSecurity.kt:CloudStreamSecurity]` — Security and HTTP validation object exposing `[CloudStreamSecurity.kt:CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES]` (`2 GiB`), `[CloudStreamSecurity.kt:CloudStreamSecurity.RangeHeaderValidation]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.validateNavidromeSongId]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.validateJellyfinItemId]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.validateRangeHeader]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.isSupportedAudioContentType]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.isAcceptableContentLength]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.isSafeRemoteStreamUrl]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.mapUpstreamStatusToProxyStatus]`, `[CloudStreamSecurity.kt:CloudStreamSecurity.isLocalServerHost]`, and `[CloudStreamSecurity.kt:CloudStreamSecurity.isPrivateIpv4Literal]`.
- `[CloudMusicUtils.kt:BulkSyncResult]` — Data class summarizing bulk cloud playlist sync counts (`playlistCount`, `syncedSongCount`, `failedPlaylistCount`).
- `[CloudMusicUtils.kt:CloudMusicUtils]` — Cloud metadata helpers `[CloudMusicUtils.kt:CloudMusicUtils.parseArtistNames]` and `[CloudMusicUtils.kt:CloudMusicUtils.normalizeDiscNumber]`.

## Invariants
- `[CloudStreamProxy.kt:CloudStreamProxy.createServer]` binds strictly to loopback `"127.0.0.1"` on an ephemeral port (`0`); `[CloudStreamProxy.kt:CloudStreamProxy.isReady]` requires `actualPort > 0`, and `[CloudStreamProxy.kt:CloudStreamProxy.awaitReady]` polls every `50L` ms up to `timeoutMs` (default `10_000L`).
- `[CloudStreamProxy.kt:CloudStreamProxy.isAuthorized]` requires a non-empty `@Volatile sessionToken` (`16` `SecureRandom` bytes hex-encoded on `start()`, cleared on `stop()`) and validates the `"t"` query parameter in constant time via `MessageDigest.isEqual`.
- `[CloudStreamProxy.kt:CloudStreamProxy.createServer]` enforces preconditions before upstream dispatch (`404` on invalid token or blank stream URL, `400` on invalid route ID, `416` on invalid `Range` header, `502` if `isSafeRemoteStreamUrl(..., allowHttpForAllowedHosts = true)` fails) and on upstream responses (status must be `200`/`206`, `Content-Type` must pass `isSupportedAudioContentType`, `Content-Length` must pass `isAcceptableContentLength` or return `413`).
- `[CloudStreamProxy.kt:CloudStreamProxy.getProxyUrl]` returns `""` if `!isReady` (`actualPort == 0`) or `!validateId(id)`; `[CloudStreamProxy.kt:CloudStreamProxy.resolveUri]` returns `null` unless `uri.scheme == uriScheme` and ID parsing and validation succeed.
- `[CloudStreamProxy.kt:CloudStreamProxy.CachedUrl]` expires cached upstream URLs once `System.currentTimeMillis() - timestamp > expirationMs`; `[CloudStreamProxy.kt:CloudStreamProxy.streamingClient]` uses a `60L` second read timeout.
- `[CloudStreamSecurity.kt:CloudStreamSecurity.validateNavidromeSongId]` requires non-blank strings `<= 1024` chars without ISO control characters; `[CloudStreamSecurity.kt:CloudStreamSecurity.validateJellyfinItemId]` requires `^[A-Za-z0-9]{1,100}$`.
- `[CloudStreamSecurity.kt:CloudStreamSecurity.validateRangeHeader]` allows `null`/blank headers or single `bytes=<start>-<end>` ranges up to `64` chars with bounds in `0..8_589_934_592L` (`8 GiB`) and `start <= end`.
- `[CloudStreamSecurity.kt:CloudStreamSecurity.isSafeRemoteStreamUrl]` rejects unparseable URLs, embedded credentials, forbidden loopback hosts (`localhost`, `127.0.0.1`, `0.0.0.0`, `::1`, `[::1]`), empty `allowedHostSuffixes`, unapproved hostnames, and non-`https` schemes (except `http` when `allowHttpForAllowedHosts` is `true`).
- `[CloudStreamSecurity.kt:CloudStreamSecurity.isAcceptableContentLength]` accepts `null`/blank or values in `0..MAX_STREAM_CONTENT_LENGTH_BYTES` (`2 GiB`); `[CloudStreamSecurity.kt:CloudStreamSecurity.isSupportedAudioContentType]` accepts `null`/blank, `audio/*`, or `EXTRA_ALLOWED_AUDIO_TYPES`.
- `[CloudMusicUtils.kt:CloudMusicUtils.parseArtistNames]` falls back to `listOf("Unknown Artist")` when splitting on `[,/&;+、]` yields no non-blank tokens; `[CloudMusicUtils.kt:CloudMusicUtils.normalizeDiscNumber]` maps values `<= 0` to `null`.

## Side Effects
- `[CloudStreamProxy.kt:CloudStreamProxy.start]` launches a coroutine on `SupervisorJob() + Dispatchers.IO`, allocates a `SecureRandom` session token, and starts an embedded Ktor CIO server on `127.0.0.1`.
- `[CloudStreamProxy.kt:CloudStreamProxy.stop]` cancels `startJob`/`proxyScope` children, stops the Ktor server (`1000ms` grace, `2000ms` timeout), clears `actualPort`/`sessionToken`, and clears `urlCache`.
- `[CloudStreamProxy.kt:CloudStreamProxy.getOrFetchStreamUrl]` invokes `resolveStreamUrl(id)` and populates `ConcurrentHashMap urlCache`.
- `[CloudStreamProxy.kt:CloudStreamProxy.createServer]` executes outbound HTTP calls via `OkHttpClient` on `Dispatchers.IO`, streams `64 KiB` chunks to Ktor responses, and logs lifecycle/errors via `Timber`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
