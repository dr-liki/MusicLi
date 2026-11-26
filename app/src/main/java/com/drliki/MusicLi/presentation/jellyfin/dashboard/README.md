# Jellyfin Dashboard

## Overview
Implements the Jellyfin server management dashboard UI and ViewModel under `presentation.jellyfin.dashboard`. `JellyfinDashboardScreen` renders sync banners, account status, music library selection sheets, favorites sync toggles, and synced Jellyfin playlists, backed by `JellyfinDashboardViewModel` coordinating `JellyfinRepository`.

## Interface
- `[JellyfinDashboardScreen.kt:JellyfinDashboardScreen]`: `@Composable fun JellyfinDashboardScreen(viewModel: JellyfinDashboardViewModel = hiltViewModel(), onBack: () -> Unit)`.
- `[JellyfinDashboardViewModel.kt:JellyfinDashboardViewModel]`: `@HiltViewModel class` (`repository: JellyfinRepository`, `@ApplicationContext context: Context`) exposing:
  - StateFlows & properties: `playlists: StateFlow<ImmutableList<JellyfinPlaylistEntity>>`, `isSyncing: StateFlow<Boolean>`, `syncMessage: StateFlow<String?>`, `selectedPlaylistSongs: StateFlow<ImmutableList<Song>>`, `libraries: StateFlow<ImmutableList<JellyfinLibrary>>`, `librariesLoadFailed: StateFlow<Boolean>`, `selectedLibraryIds: StateFlow<Set<String>>`, `syncFavorites: StateFlow<Boolean>`, `librarySelectionNeedsSync: StateFlow<Boolean>`, `username: String?`, `serverUrl: String?`, `isLoggedIn: StateFlow<Boolean>`.
  - Actions: `loadLibraries()`, `setSelectedLibraryIds(libraryIds: Set<String>)`, `setSyncFavorites(enabled: Boolean)`, `syncAllPlaylistsAndSongs()`, `syncPlaylists()`, `syncPlaylistSongs(playlistId: String)`, `loadPlaylistSongs(playlistId: String)`, `deletePlaylist(playlistId: String)`, `clearSyncMessage()`, `logout()`.

## Invariants
- `[JellyfinDashboardScreen.kt:JellyfinDashboardScreen]`: Collects all ViewModel `StateFlow`s with lifecycle awareness via `collectAsStateWithLifecycle()`.
- `[JellyfinDashboardScreen.kt:JellyfinDashboardContent]`: Styles sync banner with `errorContainer` when `message.contains("failed")` and `primaryContainer` otherwise; renders the empty-playlists placeholder only when `playlists.isEmpty() && !isSyncing`.
- `[JellyfinDashboardScreen.kt:JellyfinMenuCard]`, `[JellyfinDashboardScreen.kt:JellyfinLibrarySelectorSheet]`: Restricts selectable libraries to `library.isMusic`; computes effective IDs via `selectedJellyfinLibraryIds(musicLibraries, selectedLibraryIds)`; disables the library selector trigger unless `!isSyncing && musicLibraries.size > 1`; disables favorites toggle, sync-all button, and per-playlist sync buttons (`[JellyfinDashboardScreen.kt:JellyfinPlaylistCard]`) while `isSyncing` is `true`; `[JellyfinDashboardScreen.kt:JellyfinLibrarySummaryPanel]` caps visible chips at `selectedLibraryNames.take(3)` plus an overflow count chip.
- `[JellyfinDashboardViewModel.kt:init]`: Always triggers `loadLibraries()` on initialization and triggers `syncAllPlaylistsAndSongs()` only when `repository.isFullSyncDue` is `true`.
- `[JellyfinDashboardViewModel.kt:playlists]`, `[JellyfinDashboardViewModel.kt:selectedLibraryIds]`, `[JellyfinDashboardViewModel.kt:syncFavorites]`: Shared in `viewModelScope` using `SharingStarted.Lazily` with defaults `persistentListOf()`, `emptySet()`, and `false`.
- `[JellyfinDashboardViewModel.kt:loadLibraries]`: Resets `_libraries` to `persistentListOf()` and sets `_librariesLoadFailed = true` on failure.
- `[JellyfinDashboardViewModel.kt:setSelectedLibraryIds]`, `[JellyfinDashboardViewModel.kt:syncAllPlaylistsAndSongs]`: Marks `_librarySelectionNeedsSync = true` on selection change and clears it to `false` only on `syncAllPlaylistsAndSongs()` `onSuccess`; sync methods (`syncAllPlaylistsAndSongs`, `syncPlaylists`, `syncPlaylistSongs`) guard `_isSyncing` around repository `Result` folding.
- `[JellyfinDashboardViewModel.kt:syncPlaylistSongs]`: Catches `Exception` around `repository.syncUnifiedLibrarySongsFromJellyfin()` so unified library sync failure does not overwrite the playlist sync success message.

## Side Effects
- `[JellyfinDashboardScreen.kt:JellyfinDashboardScreen]`, `[JellyfinDashboardScreen.kt:JellyfinMenuCard]`: Toggles local modal bottom sheet visibility (`showLibrarySelector`), invokes `onBack()` on navigation back and disconnect (after `viewModel.logout()`).
- `[JellyfinDashboardViewModel.kt:loadLibraries]`: Performs network/IO call `repository.getLibraries()`.
- `[JellyfinDashboardViewModel.kt:setSelectedLibraryIds]`, `[JellyfinDashboardViewModel.kt:setSyncFavorites]`: Persists preferences (`repository.setSelectedLibraryIds`, `repository.setSyncFavoritesEnabled`), optionally runs `repository.syncFavorites()`, and resolves localized strings via `context.getString(...)`.
- `[JellyfinDashboardViewModel.kt:syncAllPlaylistsAndSongs]`, `[JellyfinDashboardViewModel.kt:syncPlaylists]`, `[JellyfinDashboardViewModel.kt:syncPlaylistSongs]`: Executes network/DB synchronization against `JellyfinRepository` and logs errors via `Timber.e(...)`.
- `[JellyfinDashboardViewModel.kt:loadPlaylistSongs]`, `[JellyfinDashboardViewModel.kt:deletePlaylist]`, `[JellyfinDashboardViewModel.kt:logout]`: Collects/deletes local playlist entities and clears Jellyfin session credentials via `JellyfinRepository`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
