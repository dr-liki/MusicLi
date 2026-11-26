# Navidrome Auth

## Overview
Implements Navidrome/Subsonic server login UI and ViewModel under `presentation.navidrome.auth`. `NavidromeLoginActivity` hosts `NavidromeLoginScreen` with locale wrapping, edge-to-edge theming, Android 37+ local network permission prompting, and multi-folder picker modal integration, while `NavidromeLoginViewModel` manages authentication, music folder discovery, folder selection persistence, and initial WorkManager sync scheduling.

## Interface
- `[NavidromeLoginActivity.kt:NavidromeLoginActivity]`: `@AndroidEntryPoint` `ComponentActivity` with `@Inject lateinit var themePreferencesRepository: ThemePreferencesRepository`; overrides `attachBaseContext(newBase: Context)` and `onCreate(savedInstanceState: Bundle?)`.
- `[NavidromeLoginActivity.kt:NavidromeLoginScreen]`: `@Composable fun NavidromeLoginScreen(viewModel: NavidromeLoginViewModel = hiltViewModel(), onClose: () -> Unit)`.
- `[NavidromeLoginViewModel.kt:NavidromeLoginState]`: `sealed interface` with `Idle`, `Loading`, `SelectLibraries(username: String, musicFolders: ImmutableList<NavidromeMusicFolder>)`, `Success(username: String)`, and `Error(message: String)`.
- `[NavidromeLoginViewModel.kt:NavidromeLoginViewModel]`: `@HiltViewModel` `ViewModel` (`repository: NavidromeRepository`, `workManager: WorkManager`) exposing `val state: StateFlow<NavidromeLoginState>` and functions `login(serverUrl: String, username: String, password: String)`, `confirmLibrarySelection(folderIds: Set<String>)`, `skipLibrarySelection()`, `clearError()`, and `reset()`.

## Invariants
- `[NavidromeLoginActivity.kt:NavidromeLoginScreen]`: On `Build.VERSION.SDK_INT >= 37`, when `NavidromeCredentials.requiresLocalNetworkAccess` is `true` and `Manifest.permission.ACCESS_LOCAL_NETWORK` is not `PackageManager.PERMISSION_GRANTED`, credentials wait for permission grant before calling `viewModel.login(...)`.
- `[NavidromeLoginActivity.kt:NavidromeLoginScreen]`: Connect button is enabled only when `!isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()`; the `http://` prefill button is enabled only when `!isLoading && serverUrl.isBlank()`; back button, inputs, and password visibility toggle are disabled while loading; `CloudLibraryPickerSheet` renders only during `NavidromeLoginState.SelectLibraries`.
- `[NavidromeLoginViewModel.kt:login]`: Ignores calls while `_state.value` is `NavidromeLoginState.Loading`.
- `[NavidromeLoginViewModel.kt:stateAfterLogin]`: Transitions to `SelectLibraries` only when `musicFolders.size > 1`; if `getMusicFolders()` fails (falling back to `emptyList()`) or returns `<= 1` folder, enqueues initial sync and transitions to `Success`.
- `[NavidromeLoginViewModel.kt:confirmLibrarySelection]`, `[NavidromeLoginViewModel.kt:skipLibrarySelection]`: Return early unless `_state.value` is `NavidromeLoginState.SelectLibraries`.
- `[NavidromeLoginViewModel.kt:clearError]`: Resets `_state.value` to `Idle` only when `_state.value` is `NavidromeLoginState.Error`.
- `[NavidromeLoginViewModel.kt:enqueueInitialSync]`: Enqueues unique WorkManager work under `NavidromeSyncWorker.WORK_NAME_ALL` with `ExistingWorkPolicy.KEEP`.

## Side Effects
- `[NavidromeLoginActivity.kt:attachBaseContext]`: Wraps `Context` via `AppLocaleManager.wrapContext(newBase)`.
- `[NavidromeLoginActivity.kt:onCreate]`: Enables edge-to-edge UI, collects `themePreferencesRepository.interfaceAppearanceFlow`, and finishes the Activity via `onClose`.
- `[NavidromeLoginActivity.kt:NavidromeLoginScreen]`: Clears focus, launches runtime permission request for `Manifest.permission.ACCESS_LOCAL_NETWORK`, displays `Snackbar` on denial/error (calling `viewModel.clearError()`), and shows welcome `Toast` + invokes `onClose()` on `Success`.
- `[NavidromeLoginViewModel.kt:login]`, `[NavidromeLoginViewModel.kt:confirmLibrarySelection]`: Launch `viewModelScope` coroutines executing `repository.login(...)`, `repository.getMusicFolders()`, and `repository.setSelectedMusicFolderIds(folderIds)`.
- `[NavidromeLoginViewModel.kt:enqueueInitialSync]`: Enqueues `NavidromeSyncWorker.startAllSync()` on `WorkManager`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
