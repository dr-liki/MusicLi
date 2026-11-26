# Library Presentation Utilities

## Overview
Provides Compose and sorting helpers for library song lists and tab ordering under `presentation.library`: deterministic multi-variant folder song sorting (`FolderSongSort.kt`), scroll-anchor preservation when an unscrolled `LazyColumn`/`LazyListState` updates (`KeepUnscrolledListAtTop.kt`), and stable library tab identifiers/order decoding (`LibraryTabId.kt`).

## Interface
- `[FolderSongSort.kt:sortFolderSongs]`: `internal fun sortFolderSongs(songs: List<Song>, option: SortOption): List<Song>`.
- `[KeepUnscrolledListAtTop.kt:KeepUnscrolledListAtTop]`: `@Composable internal fun KeepUnscrolledListAtTop(listState: LazyListState, rows: List<*>)`.
- `[LibraryTabId.kt:LibraryTabId]`: `enum class` with `(stableKey: String, @StringRes labelRes: Int, sortOptions: List<SortOption>)` and entries `Songs`, `Albums`, `Artists`, `Playlists`, `Folders`, `Liked`, `Genres`; `Companion.defaultOrder: List<LibraryTabId>` and `Companion.fromStableKey(key: String): LibraryTabId?`.
- `[LibraryTabId.kt:decodeLibraryTabOrder]`: `internal fun decodeLibraryTabOrder(orderJson: String?): List<LibraryTabId>`.

## Invariants
- `[LibraryTabId.kt:LibraryTabId]`: `stableKey` values are persisted across app versions and must remain stable.
- `[LibraryTabId.kt:decodeLibraryTabOrder]`: Gracefully ignores `null` or malformed JSON (`runCatching`) and unknown keys, deduplicates via `LinkedHashSet`, and appends missing tabs from `LibraryTabId.defaultOrder` so every `LibraryTabId` appears once.
- `[FolderSongSort.kt:sortFolderSongs]`: Returns `songs` directly without sorting or allocating when `songs.size < 2`.
- `[FolderSongSort.kt:sortFolderSongs]`: For `SortOption.FolderSongFilenameAZ` and `SortOption.FolderSongFilenameZA`, extracts the filename via `substringAfterLast('/').substringAfterLast('\\')`, falls back to `song.title` if blank, lowercases the key, and breaks ties using `titleAscending`.
- `[FolderSongSort.kt:folderSongComparator]`: Unrecognized `SortOption` values default to `titleAscending` (`title.lowercase()`, `artist.lowercase()`, `id`).
- `[FolderSongSort.kt:albumTracks]`, `[FolderSongSort.kt:albumArtistSortKey]`, `[FolderSongSort.kt:titleDescending]`: Separates identically named albums via `thenBy { it.albumId }`, defaults `null` `discNumber` to `1`, orders `trackNumber <= 0` after positive tracks (`Int.MAX_VALUE`), falls back from blank `albumArtist` to `artist.lowercase()`, and keeps tie-breakers ascending even for descending primary comparators.
- `[KeepUnscrolledListAtTop.kt:KeepUnscrolledListAtTop]`: Skips scrolling when `shown.rows == rows`, when the list is scrolled (`firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0`), or when `listState.isScrollInProgress` is `true`.

## Side Effects
- `[KeepUnscrolledListAtTop.kt:KeepUnscrolledListAtTop]`: Uses `remember(listState)` and `SideEffect` to mutate `ShownRows.rows` and invoke `listState.requestScrollToItem(0)` on `LazyListState` during composition commit when rows change at index 0.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
