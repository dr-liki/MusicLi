# Release Checklist

MusicLi releases are shipped from `main` via GitHub Releases (`https://github.com/dr-liki/MusicLi/releases`) after passing local checks and a device smoke test.

## Versioning

Version values live in `gradle.properties`:

```properties
APP_VERSION_NAME=1.0.0
APP_VERSION_CODE=1
```

For every public release:

1. Update `APP_VERSION_NAME`.
2. Increment `APP_VERSION_CODE`.
3. Move the relevant `CHANGELOG.md` entries from `Unreleased` to the release version.
4. Tag the release as `v<APP_VERSION_NAME>`, for example `v1.0.0`, and push the tag to trigger `.github/workflows/release.yml`.

## Required Local Checks

Run these before tagging:

```sh
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false -Ppixelplayer.disableReleaseSigning=true
```

For split APK artifacts, use:

```sh
./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=true
```

## Signing

Release signing is driven by an untracked `keystore.properties` file at the repository root (or `CI_KEYSTORE_B64` / `CI_KEYSTORE_PASSWORD` secrets in GitHub Actions).

Supported keys:

```properties
storeFile=/absolute/or/repo-relative/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

If signing properties or the keystore file are missing, release builds are unsigned.

## Device Smoke Test

Install the release candidate and verify:

1. First launch and setup complete.
2. Local library scan finds music.
3. Playback starts, pauses, resumes, skips, and survives backgrounding.
4. Full player, synced cover video, and lyrics sheets open and close smoothly.
5. Widget controls reach the playback service.
6. Navidrome and Jellyfin login screens open.
7. `.pxpl` backup export/restore flow works (including PixelPlayerOSS backup compatibility).

## In-App Updates

The Updates screen (`data/update/`) reads GitHub Releases (`https://api.github.com/repos/dr-liki/MusicLi/releases`):

- **Stable:** Non-prerelease GitHub Releases tagged `v<X.Y.Z>` with assets named `MusicLi-<X.Y.Z>-<abi>.apk` (or universal `MusicLi-<X.Y.Z>.apk`), as produced by `.github/workflows/release.yml`.
- **Alpha:** GitHub prereleases tagged `v<X.Y.Z>-alpha.<N>` with assets named `MusicLi-<X.Y.Z>-alpha.<N>-<abi>.apk`, as produced by `.github/workflows/alpha-release.yml`.

## Retracing Crash Reports

Release builds are minified with R8, so stack traces from the in-app crash reporter are obfuscated. Each release/alpha carries `MusicLi-<version>-mapping.txt.gz`, and nightly runs keep `MusicLi-phone-nightly-<short-sha>-mapping` for 30 days.

```sh
gunzip MusicLi-<version>-mapping.txt.gz
$ANDROID_HOME/cmdline-tools/latest/bin/retrace MusicLi-<version>-mapping.txt trace.txt
```
