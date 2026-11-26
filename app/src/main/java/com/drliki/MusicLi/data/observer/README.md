# observer

## Overview
Observes Android `MediaStore` audio and external file changes across the application process lifecycle and emits reactive rescan triggers.

## Interface
- `[MediaStoreObserver.kt:MediaStoreObserver]`: Singleton `ContentObserver` and `DefaultLifecycleObserver` exposing `mediaStoreChanges: SharedFlow<Unit>`, `externalMediaStoreChanges: SharedFlow<Unit>`, `register()`, `unregister()`, `forceRescan()`, and lifecycle callbacks `onStart`, `onStop`, `onDestroy`.

## Invariants
- `[MediaStoreObserver.kt:MediaStoreObserver]`: `register()` and `unregister()` are idempotent via `@Volatile private var isRegistered`; `mediaStoreChanges` buffers `replay = 1` with `BufferOverflow.DROP_OLDEST` and `externalMediaStoreChanges` uses `extraBufferCapacity = 1` with `BufferOverflow.DROP_OLDEST`.

## Side Effects
- `[MediaStoreObserver.kt:MediaStoreObserver]`: Attaches to `ProcessLifecycleOwner.get().lifecycle` in `init`, registers/unregisters `ContentObserver` on `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` and `MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)` via `context.contentResolver`, and emits to `SharedFlow`s on `onChange` and `forceRescan()`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
