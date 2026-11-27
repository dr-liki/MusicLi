<p align="center">
  <img src="assets/MusicLi.svg" alt="MusicLi Icon" height="220"/>
</p>

<h1 align="center">MusicLi</h1>

<p align="center">
  A Local-first beautiful Material 3 Expressive Android music player with self-hosted and HQ song streaming and downloads, home discover carousel and horizontal play queue, in-player music video playback, multi-provider synced lyrics lookup and bulk auto lyrics embed and download support.
</p>

<p align="center">
  <a href="https://github.com/dr-liki/MusicLi/releases/latest">
    <img src="https://img.shields.io/github/v/release/dr-liki/MusicLi?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest release">
  </a>
  <a href="https://github.com/dr-liki/MusicLi/releases">
    <img src="https://img.shields.io/github/downloads/dr-liki/MusicLi/total?logo=github&style=for-the-badge" alt="Total downloads">
  </a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPLv3 license">
</p>

## What It Is

MusicLi (`com.drliki.MusicLi`) is an independent, free and open-source Android music player forked from [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) and maintained by [@dr-liki](https://github.com/dr-liki). Built with Kotlin, Jetpack Compose, Material 3 Expressive, and AndroidX Media3 + FFmpeg, it keeps your local music library first while giving you full control over optional self-hosted servers and online enrichment.

The app works **100% offline by default**. No ads, no analytics, no telemetry, and no Google Play Services runtime dependencies—optional network features stay dormant until you enable or trigger them.

## Features

### Material 3 Expressive UI & Personalization
- **Dynamic Design System**: Adaptive Material 3 Expressive interface with album-art dynamic palettes, AMOLED, light, and dark themes, custom display/interface typography, blur effects, and high-refresh-rate controls.
- **Home & Discovery Surfaces**: Endless circular Home Discover carousel, horizontal Home Play Queue strip, adaptive phone/tablet navigation rail, and Glance home widgets.
- **Consistent Dialogs & In-App Release Info**: Unified edge-to-edge-width popup dialogs (`MusicLiDialog`), interactive **Release & Build Info** sheet under **Settings → About**, and a built-in **Changelog** sheet under **Settings → Updates**.

### Playback & Audio Engine
- **Media3 + FFmpeg Pipeline**: Hardware and Jellyfin FFmpeg decoding for `MP3`, `FLAC`, `AAC`, `OGG`, `WAV`, `M4A`, plus gapless playback, crossfade, custom track transitions, and external audio file opening.
- **Playback Controls & Effects**: Reorderable queue with swipe-to-dismiss undo, sleep timer, variable playback speed (`0.25×`–`3×`), Equalizer with custom/pinned presets and DSP effects, dual-deck DJ Mashup, and timestamped Audio Bookmarks.
- **Android Auto**: Built-in Android Auto media browse tree and playback controls.

### Local Library & Management
- **Structured Browsing & Smart Mixes**: Fast-scrolling Songs, Albums, Artists, Genres, Folders, Favorites, Recently Played, Daily Mix, and offline Smart Mix generation.
- **Metadata & Maintenance**: Single and batch ID3/Vorbis/MP4 tag + crop-ready cover-art editor, duplicate song scanner, delimiter configuration, directory include/exclude manager, Storage Sense cache analyzer, listening statistics, and annual Recap.

### Lyrics, Translation & Romanization
- **Multi-Format & 9-Provider Lookup**: Reads embedded audio tags and local `.lrc` / TTML files, and searches across `LRCLIB`, `BetterLyrics`, `lrc.red`, `Paxsenix`, `KuGou`, `JioSaavn`, `YouTube`, `NetEase`, and `Lyrics.ovh` with pre-filled Title & Artist search controls.
- **Bulk Auto-Embed**: Priority-ordered library-wide synced lyrics fetcher with rate-limit pause/resume and MediaStore permission handling.
- **Translation & Phonetic Romanization**: On-demand live line translation plus offline Urdu and 7-script Indic phonetic romanization alongside synced word/line highlighting.

### Explore, Synced Video & Self-Hosted Cloud
- **Explore Catalog & Offline Downloads**: Browse & Discover 2×2 categories (*New Releases*, *Charts*, *Playlists*, *Artists*), pull-to-refresh rotating feeds, section Shuffle All, vertical recent searches with individual removal, streaming playback with direct artist navigation, and persistent offline M4A + `.lrc` downloads.
- **Synced Cover Videos**: Synced YouTube cover-video player with SponsorBlock intro-offset alignment, selectable stream quality, and fullscreen orientation lock.
- **Self-Hosted Servers & Enrichment**: **Navidrome / Subsonic** and **Jellyfin** account login, multi-library sync, streaming, and offline downloads; optional **ListenBrainz** scrobbling, **MusicBrainz** metadata matching, and **Deezer** artist portrait caching.

### Overview Matrix

| Area | Highlights |
| --- | --- |
| Playback | Media3 + Jellyfin FFmpeg decoder, gapless playback, crossfade, custom transitions, equalizer, mashup, audio bookmarks, sleep timer, speed (`0.25×`–`3×`) |
| Library | Local MediaStore scanning (`MP3`, `FLAC`, `AAC`, `OGG`, `WAV`, `M4A`), smart mixes, tag/cover-art editor, duplicate scanner, Storage Sense, listening stats |
| Self-Hosted | Navidrome/Subsonic and Jellyfin multi-library sync, streaming, resilient cover loading, and app-private offline downloads |
| Lyrics | Embedded tags, `.lrc`/TTML sidecars, 9-provider lookup, priority-ordered bulk auto-embed, live translation, Urdu & 7-script Indic romanization |
| Explore & Video | Browse & Discover categories, JioSaavn streaming & offline M4A + LRC downloads, synced YouTube cover videos with SponsorBlock offsets |
| UI & Platform | Album-art dynamic palette, circular Home carousel, unified `MusicLiDialog` popups, AMOLED theme, Glance widgets, Android Auto, GitHub Releases updater |
| Backup & Sync | Encrypted (`AES-256-GCM`) and plain `.pxpl` backup/restore plus `.m3u8` folder sync (compatible with PixelPlayerOSS archives) |

## Migrating from PixelPlayerOSS

Because MusicLi uses its own package name (`com.drliki.MusicLi`) and signing key, Android installs it as a separate app:

1. In PixelPlayerOSS, export a `.pxpl` backup (`Settings → Backup & Restore`).
2. Install **MusicLi** from [GitHub Releases](https://github.com/dr-liki/MusicLi/releases).
3. Restore your `.pxpl` backup in MusicLi (during onboarding or in Settings) — playlists, stats, bookmarks, preferences, and `.m3u8` sync links carry right over.

## Download

Both **Stable** (`v*`) and **Alpha** (`v*-alpha.*`) builds are distributed exclusively on GitHub Releases and checked automatically in-app under **Settings → Updates**:

```text
https://github.com/dr-liki/MusicLi/releases
```

Package ID (for Obtainium / Android):

```text
com.drliki.MusicLi
```

## Online Services & Privacy

MusicLi separates offline playback from optional network lookups:

| Service | Purpose | Default |
| --- | --- | --- |
| Navidrome / Subsonic | Self-hosted library sync, streaming, and offline downloads | User login required |
| Jellyfin | Self-hosted library sync, streaming, and offline downloads | User login required |
| ListenBrainz | Optional scrobbling & now-playing reporting | Off (token required) |
| MusicBrainz | On-demand metadata matching and identifier enrichment | Only when requested |
| Lyrics Providers | 9-provider synced/plain lyrics search & bulk auto-embed | Off / on demand |
| Google Translate | On-demand live translation of lyric lines | Only when requested |
| Deezer | Optional missing artist portrait lookup and local caching | Off |
| Explore (JioSaavn) | Optional Explore discovery, streaming, and offline M4A downloads | Only when opened |
| YouTube / SponsorBlock | Synced cover-video playback and intro-offset matching | Only when enabled |
| GitHub Releases | In-app Stable & Alpha update checks from `dr-liki/MusicLi` | User-controlled |

## Requirements

| Requirement | Version |
| --- | --- |
| Android | 11 or newer (`minSdk = 30`) |
| JDK | 21 |
| Android SDK | `compileSdk = 37` / `targetSdk = 37` |

## Build From Source

```sh
git clone https://github.com/dr-liki/MusicLi.git
cd MusicLi
./gradlew :app:assembleDebug -Ppixelplayer.enableAbiSplits=false
```

Run unit tests:

```sh
./gradlew :app:testDebugUnitTest
```

Build a universal unsigned release APK:

```sh
./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false -Ppixelplayer.disableReleaseSigning=true
```

## System Overview

MusicLi is a multi-module Android Kotlin + Jetpack Compose application (`:app` and `:baselineprofile`) built around Hilt dependency injection, Room SQLite persistence (`PixelPlayerDatabase` v7), AndroidX Media3 playback (`MusicService` + `DualPlayerEngine`), WorkManager background synchronization, and Material 3 Expressive UI.

```text
app/src/main/java/com/drliki/MusicLi/
- data/             Room, repositories, preferences, services, workers
- di/               Hilt modules and qualifiers
- presentation/     Compose screens, components, navigation, ViewModels
- ui/               Theme and Glance widgets
- utils/            Shared utilities

baselineprofile/      Macrobenchmark and baseline profile generation
```

## Subdirectories & System Boundaries

- **app**: [`./app/README.md`](./app/README.md) — Main Android application module (`com.drliki.MusicLi`) housing [`data`](./app/src/main/java/com/drliki/MusicLi/data/README.md), [`di`](./app/src/main/java/com/drliki/MusicLi/di/README.md), [`presentation`](./app/src/main/java/com/drliki/MusicLi/presentation/README.md), [`ui`](./app/src/main/java/com/drliki/MusicLi/ui/README.md), [`utils`](./app/src/main/java/com/drliki/MusicLi/utils/README.md), and [`assets`](./app/src/main/assets/README.md).
- **baselineprofile**: [`./baselineprofile/README.md`](./baselineprofile/README.md) — AndroidX Macrobenchmark and Baseline Profile instrumentation module (`:baselineprofile`) targeting `:app` for ART profile generation and startup/gesture benchmarks.

## Global Invariants

- **Offline-First Network Gating**: Local playback and MediaStore/Room browsing operate offline by default; optional remote services remain gated behind explicit user preferences, actions, or credentials.
- **Single-Source Persistence & Concurrency**: Room (`pixelplayer_database`, WAL mode, schema v7) and DataStore preferences serve as the single source of truth; long-lived `@Singleton` state holders coordinate across ViewModel lifecycles via `OwnerStack` and serialize critical disk/database writes (library sync commits, `.pxpl` backup/restore, M3U folder sync, and crash-safe metadata write-back).
- **Media3 & Audio Pipeline Isolation**: `MusicService` wraps playback through `FadingPlayer -> MappingPlayer -> PreviousButtonPlayer`, restricts privileged custom commands, and isolates external `ACTION_VIEW`/`ACTION_SEND` playback through `ExternalPlayerActivity` and `DualPlayerEngine`.

## Global Side Effects

- **Local Storage & MediaStore**: Reads/writes Android `MediaStore`, SAF document trees, Room SQLite (`pixelplayer_database`), DataStore preferences, audio file metadata/cover art, and app-private cache/files directories (`image_cache`, `explore_stream_cache`, `metadata_recovery`, `artist_art_*.jpg`, `lyrics/*.json`).
- **System Services & Background Execution**: Runs foreground `MusicService` media playback and notifications, `WorkManager` sync/download/scrobble/update workers, `AlarmManager` sleep timer alarms, Glance widget broadcasts, and `ProcessLifecycleOwner` / `MediaStore` observers.
- **External Network Integrations**: When enabled or authenticated by the user, executes OkHttp/Retrofit requests against configured Navidrome/Subsonic, Jellyfin, ListenBrainz, LRCLIB/multi-provider lyrics, Google Translate, Deezer, MusicBrainz, JioSaavn/YouTube/SponsorBlock, and GitHub Releases endpoints.

## System Verification

- Run: `./gradlew :app:testDebugUnitTest`
- Baseline Profile (connected device/emulator): `./gradlew :baselineprofile:generateBaselineProfile`

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose |
| Design | Material 3 Expressive |
| Playback | AndroidX Media3, ExoPlayer, FFmpeg |
| Database | Room |
| Dependency Injection | Hilt |
| Preferences | DataStore |
| Background Work | WorkManager |
| Networking | Retrofit, OkHttp |
| Images | Coil |
| Metadata | TagLib, JAudioTagger |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), [docs/RELEASE.md](docs/RELEASE.md), [PRIVACY.md](PRIVACY.md), and [SECURITY.md](SECURITY.md).

## License

MusicLi is licensed under the [GNU General Public License v3.0](LICENSE) (`SPDX-License-Identifier: GPL-3.0-or-later`).

```text
MusicLi (forked from PixelPlayerOSS)
Copyright (C) 2026 Theo Vilardo
Copyright (C) 2026 MusicLi Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

Distributed APKs include third-party components under their own licenses (including `org.jellyfin.media3:media3-ffmpeg-decoder` under GPL-3.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

<p align="center">
  Maintained by <a href="https://github.com/dr-liki">dr-liki</a>
</p>
