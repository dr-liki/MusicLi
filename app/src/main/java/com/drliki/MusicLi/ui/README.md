# UI (`ui`)

## Overview
Top-level Jetpack Compose and Jetpack Glance UI package (`com.drliki.MusicLi.ui`) grouping MusicLi's Material 3 design system and home-screen app widgets.

## Subdirectories & Boundaries
- **Glance Widgets**: [`./glancewidget/README.md`](./glancewidget/README.md) — Responsive Jetpack Glance home-screen player widgets (`PixelPlayerGlanceWidget`, `BarWidget4x1`, `ControlWidget4x2`, `GridWidget2x2`), `GlanceAppWidgetReceiver` entry points, shared Glance UI components, JSON DataStore state (`PlayerInfoStateDefinition`), local artwork decoding/caching, playback action routing (`PlayerControlActionCallback`), and broadcast updates (`WidgetUpdateReceiver`).
- **Theme**: [`./theme/README.md`](./theme/README.md) — Material 3 color tokens, album-art seed color extraction and dynamic scheme generation (`ColorRoles`), deterministic genre color schemes (`GenreColors`), smooth and expressive shapes (`ShapeCache`), bundled vs. system typography (`Type`), and the root `PixelPlayerTheme` composable with AMOLED overrides and status/navigation bar styling.
