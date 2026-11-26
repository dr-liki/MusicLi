# MusicBrainz Metadata Lookup & Tagging

## Overview
Queries the MusicBrainz Web Service v2 (`https://musicbrainz.org/ws/2/`) for recording metadata matches and applies selected MusicBrainz IDs and tags to tracks in the unified Room library.

## Interface
- `[MusicBrainzApiService.kt:MusicBrainzMatch]` - Data class holding matched recording metadata (`recordingId`, `releaseId`, `artistId`, `title`, `artist`, `album`, `year`, `durationMs`, `score`, `disambiguation`).
- `[MusicBrainzApiService.kt:MusicBrainzApiService]` - `@Singleton` OkHttp client wrapper providing `searchRecording(title, artist, album, durationMs)` plus internal query/scoring helpers (`buildRecordingQuery`, `parseSearchResponse`).
- `[MusicBrainzRepository.kt:MusicBrainzRepository]` - `@Singleton` repository exposing `search(song)` and `apply(song, match)` on `Dispatchers.IO`.

## Invariants
- `[MusicBrainzApiService.kt:MusicBrainzApiService.requestMutex]` enforces a minimum `MIN_REQUEST_INTERVAL_MS` (`1_100L` ms) between outbound MusicBrainz requests and attaches a `User-Agent` header (`MusicLi/<versionName>`).
- `[MusicBrainzApiService.kt:MusicBrainzApiService.Companion.buildRecordingQuery]` escapes Lucene special characters (`+ - & | ! ( ) { } [ ] ^ " ~ * ? : \ /`) and omits the `release:` clause when `album` is blank or `"Unknown Album"`.
- `[MusicBrainzApiService.kt:MusicBrainzApiService.Companion.parseSearchResponse]` bounds results via `MAX_RESULTS` (`8`), skips entries lacking `id` or `title`, clamps `score` to `0..100` (adjusting for normalized title/artist/album match and duration delta), and sorts descending by `score`.
- `[MusicBrainzRepository.kt:MusicBrainzRepository.apply]` requires `song` to resolve to a numeric Room `songId` via `song.id.toLongOrNull()` or `musicDao.getSongIdByContentUri(song.contentUriString)`, throwing `IllegalStateException` otherwise.

## Side Effects
- Network HTTP `GET` requests to `https://musicbrainz.org/ws/2/recording` via `OkHttpClient` (`[MusicBrainzApiService.kt:MusicBrainzApiService.searchRecording]`).
- Room database update via `MusicDao.applyMusicBrainzMatch` (`[MusicBrainzRepository.kt:MusicBrainzRepository.apply]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
