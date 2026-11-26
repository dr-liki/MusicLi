# ViewModel Extensions

## Overview
Provides `DeckController` under `presentation.viewmodel.exts`, wrapping a nullable audio-only `ExoPlayer` instance for single-deck mashup/DJ playback (loading a URI, play/pause, seeking, nudging, speed, volume, normalized progress, and resource release).

## Interface
- `[DeckController.kt:DeckController]`: `class DeckController(private val context: Context)` exposing:
  - `var player: ExoPlayer?` (public getter, `private set`).
  - `fun loadSong(songUri: Uri)`, `fun playPause()`, `fun seek(progress: Float)`, `fun setSpeed(speed: Float)`, `fun nudge(amountMs: Long)`, `fun setDeckVolume(deckVolume: Float)`, `fun getProgress(): Float`, `fun release()`.

## Invariants
- `[DeckController.kt:loadSong]`, `[DeckController.kt:release]`: `loadSong` calls `release()` before building and assigning a new `ExoPlayer` instance; `release()` releases any active player and nulls `player`.
- `[DeckController.kt:buildSafePlayer]`: Enforces an audio-only pipeline by overriding `buildVideoRenderers`, `buildTextRenderers`, and `buildCameraMotionRenderers` as no-ops; disables float audio output and audio offload (`AUDIO_OFFLOAD_MODE_DISABLED`), enables extension renderers (`EXTENSION_RENDERER_MODE_ON`), configures `DefaultAudioProcessorChain` with `HiResSampleRateCapAudioProcessor` and `SurroundDownmixProcessor`, and sets `AUDIO_CONTENT_TYPE_MUSIC` / `USAGE_MEDIA` with `handleAudioFocus = false`.
- `[DeckController.kt:seek]`, `[DeckController.kt:nudge]`, `[DeckController.kt:getProgress]`: Early-return (or return `0f`) when `player == null` or `duration <= 0`; `nudge` clamps position to `0..duration`; `getProgress` clamps to `0f..1f`.

## Side Effects
- `[DeckController.kt:loadSong]`, `[DeckController.kt:buildSafePlayer]`: Allocates and prepares `ExoPlayer` with `setHandleAudioBecomingNoisy(true)` and `setWakeMode(C.WAKE_MODE_LOCAL)`.
- `[DeckController.kt:playPause]`, `[DeckController.kt:seek]`, `[DeckController.kt:setSpeed]`, `[DeckController.kt:nudge]`, `[DeckController.kt:setDeckVolume]`, `[DeckController.kt:release]`: Mutate `ExoPlayer` playback state, position, `PlaybackParameters`, volume, and native resources.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
