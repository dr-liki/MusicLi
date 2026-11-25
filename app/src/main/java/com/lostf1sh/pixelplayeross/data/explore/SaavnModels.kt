package com.lostf1sh.pixelplayeross.data.explore

import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import com.google.gson.annotations.SerializedName
import com.lostf1sh.pixelplayeross.data.model.ArtistRef
import com.lostf1sh.pixelplayeross.data.model.Song

internal const val SAAVN_SONG_ID_PREFIX = "saavn_"

val Song.isExploreStream: Boolean
    get() = id.startsWith(SAAVN_SONG_ID_PREFIX)

val Song.saavnId: String?
    get() = id.takeIf { it.startsWith(SAAVN_SONG_ID_PREFIX) }?.removePrefix(SAAVN_SONG_ID_PREFIX)

fun decodeSaavnHtml(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .trim()
}

data class SaavnImageDto(
    val quality: String? = null,
    val url: String? = null,
)

data class SaavnDownloadUrlDto(
    val quality: String? = null,
    val url: String? = null,
)

data class SaavnAlbumRefDto(
    val id: String? = null,
    val name: String? = null,
    val url: String? = null,
)

data class SaavnArtistRefDto(
    val id: String? = null,
    val name: String? = null,
    val role: String? = null,
    val type: String? = null,
    val image: List<SaavnImageDto>? = null,
    val url: String? = null,
)

data class SaavnArtistsGroupDto(
    val primary: List<SaavnArtistRefDto>? = null,
    val featured: List<SaavnArtistRefDto>? = null,
    val all: List<SaavnArtistRefDto>? = null,
)

data class SaavnLyricsDto(
    val lyrics: String? = null,
    val copyright: String? = null,
    val snippet: String? = null,
)

data class SaavnSongDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val year: String? = null,
    val releaseDate: String? = null,
    val duration: Double? = null,
    val label: String? = null,
    val explicitContent: Boolean = false,
    val playCount: Long? = null,
    val language: String? = null,
    val hasLyrics: Boolean = false,
    val lyricsId: String? = null,
    val lyrics: SaavnLyricsDto? = null,
    val url: String? = null,
    val copyright: String? = null,
    val album: SaavnAlbumRefDto? = null,
    val artists: SaavnArtistsGroupDto? = null,
    val image: List<SaavnImageDto>? = null,
    val downloadUrl: List<SaavnDownloadUrlDto>? = null,
)

data class SaavnSongResponse(
    val success: Boolean = false,
    val data: List<SaavnSongDto>? = null,
)

data class SaavnLyricsResponse(
    val success: Boolean = false,
    val data: SaavnLyricsDto? = null,
)

data class SaavnSongSearchResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val total: Int = 0,
        val start: Int = 0,
        val results: List<SaavnSongDto>? = null,
    )
}

data class SaavnGlobalSearchResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val topQuery: TopQuery? = null,
        val songs: Songs? = null,
        val albums: Albums? = null,
        val artists: Artists? = null,
        val playlists: Playlists? = null,
    ) {
        data class TopQuery(val results: List<TopResultDto>? = null)
        data class TopResultDto(
            val id: String? = null,
            val title: String? = null,
            val image: List<SaavnImageDto>? = null,
            val url: String? = null,
            val type: String? = null,
            val description: String? = null,
        )

        data class Songs(val results: List<SongItemDto>? = null)
        data class SongItemDto(
            val id: String? = null,
            val title: String? = null,
            val image: List<SaavnImageDto>? = null,
            val album: String? = null,
            val url: String? = null,
            val type: String? = null,
            val description: String? = null,
            val primaryArtists: String? = null,
            val singers: String? = null,
            val language: String? = null,
        )

        data class Albums(val results: List<AlbumItemDto>? = null)
        data class AlbumItemDto(
            val id: String? = null,
            val title: String? = null,
            val image: List<SaavnImageDto>? = null,
            val artist: String? = null,
            val url: String? = null,
            val type: String? = null,
            val description: String? = null,
            val year: String? = null,
            val language: String? = null,
        )

        data class Artists(val results: List<ArtistItemDto>? = null)
        data class ArtistItemDto(
            val id: String? = null,
            val title: String? = null,
            val image: List<SaavnImageDto>? = null,
            val type: String? = null,
            val description: String? = null,
        )

        data class Playlists(val results: List<PlaylistItemDto>? = null)
        data class PlaylistItemDto(
            val id: String? = null,
            val title: String? = null,
            val image: List<SaavnImageDto>? = null,
            val url: String? = null,
            val type: String? = null,
            val language: String? = null,
            val description: String? = null,
        )
    }
}

data class SaavnAlbumSearchItemDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    val year: Int = 0,
    val playCount: Long = 0,
    val language: String? = null,
    val explicitContent: Boolean = false,
    val artists: SaavnArtistsGroupDto? = null,
    @SerializedName("artist") val legacyArtist: SaavnArtistsGroupDto? = null,
    val image: List<SaavnImageDto>? = null,
)

data class SaavnAlbumsSearchResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val total: Int = 0,
        val start: Int = 0,
        val results: List<SaavnAlbumSearchItemDto>? = null,
    )
}

data class SaavnAlbumDetailResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val id: String? = null,
        val name: String? = null,
        val url: String? = null,
        val description: String? = null,
        val year: Int = 0,
        val language: String? = null,
        val artists: SaavnArtistsGroupDto? = null,
        @SerializedName("artist") val legacyArtist: SaavnArtistsGroupDto? = null,
        val image: List<SaavnImageDto>? = null,
        val songs: List<SaavnSongDto>? = null,
    )
}

data class SaavnPlaylistsSearchResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val total: Int = 0,
        val start: Int = 0,
        val results: List<ItemDto>? = null,
    ) {
        data class ItemDto(
            val id: String? = null,
            val name: String? = null,
            val type: String? = null,
            val url: String? = null,
            val image: List<SaavnImageDto>? = null,
            val songCount: Int = 0,
            val language: String? = null,
            val explicitContent: Boolean = false,
        )
    }
}

data class SaavnPlaylistDetailResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val id: String? = null,
        val name: String? = null,
        val url: String? = null,
        val description: String? = null,
        val year: Int = 0,
        val songCount: Int = 0,
        val language: String? = null,
        val artists: List<SaavnArtistRefDto>? = null,
        val image: List<SaavnImageDto>? = null,
        val songs: List<SaavnSongDto>? = null,
    )
}

data class SaavnArtistsSearchResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val total: Int = 0,
        val start: Int = 0,
        val results: List<SaavnArtistRefDto>? = null,
    )
}

data class SaavnArtistDetailResponse(
    val success: Boolean = false,
    val data: Data? = null,
) {
    data class Data(
        val id: String? = null,
        val name: String? = null,
        val url: String? = null,
        val followerCount: Int = 0,
        val fanCount: Int = 0,
        val dominantLanguage: String? = null,
        val dominantType: String? = null,
        val image: List<SaavnImageDto>? = null,
        val topSongs: List<SaavnSongDto>? = null,
        val topAlbums: List<SaavnAlbumSearchItemDto>? = null,
        val singles: List<SaavnAlbumSearchItemDto>? = null,
    )
}

@Immutable
data class ExploreSong(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val albumId: String,
    val albumName: String,
    val year: Int,
    val releaseDate: String?,
    val durationSeconds: Int,
    val language: String?,
    val label: String?,
    val copyright: String?,
    val hasLyrics: Boolean,
    val lyricsText: String?,
    val artworkUrl: String?,
    val thumbnailUrl: String?,
    val streamUrlsByQuality: Map<Int, String>,
    val saavnUrl: String?,
    val explicitContent: Boolean = false,
    val trackNumber: Int = 0,
) {
    fun selectStreamUrl(preferredKbps: Int): String? {
        if (streamUrlsByQuality.isEmpty()) return null
        return streamUrlsByQuality[preferredKbps]
            ?: streamUrlsByQuality.entries.minByOrNull { kotlin.math.abs(it.key - preferredKbps) }?.value
            ?: streamUrlsByQuality.maxByOrNull { it.key }?.value
    }

    fun bestStreamUrl(): String? = selectStreamUrl(320)

    fun toPlayerSong(preferredBitrateKbps: Int): Song {
        val streamUrl = selectStreamUrl(preferredBitrateKbps).orEmpty()
        val parsedArtists = artist
            .split(",")
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapIndexed { index, name ->
                ArtistRef(
                    id = -(name.hashCode().toLong()),
                    name = name,
                    isPrimary = index == 0,
                )
            }
        return Song(
            id = "$SAAVN_SONG_ID_PREFIX$id",
            title = title,
            artist = artist,
            artistId = -(artist.hashCode().toLong()),
            artists = parsedArtists,
            album = albumName.ifBlank { title },
            albumId = -(albumId.ifBlank { id }.hashCode().toLong()),
            albumArtist = albumArtist.ifBlank { artist },
            path = "",
            contentUriString = streamUrl,
            albumArtUriString = artworkUrl ?: thumbnailUrl,
            duration = durationSeconds.coerceAtLeast(0) * 1000L,
            genre = language,
            lyrics = lyricsText,
            year = year,
            trackNumber = trackNumber,
            mimeType = "audio/mp4",
            bitrate = preferredBitrateKbps,
            sampleRate = 44100,
        )
    }
}

@Immutable
data class ExploreAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val language: String?,
    val songCount: Int,
    val artworkUrl: String?,
    val songs: List<ExploreSong> = emptyList(),
)

@Immutable
data class ExplorePlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val language: String?,
    val songCount: Int,
    val artworkUrl: String?,
    val songs: List<ExploreSong> = emptyList(),
)

@Immutable
data class ExploreArtist(
    val id: String,
    val name: String,
    val role: String,
    val artworkUrl: String?,
    val followerCount: Int = 0,
    val topSongs: List<ExploreSong> = emptyList(),
    val topAlbums: List<ExploreAlbum> = emptyList(),
)

@Immutable
data class ExploreSearchResults(
    val songs: List<ExploreSong> = emptyList(),
    val albums: List<ExploreAlbum> = emptyList(),
    val artists: List<ExploreArtist> = emptyList(),
    val playlists: List<ExplorePlaylist> = emptyList(),
)

@Immutable
data class ExploreHomeFeed(
    val trendingSongs: List<ExploreSong> = emptyList(),
    val topAlbums: List<ExploreAlbum> = emptyList(),
    val featuredPlaylists: List<ExplorePlaylist> = emptyList(),
    val topArtists: List<ExploreArtist> = emptyList(),
)
