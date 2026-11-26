# External Player Overlay

## Overview
Provides the external media playback overlay (`com.drliki.MusicLi.presentation.components.external`) shown when PixelPlayer handles external playback requests. It renders a modal bottom-styled sheet with track metadata, a synchronized wavy seek bar, transport controls, and a trigger to open the full player.

## Interface
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - Public `@Composable` entry point `@OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class) fun ExternalPlayerOverlay(playerViewModel: PlayerViewModel, onDismiss: () -> Unit, onOpenFullPlayer: () -> Unit)`.

## Invariants
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `navBarCornerRadius` is sanitized via `sanitizeNavBarCornerRadius(navBarCornerRadiusRaw)` before building `AbsoluteSmoothCornerShape` (60% smoothness).
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `awaitingSong` and `sheetVisible` both initialize to `true`; `onDismiss()` is auto-invoked by `LaunchedEffect(currentSong)` only when `currentSong == null && !awaitingSong` (after a non-null song has been observed).
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `BackHandler` is enabled only while `sheetVisible` is `true`.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `totalDuration` is coerced with `coerceAtLeast(0L)`, `position` is coerced to `0L..totalDuration`, `progressFraction` defaults to `0f` when `totalDuration <= 0`, and `WavySliderExpressive` is enabled only when `totalDuration > 0`.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `sliderPosition` preserves state identity across recompositions (`remember { mutableStateOf(progressFraction) }`), and slider fractions in `onValueChange` / `onValueCommit` are coerced to `0f..1f`.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - While `isUserScrubbing` is `true`, `LaunchedEffect(progressFraction, isUserScrubbing)` skips syncing `sliderPosition` from playback progress; when `pendingSeekFraction >= 0f`, `sliderPosition` holds the committed seek position until `abs(progressFraction - pendingSeekFraction) < 0.04f` or `System.currentTimeMillis() - pendingSeekAtMs > 5000L`.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - Duration and position labels are wrapped in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` to enforce LTR formatting.

## Side Effects
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - Collects lifecycle-aware flows from `playerViewModel` (`stablePlayerState`, `currentPlaybackPosition`, `navBarCornerRadius`) via `collectAsStateWithLifecycle()`.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `LaunchedEffect(currentSong)` mutates `awaitingSong` and `sheetVisible` and calls `onDismiss()` when playback clears after an active song.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `BackHandler` and background scrim click handlers invoke `onDismiss()`.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `LaunchedEffect(currentSong.id)` and `LaunchedEffect(progressFraction, isUserScrubbing)` mutate local scrubber state (`isUserScrubbing`, `pendingSeekFraction`, `sliderPosition`) and read `System.currentTimeMillis()`.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `WavySliderExpressive.onValueCommit` invokes `playerViewModel.seekTo(targetPosition)`, reads `System.currentTimeMillis()`, and updates scrubber state.
- `[ExternalPlayerOverlay.kt:ExternalPlayerOverlay]` - `AnimatedPlaybackControls` delegates to `playerViewModel::previousSong`, `playerViewModel::playPause`, and `playerViewModel::nextSong`, and `FilledTonalButton` invokes `onOpenFullPlayer()`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
