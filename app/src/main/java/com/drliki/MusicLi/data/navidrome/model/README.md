# Navidrome Data Models

## Overview
Immutable domain models representing Subsonic/Navidrome entities (`NavidromeAlbum`, `NavidromeArtist`, `NavidromeMusicFolder`, `NavidromePlaylist`, `NavidromeSong`), authentication modes (`NavidromeAuthMethod`), and server credentials (`NavidromeCredentials`) with URL normalization and transport validation.

## Interface
- `[NavidromeAlbum.kt:NavidromeAlbum]` / `[NavidromeAlbum.kt:NavidromeAlbum.Companion.empty]` - `@Immutable` `@Parcelize` Subsonic `AlbumID3` model.
- `[NavidromeArtist.kt:NavidromeArtist]` / `[NavidromeArtist.kt:NavidromeArtist.Companion.empty]` - `@Immutable` `@Parcelize` Subsonic `ArtistID3` model.
- `[NavidromeAuthMethod.kt:NavidromeAuthMethod]` - Enum (`TOKEN`, `PASSWORD`) with `[NavidromeAuthMethod.kt:NavidromeAuthMethod.Companion.fromStorageKey]`.
- `[NavidromeCredentials.kt:NavidromeCredentials]` - Credentials (`serverUrl`, `username`, `password`, `clientId = "MusicLi"`) with `API_VERSION` (`"1.16.1"`), `empty()`, `isValid`, `normalizedHttpUrlOrNull`, `normalizedServerUrl`, `requiresLocalNetworkAccess`, and `connectionValidationError(requireHttps)`.
- `[NavidromeMusicFolder.kt:NavidromeMusicFolder]` / `[NavidromeMusicFolder.kt:NavidromeMusicFolder.Companion.empty]` - `@Immutable` `@Parcelize` Subsonic `MusicFolder` model.
- `[NavidromePlaylist.kt:NavidromePlaylist]` / `[NavidromePlaylist.kt:NavidromePlaylist.Companion.empty]` - `@Immutable` `@Parcelize` Subsonic `Playlist` model.
- `[NavidromeSong.kt:NavidromeSong]` - `@Immutable` `@Parcelize` Subsonic `Child` track model with `empty()`, `[NavidromeSong.kt:NavidromeSong.dateAddedOr]`, and `[NavidromeSong.kt:NavidromeSong.resolvedMimeType]`.

## Invariants
- `[NavidromeAuthMethod.kt:NavidromeAuthMethod.Companion.fromStorageKey]` defaults to `TOKEN` for `null` or unrecognized storage values.
- `[NavidromeCredentials.kt:NavidromeCredentials.connectionValidationError]` rejects embedded URL credentials and non-HTTPS URLs unless `requireHttps == false` and `[CloudStreamSecurity.isLocalServerHost]` returns `true` for the host.
- `[NavidromeCredentials.kt:NavidromeCredentials.normalizedHttpUrlOrNull]` strips trailing `/` characters and prepends `http://` only for local server hosts and `https://` otherwise when no scheme is present.
- `[NavidromeSong.kt:NavidromeSong.dateAddedOr]` returns `created` when `created > 0L` and `fallbackMs` otherwise; `[NavidromeSong.kt:NavidromeSong.resolvedMimeType]` falls back from `contentType` to `suffix` (`mp3`, `flac`, `ogg`/`oga`, `m4a`/`mp4`/`aac`, `wav`, `wma`) to `"audio/mpeg"`.

## Side Effects
- None.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
