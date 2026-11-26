# image

## Overview
Coil `Fetcher` and `Mapper` implementations plus a shared signature-verified disk cache for local song artwork and Navidrome/Jellyfin cloud album covers.

## Interface
- `[JellyfinCoilFetcher.kt:JellyfinCoilFetcher]` & `[JellyfinCoilFetcher.kt:JellyfinCoilFetcher.Factory]`: Coil `Fetcher` for `jellyfin_cover://` URIs.
- `[NavidromeCoilFetcher.kt:NavidromeCoilFetcher]` & `[NavidromeCoilFetcher.kt:NavidromeCoilFetcher.Factory]`: Coil `Fetcher` for `navidrome_cover://` URIs, plus internal helpers `withNavidromeCoverSize` and `resolveCloudCoverSize`.
- `[LocalArtworkCoilFetcher.kt:LocalArtworkCoilFetcher]` & `[LocalArtworkCoilFetcher.kt:LocalArtworkCoilFetcher.Factory]`: Coil `Fetcher` for local artwork URIs (`LocalArtworkUri`).
- `[SharedCloudArtworkMappers.kt:SharedCloudArtworkStringMapper]` & `[SharedCloudArtworkMappers.kt:SharedCloudArtworkUriMapper]`: Coil `Mapper`s translating `content://<pkg>.artwork/cloud/...` URIs back to raw cloud cover URIs.
- `[RemoteArtworkCache.kt:RemoteArtworkCache]`: Internal shared disk cache and OkHttp download coordinator (`DEFAULT_COVER_SIZE_PX = 500`, `cacheFileName`, `cacheFileFor`, `isCachedImage`, `cachedResult`, `download`, `imageMimeType`).

## Invariants
- `[RemoteArtworkCache.kt:RemoteArtworkCache]`: Only persists downloads whose leading bytes match a known image signature (`JPEG`, `PNG`, `GIF`, `WEBP`, `AVIF`, `HEIF`, `BMP`) via temp-file write + atomic `renameTo`, and serializes concurrent fetches for the same file path with `downloadLocks`.
- `[NavidromeCoilFetcher.kt:resolveCloudCoverSize]`: Explicit `?size=` query parameters take precedence (`>= 0`); otherwise maps Coil `Size` into `500`, `800`, or `0` (original).
- `[JellyfinCoilFetcher.kt:JellyfinCoilFetcher]` & `[NavidromeCoilFetcher.kt:NavidromeCoilFetcher]`: Skip fetches when `!repository.isLoggedIn` or cover IDs are blank, rethrow `CancellationException`, and rate-limit duplicate failure logs to once per `60_000L` ms.
- `[JellyfinCoilFetcher.kt:JellyfinCoilFetcher]`: Backs off all Jellyfin cover downloads for `30_000L` ms (`SERVER_UNREACHABLE_BACKOFF_MS`) on connection failures, falling back to `cachedAlbumCover` without caching it under the song's key.
- `[SharedCloudArtworkMappers.kt:SharedCloudArtworkUriMapper]`: Only maps URIs whose authority equals `SharedArtworkContentProvider.authority(packageName)`.

## Side Effects
- `[RemoteArtworkCache.kt:download]`: Executes async `OkHttpClient` HTTP requests (cancelled on coroutine cancellation) and reads/writes `.tmp` and `.jpg` files in `cacheDir` on `Dispatchers.IO`.
- `[LocalArtworkCoilFetcher.kt:fetch]`: Extracts/caches local album art files via `AlbumArtUtils.ensureAlbumArtCachedFile` and reads them from disk.
- `[JellyfinCoilFetcher.kt:fetch]` & `[NavidromeCoilFetcher.kt:fetch]`: Mutate companion failure-cooldown maps, `serverUnreachableUntilNanos` (Jellyfin), and emit `Timber` logs.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
