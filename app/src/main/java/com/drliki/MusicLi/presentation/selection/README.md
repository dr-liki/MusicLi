# Selection

## Overview
Implements Compose multi-selection and pointer-intercepted drag-to-select mechanics for detail song lists and library categories under `presentation.selection`. Combines snapshot-backed key sets (`DetailSongSelection.kt`), index-range tracking with placeholder retry (`DragSelectionTracker.kt`), a `ModifierNodeElement` drag-selection gesture/auto-scroll coordinator (`DragToSelect.kt`), hit-testing/edge-velocity geometry (`DragToSelectGeometry.kt`), and ordered library category selection helpers (`LibrarySelectionUtils.kt`).

## Interface
- `[DetailSongSelection.kt:DetailSongSelection]`: `@Stable internal class DetailSongSelection<K>` with `keys: PersistentSet<K>`, `isActive: Boolean`, `toggle(key: K)`, `clear()`, `selectAll(visibleKeys: Iterable<K>)`, `rangeIn(visibleKeys: List<K>): List<K>?`, `keepVisible(visibleKeys: Set<K>)`, `songsIn(visibleSongs: Map<K, Song>): ImmutableList<Song>`, and `completionCallback(): () -> Unit`.
- `[DragSelectionTracker.kt:DragSelectionChange]`, `[DragSelectionTracker.kt:DragSelectionTracker]`: `internal data class DragSelectionChange<T>(select: List<T>, deselect: List<T>, isEmpty: Boolean)` and `internal class DragSelectionTracker<T>(anchorIndex: Int, anchorKey: Any, selecting: Boolean, itemKey: (T) -> Any)` with `currentIndex: Int`, `moveTo(index: Int, itemAt: (Int) -> T?, isSelected: (T) -> Boolean): DragSelectionChange<T>`, and `isInPlace(itemAt: (Int) -> T?): Boolean`.
- `[DragToSelect.kt:DragToSelectState]`, `[DragToSelect.kt:rememberDragToSelectState]`, `[DragToSelect.kt:dragToSelect]`: State holder (`isPointerDown`, `longPressedItem`, `onItemLongPressed(item)`), `@Composable rememberDragToSelectState()`, and `Modifier.dragToSelect(state, listState, itemAt, itemKey, selectedKeys, onSelectionChange, topInset = 0.dp, bottomInset = 0.dp)`.
- `[DragToSelectGeometry.kt:itemIndexAtY]`, `[DragToSelectGeometry.kt:anchorIndexOf]`, `[DragToSelectGeometry.kt:DragRangeGate]`, `[DragToSelectGeometry.kt:dragSelectAutoScrollVelocity]`, `[DragToSelectGeometry.kt:AutoScrollEdgeGate]`: Hit-testing, anchor resolution, touch-slop range gate, and edge-zone auto-scroll velocity helpers.
- `[LibrarySelectionUtils.kt:effectiveLibraryStorageFilter]`, `[LibrarySelectionUtils.kt:toggleOrderedSelection]`, `[LibrarySelectionUtils.kt:appendDistinctSelection]`, `[LibrarySelectionUtils.kt:selectionIndex]`, `[LibrarySelectionUtils.kt:flattenDistinctGroups]`: Pure ordered-selection and storage-filter helpers.

## Invariants
- `[DetailSongSelection.kt:DetailSongSelection]`: `keys` uses `referentialEqualityPolicy()` so `completionCallback()` clears `keys` only when `keys === snapshot` (no mutation since creation); `rangeIn` returns `null` unless at least two distinct indices in `visibleKeys` are selected.
- `[DragSelectionTracker.kt:DragSelectionTracker]`: Never toggles `anchorIndex`; restores exited rows to cached pre-drag state and retries `null` placeholder rows via an `unresolved` `TreeSet`; `isInPlace` verifies `anchorIndex` and non-null entered rows still match their initial `itemKey`.
- `[DragToSelect.kt:DragToSelectState.onItemLongPressed]`, `[DragToSelect.kt:dragToSelect]`: Ignores long-presses unless `isPointerDown` is `true`; requires `dragToSelect` to be placed last in the list modifier chain and the `LazyColumn` not to use `reverseLayout`.
- `[DragToSelect.kt:DragToSelectNode]`: Consumes pointer events on `PointerEventPass.Initial` while holding the list until pointer lift; aborts drag early if external selection mutation (`selectedKeys() != selectionLeftByDrag`) or list shift (`!tracker.isInPlace(itemAt)`) occurs; dispatches only `dispatchPreScroll` and `dispatchPostFling(Velocity.Zero, Velocity.Zero)` so pull-to-refresh parents are not pulled; guards `updateGeometry` with `if (!isAttached) return`.
- `[DragToSelectGeometry.kt:itemIndexAtY]`, `[DragToSelectGeometry.kt:anchorIndexOf]`, `[DragToSelectGeometry.kt:dragSelectAutoScrollVelocity]`, `[DragToSelectGeometry.kt:AutoScrollEdgeGate]`: `itemIndexAtY` returns `null` in inter-item gaps and clamps above-first/below-last positions; `anchorIndexOf` resolves by `itemKey` nearest `y + viewportStartOffset`; `dragSelectAutoScrollVelocity` caps edge zones at `bandHeight / 4f` and enforces minimum speed `0.15f * maxVelocity`.
- `[LibrarySelectionUtils.kt:effectiveLibraryStorageFilter]`, `[LibrarySelectionUtils.kt:selectionIndex]`: Forces `StorageFilter.ONLINE` when `hideLocalMedia` is `true`; `selectionIndex` returns 1-based position or `null`.

## Side Effects
- `[DetailSongSelection.kt:DetailSongSelection]`: Mutates Compose snapshot `mutableStateOf` state (`keys`).
- `[DragSelectionTracker.kt:DragSelectionTracker.moveTo]`, `[DragToSelectGeometry.kt:DragRangeGate]`, `[DragToSelectGeometry.kt:AutoScrollEdgeGate]`: Mutate internal tracker/gate state fields.
- `[DragToSelect.kt:DragToSelectNode]`: Consumes pointer events, runs `withFrameNanos` auto-scroll coroutine (`listState.scroll` / `scrollBy`), dispatches nested scroll events, invokes `onSelectionChange`, triggers `HapticFeedbackType.SegmentFrequentTick`, and logs via `Timber.tag("DragToSelect")`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
