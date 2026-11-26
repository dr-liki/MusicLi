# Video

## Overview
Provides synced YouTube music-video discovery, candidate scoring/matching, stream extraction, SponsorBlock intro-offset lookup, and chunked HTTP range streaming under `com.drliki.MusicLi.data.video`.

## Interface
- `[SyncedVideoRepository.kt:SyncedVideoRepository]` — `@Singleton` repository managing video resolution, search, manual overrides, and intro-offset fetching (`resolveVideo`, `searchVideoCandidates`, `searchByCustomQuery`, `fallbackVideoSearchQuery`, `fetchIntroOffsetMs`, `savedVideoOverride`, `getSavedVideoEntity`, `saveVideoOverride`, `clearVideoOverride`, `videoOverrideKey`, `invalidate`).
- `[YouTubeStreamExtractor.kt:YouTubeStreamExtractor]` — `@Singleton` extractor (`resolveVideoStreamBundle`, `fetchSubtitleTracks`, `searchVideos`, `SubtitleTrackInfo`) plus `[YouTubeStreamExtractor.kt:RawVideoResult]`, `[YouTubeStreamExtractor.kt:YOUTUBE_ORIGIN]`, `[YouTubeStreamExtractor.kt:ANDROID_VR_USER_AGENT]`, and internal `videoCodecPreference`, `videoCodecMime`, `VideoDecoderSupport`.
- `[YouTubeVideoStream.kt:YouTubeVideoStream]`, `[YouTubeVideoStream.kt:SyncedVideoStreamBundle]` — Video stream quality/header/expiration metadata and resolved stream bundle models (`displayQuality`).
- `[VideoCandidateMatch.kt:VideoSearchCandidate]`, `[VideoCandidateMatch.kt:extractYoutubeVideoId]`, `[VideoCandidateMatch.kt:scoreVideoCandidate]`, `[VideoCandidateMatch.kt:isSameSongVideo]` — Video ID extraction and candidate relevance/duration/take scoring (`MIN_VIDEO_CANDIDATE_SCORE = 60`, internal `TextMatch`).
- `[ChunkedRangeDataSource.kt:ChunkedRangeDataSource]` — `@UnstableApi DataSource` (`StreamRefresher`, `Replacement`, `DEFAULT_CHUNK_SIZE = 512 * 1024L`, internal `parseContentRangeTotal`) slicing YouTube/googlevideo fetches into ranged chunks and refreshing expired URLs on HTTP `403`/`410`.

## Invariants
- `[SyncedVideoRepository.kt:SyncedVideoRepository.resolveVideo]` resolves videos in strict order: `selectedVideoId` > Room `dbEntity` (when `selectedVideoId` is blank and `!forceRefresh`) > `savedVideoOverride` > `findOfficialVideoId` (`isSameSongVideo && scoreVideoCandidate >= 60`) > `findFallbackVideoId`, throwing `IllegalStateException` if none resolve; in-memory `SyncedVideoStreamBundle` cache expires after `3` hours (`CACHE_EXPIRY_MS`).
- `[SyncedVideoRepository.kt:SyncedVideoRepository.videoOverrideKey]` normalizes keys as `"${TextMatch.normalize(title)}|${TextMatch.normalize(artist)}"`; `[SyncedVideoRepository.kt:SyncedVideoRepository.fetchIntroOffsetMs]` accepts only SponsorBlock `music_offtopic` segments starting at `<= 2.5s` with `end > start`.
- `[VideoCandidateMatch.kt:extractYoutubeVideoId]` accepts only 11-character `[A-Za-z0-9_-]{11}` IDs or `youtu.be`/`youtube.com`/`youtube-nocookie.com` URLs; `[VideoCandidateMatch.kt:scoreVideoCandidate]` rejects missing title tokens, mismatched `TextMatch.isSameTake` markers, unrelated/gaming noise, and unverified channels/artists.
- `[YouTubeStreamExtractor.kt:YouTubeStreamExtractor.resolveVideoStreamBundle]` falls back in order `ANDROID_VR` InnerTube -> `VISIONOS` InnerTube -> NewPipe `StreamInfo`, requiring `playabilityStatus.status == "OK"` and device-decodable positive-height streams (`720p` > `1080p` > highest) with `5`-hour `expiresAtEpochMs`; `[YouTubeStreamExtractor.kt:YouTubeStreamExtractor.initialize]` guards `NewPipe.init` with a double-checked synchronized flag.
- `[ChunkedRangeDataSource.kt:ChunkedRangeDataSource.open]` activates chunking only for `googlevideo.com`/`youtube.com` hosts when length is unset or `> chunkSize`, caps `StreamRefresher` retries on `403`/`410` at `MAX_REFRESHES_PER_OPEN` (`2`) per `open()`, treats HTTP `416` as EOS only when `requestedLength == C.LENGTH_UNSET`, and throws `IOException` on `2` consecutive premature EOF chunk reads when length is known.

## Side Effects
- `[SyncedVideoRepository.kt:SyncedVideoRepository]` migrates legacy `"synced_video_overrides"` `SharedPreferences` into Room (`SyncedVideoDao`) on init, reads/writes `SharedPreferences` and Room on override changes/resolution, caches bundles in memory, and sends HTTP GET requests to `https://sponsor.ajay.app/api/skipSegments`.
- `[YouTubeStreamExtractor.kt:YouTubeStreamExtractor]` initializes global `NewPipe` state, queries Android `MediaCodecList`, and executes HTTPS POST/GET requests against YouTube InnerTube (`/youtubei/v1/player`, `/youtubei/v1/search`) and watch pages via `OkHttpClient`.
- `[ChunkedRangeDataSource.kt:ChunkedRangeDataSource]` opens/reads/closes upstream `DataSource`s and invokes `StreamRefresher.refresh` on `403`/`410` rejections.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
