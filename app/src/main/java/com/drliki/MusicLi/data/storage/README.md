# Storage

## Overview
Provides storage inspection and cache-clearing operations under `com.drliki.MusicLi.data.storage` for Explore stream caches, image/artwork caches, temporary external audio files, and cached lyrics.

## Interface
- `[StorageSenseRepository.kt:StorageSenseBreakdown]` — Data class summarizing byte sizes and entry counts across Explore stream cache, image cache, temporary audio cache, lyrics cache, and completed Explore downloads, with computed `[StorageSenseRepository.kt:StorageSenseBreakdown.totalSafeCacheBytes]` and `[StorageSenseRepository.kt:StorageSenseBreakdown.totalManagedCacheBytes]`.
- `[StorageSenseRepository.kt:StorageSenseRepository]` — `@Singleton` repository exposing `[StorageSenseRepository.kt:StorageSenseRepository.inspectStorage]`, `[StorageSenseRepository.kt:StorageSenseRepository.clearExploreStreamCache]`, `[StorageSenseRepository.kt:StorageSenseRepository.clearImageCache]`, `[StorageSenseRepository.kt:StorageSenseRepository.clearTempAudioCache]`, `[StorageSenseRepository.kt:StorageSenseRepository.clearLyricsCache]`, and `[StorageSenseRepository.kt:StorageSenseRepository.clearAllSafeCaches]`.

## Invariants
- `[StorageSenseRepository.kt:StorageSenseBreakdown.totalSafeCacheBytes]` excludes `lyricsCacheBytes` and `exploreDownloadsBytes`, summing only `exploreStreamCacheBytes + imageCacheBytes + tempAudioCacheBytes`.
- `[StorageSenseRepository.kt:StorageSenseRepository.clearAllSafeCaches]` clears Explore stream, image, and temporary audio caches without deleting lyrics cache or completed Explore downloads.
- `[StorageSenseRepository.kt:StorageSenseRepository]` executes all disk traversal and deletion calls on `Dispatchers.IO`.

## Side Effects
- `[StorageSenseRepository.kt:StorageSenseRepository.inspectStorage]` walks `cacheDir` (`image_cache`, `external_artwork`, `external_audio`, `explore_*`/`*.tmp` files), `filesDir/lyrics`, and reads `exploreTrackDownloader.downloads.value` and `exploreStreamCacheManager` metrics.
- `[StorageSenseRepository.kt:StorageSenseRepository.clearExploreStreamCache]` clears `exploreStreamCacheManager` cache, `exploreRepository.clearFeedCache()`, and `dualPlayerEngine.clearResolvedUriCache()`.
- `[StorageSenseRepository.kt:StorageSenseRepository.clearImageCache]` clears Coil `memoryCache` and `diskCache` and recursively deletes contents of `cacheDir/image_cache` and `cacheDir/external_artwork`.
- `[StorageSenseRepository.kt:StorageSenseRepository.clearTempAudioCache]` deletes contents of `cacheDir/external_audio` and any `explore_*` or `*.tmp` files directly in `cacheDir`.
- `[StorageSenseRepository.kt:StorageSenseRepository.clearLyricsCache]` delegates to `lyricsRepository.resetAllLyrics()`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
