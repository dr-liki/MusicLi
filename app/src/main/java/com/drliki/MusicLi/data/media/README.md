# media

## Overview
Audio metadata reading, custom tag/rating normalization, ReplayGain gain calculation, BPM tag parsing/caching, transactional metadata and cover-art write-back (`SongMetadataEditor` + interrupted-save recovery), Media3 `MediaController` creation, `MediaItem`-to-`Song` mapping, and Coil cover-art cache invalidation.

## Interface
- `[AudioMetadataReader.kt:AudioMetadataReader]`: Reads audio tags, technical properties, MusicBrainz IDs, ReplayGain, ratings, custom fields, and artwork (`readSourceUrl`, `read(context, uri)`, `read(file, readArtwork, readCustomMetadata)`, `readWithJAudioTagger`), alongside `AudioMetadata`, `AudioMetadataArtwork`, `firstMusicBrainzId`, and `normalizeArtistMetadataValues`.
- `[AudioMetadataUtils.kt]`: URI/image helpers (`createTempAudioFileFromUri`, `resolveAudioFileExtension`, `isValidImageData`, `imageExtensionFromMimeType`, `guessImageMimeType`).
- `[CustomMetadata.kt]`: Tag-family rating encoding/decoding and custom field validation/diffing (`CustomMetadataField`, `CustomMetadataFieldUpdate`, `MetadataValueUpdate`, `CustomMetadataChanges`, `MetadataTagFamily`, `metadataTagFamily`, `encodeRatingForTag`, `decodeRatingFromTag`, `validateCustomMetadataChanges`, `buildCustomMetadataChanges`, `extractEditableCustomMetadataFields`).
- `[EmbeddedSourceUrl.kt:findEmbeddedSourceUrl]`: Extracts validated `http`/`https` source URLs from `"SOURCE"` / `"URL:SOURCE"` tag properties.
- `[SongMetadataEditor.kt:SongMetadataEditor]`: Edits tags, custom metadata fields, ReplayGain, and cover art (`songsAwaitingWriteAccess`, `editSongMetadata`, `resumeInterruptedWriteBacks`), paired with `MetadataEditError`, `InterruptedSaveResumeResult`, `SongMetadataEditResult`, `CoverArtUpdate`, and `Tag.applyCustomMetadataField`.
- `[SongMetadataWriteBack.kt]`: Staged write-back and crash-recovery engine (`PendingWriteBackStore`, `PendingWriteBackGate`, `PendingWriteBackSong`, `PathResolvedSong`, `MediaStoreRow`, `WriteBackTarget`, `SongFileStat`, `PendingWriteBack`, `WriteBackResult`, `ResumeOutcome`, `ResumedWriteBack`, `writeBackEditedCopy`, `resumePendingWriteBack`, `WRITE_BACK_SPACE_MARGIN_BYTES`, `WRITE_BACK_ATTEMPTS`, `MISSING_SONG_MAX_AGE_MILLIS`).
- `[ReplayGainManager.kt:ReplayGainManager]`: `@Singleton` ReplayGain tag reader and volume multiplier calculator (`DEFAULT_PRE_AMP_DB = 0.0f`, `ReplayGainValues`, `getCachedReplayGain`, `readReplayGain`, `gainDbToVolume`, `getVolumeMultiplier`).
- `[TagBpmReader.kt:TagBpmReader]` & `[TrackBpmRepository.kt:TrackBpmRepository]`: Parses ID3v2.3+ `TBPM` / FLAC Vorbis `BPM`/`TBPM` tags (`readBpm`) and caches plausible BPMs per `MediaItem` (`bpmFor`).
- `[ImageCacheManager.kt:ImageCacheManager]`: `@Singleton` Coil memory/disk cache invalidator (`clearAllCoverArtCaches`, `invalidateCoverArtCaches`).
- `[MediaControllerFactory.kt:MediaControllerFactory]`: `@Singleton` wrapper building an asynchronous Media3 `MediaController` (`create`).
- `[MediaMapper.kt:MediaMapper]`: `@Singleton` mapper converting a Media3 `MediaItem` into a `Song` (`resolveSongFromMediaItem`).

## Invariants
- `[AudioMetadataReader.kt:AudioMetadataReader]`: Requires positive duration/bitrate/sampleRate (scaling duration and bitrate by `1000`), validates artwork via `isValidImageData` (`BitmapFactory` bounds > 0), falls back to JAudioTagger when title/artist/requested artwork are missing or `readCustomMetadata` is true, and prefers fallback ratings over `propertyMap` ratings. `firstMusicBrainzId` matches lowercase UUIDs split on `;`, `/`, `,`; `normalizeArtistMetadataValues` deduplicates case-insensitively preserving order.
- `[CustomMetadata.kt]`: `encodeRatingForTag`/`decodeRatingFromTag` normalize 0..5 star ratings across `ID3` (`{0,1,64,128,196,255}`), `MP4` (`* 20`), and `VORBIS` (`0..5`); `validateCustomMetadataChanges` caps fields at `24`, keys at `<= 64` chars (`[A-Z0-9 _.-]`), values at `1..4096` chars, and blocks `RESERVED_CUSTOM_METADATA_KEYS`; `buildCustomMetadataChanges` emits `Keep` when `!metadataWasRead && editedRating == null`.
- `[EmbeddedSourceUrl.kt:findEmbeddedSourceUrl]`: Accepts only `http`/`https` URLs with non-empty host and null `rawUserInfo`, normalizing scheme to lowercase.
- `[SongMetadataEditor.kt:SongMetadataEditor]`: Enforces title/artist/album/albumArtist/composer `<= 500` chars (non-blank title), genre `<= 100`, lyrics `<= 50_000`; normalizes ReplayGain to `"%.2f dB"` (`Locale.US`); routes writes by magic-byte container detection (`detectContainerFormat`) rather than extension alone; forces JAudioTagger for problematic hi-res FLAC (`sampleRate > 96000 || bitsPerSample > 24`); serializes writes via `writeBackGate.writeSong` and refuses edits (`WRITE_INTERRUPTED`) if an earlier pending write-back for that path still needs access.
- `[SongMetadataWriteBack.kt]`: Stages `.pending` + `.audio` (with `fd.sync()`) before opening/truncating destinations; checks free space margin (`WRITE_BACK_SPACE_MARGIN_BYTES = 1 MiB`); retries writes up to `WRITE_BACK_ATTEMPTS` (`2`); matches MediaStore rows strictly by path and `expectedRowId`; expires missing songs only after `MISSING_SONG_MAX_AGE_MILLIS` (`30` days); and serializes `resumeAll`/`writeSong` under a single `Mutex`.
- `[ReplayGainManager.kt:ReplayGainManager]`: Uses a synchronized access-ordered `LinkedHashMap` capped at `200` entries, parses gain tags in priority order without falling through on malformed first tags, and clamps `gainDbToVolume` to `0f..2f`.
- `[TagBpmReader.kt:TagBpmReader]` & `[TrackBpmRepository.kt:TrackBpmRepository]`: `TagBpmReader` accepts only ID3v2.3+ `TBPM` or FLAC Vorbis `BPM`/`TBPM` leading numeric tokens; `TrackBpmRepository` accepts only `40f..220f` and caches results (including `NoBpm` sentinels) in an `LruCache(512)`.
- `[ImageCacheManager.kt:invalidateCoverArtCaches]`: Evicts the base URI plus fixed size suffixes (`128x128` .. `800x800`).
- `[MediaMapper.kt:resolveSongFromMediaItem]`: Returns `null` when both `EXTERNAL_EXTRA_CONTENT_URI` and `localConfiguration?.uri` are null, defaulting `artistId = -1L`, `albumId = -1L`, `duration = 0L`, and `dateAdded = System.currentTimeMillis()`.

## Side Effects
- `[AudioMetadataReader.kt]`, `[AudioMetadataUtils.kt]`, `[ReplayGainManager.kt]`, `[TagBpmReader.kt]`: Open `ContentResolver` FDs/streams, `ParcelFileDescriptor`, and `RandomAccessFile`s; create/delete temp files in `context.cacheDir`; invoke native `TagLib`; disable `org.jaudiotagger` JUL logging globally; record `PerformanceMetrics`; and log via `Timber`.
- `[SongMetadataEditor.kt]` & `[SongMetadataWriteBack.kt]`: Mutate audio files and embedded artwork on disk via TagLib, JAudioTagger, VorbisJava, sibling atomic rename, or staged `ContentResolver` `"rwt"` truncation; maintain recovery files in `context.filesDir/metadata_recovery`; update MediaStore rows and trigger `MediaScannerConnection.scanFile`; mutate Room (`MusicDao`), artwork caches (`AlbumArtUtils`), and `writeBacksAwaitingAccess` `StateFlow`.
- `[ImageCacheManager.kt]`: Clears/removes entries in Coil `memoryCache` and `diskCache` and clears `AlbumArtUtils` song caches.
- `[MediaControllerFactory.kt:create]`: Initiates asynchronous IPC binding via `MediaController.Builder.buildAsync()`.
- `[TrackBpmRepository.kt:bpmFor]`: Runs `TagBpmReader.readBpm` on `Dispatchers.IO` and mutates its in-memory `LruCache`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
