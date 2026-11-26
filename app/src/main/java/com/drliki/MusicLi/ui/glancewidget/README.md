# Glance Widgets (`ui/glancewidget`)

## Overview
Jetpack Glance home-screen app widget suite (`com.drliki.MusicLi.ui.glancewidget`) providing responsive multi-size player widgets (`PixelPlayerGlanceWidget`, `BarWidget4x1`, `ControlWidget4x2`, `GridWidget2x2`), `GlanceAppWidgetReceiver` entry points, shared Glance UI components, JSON DataStore state persistence, local artwork decoding/caching, playback action routing to `MusicService`, and broadcast-driven updates.

## Interface
- Widgets & Receivers:
  - [`PixelPlayerGlanceWidget`](PixelPlayerGlanceWidget.kt) & [`PixelPlayerGlanceWidgetReceiver`](PixelPlayerGlanceWidgetReceiver.kt): `[PixelPlayerGlanceWidget.kt:PixelPlayerGlanceWidget]` (`SizeMode.Exact`, `PlayerInfoStateDefinition`, 10 size-specific `@Composable` layouts including `VeryThinWidgetLayout`, `ThinWidgetLayout`, `GabeTwoHeightWidgetLayout`, `GabeWidgetLayout`, `OneByOneWidgetLayout`, `SmallHorizontalWidgetLayout`, `SmallWidgetLayout`, `MediumWidgetLayout`, `LargeWidgetLayout`, `ExtraLargeWidgetLayout`, plus `AlbumArtImageGlance`, `PlayPauseButtonGlance`, `NextButtonDGlance`, `NextButtonGlance`, `PreviousButtonGlance`, `EndOfQueuePlaceholder`) and `[PixelPlayerGlanceWidgetReceiver.kt:PixelPlayerGlanceWidgetReceiver]`.
  - [`BarWidget4x1`](BarWidget4x1.kt) & [`BarWidget4x1Receiver`](BarWidget4x1Receiver.kt): `[BarWidget4x1.kt:BarWidget4x1]` and `[BarWidget4x1Receiver.kt:BarWidget4x1Receiver]`.
  - [`ControlWidget4x2`](ControlWidget4x2.kt) & [`ControlWidget4x2Receiver`](ControlWidget4x2Receiver.kt): `[ControlWidget4x2.kt:ControlWidget4x2]` and `[ControlWidget4x2Receiver.kt:ControlWidget4x2Receiver]`.
  - [`GridWidget2x2`](GridWidget2x2.kt) & [`GridWidget2x2Receiver`](GridWidget2x2Receiver.kt): `[GridWidget2x2.kt:GridWidget2x2]` and `[GridWidget2x2Receiver.kt:GridWidget2x2Receiver]`.
- Actions, State & Updates:
  - [`PlayerControlActionCallback`](PlayerControlActionCallback.kt): `[PlayerControlActionCallback.kt:PlayerControlActionCallback]` (`onAction`) and `[PlayerControlActionCallback.kt:PlayerActions]` (`key`, `songIdKey`, `PLAY_PAUSE`, `NEXT`, `PREVIOUS`, `FAVORITE`, `PLAY_FROM_QUEUE`, `SHUFFLE`, `REPEAT`).
  - [`PlayerInfoStateDefinition`](PlayerInfoStateDefinition.kt): `[PlayerInfoStateDefinition.kt:PlayerInfoStateDefinition]` and `[PlayerInfoStateDefinition.kt:PlayerInfoJsonSerializer]`.
  - [`WidgetUpdateReceiver`](WidgetUpdateReceiver.kt): `[WidgetUpdateReceiver.kt:WidgetUpdateReceiver]` (`onReceive`).
- Components, Artwork & Styling:
  - [`WidgetComponents`](WidgetComponents.kt): `[WidgetComponents.kt:AlbumArtImage]`, `[WidgetComponents.kt:WidgetIconButton]`, `[WidgetComponents.kt:PreviousButton]`, `[WidgetComponents.kt:NextButton]`, `[WidgetComponents.kt:PlayPauseButton]`, `[WidgetComponents.kt:ShuffleButton]`, `[WidgetComponents.kt:RepeatButton]`.
  - [`WidgetArtworkDecoder`](WidgetArtworkDecoder.kt): `[WidgetArtworkDecoder.kt:decodeWidgetAlbumArtBitmap]` (internal).
  - [`WidgetUtils`](WidgetUtils.kt): `[WidgetUtils.kt:AlbumArtBitmapCache]`, `[WidgetUtils.kt:WidgetColors]`, `[WidgetUtils.kt:getWidgetColors]`, `[WidgetUtils.kt:toGlanceColorProviders]`.

## Invariants
- All 4 `GlanceAppWidget` classes use `SizeMode.Exact` and `PlayerInfoStateDefinition` (`[PixelPlayerGlanceWidget.kt:PixelPlayerGlanceWidget]`, `[BarWidget4x1.kt:BarWidget4x1]`, `[ControlWidget4x2.kt:ControlWidget4x2]`, `[GridWidget2x2.kt:GridWidget2x2]`).
- `[PlayerInfoStateDefinition.kt:PlayerInfoStateDefinition]` ignores `fileKey` and always reads/writes `"datastore/pixelPlayPlayerInfo_v1_json"` under `context.filesDir`; `[PlayerInfoStateDefinition.kt:PlayerInfoJsonSerializer.readFrom]` returns `defaultValue` on blank input and throws `CorruptionException` on `SerializationException`/`IOException`.
- `[PixelPlayerGlanceWidget.kt:PixelPlayerGlanceWidget.WidgetUi]` selects one of 10 layouts by comparing `LocalSize.current` against `Companion` `DpSize` thresholds, substitutes `R.string.app_name`/`R.string.widget_tap_to_open` for empty metadata, and `[PixelPlayerGlanceWidget.kt:PixelPlayerGlanceWidget.ExtraLargeWidgetLayout]` always renders 4 queue slots (`queue.take(4)` padded with `EndOfQueuePlaceholder`).
- `[PixelPlayerGlanceWidget.kt:PixelPlayerGlanceWidget.AlbumArtImageGlance]` and `[WidgetComponents.kt:AlbumArtImage]` prioritize `bitmapData` over `albumArtUri`; `[WidgetArtworkDecoder.kt:decodeWidgetAlbumArtBitmap]` restricts decoding to local URI schemes (`content`, `file`, `android.resource`, `LocalArtworkUri.SCHEME`, or blank starting with `"/"`), caps reads at `ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit`, and returns `null` if image bounds `<= 0`.
- `[WidgetUtils.kt:AlbumArtBitmapCache]` bounds its `LruCache` to 4 MiB measured by `Bitmap.byteCount` and only inserts if `getBitmap(key) == null`.
- `[PlayerControlActionCallback.kt:PlayerControlActionCallback.onAction]` exits early if `PlayerActions.key` is missing or `PLAY_FROM_QUEUE` lacks `PlayerActions.songIdKey`; on Android O+ it calls `startForegroundService` with `EXTRA_FORCE_FOREGROUND_ON_START = true` and `MusicService.markPendingTemporaryForegroundStart()` only if `startService` throws `IllegalStateException`, calling `MusicService.unmarkPendingTemporaryForegroundStart()` if `startForegroundService` throws.
- `[WidgetUpdateReceiver.kt:WidgetUpdateReceiver.onReceive]` ignores intents not matching `"com.drliki.MusicLi.ACTION_WIDGET_UPDATE_PLAYBACK_STATE"` and always runs `pendingResult.finish()` and `scope.cancel()` in `finally`.

## Side Effects
- Clicking widget bodies launches `MainActivity` via `actionStartActivity<MainActivity>()`; control buttons trigger `actionRunCallback<PlayerControlActionCallback>()` which starts `MusicService` (normal or temporary foreground) and updates `MusicService` pending foreground state.
- `[PlayerInfoStateDefinition.kt:PlayerInfoStateDefinition]` performs disk I/O at `context.filesDir/datastore/pixelPlayPlayerInfo_v1_json`.
- `[WidgetArtworkDecoder.kt:decodeWidgetAlbumArtBitmap]`, `[WidgetComponents.kt:AlbumArtImage]`, `[PixelPlayerGlanceWidget.kt:PixelPlayerGlanceWidget.AlbumArtImageGlance]`, and `[PixelPlayerGlanceWidget.kt:PixelPlayerGlanceWidget.NextButtonDGlance]` open artwork streams, decode/allocate `Bitmap`s, and mutate `AlbumArtBitmapCache`.
- `[WidgetUpdateReceiver.kt:WidgetUpdateReceiver.onReceive]` pushes widget updates to all active instances of `PixelPlayerGlanceWidget`, `BarWidget4x1`, `ControlWidget4x2`, and `GridWidget2x2` via `GlanceAppWidgetManager`.
- Widget lifecycle, action handling, and artwork decoding emit logs via `Timber`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
