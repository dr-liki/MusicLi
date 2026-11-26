# Stats Presentation Helpers

## Overview
Provides lightweight presentation helpers for playback statistics under `presentation.stats`: concurrent artist image URL resolution (`StatsArtistImages.kt`) and `StatsTimeRange` UI string resource mapping (`StatsTimeRangeUi.kt`).

## Interface
- `[StatsArtistImages.kt:resolveStatsArtistImages]`: `internal suspend fun resolveStatsArtistImages(names: Collection<String>, musicRepository: MusicRepository, artistImageRepository: ArtistImageRepository): Map<String, String>`.
- `[StatsTimeRangeUi.kt:displayNameRes]`: `@StringRes fun StatsTimeRange.displayNameRes(): Int`.

## Invariants
- `[StatsArtistImages.kt:resolveStatsArtistImages]`, `[StatsArtistImages.kt:UNKNOWN_ARTIST_ID]`: Deduplicates `names.distinct()`, uses `-1L` (`UNKNOWN_ARTIST_ID`) when `musicRepository.getArtistIdByName(name)` returns `null`, and omits entries whose resolved URL is `null`, blank, or fails with an exception (`runCatching`).
- `[StatsTimeRangeUi.kt:displayNameRes]`: Exhaustive `when (this)` mapping every `StatsTimeRange` (`DAY`, `WEEK`, `MONTH`, `YEAR`, `ALL`) to `R.string.presentation_batch_g_stats_range_*`.

## Side Effects
- `[StatsArtistImages.kt:resolveStatsArtistImages]`: Launches concurrent child coroutines via `coroutineScope`/`async`/`awaitAll`, queries `MusicRepository` and `ArtistImageRepository`, and logs failures with `Timber.w`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
