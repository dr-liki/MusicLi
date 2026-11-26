# Paging

## Overview
Provides Jetpack Paging 3 integration for loading ordered `Song` pages from Android `MediaStore` against a pre-filtered list of media IDs.

## Interface
- `[MediaStorePagingSource.kt:MediaStorePagingSource]` - `PagingSource<Int, Song>` constructed with `context`, `filteredIds: List<Long>`, and `songIdToGenreMap: Map<Long, String>`, implementing `getRefreshKey` and `load`.

## Invariants
- `[MediaStorePagingSource.kt:MediaStorePagingSource.load]` runs on `Dispatchers.IO`, slices `filteredIds` by `pageIndex * params.loadSize`, returns `LoadResult.Page(emptyList(), ...)` when `filteredIds` is empty or `start >= filteredIds.size`, and preserves the input `filteredIds` slice order via `idsToLoad.mapNotNull { songsMap[it] }`.
- `[MediaStorePagingSource.kt:MediaStorePagingSource.fetchSongDetails]` queries `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with `_ID IN (...)`, normalizes `trackNumber` via `% 1000`, injects genres from `songIdToGenreMap`, and leaves `artists = emptyList()`, `lyrics = null`, `isFavorite = false`, `mimeType = null`, `bitrate = null`, and `sampleRate = null`.

## Side Effects
- Queries Android `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` via `context.contentResolver.query` and resolves album art URIs via `AlbumArtUtils.getAlbumArtUri` (`[MediaStorePagingSource.kt:MediaStorePagingSource.fetchSongDetails]`).
- Logs load exceptions via `Timber` (`[MediaStorePagingSource.kt:MediaStorePagingSource.load]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
