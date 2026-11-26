# Presentation Layer (`presentation`)

## Overview
Intermediate Blueprint grouping MusicLi's Jetpack Compose UI layer (`com.drliki.MusicLi.presentation`), spanning screens, reusable player/library components, ViewModels and state holders, navigation, onboarding, settings search, multi-selection, synced video UI, listening recap, and presentation utilities.

## Subdirectories & Boundaries
- **Components**: [`./components/README.md`](./components/README.md) — Unified expandable player sheet (`UnifiedPlayerSheetV2`), lyrics and queue sheets, tag/artwork editors, wavy sliders, fast scrollbars, and 6 subpackages (`brickbreaker`, `external`, `player`, `scoped`, `snapping`, `subcomps`).
- **Jellyfin**: [`./jellyfin/README.md`](./jellyfin/README.md) — Server authentication (`auth`) and library/sync management dashboard (`dashboard`).
- **Library**: [`./library/README.md`](./library/README.md) — Deterministic folder song sorting (`FolderSongSort`) and scroll-to-top list helpers.
- **Model**: [`./model/README.md`](./model/README.md) — Presentation models and UI helpers (`LibraryTabId`, `ArtistGroup`, `PlaylistUiModel`).
- **Navidrome**: [`./navidrome/README.md`](./navidrome/README.md) — Subsonic/Navidrome login (`auth`) and connected dashboard (`dashboard`).
- **Navigation**: [`./navigation/README.md`](./navigation/README.md) — Compose Navigation graph (`AppNavigation`), typed `Screen` routes, guarded `navigateSafely`, and bottom-tab root ordering.
- **Recap**: [`./recap/README.md`](./recap/README.md) — Listening Recap eligibility scheduling, slide models, animated story slides, and shareable PNG export.
- **Screens**: [`./screens/README.md`](./screens/README.md) — Top-level Compose screens (Home, Library, Search + `./search/README.md`, Explore, Detail screens, Equalizer, Mashup, Setup, Settings, Accounts, Stats, Updates, About/Licenses).
- **Selection**: [`./selection/README.md`](./selection/README.md) — Multi-select state (`DetailSongSelection`), `LazyColumn` drag-to-select gesture modifier with auto-scroll, and ordered selection helpers.
- **Settings**: [`./settings/README.md`](./settings/README.md) — Searchable settings registry, fuzzy search engine, and highlight deep-linking (`./settings/search/README.md`).
- **Splash**: [`./splash/README.md`](./splash/README.md) — Animated `PixelPlayerSplash` handoff from the system splash screen with circular reveal.
- **Stats**: [`./stats/README.md`](./stats/README.md) — Concurrent stats artist-image resolution and `StatsTimeRange` display name mapping.
- **Utils**: [`./utils/README.md`](./utils/README.md) — App-gated haptics, `ScreenAwakeCoordinator`, segmented item shapes, genre icons, and seamless selection scaling.
- **Video**: [`./video/README.md`](./video/README.md) — Synced YouTube cover-video player (`SyncedCoverVideoPlayer`), fullscreen dialog, quality selector, and candidate picker.
- **ViewModel**: [`./viewmodel/README.md`](./viewmodel/README.md) — Hilt ViewModels, `@Singleton`/`@ViewModelScoped` state holders, playback/theme/library coordinators, and dual-deck extension (`./viewmodel/exts/README.md`).
