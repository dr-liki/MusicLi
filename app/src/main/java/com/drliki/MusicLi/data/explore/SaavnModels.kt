package com.drliki.MusicLi.data.explore

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import com.google.gson.annotations.SerializedName
import com.drliki.MusicLi.data.model.ArtistRef
import com.drliki.MusicLi.data.model.Song

internal const val SAAVN_SONG_ID_PREFIX = "saavn_"

val Song.isExploreStream: Boolean
    get() = id.startsWith(SAAVN_SONG_ID_PREFIX)

val Song.saavnId: String?
    get() = id.takeIf { it.startsWith(SAAVN_SONG_ID_PREFIX) }?.removePrefix(SAAVN_SONG_ID_PREFIX)

fun decodeSaavnHtml(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val decoded = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)?.toString() ?: raw
    return decoded
        .replace('\u00A0', ' ')
        .trim()
}

@Keep
data class SaavnImageDto(
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("url") val url: String? = null,
)

@Keep
data class SaavnDownloadUrlDto(
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("url") val url: String? = null,
)

@Keep
data class SaavnAlbumRefDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String? = null,
)

@Keep
data class SaavnArtistRefDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("image") val image: List<SaavnImageDto>? = null,
    @SerializedName("url") val url: String? = null,
)

@Keep
data class SaavnArtistsGroupDto(
    @SerializedName("primary") val primary: List<SaavnArtistRefDto>? = null,
    @SerializedName("featured") val featured: List<SaavnArtistRefDto>? = null,
    @SerializedName("all") val all: List<SaavnArtistRefDto>? = null,
)

@Keep
data class SaavnLyricsDto(
    @SerializedName("lyrics") val lyrics: String? = null,
    @SerializedName("copyright") val copyright: String? = null,
    @SerializedName("snippet") val snippet: String? = null,
)

@Keep
data class SaavnSongDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("explicitContent") val explicitContent: Boolean? = null,
    @SerializedName("playCount") val playCount: Long? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("hasLyrics") val hasLyrics: Boolean? = null,
    @SerializedName("lyricsId") val lyricsId: String? = null,
    @SerializedName("lyrics") val lyrics: SaavnLyricsDto? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("copyright") val copyright: String? = null,
    @SerializedName("album") val album: SaavnAlbumRefDto? = null,
    @SerializedName("artists") val artists: SaavnArtistsGroupDto? = null,
    @SerializedName("image") val image: List<SaavnImageDto>? = null,
    @SerializedName("downloadUrl") val downloadUrl: List<SaavnDownloadUrlDto>? = null,
)

@Keep
data class SaavnSongResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<SaavnSongDto>? = null,
)

@Keep
data class SaavnLyricsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: SaavnLyricsDto? = null,
)

@Keep
data class SaavnSongSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("total") val total: Int? = null,
        @SerializedName("start") val start: Int? = null,
        @SerializedName("results") val results: List<SaavnSongDto>? = null,
    )
}

@Keep
data class SaavnGlobalSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("topQuery") val topQuery: TopQuery? = null,
        @SerializedName("songs") val songs: Songs? = null,
        @SerializedName("albums") val albums: Albums? = null,
        @SerializedName("artists") val artists: Artists? = null,
        @SerializedName("playlists") val playlists: Playlists? = null,
    ) {
        @Keep
        data class TopQuery(
            @SerializedName("results") val results: List<TopResultDto>? = null,
        )

        @Keep
        data class TopResultDto(
            @SerializedName("id") val id: String? = null,
            @SerializedName("title") val title: String? = null,
            @SerializedName("image") val image: List<SaavnImageDto>? = null,
            @SerializedName("url") val url: String? = null,
            @SerializedName("type") val type: String? = null,
            @SerializedName("description") val description: String? = null,
        )

        @Keep
        data class Songs(
            @SerializedName("results") val results: List<SongItemDto>? = null,
        )

        @Keep
        data class SongItemDto(
            @SerializedName("id") val id: String? = null,
            @SerializedName("title") val title: String? = null,
            @SerializedName("image") val image: List<SaavnImageDto>? = null,
            @SerializedName("album") val album: String? = null,
            @SerializedName("url") val url: String? = null,
            @SerializedName("type") val type: String? = null,
            @SerializedName("description") val description: String? = null,
            @SerializedName("primaryArtists") val primaryArtists: String? = null,
            @SerializedName("singers") val singers: String? = null,
            @SerializedName("language") val language: String? = null,
        )

        @Keep
        data class Albums(
            @SerializedName("results") val results: List<AlbumItemDto>? = null,
        )

        @Keep
        data class AlbumItemDto(
            @SerializedName("id") val id: String? = null,
            @SerializedName("title") val title: String? = null,
            @SerializedName("image") val image: List<SaavnImageDto>? = null,
            @SerializedName("artist") val artist: String? = null,
            @SerializedName("url") val url: String? = null,
            @SerializedName("type") val type: String? = null,
            @SerializedName("description") val description: String? = null,
            @SerializedName("year") val year: String? = null,
            @SerializedName("language") val language: String? = null,
        )

        @Keep
        data class Artists(
            @SerializedName("results") val results: List<ArtistItemDto>? = null,
        )

        @Keep
        data class ArtistItemDto(
            @SerializedName("id") val id: String? = null,
            @SerializedName("title") val title: String? = null,
            @SerializedName("image") val image: List<SaavnImageDto>? = null,
            @SerializedName("type") val type: String? = null,
            @SerializedName("description") val description: String? = null,
        )

        @Keep
        data class Playlists(
            @SerializedName("results") val results: List<PlaylistItemDto>? = null,
        )

        @Keep
        data class PlaylistItemDto(
            @SerializedName("id") val id: String? = null,
            @SerializedName("title") val title: String? = null,
            @SerializedName("image") val image: List<SaavnImageDto>? = null,
            @SerializedName("url") val url: String? = null,
            @SerializedName("type") val type: String? = null,
            @SerializedName("language") val language: String? = null,
            @SerializedName("description") val description: String? = null,
        )
    }
}

@Keep
data class SaavnAlbumSearchItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("playCount") val playCount: Long? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("explicitContent") val explicitContent: Boolean? = null,
    @SerializedName("artists") val artists: SaavnArtistsGroupDto? = null,
    @SerializedName("artist") val legacyArtist: SaavnArtistsGroupDto? = null,
    @SerializedName("image") val image: List<SaavnImageDto>? = null,
)

@Keep
data class SaavnAlbumsSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("total") val total: Int? = null,
        @SerializedName("start") val start: Int? = null,
        @SerializedName("results") val results: List<SaavnAlbumSearchItemDto>? = null,
    )
}

@Keep
data class SaavnAlbumDetailResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("id") val id: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("url") val url: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("year") val year: Int? = null,
        @SerializedName("language") val language: String? = null,
        @SerializedName("artists") val artists: SaavnArtistsGroupDto? = null,
        @SerializedName("artist") val legacyArtist: SaavnArtistsGroupDto? = null,
        @SerializedName("image") val image: List<SaavnImageDto>? = null,
        @SerializedName("songs") val songs: List<SaavnSongDto>? = null,
    )
}

@Keep
data class SaavnPlaylistsSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("total") val total: Int? = null,
        @SerializedName("start") val start: Int? = null,
        @SerializedName("results") val results: List<ItemDto>? = null,
    ) {
        @Keep
        data class ItemDto(
            @SerializedName("id") val id: String? = null,
            @SerializedName("name") val name: String? = null,
            @SerializedName("type") val type: String? = null,
            @SerializedName("url") val url: String? = null,
            @SerializedName("image") val image: List<SaavnImageDto>? = null,
            @SerializedName("songCount") val songCount: Int? = null,
            @SerializedName("language") val language: String? = null,
            @SerializedName("explicitContent") val explicitContent: Boolean? = null,
        )
    }
}

@Keep
data class SaavnPlaylistDetailResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("id") val id: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("url") val url: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("year") val year: Int? = null,
        @SerializedName("songCount") val songCount: Int? = null,
        @SerializedName("language") val language: String? = null,
        @SerializedName("artists") val artists: List<SaavnArtistRefDto>? = null,
        @SerializedName("image") val image: List<SaavnImageDto>? = null,
        @SerializedName("songs") val songs: List<SaavnSongDto>? = null,
    )
}

@Keep
data class SaavnArtistsSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("total") val total: Int? = null,
        @SerializedName("start") val start: Int? = null,
        @SerializedName("results") val results: List<SaavnArtistRefDto>? = null,
    )
}

@Keep
data class SaavnArtistDetailResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: Data? = null,
) {
    @Keep
    data class Data(
        @SerializedName("id") val id: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("url") val url: String? = null,
        @SerializedName("followerCount") val followerCount: Int? = null,
        @SerializedName("fanCount") val fanCount: Int? = null,
        @SerializedName("dominantLanguage") val dominantLanguage: String? = null,
        @SerializedName("dominantType") val dominantType: String? = null,
        @SerializedName("image") val image: List<SaavnImageDto>? = null,
        @SerializedName("topSongs") val topSongs: List<SaavnSongDto>? = null,
        @SerializedName("topAlbums") val topAlbums: List<SaavnAlbumSearchItemDto>? = null,
        @SerializedName("singles") val singles: List<SaavnAlbumSearchItemDto>? = null,
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
