# Data Layer (`data`)

## Overview
Core data layer (`com.drliki.MusicLi.data`) encompassing Room SQLite persistence, MediaStore/SAF library scanning, audio metadata & cover-art editing, Media3 playback service & DSP equalizer, offline/cloud sync workers, `.pxpl` backup/restore, Navidrome & Jellyfin integrations, online lookups (LRCLIB, MusicBrainz, Deezer, ListenBrainz, Explore/YouTube), NLP smart playlists, listening statistics, and personalized mix generation (`DailyMixManager`).

## Interface
- **Direct Symbols**:
  - [`DailyMixManager`](DailyMixManager.kt): `[DailyMixManager.kt:DailyMixManager]` `@Singleton` personalized mix generator exposing:
    - `[DailyMixManager.kt:DailyMixManager.SongEngagementStats]` (`playCount`, `totalPlayDurationMs`, `lastPlayedTimestamp`)
    - `[DailyMixManager.kt:DailyMixManager.recordPlay]`, `[DailyMixManager.kt:DailyMixManager.incrementScore]`, `[DailyMixManager.kt:DailyMixManager.getScore]`, `[DailyMixManager.kt:DailyMixManager.getEngagementStats]`, `[DailyMixManager.kt:DailyMixManager.getAllEngagementStats]`
    - `[DailyMixManager.kt:DailyMixManager.generateDailyMix]` (default `limit = 30`) and `[DailyMixManager.kt:DailyMixManager.generateYourMix]` (default `limit = 60`, 30% favorites / 45% core / 25% discovery)
- **Subdirectories**:
  - [`backup`](./backup/README.md) — `.pxpl` encrypted/unencrypted backup export, validation, transactional restore, and backup history (`BackupManager`, `AppDataBackupManager`)
  - [`database`](./database/README.md) — Room `PixelPlayerDatabase` (v7), 21 entities, FTS4 index, runtime triggers, migrations `1->7`, and 14 DAOs
  - [`diagnostics`](./diagnostics/README.md) — Process-wide telemetry (`PerformanceMetrics`), main-thread frame stall monitoring, timed diagnostics, and debug reports
  - [`equalizer`](./equalizer/README.md) — Software PCM DSP equalizer pipeline, Media3 `ForwardingAudioSink`, and external Android `AudioEffect` sessions
  - [`explore`](./explore/README.md) — JioSaavn online discovery client, ExoPlayer HTTP stream cache, and offline M4A + LRC downloader
  - [`image`](./image/README.md) — Coil fetchers/mappers and signature-verified disk cache for local and cloud album artwork
  - [`jellyfin`](./jellyfin/README.md) — Jellyfin auth, library/playlist sync, bidirectional favorites sync, and stream proxy
  - [`library`](./library/README.md) — In-memory library algorithms (`DuplicateFinder`)
  - [`listenbrainz`](./listenbrainz/README.md) — Opt-in ListenBrainz/Maloja auth, custom endpoint rewriting, and offline scrobble queue
  - [`lyrics`](./lyrics/README.md) — Batch offline lyrics embedding workflows (`./bulk/README.md`)
  - [`media`](./media/README.md) — Audio tag/artwork/ReplayGain/BPM readers and crash-recoverable metadata write-back (`SongMetadataEditor`)
  - [`model`](./model/README.md) — Immutable domain models, enums, 64-entry `SortOption`, `SmartPlaylistRule`, and playback state models
  - [`musicbrainz`](./musicbrainz/README.md) — MusicBrainz WS v2 recording lookup and ID/tag enrichment
  - [`navidrome`](./navidrome/README.md) — Subsonic/Navidrome auth, folder-filtered sync, and stream proxy
  - [`network`](./network/README.md) — Remote API clients, parsers, and multi-provider orchestrators (`deezer`, `jellyfin`, `lyrics`, `navidrome`)
  - [`observer`](./observer/README.md) — Process-lifecycle `MediaStore` and external file observer emitting rescan triggers
  - [`offline`](./offline/README.md) — Cloud track offline caching, optional SAF export, and download notifications
  - [`paging`](./paging/README.md) — Jetpack Paging 3 `MediaStorePagingSource` for ordered `Song` paging
  - [`playlist`](./playlist/README.md) — M3U parsing/export, two-way SAF `.m3u8` sync, smart playlist rules, and offline NLP mix generation (`./nlp/README.md`)
  - [`preferences`](./preferences/README.md) — DataStore & Room-backed settings, theme, equalizer, playlist, smart-mix, M3U sync, and ListenBrainz preferences
  - [`provider`](./provider/README.md) — Read-only `SharedArtworkContentProvider` for securely sharing local/cached album art
  - [`repository`](./repository/README.md) — Domain repository interfaces and `@Singleton` implementations (`MusicRepository`, `SongRepository`, `LyricsRepository`, `ArtistImageRepository`, `AudioBookmarkRepository`, `TransitionRepository`)
  - [`service`](./service/README.md) — Media3 `MediaLibraryService` (`MusicService`), Android Auto tree, sleep timer, ReplayGain processor, dual-deck player engine (`./player/README.md`), and Quick Settings tile (`./tile/README.md`)
  - [`stats`](./stats/README.md) — Playback history persistence and time-range/recap listening statistics
  - [`storage`](./storage/README.md) — Storage usage inspection and cache-clearing utilities
  - [`stream`](./stream/README.md) — Shared local HTTP streaming proxy, URL/TLS security validation, and artist/disc normalization
  - [`update`](./update/README.md) — GitHub release checks, APK download, signature/version verification, and `PackageInstaller` flow
  - [`video`](./video/README.md) — Synced YouTube cover-video discovery, SponsorBlock intro offset lookup, and chunked range streaming
  - [`worker`](./worker/README.md) — WorkManager sync orchestration (`SyncManager`, `SyncWorker`), cloud sync/downloads, bulk lyrics embedding, and scrobble flushing

## Invariants
- `[DailyMixManager.kt:DailyMixManager.ensureLegacyDataMigrated]` migrates legacy `song_scores.json` into `EngagementDao` lazily at most once, double-checked under `migrationMutex` with file reads guarded by `fileLock` without suspension points inside `synchronized`.
- `[DailyMixManager.kt:DailyMixManager.sanitizeStats]` clamps `playCount`, `totalPlayDurationMs`, and `lastPlayedTimestamp` to `>= 0`.
- `[DailyMixManager.kt:DailyMixManager.generateDailyMix]` and `[DailyMixManager.kt:DailyMixManager.generateYourMix]` seed `java.util.Random` deterministically per day (`YEAR * 1000 + DAY_OF_YEAR`, `+ 17` for `generateYourMix`) and enforce per-artist (`2`, or `3` for favorites) and per-genre diversity caps before falling back to quota/random fill.
- `[DailyMixManager.kt:DailyMixManager.normalizeGenreKey]` treats blank or `"unknown"`-containing genre strings as `null`.

## Side Effects
- `[DailyMixManager.kt:DailyMixManager.migrateLegacyDataIfNeeded]` reads `filesDir/song_scores.json`, upserts rows into Room `EngagementDao`, and renames the legacy file to `filesDir/song_scores.json.bak`.
- `[DailyMixManager.kt:DailyMixManager.recordPlay]` and `[DailyMixManager.kt:DailyMixManager.incrementScore]` perform Room database upserts via `EngagementDao`.
- Logs migration and parsing diagnostics via `Timber`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
