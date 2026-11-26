# Subcomps

## Overview
Provides reusable leaf-level UI subcomponents (`com.drliki.MusicLi.presentation.components.subcomps`) shared across library, lyrics, selection, widget/Glance, and player surfaces. This includes auto-sizing text renderers, song list items, library/selection action rows, lyrics dialogs/sheets, seek bars, equalizer icons, and animated waveform primitives.

## Interface
- `[AutoSizingText.kt:AutoSizingTextToFill]` - `@Composable fun AutoSizingTextToFill(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium, minFontSize: TextUnit = 8.sp, fontFamily: FontFamily? = null, fontWeight: FontWeight? = null, maxFontSizeLimit: TextUnit = 100.sp, lineHeightRatio: Float = 1.2f, maxLines: Int = Int.MAX_VALUE)`.
- `[AutoSizingTextGlance.kt:AutoSizingTextGlance]` - `@Composable fun AutoSizingTextGlance(text: String, modifier: GlanceModifier = GlanceModifier, style: TextStyle, color: ColorProvider, width: Dp, height: Dp, textAlign: TextAlign = TextAlign.Start, minFontSize: TextUnit = 8.sp, maxFontSize: TextUnit = 100.sp)`.
- `[EnhancedSongListItem.kt:EnhancedSongListItem]` - `@Composable fun EnhancedSongListItem(modifier: Modifier = Modifier, song: Song, isPlaying: Boolean, isCurrentSong: Boolean = false, isLoading: Boolean = false, showAlbumArt: Boolean = true, albumArtSize: Dp = 50.dp, customShape: Shape? = null, containerColorOverride: Color? = null, isSelected: Boolean = false, selectionIndex: Int? = null, isSelectionMode: Boolean = false, showMoreOptionsButton: Boolean = true, onLongPress: () -> Unit = {}, onSwipeToQueue: (() -> Unit)? = null, onMoreOptionsClick: (Song) -> Unit, onClick: () -> Unit)`.
- `[ExpressiveSongListItem.kt:ExpressiveSongListItem]` - `@Composable fun ExpressiveSongListItem(song: Song, isPlaying: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier)`.
- `[FetchLyricsDialog.kt:FetchLyricsDialog]` - `@Composable fun FetchLyricsDialog(uiState: LyricsSearchUiState, currentSong: Song?, defaultForcePickResults: Boolean = !currentSong?.lyrics.isNullOrBlank(), onConfirm: (Boolean) -> Unit, onPickResult: (LyricsSearchResult) -> Unit, onManualSearch: (String, String?) -> Unit, onDismiss: () -> Unit, onImport: () -> Unit)`.
- `[FetchLyricsDialog.kt:formatDurationMsBadge]` - `internal fun formatDurationMsBadge(durationMs: Long): String`.
- `[FetchLyricsDialog.kt:NotFoundContent]` - `@Composable fun NotFoundContent(initialTitle: String, initialArtist: String?, message: String, onManualSearch: (String, String?) -> Unit, onCancel: () -> Unit)`.
- `[LibraryActionRow.kt:defaultShape]` - `val defaultShape: RoundedCornerShape`.
- `[LibraryActionRow.kt:genHeight]` - `val genHeight: Dp`.
- `[LibraryActionRow.kt:LibraryActionRow]` - `@Composable fun LibraryActionRow(...)` switching between shuffle, playlist, and folder breadcrumb toolbar modes.
- `[LibraryActionRow.kt:Breadcrumbs]` - `@Composable fun Breadcrumbs(currentFolderPath: String?, rootPath: String, rootLabel: String, onFolderClick: (String) -> Unit, onNavigateBack: () -> Unit)`.
- `[LyricsMoreBottomSheet.kt:LyricsMoreBottomSheet]` - `@Composable fun LyricsMoreBottomSheet(...)` modal bottom sheet for lyrics actions, sync controls, alignment, translation/romanization, and playback toggles.
- `[MaterialYouVectorDrawable.kt:MaterialYouVectorDrawable]` - `@Composable fun MaterialYouVectorDrawable(modifier: Modifier = Modifier, @DrawableRes drawableResId: Int, contentScale: ContentScale = ContentScale.Fit)`.
- `[PlayerProgressBarSection.kt]` - Package declaration only; defines no symbols.
- `[PlayerSeekBar.kt:PlayerSeekBar]` - `@Composable fun PlayerSeekBar(backgroundColor: Color, onBackgroundColor: Color, primaryColor: Color, currentPosition: Long, totalDuration: Long, onSeek: (Long) -> Unit, onSeekPreview: (Long?) -> Unit = {}, isPlaying: Boolean, modifier: Modifier = Modifier)`.
- `[PlayingEqIcon.kt:PlayingEqIcon]` - `@Composable fun PlayingEqIcon(modifier: Modifier = Modifier, color: Color, isPlaying: Boolean = true, bars: Int = 3, minHeightFraction: Float = 0.28f, maxHeightFraction: Float = 1.0f, phaseDurationMillis: Int = 3600, wanderDurationMillis: Int = 12000, gapFraction: Float = 0.30f)`.
- `[SelectionActionRow.kt:SelectionActionRow]` - `@Composable fun SelectionActionRow(selectedCount: Int, onSelectAll: () -> Unit, onDeselect: () -> Unit, onOptionsClick: () -> Unit, modifier: Modifier = Modifier)`.
- `[SelectionActionRow.kt:SelectionCountPill]` - `@Composable fun SelectionCountPill(selectedCount: Int, modifier: Modifier = Modifier)`.
- `[SelectionHeader.kt:SelectionHeader]` - `@Composable fun SelectionHeader(selectedSongs: ImmutableList<Song>, onPlayClick: () -> Unit, onLikeClick: () -> Unit, onShareClick: () -> Unit, modifier: Modifier = Modifier)`.
- `[SineWaveLine.kt:SineWaveLine]` - `@Composable fun SineWaveLine(modifier: Modifier = Modifier, color: Color = Color.Black, alpha: Float = 1f, strokeWidth: Dp = 2.dp, amplitude: Dp = 8.dp, waves: Float = 2f, phase: Float = 0f, animate: Boolean? = false, animationDurationMillis: Int = 2000, samples: Int = 400, cap: StrokeCap = StrokeCap.Round)`.
- `[TightWrapText.kt:TightWrapText]` - `@Composable fun TightWrapText(...)` custom layout text wrapper trimmed to glyph line bounds.

## Invariants
- `[AutoSizingText.kt:AutoSizingTextToFill]` - `upperBoundSp` is clamped via `maxFontSizeLimit.value.coerceAtLeast(minFontSize.value)`; falls back to `minFontSize` when `lowerBoundSp > upperBoundSp + 0.01f` or `minFontLayoutResult.hasVisualOverflow`; binary search runs at most 15 iterations (stopping when `upperBoundSp - lowerBoundSp < 0.1f`) and renders `Text` only when `readyToDraw` is `true`.
- `[AutoSizingTextGlance.kt:AutoSizingTextGlance]` - Bitmap dimensions clamp to at least `1x1` px (`coerceAtLeast(1)`); binary search runs only when `widthPx > 0 && heightPx > 0` and preserves `minFontSize.value` if no larger size fits `heightPx`.
- `[EnhancedSongListItem.kt:EnhancedSongListItem]` - `isHighlighted` is `true` only when `isCurrentSong && !isLoading`; when `isSelectionMode` is `true`, swipe-to-queue is disabled (`onSwipeToQueue?.takeUnless { isSelectionMode }`), taps route to `onLongPress()`, marquee/EQ/more-options controls are hidden, and semantics marks `selected = isSelected`. Selection overlay shows `selectionIndex` only when `selectionIndex != null && selectionIndex >= 0`, otherwise `Icons.Rounded.CheckCircle`.
- `[FetchLyricsDialog.kt:FetchLyricsDialog]` - Returns immediately without rendering a `Dialog` when `uiState` is `LyricsSearchUiState.Success`; `forcePickResults` state is keyed by `rememberSaveable(currentSong?.id, defaultForcePickResults)`.
- `[FetchLyricsDialog.kt:PickResultContent]` & `[FetchLyricsDialog.kt:ResultItemCard]` - Current song duration badge renders only when `currentSong != null && currentSong.duration > 0L`; `isCloseDurationMatch` holds only when both durations are positive and `abs(resultDurationMs - songDurationMs) <= 3_000L`.
- `[FetchLyricsDialog.kt:formatDurationMsBadge]` - Clamps `durationMs >= 0L`, rounds to nearest second (`+ 500L`), and formats `"%02d:%02d"` with `Locale.ROOT`.
- `[FetchLyricsDialog.kt:NotFoundContent]` - Clears blank strings and `"<unknown>"` (case-insensitive) from `normalizedArtist`; search button is enabled only when `title.isNotBlank()`.
- `[LibraryActionRow.kt:LibraryToolbarContent]` & `[LibraryActionRow.kt:LibraryActionRowContent]` - Toolbar mode precedence is `isFoldersTab -> BREADCRUMBS`, else `isPlaylistTab -> PLAYLIST`, else `SHUFFLE`; Import M3U button shows only when `isPlaylistTab && content.showImportButton`; trailing action cluster renders only when `showSortButton` is `true`.
- `[LibraryActionRow.kt:Breadcrumbs]` - When `currentFolderPath == null` (`isAtRoot`), back button is disabled and shows `Home`; if path does not start with or equals `rootPath`, `pathSegments` contains only `(rootLabel to rootPath)`; only non-last segments (`!isLast`) are clickable.
- `[LyricsMoreBottomSheet.kt:LyricsMoreBottomSheet]` - Conditionally renders Save (`lyrics != null`), Copy (`canCopyLyrics`), Sync controls (`showSyncedLyrics`), Romanization (`hasRomanizedLyrics`), Translation (`hasTranslatedLyrics`), and Immersive disable (`showSyncedLyrics && immersiveLyricsEnabled`), while Keep-screen-on is always visible and rounds all corners (`24.dp`) when first; sheet constrains insets to `WindowInsets.statusBars` plus `navigationBarsPadding`.
- `[MaterialYouVectorDrawable.kt:createVectorThemedContext]` - Sets `UI_MODE_NIGHT_YES` or `UI_MODE_NIGHT_NO` from `LocalPixelPlayerDarkTheme.current` and wraps with `R.style.Theme_PixelPlayer`.
- `[PlayerSeekBar.kt:PlayerSeekBar]` - `progressFraction` defaults to `0f` when `totalDuration <= 0` and otherwise `(currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)`; external progress updates do not overwrite `seekFraction` while `isUserSeeking` is `true` or within 5,000ms of seek commit until `abs(progressFraction - targetSeekFraction) < 0.04f`.
- `[PlayingEqIcon.kt:PlayingEqIcon]` - `LaunchedEffect` animation loops exit immediately when `!isPlaying` and wrap `phaseAnim`/`wanderAnim` modulo `2 * PI`; bar widths derive from `size.width / (bars + (bars - 1) * (1f + gapFraction))`.
- `[SelectionActionRow.kt:SelectionCountPill]` - `AnimatedVisibility` renders the pill only when `selectedCount > 0`.
- `[SelectionHeader.kt:SelectionHeader]` - Passes at most 5 items (`selectedSongs.take(5)`) to `StackedCoverArts`, renders overflow badge only when `totalCount > songs.size`, and stacks covers via `zIndex((songs.size - index).toFloat())`.
- `[SineWaveLine.kt:SineWaveLine]` - Canvas drawing aborts early when `w <= 0f || samples < 2`.
- `[TightWrapText.kt:TightWrapText]` - Computes `tightWidth = ceil(maxX - minX).toInt()` when `result.lineCount > 0` (`0` otherwise) and translates drawing by `Offset(-minX, 0f)`.

## Side Effects
- `[AutoSizingText.kt:AutoSizingTextToFill]` - Runs `LaunchedEffect` using `TextMeasurer.measure` and mutates `readyToDraw` and `currentFontSize`.
- `[AutoSizingTextGlance.kt:AutoSizingTextGlance]` - Allocates an `ARGB_8888` `Bitmap` and `android.graphics.Canvas` during composition and draws `StaticLayout` using `LocalContext.current`.
- `[EnhancedSongListItem.kt:EnhancedSongListItem]` & `[ExpressiveSongListItem.kt:ExpressiveSongListItem]` - Load artwork via `SmartImage`, drive Compose transitions, and invoke `onClick`, `onLongPress`, `onSwipeToQueue`, and `onMoreOptionsClick(song)`.
- `[FetchLyricsDialog.kt:FetchLyricsDialog]` & `[FetchLyricsDialog.kt:NotFoundContent]` - Mutate `rememberSaveable` UI state and invoke `onConfirm`, `onPickResult`, `onManualSearch`, `onDismiss`, `onImport`, and `onCancel`.
- `[LibraryActionRow.kt:LibraryActionRowContent]` & `[LibraryActionRow.kt:Breadcrumbs]` - Run `LaunchedEffect` animations (`spin` `Animatable` and `LazyListState.animateScrollToItem`) and invoke action/navigation callbacks.
- `[LyricsMoreBottomSheet.kt:LyricsMoreBottomSheet]` - Displays `ModalBottomSheet` and an internal reset confirmation `AlertDialog`, invoking `onDismissRequest` and lyrics/playback mutation callbacks.
- `[MaterialYouVectorDrawable.kt:MaterialYouVectorDrawable]` - Creates a themed `ContextThemeWrapper` and inflates a mutated drawable via `AppCompatResources.getDrawable`.
- `[PlayerSeekBar.kt:PlayerSeekBar]` - Reads `System.currentTimeMillis()`, mutates `seekFraction` / `targetSeekFraction` / `lastHapticStep`, triggers `HapticFeedbackType.TextHandleMove` on 20-step boundary crossings, and invokes `onSeekPreview` / `onSeek`.
- `[PlayingEqIcon.kt:PlayingEqIcon]` & `[SineWaveLine.kt:SineWaveLine]` - Launch coroutine/infinite animations and draw onto Compose `Canvas`.
- `[SelectionActionRow.kt:SelectionActionRow]` & `[SelectionHeader.kt:SelectionHeader]` - Trigger `SmartImage` loads, `AnimatedContent`/`AnimatedVisibility` transitions, and selection action callbacks.
- `[TightWrapText.kt:TightWrapText]` - Mutates `TextLayoutContainer.result` during layout, draws into `drawBehind`, and populates text semantics.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
