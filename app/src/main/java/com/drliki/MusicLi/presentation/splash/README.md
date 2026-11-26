# Splash

## Overview
Implements the animated Compose splash screen overlay (`PixelPlayerSplash`) under `presentation.splash`. Aligns its initial frame with the system splash icon and colors, waits for the system splash exit (bounded by timeout), animates color blending, container/halo Material 3 Expressive shape morphing, and glyph/note beats, and finishes with an offscreen `BlendMode.Clear` circular reveal before invoking `onFinished`.

## Interface
- `[PixelPlayerSplash.kt:PixelPlayerSplash]`: `@OptIn(ExperimentalMaterial3ExpressiveApi::class) @Composable fun PixelPlayerSplash(systemSplashGone: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier)`.

## Invariants
- `[PixelPlayerSplash.kt:PixelPlayerSplash]`: Holds the static initial frame via `withTimeoutOrNull(SystemSplashWaitTimeoutMs)` (`700` ms) waiting on `snapshotFlow { currentSystemSplashGone }.first { it }`.
- `[PixelPlayerSplash.kt:PixelPlayerSplash]`: Applies `CompositingStrategy.Offscreen` on the `Canvas` `graphicsLayer` so `BlendMode.Clear` punches a transparent circular reveal without corrupting underlying content; skips halo/container/glyph drawing when `emblemAlpha <= 0f`.
- `[PixelPlayerSplash.kt:PixelPlayerSplash]`: Intercepts and consumes all pointer changes on `PointerEventPass.Initial` and clears accessibility semantics with `clearAndSetSemantics { }` while composed.
- `[PixelPlayerSplash.kt:drawMorph]`: Returns early when `diameter <= 0f` and coerces the morph chain index to `0..chain.lastIndex` and fraction to `0f..1f`.
- `[PixelPlayerSplash.kt:rememberSplashGlyph]`: Assumes `R.drawable.splash_icon` flattens into non-empty paths where `paths.first()` is the body and `paths.last()` is the note.

## Side Effects
- `[PixelPlayerSplash.kt:PixelPlayerSplash]`: Launches `LaunchedEffect(Unit)` animations (`blend`, `enter`, `settle`, `spin`, `morph`, `noteBeat`, `emblemBeat`, `reveal`) and invokes `currentOnFinished()`; runs an infinite `pointerInput(Unit)` loop consuming pointer events; reads Android resources (`R.drawable.splash_icon`, `R.color.splash_background`, `R.color.splash_glyph`).
- `[PixelPlayerSplash.kt:drawMorph]`: Mutates a remembered `android.graphics.Path` scratch buffer via `rewind()` and `Morph.toPath()` on draw frames.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
