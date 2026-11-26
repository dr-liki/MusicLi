# offline

## Overview
Manages offline caching of Navidrome and Jellyfin cloud tracks via Room and WorkManager, optional SAF folder export of downloaded files, and download completion notifications.

## Interface
- `[CloudOfflineRepository.kt:OfflineDownloadStatus]`: Status enum (`QUEUED`, `DOWNLOADING`, `COMPLETE`, `FAILED`) with `fromStorage`.
- `[CloudOfflineRepository.kt:OfflineDownload]`: Domain model with derived `progress: Float?`.
- `[CloudOfflineRepository.kt:CloudOfflineRepository]`: Singleton managing download observation (`observe`, `observeCompleted`, `observeAll`), queueing/retry/removal (`enqueue`, `enqueueAll`, `retry`, `remove`), local URI resolution (`resolveLocalUri`), and companion helpers (`isCloudSong`, `downloadCandidates`, `providerFor`, `downloadId`, `workName`, `downloadDirectory`).
- `[CloudDownloadExports.kt:CloudExportFolder]` & `[CloudDownloadExports.kt:CloudExportResult]`: SAF export destination model and result enum (`DISABLED`, `EXPORTED`, `FAILED`).
- `[CloudDownloadExports.kt:CloudDownloadExports]`: Singleton managing SAF folder selection (`folder`, `selectFolder`, `clearFolder`), track copying (`export`, `exportDownloadedTrack`), and internal filename formatting (`exportFileName`).
- `[CloudDownloadNotifications.kt:CloudDownloadNotifications]`: Internal helper posting download success/failure notifications (`show`).

## Invariants
- `[CloudOfflineRepository.kt:CloudOfflineRepository]`: Only `navidrome` and `jellyfin` URIs are accepted (`providerFor`); `downloadId` is the lowercase SHA-256 hex digest of `sourceUri`. Queue, retry, and removal operations serialize on `mutationMutex`; `shouldStartNewAttempt` never replaces `QUEUED`, `DOWNLOADING`, or `COMPLETE` rows with an existing non-empty file; `remove` deletes DB ownership via `deleteBySourceUriForAttempt` before cancelling WorkManager tags.
- `[CloudOfflineRepository.kt:resolveLocalUri]`: Returns a `file://` URI only when `state == COMPLETE` and the file exists with `length() > 0L`; otherwise deletes the stale DB row and returns `null`.
- `[CloudDownloadExports.kt:CloudDownloadExports]`: Serializes folder updates on `configurationMutex`, requires `DocumentsContract.isTreeUri` with `FLAG_DIR_SUPPORTS_CREATE`, preserves persistable URI permissions if `m3uPreferences.snapshot().treeUri` matches, and deletes incomplete SAF documents in `finally` on failure or cancellation.
- `[CloudDownloadNotifications.kt:show]`: No-ops without `POST_NOTIFICATIONS` on API 33+ or when app notifications are disabled.

## Side Effects
- `[CloudOfflineRepository.kt]`: Reads/mutates `OfflineTrackDao` (`offline_tracks`), enqueues/cancels `CloudTrackDownloadWorker` via `WorkManager`, and deletes files under `filesDir/cloud_downloads`.
- `[CloudDownloadExports.kt]`: Reads/mutates `DataStore<Preferences>`, takes/releases persistable SAF URI permissions on `ContentResolver`, and creates/writes/deletes documents in the selected SAF tree.
- `[CloudDownloadNotifications.kt:show]`: Creates notification channel `"cloud_download_results"` and posts notifications via `NotificationManager`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
