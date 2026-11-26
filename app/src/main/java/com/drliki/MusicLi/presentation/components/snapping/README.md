# Snapping

## Overview
Provides Compose `LazyListState` item-snapping fling mechanics (`com.drliki.MusicLi.presentation.components.snapping`), derived from Snapper, to compute decay/spring fling target indices and drive smooth scroll snapping in lazy lists.

## Interface
- `[LazyListSnapper.kt:ExperimentalSnapperApi]` - Public `@RequiresOptIn` annotation class (`AnnotationRetention.BINARY`).
- `[LazyListSnapper.kt:SnapperFlingBehaviorDefaults]` - Public object exposing `SpringAnimationSpec: AnimationSpec<Float>` (`spring(stiffness = 400f)`) and `SnapIndex: (SnapperLayoutInfo, startIndex: Int, targetIndex: Int) -> Int`.
- `[LazyListSnapper.kt:SnapperLayoutInfo]` - Public abstract class defining `startScrollOffset`, `endScrollOffset`, `visibleItems`, `currentItem`, `totalItemsCount`, `determineTargetIndex(...)`, `distanceToIndexSnap(...)`, `canScrollTowardsStart()`, and `canScrollTowardsEnd()`.
- `[LazyListSnapper.kt:SnapperLayoutItemInfo]` - Public abstract class exposing item `index`, `offset`, `size`, and `toString()`.
- `[LazyListSnapper.kt:LazyListSnapperLayoutInfo]` - Public `@Stable` class (with `internal` constructor) extending `SnapperLayoutInfo` for Compose `LazyListState`.
- `[LazyListSnapper.kt:rememberLazyListSnapperLayoutInfo]` - Public `@Composable` helper returning a remembered `LazyListSnapperLayoutInfo`.
- `[LazyListSnapper.kt:rememberSnapperFlingBehavior]` - Public `@Composable` helper returning a remembered `SnapperFlingBehavior`.
- `[LazyListSnapper.kt:SnapperFlingBehavior]` - Public `FlingBehavior` implementation (with `internal` constructor) exposing `animationTarget: Int?` (`private set`) and `override suspend fun ScrollScope.performFling(initialVelocity: Float): Float`.

## Invariants
- `[LazyListSnapper.kt:SnapperFlingBehavior.performFling]` - Enforces `require(it in 0 until layoutInfo.totalItemsCount)` on the index returned by `snapIndex`.
- `[LazyListSnapper.kt:SnapperFlingBehavior.performFling]` - Returns `initialVelocity` immediately without scrolling if `!layoutInfo.canScrollTowardsStart() || !layoutInfo.canScrollTowardsEnd()` or `layoutInfo.currentItem == null`.
- `[LazyListSnapper.kt:LazyListSnapperLayoutInfo.startScrollOffset]` - Fixed constant `0`.
- `[LazyListSnapper.kt:LazyListSnapperLayoutInfo.determineTargetIndex]` - Returns `-1` when `currentItem` is `null`; returns `current.index` when `estimateDistancePerItem() <= 0`; otherwise coerces the computed target index to `0..(totalItemsCount - 1)`.
- `[LazyListSnapper.kt:SnapperFlingBehavior.performDecayFling]` - Guarantees `animationTarget` is reset to `null` in a `finally` block; cancels the animation if `abs(delta - consumed) > 0.5f`, `currentItem` becomes `null`, or `performSnapBackIfNeeded` returns `true`.
- `[LazyListSnapper.kt:SnapperFlingBehavior.performSpringFling]` - Guarantees `animationTarget` is reset to `null` in a `finally` block; cancels the animation if `currentItem` becomes `null`, `performSnapBackIfNeeded` returns `true`, or `abs(delta - consumed) > 0.5f`.

## Side Effects
- `[LazyListSnapper.kt:SnapperFlingBehavior.performDecayFling]` - Mutates Compose state `animationTarget` (sets to `targetIndex`, resets to `null` in `finally`) and calls `ScrollScope.scrollBy` during `AnimationState.animateDecay`.
- `[LazyListSnapper.kt:SnapperFlingBehavior.performSpringFling]` - Mutates Compose state `animationTarget` (sets to `targetIndex`, resets to `null` in `finally`) and calls `ScrollScope.scrollBy` during `AnimationState.animateTo`.
- `[LazyListSnapper.kt:SnapperFlingBehavior.performSnapBackIfNeeded]` - Invokes `scrollBy(snapBackAmount.toFloat())` when `calculateSnapBack` returns a non-zero offset.
- `[LazyListSnapper.kt:rememberLazyListSnapperLayoutInfo]` - Caches a `LazyListSnapperLayoutInfo` instance in Compose composition via `remember`.
- `[LazyListSnapper.kt:rememberSnapperFlingBehavior]` - Caches a `SnapperFlingBehavior` instance in Compose composition via `remember`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
