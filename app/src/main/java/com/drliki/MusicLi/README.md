# MusicLi Application Package (`com.drliki.MusicLi`)

## Overview
Root Android application package containing `PixelPlayerApplication` (Hilt application bootstrap, WorkManager configuration, Coil `ImageLoaderFactory`, lifecycle & trim-memory orchestration), `MainActivity` (single-activity Compose host, intent routing, Media3 `MediaController` lifecycle, animated splash handoff, and adaptive navigation chrome), `ExternalPlayerActivity` (standalone overlay player for `ACTION_VIEW`/`ACTION_SEND` audio intents), `MainActivityIntentContract`, and `ReleaseTree`, along with child architecture layers (`data`, `di`, `presentation`, `ui`, `utils`).

## Interface
- **Direct Symbols**:
  - [`PixelPlayerApplication`](PixelPlayerApplication.kt): `[PixelPlayerApplication.kt:PixelPlayerApplication]` (`@HiltAndroidApp`, `ImageLoaderFactory`, `Configuration.Provider`), `[PixelPlayerApplication.kt:PixelPlayerApplication.NOTIFICATION_CHANNEL_ID]` (`"pixelplayer_music_channel"`), `[PixelPlayerApplication.kt:PixelPlayerApplication.newImageLoader]`, `[PixelPlayerApplication.kt:PixelPlayerApplication.onTrimMemory]`, `[PixelPlayerApplication.kt:PixelPlayerApplication.workManagerConfiguration]`
  - [`MainActivity`](MainActivity.kt): `[MainActivity.kt:MainActivity]` (`@AndroidEntryPoint`), `[MainActivity.kt:BottomNavItem]`, `[MainActivity.kt:MainNavItems]` (`Home`, `Search`, `Library`, `Explore`)
  - [`MainActivityIntentContract`](MainActivityIntentContract.kt): `[MainActivityIntentContract.kt:MainActivityIntentContract]` (`ACTION_SHUFFLE_ALL`, `ACTION_OPEN_PLAYLIST`, `EXTRA_PLAYLIST_ID`, `ACTION_OPEN_UPDATES`)
  - [`ExternalPlayerActivity`](ExternalPlayerActivity.kt): `[ExternalPlayerActivity.kt:ExternalPlayerActivity]` (`@AndroidEntryPoint`)
  - [`ReleaseTree`](ReleaseTree.kt): `[ReleaseTree.kt:ReleaseTree]` (extends `Timber.Tree`, logs `Log.WARN` and above)
- **Subdirectories**:
  - [`data`](./data/README.md) — Persistence, MediaStore/SAF scanning, Media3 service/DSP, sync workers, backup, cloud & online clients
  - [`di`](./di/README.md) — Hilt `SingletonComponent` modules (`AppModule`, `BackupModule`, `DispatcherProvider`, `NetworkModule`, `Qualifiers`)
  - [`presentation`](./presentation/README.md) — Compose screens, components, ViewModels, navigation, recap, selection, and synced video UI
  - [`ui`](./ui/README.md) — Material 3 theme system (`theme`) and Jetpack Glance home-screen widgets (`glancewidget`)
  - [`utils`](./utils/README.md) — Core utilities for artwork, lyrics, romanization, Media3/MediaStore, audio metadata, crash handling, and formatting

## Invariants
- `[PixelPlayerApplication.kt:PixelPlayerApplication.onCreate]` skips `CrashHandler.install(this)` when `BuildConfig.BUILD_TYPE == "benchmark"`, plants `Timber.DebugTree()` in debug vs `ReleaseTree()` in release, and creates `NOTIFICATION_CHANNEL_ID` on API 26+.
- `[PixelPlayerApplication.kt:PixelPlayerApplication.newImageLoader]` prepends `SharedCloudArtworkStringMapper`, `SharedCloudArtworkUriMapper`, `localArtworkCoilFetcherFactory`, `navidromeCoilFetcherFactory`, and `jellyfinCoilFetcherFactory`.
- `[MainActivity.kt:MainActivity.onCreate]` plays `PixelPlayerSplash` only on cold starts (`savedInstanceState == null`) when `!isBenchmarkMode` and `Settings.Global.ANIMATOR_DURATION_SCALE > 0f`; `[MainActivity.kt:MainActivity.handleIntent]` clears handled action/URI payloads to prevent replay on configuration changes.
- `[MainActivity.kt:MainActivity.onConfigurationChanged]` re-applies `PhoneOrientationPolicy.apply(this)` across fold/unfold transitions.
- `[ExternalPlayerActivity.kt:ExternalPlayerActivity]` and `[MainActivity.kt:MainActivity]` both wrap base context with `AppLocaleManager.wrapContext` and clear handled external URI/clipData/stream payloads after dispatch.
- `[ReleaseTree.kt:ReleaseTree.isLoggable]` suppresses `VERBOSE`, `DEBUG`, and `INFO` logs in release builds.

## Side Effects
- `[PixelPlayerApplication.kt:PixelPlayerApplication]` registers `ProcessLifecycleOwner` observer and `PhoneOrientationPolicy` activity callbacks, starts `SyncManager`, `M3uSyncCoordinator`, `UpdateCheckScheduler`, and `AdvancedPerformanceDiagnosticsController`, initializes `AlbumArtCacheManager.configuredCacheLimitMb`, resumes interrupted `SongMetadataEditor` write-backs, reconciles `FolderArtworkSettingsCoordinator`, and trims Coil/theme/artist/library/retriever caches on `onTrimMemory`.
- `[MainActivity.kt:MainActivity]` installs system splash screen, toggles `FLAG_KEEP_SCREEN_ON` and `preferredDisplayModeId`, connects/releases async Media3 `MediaController` for `MusicService`, launches `AllFilesAccess` activity result flows, and mutates `PlayerViewModel`/`MainViewModel` state.
- `[ExternalPlayerActivity.kt:ExternalPlayerActivity]` takes persistable URI read/write permissions when granted, invokes `playerViewModel.playExternalUri`, and launches `MainActivity` with `ACTION_SHOW_PLAYER` when expanding to full player.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
