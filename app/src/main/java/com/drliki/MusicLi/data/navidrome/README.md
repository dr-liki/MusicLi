# Navidrome / Subsonic Integration

## Overview
Coordinates Subsonic/Navidrome authentication (with automatic token/password auth fallback persistence), music-folder-filtered library and playlist synchronization into Room and unified library tables, playback/scrobble reporting, canonical `navidrome://` URI encoding, and local HTTP stream proxying.

## Interface
- `[NavidromeRepository.kt:NavidromeRepository]` - `@Singleton` repository managing credentials (`isLoggedInFlow`, `isLoggedIn`, `serverUrl`, `username`, `lastFullSyncTime`, `selectedMusicFolderIdsFlow`), auth (`login`, `logout`), sync (`getMusicFolders`, `setSelectedMusicFolderIds`, `syncPlaylists`, `syncPlaylistSongs`, `syncLibrarySongs`, `syncAllPlaylistsAndSongs`, `syncUnifiedLibrarySongsFromNavidrome`, `deletePlaylist`), queries & media resolution (`getPlaylists`, `getPlaylistSongs`, `getAllSongs`, `searchSongs`, `searchLocalSongs`, `getStreamUrl`, `getCoverArtUrl`, `getLyrics`), and playback reporting (`reportPlayback`, `scrobble`), with constants `SYNC_THRESHOLD_MS` (`24 * 60 * 60 * 1000L`) and `LIBRARY_PLAYLIST_ID` (`"__library__"`).
- `[NavidromeRepository.kt:navidromePlaylistsWithLibraryFallback]` / `[NavidromeRepository.kt:selectedNavidromeMusicFolderIds]` / `[NavidromeRepository.kt:NavidromeSong.toSong]` - Helpers for synthetic library playlist fallback, folder selection filtering, and display `Song` mapping.
- `[NavidromeSongUri.kt:NavidromeSongUri]` - Object providing `build(songId)`, `parseId(uri)`, and `encodeId(id)` for `navidrome://<id>` URIs.
- `[NavidromeStreamProxy.kt:NavidromeStreamProxy]` - `@Singleton` `CloudStreamProxy<String>` implementation serving `/navidrome/{songId}` with `resolveNavidromeUri` and `warmUpStreamUrl`.
- Subdirectory `[model](./model/README.md)` - Immutable Subsonic/Navidrome models and credential/auth-method definitions.

## Invariants
- `[NavidromeSongUri.kt:NavidromeSongUri]` percent-encodes `songId` (spaces as `%20`, never `+`) on `build` and decodes in `parseId` only when `encodeId(decoded) == tail`, preserving backward compatibility with legacy unencoded raw IDs.
- `[NavidromeRepository.kt:NavidromeRepository.toUnifiedSongId]`, `toUnifiedAlbumId`, and `toUnifiedArtistId` assign deterministic negative IDs offset by `9_000_000_000_000L`, `10_000_000_000_000L`, and `11_000_000_000_000L` respectively, with `SourceType.NAVIDROME`.
- `[NavidromeRepository.kt:NavidromeRepository.syncPlaylists]` aborts without deleting local playlists if the parser returns empty while the JSON array is non-empty, or if the server returns empty while `dao.getPlaylistCount() > 0`; `[NavidromeRepository.kt:NavidromeRepository.deletePlaylist]` is a no-op for `LIBRARY_PLAYLIST_ID`.
- `[NavidromeRepository.kt:NavidromeRepository.syncLibrarySongs]` fetches albums using a concurrency `Semaphore(5)` and fails the whole album list fetch (`fetchAllAlbums`) if any page fails so a partial library is never treated as complete.
- `[NavidromeRepository.kt:NavidromeRepository.reportPlayback]` falls back to `api.scrobble(id, submission = false)` when `reportPlayback` fails with `"404"` during `"playing"` or `"starting"` states.
- `[NavidromeStreamProxy.kt:NavidromeStreamProxy]` restricts upstream hosts via `allowedHostSuffixes` to `repository.serverUrl` host, validates IDs with `CloudStreamSecurity.validateNavidromeSongId`, and expires cached stream URLs after `30` minutes.

## Side Effects
- Encrypted SharedPreferences (`navidrome_prefs` via `EncryptedSharedPreferences`, falling back to `navidrome_prefs_plain`) storing server URL, username, password, `auth_method` (updated dynamically via `api.onAuthMethodChanged`), and `last_full_sync` (`[NavidromeRepository.kt:NavidromeRepository.createCredentialPrefs]`).
- Network calls via `NavidromeApiService` for ping, folders, albums, playlists, search, lyrics, playback reporting, and scrobbling; upstream HTTP stream proxying via `OkHttpClient` in `[NavidromeStreamProxy.kt:NavidromeStreamProxy]`.
- Room database writes to `NavidromeDao` and `MusicDao`, plus DataStore updates in `UserPreferencesRepository` and `PlaylistPreferencesRepository`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
