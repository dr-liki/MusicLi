# model

## Overview
Immutable domain models, enums, Parcelable/Serializable payloads, sort-option catalog, smart-playlist rules, and playback/transition state structures shared across repositories, database mappers, media playback, widgets, and UI layers.

## Interface
- `[Song.kt:Song]`: `@Immutable @Parcelize` track model (`id`, `title`, `artist`, `artistId`, `artists`, `album`, `albumId`, `albumArtist`, `path`, `contentUriString`, `albumArtUriString`, `duration`, `genre`, `lyrics`, `isFavorite`, `trackNumber`, `discNumber`, `year`, `dateAdded`, `dateModified`, `mimeType`, `bitrate`, `sampleRate`, `navidromeId`, `jellyfinId`) with `displayArtist`, `primaryArtist`, and `emptySong()`.
- `[LibraryModels.kt]`: `@Immutable @Parcelize` library entities `Album` (`empty()`), `Artist` (`effectiveImageUrl`, `empty()`), and `ArtistRef`.
- `[Genre.kt:Genre]`: `@Immutable` genre model with optional icon/light/dark hex colors and `songCount`.
- `[DirectoryItem.kt:DirectoryItem]` & `[MusicFolder.kt:MusicFolder]`: Folder-tree models (`displayName`, `totalSongCount`, `totalSubFolderCount`) alongside `[FolderSource.kt:FolderSource]` (`INTERNAL`, `SD_CARD`, `fromStorageKey`) and `[StorageFilter.kt:StorageFilter]` (`ALL`, `OFFLINE`, `ONLINE`).
- `[PlayList.kt:Playlist]` & `[PlayList.kt:PlaylistShapeType]`: `@Immutable @Serializable` playlist model and cover shape enum (`Circle`, `SmoothRect`, `RotatedPill`, `Star`).
- `[SmartPlaylistRule.kt:SmartPlaylistRule]`: Rule enum (`TOP_PLAYED`, `RECENTLY_PLAYED`, `FORGOTTEN_FAVORITES`, `NEW_GEMS`) with `SMART_PLAYLIST_SOURCE_LEGACY` (`"SMART"`), `SMART_PLAYLIST_SOURCE_PREFIX` (`"SMART:"`), `fromStorageKey`, `toPlaylistSource`, `fromPlaylistSource`, `isSmartPlaylistSource`, and `Playlist.isSmartPlaylist`.
- `[SortOption.kt:SortOption]` & `[SortOption.kt:SortDirection]`: Sealed sort hierarchy (64 concrete sort objects across `SONGS`, `SONG_SECONDARY`, `ALBUMS`, `ARTISTS`, `PLAYLISTS`, `FOLDERS`, `FOLDER_SONGS`, `LIKED`, `GENRES`) with `canFlipDirection`, `methodOption`, `resolveForDirection`, `flipDirection`, `resolveSecondarySongSort`, and `fromStorageKey`.
- `[LibraryTabId.kt:LibraryTabId]`: Library tab enum (`SONGS`, `ALBUMS`, `ARTISTS`, `PLAYLISTS`, `FOLDERS`, `LIKED`, `GENRES`), `fromStorageKey`, and `String.toLibraryTabIdOrNull`.
- `[Lyrics.kt]`: `@Serializable` lyrics hierarchy (`Lyrics`, `SyncedLine`, `BackgroundVocals`, `SyncedWord`) and `[LyricsSourcePreference.kt:LyricsSourcePreference]` (`API_FIRST`, `EMBEDDED_FIRST`, `LOCAL_FIRST`, `fromOrdinal`, `fromName`).
- `[PlaybackQueueSnapshot.kt]` & `[PlayerInfo.kt]`: Serializable queue persistence (`PlaybackQueueItemSnapshot`, `PlaybackQueueSnapshot`) and widget state (`QueueItem`, `WidgetThemeColors`, `PlayerInfo`).
- `[SearchFilterType.kt:SearchFilterType]`, `[SearchHistoryItem.kt:SearchHistoryItem]`, `[SearchResultItem.kt:SearchResultItem]`: Search filter tabs, history items, and sealed result wrappers (`SongItem`, `AlbumItem`, `ArtistItem`, `PlaylistItem`).
- `[AudioOutputMode.kt:AudioOutputMode]`: Output mode enum (`SYSTEM_DEFAULT`, `DIRECT`, `PCM_FLOAT`, `BIT_PERFECT_USB`) with `usesFloatOutput`, `usesUnmodifiedMedia3AudioSink`, `fromStorageKey`, and `isBitPerfectUsbAvailable`.
- `[Transition.kt]`: Crossfade/transition models (`TransitionMode`, `Curve`, `TransitionSource`, `TransitionSettings`, `TransitionResolution`, `TransitionRule`).

## Invariants
- `[AudioOutputMode.kt:AudioOutputMode]`: `usesFloatOutput` is `true` only for `PCM_FLOAT` and `BIT_PERFECT_USB`; `usesUnmodifiedMedia3AudioSink` is `true` only for `DIRECT`; `isBitPerfectUsbAvailable` requires `sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34) and `pcmFloatSupported`; unknown keys fall back to `PCM_FLOAT` if `legacyHiFiEnabled`, else `SYSTEM_DEFAULT`.
- `[LibraryTabId.kt:LibraryTabId]`: `ARTISTS` uses singular `storageKey = "ARTIST"`; `fromStorageKey` defaults unknown keys to `SONGS` while `toLibraryTabIdOrNull` returns `null`.
- `[LyricsSourcePreference.kt:LyricsSourcePreference]`: Out-of-range ordinals and unknown names fall back to `EMBEDDED_FIRST`.
- `[FolderSource.kt:fromStorageKey]`: Unknown/null keys fall back to `FolderSource.INTERNAL`.
- `[Song.kt:Song]` & `[LibraryModels.kt]`: `Song.displayArtist` puts `isPrimary` artists first before joining with `", "` (else legacy `artist`); `Song.primaryArtist` falls back to `artists.firstOrNull()` then a synthesized `ArtistRef`; sentinel `Album.empty()`, `Artist.empty()`, and `Song.emptySong()` use `id = -1` / `"-1"`; `Artist.effectiveImageUrl` prefers non-blank `customImageUri` over non-blank `imageUrl`.
- `[SmartPlaylistRule.kt]`: `isSmartPlaylistSource` accepts `"SMART"` or `"SMART:*"`; `fromPlaylistSource` matches only `"SMART:<storageKey>"`.
- `[SortOption.kt:SortOption]`: `SONG_SECONDARY` contains only `SONGS` options with non-null `direction` (excluding `SongDefaultOrder`); `resolveSecondarySongSort` returns `null` if not in `SONG_SECONDARY` and substitutes the first distinct `methodKey` option on collision with the primary sort; `canFlipDirection` is `false` when no opposite-direction variant exists (e.g., `AlbumDateAdded`); `fromStorageKey` matches `storageKey` then `displayName` inside `allowed`, falling back to `fallback`.
- `[PlayerInfo.kt:PlayerInfo]`: Custom `equals`/`hashCode` compare `albumArtBitmapData` by array content (`contentEquals`/`contentHashCode`).
- `[MusicFolder.kt:MusicFolder]`: `totalSongCount` and `totalSubFolderCount` recursively sum all nested `subFolders` lazily.

## Side Effects
- `[PlayList.kt:Playlist]` & `[PlaybackQueueSnapshot.kt:PlaybackQueueSnapshot]`: Default timestamp arguments (`createdAt`, `lastModified`, `savedAtEpochMs`) read `System.currentTimeMillis()` on construction.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
