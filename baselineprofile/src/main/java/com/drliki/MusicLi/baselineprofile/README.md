# Baseline Profile & Benchmarks (`baselineprofile`)

## Overview
AndroidX Macrobenchmark and Baseline Profile instrumentation suite (`com.drliki.MusicLi.baselineprofile`) that generates ART startup/baseline profiles and measures cold startup latency and `UnifiedPlayerSheetV2` gesture frame timing against `com.drliki.MusicLi`.

## Interface
- [`BenchmarkEnvironment`](BenchmarkEnvironment.kt): Internal constants `[BenchmarkEnvironment.kt:TARGET_APP_ID]`, `[BenchmarkEnvironment.kt:BENCHMARK_EXTRA]` and helpers `[BenchmarkEnvironment.kt:benchmarkTargetPackageName]`, `[BenchmarkEnvironment.kt:setupBenchmarkPermissions]`, `[BenchmarkEnvironment.kt:executeBenchmarkShellCommand]`, `[BenchmarkEnvironment.kt:waitForAppForeground]`, `[BenchmarkEnvironment.kt:assertAppForeground]`, and `[BenchmarkEnvironment.kt:waitForTargetPackageVisible]`.
- [`BaselineProfileGenerator`](BaselineProfileGenerator.kt): `[BaselineProfileGenerator.kt:BaselineProfileGenerator]` (`@RunWith(AndroidJUnit4::class) @LargeTest`) with `[BaselineProfileGenerator.kt:BaselineProfileGenerator.rule]` (`BaselineProfileRule`), `[BaselineProfileGenerator.kt:BaselineProfileGenerator.generateStartupProfile]`, and `[BaselineProfileGenerator.kt:BaselineProfileGenerator.generateBaselineProfile]`.
- [`PlayerSheetAnimationBenchmarks`](PlayerSheetAnimationBenchmarks.kt): `[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks]` (`@RunWith(AndroidJUnit4::class) @LargeTest`) with `[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks.benchmarkRule]` (`MacrobenchmarkRule`) and `[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks.playerSheetOpenCloseGestures]`.
- [`StartupBenchmarks`](StartupBenchmarks.kt): `[StartupBenchmarks.kt:StartupBenchmarks]` (`@RunWith(AndroidJUnit4::class) @LargeTest`) with `[StartupBenchmarks.kt:StartupBenchmarks.benchmarkRule]` (`MacrobenchmarkRule`), `[StartupBenchmarks.kt:StartupBenchmarks.startupCompilationNone]`, and `[StartupBenchmarks.kt:StartupBenchmarks.startupCompilationBaselineProfiles]`.

## Invariants
- `[BenchmarkEnvironment.kt:setupBenchmarkPermissions]` throws `IllegalStateException` if any SDK-applicable runtime permission (`READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`, `READ_EXTERNAL_STORAGE`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`) is not `granted=true` in `dumpsys package` after `pm grant`.
- `[BenchmarkEnvironment.kt:assertAppForeground]` throws `IllegalStateException` if `device.hasObject(By.pkg(packageName))` is `false`; `[BenchmarkEnvironment.kt:waitForTargetPackageVisible]` throws if the target package UI hierarchy does not appear within `timeoutMs` (default `15_000L`).
- `[BaselineProfileGenerator.kt:BaselineProfileGenerator.runStep]` verifies target app foreground before (`requireAppBefore = true`) and after every step; `[BaselineProfileGenerator.kt:BaselineProfileGenerator.click]` and `[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks.click]` throw `IllegalStateException` if a `UiObject2` belongs to another non-blank `applicationPackage`.
- `[BaselineProfileGenerator.kt:BaselineProfileGenerator.generateStartupProfile]` and `[BaselineProfileGenerator.kt:BaselineProfileGenerator.generateBaselineProfile]` run `BaselineProfileRule.collect` with `maxIterations = 5`, `stableIterations = 3`, and `BENCHMARK_EXTRA = true`, calling `pressHome()` in `finally`.
- `[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks.playerSheetOpenCloseGestures]` runs 6 `StartupMode.WARM` iterations under `CompilationMode.Partial(BaselineProfileMode.UseIfAvailable)` measuring `FrameTimingMetric`, requires at least one MediaStore audio row (`[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks.assertDeviceMediaStoreHasAudio]`), polls up to 75 times for library content (`[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks.waitForLibraryContent]`), and asserts `SheetState.EXPANDED` / `SheetState.COLLAPSED` transitions within `SHEET_STATE_WAIT_MS` (`3_000L`) while keeping the target app foregrounded.
- `[StartupBenchmarks.kt:StartupBenchmarks.startup]` measures `StartupTimingMetric` across 7 `StartupMode.COLD` iterations for both `CompilationMode.None` and `CompilationMode.Partial` with `BENCHMARK_EXTRA = true`.

## Side Effects
- `[BenchmarkEnvironment.kt:setupBenchmarkPermissions]` and `[BenchmarkEnvironment.kt:executeBenchmarkShellCommand]` execute device shell commands (`pm grant`, `dumpsys package`, `dumpsys window`, and arbitrary commands) via `device.executeShellCommand`.
- `[BaselineProfileGenerator.kt:BaselineProfileGenerator]` kills/launches the target app, clicks system permission dialogs, triggers library rescan, injects search text via shell `input text`, starts audio playback, writes ART baseline/startup profiles, and logs via `android.util.Log`.
- `[PlayerSheetAnimationBenchmarks.kt:PlayerSheetAnimationBenchmarks]` queries MediaStore via `content query`, triggers a database rebuild on the first iteration (mutating static `libraryRebuiltForThisRun`), starts playback, and injects raw touch events via shell `input motionevent DOWN/MOVE/CANCEL`.
- `[StartupBenchmarks.kt:StartupBenchmarks]` grants permissions, presses Home, and cold-starts the target activity across 7 iterations per compilation mode.

## Verification
- Run: `./gradlew :baselineprofile:generateBaselineProfile`
