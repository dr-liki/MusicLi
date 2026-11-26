# Jellyfin Network Client & Parser

## Overview
OkHttp-based REST client (`JellyfinApiService`) and `org.json` response parser (`JellyfinResponseParser`) for authenticating with Jellyfin servers, browsing views/albums/artists/playlists/tracks, toggling favorites, fetching lyrics, and building universal stream and image URLs.

## Interface
- `[JellyfinApiService.kt:JellyfinApiService]` - `@Singleton` client wrapping `OkHttpClient` with methods `setCredentials`, `clearCredentials`, `hasCredentials`, `getServerUrl`, `getAuthorizationHeader`, `authenticateByName`, `getFavoriteAudioIds`, `markFavorite`, `unmarkFavorite`, `ping`, `getUserViews`, `getMusicItems`, `getAlbums`, `getAlbumItems`, `getArtists`, `getPlaylists`, `getPlaylistItems`, `searchSongs`, `getStreamUrl`, `getImageUrl`, and `getLyrics`.
- `[JellyfinResponseParser.kt:JellyfinResponseParser]` - Object parsing `JSONObject` payloads into domain models via `parseSong`, `parseSongs`, `parseAlbum`, `parseAlbums`, `parseArtist`, `parseArtists`, `parseLibrary`, `parseLibraries`, `parsePlaylist`, `parsePlaylists`, `isAudioPlaylist`, and `isAudioItem`.

## Invariants
- `[JellyfinApiService.kt:JellyfinApiService]` configures derived `OkHttpClient` timeouts to `15s` connect, `30s` read, and `15s` write, and attaches a `MediaBrowser` `Authorization` header (`Client="MusicLi"`, `Device="Android"`, `DeviceId="MusicLi-Android"`, `Version="1.0"`, plus optional `Token`).
- `[JellyfinApiService.kt:JellyfinApiService.getPlaylists]` intentionally omits `MediaTypes=Audio` from the server query and filters client-side with `[JellyfinResponseParser.kt:JellyfinResponseParser.isAudioPlaylist]`, accepting empty, `"Audio"`, or `"Unknown"` `MediaType` values; `[JellyfinApiService.kt:JellyfinApiService.getPlaylistItems]` filters tracks via `[JellyfinResponseParser.kt:JellyfinResponseParser.isAudioItem]`.
- `[JellyfinApiService.kt:JellyfinApiService.getStreamUrl]` targets `/Audio/{itemId}/universal` with supported containers (`mp3,flac,m4a,ogg,wav,aac,opus,webm`) and codecs (`mp3,flac,aac,opus`), appending `MaxStreamingBitrate = maxBitRate * 1000` when `maxBitRate > 0`.
- `[JellyfinResponseParser.kt:JellyfinResponseParser]` converts `RunTimeTicks` to milliseconds via `RunTimeTicks / 10_000` and `MediaSources[0].Bitrate` to kbps via `Bitrate / 1000`.
- `[JellyfinResponseParser.kt:JellyfinResponseParser.parseSongs]` skips individual malformed song objects instead of failing the whole list.

## Side Effects
- Network HTTP requests (`GET`, `POST`, `DELETE`) against the configured Jellyfin server via `OkHttpClient` (`[JellyfinApiService.kt:JellyfinApiService]`).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
