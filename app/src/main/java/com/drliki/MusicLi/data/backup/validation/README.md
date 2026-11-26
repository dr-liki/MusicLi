# backup/validation

## Overview
Multi-stage backup security and integrity validation: container size/ZIP-bomb checks, manifest version and SHA-256 checksum verification, per-module JSON schema checks, and string/URL sanitization.

## Interface
- `[BackupFileValidator.kt:BackupFileValidator]`: Validates file accessibility, extension, size, format magic, and ZIP archive safety (`validate(uri)`, `MAX_BACKUP_SIZE_BYTES`, `MAX_ZIP_RATIO`).
- `[ContentSanitizer.kt:ContentSanitizer]`: Sanitizes imported strings, HTTP/HTTPS URLs, and module keys (`sanitizeString`, `sanitizeUrl`, `isValidModuleKey`, `DEFAULT_MAX_LENGTH`).
- `[ManifestValidator.kt:ManifestValidator]`: Validates `BackupManifest` schema bounds, timestamps, and module keys (`validate`) and verifies strict `sha256:` payload checksums (`verifyChecksum`).
- `[ModuleSchemaValidator.kt:ModuleSchemaValidator]`: Validates JSON structure and field constraints for all 13 `BackupSection` types (`validate(section, payload)`, `MAX_STRING_LENGTH`, `MAX_ENTRIES_PER_MODULE`).
- `[ValidationPipeline.kt:ValidationPipeline]`: Facade composing file, manifest, checksum, and schema validators (`validateFile`, `validateManifest`, `validateModulePayload`, `collectWarnings`).

## Invariants
- `[BackupFileValidator.kt:BackupFileValidator]`: Enforces `MAX_BACKUP_SIZE_BYTES = 50L * 1024 * 1024`, `MAX_ZIP_RATIO = 100`, `MAX_TOTAL_DECOMPRESSED_BYTES = 256L * 1024 * 1024`, and rejects `..`, leading `/`, or leading `\` ZIP entry names (`ZIP_PATH_TRAVERSAL`) and oversized entries (`MAX_MANIFEST_BYTES` / `MAX_MODULE_PAYLOAD_BYTES`).
- `[ContentSanitizer.kt:ContentSanitizer]`: Strips ASCII control characters (`[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]`), caps strings to `maxLength` (`10_000` default), blanks URLs not starting with `https://` or `http://`, and accepts module keys matching `^[a-z_]+$` with length `<= 50`.
- `[ManifestValidator.kt:verifyChecksum]`: Strictly requires a matching `sha256:<hex>` entry in `manifest.modules[moduleKey]` (case-insensitive hex over UTF-8 bytes); missing or non-`sha256:` checksums fail verification.
- `[ModuleSchemaValidator.kt:validate]`: Rejects malformed JSON, non-array payloads (except `PLAYLISTS`, which accepts a JSON object or legacy array, and `QUICK_FILL` / `EQUALIZER`), and arrays exceeding `MAX_ENTRIES_PER_MODULE` (`100_000`) as `Severity.ERROR`, reporting per-entry schema issues as `Severity.WARNING`.
- `[ValidationPipeline.kt:validateModulePayload]`: Verifies the manifest checksum (when `manifest != null`) and short-circuits with `CHECKSUM_MISMATCH` before running `ModuleSchemaValidator`.

## Side Effects
- `[BackupFileValidator.kt:validate]`: Queries `DocumentFile.fromSingleUri` metadata and opens `Context.contentResolver.openInputStream(uri)` to read headers and ZIP entries.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
