# equalizer

## Overview
Software PCM DSP equalizer pipeline (10-band peaking EQ, bass low-shelf, stereo mid/side virtualizer, automatic headroom compensation, loudness gain, and peak limiter), Media3 `ForwardingAudioSink` integration, preset definitions, and external audio-effect session announcements.

## Interface
- `[EqualizerDsp.kt:AudioEffectsSettings]`: Immutable effect configuration (`anyEnabled`, `bandLevelsDb`, strengths `0..1000`, `loudnessGainMb`).
- `[EqualizerDsp.kt:EqualizerDsp]`: Double-precision biquad cascade + output stage (`BAND_COUNT = 10`, `MAX_BAND_DB = 15`, `MAX_BASS_BOOST_DB = 12.0`, `isBypassed`, `configure`, `update`, `reset`, `process`).
- `[EqualizerAudioSink.kt:EqualizerAudioSink]`: Media3 `ForwardingAudioSink` applying `EqualizerDsp` to decoded `MimeTypes.AUDIO_RAW` PCM buffers (`configure`, `handleBuffer`, `flush`, `reset`).
- `[EqualizerManager.kt:EqualizerManager]`: Singleton holding live `settings: StateFlow<AudioEffectsSettings>`, preference restoration (`restoreFromPreferences`), and mutators (`setEnabled`, `setBandLevel`, `applyPreset`, `setBassBoostEnabled`, `setBassBoostStrength`, `setVirtualizerEnabled`, `setVirtualizerStrength`, `setLoudnessEnhancerEnabled`, `setLoudnessEnhancerStrength`).
- `[EqualizerPreset.kt:EqualizerPreset]`: `@Serializable @Immutable` 10-band preset model with `BAND_FREQUENCIES`, 10 built-in presets (`ALL_PRESETS`), `custom`, `fromName`, and `resolve`.
- `[ExternalAudioEffectSession.kt:ExternalAudioEffectSession]`: Singleton broadcasting Android audio-effect control session open/close intents (`open`, `close`).

## Invariants
- `[EqualizerDsp.kt:EqualizerDsp]`: Owned by a single playback thread (not thread-safe); zeroes bands whose center frequency is `>= 0.45 * sampleRate`; applies automatic pre-gain headroom (`loudnessDb - peakResponseDb()`) and a unity-ceiling peak limiter (`LIMITER_CEILING = 1.0`); reports `isBypassed == true` only when `limiterGain == 1.0` and all `current`/`target` parameters are `0.0`.
- `[EqualizerAudioSink.kt:EqualizerAudioSink]`: Leaves encoded/offloaded/passthrough audio and bypassed PCM untouched (bit-exact `ByteBuffer` forwarding), supports `8/16/24/32-bit` integer and `float`/`double` PCM (both endiannesses), and retains `pendingInput`/`pendingForwarded` so downstream `handleBuffer` back-pressure retries reuse the exact same buffer.
- `[EqualizerManager.kt:restoreFromPreferences]`: Guards one-time process initialization with `restoreLock` (`Mutex`) and `restored`, clamping band levels to `[-15, 15]` dB and strengths/loudness to `[0, 1000]`.
- `[EqualizerPreset.kt:EqualizerPreset]`: `fromName` falls back to `FLAT` when unknown; `resolve` checks `"custom"`, then `customPresets`, then `fromName`.
- `[ExternalAudioEffectSession.kt:ExternalAudioEffectSession]`: Synchronizes state transitions, ignores `audioSessionId <= 0` or already-open IDs, and broadcasts `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION` for any prior session before opening a new one.

## Side Effects
- `[EqualizerAudioSink.kt:prepare]`: Allocates/grows reusable `DoubleArray` and direct `ByteBuffer` scratch buffers and delegates processed PCM to the wrapped `AudioSink`.
- `[EqualizerManager.kt:EqualizerManager]`: Reads `EqualizerPreferencesRepository` flows once and updates `_settings: MutableStateFlow<AudioEffectsSettings>`.
- `[ExternalAudioEffectSession.kt:broadcast]`: Sends implicit `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` / `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION` broadcasts via `context.sendBroadcast` and logs via `Timber`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
