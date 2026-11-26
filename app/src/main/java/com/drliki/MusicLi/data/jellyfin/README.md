# Jellyfin Integration

## Overview
Coordinates Jellyfin authentication, library/playlist/track synchronization into Room and unified library tables, bidirectional favorites synchronization, and local HTTP stream proxying for `jellyfin://` URIs.

## Interface
- `[JellyfinRepository.kt:JellyfinRepository]` - `@Singleton` repository managing Jellyfin session credentials (`isLoggedInFlow`, `isLoggedIn`, `serverUrl`, `username`, `lastFullSyncTime`, `isFullSyncDue`, `selectedLibraryIdsFlow`, `syncFavoritesFlow`), authentication (`login`, `logout`, `getAuthorizationHeader`), library & playlist sync (`getLibraries`, `setSelectedLibraryIds`, `syncPlaylists`, `syncPlaylistSongs`, `syncLibrarySongs`, `syncAllPlaylistsAndSongs`, `deletePlaylist`), unified library rebuilds (`syncUnifiedLibrarySongsFromJellyfinIfChanged`, `syncUnifiedLibrarySongsFromJellyfin`), queries & media resolution (`getPlaylists`, `getPlaylistSongs`, `getAllSongs`, `searchSongs`, `searchLocalSongs`, `getStreamUrl`, `getImageUrl`, `getAlbumCoverItemId`, `getLyrics`), and favorite synchronization (`setSyncFavoritesEnabled`, `withLocalFavoriteLock`, `recordLocalFavoriteMutation`, `clearLocalFavoriteMutation`, `pushFavoriteChange`, `syncFavorites`).
- `[JellyfinRepository.kt:JellyfinRepository.FavoriteMutationToken]` - Token binding an in-flight local favorite toggle to `(sessionGen, accountKey, mutationGen)`.
- `[JellyfinRepository.kt:JELLYFIN_SYNC_THRESHOLD_MS]` / `[JellyfinRepository.kt:unifiedJellyfinLibraryState]` / `[JellyfinRepository.kt:isJellyfinFullSyncDue]` / `[JellyfinRepository.kt:selectedJellyfinLibraryIds]` / `[JellyfinRepository.kt:JellyfinFavoriteSyncDiff]` / `[JellyfinRepository.kt:computeJellyfinFavoriteSyncDiff]` - Internal helpers for sync threshold, unified library state fingerprinting, library selection fallback, and 3-way favorite diffing.
- `[JellyfinStreamProxy.kt:JellyfinStreamProxy]` - `@Singleton` `CloudStreamProxy<String>` implementation serving `/jellyfin/{itemId}` with `resolveJellyfinUri` and `warmUpStreamUrl`.
- Subdirectory `[model](./model/README.md)` - Immutable Jellyfin domain models and credential validation.

## Invariants
- `[JellyfinRepository.kt:JellyfinRepository.songCacheMutex]` serializes every write to cached Jellyfin songs (`writeSongCache`) and every unified-library rebuild (`syncUnifiedLibrarySongsFromJellyfinIfChanged`, `syncUnifiedLibrarySongsFromJellyfin`), bumping `KEY_SONG_CACHE_REVISION` synchronously via `commit()` before mutating DAO rows.
- `[JellyfinRepository.kt:JellyfinRepository.favoritesSyncMutex]` and `[JellyfinRepository.kt:JellyfinRepository.localFavoritesMutex]` coordinate favorite pushes/syncs against `accountSessionGeneration` and `inFlightLocalFavoriteMutations` so stale network results after logout, account switch, or newer toggles are discarded and protected pending IDs are never resurrected.
- `[JellyfinRepository.kt:JellyfinRepository.toUnifiedSongId]`, `toUnifiedAlbumId`, and `toUnifiedArtistId` assign deterministic negative IDs offset by `12_000_000_000_000L`, `13_000_000_000_000L`, and `14_000_000_000_000L` respectively, with `SourceType.JELLYFIN` and `contentUriString = "jellyfin://<id>"`.
- `[JellyfinRepository.kt:JellyfinRepository.syncPlaylists]` aborts without wiping local playlists if the parser returns empty while the JSON array is non-empty, or if the server returns empty while `dao.getPlaylistCount() > 0`.
- `[JellyfinStreamProxy.kt:JellyfinStreamProxy]` restricts upstream hosts via `allowedHostSuffixes` to the configured Jellyfin `serverUrl` host, validates item IDs via `CloudStreamSecurity.validateJellyfinItemId`, and expires cached stream URLs after `cacheExpirationMs` (`30` minutes).

## Side Effects
- Encrypted SharedPreferences (`jellyfin_prefs` via `EncryptedSharedPreferences`, falling back to `jellyfin_prefs_plain` if unreadable) storing server URL, username, access token, user ID, sync timestamps/revisions, unified library state fingerprint, and favorite sync baseline/pending queues (`[JellyfinRepository.kt:JellyfinRepository.createCredentialPrefs]`).
- Network calls via `JellyfinApiService` for authentication, views, playlists, music items, search, lyrics, and favorite toggling; upstream HTTP proxying via `OkHttpClient` in `[JellyfinStreamProxy.kt:JellyfinStreamProxy]`.
- Room database writes to `JellyfinDao`, `MusicDao`, and `FavoritesDao`, plus DataStore preference updates in `UserPreferencesRepository` and `PlaylistPreferencesRepository`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
