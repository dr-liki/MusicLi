# Settings Search

## Overview
Implements searchable settings metadata, fuzzy query scoring, search results UI, and one-shot scroll-and-pulse row highlighting under `presentation.settings.search`. `SettingsRegistry` catalogs searchable `SettingSpec` items, `SettingsFuzzySearchEngine` normalizes and ranks matches into Top Matches and Related sections, `SettingsSearchComponents` renders category-grouped switches/cards, and `SettingHighlightModifier` scrolls and highlights navigated rows once per destination entry.

## Interface
- `[SettingSpec.kt:SettingType]`, `[SettingSpec.kt:SettingSpec]`, `[SettingSpec.kt:SearchResultItem]`, `[SettingSpec.kt:SearchResultSection]`: Models defining `SettingType` (`SWITCH`, `NAVIGABLE_CARD`, `ACTION`), `SettingSpec` (`getTitle(Context)`, `getSubtitle(Context)`, `createNavigationRoute()`), and ranked result containers.
- `[SettingsRegistry.kt:SettingsRegistry]`: Singleton exposing `val allSettings: List<SettingSpec>` (`by lazy`) across `SettingsCategory` entries (`LIBRARY`, `PLAYBACK`, `APPEARANCE`, `BEHAVIOR`, `BACKUP_RESTORE`, `DEVELOPER`, `EQUALIZER`, `DEVICE_CAPABILITIES`, `ABOUT`).
- `[SettingsFuzzySearchEngine.kt:SettingsFuzzySearchEngine]`: Singleton exposing `fun search(context: Context, query: String, registry: List<SettingSpec>): List<SearchResultSection>`.
- `[SettingsSearchComponents.kt:SettingsSearchResultsContent]`, `[SettingsSearchComponents.kt:SearchResultSwitchItem]`, `[SettingsSearchComponents.kt:SearchResultNavigableItem]`: Compose search results list and segmented row items.
- `[SettingHighlightModifier.kt:SettingsHighlightTarget]`, `[SettingHighlightModifier.kt:LocalSettingsHighlightTarget]`, `[SettingHighlightModifier.kt:ProvideSettingsHighlight]`, `[SettingHighlightModifier.kt:DefaultSettingHighlightShape]`, `[SettingHighlightModifier.kt:settingHighlight]`: CompositionLocal target provider and `Modifier.settingHighlight(...)` overloads.

## Invariants
- `[SettingHighlightModifier.kt:SettingsHighlightTarget.consume]`, `[SettingHighlightModifier.kt:ProvideSettingsHighlight]`, `[SettingHighlightModifier.kt:settingHighlight]`: `consume()` guards with `if (isConsumed) return` so `consumed` (`rememberSaveable(highlightKey)`) fires at most once across recompositions/rotation; `settingHighlight` short-circuits when `target == null || highlightKey != itemKey || target.itemKey != itemKey || target.isConsumed`, catches `CancellationException` on `bringIntoView` while calling `currentCoroutineContext().ensureActive()`, and skips `drawOutline` unless `alpha > 0f && !size.isEmpty()`.
- `[SettingSpec.kt:SettingSpec]`: Title/subtitle precedence is `titleStatic ?: titleRes?.let { context.getString(it) } ?: ""` and `subtitleStatic ?: subtitleRes?.let { context.getString(it) }`; `createNavigationRoute()` appends `highlightKey=$itemKey` using `&` if `?` is present else `?`.
- `[SettingsFuzzySearchEngine.kt:SettingsFuzzySearchEngine.search]`: Returns `emptyList()` for blank trimmed queries; excludes items with `score < 0.35f`; sorts descending and partitions into Top Matches (`score >= 0.70f`) and Related (`score in 0.35f..<0.70f`); normalizes with `Locale.ROOT` and `Normalizer.Form.NFD` (stripping diacritics); counts Levenshtein similarity only when `sim >= 0.7f`.
- `[SettingsSearchComponents.kt:SettingsSearchResultsContent]`: Deduplicates items across sections by `spec.itemKey` before grouping by `spec.category`; defaults switch state to `false` when `spec.getValue` is `null`; falls back to `navController.navigateSafely(spec.createNavigationRoute())` for `ACTION` items when `spec.onAction` is `null`.

## Side Effects
- `[SettingHighlightModifier.kt:ProvideSettingsHighlight]`, `[SettingHighlightModifier.kt:settingHighlight]`: Provides `LocalSettingsHighlightTarget`, launches `LaunchedEffect(target)` (`bringIntoView` with `200.dp`/`140.dp` padding, `target.consume()`, 4-step `Animatable` alpha pulse, `onHighlightFinished`), and draws an outline overlay.
- `[SettingSpec.kt:SettingSpec]`, `[SettingsFuzzySearchEngine.kt:SettingsFuzzySearchEngine.search]`: Read localized Android string resources via `Context.getString(...)`.
- `[SettingsRegistry.kt:SettingsRegistry]`, `[SettingsSearchComponents.kt:SettingsSearchResultsContent]`: Switch toggles invoke `SettingsViewModel` preference setters; card/action clicks invoke `NavController.navigateSafely(...)` (including `Screen.Stats.route`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
