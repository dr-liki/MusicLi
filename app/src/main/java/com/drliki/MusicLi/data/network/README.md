# Network

## Overview
Groups all remote API clients, response parsers, and multi-provider search orchestrators under `com.drliki.MusicLi.data.network`, covering Deezer artist metadata, Jellyfin media servers, LRCLIB/multi-provider lyrics and translation, and Navidrome/Subsonic servers.

## Subdirectories & Boundaries
- `deezer`: [./deezer/README.md](./deezer/README.md) - Retrofit client (`DeezerApiService`) and Gson models (`DeezerSearchResponse`, `DeezerArtist`) for Deezer artist artwork search.
- `jellyfin`: [./jellyfin/README.md](./jellyfin/README.md) - OkHttp REST client (`JellyfinApiService`) and `org.json` parser (`JellyfinResponseParser`) for Jellyfin authentication, library browsing, lyrics, and stream/image URLs.
- `lyrics`: [./lyrics/README.md](./lyrics/README.md) - LRCLIB API client (`LrcLibApiService`), `LyricsfileParser`, `LyricsTranslator`, and the pluggable multi-provider lyrics subsystem (`./lyrics/providers/README.md`).
- `navidrome`: [./navidrome/README.md](./navidrome/README.md) - OkHttp Subsonic/OpenSubsonic client (`NavidromeApiService`) and parser (`NavidromeResponseParser`) for Navidrome-compatible servers.
