# Dependency Injection (`di`)

## Overview
Hilt dependency injection modules and qualifier annotations (`com.drliki.MusicLi.di`) providing `@Singleton` bindings in `SingletonComponent` for application context, Room database and DAOs, repositories, DataStore, WorkManager, Coil `ImageLoader`, serializers, coroutine dispatchers/scopes, OkHttp/Retrofit network clients (LRCLIB, Deezer, ListenBrainz), and modular backup handlers.

## Interface
- [`AppModule`](AppModule.kt): `[AppModule.kt:AppModule]` `@Module @InstallIn(SingletonComponent::class)` object providing:
  - App & concurrency: `[AppModule.kt:provideApplication]`, `[AppModule.kt:provideSessionToken]`, `[AppModule.kt:providePreferencesDataStore]`, `[AppModule.kt:provideAppCoroutineScope]` (`@AppScope`), `[AppModule.kt:provideWorkManager]`
  - Serialization & imaging: `[AppModule.kt:provideGson]`, `[AppModule.kt:provideJson]`, `[AppModule.kt:provideImageLoader]`
  - Database & DAOs: `[AppModule.kt:providePixelPlayerDatabase]`, `[AppModule.kt:provideAlbumArtThemeDao]`, `[AppModule.kt:provideSearchHistoryDao]`, `[AppModule.kt:provideMusicDao]`, `[AppModule.kt:provideTransitionDao]`, `[AppModule.kt:provideEngagementDao]`, `[AppModule.kt:provideFavoritesDao]`, `[AppModule.kt:provideLyricsDao]`, `[AppModule.kt:provideLocalPlaylistDao]`, `[AppModule.kt:provideNavidromeDao]`, `[AppModule.kt:provideJellyfinDao]`, `[AppModule.kt:provideListenBrainzDao]`, `[AppModule.kt:provideAudioBookmarkDao]`, `[AppModule.kt:provideOfflineTrackDao]`, `[AppModule.kt:provideSyncedVideoDao]`
  - Repositories & domain services: `[AppModule.kt:provideLyricsRepository]`, `[AppModule.kt:provideSongRepository]`, `[AppModule.kt:provideFolderTreeBuilder]`, `[AppModule.kt:provideMusicRepository]`, `[AppModule.kt:provideTransitionRepository]`, `[AppModule.kt:provideAudioBookmarkRepository]`, `[AppModule.kt:provideSongMetadataEditor]`, `[AppModule.kt:provideArtistImageRepository]`
  - Networking: `[AppModule.kt:provideOkHttpClient]`, `[AppModule.kt:provideFastOkHttpClient]` (`@FastOkHttpClient`), `[AppModule.kt:provideRetrofit]` (LRCLIB), `[AppModule.kt:provideLrcLibApiService]`, `[AppModule.kt:provideDeezerRetrofit]` (`@DeezerRetrofit`), `[AppModule.kt:provideDeezerApiService]`
- [`BackupModule`](BackupModule.kt): `[BackupModule.kt:BackupModule]` `@Module @InstallIn(SingletonComponent::class)` object providing `[BackupModule.kt:provideBackupGson]` (`@BackupGson`), `[BackupModule.kt:provideBackupFormatDetector]`, and `[BackupModule.kt:provideModuleHandlers]` (`Map<BackupSection, BackupModuleHandler>`).
- [`DispatcherProvider`](DispatcherProvider.kt): `[DispatcherProvider.kt:DispatcherProvider]` interface (`main`, `io`, `default`), `[DispatcherProvider.kt:DefaultDispatcherProvider]` implementation, and `[DispatcherProvider.kt:DispatcherModule]` binding `[DispatcherProvider.kt:bindDispatcherProvider]`.
- [`NetworkModule`](NetworkModule.kt): `[NetworkModule.kt:NetworkModule]` `@Module @InstallIn(SingletonComponent::class)` object providing `[NetworkModule.kt:provideListenBrainzRetrofit]` (`@ListenBrainzRetrofit`) and `[NetworkModule.kt:provideListenBrainzApiService]`.
- [`Qualifiers`](Qualifiers.kt): `[Qualifiers.kt:DeezerRetrofit]`, `[Qualifiers.kt:FastOkHttpClient]`, `[Qualifiers.kt:BackupGson]`, `[Qualifiers.kt:AppScope]`, and `[Qualifiers.kt:ListenBrainzRetrofit]` `@Qualifier` annotations.

## Invariants
- All modules install bindings into Hilt `SingletonComponent` with `@Singleton` scope (`[AppModule.kt:AppModule]`, `[BackupModule.kt:BackupModule]`, `[DispatcherProvider.kt:DispatcherModule]`, `[NetworkModule.kt:NetworkModule]`).
- `[AppModule.kt:provideApplication]` assumes `@ApplicationContext` is an instance of `PixelPlayerApplication`.
- `[AppModule.kt:provideJson]` configures Kotlinx `Json` with `isLenient = true`, `ignoreUnknownKeys = true`, and `coerceInputValues = true`.
- `[AppModule.kt:provideAppCoroutineScope]` binds `SupervisorJob() + dispatchers.io` so child coroutine failures are isolated.
- `[AppModule.kt:providePixelPlayerDatabase]` names the database `"pixelplayer_database"`, sets `WRITE_AHEAD_LOGGING`, registers `MIGRATION_1_2` through `MIGRATION_6_7`, and enables `fallbackToDestructiveMigration(dropAllTables = true)` only when `BuildConfig.DEBUG` is `true`.
- `[AppModule.kt:provideOkHttpClient]` and `[AppModule.kt:provideFastOkHttpClient]` use a 5-idle/30s `ConnectionPool`, enable `retryOnConnectionFailure(true)`, log `HEADERS` only in `BuildConfig.DEBUG` (`NONE` otherwise), and redact `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Emby-Token`, `X-Emby-Authorization`, and `X-MediaBrowser-Token`.
- `[AppModule.kt:provideFastOkHttpClient]` restricts protocols to `HTTP_1_1`, enforces `MODERN_TLS`/`COMPATIBLE_TLS`, sets 8s connect/write and 15s read timeouts, and falls back from `Dns.SYSTEM.lookup` to `InetAddress.getAllByName` on exception.
- `[AppModule.kt:provideRetrofit]` targets `https://lrclib.net/` using `@FastOkHttpClient`; `[AppModule.kt:provideDeezerRetrofit]` targets `https://api.deezer.com/` using the default `OkHttpClient`.
- `[BackupModule.kt:provideBackupGson]` enables `setPrettyPrinting()` and `serializeNulls()`; `[BackupModule.kt:provideModuleHandlers]` maps all 13 `BackupSection` keys (`PLAYLISTS`, `GLOBAL_SETTINGS`, `FAVORITES`, `LYRICS`, `SEARCH_HISTORY`, `TRANSITIONS`, `ENGAGEMENT_STATS`, `PLAYBACK_HISTORY`, `QUICK_FILL`, `ARTIST_IMAGES`, `EQUALIZER`, `AUDIO_BOOKMARKS`, `SYNCED_VIDEOS`).
- `[DispatcherProvider.kt:DefaultDispatcherProvider]` maps `main` -> `Dispatchers.Main`, `io` -> `Dispatchers.IO`, `default` -> `Dispatchers.Default`.
- `[NetworkModule.kt:provideListenBrainzRetrofit]` sets base URL `ListenBrainzEndpoint.DEFAULT_BASE_URL` and derives a client that rewrites URLs via `endpoint.rewrite(request.url)`.

## Side Effects
- `[AppModule.kt:provideWorkManager]` initializes/fetches the process `WorkManager` singleton via `WorkManager.getInstance(context)`.
- `[AppModule.kt:providePixelPlayerDatabase]` builds the Room SQLite database `"pixelplayer_database"` on disk and attaches `PixelPlayerDatabase.createRuntimeArtifactsCallback()`.
- `[AppModule.kt:provideImageLoader]` creates Coil `DiskCache` under `context.cacheDir.resolve("image_cache")` (100 MB max) and `MemoryCache` (40 MB max) with `respectCacheHeaders(false)`.
- `[AppModule.kt:provideOkHttpClient]` and `[AppModule.kt:provideFastOkHttpClient]` mutate outgoing HTTP request headers (`User-Agent`, and `Accept: application/json` in `provideFastOkHttpClient`) and emit header logs in `BuildConfig.DEBUG`.
- `[NetworkModule.kt:provideListenBrainzRetrofit]` intercepts outgoing requests and rewrites URLs when `endpoint.rewrite(request.url)` differs from `request.url`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
