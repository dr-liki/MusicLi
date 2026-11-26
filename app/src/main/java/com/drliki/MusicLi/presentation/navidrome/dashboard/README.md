# Navidrome Dashboard

## Overview
Implements the Navidrome/Subsonic server management dashboard UI and ViewModel under `presentation.navidrome.dashboard`. `NavidromeDashboardScreen` renders sync progress/status banners, account summary, music-folder library selection sheet, full/per-playlist sync controls, and synced playlists with song previews, driven by `NavidromeDashboardViewModel` via `NavidromeRepository` and `WorkManager`.

## Interface
- `[NavidromeDashboardScreen.kt:NavidromeDashboardScreen]`: `@Composable fun NavidromeDashboardScreen(viewModel: NavidromeDashboardViewModel = hiltViewModel(), onBack: () -> Unit)`.
- `[NavidromeDashboardViewModel.kt:NavidromeDashboardViewModel]`: `@HiltViewModel class` (`repository: NavidromeRepository`, `workManager: WorkManager`) exposing:
  - StateFlows & properties: `playlists: StateFlow<ImmutableList<NavidromePlaylistEntity>>`, `isSyncing: StateFlow<Boolean>`, `syncProgress: StateFlow<Float?>`, `syncMessage: StateFlow<String?>`, `selectedPlaylistSongs: StateFlow<ImmutableList<Song>>`, `selectedPlaylistName: StateFlow<String?>`, `musicFolders: StateFlow<ImmutableList<NavidromeMusicFolder>>`, `musicFoldersLoadFailed: StateFlow<Boolean>`, `selectedMusicFolderIds: StateFlow<Set<String>>`, `librarySelectionNeedsSync: StateFlow<Boolean>`, `username: String?`, `serverUrl: String?`, `isLoggedIn: StateFlow<Boolean>`, `lastSyncTime: Long`.
  - Actions: `syncAllPlaylistsAndSongs()`, `loadMusicFolders()`, `setSelectedMusicFolderIds(folderIds: Set<String>)`, `syncPlaylistSongs(playlistId: String)`, `loadPlaylistSongs(playlistId: String, playlistName: String)`, `deletePlaylist(playlistId: String)`, `clearSyncMessage()`, `logout()`.

## Invariants
- `[NavidromeDashboardScreen.kt:DashboardContent]`, `[NavidromeDashboardScreen.kt:PlaylistCard]`: Playlists with `id == NavidromeRepository.LIBRARY_PLAYLIST_ID` set `canDelete = false` and guard deletion with `!isLibraryFallback`; per-playlist sync buttons are disabled while `isSyncing` is `true`; empty-playlist placeholder renders only when `playlists.isEmpty() && !isSyncing`; sync banner uses `errorContainer` when `message.contains("failed")`.
- `[NavidromeDashboardScreen.kt:SubsonicMenuCard]`, `[NavidromeDashboardScreen.kt:LibrarySelectorSheet]`, `[NavidromeDashboardScreen.kt:NavidromeLibrarySummaryPanel]`: Normalizes folder selection via `selectedNavidromeMusicFolderIds(musicFolders, selectedMusicFolderIds)`; enables library selector only when `!isSyncing && musicFolders.size > 1`; disables full sync while `isSyncing`; caps summary `FilterChip`s at 3 with a collapsed overflow count chip.
- `[NavidromeDashboardViewModel.kt:init]`: Auto-triggers `syncAllPlaylistsAndSongs()` on creation only when `(System.currentTimeMillis() - repository.lastFullSyncTime) > NavidromeRepository.SYNC_THRESHOLD_MS`.
- `[NavidromeDashboardViewModel.kt:syncAllPlaylistsAndSongs]`, `[NavidromeDashboardViewModel.kt:syncPlaylistSongs]`: Enqueue unique WorkManager jobs `NavidromeSyncWorker.WORK_NAME_ALL` (`ExistingWorkPolicy.KEEP`) and `"navidrome_sync_playlist_$playlistId"` (`ExistingWorkPolicy.REPLACE`).
- `[NavidromeDashboardViewModel.kt:observeSyncWorker]`: Exposes `_syncProgress` only while `state == WorkInfo.State.RUNNING && progress > 0f`; clears `_librarySelectionNeedsSync = false` only on `WorkInfo.State.SUCCEEDED`; defaults `FAILED` messages to `"Sync failed"` when `ERROR_MESSAGE` is `null`.
- `[NavidromeDashboardViewModel.kt:loadPlaylistSongs]`, `[NavidromeDashboardViewModel.kt:loadMusicFolders]`: Cancels any prior `selectedPlaylistJob` before collecting `repository.getPlaylistSongs(playlistId)`; resets `_musicFolders` to `persistentListOf()` and toggles `_musicFoldersLoadFailed` on folder fetch failure.

## Side Effects
- `[NavidromeDashboardScreen.kt:NavidromeDashboardScreen]`, `[NavidromeDashboardScreen.kt:SubsonicMenuCard]`, `[NavidromeDashboardScreen.kt:SongCard]`, `[NavidromeDashboardScreen.kt:PlaylistCard]`: Collects `StateFlow`s via `collectAsStateWithLifecycle()`, toggles `LibrarySelectorSheet`, invokes `viewModel.logout()` + `onBack()`, and loads album/cover art asynchronously via `SmartImage`.
- `[NavidromeDashboardViewModel.kt:observeSyncWorker]`, `[NavidromeDashboardViewModel.kt:syncAllPlaylistsAndSongs]`, `[NavidromeDashboardViewModel.kt:syncPlaylistSongs]`: Observes `workManager.getWorkInfosForUniqueWorkFlow(...)` and enqueues `NavidromeSyncWorker` background work.
- `[NavidromeDashboardViewModel.kt:loadMusicFolders]`, `[NavidromeDashboardViewModel.kt:setSelectedMusicFolderIds]`, `[NavidromeDashboardViewModel.kt:loadPlaylistSongs]`, `[NavidromeDashboardViewModel.kt:deletePlaylist]`, `[NavidromeDashboardViewModel.kt:logout]`: Executes repository network/DB/preferences operations in `viewModelScope` and mutates UI `StateFlow`s.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
