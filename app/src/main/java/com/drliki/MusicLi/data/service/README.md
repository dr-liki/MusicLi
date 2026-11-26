# Service

## Overview
Hosts the Android `MediaLibraryService` (`MusicService`), Media3 browse tree (`AutoMediaBrowseTree`), sleep timer orchestration, ReplayGain normalization, media button/notification plumbing, widget state updates, and playback snapshot persistence under `com.drliki.MusicLi.data.service`, integrating child playback-engine and Quick Settings tile subsystems.

## Interface
- `[MusicService.kt:MusicService]` — `@AndroidEntryPoint MediaLibraryService` managing `MediaLibrarySession`, foreground lifecycle, `DualPlayerEngine`/`TransitionController` wiring, custom session commands, widget updates, queue snapshot persistence/restoration, headset reconnect resume, and Navidrome playback reporting/scrobbling (`NOTIFICATION_ID = 101`, `ACTION_SLEEP_TIMER_EXPIRED`, `EXTRA_FORCE_FOREGROUND_ON_START`, `isSongFavorite`, `loadArtworkBytesForWidget`, `markPendingTemporaryForegroundStart`, `unmarkPendingTemporaryForegroundStart`, `onCreate`, `onStartCommand`, `onGetSession`, `onTaskRemoved`, `onDestroy`, `onUpdateNotification`, `startForegroundService`).
- Top-level helpers in `MusicService.kt`: `[MusicService.kt:shouldContinuePlaybackAfterTaskRemoved]`, `[MusicService.kt:requiresTemporaryForegroundForStart]`, `[MusicService.kt:loadArtworkBytesViaCoil]`.
- `[AutoMediaBrowseTree.kt:AutoMediaBrowseTree]` — `@Singleton` Android Auto/external browser tree (`ROOT_ID`, `RECENTS_ID`, `LIKED_ID`, `ALBUMS_ID`, `ARTISTS_ID`, `PLAYLISTS_ID`, `ALL_SONGS_ID`, `ALBUM_PREFIX`, `ARTIST_PREFIX`, `PLAYLIST_PREFIX`, `getLibraryRootItem`, `getChildren`, `getItem`, `searchCount`, `getSearchResult`, `expandContainerSongs`, internal `paginate`, `isContainerId`).
- `[SleepTimerSession.kt:SleepTimerSession]` — `@Singleton` process-wide sleep timer state (`timer`, `countedPlay`, `events`, nested `Timer`, `CountedPlay`, `Event`).
- `[SleepTimerController.kt:SleepTimerController]` — Internal sleep-timer coordinator (`attach`, `release`, `setDurationTimer`, `onDurationExpired`, `setEndOfTrackTimer`, `cancelTimer`, `startCountedPlay`, `stopCountedPlay`, `onMediaItemTransition`, `onRepeatModeChanged`, `onPlaybackEnded`).
- `[SleepTimerAlarmScheduler.kt:SleepTimerAlarmScheduler]` & `[SleepTimerAlarmScheduler.kt:AlarmManagerSleepTimerScheduler]` — OS `AlarmManager` backstop scheduler (`schedule`, `cancel`).
- `[SleepTimerReceiver.kt:SleepTimerReceiver]` — `BroadcastReceiver` forwarding `ACTION_SLEEP_TIMER_EXPIRED` to `MusicService`.
- `[ReplayGainProcessor.kt:ReplayGainProcessor]` — ReplayGain tag loader and crossfade-aware player volume controller (`setEnabled`, `setUseAlbumGain`, `captureUserVolume`, `cancel`, `onPlayerVolumeChanged`, `reapplyLastAppliedVolume`, `prepareForTransition`, `apply`, `prefetch`, `onTransitionFinished`, `onMediaMetadataChanged`).
- `[CoilBitmapLoader.kt:CoilBitmapLoader]` — Media3 `BitmapLoader` loading/decoding artwork capped at `512` px.
- `[LocalOnlyMediaNotificationProvider.kt:LocalOnlyMediaNotificationProvider]` — `MediaNotification.Provider` wrapping `DefaultMediaNotificationProvider` with `setLocalOnly(true)` (`setSmallIcon`, `createNotification`, `handleCustomCommand`, `getNotificationChannelInfo`).
- `[MusicNotificationProvider.kt:MusicNotificationProvider]` — Custom `SessionCommand` action and extra constants (`CUSTOM_COMMAND_*`, `EXTRA_*`).
- `[PixelPlayerMediaButtonReceiver.kt:PixelPlayerMediaButtonReceiver]` — `MediaButtonReceiver` marking pending temporary foreground starts on `ACTION_MEDIA_BUTTON`.
- `[PlaybackActivityTracker.kt:PlaybackActivityTracker]` — Process-wide `AtomicBoolean` playback activity flag (`isPlaybackActive`, `setPlaybackActive`).
- `[PlaybackSnapshotItemCache.kt:PlaybackSnapshotItemCache]` — Internal immutable queue-item snapshot cache (`getOrBuild`, `invalidate`).
- `[TrustedMediaItemsResolution.kt:TrustedMediaItemsResolution]` & `[TrustedMediaItemsResolution.kt:resolveMediaItemsWithTrustedArtworkGrants]` — Internal resolver separating trusted library artwork grant items.
- `[WidgetQueuePreview.kt:widgetQueuePreviewIndices]` & `[WidgetQueuePreview.kt:toWidgetQueueItem]` — Internal queue-preview index traversal and `QueueItem` mapping helpers.
- Subdirectories:
  - [Service Player](./player/README.md) — Dual-ExoPlayer engine, crossfade/transition controller, Hi-Fi/USB bit-perfect output, PCM processors, and `ForwardingPlayer` wrappers.
  - [Service Tile](./tile/README.md) — Quick Settings `TileService` integrations (`LastPlaylistTileService`, `ShuffleAllTileService`).

## Invariants
- `[MusicService.kt:MusicService.wrapSessionPlayer]` wraps `engine.masterPlayer` in exact order `FadingPlayer` -> `MappingPlayer` -> `PreviousButtonPlayer` (`unwrapSessionPlayer()` is its strict inverse); `[MusicService.kt:MusicService.onConnect]` exposes `Player.COMMAND_GET_TIMELINE` only to `packageName` and restricts custom commands to `isPrivilegedController` (`controller.packageName == packageName || controller.isTrusted`).
- `[MusicService.kt:shouldContinuePlaybackAfterTaskRemoved]` keeps playback alive on `onTaskRemoved` only when `hasForegroundPlaybackIntent && keepPlayingInBackground`; otherwise calls `stopPlaybackAndUnload`.
- `[MusicService.kt:requiresTemporaryForegroundForStart]` promotes `MusicService` to temporary foreground only for `EXTRA_FORCE_FOREGROUND_ON_START`, pending `PixelPlayerMediaButtonReceiver` starts, or `ACTION_MEDIA_BUTTON` not yet foregrounded; `[MusicService.kt:MusicService.clearTemporaryForegroundNotification]` explicitly cancels `NOTIFICATION_ID` once the Media3 session notification takes over or after `2_000L` ms idle.
- `[MusicService.kt:MusicService.capturePlaybackSnapshotFromPlayer]` reads `engine.getFullQueue()` and `engine.getCurrentAbsoluteIndex()` when `engine.isUsingWindowedQueue()` is true and persists canonical cloud URIs via `selectCanonicalCloudPlaybackUri`; `[MusicService.kt:MusicService.flushPendingPlaybackSnapshotOnDestroy]` forces `playWhenReady = false` when flushing a pending debounced write on `onDestroy`.
- `[MusicService.kt:MusicService.onPlayerError]` skips to the next item at most `maxConsecutivePlaybackErrors` (`5`) times before resetting; `[MusicService.kt:MusicService.warnIfNoDecodableAudioTrack]` warns at most once per `mediaId`.
- `[MusicService.kt:MusicService.maybeResumeAfterHeadsetReconnect]` resumes playback only when `resumeOnHeadsetReconnectEnabled`, paused due to `AUDIO_BECOMING_NOISY` within `15_000L` ms, and a reconnectable headset output is attached while paused mid-track.
- `[MusicService.kt:MusicService.grantArtworkUriPermissions]` grants `FLAG_GRANT_READ_URI_PERMISSION` only to `content://` URIs matching `$packageName.provider` or `$packageName.artwork`.
- `[AutoMediaBrowseTree.kt:AutoMediaBrowseTree.getChildren]` enforces container-ID routing (`auto_root`, `auto_recents`, `auto_liked`, `auto_albums`, `auto_artists`, `auto_playlists`, `auto_all_songs`, `auto_album:*`, `auto_artist:*`, `auto_playlist:*`) and returns `ERROR_BAD_VALUE` for invalid IDs.
- `[SleepTimerController.kt:SleepTimerController]` allows at most one active `Timer` (`Duration` or `EndOfTrack`), sets `DualPlayerEngine.SleepTimerHold` flags (`suppressCrossfade`, `pauseAtEndOfCurrentItem`, `keepCpuAwake`), and tracks `CountedPlay` via `REPEAT_MODE_ONE` transitions.
- `[ReplayGainProcessor.kt:ReplayGainProcessor]` validates `requestToken` and `currentSessionMediaItem()?.mediaId` before applying async IO results, defers volume updates into `pendingVolume` + `engine.incomingTrackReplayGainVolume` while `engine.isTransitionRunning()`, and distinguishes programmatic `expectedVolume` writes from user volume changes.
- `[CoilBitmapLoader.kt:CoilBitmapLoader]` caps notification/AVRCP bitmaps at `512` px with hardware bitmaps and memory caching disabled.

## Side Effects
- `[MusicService.kt:MusicService]` installs a main-thread `UncaughtExceptionHandler` suppressing Android 12+ `ForegroundServiceStartNotAllowedException` from internal Media3 paths, manages `MediaLibrarySession`, foreground notifications, `AudioDeviceCallback`, `ExternalAudioEffectSession`, `ListeningStatsTracker`, `NavidromeRepository` playback/scrobble reports, `UserPreferencesRepository` snapshot persistence, `GlanceAppWidget` updates, `Toast` alerts, `grantUriPermission` calls, and `PlaybackActivityTracker`.
- `[AutoMediaBrowseTree.kt:AutoMediaBrowseTree]` queries `MusicRepository`, `PlaylistPreferencesRepository`, and `EngagementDao` (`RecentlyPlayed`).
- `[SleepTimerController.kt:SleepTimerController]` and `[SleepTimerAlarmScheduler.kt:AlarmManagerSleepTimerScheduler]` schedule/cancel `AlarmManager` RTC wakeup alarms targeting `SleepTimerReceiver`, mutate `SleepTimerSession` flows, and mutate `DualPlayerEngine` holds and `Player` repeat/pause/seek state.
- `[ReplayGainProcessor.kt:ReplayGainProcessor]` launches `Dispatchers.IO` tag reads via `ReplayGainManager` and mutates `Player.volume` and `DualPlayerEngine.incomingTrackReplayGainVolume`.
- `[CoilBitmapLoader.kt:CoilBitmapLoader]` executes Coil image requests; `[PixelPlayerMediaButtonReceiver.kt:PixelPlayerMediaButtonReceiver]` mutates `MusicService.pendingTemporaryForegroundStarts` and delegates to `MediaButtonReceiver.onReceive`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
