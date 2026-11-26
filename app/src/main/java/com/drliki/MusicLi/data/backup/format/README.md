# backup/format

## Overview
Binary container detection, encryption, reading, writing, and legacy schema adaptation for `.pxpl` backup archives. Supports v4 AES-256-GCM encrypted containers, v3 ZIP archives, v2 `PXPL`-prefixed GZIP archives, legacy GZIP payloads, and raw JSON payloads.

## Interface
- `[BackupCrypto.kt:BackupCrypto]`: Passphrase-based AES-256-GCM encryption/decryption (`encryptingStream`, `decryptingStream`, `ENC_MARKER`, `HEADER_BYTES_AFTER_MAGIC`).
- `[BackupCrypto.kt:BackupEncryptedException]`: Thrown when opening an encrypted `.pxpl` v4 backup without a passphrase.
- `[BackupCrypto.kt:BackupWrongPassphraseException]`: Thrown when decryption fails due to a wrong passphrase or corrupted ciphertext.
- `[BackupFormatDetector.kt:BackupFormatDetector]`: Magic-header detector (`detect`, `readHeader`, `PXPL_MAGIC`, `PXPL_MAGIC_SIZE`) and `Format` enum (`PXPL_V4_ENCRYPTED`, `PXPL_V3_ZIP`, `PXPL_V2_GZIP`, `LEGACY_GZIP`, `LEGACY_RAW`, `UNKNOWN`).
- `[BackupReader.kt:BackupReader]`: Reads manifests, single modules, and full module maps from backup URIs (`readManifest`, `readModulePayload`, `readAllModulePayloads`, `detectFormat`, `MAX_MANIFEST_BYTES`, `MAX_MODULE_PAYLOAD_BYTES`, `MAX_LEGACY_BACKUP_BYTES`).
- `[BackupWriter.kt:BackupWriter]`: Writes `PXPL`-prefixed v3 ZIP or v4 encrypted `.pxpl` archives (`write`).
- `[LegacyPayloadAdapter.kt:LegacyPayloadAdapter]`: Adapts v1/v2 `AppDataBackupPayload` JSON into a v3 `BackupManifest` and module payload map (`adapt`).

## Invariants
- `[BackupCrypto.kt:BackupCrypto]`: Encrypted v4 containers place `ENC_MARKER` (`EC01`), a 16-byte random salt, a 12-byte random GCM IV, and a 4-byte big-endian PBKDF2-HMAC-SHA256 iteration count (`210_000` on write, validated within `1..5_000_000` on read) immediately after the 4-byte `PXPL` magic, followed by AES-256-GCM (`AES/GCM/NoPadding`, 128-bit tag) ciphertext of a v3 ZIP stream.
- `[BackupFormatDetector.kt:detect]`: Requires at least 4 header bytes (`< 4` returns `UNKNOWN`), inspects bytes `4..5` after `PXPL` (`PK` -> `PXPL_V3_ZIP`, `EC` -> `PXPL_V4_ENCRYPTED`, otherwise `PXPL_V2_GZIP`), and maps `0x1f 0x8b` to `LEGACY_GZIP` and leading `{` to `LEGACY_RAW`.
- `[BackupReader.kt:BackupReader]`: Enforces bounded in-memory text reads (`MAX_MANIFEST_BYTES = 512 * 1024`, `MAX_MODULE_PAYLOAD_BYTES = 16 * 1024 * 1024`, `MAX_LEGACY_BACKUP_BYTES = 32 * 1024 * 1024`), rejects `PXPL_V4_ENCRYPTED` directly with `BackupEncryptedException`, and skips ZIP entries flagged by `isSuspiciousEntryName` (`..`, `/`, `\`, or spaces).
- `[BackupWriter.kt:write]`: Computes `sha256:` checksums, JSON array entry counts, and UTF-8 byte sizes in `BackupManifest.modules` before writing `manifest.json` and `<module>.json` entries.
- `[LegacyPayloadAdapter.kt:adapt]`: Splits v1 `preferences` keys belonging to `PlaylistsModuleHandler.PLAYLIST_KEYS` into `"playlists"` and all remaining keys into `"global_settings"`, emitting `sha256:` checksums for every extracted module.

## Side Effects
- `[BackupReader.kt:BackupReader]`: Opens read streams via `Context.contentResolver.openInputStream` on `Dispatchers.IO`.
- `[BackupWriter.kt:write]`: Opens output streams via `Context.contentResolver.openOutputStream` on `Dispatchers.IO`, writes compressed/encrypted archive bytes, and invokes `onProgress`.
- `[BackupCrypto.kt:encryptingStream]`: Draws salt and IV bytes from `SecureRandom` and clears `PBEKeySpec` password characters after key derivation.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
