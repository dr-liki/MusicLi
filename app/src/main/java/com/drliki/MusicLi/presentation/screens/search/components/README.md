# search/components

## Overview
UI components, typography, auto-sizing, and genre-icon resolution utilities used to render browseable genre tiles in Search and the Library Genres tab.

## Interface
- `GenreCategoriesGrid(genres: ImmutableList<Genre>, onGenreClick: (Genre) -> Unit, playerViewModel: PlayerViewModel, ...)` (`@Composable`, `@OptIn(UnstableApi::class)`): Renders a responsive `LazyVerticalGrid` of genre cards with optional header toggle, song counts, and multi-select state (`[GenreCategoriesGrid.kt:GenreCategoriesGrid]`).
- `GenreTitleAutoSize(maxFontSizeSp: Float, minFontSizeSp: Float, singleLineMinFontSizeSp: Float, stepSp: Float = DEFAULT_STEP_SP) : TextAutoSize`: Custom `TextAutoSize` implementation with `DEFAULT_STEP_SP = 0.5f` that shrinks genre titles without breaking inside words (`[GenreTitleAutoSize.kt:GenreTitleAutoSize]`).
- `internal enum class GenreTitleFit { SingleLine, Wrapped, Overflow }`, `internal fun chooseGenreTitleFontSize(...)`, `internal fun genreTitleFit(...)`: Package-internal layout classification and binary-search font-sizing helpers (`[GenreTitleAutoSize.kt:GenreTitleFit]`, `[GenreTitleAutoSize.kt:chooseGenreTitleFontSize]`, `[GenreTitleAutoSize.kt:genreTitleFit]`).
- `GenreTypography` (`object`): Exposes `TitlePresentation`, `getGenreStyle(genreId: String, genreName: String): TextStyle`, and `resolveTitlePresentationFast(genreId: String, genreName: String, isGridView: Boolean): TitlePresentation` for deterministic variable-font genre card styling (`[GenreTypography.kt:GenreTypography]`).
- `getGenreImageResource(genreId: String): Int` (`@DrawableRes`): Maps a genre string to a drawable resource ID via alias normalization, delimiter splitting, and keyword fallback (`[GenreiconProvider.kt:getGenreImageResource]`).

## Invariants
- When `genres.isEmpty()`, `GenreCategoriesGrid` renders a centered empty-state message (`R.string.no_genres_available`) and returns early without rendering a grid (`[GenreCategoriesGrid.kt:GenreCategoriesGrid]`).
- `GenreCategoriesGrid` computes column count from `isGenreGridView` and `WindowSizeClass` breakpoints (`4` for grid+expanded, `3` for grid+medium, `2` for grid+compact or list+medium, `1` for list+compact), and when `contentPadding` is `null` reserves bottom padding of `28.dp + resolveNavBarOccupiedHeight(...) + MiniPlayerHeight` (`[GenreCategoriesGrid.kt:GenreCategoriesGrid]`).
- Selection index badges on genre cards render `selectionIndex.toString()` only when `selectionIndex != null && selectionIndex >= 0`, otherwise displaying `Icons.Rounded.CheckCircle`; song count labels render only when `showSongCount && genre.songCount > 0` (`[GenreCategoriesGrid.kt:GenreCard]`).
- `chooseGenreTitleFontSize` enforces `require(stepSp > 0f)` and clamps search bounds (`minFontSizeSp.coerceAtMost(maxFontSizeSp)`, `singleLineMinFontSizeSp.coerceIn(lowest, maxFontSizeSp)`); `genreTitleFit` treats any mid-word line break (`!isBreakAllowed(lineEnd(line))`) as `GenreTitleFit.Overflow` (`[GenreTitleAutoSize.kt:chooseGenreTitleFontSize]`, `[GenreTitleAutoSize.kt:genreTitleFit]`).
- `GenreTypography.resolveTitlePresentationFast` normalizes whitespace (`trim().replace(Regex("\\s+"), " ")`), restricts `maxLines` to `1` unless `isGridView && hasLineBreakOpportunity(normalizedName)` (in which case `2`), clamps `minFontSizeSp` to `(maxFontSizeSp * 0.6f).coerceAtLeast(14f).coerceAtMost(maxFontSizeSp)`, and derives deterministic clamped variable-font axes (`R.font.gflex_variable`) from `genreId.hashCode().toLong().absoluteValue` with `LineBreak.Simple` and `Hyphens.None` (`[GenreTypography.kt:GenreTypography]`).
- `getGenreImageResource` normalizes keys with `Locale.ROOT` and `Normalizer.Form.NFD` diacritic stripping, returning `R.drawable.rounded_library_music_24` for blank inputs or unmatched genres (`[GenreiconProvider.kt:getGenreImageResource]`, `[GenreiconProvider.kt:normalizeGenreKey]`).

## Side Effects
- `GenreCategoriesGrid` collects `playerViewModel.customGenreIcons`, `playerViewModel.isGenreGridView`, and `playerViewModel.navBarCompactMode` via `collectAsStateWithLifecycle()`, invokes `playerViewModel.toggleGenreViewMode()` from the header toggle button, and dispatches `onGenreClick`, `onGenreSelectionToggle`, or `onGenreLongPress` callbacks (`[GenreCategoriesGrid.kt:GenreCategoriesGrid]`, `[GenreCategoriesGrid.kt:GenreCard]`).
- `getGenreImageResource` lazily initializes the thread-safe `GENRE_ICON_BY_ALIAS` lookup map (`LazyThreadSafetyMode.PUBLICATION`) on first access (`[GenreiconProvider.kt:GENRE_ICON_BY_ALIAS]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
