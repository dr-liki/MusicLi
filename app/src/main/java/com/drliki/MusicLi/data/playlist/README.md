# Playlist Management, M3U Sync & Smart Mixes

## Overview
Coordinates M3U playlist parsing/export, two-way Storage Access Framework (SAF) `.m3u8` folder synchronization, offline NLP smart-mix generation backed by bundled assets, rule-based smart playlists, and order-preserving playlist merges under `com.drliki.MusicLi.data.playlist`.

## Interface
- `[M3uManager.kt:M3uManager]` & `[M3uManager.kt:M3uParseResult]` - `@Singleton` M3U/M3U8 reader and serializer exposing `parseM3u`, `generateM3u`, `Companion.parseContent`, `Companion.generateContent`, and internal `readM3uTextBounded`.
- `[M3uSyncPlanner.kt:M3uSyncPlanner]`, `[M3uSyncPlanner.kt:M3uSyncAction]`, & `[M3uSyncPlanner.kt:M3uSyncCheckpoint]` - Pure 3-way hash planner (`decide`) resolving `NONE`, `EXPORT`, `IMPORT`, or `CONFLICT`.
- `[M3uSyncRepository.kt:M3uSyncRepository]`, `[M3uSyncRepository.kt:M3uSyncReport]`, `[M3uSyncRepository.kt:M3uSyncState]`, `[M3uSyncRepository.kt:M3uSyncSafety]`, `[M3uSyncRepository.kt:M3uReplacementNames]`, `[M3uSyncRepository.kt:m3uReplacementNames]` - `@Singleton` SAF two-way sync repository exposing `state: StateFlow<M3uSyncState>`, `configure`, `disable`, and `syncNow`, plus internal safety and filename helpers.
- `[M3uSyncWorker.kt:M3uSyncWorker]` & `[M3uSyncWorker.kt:M3uSyncCoordinator]` - `@HiltWorker` (`doWork`) and `@Singleton` WorkManager/ProcessLifecycle coordinator (`start`, `onAppForeground`, `requestNow`).
- `[ArtistGenreLoader.kt:ArtistGenreLoader]` - `@Singleton` asset loader (`load(artistKeys)`) parsing `nlp/artist-genres.txt` into `ArtistGenreTable`.
- `[ConceptTableLoader.kt:ConceptTableLoader]` - `@Singleton` asset loader (`load()`) parsing `nlp/concepts.txt` into a cached `ConceptTable`.
- `[NlpPlaylistGenerator.kt:NlpPlaylistGenerator]` & `[NlpPlaylistGenerator.kt:NlpPlaylistGenerator.MixProbe]` - `@Singleton` smart-mix generator exposing `generate`, `clearHistory`, `createMixProbe`, `DEFAULT_MIN_LENGTH = 10`, and `DEFAULT_MAX_LENGTH = 20`.
- `[SmartPlaylistBuilder.kt:SmartPlaylistBuilder]` - Pure deterministic rule evaluator (`buildSongIds`, `FORGOTTEN_FAVORITE_DAYS = 30L`, `NEW_GEM_MAX_PLAY_COUNT = 2`).
- `[PlaylistOrderMerge.kt:mergeVisiblePlaylistOrder]` - Internal helper reordering visible song IDs while preserving positions of hidden/filtered-out IDs.
- Subdirectory `nlp`: [./nlp/README.md](./nlp/README.md) - Offline NLP lexicon, tokenization/stemming, genre taxonomy, mood profiles, library index, intent engine, and diversity reranker.

## Invariants
- `[M3uManager.kt:M3uManager.Companion.parseContent]` strips UTF-8 BOM (`\uFEFF`), normalizes `\` to `/`, accepts `#PIXELPLAYER-PLAYLIST-ID:` only when non-blank and `<= 200` chars, and resolves tracks by unique exact path/URI before unique basename (recording ambiguities/unresolved entries separately); bounded reads enforce `MAX_M3U_CHARACTERS = 8 * 1024 * 1024`.
- `[M3uSyncPlanner.kt:M3uSyncPlanner.decide]` returns `NONE` when both hashes are null or equal, `EXPORT` when only `appHash` changed or `fileHash == null`, `IMPORT` when only `fileHash` changed or `appHash == null`, and `CONFLICT` when both sides changed or differ without a checkpoint.
- `[M3uSyncRepository.kt:M3uSyncRepository]` serializes `configure`, `disable`, and `syncNow` under `syncMutex`, requires `DocumentsContract.isTreeUri(treeUri)` during `configure`, syncs only playlists with `!isQueueGenerated && !isSmartPlaylist && source == "LOCAL"`, refuses importing files with unresolved/ambiguous entries or exporting playlists with unavailable songs, commits link updates atomically via `replaceLinksIfSelection(expectedTreeUri, config.revision, links)`, and stages document writes via temp files and `.pixelplayer-backup-...` rollbacks.
- `[M3uSyncWorker.kt:M3uSyncWorker.doWork]` retries `IOException` up to `MAX_RETRIES = 3` and fails immediately on `SecurityException`; `[M3uSyncWorker.kt:M3uSyncCoordinator.start]` is idempotent via `started.compareAndSet(false, true)` and debounces playlist changes by `1500 ms`.
- `[ArtistGenreLoader.kt:ArtistGenreLoader.load]` does not cache in memory and returns `ArtistGenreTable.EMPTY` on empty `artistKeys` or asset errors; `[ConceptTableLoader.kt:ConceptTableLoader.load]` caches its first parsed table (or `ConceptTable.EMPTY` on error) in `@Volatile cached`.
- `[NlpPlaylistGenerator.kt:NlpPlaylistGenerator]` serializes `generate` with `generationMutex` and `getOrBuildIndex` with `indexMutex` (rebuilding only on `LibraryIndex.signatureOf(songs)` change), reads audio file BPM tags (`40f..220f`) with `Semaphore(8)` only when `parsed.hasEnergySignal` is true, bounds session mix history to `MAX_SESSION_MIXES = 3` within `7 days`, and keeps `createMixProbe`/`MixProbe.canMix` side-effect-free (`PROBE_LENGTH = 10`, `PROBE_SEED = 1`).
- `[PlaylistOrderMerge.kt:mergeVisiblePlaylistOrder]` returns `null` unless `newVisibleOrder` is an exact multiset permutation of its occurrences in `storedIds`.
- `[SmartPlaylistBuilder.kt:SmartPlaylistBuilder.buildSongIds]` clamps `limit` to `1..allSongs.size` and enforces rule predicates for `TOP_PLAYED`, `RECENTLY_PLAYED`, `FORGOTTEN_FAVORITES`, and `NEW_GEMS`.

## Side Effects
- `[M3uManager.kt:M3uManager.parseM3u]` queries `MusicRepository.getAllSongsOnce()` and reads SAF streams/display names via `ContentResolver`.
- `[M3uSyncRepository.kt:M3uSyncRepository]` takes/releases persistable SAF URI permissions, reads/writes playlists in `PlaylistPreferencesRepository`, creates/reads/writes/renames/deletes SAF documents via `DocumentsContract`/`ContentResolver`, updates `M3uSyncPreferences` and `runtimeState`, and logs via `Timber`.
- `[M3uSyncWorker.kt:M3uSyncCoordinator]` launches `@AppScope` coroutines and schedules/cancels WorkManager jobs (`automatic_m3u_sync_periodic`, `automatic_m3u_sync_immediate`); `[M3uSyncWorker.kt:M3uSyncWorker.doWork]` executes `M3uSyncRepository.syncNow()`.
- `[ArtistGenreLoader.kt:ArtistGenreLoader.load]` & `[ConceptTableLoader.kt:ConceptTableLoader.load]` read Android assets (`nlp/artist-genres.txt`, `nlp/concepts.txt`) and log via `Timber`.
- `[NlpPlaylistGenerator.kt:NlpPlaylistGenerator]` reads repositories/preferences and file BPM tags via `TagBpmReader` on `Dispatchers.IO`, mutates `cachedIndex`, `cachedBpm`, and `recentMixes`, and persists mix history in `MixPreferences`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
