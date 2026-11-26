# Multi-Provider Lyrics Search & Scoring

## Overview
Implements the pluggable remote lyrics provider ecosystem (`LyricsProvider`), match scoring/filtering (`LyricsMatchScorer`), bounded HTTP helper (`ProviderHttpClient`), nine concrete providers (`LRCLIB`, `BetterLyrics`, `lrc.red`, `Paxsenix`, `KuGou`, `JioSaavn`, `YouTube`, `NetEase`, `Lyrics.ovh`), and `MultiProviderLyricsOrchestrator` supporting both concurrent `AUTO_FASTEST` racing and strict `PRIORITY_ORDER` waterfalls (including rate-limit-aware batch lookups).

## Interface
- `[LyricsProviderModels.kt:LyricsProviderId]` - Enum of the 9 providers (`LRCLIB`, `BETTER_LYRICS`, `LRC_RED`, `PAXSENIX`, `KUGOU`, `JIOSAAVN`, `YOUTUBE`, `NETEASE`, `LYRICS_OVH`) with `DEFAULT_ORDER`, `DEFAULT_ENABLED`, `fromNameOrNull`, `parseOrder`, and `parseEnabled`.
- `[LyricsProviderModels.kt:LyricsSearchStrategy]` - Enum (`AUTO_FASTEST`, `PRIORITY_ORDER`) with `fromStorage`.
- `[LyricsProviderModels.kt:LyricsProviderCandidate]` / `[LyricsProviderModels.kt:LyricsProvider]` - Provider search contract and candidate DTO (`compositeId = "${providerId.name}:$externalId"`).
- `[MultiProviderLyricsOrchestrator.kt:BatchLyricsLookupResult]` - Sealed result (`Matched`, `NotFound`, `RateLimited`, `NetworkError`) for batch lookups.
- `[MultiProviderLyricsOrchestrator.kt:MultiProviderLyricsOrchestrator]` - `@Singleton` orchestrator exposing `fetchBestMatch`, `searchCandidates`, `searchByQuery`, `fetchSyncedByPriorityForBatch`, and `lastKnownLatencyMs`.
- `[LyricsMatchScorer.kt:LyricsMatchScorer]` - Internal scorer/ranker exposing `rankCandidates`, `scoreCandidate`, `cleanTitleSmart`, and `normalizeForMatch` (with `LyricsMatchMode`, `ScoredLyricsCandidate`).
- `[ProviderHttpClient.kt:ProviderHttpClient]` / `[ProviderHttpClient.kt:ProviderRateLimitException]` - Internal HTTP GET helper (`getText`, `getJson`, `parseRetryAfterMs`, `APP_USER_AGENT`) with `4_500L` ms default `callTimeout`.
- Concrete `@Singleton` providers: `[LrcLibLyricsProvider.kt:LrcLibLyricsProvider]`, `[BetterLyricsProvider.kt:BetterLyricsProvider]`, `[LrcRedProvider.kt:LrcRedProvider]`, `[PaxsenixLyricsProvider.kt:PaxsenixLyricsProvider]`, `[KuGouLyricsProvider.kt:KuGouLyricsProvider]`, `[JioSaavnLyricsProvider.kt:JioSaavnLyricsProvider]`, `[YouTubeLyricsProvider.kt:YouTubeLyricsProvider]`, `[NetEaseLyricsProvider.kt:NetEaseLyricsProvider]`, `[LyricsOvhProvider.kt:LyricsOvhProvider]`.

## Invariants
- `[MultiProviderLyricsOrchestrator.kt:MultiProviderLyricsOrchestrator]` applies `DEFAULT_PROVIDER_TIMEOUT_MS` (`4_500L` ms) per provider call; in `AUTO_FASTEST`, if a plain result arrives while synced-capable providers are still pending and `preferSynced` is `true`, it waits up to `DEFAULT_SYNCED_GRACE_WINDOW_MS` (`1_200L` ms) before returning the best plain match.
- `[MultiProviderLyricsOrchestrator.kt:MultiProviderLyricsOrchestrator.fetchSyncedByPriorityForBatch]` always walks enabled providers strictly in priority order (never `AUTO_FASTEST`) and immediately returns `BatchLyricsLookupResult.RateLimited` on `[ProviderHttpClient.kt:ProviderRateLimitException]` (HTTP `429` or `503` with `Retry-After`) instead of falling through.
- `[MultiProviderLyricsOrchestrator.kt:MultiProviderLyricsOrchestrator.toSearchResult]` converts TTML payloads (`rawLyrics.trimStart().startsWith("<")`) to enhanced LRC via `TtmlLyricsParser.parseToEnhancedLrc`.
- `[LyricsMatchScorer.kt:LyricsMatchScorer.scoreCandidate]` rejects blank lyrics, incompatible remix/live/acoustic/etc. timing variant descriptors (`variantDescriptorsCompatible`), candidates exceeding mode-specific duration tolerances (`AUTOMATIC`: `2%` clamped `5..8s` for synced or `4%` clamped `8..15s` for plain; `CANDIDATE`: `18s`), and mismatched artist names (except `YOUTUBE` in `CANDIDATE` mode with `titleScore >= 60`); `[LyricsMatchScorer.kt:LyricsMatchScorer.rankCandidates]` sorts by `hasWordSync`, `hasSyncedContent`, `score`, `providerOrder`, and smallest duration delta.
- `[YouTubeLyricsProvider.kt:YouTubeLyricsProvider.Companion.parseCaptionsToLrc]` returns `null` when fewer than `3` deduplicated non-noise caption cues remain (unless TTML `<tt`).
- `[PaxsenixLyricsProvider.kt:PaxsenixLyricsProvider]` searches `lyrics.paxsenix.org/apple-music/search` first and falls back to `itunes.apple.com/search`.

## Side Effects
- Network HTTP requests to external lyrics endpoints: `lrclib.net` (`LrcLibApiService`), `lyrics-api.boidu.dev` (`BetterLyricsProvider`), `lrc.red` (`LrcRedProvider`), `lyrics.paxsenix.org` & `itunes.apple.com` (`PaxsenixLyricsProvider`), `krcs.kugou.com` & `lyrics.kugou.com` (`KuGouLyricsProvider`), configured JioSaavn/Explore API (`JioSaavnLyricsProvider` + `ExploreRepository`), YouTube captions (`YouTubeStreamExtractor` + `OkHttpClient`), `music.163.com` (`NetEaseLyricsProvider`), and `api.lyrics.ovh` (`LyricsOvhProvider`).
- Updates in-memory EWMA latency metrics (`ewmaLatencyMs`) per `LyricsProviderId` in `[MultiProviderLyricsOrchestrator.kt:MultiProviderLyricsOrchestrator.recordLatency]`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
