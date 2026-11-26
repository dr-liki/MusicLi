# Lyrics Data Subsystem

## Overview
Intermediate package grouping offline/batch lyrics processing workflows under `com.drliki.MusicLi.data.lyrics`. Contains no direct source files at this level; delegates batch lyrics lookup, tag embedding, and checkpoint management to its `bulk` subpackage.

## Subdirectories & Boundaries
- `bulk`: [./bulk/README.md](./bulk/README.md) - Resumable WorkManager-backed pipeline (`BulkLyricsEmbedManager`, `BulkLyricsEmbedUiState`, `BulkLyricsEmbedOptions`) for fetching lyrics from providers and embedding them into local audio files and Room caches.
