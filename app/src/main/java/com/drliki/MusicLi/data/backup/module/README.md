# backup/module

## Overview
Modular backup handlers (`BackupModuleHandler`) and cross-device song ID resolution (`BackupSongResolver`) implementing export, entry counting, snapshot capture, restore, and rollback for each `BackupSection`.

## Interface
- `[BackupModuleHandler.kt:BackupModuleHandler]`: Contract for per-section handlers (`section`, `export`, `countEntries`, `snapshot`, `restore`, `rollback`).
- `[BackupSongResolver.kt:BackupSongResolver]` & `[BackupSongResolver.kt:BackupSongMetadata]`: Resolves local MediaStore song IDs across devices/rescans (`metadataFor`, `songsFor`, `library`, `sameTag`) via inner snapshot matcher `Library` (`songIdsTitled`, `resolve`).
- `[ArtistImagesModuleHandler.kt:ArtistImagesModuleHandler]`: Handles `BackupSection.ARTIST_IMAGES` (artist `imageUrl` and Base64-encoded `customImageUri` files).
- `[AudioBookmarksModuleHandler.kt:AudioBookmarksModuleHandler]`: Handles `BackupSection.AUDIO_BOOKMARKS` with embedded `"song"` metadata and deferred placeholder IDs.
- `[EngagementStatsModuleHandler.kt:EngagementStatsModuleHandler]`: Handles `BackupSection.ENGAGEMENT_STATS` with legacy JSON key aliases and per-`songId` merging.
- `[EqualizerModuleHandler.kt:EqualizerModuleHandler]`: Handles `BackupSection.EQUALIZER` (`EQUALIZER_KEYS`: `"custom_presets_json"`, `"pinned_presets_json"`).
- `[FavoritesModuleHandler.kt:FavoritesModuleHandler]`: Handles `BackupSection.FAVORITES` via `FavoritesDao`.
- `[GlobalSettingsModuleHandler.kt:GlobalSettingsModuleHandler]`: Handles `BackupSection.GLOBAL_SETTINGS` while excluding keys owned by dedicated handlers (`EXCLUDED_KEYS`).
- `[LyricsModuleHandler.kt:LyricsModuleHandler]`: Handles `BackupSection.LYRICS` via `LyricsDao`.
- `[PlaybackHistoryModuleHandler.kt:PlaybackHistoryModuleHandler]`: Handles `BackupSection.PLAYBACK_HISTORY` via `PlaybackStatsRepository`.
- `[PlaylistsModuleHandler.kt:PlaylistsModuleHandler]`: Handles `BackupSection.PLAYLISTS` (`PlaylistsBackupPayload`, `PLAYLIST_KEYS`, `LEGACY_USER_PLAYLISTS_KEY`, `LEGACY_PLAYLIST_ORDER_MODES_KEY`, `LEGACY_PLAYLIST_SORT_OPTION_KEY`), cover images, legacy preference lists, and post-sync deferred song matching.
- `[QuickFillModuleHandler.kt:QuickFillModuleHandler]`: Handles `BackupSection.QUICK_FILL` (`QUICK_FILL_KEYS`: `"custom_genres"`, `"custom_genre_icons"`).
- `[SearchHistoryModuleHandler.kt:SearchHistoryModuleHandler]`: Handles `BackupSection.SEARCH_HISTORY` via `SearchHistoryDao`.
- `[SyncedVideosModuleHandler.kt:SyncedVideosModuleHandler]`: Handles `BackupSection.SYNCED_VIDEOS` via `SyncedVideoDao`.
- `[TransitionsModuleHandler.kt:TransitionsModuleHandler]`: Handles `BackupSection.TRANSITIONS` with `"fromSong"` and `"toSong"` metadata remapping.

## Invariants
- `[BackupSongResolver.kt:Library.resolve]`: Matches backup IDs in order: direct ID verified against `BackupSongMetadata` (when present), unique case-insensitive `title|artist` match, unique `albumName` disambiguation, then `duration` within `DURATION_TOLERANCE_MS` (`2000L` ms); returns `null` when ambiguous. `songsFor` chunks queries at `SQLITE_CHUNK_SIZE = 500`.
- `[PlaylistsModuleHandler.kt:PlaylistsModuleHandler]`: Exports only `"LOCAL"` or smart-playlist sources (`isSmartPlaylistSource`), strips cloud song IDs (`Navidrome` / `Jellyfin`), serializes restore and post-sync resolution under `pendingRestoreMutex`, persists `pending_playlists_restore.json` atomically via `.tmp` + `fsync` + `StandardCopyOption.ATOMIC_MOVE`, and catches non-cancellation exceptions inside `retryPendingWhenIdle` so the `appScope` collector never terminates.
- `[AudioBookmarksModuleHandler.kt:restore]`: Maps unmatched positive MediaStore IDs (`> 0L`) to `restoredBookmarkSongId(backupSongId)` with `albumArtUri = null` to avoid colliding with reused local IDs, while preserving non-positive/cloud IDs unchanged.
- `[TransitionsModuleHandler.kt:restore]`: Drops self-transitions (`fromTrackId != null && fromTrackId == toTrackId`) and deduplicates by `(playlistId, fromTrackId, toTrackId)` before replacing rules.
- `[EngagementStatsModuleHandler.kt:restore]`: Requires a JSON array, rejects non-empty arrays with zero valid entries, clamps counts/durations/timestamps `>= 0`, and merges duplicate `songId` records using `maxOf`.
- `[GlobalSettingsModuleHandler.kt:GlobalSettingsModuleHandler]`: `EXCLUDED_KEYS` equals `PlaylistsModuleHandler.PLAYLIST_KEYS + QuickFillModuleHandler.QUICK_FILL_KEYS + EqualizerModuleHandler.EQUALIZER_KEYS`; `restore` clears all keys except `EXCLUDED_KEYS` before importing filtered entries.
- `[ArtistImagesModuleHandler.kt:rollback]`: `restore` is additive for non-blank `imageUrl` and non-null `customImageBase64` on existing artists, whereas `rollback` resets every artist in `musicDao.getAllArtistsListRaw()` to exact snapshot state.

## Side Effects
- `[ArtistImagesModuleHandler.kt:restore]`, `[ArtistImagesModuleHandler.kt:rollback]`: Reads, writes, and deletes `artist_art_<id>.jpg` in `context.filesDir` and updates `MusicDao` on `Dispatchers.IO`.
- `[PlaylistsModuleHandler.kt:PlaylistsModuleHandler]`: Launches an `appScope` collector on `SyncManager.syncProgress`, writes/deletes `playlist_cover_<id>.jpg` and `pending_playlists_restore.json` in `context.filesDir`, and mutates `PlaylistPreferencesRepository` and `UserPreferencesRepository.clearLegacyUserPlaylists()`.
- `[EqualizerModuleHandler.kt:restore]`, `[GlobalSettingsModuleHandler.kt:restore]`, `[QuickFillModuleHandler.kt:restore]`: Clears and imports preference keys in `UserPreferencesRepository` on `Dispatchers.IO`.
- `[AudioBookmarksModuleHandler.kt:restore]`, `[EngagementStatsModuleHandler.kt:restore]`, `[FavoritesModuleHandler.kt:restore]`, `[LyricsModuleHandler.kt:restore]`, `[PlaybackHistoryModuleHandler.kt:restore]`, `[SearchHistoryModuleHandler.kt:restore]`, `[SyncedVideosModuleHandler.kt:restore]`, `[TransitionsModuleHandler.kt:restore]`: Replaces stored rows in the corresponding Room DAOs or `PlaybackStatsRepository` on `Dispatchers.IO`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
