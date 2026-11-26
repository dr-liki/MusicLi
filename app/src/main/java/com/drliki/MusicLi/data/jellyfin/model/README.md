# Jellyfin Data Models

## Overview
Immutable domain data classes representing Jellyfin server media entities (`JellyfinAlbum`, `JellyfinArtist`, `JellyfinLibrary`, `JellyfinPlaylist`, `JellyfinSong`) and server connection credentials (`JellyfinCredentials`) with URL normalization and transport security validation.

## Interface
- `[JellyfinAlbum.kt:JellyfinAlbum]` / `[JellyfinAlbum.kt:JellyfinAlbum.Companion.empty]` - `@Immutable` `@Parcelize` data class representing an album (`id`, `name`, `artist`, `artistId`, `songCount`, `duration`, `year`, `genre`).
- `[JellyfinArtist.kt:JellyfinArtist]` / `[JellyfinArtist.kt:JellyfinArtist.Companion.empty]` - `@Immutable` `@Parcelize` data class representing an artist (`id`, `name`, `albumCount`).
- `[JellyfinCredentials.kt:JellyfinCredentials]` - Connection credentials (`serverUrl`, `username`, `password`, `accessToken`, `userId`) with `[JellyfinCredentials.kt:JellyfinCredentials.Companion.empty]`, `[JellyfinCredentials.kt:JellyfinCredentials.isValid]`, `[JellyfinCredentials.kt:JellyfinCredentials.hasToken]`, `[JellyfinCredentials.kt:JellyfinCredentials.normalizedHttpUrlOrNull]`, `[JellyfinCredentials.kt:JellyfinCredentials.normalizedServerUrl]`, `[JellyfinCredentials.kt:JellyfinCredentials.requiresLocalNetworkAccess]`, and `[JellyfinCredentials.kt:JellyfinCredentials.connectionValidationError]`.
- `[JellyfinLibrary.kt:JellyfinLibrary]` - `@Immutable` `@Parcelize` library view model (`id`, `name`, `collectionType`), `[JellyfinLibrary.kt:JellyfinLibrary.isMusic]`, and `[JellyfinLibrary.kt:JellyfinLibrary.Companion.COLLECTION_TYPE_MUSIC]` (`"music"`).
- `[JellyfinPlaylist.kt:JellyfinPlaylist]` / `[JellyfinPlaylist.kt:JellyfinPlaylist.Companion.empty]` - `@Immutable` `@Parcelize` playlist metadata model (`id`, `name`, `songCount`, `duration`, `created`, `changed`).
- `[JellyfinSong.kt:JellyfinSong]` - `@Immutable` `@Parcelize` track model with `[JellyfinSong.kt:JellyfinSong.Companion.empty]` and `[JellyfinSong.kt:JellyfinSong.resolvedMimeType]`.

## Invariants
- `[JellyfinCredentials.kt:JellyfinCredentials.connectionValidationError]` rejects URLs with embedded username/password credentials and rejects non-HTTPS URLs unless `[CloudStreamSecurity.isLocalServerHost]` returns `true` for the host.
- `[JellyfinCredentials.kt:JellyfinCredentials.normalizedHttpUrlOrNull]` strips trailing `/` characters and defaults scheme-less URLs to `http://` only for local server hosts and `https://` for all other hosts.
- `[JellyfinCredentials.kt:JellyfinCredentials.isValid]` requires non-blank `serverUrl`, non-blank `username`, and either non-blank `password` or non-blank `accessToken`; `[JellyfinCredentials.kt:JellyfinCredentials.hasToken]` requires both non-blank `accessToken` and `userId`.
- `[JellyfinLibrary.kt:JellyfinLibrary.isMusic]` is `true` only when `collectionType == "music"` (`[JellyfinLibrary.kt:JellyfinLibrary.COLLECTION_TYPE_MUSIC]`).
- `[JellyfinSong.kt:JellyfinSong.resolvedMimeType]` defaults to `"audio/mpeg"` when `contentType` is `null`.

## Side Effects
- None.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
