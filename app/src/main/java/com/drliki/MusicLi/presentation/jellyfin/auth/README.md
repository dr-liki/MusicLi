# Jellyfin Auth

## Overview
Implements Jellyfin server authentication UI and state management under `presentation.jellyfin.auth`. `JellyfinLoginActivity` hosts the Compose `JellyfinLoginScreen` with locale wrapping, edge-to-edge theming, Android 15+ (API 37+) local network permission handling, and library picker integration, while `JellyfinLoginViewModel` drives authentication, music library discovery/selection, and initial WorkManager sync scheduling.

## Interface
- `[JellyfinLoginActivity.kt:JellyfinLoginActivity]`: `@AndroidEntryPoint` `ComponentActivity` with injected `themePreferencesRepository: ThemePreferencesRepository`; overrides `attachBaseContext(newBase: Context)` and `onCreate(savedInstanceState: Bundle?)`.
- `[JellyfinLoginActivity.kt:JellyfinLoginScreen]`: `@Composable fun JellyfinLoginScreen(viewModel: JellyfinLoginViewModel = hiltViewModel(), onClose: () -> Unit)`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginState]`: `sealed interface` with states `Idle`, `Loading`, `SelectLibraries(username: String, libraries: ImmutableList<JellyfinLibrary>)`, `Success(username: String)`, and `Error(message: String)`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel]`: `@HiltViewModel` `ViewModel` (`repository: JellyfinRepository`, `workManager: WorkManager`) exposing `val state: StateFlow<JellyfinLoginState>` and functions `login(serverUrl: String, username: String, password: String)`, `confirmLibrarySelection(libraryIds: Set<String>)`, `skipLibrarySelection()`, `clearError()`, and `reset()`.

## Invariants
- `[JellyfinLoginActivity.kt:JellyfinLoginScreen]`: On `Build.VERSION.SDK_INT >= 37`, when `JellyfinCredentials(serverUrl, username, password).requiresLocalNetworkAccess` is `true` and `Manifest.permission.ACCESS_LOCAL_NETWORK` is not `PackageManager.PERMISSION_GRANTED`, credentials are staged in `pendingLocalNetworkLogin` and permission is requested before calling `viewModel.login(...)`.
- `[JellyfinLoginActivity.kt:JellyfinLoginScreen]`: The Connect button and password IME Done action only trigger connection when `serverUrl`, `username`, and `password` are all non-blank; input fields, password visibility toggle, back button, Use HTTP button, and Connect button are disabled while `loginState` is `JellyfinLoginState.Loading`.
- `[JellyfinLoginActivity.kt:JellyfinLoginScreen]`: The Use HTTP button is also disabled when `serverUrl` already starts with `"http://"` (case-insensitive); `CloudLibraryPickerSheet` renders only when `loginState` is `JellyfinLoginState.SelectLibraries`, restricting `selectable` to `library.isMusic`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.login]`: Ignores concurrent calls by returning early if `_state.value` is `JellyfinLoginState.Loading`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.stateAfterLogin]`: Transitions to `SelectLibraries` only when `libraries.count { it.isMusic } > 1` (falling back to `emptyList()` if `getLibraries()` fails); otherwise enqueues initial sync and transitions to `Success`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.confirmLibrarySelection]`, `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.skipLibrarySelection]`: Return early unless `_state.value` is `JellyfinLoginState.SelectLibraries`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.clearError]`: Resets `_state.value` to `Idle` only when `_state.value` is `JellyfinLoginState.Error`.

## Side Effects
- `[JellyfinLoginActivity.kt:JellyfinLoginActivity.attachBaseContext]`: Wraps base `Context` via `AppLocaleManager.wrapContext(newBase)`.
- `[JellyfinLoginActivity.kt:JellyfinLoginActivity.onCreate]`: Enables edge-to-edge UI, collects `themePreferencesRepository.interfaceAppearanceFlow`, and calls `finish()` on close.
- `[JellyfinLoginActivity.kt:JellyfinLoginScreen]`: Launches system permission prompt for `Manifest.permission.ACCESS_LOCAL_NETWORK`, updates focus via `FocusManager`, shows `Toast` (`R.string.toast_welcome_user`) and calls `onClose()` on `Success`, and shows `Snackbar` on permission denial or `Error` (followed by `viewModel.clearError()`).
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.login]`: Launches a `viewModelScope` coroutine executing `repository.login(...)` and `repository.getLibraries()` network calls and mutating `_state`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.confirmLibrarySelection]`: Launches a `viewModelScope` coroutine persisting selected IDs via `repository.setSelectedLibraryIds(libraryIds)`, enqueuing initial sync, and mutating `_state`.
- `[JellyfinLoginViewModel.kt:JellyfinLoginViewModel.enqueueInitialSync]`: Enqueues unique WorkManager work `JellyfinSyncWorker.WORK_NAME_ALL` with `ExistingWorkPolicy.KEEP` and `JellyfinSyncWorker.startAllSync()`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
