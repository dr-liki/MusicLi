# Service Tile

## Overview
Provides Android Quick Settings (`TileService`) integrations under `com.drliki.MusicLi.data.service.tile` for jumping into the last played playlist or launching shuffle-all playback directly from the system shade.

## Interface
- `[LastPlaylistTileService.kt:LastPlaylistTileService]` — Android N+ `TileService` that resolves and opens the last played playlist in `MainActivity`.
- `[LastPlaylistTileService.kt:LastPlaylistTileService.LastPlaylistTileEntryPoint]` — Hilt `@EntryPoint` exposing `musicRepository()`, `playlistPreferencesRepository()`, and `userPreferencesRepository()`.
- `[LastPlaylistTileService.kt:LastPlaylistTileService.onStartListening]`, `[LastPlaylistTileService.kt:LastPlaylistTileService.onClick]`, `[LastPlaylistTileService.kt:LastPlaylistTileService.onDestroy]` — Lifecycle and click handlers for the last-playlist Quick Settings tile.
- `[ShuffleAllTileService.kt:ShuffleAllTileService]` — Android N+ `TileService` that launches `MainActivity` with `ACTION_SHUFFLE_ALL`.
- `[ShuffleAllTileService.kt:ShuffleAllTileService.onStartListening]`, `[ShuffleAllTileService.kt:ShuffleAllTileService.onClick]` — Lifecycle and click handlers for the shuffle-all Quick Settings tile.
- `[TileServiceCompat.kt:startActivityAndCollapseCompat]` — Internal `TileService` extension bridging API 34+ `PendingIntent`-based `startActivityAndCollapse` with the legacy `Intent` overload.

## Invariants
- `[LastPlaylistTileService.kt:LastPlaylistTileService]` and `[ShuffleAllTileService.kt:ShuffleAllTileService]` require Android N (`@RequiresApi(Build.VERSION_CODES.N)`).
- `[LastPlaylistTileService.kt:LastPlaylistTileService.resolveLaunchablePlaylistId]` validates `lastPlaylistIdFlow.first()`: `"folder_playlist:"`-prefixed IDs must match a URL-decoded folder path via BFS over `musicRepo.getMusicFolders().first()`, and all other IDs must exist in `playlistRepo.getPlaylistsOnce()`, returning `null` otherwise.
- `[LastPlaylistTileService.kt:LastPlaylistTileService.onStartListening]` sets `qsTile.state` to `Tile.STATE_INACTIVE` when a valid playlist ID resolves or `Tile.STATE_UNAVAILABLE` when `null`.
- `[LastPlaylistTileService.kt:LastPlaylistTileService.onClick]` aborts launch if `resolveLaunchablePlaylistId()` returns `null`; otherwise targets `MainActivity` scoped to `packageName` with `ACTION_OPEN_PLAYLIST`, `EXTRA_PLAYLIST_ID`, and `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP`.
- `[ShuffleAllTileService.kt:ShuffleAllTileService.onStartListening]` sets `qsTile.state` to `Tile.STATE_INACTIVE`; `[ShuffleAllTileService.kt:ShuffleAllTileService.onClick]` targets `MainActivity` scoped to `packageName` with `ACTION_SHUFFLE_ALL` and `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP`.
- `[TileServiceCompat.kt:startActivityAndCollapseCompat]` uses `PendingIntent.getActivity` with `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` on `SDK_INT >= UPSIDE_DOWN_CAKE` and `startActivityAndCollapse(intent)` on older APIs.

## Side Effects
- `[LastPlaylistTileService.kt:LastPlaylistTileService.resolveLaunchablePlaylistId]` reads preferences/library state on `Dispatchers.IO` and clears stale persisted state via `prefsRepo.clearLastPlaylist()` when the stored playlist ID is invalid.
- `[LastPlaylistTileService.kt:LastPlaylistTileService.onStartListening]` and `[ShuffleAllTileService.kt:ShuffleAllTileService.onStartListening]` update system Quick Settings tile state via `qsTile?.updateTile()`.
- `[LastPlaylistTileService.kt:LastPlaylistTileService.onClick]` displays a short `Toast` (`R.string.tile_last_playlist_unavailable`) and updates tile state if unavailable, or launches `MainActivity` and collapses Quick Settings via `startActivityAndCollapseCompat`.
- `[ShuffleAllTileService.kt:ShuffleAllTileService.onClick]` launches `MainActivity` and collapses Quick Settings via `startActivityAndCollapseCompat`.
- `[LastPlaylistTileService.kt:LastPlaylistTileService.onDestroy]` cancels `serviceScope` coroutines.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
