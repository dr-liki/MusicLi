# Provider

## Overview
Provides a read-only Android `ContentProvider` (`SharedArtworkContentProvider`) under `com.drliki.MusicLi.data.provider` for securely sharing local song album art and cached cloud artwork with MediaSession controllers and external system consumers.

## Interface
- `[SharedArtworkContentProvider.kt:SharedArtworkContentProvider]` — Read-only `ContentProvider` implementing `[SharedArtworkContentProvider.kt:onCreate]`, `[SharedArtworkContentProvider.kt:query]`, `[SharedArtworkContentProvider.kt:getType]`, `[SharedArtworkContentProvider.kt:insert]`, `[SharedArtworkContentProvider.kt:delete]`, `[SharedArtworkContentProvider.kt:update]`, `[SharedArtworkContentProvider.kt:openFile]`, and `[SharedArtworkContentProvider.kt:openAssetFile]`.
- `[SharedArtworkContentProvider.kt:authority]` — Computes `<packageName>.artwork` provider authority.
- `[SharedArtworkContentProvider.kt:buildSongUri]`, `[SharedArtworkContentProvider.kt:buildSongUriString]`, `[SharedArtworkContentProvider.kt:parseSongId]` — Builds and parses `content://<authority>/song/<songId>` URIs (with optional cache-bust token).
- `[SharedArtworkContentProvider.kt:buildCloudUri]`, `[SharedArtworkContentProvider.kt:buildCloudUriString]`, `[SharedArtworkContentProvider.kt:parseCloudArtworkUri]` — Builds and parses `content://<authority>/cloud/<urlSafeBase64>` URIs for `navidrome_cover` and `jellyfin_cover` schemes.
- `[SharedArtworkContentProvider.kt:publishSessionArtwork]`, `[SharedArtworkContentProvider.kt:isPublishedSessionArtwork]`, `[SharedArtworkContentProvider.kt:clearPublishedSessionArtwork]` — Tracks recently published session artwork URIs for `MEDIA_CONTENT_CONTROL` access checks.
- `[SharedArtworkContentProvider.kt:resolveCachedCloudArtwork]`, `[SharedArtworkContentProvider.kt:isSafeForExternalArtworkReader]`, `[SharedArtworkContentProvider.kt:hasArtworkReadAccess]` — Internal security, caching, and access-validation helpers.
- `[SharedArtworkContentProvider.kt:MAX_SHARED_CLOUD_ARTWORK_BYTES]`, `[SharedArtworkContentProvider.kt:MAX_SHARED_CLOUD_ARTWORK_DIMENSION_PX]`, `[SharedArtworkContentProvider.kt:ArtworkImageBounds]` — External artwork size/dimension limits (`2 MiB`, `2048 px`) and bounds container.

## Invariants
- `[SharedArtworkContentProvider.kt:openFile]` enforces read-only access (`mode == "r"` required, throwing `FileNotFoundException` otherwise); `[SharedArtworkContentProvider.kt:query]` and `[SharedArtworkContentProvider.kt:insert]` return `null`; `[SharedArtworkContentProvider.kt:delete]` and `[SharedArtworkContentProvider.kt:update]` return `0`.
- `[SharedArtworkContentProvider.kt:hasArtworkReadAccess]` permits reads only for self (`callingUid == providerUid`), explicit `FLAG_GRANT_READ_URI_PERMISSION`, or `Manifest.permission.MEDIA_CONTENT_CONTROL` matching a published session artwork URI; denied access throws `FileNotFoundException` in `[SharedArtworkContentProvider.kt:openFile]`.
- `[SharedArtworkContentProvider.kt:openCachedCloudArtwork]` never performs synchronous network fetches on `openFile`; uncached cloud artwork triggers asynchronous warmup and throws `FileNotFoundException`.
- `[SharedArtworkContentProvider.kt:isSafeForExternalArtworkReader]` restricts externally shared artwork (all cloud artwork and non-self song artwork) to readable files sized `1..MAX_SHARED_CLOUD_ARTWORK_BYTES` (`2 MiB`), recognized by `RemoteArtworkCache.isCachedImage`, with decoded dimensions `1..MAX_SHARED_CLOUD_ARTWORK_DIMENSION_PX` (`2048 px`).
- `[SharedArtworkContentProvider.kt:isSupportedCloudArtworkUri]` restricts cloud URIs to `navidrome_cover` and `jellyfin_cover` schemes encoded with URL-safe Base64 without padding.
- `[SharedArtworkContentProvider.kt:publishSessionArtwork]` synchronizes on `publishedSessionArtwork`, deduplicates canonical `"song:<id>"` / `"cloud:<rawUri>"` keys, and bounds the queue to `MAX_PUBLISHED_SESSION_ARTWORK` (`3`).
- `[SharedArtworkContentProvider.kt:warmCloudArtworkCache]` deduplicates concurrent image warmups per URI using `ConcurrentHashMap.newKeySet()`.

## Side Effects
- `[SharedArtworkContentProvider.kt:getType]` reads cached artwork metadata from disk via `RemoteArtworkCache` and `AlbumArtUtils`.
- `[SharedArtworkContentProvider.kt:openFile]` invokes `AlbumArtUtils.ensureAlbumArtCachedFile` or `AlbumArtUtils.getOrWarmCachedAlbumArtFile` (disk read/write) and opens a read-only `ParcelFileDescriptor`.
- `[SharedArtworkContentProvider.kt:openCachedCloudArtwork]` and `[SharedArtworkContentProvider.kt:decodeArtworkImageBounds]` read image files from disk.
- `[SharedArtworkContentProvider.kt:warmCloudArtworkCache]` mutates `cloudArtworkWarmups` and launches background Coil `ImageLoader.execute` requests on `SupervisorJob() + Dispatchers.IO` to populate disk artwork cache.
- `[SharedArtworkContentProvider.kt:publishSessionArtwork]` and `[SharedArtworkContentProvider.kt:clearPublishedSessionArtwork]` mutate the in-memory `publishedSessionArtwork` queue.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
