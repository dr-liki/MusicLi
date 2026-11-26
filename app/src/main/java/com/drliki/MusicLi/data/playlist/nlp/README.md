# Playlist NLP & Ranking Engine

## Overview
Provides the pure-Kotlin offline natural-language playlist generation, query parsing, metadata heuristics, and diversity-aware ranking pipeline under `com.drliki.MusicLi.data.playlist.nlp`.

## Interface
- `[NlpText.kt:NlpText]` - Accent-folding normalization (`normalize`), tokenization (`tokenize`), and conservative English/Spanish stemming (`stemTokens`, `stem`, `MIN_STEM_LENGTH = 4`).
- `[NlpLexicon.kt:NlpLexicon]` - Pre-normalized stop-words (`stopWords`, `isStopWord`), negation cues (`negationCues`, `isNegationCue`), similarity phrases (`similarityPhrases`), and decade patterns (`SHORT_DECADE`, `FULL_DECADE`, `spelledDecades`, `spelledDecadeStart`).
- `[SmartMixRequest.kt:SmartMixRequest]` - Formats UI smart-mix requests into structured prompt strings (`prompt(query, selectedEnergyBpm, selectedLength)`).
- `[ArtistGenreTable.kt:ArtistGenreTable]` - Asset-backed normalized-artist-to-genre lookup table (`EMPTY`, `parse`, `genreFor`).
- `[ConceptTable.kt:ConceptTable]` / `[ConceptTable.kt:ConceptWeights]` - Stem/word-to-(`GenreFamily`, `MoodProfile`) weight lookup table (`EMPTY`, `parse`, `lookup`, `lookupWord`).
- `[GenreTaxonomy.kt:GenreTaxonomy]` / `[GenreTaxonomy.kt:GenreFamily]` - Canonical genre family enum and taxonomy resolver/matcher (`Entry`, `resolve`, `energyOf`, `familyOf`, `queryGenres`, `matchesGenre`, `containsPhrase`, `maskPhrase`, `queryFamilies`).
- `[MoodProfile.kt:MoodProfile]` / `[MoodProfile.kt:MoodKind]` - Behavioral and audio-target mood profiles (`CHILL`, `WORKOUT`, `SAD`, `HAPPY`, `FAVORITES`, `DISCOVERY`, `RECENT`) with `matches`, `matchStrength`, and `matchesGenre`.
- `[LibraryIndex.kt:LibraryIndex]` / `[LibraryIndex.kt:IndexedSong]` - Cached library inverted-frequency and artist-genre inheritance index (`signatureOf`, `artistKeysOf`, `build`, `idf`, `termWeight`, `withInferredGenre`).
- `[LocalMetadataHeuristics.kt:LocalMetadataHeuristics]` - Rule-based local metadata completion (`completeMetadata`), tag generation (`generateTags`), mood vector analysis (`analyzeMood`), and genre energy lookup (`genreEnergy`).
- `[PlaylistIntentEngine.kt:PlaylistIntentEngine]` / `[PlaylistIntentEngine.kt:SongVibe]` - Query parser and hard-filter/scorer (`ParsedQuery`, `Shortlist`, `parse`, `shortlist`, `generate`).
- `[OfflineMixRanker.kt:OfflineMixRanker]` / `[OfflineMixRanker.kt:MixWeights]` / `[OfflineMixRanker.kt:ContentSimilarityWeights]` / `[OfflineMixRanker.kt:MixContext]` - Banded diversity reranker (`Candidate`, `Factors`, `rank`, `similarity`).
- `[MixSuggestionEngine.kt:MixSuggestionEngine]` / `[MixSuggestionEngine.kt:MixSuggestion]` / `[MixSuggestionEngine.kt:MixMoment]` / `[MixSuggestionEngine.kt:MixSuggestionPlays]` - Daily-rotated Smart Mix pill generator (`ROWS = 3`, `MAX_PER_ROW = 4`, `suggest`, `momentsFor`).

## Invariants
- `[NlpText.kt:NlpText]` drops tokens of length `<= 1` after NFD accent stripping and leaves tokens of length `<= 4` (`MIN_STEM_LENGTH`) or containing digits unstemmed except terminal `-ing`/`-ed` undoubling rules; `[NlpLexicon.kt:NlpLexicon]` entries are pre-normalized via `NlpText.normalize`.
- `[ArtistGenreTable.kt:ArtistGenreTable.parse]` skips blank/`#` lines and rows without `|` at index `> 0`, resolves ties across `GenreFamily` with `TIE_MAX_ENERGY_SPAN = 0.25`, and returns `EMPTY` if `wanted` is non-null and empty; `[ConceptTable.kt:ConceptTable.parse]` scales percent weights by `100.0`, clamps to `[0.0, 1.0]`, and merges duplicate stems via max weight.
- `[GenreTaxonomy.kt:GenreTaxonomy]` resolves `ENTRIES` most-specific-first (`resolve`), evaluates `queryGenres` longest-alias-first consuming matched word positions to prevent double matching, and guards `rock` matching against unrelated subgenres (`matchesGenre`).
- `[LibraryIndex.kt:LibraryIndex.build]` deduplicates by `song.id`, reuses cached `IndexedSong` instances when unchanged, inherits genres from artist peers when share `>= 0.5` (`INHERITANCE_MIN_SHARE`) before falling back to `ArtistGenreTable`, and clamps `termWeight` to `[0.05, 1.0]`.
- `[PlaylistIntentEngine.kt:PlaylistIntentEngine.parse]` prioritizes inline `\b(\d{2,3})\s*bpm\b` over `[Target BPM: ...]` directives (`20f..400f`), protects multi-token local artist spans (`artistTokenIndices`) from negation/strict/decade parsing, disables `conceptTable` when `requiredArtists` is non-empty, and consults `conceptTable` only when curated `GenreTaxonomy.queryFamilies` or `MoodProfile.triggerStems` do not already match.
- `[PlaylistIntentEngine.kt:PlaylistIntentEngine.shortlist]` excludes all seed songs (`context.seedIds`, `similarToArtist`, or similarity-cue title/album matches), enforces `satisfiesRequirements` (artist, genre/strict tags, year ranges, BPM/energy tolerances, `SongVibe` Euclidean distance `<= 0.7`, and `FAVORITES`/`DISCOVERY`/`RECENT` gates), and requires similarity `>= weights.minimumSeedSimilarity` (or playlist co-occurrence) when seeded or `score > 0.0` when selection/energy signals are active.
- `[OfflineMixRanker.kt:MixWeights]` & `[OfflineMixRanker.kt:ContentSimilarityWeights]` validate `poolSize` in `2..5000`, `maxTracks` in `1..200`, `maxSeeds` in `1..64`, `minimumSeedSimilarity` in `0.0..1.0`, positive finite scales, and non-negative weights; `[OfflineMixRanker.kt:OfflineMixRanker.rank]` excludes seed `song.id` and `recordingKey`, quantizes relevance into 10-point bands, never promotes a lower relevance band over a higher one, avoids back-to-back `artistKey` repeats when alternatives exist, caps repeat penalty multipliers at `MAX_REPEAT_COUNT = 3`, and breaks ties by smallest `song.id`.
- `[MixSuggestionEngine.kt:MixSuggestionEngine.suggest]` seeds `Random` deterministically with `YEAR * 1000L + DAY_OF_YEAR`, lowers minimum song thresholds for libraries `< 300` songs, caps total pills at `ROWS * MAX_PER_ROW` (`12`), and balances pills across up to `3` rows differing in length by at most `1`.
- `[LocalMetadataHeuristics.kt:LocalMetadataHeuristics]` preserves non-blank/non-`"music"`/non-`"unknown"` genres, emits `6..10` unique hyphenated tags (`generateTags`), and clamps all `analyzeMood` vector dimensions to `[0.0, 1.0]`.

## Side Effects
- `[OfflineMixRanker.kt:OfflineMixRanker.rank]` invokes the caller-supplied `explain` callback and consumes `random`; `[OfflineMixRanker.kt:MixContext]` reads `System.currentTimeMillis()` as the default `nowMs`.
- `[PlaylistIntentEngine.kt:PlaylistIntentEngine.shortlist]` & `[PlaylistIntentEngine.kt:PlaylistIntentEngine.generate]` consume `random` when selecting a target length between `minLength` and `maxLength`.
- `[MixSuggestionEngine.kt:MixSuggestionEngine.suggest]` invokes caller-supplied `canMix` and `textOf` callbacks and instantiates `Calendar.getInstance()` by default. No disk, network, or database I/O occurs in this package.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
