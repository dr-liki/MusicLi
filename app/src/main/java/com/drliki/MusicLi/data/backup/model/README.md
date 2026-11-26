# backup/model

## Overview
Immutable data transfer objects, manifest schemas, progress updates, validation errors, restore plans, and section descriptors shared across the modular backup and restore pipeline.

## Interface
- `[BackupManifest.kt:BackupManifest]`: Archive manifest model (`CURRENT_SCHEMA_VERSION = 5`, `MIN_SUPPORTED_VERSION = 1`, `MANIFEST_FILENAME = "manifest.json"`), paired with `[BackupManifest.kt:DeviceInfo]` and `[BackupManifest.kt:BackupModuleInfo]`.
- `[BackupModels.kt:BackupOperationType]`: Enum (`EXPORT`, `IMPORT`).
- `[BackupModels.kt:BackupTransferProgressUpdate]`: Progress payload with derived `progress: Float`.
- `[BackupModels.kt:PlaybackHistoryBackupEntry]`, `[BackupModels.kt:ArtistImageBackupEntry]`, `[BackupModels.kt:BackupHistoryEntry]`: Serializable backup record models.
- `[BackupModels.kt:RestorePlan]` & `[BackupModels.kt:ModuleRestoreDetail]`: Restore preview plan and per-module metadata.
- `[BackupModels.kt:BackupValidationResult]`, `[BackupModels.kt:ValidationError]`, `[BackupModels.kt:Severity]`: Validation outcome hierarchy (`Valid`, `Invalid`) and severity levels (`ERROR`, `WARNING`).
- `[BackupModels.kt:RestoreResult]`: Sealed restore result (`Success`, `PartialFailure`, `TotalFailure`).
- `[BackupSection.kt:BackupSection]`: Enum of all 13 backup modules (`key`, `label`, `description`, `iconRes`, `sinceVersion`, `defaultSelection`, `fromKey`).

## Invariants
- `[BackupManifest.kt:BackupManifest]`: Supported schema versions span `MIN_SUPPORTED_VERSION` (`1`) through `CURRENT_SCHEMA_VERSION` (`5`), with manifest filename `"manifest.json"`.
- `[BackupModels.kt:BackupTransferProgressUpdate]`: `progress` clamps `step.toFloat() / totalSteps` to `0f..1f` and returns `0f` when `totalSteps <= 0`.
- `[BackupModels.kt:BackupValidationResult]`: `isValid()` returns `true` for `Valid` or `Invalid` with empty `fatalErrors` (`Severity.ERROR`).
- `[BackupSection.kt:BackupSection]`: `defaultSelection` includes every enum entry, and `fromKey(key)` resolves sections strictly by `BackupSection.key`.

## Side Effects
- `[BackupManifest.kt:BackupManifest]`: Default `createdAt` argument calls `System.currentTimeMillis()`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
