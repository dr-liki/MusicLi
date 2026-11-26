# backup/history

## Overview
Persists a bounded log of recently restored backup archives in Jetpack DataStore Preferences for display and quick access in backup management UI.

## Interface
- `[BackupHistoryRepository.kt:BackupHistoryRepository]`: Singleton repository exposing `historyFlow: Flow<List<BackupHistoryEntry>>`, `addEntry(entry)`, `removeEntry(uri)`, `clear()`, and companion constant `MAX_HISTORY_ENTRIES`.

## Invariants
- `[BackupHistoryRepository.kt:addEntry]`: Deduplicates entries by `uri`, inserts the newest entry at index `0`, and truncates the persisted list to at most `MAX_HISTORY_ENTRIES` (`10`).
- `[BackupHistoryRepository.kt:historyFlow]`: Returns `emptyList()` if `"backup_history_json"` is unset or fails Gson deserialization.

## Side Effects
- `[BackupHistoryRepository.kt:addEntry]`: Mutates `DataStore<Preferences>` key `"backup_history_json"`.
- `[BackupHistoryRepository.kt:removeEntry]`: Filters and rewrites `DataStore<Preferences>` key `"backup_history_json"`.
- `[BackupHistoryRepository.kt:clear]`: Removes `"backup_history_json"` from `DataStore<Preferences>`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
