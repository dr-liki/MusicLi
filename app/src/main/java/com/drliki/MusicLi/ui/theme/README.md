# Theme (`ui/theme`)

## Overview
Material 3 theming package (`com.drliki.MusicLi.ui.theme`) defining static color palettes, album-art seed color extraction and dynamic color scheme generation, deterministic genre-based color schemes, smooth and expressive shapes, bundled vs. system typography tokens, and the root `PixelPlayerTheme` composable.

## Interface
- [`AppWideNowPlayingTheme`](AppWideNowPlayingTheme.kt): `[AppWideNowPlayingTheme.kt:resolveAppWideNowPlayingColorSchemePair]` (internal) resolving whether the active song's dynamic palette applies app-wide.
- [`Color`](Color.kt): Palette tokens `[Color.kt:PixelPlayerPurpleDark]`, `[Color.kt:PixelPlayerPurplePrimary]`, `[Color.kt:PixelPlayerPink]`, `[Color.kt:PixelPlayerOrange]`, `[Color.kt:PixelPlayerLightPurple]`, `[Color.kt:PixelPlayerWhite]`, `[Color.kt:PixelPlayerBlack]`, `[Color.kt:PixelPlayerSurface]`, `[Color.kt:LightBackground]`, `[Color.kt:LightSurface]`, `[Color.kt:LightSurfaceVariant]`, `[Color.kt:LightOnSurface]`, `[Color.kt:LightOnSurfaceVariant]`, `[Color.kt:LightPrimary]`, `[Color.kt:LightPrimaryContainer]`, `[Color.kt:LightOnPrimaryContainer]`, and `[Color.kt:LightOutline]`.
- [`ColorRoles`](ColorRoles.kt): `[ColorRoles.kt:ColorScoringConfig]`, `[ColorRoles.kt:ColorExtractionConfig]` (`normalizedAccuracy`), `[ColorRoles.kt:clearExtractedColorCache]`, `[ColorRoles.kt:extractSeedColor]`, `[ColorRoles.kt:generateColorSchemeFromSeed]`, `[ColorRoles.kt:generateMonochromeColorSchemeFromSeed]`, and `[ColorRoles.kt:selectSeedColorArgbFromPixels]` (internal).
- [`GenreColors`](GenreColors.kt): `[GenreColors.kt:GenreThemeColor]` and `[GenreColors.kt:GenreThemeUtils]` (`getGenreThemeColor`, `getGenreColorScheme`, `getGenreDetailColorScheme`, `getColorSchemeFromSeed`).
- [`Shape`](Shape.kt) & [`ShapeCache`](ShapeCache.kt): `[Shape.kt:Shapes]` Material 3 shape tokens and `[ShapeCache.kt:ShapeCache]` cached smooth (`smooth8`..`smooth32`, `smoothPill`) and expressive (`expressiveAvatar`, `expressiveClover`, `expressiveHero`) shapes.
- [`Theme`](Theme.kt): CompositionLocals (`[Theme.kt:LocalPixelPlayerDarkTheme]`, `[Theme.kt:LocalPixelPlayerAmoledTheme]`, `[Theme.kt:LocalShowScrollbar]`, internal `[Theme.kt:LocalUseSystemFont]`), `[Theme.kt:PixelPlayerStatusBarStyle]`, `[Theme.kt:DarkColorScheme]`, `[Theme.kt:LightColorScheme]`, `[Theme.kt:isAmoledThemePreset]`, `[Theme.kt:withAmoledSurfaces]`, `[Theme.kt:resolveAmoledColorScheme]` (internal), `[Theme.kt:getAmoledColorScheme]`, and `[Theme.kt:PixelPlayerTheme]`.
- [`Type`](Type.kt): `[Type.kt:MontserratFamily]`, `[Type.kt:ExpTitleTypography]`, `[Type.kt:RoundedSans]`, `[Type.kt:rememberDisplayFont]` (internal), `[Type.kt:withInterfaceFont]` (internal), `[Type.kt:Typography]`, and `[Type.kt:SystemTypography]` (internal).

## Invariants
- `[AppWideNowPlayingTheme.kt:resolveAppWideNowPlayingColorSchemePair]` returns `null` when `!enabled` or `currentSongId == null`, and retains `lastValidScheme` only when `currentSongScheme == null`, `!isPlaying`, and `currentSongId == lastValidSongId`.
- `[ColorRoles.kt:ColorExtractionConfig.normalizedAccuracy]` clamps `accuracyLevel` via `AlbumArtColorAccuracy.clamp` before dividing by `AlbumArtColorAccuracy.MAX`; `[ColorRoles.kt:scoreQuantizedColors]` clamps `desiredColorCount >= 1`, `minHueDifference >= 1`, and `maxHueDifference >= minHueDifference`.
- `[ColorRoles.kt:extractSeedColor]` falls back to `DarkColorScheme.primary` on exception; `[ColorRoles.kt:generateColorSchemeFromSeed]` and `[ColorRoles.kt:generateMonochromeColorSchemeFromSeed]` fall back to `ColorSchemePair(LightColorScheme, DarkColorScheme)` on exception.
- `[ColorRoles.kt:generateColorSchemeFromSeed]` forces grayscale conversion when `sourceHct.chroma <= 12.0` and max RGB channel delta `<= 10`; `[ColorRoles.kt:selectSeedColorArgbFromPixels]` returns `averageColorArgb(pixels)` directly when quantized artwork is predominantly neutral (`neutralRatio >= 0.92`, `highChromaRatio <= 0.03`, `meanChroma <= 9.0`) and fallback RGB delta `<= 10`.
- `[GenreColors.kt:GenreThemeUtils.isUnknownGenreId]` treats trimmed case-insensitive `"unknown"`, `"unknown genre"`, and `"unknown_genre"` as unknown, using `unknownDarkThemeColor`/`unknownLightThemeColor` and monochrome `unknownSeedColor` (`0xFF7C7D84`); known IDs map deterministically via `abs(genreId.hashCode()) % darkColors.size` (20 entries).
- `[ShapeCache.kt:ShapeCache]` uses `smoothnessAsPercent = 60` on all `smooth*` shapes and scales a single normalized `basePath` in `RoundedPolygonShape.createOutline`.
- `[Theme.kt:isAmoledThemePreset]` is `true` only for `ThemePreset.AMOLED`; `[Theme.kt:resolveAmoledColorScheme]` and `[Theme.kt:PixelPlayerTheme]` always force dark schemes for AMOLED (`LocalPixelPlayerDarkTheme = darkTheme || isAmoled`) and call `dynamicDarkColorScheme`/`dynamicLightColorScheme` only on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` with `DarkColorScheme`/`LightColorScheme` fallback.
- `[Theme.kt:PixelPlayerTheme]` preserves `systemDensity` when `scalePercent == InterfaceAppearance.DEFAULT_SCALE`, otherwise scales density by `scalePercent / 100f`.
- `[Type.kt:ExpTitleTypography]`, `[Type.kt:RoundedSans]`, `[Type.kt:rememberDisplayFont]`, and `[Type.kt:withInterfaceFont]` switch to `FontFamily.Default` / `SystemExpTitleTypography` when `LocalUseSystemFont.current` is `true`.

## Side Effects
- `[ColorRoles.kt:extractSeedColor]` reads/writes `extractedColorCache` (`LruCache<Int, Color>(32)`) and recycles any newly allocated resized `Bitmap`; `[ColorRoles.kt:clearExtractedColorCache]` calls `extractedColorCache.evictAll()`.
- `[GenreColors.kt:GenreThemeUtils.getColorSchemeFromSeed]` reads/writes `genreColorSchemeCache` (`LruCache<Int, ColorScheme>(96)`).
- `[Theme.kt:PixelPlayerStatusBarStyle]` executes a Compose `SideEffect` (outside edit mode) mutating the host `Activity` `Window` (`statusBarColor`, `navigationBarColor`, contrast enforcement flags on API Q+, and light/dark appearance flags).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
