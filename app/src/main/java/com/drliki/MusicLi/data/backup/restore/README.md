# backup/restore

## Overview
Plans and transactionally executes backup restoration from `.pxpl` and legacy archives, capturing per-module snapshots beforehand and rolling back applied modules on failure.

## Interface
- `[RestorePlanner.kt:RestorePlanner]`: Reads a backup manifest and builds a `RestorePlan` (`buildRestorePlan(uri)`).
- `[RestoreExecutor.kt:RestoreExecutor]`: Executes a `RestorePlan` with validation, per-module restore, and automatic rollback (`execute(uri, plan, onProgress)`).

## Invariants
- `[RestorePlanner.kt:buildRestorePlan]`: Maps manifest module keys through `BackupSection.fromKey`, marks every `ModuleRestoreDetail` with `willOverwrite = true`, defaults `selectedModules` to `availableModules`, and emits a warning if `manifest.schemaVersion < 3`.
- `[RestoreExecutor.kt:execute]`: Processes `plan.selectedModules` sorted by `section.key`; snapshots all selected modules via `BackupModuleHandler.snapshot()` before any write; enforces `moduleInfo.sizeBytes <= BackupReader.MAX_MODULE_PAYLOAD_BYTES` and `ValidationPipeline.validateModulePayload` fatal-error checks; and rolls back `(restoredModules + currentSection).distinct().asReversed()` on any error, returning `RestoreResult.TotalFailure` when rollback succeeds or `RestoreResult.PartialFailure(..., rolledBack = false)` when any rollback fails.

## Side Effects
- `[RestorePlanner.kt:buildRestorePlan]`: Reads archive manifests via `BackupReader.readManifest` on `Dispatchers.IO`.
- `[RestoreExecutor.kt:execute]`: Reads module payloads via `BackupReader.readModulePayload` on `Dispatchers.IO`, mutates persistent app stores via `BackupModuleHandler.restore` / `rollback`, emits `BackupOperationType.IMPORT` progress callbacks, and logs failures with `Timber`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
