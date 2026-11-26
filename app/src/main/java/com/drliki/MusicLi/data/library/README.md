# library

## Overview
Pure library-management algorithms operating on in-memory `Song` collections.

## Interface
- `[DuplicateFinder.kt:DuplicateFinder]`: Singleton exposing `DEFAULT_DURATION_TOLERANCE_MS = 3_000L`, `DuplicateGroup(title, artist, songs)`, and `findDuplicates(songs, durationToleranceMs)`.

## Invariants
- `[DuplicateFinder.kt:findDuplicates]`: Groups tracks by trimmed, lowercased, whitespace-collapsed `(title, artist)`, then partitions each group sorted by `duration` into clusters where `song.duration - cluster.first().duration <= durationToleranceMs`. Only clusters with `size > 1` are returned, sorted by descending `songs.size` then case-insensitive `title`.

## Side Effects
- `["None"]`

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
