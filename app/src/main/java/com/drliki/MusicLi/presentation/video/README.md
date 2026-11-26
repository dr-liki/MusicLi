# Synced Video Player

## Overview
Implements synchronized background/cover music video playback under `presentation.video`. `SyncedVideoViewModel` manages YouTube stream resolution, muted `ExoPlayer` lifecycle with a chunked range `DataSource` and link-refresh error recovery, quality selection, and video candidate search/overrides, paired with Compose UIs for inline PLL-synchronized playback (`SyncedCoverVideoPlayer.kt`), fullscreen orientation/system-bar-managed playback (`SyncedVideoDialog.kt`), candidate picking (`VideoCandidatePickerDialog.kt`), and quality selection (`VideoQualityDialog.kt`).

## Interface
- `[SyncedVideoViewModel.kt:SyncedVideoUiState]`, `[SyncedVideoViewModel.kt:VideoPickerUiState]`, `[SyncedVideoViewModel.kt:SyncedVideoViewModel]`: `@HiltViewModel` exposing `uiState: StateFlow<SyncedVideoUiState>`, `isVideoActive: StateFlow<Boolean>`, `isFullscreen: StateFlow<Boolean>`, `videoPickerState: StateFlow<VideoPickerUiState>`, `currentSearchQuery: StateFlow<String>`, `player: ExoPlayer`, and control methods (`setVideoActive`, `setFullscreen`, `toggleVideo`, `loadVideo`, `selectQuality`, `refreshCurrentVideoLink`, `onVideoPlaybackError`, `openVideoPicker`, `searchCustomQuery`, `dismissVideoPicker`, `selectVideo`, `selectVideoUrl`, `clearVideoOverride`, `onCleared`).
- `[SyncedCoverVideoPlayer.kt:SyncedCoverVideoPlayer]`, `[SyncedCoverVideoPlayer.kt:computeVideoAspectRatio]`: Inline video player composable and aspect-ratio resolver.
- `[SyncedVideoDialog.kt:SyncedVideoDialog]`: Fullscreen video player dialog composable.
- `[VideoCandidatePickerDialog.kt:VideoCandidatePickerDialog]`, `[VideoCandidatePickerDialog.kt:VideoLinkInput]`, `[VideoCandidatePickerDialog.kt:VideoSearchInput]`: Candidate search, URL paste, and override reset dialog composables.
- `[VideoQualityDialog.kt:VideoQualityDialog]`: Quality selection dialog composable (`Auto` plus available stream heights).

## Invariants
- `[SyncedVideoViewModel.kt:SyncedVideoViewModel.player]`: Lazy `ExoPlayer` is always muted (`volume = 0f`) with audio track disabled (`C.TRACK_TYPE_AUDIO`), `VIDEO_SCALING_MODE_SCALE_TO_FIT`, `ChunkedRangeDataSource` (`512 * 1024L`), and VR headers; deactivating video (`setVideoActive(false)`) resets `_isFullscreen = false` and pauses if initialized.
- `[SyncedVideoViewModel.kt:SyncedVideoViewModel]`: `loadVideo` deduplicates by composite key `"$title|$artist|$genre|$durationMs|$selectedVideoId"` and resets `linkRefreshesLeft` to `MAX_LINK_REFRESHES_PER_SONG` (2); `resolveStream`/`selectQuality`/`refreshCurrentVideoLink` fall back to `bundle.defaultStream` (`selectedHeight = null`) if `preferredHeight` is unavailable; `onVideoPlaybackError` drops from explicit height to Auto first, then refreshes link up to 2 times before emitting `Error`; picker requests discard stale async responses when `videoPickerRequest != request` and pin any `savedVideoId` first.
- `[SyncedCoverVideoPlayer.kt:SyncedCoverVideoPlayer]`, `[SyncedCoverVideoPlayer.kt:computeVideoAspectRatio]`: Mounts inline `TextureView` and `VideoCandidatePickerDialog` only when `!isFullscreen`; clamps Ready aspect ratio to `0.6f..2.6f` (defaulting to `16f / 9f`); 50ms PLL loop skips while `Player.STATE_BUFFERING`, enforces `> 1000L` ms cooldown between hard seeks when `absDrift > 600L`, and tunes playback speed (`0.85f`/`0.95f`/`1.0f`/`1.05f`/`1.15f`) for smaller drift.
- `[SyncedVideoDialog.kt:SyncedVideoDialog]`: Returns immediately when `!isFullscreen`; polls position every 200ms while `!isSeeking`; auto-hides controls after 4500ms; snapshots and restores `Activity` orientation and `WindowInsetsControllerCompat` behavior on dispose.
- `[VideoCandidatePickerDialog.kt:VideoCandidatePickerDialog]`, `[VideoQualityDialog.kt:VideoQualityDialog]`: Picker renders nothing when `Hidden`, shows Reset only if `savedVideoId != null`, and falls back to `i.ytimg.com` thumbnails; quality dialog lists `Auto` (`null`) first followed by distinct heights descending.

## Side Effects
- `[SyncedVideoViewModel.kt:SyncedVideoViewModel]`: Allocates/releases `ExoPlayer` and `ChunkedRangeDataSource`, evicts `okHttpClient.connectionPool`, performs repository/network lookups and `runBlocking(Dispatchers.IO)` in `refreshStreamUrlSync`, persists `preferredVideoQuality` in `UserPreferencesRepository` and video overrides in `SyncedVideoRepository`, and logs via `android.util.Log.w`.
- `[SyncedCoverVideoPlayer.kt:SyncedCoverVideoPlayer]`, `[SyncedVideoDialog.kt:SyncedVideoDialog]`: Attach/clear `TextureView` and `Player.Listener` on `viewModel.player`, drive player transport/speed/seek, mutate `Activity.requestedOrientation`, `PhoneOrientationPolicy`, and `WindowInsetsCompat` system bars, and load thumbnails via Coil `AsyncImage`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
