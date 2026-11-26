# Bulk Lyrics Embedding

## Overview
Resumable batch pipeline that scans editable local audio files, queries enabled lyrics providers in priority order, embeds matched synced/plain lyrics into audio file tags (with optional `.lrc` sidecar files), updates Room lyrics caches, and handles Android 11+ MediaStore write-permission batching, rate-limit cooldowns, and network interruptions via a JSON checkpoint and WorkManager (`BulkLyricsEmbedWorker`).

## Interface
- `[BulkLyricsEmbedModels.kt:BulkLyricsEmbedStatus]` - Enum of job states: `IDLE`, `RUNNING`, `PAUSED_USER`, `PAUSED_RATE_LIMIT`, `PAUSED_NETWORK`, `NEEDS_PERMISSION`, `COMPLETED`.
- `[BulkLyricsEmbedModels.kt:BulkLyricsLogOutcome]` / `BulkLyricsLogEntry` / `BulkLyricsEmbedOptions` / `BulkLyricsCurrentSong` / `BulkLyricsEmbedUiState` - Public UI state and configuration models (`remainingCount`, `progressFraction`), plus internal `[BulkLyricsEmbedModels.kt:BulkLyricsEmbedCheckpoint]`.
- `[BulkLyricsEmbedManager.kt:PendingWritePermissionBatch]` - Holds `IntentSender`, `songIds`, and `totalPendingCount` for MediaStore write grants.
- `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager]` - `@Singleton` orchestrator exposing `uiState`, `eligibleLibraryCount`, `refreshEligibleLibraryCount`, `updateOptions`, `startNewRun`, `pause`, `prepareResumeFromWorker`, `resume`, `stopAndReset`, `retryFailed`, `createNextWritePermissionBatch`, `onWritePermissionBatchResult`, and `processQueueLoop`.
- `[BulkLyricsEmbedManager.kt:BulkLyricsWorkScheduler]` / `BulkLyricsWriteResult` / `BulkLyricsEnvAdapter` - Internal abstractions for WorkManager scheduling and environment I/O.

## Invariants
- `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager.stateMutex]` guards all checkpoint mutations and UI state emissions; `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager.workerRunMutex]` serializes `processQueueLoop` execution.
- `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager.restoreCheckpointFromDisk]` demotes a persisted `RUNNING` status on startup to `PAUSED_USER` if the OS killed the worker mid-run.
- `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager.filterEditableLocalSongs]` restricts candidates to `id > 0L`, `SourceType.LOCAL`, valid MediaStore request URIs (`MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest`), and existing files on disk.
- `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager.canonicalLrcText]` converts KuGou raw tag format (`^\[\d+,\d+]`) or XML/TTML (`<...`) into canonical LRC via `LyricsUtils.toLrcString`.
- `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager]` clamps rate-limit cooldowns between `MIN_RATE_LIMIT_COOLDOWN_MS` (`10_000L` ms) and `MAX_RATE_LIMIT_COOLDOWN_MS` (`15 * 60_000L` ms, default `60_000L` ms), caps `logs` at `MAX_LOG_ENTRIES` (`80`), and defaults permission batches to `DEFAULT_PERMISSION_BATCH_SIZE` (`150`).
- `[BulkLyricsEmbedManager.kt:BulkLyricsEmbedManager.saveCheckpointLocked]` writes atomically via a `.tmp` file rename/copy to `bulk_lyrics_embed_state.json`.

## Side Effects
- Disk I/O: writes `bulk_lyrics_embed_state.json` under `context.filesDir`, mutates audio file tags via `SongMetadataEditor`, and optionally writes `<audio_name>.lrc` UTF-8 sidecar files (`[BulkLyricsEmbedManager.kt:RealBulkLyricsEnvAdapter.embedSongLyrics]`).
- Database mutations via `LyricsRepository.updateLyrics` and `MusicDao.updateLyrics`.
- Network calls via `MultiProviderLyricsOrchestrator.fetchSyncedByPriorityForBatch`.
- WorkManager unique work scheduling (`"bulk_lyrics_embed"`) with `NetworkType.CONNECTED` constraint (`[BulkLyricsEmbedManager.kt:WorkManagerBulkLyricsScheduler]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
