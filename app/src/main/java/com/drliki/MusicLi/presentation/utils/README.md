# Presentation Utils

## Overview
Provides shared Compose and Android UI utility primitives under `presentation.utils`: configurable haptic feedback dispatchers (`AppHaptics.kt`), multilingual genre-to-icon mapping (`GenreIconProvider.kt`), main-thread `keepScreenOn` reference counting (`ScreenAwakeCoordinator.kt`), segmented list/grid `RoundedCornerShape` builders with pre-API-33 clipping fallback (`SegmentedItemShape.kt`), and offscreen-composited selection scaling (`SelectionScale.kt`).

## Interface
- `[AppHaptics.kt:AppHapticsConfig]`, `[AppHaptics.kt:LocalAppHapticsConfig]`, `[AppHaptics.kt:NoOpHapticFeedback]`, `[AppHaptics.kt:performAppHapticFeedback]`, `[AppHaptics.kt:performAppCompatHapticFeedback]`: Haptic configuration (`enabled: Boolean = true`, `intensityScale: Float = 1f`), CompositionLocal, no-op `HapticFeedback`, and `View` / `ViewCompat` extension dispatchers.
- `[GenreIconProvider.kt:GenreIconProvider]`: `object` exposing `DEFAULT_GENRES: List<String>`, `SELECTABLE_ICONS: List<Int>`, and `getGenreImageResource(genreId: String, customIcons: Map<String, Int> = emptyMap()): Any`.
- `[ScreenAwakeCoordinator.kt:ScreenAwakeCoordinator]`: `@MainThread object` with `request(requester: String, keepOn: Boolean, view: View? = null)`, `reset(view: View? = null)`, and `isScreenAwakeRequested(): Boolean`.
- `[SegmentedItemShape.kt:SegmentedItemShapeDefaults]`, `[SegmentedItemShape.kt:getSegmentedGridItemShape]`, `[SegmentedItemShape.kt:getSegmentedItemShape]`, `[SegmentedItemShape.kt:clipCardCornersFallback]`: Default corner radii/shapes, dynamic segmented list/grid shape builders, and `Modifier.clipCardCornersFallback(shape: Shape)`.
- `[SelectionScale.kt:seamlessSelectionScale]`: `fun Modifier.seamlessSelectionScale(scale: Float): Modifier`.

## Invariants
- `[AppHaptics.kt:performAppHapticFeedback]`, `[AppHaptics.kt:performAppCompatHapticFeedback]`: Return `false` immediately without invoking platform haptics when `!appHapticsConfig.enabled`.
- `[GenreIconProvider.kt:GenreIconProvider.getGenreImageResource]`: Checks exact `customIcons[genreId]` before normalized `genreId.lowercase().trim()` lookup, falling back to `R.drawable.rounded_library_music_24`.
- `[ScreenAwakeCoordinator.kt:ScreenAwakeCoordinator]`: Confined to `@MainThread`; tracks active requesters in a set so clearing one preserves `keepScreenOn = true` while `activeRequesters.isNotEmpty()`.
- `[SegmentedItemShape.kt:getSegmentedGridItemShape]`, `[SegmentedItemShape.kt:getSegmentedItemShape]`, `[SegmentedItemShape.kt:clipCardCornersFallback]`: Guard `count <= 1 || columns <= 0` / `count <= 1` for uniform single-item rounding, reuse `SegmentedItemShapeDefaults` when defaults match, and apply `clipCardCornersFallback` only below `Build.VERSION_CODES.TIRAMISU` (API 33).
- `[SelectionScale.kt:seamlessSelectionScale]`: Uses `CompositingStrategy.Offscreen` only when `scale != 1f` and `CompositingStrategy.Auto` otherwise.

## Side Effects
- `[AppHaptics.kt:performAppHapticFeedback]`, `[AppHaptics.kt:performAppCompatHapticFeedback]`: Dispatch `View.performHapticFeedback` / `ViewCompat.performHapticFeedback` when enabled.
- `[ScreenAwakeCoordinator.kt:ScreenAwakeCoordinator]`: Mutates `activeRequesters` and `view?.keepScreenOn`.
- `[SegmentedItemShape.kt:clipCardCornersFallback]`, `[SelectionScale.kt:seamlessSelectionScale]`: Mutate Compose draw clipping (`clipPath` on API < 33) and `graphicsLayer` compositing/scale.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
