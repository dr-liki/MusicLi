# Scoped Player Sheet & Gestures

## Overview
Provides scoped state holders, gesture handlers, predictive back handlers, motion controllers, layout math, and runtime policies (`com.drliki.MusicLi.presentation.components.scoped`) powering the unified expandable player sheet, mini-player, and embedded queue/lyrics overlays.

## Interface
- `[ComposeLoader.kt:DeferAt]`, `[ComposeLoader.kt:DeferUntil]`, `[ComposeLoader.kt:rememberSmoothProgress]` - Deferred composition gates and smooth playback progress polling.
- `[CustomNavigationBarItem.kt:CustomNavigationBarItem]` - `RowScope` bottom navigation item with animated icon/label colors and tab semantics.
- `[Expansion.kt:rememberExpansionTransition]` - Returns a Compose `Transition<Float>` over `expansionFraction`.
- `[FullPlayerCompositionPolicy.kt:FullPlayerCompositionPolicy]`, `[FullPlayerCompositionPolicy.kt:rememberFullPlayerCompositionPolicy]` - Policy controlling when full-player composition stays mounted (`shouldRenderFullPlayer`).
- `[FullPlayerRuntimePolicy.kt:FullPlayerRuntimePolicy]`, `[FullPlayerRuntimePolicy.kt:rememberFullPlayerRuntimePolicy]` - Policy gating realtime updates (`allowRealtimeUpdates`).
- `[FullPlayerVisualState.kt:FullPlayerVisualState]`, `[FullPlayerVisualState.kt:rememberFullPlayerVisualState]` - Expansion-driven `contentAlpha` and `translationY`.
- `[KeylineListScope.kt]` - Empty file (package declaration only; defines no symbols).
- `[LyricsPredictiveBackHandler.kt:LyricsPredictiveBackHandler]` - Predictive/legacy back handler driving normalized progress `[0, 1]` and `onBack`.
- `[MiniPlayerDismissGestureHandler.kt:MiniPlayerGestureOutcome]`, `[MiniPlayerDismissGestureHandler.kt:resolveMiniPlayerGestureOutcome]`, `[MiniPlayerDismissGestureHandler.kt:MiniPlayerDismissGestureHandler]`, `[MiniPlayerDismissGestureHandler.kt:rememberMiniPlayerDismissGestureHandler]`, `[MiniPlayerDismissGestureHandler.kt:miniPlayerHorizontalGesture]` - Horizontal mini-player skip/dismiss gesture state machine and `Modifier`.
- `[MiniPlayerSkipGestureHandler.kt:MiniPlayerSkipGestureHandler]`, `[MiniPlayerSkipGestureHandler.kt:rememberMiniPlayerSkipGestureHandler]` - Tanh-bounded horizontal skip gesture handler.
- `[PlayerAlbumNavigationEffect.kt:PlayerAlbumNavigationEffect]`, `[PlayerArtistNavigationEffect.kt:PlayerArtistNavigationEffect]` - Side-effect collectors collapsing the sheet and navigating to `Screen.AlbumDetail` / `Screen.ArtistDetail`.
- `[PlayerSheetHorizontalLayout.kt:sheetHorizontalPaddingPx]`, `[PlayerSheetHorizontalLayout.kt:measuresPlayerContentAtFullWidth]`, `[PlayerSheetHorizontalLayout.kt:playerContentWidthPx]`, `[PlayerSheetHorizontalLayout.kt:playerContentOffsetXPx]`, `[PlayerSheetHorizontalLayout.kt:miniPlayerLayerWidthPx]`, `[PlayerSheetHorizontalLayout.kt:miniPlayerLayerOffsetXPx]` - Width and X-offset layout math across mini/full sheet transitions.
- `[PlayerSheetPredictiveBackHandler.kt:PlayerSheetPredictiveBackHandler]` - Predictive/legacy back handler for the expanded player sheet.
- `[PrefetchAlbumNeighbors.kt:PrefetchAlbumNeighborsImg]`, `[PrefetchAlbumNeighbors.kt:PrefetchAlbumNeighbors]` - Coil neighbor artwork prefetchers.
- `[PrewarmFullPlayerState.kt:rememberPrewarmFullPlayer]` - Low-RAM-aware temporary full-player prewarm flag.
- `[QueueItemDismissGestureHandler.kt:QueueItemDismissGestureHandler]`, `[QueueItemDismissGestureHandler.kt:rememberQueueItemDismissGestureHandler]` - Left-swipe queue item dismiss handler.
- `[QueuePredictiveBackHandler.kt:QueuePredictiveBackHandler]`, `[QueuePredictiveBackHandler.kt:settleQueueBackProgress]` - Predictive/legacy back handler and settle helper for the queue sheet.
- `[QueueSheetController.kt:QueueSheetController]` - Queue sheet drag/snap/animate controller.
- `[QueueSheetRuntimeEffects.kt:QueueSheetRuntimeEffects]` - Lifecycle synchronization and top-edge detection effects for `QueueSheetController`.
- `[QueueSheetState.kt:QueueSheetState]`, `[QueueSheetState.kt:rememberQueueSheetState]` - Remembered queue sheet offset, height, and controller state.
- `[SheetActionHandlers.kt:SheetActionHandlers]`, `[SheetActionHandlers.kt:rememberSheetActionHandlers]` - Consolidated callbacks for queue, modal overlays, and album/artist/genre navigation.
- `[SheetBackAndDragState.kt:SheetBackAndDragState]`, `[SheetBackAndDragState.kt:rememberSheetBackAndDragState]` - Dragging flags and `predictiveBackEnabled` state.
- `[SheetInteractionState.kt:SheetInteractionState]`, `[SheetInteractionState.kt:rememberSheetInteractionState]` - Dynamic player shadow shape (`PlayerSheetDynamicShape`) and vertical drag handler wiring.
- `[SheetModalOverlayController.kt:SheetModalOverlayController]`, `[SheetModalOverlayController.kt:rememberSheetModalOverlayController]` - State controller for `pendingSaveQueueOverlay` and `selectedSongForInfo`.
- `[SheetMotionController.kt:SheetMotionController]` - Mutex-serialized controller for `translationY` and `expansionFraction` `Animatable`s.
- `[SheetOverlayState.kt:SheetOverlayState]`, `[SheetOverlayState.kt:computeQueueVisualOpenFraction]`, `[SheetOverlayState.kt:computeShouldRenderQueueHost]`, `[SheetOverlayState.kt:rememberSheetOverlayState]` - IME visibility, queue visibility, scrim alpha, and occlusion metrics.
- `[SheetThemeState.kt:SheetThemeState]`, `[SheetThemeState.kt:resolvePlayerSheetTargetScheme]`, `[SheetThemeState.kt:rememberSheetThemeState]` - Animated album/system/AMOLED `ColorScheme` state and mini-player readiness metrics.
- `[SheetVerticalDragGestureHandler.kt:SheetVerticalDragGestureHandler]`, `[SheetVerticalDragGestureHandler.kt:playerSheetVerticalDragGesture]` - Vertical expand/collapse and collapsed-dismiss gesture handler and `Modifier`.
- `[SheetVerticalDragMath.kt:SheetVerticalDragFrame]`, `[SheetVerticalDragMath.kt:computeSheetVerticalDragFrame]`, `[SheetVerticalDragMath.kt:resolveVerticalSheetTargetState]`, `[SheetVerticalDragMath.kt:collapseSpringDampingForFraction]`, `[SheetVerticalDragMath.kt:collapseInitialSquashForFraction]` - Pure math helpers for vertical sheet drag frames and settle physics.
- `[SheetVisualState.kt:SheetVisualState]`, `[SheetVisualState.kt:rememberSheetVisualState]` - Visual sheet padding, height, translation, and dynamic corner radii.

## Invariants
- `[ComposeLoader.kt:DeferAt]` & `[ComposeLoader.kt:DeferUntil]` - Once `ready` latches to `true` in `rememberSaveable(keepAliveKey)`, it never reverts to `false` for that key.
- `[ComposeLoader.kt:rememberSmoothProgress]` - Clamps `safeUpperBound = totalDuration.coerceAtLeast(0L)`, `safeDuration = totalDuration.coerceAtLeast(1L)`, `sampledPosition` to `0L..safeUpperBound`, `sampledFraction` to `0f..1f`, and suspends polling via `snapshotFlow` while `!isVisible || !isPlaying`.
- `[FullPlayerCompositionPolicy.kt:rememberFullPlayerCompositionPolicy]` - `shouldRenderFullPlayer` is always `false` when `currentSongId == null`, and `true` when `currentSongId != null` and expanded, `expansionFraction.value > 0.015f`, or `keepFullPlayerComposed` has latched; `expansionFraction` is never used as a `remember` or `LaunchedEffect` key.
- `[FullPlayerRuntimePolicy.kt:rememberFullPlayerRuntimePolicy]` - `allowRealtimeUpdates` is `true` iff `currentSheetState == PlayerSheetState.EXPANDED`, `expansionFraction.value >= 0.985f`, `alpha >= 0.95f`, and `!isQueueOccludingPlayer`.
- `[FullPlayerVisualState.kt:FullPlayerVisualState]` - `contentAlpha` maps `expansionFraction.value` linearly from `[0.25f, 1f]` to `[0f, 1f]`; `translationY = lerp(initialOffsetY, 0f, contentAlpha)`.
- `[MiniPlayerDismissGestureHandler.kt:resolveMiniPlayerGestureOutcome]` - Dismisses when `screenWidthPx > 0f && abs(displacementX) > screenWidthPx * 0.4f`; returns `None` when `abs(displacementX) > 120f * safeDensity`; requires `>= 56f * safeDensity` (or `>= 24f * safeDensity` with `abs(velocityX) >= 900f * safeDensity`) to skip; maps `displacementX > 0f` to `Previous` and `<= 0f` to `Next` in both LTR and RTL.
- `[MiniPlayerSkipGestureHandler.kt:MiniPlayerSkipGestureHandler]` - Ignores drag callbacks while `swapInFlight` is `true` (cleared in `finally`); bounds offset asymptotically via `travel * tanh(accumulatedDragX / travel)`; commits skip when `abs(dragX) >= screenWidthPx * 0.22f` or `(abs(dragX) > screenWidthPx * 0.05f && abs(velocityX) >= 620f && velocityX.sign == dragX.sign)`.
- `[PlayerSheetHorizontalLayout.kt:playerContentWidthPx]` & `[PlayerSheetHorizontalLayout.kt:miniPlayerLayerWidthPx]` - Full-width layout applies iff `expansionFraction > 0f`; `sheetHorizontalPaddingPx` clamps to `>= 0`.
- `[PrefetchAlbumNeighbors.kt:PrefetchAlbumNeighborsImg]` & `[PrefetchAlbumNeighbors.kt:PrefetchAlbumNeighbors]` - No-ops on empty queue or missing anchor; skips the current item; disables disk cache (`CachePolicy.DISABLED`, `diskCacheKey = null`) for `LocalArtworkUri`.
- `[PrewarmFullPlayerState.kt:rememberPrewarmFullPlayer]` - Always returns `false` when `ActivityManager.isLowRamDevice` is `true`; otherwise resets to `false` after `32ms`.
- `[QueueItemDismissGestureHandler.kt:QueueItemDismissGestureHandler]` - Ignores drags while `isDismissing` is `true`, clamps positive (left-to-right) drag to `0f`, applies a `60.dp` tension threshold (max `20.dp` dampened offset), and requires `abs(accumulatedDragX) > itemWidthPx * 0.40f` to commit dismiss.
- `[QueueSheetController.kt:QueueSheetController]` & `[QueueSheetState.kt:rememberQueueSheetState]` - `allowQueueSheetInteraction` holds iff `showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED`; `dragBy` clamps offset to `0f..hiddenOffset`; `beginDrag`/`dragBy`/`endDrag` no-op when `hiddenOffset == 0f` or `!allowInteractionProvider()`.
- `[SheetActionHandlers.kt:rememberSheetActionHandlers]` - Album navigation requires `albumId != -1L`, artist navigation requires `song.artistId != -1L`, and genre navigation requires `!song.genre.isNullOrEmpty()`.
- `[SheetBackAndDragState.kt:rememberSheetBackAndDragState]` - `predictiveBackEnabled` is `true` iff `showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED && !isDragging`.
- `[SheetInteractionState.kt:PlayerSheetDynamicShape]` - Smooth corners apply only when `useSmoothCorners && !isDragging && !playerContentExpansionFraction.isRunning` and radii exceed `1.dp`.
- `[SheetModalOverlayController.kt:SheetModalOverlayController.launchSaveQueueOverlay]` - No-ops if `pendingSaveQueueOverlay != null`.
- `[SheetMotionController.kt:SheetMotionController]` - Serializes `animateTo`, `snapTo`, `snapCollapsed`, and `syncToExpansion` via `mutex.mutate`; `targetFraction` is `1f` only when `canExpand && targetExpanded`.
- `[SheetOverlayState.kt:computeQueueVisualOpenFraction]` & `[SheetOverlayState.kt:rememberSheetOverlayState]` - Returns `0f` when `!isQueueShown || queueHiddenOffsetPx <= 0f`; `isQueueOccludingPlayer` is `fraction >= 0.08f`; `computeShouldRenderQueueHost` returns `!isKeyboardVisible || isQueueShown || isSongInfoShown`.
- `[SheetThemeState.kt:resolvePlayerSheetTargetScheme]` - Falls back to `systemColorScheme` when `!isAlbumArtTheme || !hasAlbumArt` and applies `withAmoledSurfaces()` when `isAmoledTheme`.
- `[SheetVerticalDragGestureHandler.kt:SheetVerticalDragGestureHandler]` & `[SheetVerticalDragMath.kt:computeSheetVerticalDragFrame]` - Clamps `translationY` to `[expandedY - miniHeightPx * 0.2f, collapsedY + miniHeightPx * 0.2f]` and `expansionFraction` to `[0f, 1f]`; collapsed dismiss commits only when `!cancelled && (accumulatedDragY > travel * 0.38f || (verticalVelocity > 900f && accumulatedDragY > 12.dp.toPx()))`.
- `[SheetVisualState.kt:rememberSheetVisualState]` - `currentBottomPadding` is fixed at `0.dp`; `FULL_WIDTH` nav bar style overrides collapsed horizontal padding to `14.dp`.

## Side Effects
- `[ComposeLoader.kt:rememberSmoothProgress]`, `[FullPlayerCompositionPolicy.kt:rememberFullPlayerCompositionPolicy]`, `[PrewarmFullPlayerState.kt:rememberPrewarmFullPlayer]`, `[SheetOverlayState.kt:rememberSheetOverlayState]`, `[SheetThemeState.kt:rememberSheetThemeState]`, `[QueueSheetRuntimeEffects.kt:QueueSheetRuntimeEffects]` - Launch `LaunchedEffect` coroutines, collect `snapshotFlow`s (including IME insets), query `ActivityManager`, and mutate Compose states/`Animatable`s.
- `[LyricsPredictiveBackHandler.kt:LyricsPredictiveBackHandler]`, `[PlayerSheetPredictiveBackHandler.kt:PlayerSheetPredictiveBackHandler]`, `[QueuePredictiveBackHandler.kt:QueuePredictiveBackHandler]` - Register Android 14+ `PredictiveBackHandler` or legacy `BackHandler`, drive progress `Animatable`s, update `playerViewModel` collapse fraction/sheet state, and invoke back/close callbacks.
- `[MiniPlayerDismissGestureHandler.kt:MiniPlayerDismissGestureHandler]`, `[MiniPlayerSkipGestureHandler.kt:MiniPlayerSkipGestureHandler]`, `[QueueItemDismissGestureHandler.kt:QueueItemDismissGestureHandler]`, `[SheetVerticalDragGestureHandler.kt:SheetVerticalDragGestureHandler]` - Install `pointerInput` detectors, consume drag events, track velocity via `VelocityTracker`, trigger haptic feedback (`HapticFeedbackType` / `performAppCompatHapticFeedback`), launch/cancel `Animatable` coroutines, and invoke skip/dismiss/sheet-state callbacks.
- `[PlayerAlbumNavigationEffect.kt:PlayerAlbumNavigationEffect]`, `[PlayerArtistNavigationEffect.kt:PlayerArtistNavigationEffect]`, `[SheetActionHandlers.kt:rememberSheetActionHandlers]` - Collect navigation requests, collapse `playerViewModel` and `SheetMotionController`/`QueueSheetController`, and navigate via `navController.navigateSafelyReplacing`.
- `[PrefetchAlbumNeighbors.kt:PrefetchAlbumNeighborsImg]` & `[PrefetchAlbumNeighbors.kt:PrefetchAlbumNeighbors]` - Enqueue Coil `ImageRequest`s into `ImageLoader(context)` / `Coil.imageLoader(context)`.
- `[SheetMotionController.kt:SheetMotionController]` & `[QueueSheetController.kt:QueueSheetController]` - Mutate `Animatable` values under coroutine/mutex control and await frame ticks.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
