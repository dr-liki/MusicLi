# Privacy Policy

MusicLi is local-first and does not include analytics, advertising SDKs, Firebase, Crashlytics, or Google Play Services runtime dependencies.

## Data Stored On Device

The app stores music library metadata, playlists, favorites, lyrics, preferences, playback state, artwork cache, synced video overrides, and optional backup/restore data on the device.

## Network Access

Network features are optional and user-controlled:

- Navidrome/Subsonic and Jellyfin are used only after the user signs in to a self-hosted server. Those servers may receive the authentication, library, playback state, and play history requests needed for their protocols.
- MusicBrainz lookup runs only when the user requests enrichment for a track. The app sends that track's title and, when available, artist, album, and duration to the public MusicBrainz search API; applying a result stores the selected MusicBrainz identifiers locally.
- Online lyrics search and bulk auto-embedding run only when online lyrics are enabled or triggered by the user, querying configured providers (LRCLIB, BetterLyrics, lrc.red, Paxsenix, KuGou, JioSaavn, YouTube captions, NetEase, and Lyrics.ovh) with track title, artist, album, and duration metadata.
- Live lyrics translation runs only when requested by the user and sends lyric text lines to `translate.googleapis.com`.
- Deezer artist artwork lookup is used only when online artist images are enabled.
- Explore discovery, streaming, and offline downloads run only when the user uses the Explore tab or searches Explore, querying the configured JioSaavn API endpoint.
- Synced cover video playback runs only when enabled or opened by the user, querying YouTube (via NewPipeExtractor / InnerTube) and SponsorBlock for video candidate matching and intro-offset metadata.
- ListenBrainz scrobbling is optional and disabled by default. It activates only after the user connects a ListenBrainz account with their own user token. While connected, the app submits listening activity (track title, artist, album, duration, listen timestamps, and MusicBrainz identifiers when available) to the configured ListenBrainz server for the playback sources the user has enabled — `listenbrainz.org` by default, or a user-supplied custom URL for self-hosted ListenBrainz-compatible servers such as Maloja. Disconnecting stops submissions and deletes any queued listens.
- Update checks contact the GitHub Releases API (`api.github.com/repos/dr-liki/MusicLi/releases`) when the user opens the Updates screen, taps Check, or uses daily automatic checks. Installing an update downloads the APK from GitHub Releases. These requests carry no account or library data, only standard HTTP headers including the app version.

Server credentials and preferences are stored locally. The app does not sell or share user data.

## Files And Media

MusicLi requests media/file permissions to scan and play local music, read artwork, edit metadata, sync `.m3u8` playlists, and export/import user backups. Optional Navidrome/Subsonic and Jellyfin downloads are stored in the app's private files area for offline playback and are excluded from Android cloud backup and device transfer.

## Crash Reports

The app keeps crash logs locally for troubleshooting. It does not automatically upload crash reports.

## Contact

Open an issue at `https://github.com/dr-liki/MusicLi/issues` for privacy questions that do not contain sensitive personal data.
