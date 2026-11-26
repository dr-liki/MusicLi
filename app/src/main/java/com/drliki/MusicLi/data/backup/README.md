# backup

## Overview
Orchestrates `.pxpl` backup export, encrypted/unencrypted archive inspection, validation, transactional restore, and backup history tracking, alongside the deprecated v2 `AppDataBackupManager`.

## Interface
- `[BackupManager.kt:BackupManager]`: Primary singleton coordinator exposing `export`, `isBackupEncrypted`, `inspectBackup`, `restore`, `getBackupHistory`, `removeBackupHistoryEntry`, and `discardDecryptedBackup`.
- `[AppDataBackupManager.kt:AppDataBackupManager]`: `@Deprecated` legacy v2 backup manager (`exportToUri`, `importFromUri`) alongside legacy package-root types `BackupSection`, `BackupOperationType`, `BackupTransferProgressUpdate`, `PlaybackHistoryBackupEntry`, and `AppDataBackupPayload`.
- Subdirectories:
  - [format](./format/README.md): Binary `.pxpl` format detection, v4 AES-256-GCM crypto, ZIP/GZIP/legacy reading and writing, and legacy payload adaptation.
  - [history](./history/README.md): Bounded DataStore log of recently restored backups.
  - [model](./model/README.md): Manifests, `BackupSection` descriptors, progress updates, validation outcomes, and restore plans/results.
  - [module](./module/README.md): Per-section `BackupModuleHandler` implementations and cross-device `BackupSongResolver`.
  - [restore](./restore/README.md): Restore planning (`RestorePlanner`) and transactional rollback execution (`RestoreExecutor`).
  - [validation](./validation/README.md): File/ZIP-bomb, manifest, SHA-256 checksum, module schema, and string/URL validation pipeline.

## Invariants
- `[BackupManager.kt:resolveReadableUri]`: Plain archives are read directly from the source `Uri`; `PXPL_V4_ENCRYPTED` archives require inspecting with a passphrase first (throwing `BackupEncryptedException` if omitted or `BackupWrongPassphraseException` on `AEADBadTagException`), copy and GCM-authenticate the full stream up to `BackupFileValidator.MAX_BACKUP_SIZE_BYTES` into `cacheDir/backup_dec`, and keep at most one cached decrypted temp file in `decryptedBackups`.
- `[BackupManager.kt:restore]`: Runs `validationPipeline.validateFile` before `restoreExecutor.execute`, always invokes `discardDecryptedBackup(uri)` in a `finally` block (and on fatal file validation failure), and records `BackupHistoryEntry` only on `RestoreResult.Success`.
- `[BackupManager.kt:inspectBackup]`: Validates file, builds `RestorePlan`, validates manifest, skips per-module payload preview validation when `moduleInfo.sizeBytes > BackupReader.MAX_MODULE_PAYLOAD_BYTES` (adding a warning), and returns the plan with `backupUri` set to the caller's original URI rather than the decrypted temp URI.
- `[AppDataBackupManager.kt:AppDataBackupManager]`: Requires `payload.formatVersion >= 1` on import and routes `PLAYLISTS` / `GLOBAL_SETTINGS` preference keys by `playlistPreferenceKeys`.

## Side Effects
- `[BackupManager.kt:export]`: Invokes `BackupModuleHandler.export` across selected sections, reads `PackageManager` version info, and writes `.pxpl` archives via `BackupWriter.write` on `Dispatchers.IO` while emitting `BackupTransferProgressUpdate` callbacks.
- `[BackupManager.kt:inspectBackup]`, `[BackupManager.kt:restore]`, `[BackupManager.kt:discardDecryptedBackup]`: Creates and deletes temporary decrypted `.pxpl` files in `context.cacheDir/backup_dec`, reads `ContentResolver` input streams / `DocumentFile` metadata, mutates persisted app data via `RestoreExecutor`, and appends entries to `BackupHistoryRepository`.
- `[AppDataBackupManager.kt:exportToUri]`, `[AppDataBackupManager.kt:importFromUri]`: Reads/writes `ContentResolver` streams on `Dispatchers.IO` and directly mutates `UserPreferencesRepository`, DAOs, and `PlaybackStatsRepository`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
