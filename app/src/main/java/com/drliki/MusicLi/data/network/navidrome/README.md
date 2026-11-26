# Navidrome Network Client & Parser

## Overview
OkHttp-based Subsonic/OpenSubsonic REST client (`NavidromeApiService`) and JSON response parser (`NavidromeResponseParser`) for authenticating against Navidrome and compatible Subsonic servers, browsing music folders/artists/albums/playlists/tracks, searching, building stream and cover art URLs, fetching lyrics, scrobbling, and starring items.

## Interface
- `[NavidromeApiService.kt:NavidromeApiService]` - `@Singleton` client wrapping `OkHttpClient` with methods `setCredentials`, `clearCredentials`, `activeAuthMethod`, `onAuthMethodChanged`, `hasCredentials`, `getServerUrl`, `ping`, `getLicense`, `getMusicFolders`, `getArtists`, `getArtist`, `getAlbumList`, `getAlbum`, `getSong`, `getPlaylists`, `getPlaylist`, `search3`, `searchSongs`, `searchAlbums`, `searchArtists`, `getStreamUrl`, `getCoverArtUrl`, `getLyrics`, `getLyricsBySongId`, `reportPlayback`, `scrobble`, `star`, and `unstar`.
- `[NavidromeApiService.kt:SubsonicApiException]` - Exception carrying Subsonic `code` and `serverMessage` when a response reports `status != "ok"`.
- `[NavidromeApiService.kt:shouldFallBackToPasswordAuth]` & `[NavidromeApiService.kt:subsonicAuthParams]` - Internal authentication helpers for token+salt (`t`/`s`) and hex-encoded password (`p=enc:...`) modes.
- `[NavidromeResponseParser.kt:NavidromeResponseParser]` - `object` parsing Subsonic `JSONObject` payloads via `parseMusicFolder`, `parseMusicFolders`, `parseArtist`, `parseArtists`, `parseAlbum`, `parseAlbums`, `parseSong`, `parseSongs`, `parseSongsFromAlbumResponse`, `parsePlaylist`, `parsePlaylists`, `parseSongsFromPlaylistResponse`, and `parseSearchResults`.
- `[NavidromeResponseParser.kt:SearchResults]` - Data class holding parsed `artists`, `albums`, and `songs` lists.

## Invariants
- `[NavidromeApiService.kt:NavidromeApiService]` derives an `OkHttpClient` with `15s` connect, `30s` read, and `15s` write timeouts and targets `/rest/<endpoint>.view` with `v=1.16.1`, `f=json`, and client ID defaulting to `"MusicLi"`.
- `[NavidromeApiService.kt:NavidromeApiService.requestAndParse]` & `[NavidromeApiService.kt:shouldFallBackToPasswordAuth]` switch `authMethod` from `NavidromeAuthMethod.TOKEN` to `NavidromeAuthMethod.PASSWORD` and retry once only when a server rejects token auth with Subsonic error code `41` (`SUBSONIC_ERROR_TOKEN_AUTH_UNSUPPORTED`).
- `[NavidromeApiService.kt:NavidromeApiService.buildApiUrl]`, `[NavidromeApiService.kt:NavidromeApiService.getStreamUrl]`, and `[NavidromeApiService.kt:NavidromeApiService.getCoverArtUrl]` throw `IllegalStateException` when `credentials` is `null`.
- `[NavidromeResponseParser.kt:NavidromeResponseParser]` converts Subsonic `duration` seconds to milliseconds (`* 1000L`), prioritizes OpenSubsonic `genres` arrays joined via `GenreParser.joinValues` over legacy `genre`, and skips malformed elements individually in `parseSongsFromAlbumResponse`, `parseSongsFromPlaylistResponse`, and `parseSearchResults`.

## Side Effects
- Executes HTTP `GET` requests to `/rest/*.view` on the configured Subsonic/Navidrome server via `OkHttpClient` on `Dispatchers.IO` (`[NavidromeApiService.kt:NavidromeApiService.request]`).
- Mutates volatile in-memory `credentials` and `authMethod` state, and invokes `onAuthMethodChanged` when falling back to password authentication (`[NavidromeApiService.kt:NavidromeApiService]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
