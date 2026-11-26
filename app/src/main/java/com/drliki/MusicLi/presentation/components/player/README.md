# Player Components

## Overview
Provides the full-screen player UI and its immediate modal/embedded panes (`com.drliki.MusicLi.presentation.components.player`). This includes the adaptive portrait/landscape/foldable `FullPlayerContent`, embedded queue and synced/plain lyrics panes, animated transport and toggle controls, bookmark/audio-details dialogs, and output/speed/artist picker bottom sheets.

## Interface
- `[AddBookmarkDialog.kt:AddBookmarkDialog]` - `@Composable fun AddBookmarkDialog(currentProgressMs: Long, lyricTitle: String? = null, onSave: (String) -> Unit, onDismiss: () -> Unit)`.
- `[AddBookmarkDialog.kt:resolveLyricBookmarkTitle]` - `internal fun resolveLyricBookmarkTitle(lyrics: Lyrics?, timestampMs: Long, lyricsSyncOffsetMs: Int): String?`.
- `[AnimatedPlaybackControls.kt:AnimatedPlaybackControls]` - `@Composable fun AnimatedPlaybackControls(...)` animated previous, play/pause, and next transport button group.
- `[AudioOutputBottomSheet.kt:AudioOutputBottomSheet]` - `@Composable internal fun AudioOutputBottomSheet(sheetState: SheetState, onDismiss: () -> Unit, viewModel: AudioOutputViewModel = hiltViewModel())`.
- `[BottomToggleRow.kt:BottomToggleRow]` - `@Composable fun BottomToggleRow(...)` shuffle, repeat, and favorite toggle row.
- `[EmbeddedLyricsPane.kt:EmbeddedLyricsPane]` - `@Composable fun EmbeddedLyricsPane(...)` embedded synced/plain lyrics viewer with alignment, translation/romanization, sync controls, clipboard copy, and screen-awake support.
- `[EmbeddedQueuePane.kt:EmbeddedQueuePane]` - `@Composable fun EmbeddedQueuePane(...)` embedded reorderable/swipe-dismissible play queue with sleep timer, playback speed, undo bar, and save-as-playlist support.
- `[FullPlayerContent.kt:FoldableRightPane]` - Public enum (`QUEUE`, `LYRICS`).
- `[FullPlayerContent.kt:FullPlayerContent]` - `@Composable fun FullPlayerContent(...)` primary full-player surface adapting across portrait, compact landscape, and two-pane foldable layouts.
- `[MorphingPlayPauseIcon.kt:MorphingPlayPauseIcon]` - `@Composable fun MorphingPlayPauseIcon(isPlaying: Boolean, tint: Color, size: Dp, modifier: Modifier = Modifier)`.
- `[PlaybackSpeedBottomSheet.kt:PlaybackSpeedBottomSheet]` - `@Composable fun PlaybackSpeedBottomSheet(speed: Float, onSpeedChange: (Float) -> Unit, onDismiss: () -> Unit)`.
- `[PlayerArtistPickerBottomSheet.kt:PlayerArtistPickerBottomSheet]` - `@Composable internal fun PlayerArtistPickerBottomSheet(song: Song, artists: ImmutableList<Artist>, sheetState: SheetState, onDismiss: () -> Unit, onArtistClick: (Artist) -> Unit)`.
- `[SongAudioDetailsDialog.kt:SongAudioDetailsDialog]` - `@Composable fun SongAudioDetailsDialog(...)` audio codec, sample rate, bitrate, bit depth, and file metadata dialog.

## Invariants
- `[AddBookmarkDialog.kt:AddBookmarkDialog]` & `[AddBookmarkDialog.kt:resolveLyricBookmarkTitle]` - `availableLyricTitle` is trimmed/non-blank; confirm is enabled only when `resolvedTitle.isNotBlank()`; `resolveLyricBookmarkTitle` clamps `(timestampMs + lyricsSyncOffsetMs).coerceAtLeast(0L)` and selects the last synced line `<= adjustedPositionMs`, returning `null` if blank or empty.
- `[AnimatedPlaybackControls.kt:AnimatedPlaybackControls]` - `isPlayPauseLocked` stays `true` while `lastClicked` is `NEXT` or `PREVIOUS`, deferring `pendingPlayPauseState` until `releaseDelay` (`220L`) elapses; buttons are always laid out LTR regardless of locale.
- `[BottomToggleRow.kt:BottomToggleRow]` - `repeatActive` holds iff `repeatMode != Player.REPEAT_MODE_OFF`; `repeatIcon` uses `rounded_repeat_one_24` only for `Player.REPEAT_MODE_ONE`.
- `[EmbeddedLyricsPane.kt:EmbeddedLyricsPane]` - `Sync` controls and `SyncedLyricsList` render only when `showSyncedLyrics && hasSyncedLyrics`; `ScreenAwakeCoordinator` keep-awake is active only when `keepScreenOn && isPlayerExpanded` and is disabled unconditionally on dispose, `ON_STOP`, and `ACTION_SCREEN_OFF`; `lyricsFontSizeSp` is sanitized via `sanitizeLyricsFontSize`.
- `[EmbeddedQueuePane.kt:EmbeddedQueuePane]` - Current song resolution prefers `currentMediaItemIndex` when matching `currentSongId`, else `indexOfFirst`; only items strictly after `currentSongDisplayIndex` allow reorder/swipe-dismiss; `LazyColumn` user scroll is disabled while dragging/holding reorder handles; `onReorder(fromGlobal, toGlobal)` dispatches only after drag end when `initialFrom != finalTo`; `BackHandler` is enabled only when `isFabExpanded` is `true`.
- `[FullPlayerContent.kt:FullPlayerContent]` - Retains last non-null song in `retainedSong` (returning early if both are `null`); `isFoldableTwoPane` activates only when `isMediumOrExpandedWidth && !isCompactHeight && screenWidthDp >= 660.dp`; queue drag gestures ignore touches below `bottomGestureBoundaryY`, when not `EXPANDED`/`>= 0.99f`, or in two-pane foldable mode; skip commands pass through `skipRequests` (`extraBufferCapacity = 16`) with `SKIP_COMMAND_GUARD_MS = 96L`; `EfficientTimeLabels` forces `LayoutDirection.Ltr`; `DelayedContent` suppresses gate switches while `switchOnDragRelease && isSheetDragGestureActive`.
- `[FullPlayerContent.kt:FullPlayerProgressSection]` & `[FullPlayerContent.kt:PlayerProgressBarSection]` - Playback audio metadata applies only when `playbackMetadataMediaId == song.id`; `PlayerProgressBarSection` clears `targetSeekFraction` only when `sliderDragValue == null` and either `5000ms` elapsed or `abs(progress - targetSeekFraction) < 0.04f`.
- `[MorphingPlayPauseIcon.kt:MorphingPlayPauseIcon]` - Clamps morph progress via `progress.coerceIn(0f, 1f)` before `toPath`.
- `[PlaybackSpeedBottomSheet.kt:PlaybackSpeedBottomSheet]` - Configures `rememberModalSheetState(skipPartiallyExpanded = true)`.
- `[PlayerArtistPickerBottomSheet.kt:PlayerArtistPickerBottomSheet]` - Matches primary artist by `id` (`!= 0L && != -1L`), else case-insensitive non-blank `name`, else index `0`, sorting items descending by `isPrimary`.
- `[SongAudioDetailsDialog.kt:SongAudioDetailsDialog]` & `[SongAudioDetailsDialog.kt:inspectAudioFileDetails]` - Resolves MIME/bitrate/sampleRate in order `playback* -> song.* -> extraDetails.detected*` (`> 0`); runs `MediaExtractor` only for readable local files, reads `bits-per-sample` only on `API >= S`, releases `MediaExtractor` in `finally`, and always invokes `onDismiss()` before `onOpenFullSongInfo()`.

## Side Effects
- `[AddBookmarkDialog.kt:AddBookmarkDialog]` - Requests input focus via `FocusRequester` when `!useLyricTitle` and invokes `onSave(resolvedTitle)` / `onDismiss()`.
- `[AnimatedPlaybackControls.kt:AnimatedPlaybackControls]` - Schedules `LaunchedEffect` confirmation/release timeouts (`220L`, `1_000L`), triggers `HapticFeedbackType.TextHandleMove`, and invokes `onPrevious`, `onPlayPause`, and `onNext`.
- `[AudioOutputBottomSheet.kt:AudioOutputBottomSheet]` - Toggles `viewModel.setPickerActive(true/false)` via `DisposableEffect`, invokes `viewModel.selectOutput(output.id)`, and opens `MediaRouter2.showSystemOutputSwitcher()` (API >= 34) or `Settings.ACTION_BLUETOOTH_SETTINGS` (logging `Timber.w` on `ActivityNotFoundException`).
- `[EmbeddedLyricsPane.kt:EmbeddedLyricsPane]` - Reads and edits `context.dataStore` preferences (`keep_screen_on_lyrics`, `lyrics_alignment`, `show_lyrics_translation`, `show_lyrics_romanization`, etc.), registers `LifecycleEventObserver` and `ACTION_SCREEN_OFF` `BroadcastReceiver`, mutates `ScreenAwakeCoordinator`, runs romanization on `Dispatchers.Default`, and copies lyrics to `LocalClipboard.current`.
- `[EmbeddedQueuePane.kt:EmbeddedQueuePane]` - Collects flows from `PlayerViewModel`, `SettingsViewModel`, and `PlaylistViewModel`; animates `LazyListState` scroll; triggers `performAppCompatHapticFeedback`; and calls queue/timer/speed/playlist mutation methods on those ViewModels.
- `[FullPlayerContent.kt:FullPlayerContent]` - Acquires and collects Hilt ViewModels (`AudioBookmarksViewModel`, `SyncedVideoViewModel`, `ExploreViewModel`, `PlayerViewModel`), opens `OpenDocument` picker and reads/validates URIs on `Dispatchers.IO` (`ContentResolver` + `Timber.e`), loads synced video, performs haptic feedback, and mutates bookmark/download/lyrics/queue/playback/navigation state on ViewModels.
- `[PlaybackSpeedBottomSheet.kt:PlaybackSpeedBottomSheet]` & `[PlayerArtistPickerBottomSheet.kt:PlayerArtistPickerBottomSheet]` & `[BottomToggleRow.kt:BottomToggleRow]` - Trigger modal sheet hide coroutines and invoke caller callbacks.
- `[SongAudioDetailsDialog.kt:SongAudioDetailsDialog]` - Runs `inspectAudioFileDetails` on `Dispatchers.IO` via `produceState`, performing `File` checks, `ContentResolver.openAssetFileDescriptor`, and `MediaExtractor` metadata extraction.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
