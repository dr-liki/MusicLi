# Presentation Models

## Overview
Defines UI-layer presentation models and helpers under `presentation.model`. Covers recently played song UI mapping and time-range filtering (`RecentlyPlayedSongUi.kt`) and settings category metadata (`SettingsCategory.kt`).

## Interface
- `[RecentlyPlayedSongUi.kt:RecentlyPlayedSongUiModel]`: `@Parcelize data class RecentlyPlayedSongUiModel(val song: Song, val lastPlayedTimestamp: Long) : Parcelable`.
- `[RecentlyPlayedSongUi.kt:mapRecentlyPlayedSongs]`: `fun mapRecentlyPlayedSongs(playbackHistory: List<PlaybackStatsRepository.PlaybackHistoryEntry>, songs: List<Song>, range: StatsTimeRange? = null, nowMillis: Long = System.currentTimeMillis(), maxItems: Int = Int.MAX_VALUE): List<RecentlyPlayedSongUiModel>`.
- `[RecentlyPlayedSongUi.kt:collectRecentlyPlayedSongIds]`: `fun collectRecentlyPlayedSongIds(playbackHistory: List<PlaybackStatsRepository.PlaybackHistoryEntry>, range: StatsTimeRange? = null, nowMillis: Long = System.currentTimeMillis(), maxItems: Int = Int.MAX_VALUE): List<String>`.
- `[RecentlyPlayedSongUi.kt:withoutSong]`: `fun List<RecentlyPlayedSongUiModel>.withoutSong(songId: String): List<RecentlyPlayedSongUiModel>`.
- `[RecentlyPlayedSongUi.kt:withUpdatedSongs]`: `fun List<RecentlyPlayedSongUiModel>.withUpdatedSongs(latest: List<RecentlyPlayedSongUiModel>): List<RecentlyPlayedSongUiModel>`.
- `[SettingsCategory.kt:SettingsCategory]`: `enum class SettingsCategory(val id: String, @StringRes val titleRes: Int, @StringRes val subtitleRes: Int, val icon: ImageVector? = null, val iconRes: Int? = null)` with entries `LIBRARY`, `APPEARANCE`, `PLAYBACK`, `EQUALIZER`, `BEHAVIOR`, `BACKUP_RESTORE`, `DEVELOPER`, `DEVICE_CAPABILITIES`, `ABOUT`, and `Companion.fromId(id: String): SettingsCategory?`.

## Invariants
- `[RecentlyPlayedSongUi.kt:mapRecentlyPlayedSongs]`, `[RecentlyPlayedSongUi.kt:collectRecentlyPlayedSongIds]`: Return `emptyList()` when `maxItems <= 0`, `playbackHistory` is empty, or `songs` is empty; sort by descending timestamp then ascending `songId`, clamp timestamps and `nowMillis` with `coerceAtLeast(0L)`, filter within `startBound..endBound` (where `[RecentlyPlayedSongUi.kt:resolveBounds]` computes `DAY`, Monday-anchored `WEEK`, `MONTH`, `YEAR`, or unbounded `ALL`/`null`), deduplicate via `LinkedHashSet`, skip missing songs, and cap at `maxItems`.
- `[RecentlyPlayedSongUi.kt:withoutSong]`, `[RecentlyPlayedSongUi.kt:withUpdatedSongs]`: Preserve list ordering and return the receiver unchanged when no entries match or change.

## Side Effects
- `[RecentlyPlayedSongUi.kt:mapRecentlyPlayedSongs]`, `[RecentlyPlayedSongUi.kt:collectRecentlyPlayedSongIds]`: Read the system clock via default parameter `System.currentTimeMillis()` and system timezone via `ZoneId.systemDefault()`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
