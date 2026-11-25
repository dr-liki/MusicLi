package com.lostf1sh.pixelplayeross.data.explore

import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExploreRepository @Inject constructor(
    private val api: JioSaavnApiClient,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val feedCache = ConcurrentHashMap<String, ExploreHomeFeed>()

    private suspend fun baseUrl(): String = userPreferencesRepository.exploreApiBaseUrlFlow.first()

    suspend fun getHomeFeed(language: String = "All", forceRefresh: Boolean = false): Result<ExploreHomeFeed> =
        runCatching {
            val langKey = language.lowercase().trim()
            if (!forceRefresh) {
                feedCache[langKey]?.let { return@runCatching it }
            }
            val url = baseUrl()
            val langQuery = when (langKey) {
                "all", "" -> "trending"
                else -> "$language hits"
            }
            val albumQuery = when (langKey) {
                "all", "" -> "top albums"
                else -> "$language album"
            }
            val playlistQuery = when (langKey) {
                "all", "" -> "top charts"
                else -> "$language hits"
            }
            val artistQuery = when (langKey) {
                "all", "" -> "arijit"
                else -> "$language singer"
            }

            val feed = coroutineScope {
                val songsDeferred = async { runCatching { api.searchSongs(url, langQuery, 0, 20) } }
                val albumsDeferred = async { runCatching { api.searchAlbums(url, albumQuery, 0, 15) } }
                val playlistsDeferred = async { runCatching { api.searchPlaylists(url, playlistQuery, 0, 15) } }
                val artistsDeferred = async { runCatching { api.searchArtists(url, artistQuery, 0, 12) } }

                val songsResult = songsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val playlistsResult = playlistsDeferred.await()
                val artistsResult = artistsDeferred.await()

                val songs = songsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExploreSong() }
                val albums = albumsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExploreAlbum() }
                val playlists = playlistsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExplorePlaylist() }
                val artists = artistsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExploreArtist() }

                val resultFeed = ExploreHomeFeed(
                    trendingSongs = songs,
                    topAlbums = albums,
                    featuredPlaylists = playlists,
                    topArtists = artists,
                )
                if (songs.isEmpty() && albums.isEmpty() && playlists.isEmpty() && artists.isEmpty()) {
                    val failure = listOfNotNull(
                        songsResult.exceptionOrNull(),
                        albumsResult.exceptionOrNull(),
                        playlistsResult.exceptionOrNull(),
                        artistsResult.exceptionOrNull(),
                    ).firstOrNull()
                    if (failure != null) throw failure
                }
                resultFeed
            }
            if (feed.trendingSongs.isNotEmpty() ||
                feed.topAlbums.isNotEmpty() ||
                feed.featuredPlaylists.isNotEmpty() ||
                feed.topArtists.isNotEmpty()
            ) {
                feedCache[langKey] = feed
            }
            feed
        }

    suspend fun searchAll(query: String): Result<ExploreSearchResults> = runCatching {
        val url = baseUrl()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@runCatching ExploreSearchResults()

        coroutineScope {
            val songsDeferred = async { runCatching { api.searchSongs(url, trimmed, 0, 30) } }
            val albumsDeferred = async { runCatching { api.searchAlbums(url, trimmed, 0, 15) } }
            val artistsDeferred = async { runCatching { api.searchArtists(url, trimmed, 0, 12) } }
            val playlistsDeferred = async { runCatching { api.searchPlaylists(url, trimmed, 0, 15) } }

            val songsResult = songsDeferred.await()
            val albumsResult = albumsDeferred.await()
            val artistsResult = artistsDeferred.await()
            val playlistsResult = playlistsDeferred.await()

            val songs = songsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExploreSong() }
            val albums = albumsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExploreAlbum() }
            val artists = artistsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExploreArtist() }
            val playlists = playlistsResult.getOrNull()?.data?.results.orEmpty().mapNotNull { it.toExplorePlaylist() }

            if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) {
                val failure = listOfNotNull(
                    songsResult.exceptionOrNull(),
                    albumsResult.exceptionOrNull(),
                    artistsResult.exceptionOrNull(),
                    playlistsResult.exceptionOrNull(),
                ).firstOrNull()
                if (failure != null) throw failure
            }

            ExploreSearchResults(
                songs = songs,
                albums = albums,
                artists = artists,
                playlists = playlists,
            )
        }
    }

    suspend fun resolveSong(songId: String, includeLyrics: Boolean = true): Result<ExploreSong> = runCatching {
        val url = baseUrl()
        val response = api.getSongById(url, songId, includeLyrics)
        val dto = response.data?.firstOrNull()
            ?: throw IllegalStateException("Song details unavailable for id=$songId")
        dto.toExploreSong() ?: throw IllegalStateException("Invalid song metadata for id=$songId")
    }

    suspend fun getAlbumDetail(albumId: String): Result<ExploreAlbum> = runCatching {
        val url = baseUrl()
        val data = api.getAlbumById(url, albumId).data
            ?: throw IllegalStateException("Album details unavailable")
        val songs = data.songs.orEmpty().mapIndexedNotNull { idx, dto ->
            dto.toExploreSong(trackNumber = idx + 1)
        }
        val artistName = formatArtists(data.artists ?: data.legacyArtist)
        ExploreAlbum(
            id = data.id ?: albumId,
            title = decodeSaavnHtml(data.name).ifBlank { "Album" },
            artist = artistName,
            year = data.year ?: 0,
            language = data.language,
            songCount = songs.size,
            artworkUrl = pickBestImageUrl(data.image),
            songs = songs,
        )
    }

    suspend fun getPlaylistDetail(playlistId: String): Result<ExplorePlaylist> = runCatching {
        val url = baseUrl()
        val data = api.getPlaylistById(url, playlistId).data
            ?: throw IllegalStateException("Playlist details unavailable")
        val songs = data.songs.orEmpty().mapIndexedNotNull { idx, dto ->
            dto.toExploreSong(trackNumber = idx + 1)
        }
        ExplorePlaylist(
            id = data.id ?: playlistId,
            title = decodeSaavnHtml(data.name).ifBlank { "Playlist" },
            subtitle = decodeSaavnHtml(data.description).ifBlank { "${songs.size} tracks" },
            language = data.language,
            songCount = maxOf(data.songCount ?: 0, songs.size),
            artworkUrl = pickBestImageUrl(data.image),
            songs = songs,
        )
    }

    suspend fun getArtistDetail(artistId: String): Result<ExploreArtist> = runCatching {
        val url = baseUrl()
        val data = api.getArtistById(url, artistId).data
            ?: throw IllegalStateException("Artist details unavailable")
        ExploreArtist(
            id = data.id ?: artistId,
            name = decodeSaavnHtml(data.name).ifBlank { "Artist" },
            role = decodeSaavnHtml(data.dominantType).ifBlank { "Artist" },
            artworkUrl = pickBestImageUrl(data.image),
            followerCount = data.followerCount ?: 0,
            topSongs = data.topSongs.orEmpty().mapNotNull { it.toExploreSong() },
            topAlbums = data.topAlbums.orEmpty().mapNotNull { it.toExploreAlbum() },
        )
    }

    private fun SaavnSongDto.toExploreSong(trackNumber: Int = 0): ExploreSong? {
        val songId = id?.takeIf { it.isNotBlank() } ?: return null
        val title = decodeSaavnHtml(name).ifBlank { return null }
        val primary = artists?.primary.orEmpty().map { decodeSaavnHtml(it.name) }.filter(String::isNotBlank)
        val all = artists?.all.orEmpty().map { decodeSaavnHtml(it.name) }.filter(String::isNotBlank)
        val artistStr = (if (primary.isNotEmpty()) primary else all).distinct().joinToString(", ").ifBlank { "Unknown Artist" }
        val albumArtistStr = all.distinct().joinToString(", ").ifBlank { artistStr }
        val streamMap = buildMap {
            downloadUrl.orEmpty().forEach { item ->
                val url = item.url?.takeIf { it.isNotBlank() } ?: return@forEach
                val kbps = Regex("""\d+""").find(item.quality.orEmpty())?.value?.toIntOrNull() ?: 320
                put(kbps, url)
            }
        }
        return ExploreSong(
            id = songId,
            title = title,
            artist = artistStr,
            albumArtist = albumArtistStr,
            albumId = album?.id.orEmpty(),
            albumName = decodeSaavnHtml(album?.name),
            year = year?.trim()?.toIntOrNull() ?: 0,
            releaseDate = releaseDate,
            durationSeconds = duration?.toInt() ?: 0,
            language = language?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
            label = decodeSaavnHtml(label).takeIf { it.isNotBlank() },
            copyright = decodeSaavnHtml(copyright).takeIf { it.isNotBlank() },
            hasLyrics = hasLyrics == true,
            lyricsText = lyrics?.lyrics?.let(::decodeSaavnHtml)?.takeIf { it.isNotBlank() },
            artworkUrl = pickBestImageUrl(image),
            thumbnailUrl = pickThumbnailUrl(image),
            streamUrlsByQuality = streamMap,
            saavnUrl = url,
            explicitContent = explicitContent == true,
            trackNumber = trackNumber,
        )
    }

    private fun SaavnAlbumSearchItemDto.toExploreAlbum(): ExploreAlbum? {
        val albumId = id?.takeIf { it.isNotBlank() } ?: return null
        val title = decodeSaavnHtml(name).ifBlank { return null }
        return ExploreAlbum(
            id = albumId,
            title = title,
            artist = formatArtists(artists ?: legacyArtist),
            year = year ?: 0,
            language = language,
            songCount = 0,
            artworkUrl = pickBestImageUrl(image),
        )
    }

    private fun SaavnPlaylistsSearchResponse.Data.ItemDto.toExplorePlaylist(): ExplorePlaylist? {
        val plId = id?.takeIf { it.isNotBlank() } ?: return null
        val title = decodeSaavnHtml(name).ifBlank { return null }
        val count = songCount ?: 0
        return ExplorePlaylist(
            id = plId,
            title = title,
            subtitle = if (count > 0) "$count songs" else (language.orEmpty()),
            language = language,
            songCount = count,
            artworkUrl = pickBestImageUrl(image),
        )
    }

    private fun SaavnArtistRefDto.toExploreArtist(): ExploreArtist? {
        val artistId = id?.takeIf { it.isNotBlank() } ?: return null
        val artistName = decodeSaavnHtml(name).ifBlank { return null }
        return ExploreArtist(
            id = artistId,
            name = artistName,
            role = decodeSaavnHtml(role).ifBlank { "Artist" },
            artworkUrl = pickBestImageUrl(image),
        )
    }

    private fun formatArtists(group: SaavnArtistsGroupDto?): String {
        val primary = group?.primary.orEmpty().map { decodeSaavnHtml(it.name) }.filter(String::isNotBlank)
        val all = group?.all.orEmpty().map { decodeSaavnHtml(it.name) }.filter(String::isNotBlank)
        return (if (primary.isNotEmpty()) primary else all).distinct().joinToString(", ").ifBlank { "Various Artists" }
    }

    private fun pickBestImageUrl(images: List<SaavnImageDto>?): String? {
        if (images.isNullOrEmpty()) return null
        return images.lastOrNull { !it.url.isNullOrBlank() }?.url
    }

    private fun pickThumbnailUrl(images: List<SaavnImageDto>?): String? {
        if (images.isNullOrEmpty()) return null
        return images.firstOrNull { it.quality?.contains("150") == true && !it.url.isNullOrBlank() }?.url
            ?: pickBestImageUrl(images)
    }
}
