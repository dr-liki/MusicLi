# Lyrics Network Clients & Parsers

## Overview
Provides LRCLIB Retrofit bindings, Lyricsfile YAML-to-enhanced-LRC conversion, and Google Translate lyrics translation for the lyrics subsystem, alongside the multi-provider search and scoring package under `./providers/README.md`.

## Interface
- `[LrcLibApiService.kt:LrcLibApiService]` - Retrofit interface defining `getLyrics` (`GET api/get`) and `searchLyrics` (`GET api/search`) against LRCLIB.
- `[LrcLibResponse.kt:LrcLibResponse]` - Gson DTO (`id`, `name`, `artistName`, `albumName`, `duration`, `plainLyrics`, `syncedLyrics`, `lyricsfile`) exposing `hasSyncedContent` and `preferredLyricsContent()`.
- `[LyricsTranslator.kt:LyricsTranslator]` - `@Singleton` translator backed by `OkHttpClient` exposing `translateLyrics`, `resolvedLanguageTag`, `clearCacheForSong`, and `Companion.parseTranslationJson`.
- `[LyricsfileParser.kt:LyricsfileParser]` - Internal converter (`toEnhancedLrc`) parsing LRCLIB Lyricsfile YAML payloads into enhanced-LRC strings using SnakeYAML `SafeConstructor`.
- Subdirectory `providers`: [./providers/README.md](./providers/README.md) - Pluggable multi-provider lyrics search, scoring, HTTP helper, and 9 concrete provider implementations.

## Invariants
- `[LrcLibResponse.kt:LrcLibResponse.preferredLyricsContent]` resolves content in strict priority order: `LyricsfileParser.toEnhancedLrc(lyricsfile)`, then non-blank `syncedLyrics`, then non-blank `plainLyrics`.
- `[LyricsfileParser.kt:LyricsfileParser]` configures SnakeYAML `SafeConstructor` with `codePointLimit = 1_000_000`, `maxAliasesForCollections = 0`, `nestingDepthLimit = 20`, `isAllowDuplicateKeys = false`, `MAX_LINES = 10_000`, and `MAX_WORDS_PER_LINE = 2_000`; `toEnhancedLrc` returns `null` for any `version` other than `"1.0"`.
- `[LyricsTranslator.kt:LyricsTranslator.resolvedLanguageTag]` falls back to `Locale.getDefault().language` (defaulting to `"en"` if blank) when `targetSetting` is blank or `"auto"`.
- `[LyricsTranslator.kt:LyricsTranslator.translateLineBatch]` chunks non-blank lines into batches of at most `18` lines / `1200` characters and falls back to per-line translation requests if the returned line count does not match `batchIndices.size`; `[LyricsTranslator.kt:LyricsTranslator.translateLyrics]` fails with `IllegalStateException` when both `synced` and `plain` are null/empty and retains existing translations when the returned string is blank or matches the source line case-insensitively.

## Side Effects
- Executes HTTP `GET` requests to LRCLIB (`api/get`, `api/search` via `[LrcLibApiService.kt:LrcLibApiService]`) and `https://translate.googleapis.com/translate_a/single` on `Dispatchers.IO` (`[LyricsTranslator.kt:LyricsTranslator.requestTranslation]`).
- Mutates an in-memory `ConcurrentHashMap` translation cache keyed by `"$songId|$targetLang"` (`[LyricsTranslator.kt:LyricsTranslator]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
