# Update

## Overview
Implements in-app release discovery, APK download, signature/version verification, `PackageInstaller` installation, background periodic checks, and update notifications under `com.drliki.MusicLi.data.update` for GitHub Releases (`STABLE`) and GitHub prerelease (`ALPHA`) channels from `https://github.com/dr-liki/MusicLi`.

## Interface
- `[AppUpdateManager.kt:AppUpdateManager]` — `@Singleton` coordinator exposing `installedVersionName`, `installedChannel`, `selectedChannel`, `autoCheckEnabled`, `status` (`StateFlow<UpdateStatus>`), `setChannel`, `setAutoCheck`, `checkNow`, `findUpdate`, `downloadAndInstall`, `cancel`, `resumeInstall`, `canRequestPackageInstalls`, `dismiss`, and internal `onInstallStatus`.
- `[AppUpdateManager.kt:UpdateStatus]`, `[AppUpdateManager.kt:UpdateError]` — Sealed status state machine (`Idle`, `Checking`, `UpToDate`, `Available`, `Downloading`, `AwaitingInstallPermission`, `AwaitingConfirmation`, `Installing`, `Failed`) and error enum (`CHECK_FAILED`, `NOTHING_PUBLISHED`, `DOWNLOAD_FAILED`, `INVALID_APK`, `NOT_NEWER`, `INSTALL_FAILED`).
- `[AppReleaseSource.kt:AppReleaseSource]` — `@Singleton` release fetcher (`latest`, internal `PUBLISHED_PACKAGE`, `parseLatestStable`, `parseLatestAlpha`).
- `[UpdateChannel.kt:UpdateChannel]`, `[UpdateChannel.kt:StableVersion]`, `[UpdateChannel.kt:AlphaVersion]`, `[UpdateChannel.kt:AppRelease]`, `[UpdateChannel.kt:isNewerThan]` — Release channel enum (`STABLE`, `ALPHA`), stable and alpha semantic version comparators, release metadata model, and internal version comparison helper.
- `[UpdateCheckWorker.kt:UpdateCheckWorker]`, `[UpdateCheckWorker.kt:UpdateCheckScheduler]` — Hilt `CoroutineWorker` for daily periodic update checks and `@Singleton` scheduler (`start`).
- `[UpdateDownloadService.kt:UpdateDownloadService]` — Foreground `Service` (`Companion.start`, `@EntryPoint UpdateDownloadServiceEntryPoint`) keeping APK downloads alive with progress notifications.
- `[UpdateInstallReceiver.kt:UpdateInstallReceiver]` — `BroadcastReceiver` (`ACTION_INSTALL_STATUS`, `@EntryPoint UpdateInstallReceiverEntryPoint`) handling `PackageInstaller` status callbacks.
- `[UpdateNotifications.kt:UpdateNotifications]` — Internal notification builder/poster (`DOWNLOAD_ID`, `showUpdateAvailable`, `downloadProgress`, `updateDownloadProgress`, `showInstallConfirmation`, `dismissInstallConfirmation`).

## Invariants
- `[AppReleaseSource.kt:AppReleaseSource.Companion.parseLatestStable]` filters to `!prerelease && !draft` and validates `StableVersion.parse(tag_name)`; `[AppReleaseSource.kt:AppReleaseSource.Companion.parseLatestAlpha]` filters to `prerelease && !draft` and validates `AlphaVersion.parse(tag_name)`. Both select the first `.apk` asset matching `supportedAbis` in preference order (`-$abi.apk`) or fall back to a universal `.apk`.
- `[UpdateChannel.kt:UpdateChannel.Companion.ofVersionName]` classifies version names containing `'-'` as `ALPHA` and others as `STABLE`; `[UpdateChannel.kt:isNewerThan]` compares `StableVersion` semantically for `STABLE` (with `versionCode` fallback) and `AlphaVersion` for `ALPHA`.
- `[AppUpdateManager.kt:AppUpdateManager.findUpdate]` sets `requiresReinstall = true` when `context.packageName != AppReleaseSource.PUBLISHED_PACKAGE` (e.g., debug suffix builds).
- `[AppUpdateManager.kt:AppUpdateManager.downloadAndInstall]` requires `status` to be `Available` with `!requiresReinstall`, awaits prior `apkCleanup`/`download` jobs before writing so cleanup never races a retry, verifies downloaded APKs for matching `packageName`, `longVersionCode > installed`, and compatible signing certificate/rotation lineage, and checks `canRequestPackageInstalls()` before creating a `PackageInstaller` session.
- `[AppUpdateManager.kt:AppUpdateManager.onInstallStatus]` accepts `STATUS_PENDING_USER_ACTION` only when status is `Installing` or `AwaitingConfirmation` with non-null `confirmIntent`.
- `[UpdateCheckWorker.kt:UpdateCheckScheduler.start]` uses `AtomicBoolean.compareAndSet(false, true)` for one-time collector registration; `[UpdateCheckWorker.kt:UpdateCheckWorker.doWork]` skips notifying when `requiresReinstall` is true or `versionName` was already notified, and updates `lastNotifiedUpdateVersion` only when `showUpdateAvailable` succeeds.
- `[UpdateDownloadService.kt:UpdateDownloadService.onStartCommand]` promotes to foreground (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`) before checking `Downloading` state; `[UpdateInstallReceiver.kt:UpdateInstallReceiver.onReceive]` launches `confirmIntent` directly only when `ProcessLifecycleOwner` is at least `STARTED`, otherwise posting a confirmation notification; `[UpdateNotifications.kt:UpdateNotifications]` checks `POST_NOTIFICATIONS` and channel importance before posting.

## Side Effects
- `[AppReleaseSource.kt:AppReleaseSource.latest]` executes HTTPS GET requests via `OkHttpClient` on `Dispatchers.IO` to the GitHub Releases API (`api.github.com/repos/dr-liki/MusicLi/releases`).
- `[AppUpdateManager.kt:AppUpdateManager]` reads/writes channel, auto-check, and last-notified preferences in `UserPreferencesRepository`, deletes/writes APK files under `cacheDir/updates`, starts `UpdateDownloadService`, commits/abandons `PackageInstaller` sessions with `PendingIntent` targeting `UpdateInstallReceiver`, and mutates `status` `StateFlow`.
- `[UpdateCheckWorker.kt:UpdateCheckScheduler]` schedules (`ExistingPeriodicWorkPolicy.KEEP`, 1-day interval, `NetworkType.CONNECTED`) or cancels `"app_update_check"` in `WorkManager`; `[UpdateCheckWorker.kt:UpdateCheckWorker]` updates `UserPreferencesRepository` and posts notifications.
- `[UpdateDownloadService.kt:UpdateDownloadService]`, `[UpdateInstallReceiver.kt:UpdateInstallReceiver]`, and `[UpdateNotifications.kt:UpdateNotifications]` manage foreground service state, notification channel `"app_updates"`, system notifications (`0x5550`, `0x5551`, `0x5552`), and activity launches for install confirmation.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
