# Navigation

## Overview
Defines the Compose Navigation graph, strongly-typed route definitions, lifecycle- and graph-guarded `NavController` extensions, bottom-tab root ordering helpers, transition specs, and player-sheet back-press coordination under `presentation.navigation`.

## Interface
- `[AppNavigation.kt:AppNavigation]`: `@Composable fun AppNavigation(playerViewModel: PlayerViewModel, navController: NavHostController, paddingValues: PaddingValues, userPreferencesRepository: UserPreferencesRepository, onSearchBarActiveChange: (Boolean) -> Unit, onOpenSidebar: () -> Unit)`.
- `[MainRootRoutes.kt:isMainRootRoute]`, `[MainRootRoutes.kt:mainRootRouteIndex]`: `internal fun isMainRootRoute(route: String?): Boolean`, `internal fun mainRootRouteIndex(route: String?): Int?`.
- `[NavControllerExtensions.kt:navigateSafely]`: `fun NavController.navigateSafely(route: String): Boolean` and `fun NavController.navigateSafely(route: String, builder: NavOptionsBuilder.() -> Unit): Boolean`.
- `[NavControllerExtensions.kt:navigateSafelyReplacing]`: `fun NavController.navigateSafelyReplacing(route: String, patternToPop: String, builder: NavOptionsBuilder.() -> Unit = {}): Boolean`.
- `[NavControllerExtensions.kt:navigateToTopLevelSafely]`: `fun NavController.navigateToTopLevelSafely(route: String): Boolean`.
- `[PlayerSheetBackCoordinator.kt:PlayerSheetBackCoordinator]`: `@MainThread internal class` with `updatePlayerSheetHandlingBack(handling: Boolean)`, `updateCallback(callback: OnBackPressedCallback, enabled: Boolean)`, and `removeCallback(callback: OnBackPressedCallback)`.
- `[Screen.kt:Screen]`: `@Immutable sealed class Screen(val route: String)` defining 40 route objects (`Home`, `Explore`, `ExploreDownloads`, `StorageSense`, `Search`, `Library`, `Settings`, `Accounts`, `SettingsCategory`, `PaletteStyle`, `Experimental`, `LyricsSettings`, `BulkLyricsEmbed`, `NavBarCrRad`, `PlaylistDetail`, `DailyMixScreen`, `RecentlyPlayed`, `Stats`, `Recap`, `Duplicates`, `GenreDetail`, `DJSpace`, `AlbumDetail`, `ArtistDetail`, `EditTransition`, `About`, `Licenses`, `EasterEgg`, `ArtistSettings`, `DelimiterConfig`, `WordDelimiterConfig`, `ExcludedDirectories`, `Equalizer`, `DeviceCapabilities`, `NavidromeDashboard`, `JellyfinDashboard`, `CloudDownloads`, `Updates`, `AudioBookmarks`, `AudioBookmarkFolder`) plus `createRoute(...)` factories on parameterized routes.
- `[Transitions.kt:TRANSITION_DURATION]`, `[Transitions.kt:enterTransition]`, `[Transitions.kt:exitTransition]`, `[Transitions.kt:popEnterTransition]`, `[Transitions.kt:popExitTransition]`: Shared `450` ms duration constant and Compose transition spec builders.

## Invariants
- `[AppNavigation.kt:AppNavigation]`: Delays composing `NavHost` until `startDestination` resolves from `userPreferencesRepository.launchTabFlow.first().toRoute()`; wraps all destinations except `Screen.AudioBookmarks` and `Screen.AudioBookmarkFolder` in `ScreenWrapper`.
- `[AppNavigation.kt:AppNavigation]`: Required route arguments guard rendering (`SettingsCategory`, `PlaylistDetail`, `AlbumDetail`, `ArtistDetail` render nothing if ID is `null`; `GenreDetail` renders `R.string.nav_error_genre_id_missing`; `AudioBookmarkFolder` coerces `null` `songId` via `orEmpty()`; `Screen.Recap` registers `RecapViewModel.YEAR_ARG` as `NavType.IntType`).
- `[AppNavigation.kt:mainRootDirection]`, `[MainRootRoutes.kt:isMainRootRoute]`, `[MainRootRoutes.kt:mainRootRouteIndex]`: Only `Home` (0), `Search` (1), `Library` (2), and `Explore` (3) are main root routes; directional horizontal transitions apply only between distinct main root routes.
- `[NavControllerExtensions.kt:isReadyForNavigation]`, `[NavControllerExtensions.kt:navigateSafely]`, `[NavControllerExtensions.kt:navigateSafelyReplacing]`: Require `currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true`; `navigateSafely` forces `launchSingleTop = true`, while `navigateSafelyReplacing` uses `launchSingleTop = false` and inclusive `popUpTo(patternToPop)`.
- `[NavControllerExtensions.kt:navigateToTopLevelSafely]`: Bypasses `isReadyForNavigation()`, returns `false` if `graph.startDestinationId` throws or is `null`, and configures `popUpTo(startDestinationId) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`.
- `[PlayerSheetBackCoordinator.kt:PlayerSheetBackCoordinator]`: Main-thread-confined; updates `callback.isEnabled` synchronously as `enabled && !isPlayerSheetHandlingBack` and no-ops in `updatePlayerSheetHandlingBack` if unchanged.
- `[Screen.kt:Screen]`: Marked `@Immutable`; `AudioBookmarkFolder.createRoute` URL-encodes `songId` via `Uri.encode(songId)`, while optional query arguments (`highlightKey`, `playlistId`) are omitted when `null`.
- `[Transitions.kt:TRANSITION_DURATION]`: Uses `450` ms for `enterTransition` and `225` ms (`TRANSITION_DURATION / 2`) for `exitTransition`, `popEnterTransition`, and `popExitTransition`.

## Side Effects
- `[AppNavigation.kt:AppNavigation]`: Reads `userPreferencesRepository.launchTabFlow.first()` in `LaunchedEffect(Unit)`, instantiates `PlaylistViewModel` via `hiltViewModel()`, collects `playerViewModel.stablePlayerState`, wraps settings routes in `ProvideSettingsHighlight`, and mutates `NavController` back stack on screen callbacks.
- `[NavControllerExtensions.kt:navigateSafely]`, `[NavControllerExtensions.kt:navigateSafelyReplacing]`, `[NavControllerExtensions.kt:navigateToTopLevelSafely]`: Mutate `NavController` navigation state and back stack.
- `[PlayerSheetBackCoordinator.kt:PlayerSheetBackCoordinator]`: Mutates internal callback map and `OnBackPressedCallback.isEnabled`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
