package com.drliki.MusicLi.data.explore

import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class ExploreBrowseCategory {
    NEW_RELEASES,
    CHARTS,
    PLAYLISTS,
    ARTISTS,
}

@Singleton
class ExploreRepository @Inject constructor(
    private val api: JioSaavnApiClient,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val feedCache = ConcurrentHashMap<String, ExploreHomeFeed>()
    private val feedPageOffsets = ConcurrentHashMap<String, Int>()
    private val legacyUrlMigrated = AtomicBoolean(false)

    val connectionDiagnostics: StateFlow<ExploreConnectionDiagnostics>
        get() = api.diagnostics

    fun clearFeedCache() {
        feedCache.clear()
        feedPageOffsets.clear()
    }

    suspend fun testApiConnection(): ExploreConnectionTestResult = api.testConnection(baseUrl())

    private suspend fun baseUrl(): String {
        if (legacyUrlMigrated.compareAndSet(false, true)) {
            runCatching { userPreferencesRepository.migrateLegacyExploreUrlIfNeeded() }
        }
        runCatching {
            api.setAllowMirrorFallback(userPreferencesRepository.exploreAllowMirrorFallbackFlow.first())
        }
        return userPreferencesRepository.exploreApiBaseUrlFlow.first()
    }

    suspend fun getHomeFeed(language: String = "All", forceRefresh: Boolean = false): Result<ExploreHomeFeed> =
        runCatching {
            val langKey = language.lowercase().trim()
            if (!forceRefresh) {
                feedCache[langKey]?.let { return@runCatching it }
            }
            val nextPage = if (forceRefresh) {
                feedPageOffsets.compute(langKey) { _, prev -> ((prev ?: 0) + 1) % 5 } ?: 0
            } else {
                feedPageOffsets[langKey] ?: 0
            }
            val url = baseUrl()
            val isAllLanguages = langKey == "all" || langKey.isEmpty()
            val langQuery = if (isAllLanguages) "trending" else "$language hits"
            val albumQuery = if (isAllLanguages) "top albums" else "$language album"
            val playlistQuery = if (isAllLanguages) "top charts" else "$language hits"
            val artistQuery = when (langKey) {
                "english" -> listOf("The Weeknd", "Taylor Swift", "Coldplay", "Ed Sheeran", "Imagine Dragons")[nextPage % 5]
                "kannada" -> listOf("Sanjith Hegde", "Vijay Prakash", "Sonu Nigam", "Raghu Dixit", "Armaan Malik")[nextPage % 5]
                "tamil" -> listOf("Anirudh Ravichander", "A.R. Rahman", "Yuvan Shankar Raja", "Sid Sriram", "Harris Jayaraj")[nextPage % 5]
                "telugu" -> listOf("Devi Sri Prasad", "Thaman S", "Sid Sriram", "Anirudh Ravichander", "M.M. Keeravani")[nextPage % 5]
                "punjabi" -> listOf("Diljit Dosanjh", "AP Dhillon", "Karan Aujla", "Sidhu Moose Wala", "Shubh")[nextPage % 5]
                "hindi" -> listOf("Arijit Singh", "Pritam", "Shreya Ghoshal", "A.R. Rahman", "Atif Aslam")[nextPage % 5]
                else -> listOf("Arijit Singh", "The Weeknd", "Pritam", "Taylor Swift", "A.R. Rahman")[nextPage % 5]
            }

            suspend fun fetchFeedPage(page: Int): ExploreHomeFeed = coroutineScope {
                val useNativeLaunch = isNativeSaavnApiUrl(url)
                if (useNativeLaunch) {
                    val launchLangCookie = if (isAllLanguages) "english,hindi" else langKey
                    val launchDeferred = async { runCatching { api.getLaunchData(url, launchLangCookie) } }
                    val newReleasesDeferred = async {
                        runCatching { api.getNewReleases(url, launchLangCookie, page = page, limit = 24) }
                    }
                    val trendingSongsDeferred = async {
                        runCatching {
                            if (isAllLanguages) {
                                val engDeferred = async { runCatching { api.getTrendingSongs(url, "english", "english hits", page, 12) } }
                                val hinDeferred = async { runCatching { api.getTrendingSongs(url, "hindi", "hindi hits", page, 12) } }
                                val eng = engDeferred.await().getOrNull()?.data?.results.orEmpty()
                                val hin = hinDeferred.await().getOrNull()?.data?.results.orEmpty()
                                val mixed = interleave(eng, hin).take(20)
                                if (mixed.isNotEmpty()) {
                                    SaavnSongSearchResponse(success = true, data = SaavnSongSearchResponse.Data(total = mixed.size, results = mixed))
                                } else {
                                    api.searchSongs(url, langQuery, page, 20)
                                }
                            } else {
                                api.getTrendingSongs(url, langKey, langQuery, page, 20)
                            }
                        }
                    }
                    val artistSearchDeferred = async { runCatching { api.searchArtists(url, artistQuery, page, 12) } }

                    val launchResult = launchDeferred.await()
                    val newReleasesResult = newReleasesDeferred.await()
                    val trendingResult = trendingSongsDeferred.await()
                    val artistSearchResult = artistSearchDeferred.await()

                    val launch = launchResult.getOrNull() ?: SaavnLaunchData()
                    val releases = newReleasesResult.getOrNull() ?: SaavnNewReleasesResult()

                    val trendingRaw = trendingResult.getOrNull()?.data?.results.orEmpty()
                    val hydratedEnglishChartSongs = if (langKey == "english" || trendingRaw.size < 6) {
                        val chartTarget = if (langKey == "english") {
                            launch.charts.firstOrNull {
                                val title = decodeSaavnHtml(it.name).lowercase()
                                title.contains("english") || title.contains("international")
                            } ?: launch.charts.firstOrNull()
                        } else {
                            launch.charts.firstOrNull()
                        }
                        chartTarget?.id?.let { plId ->
                            runCatching { api.getPlaylistById(url, plId, 0, 20).data?.songs.orEmpty() }
                                .getOrDefault(emptyList())
                        }.orEmpty()
                    } else {
                        emptyList()
                    }

                    val songPool = if (langKey == "english") {
                        hydratedEnglishChartSongs + trendingRaw + launch.trendingSongs + releases.songs
                    } else {
                        trendingRaw + launch.trendingSongs + releases.songs + hydratedEnglishChartSongs
                    }

                    val combinedSongs = songPool
                        .distinctBy { it.id }
                        .take(20)
                        .mapNotNull { it.toExploreSong() }

                    val rawAlbums = (
                        if (page > 0 && releases.albums.isNotEmpty()) {
                            releases.albums + launch.newAlbums
                        } else {
                            launch.newAlbums + releases.albums
                        }
                        ).distinctBy { it.id }
                    val combinedAlbums = rotateList(
                        rawAlbums.ifEmpty {
                            runCatching { api.getTrendingAlbums(url, if (isAllLanguages) "english" else langKey, albumQuery, page, 15).data?.results.orEmpty() }.getOrDefault(emptyList())
                        },
                        page = page,
                        limit = 15,
                    ).mapNotNull { it.toExploreAlbum() }

                    val rawPlaylists = (launch.charts + launch.topPlaylists).distinctBy { it.id }
                    val combinedPlaylists = rotateList(
                        rawPlaylists.ifEmpty {
                            runCatching { api.getTrendingPlaylists(url, if (isAllLanguages) "english" else langKey, playlistQuery, page, 15).data?.results.orEmpty() }.getOrDefault(emptyList())
                        },
                        page = page,
                        limit = 15,
                    ).mapNotNull { it.toExplorePlaylist() }

                    val searchedArtists = artistSearchResult.getOrNull()?.data?.results.orEmpty()
                    val rawArtists = if (langKey == "all" || langKey == "hindi" || langKey.isEmpty()) {
                        (launch.artistRecos + searchedArtists).distinctBy { it.id }
                    } else {
                        (searchedArtists + launch.artistRecos).distinctBy { it.id }
                    }
                    val combinedArtists = rotateList(rawArtists, page = page, limit = 12)
                        .mapNotNull { it.toExploreArtist() }

                    val resultFeed = ExploreHomeFeed(
                        trendingSongs = combinedSongs,
                        topAlbums = combinedAlbums,
                        featuredPlaylists = combinedPlaylists,
                        topArtists = combinedArtists,
                    )
                    if (combinedSongs.isEmpty() && combinedAlbums.isEmpty() && combinedPlaylists.isEmpty() && combinedArtists.isEmpty()) {
                        val failure = listOfNotNull(
                            launchResult.exceptionOrNull(),
                            trendingResult.exceptionOrNull(),
                            newReleasesResult.exceptionOrNull(),
                            artistSearchResult.exceptionOrNull(),
                        ).firstOrNull()
                        if (failure != null) throw failure
                    }
                    return@coroutineScope resultFeed
                }

                val songsDeferred = async { runCatching { api.searchSongs(url, langQuery, page, 20) } }
                val albumsDeferred = async { runCatching { api.searchAlbums(url, albumQuery, page, 15) } }
                val playlistsDeferred = async { runCatching { api.searchPlaylists(url, playlistQuery, page, 15) } }
                val artistsDeferred = async { runCatching { api.searchArtists(url, artistQuery, page, 12) } }

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

            var feed = fetchFeedPage(nextPage)
            if (nextPage > 0 &&
                feed.trendingSongs.isEmpty() &&
                feed.topAlbums.isEmpty() &&
                feed.featuredPlaylists.isEmpty() &&
                feed.topArtists.isEmpty()
            ) {
                feedPageOffsets[langKey] = 0
                feed = fetchFeedPage(0)
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

    suspend fun browseCategoryFeed(
        category: ExploreBrowseCategory,
        language: String = "All",
        fallbackQuery: String = "",
    ): Result<ExploreSearchResults> = runCatching {
        val url = baseUrl()
        val langKey = language.lowercase().trim()
        val isAll = langKey.isEmpty() || langKey == "all"
        if (!isNativeSaavnApiUrl(url)) {
            return searchAll(fallbackQuery.ifBlank { category.name.lowercase().replace('_', ' ') })
        }

        val launchLangCookie = if (isAll) "english,hindi" else langKey
        coroutineScope {
            when (category) {
                ExploreBrowseCategory.NEW_RELEASES -> {
                    val releasesDeferred = async { runCatching { api.getNewReleases(url, launchLangCookie, page = 0, limit = 30) } }
                    val launchDeferred = async { runCatching { api.getLaunchData(url, launchLangCookie) } }
                    val releasesRes = releasesDeferred.await()
                    val launchRes = launchDeferred.await()
                    val releases = releasesRes.getOrNull() ?: SaavnNewReleasesResult()
                    val launch = launchRes.getOrNull() ?: SaavnLaunchData()
                    val songs = (releases.songs + launch.trendingSongs).distinctBy { it.id }.mapNotNull { it.toExploreSong() }
                    val albums = (releases.albums + launch.newAlbums).distinctBy { it.id }.mapNotNull { it.toExploreAlbum() }
                    if (songs.isEmpty() && albums.isEmpty()) {
                        (releasesRes.exceptionOrNull() ?: launchRes.exceptionOrNull())?.let { throw it }
                    }
                    ExploreSearchResults(songs = songs, albums = albums)
                }

                ExploreBrowseCategory.CHARTS -> {
                    val launchDeferred = async { runCatching { api.getLaunchData(url, launchLangCookie) } }
                    val trendingDeferred = async {
                        runCatching {
                            if (isAll) {
                                val eng = async { runCatching { api.getTrendingSongs(url, "english", "english hits", 0, 15) } }
                                val hin = async { runCatching { api.getTrendingSongs(url, "hindi", "hindi hits", 0, 15) } }
                                interleave(
                                    eng.await().getOrNull()?.data?.results.orEmpty(),
                                    hin.await().getOrNull()?.data?.results.orEmpty(),
                                ).take(24)
                            } else {
                                api.getTrendingSongs(url, langKey, "$language hits", 0, 24).data?.results.orEmpty()
                            }
                        }
                    }
                    val launchRes = launchDeferred.await()
                    val trendingRes = trendingDeferred.await()
                    val launch = launchRes.getOrNull() ?: SaavnLaunchData()
                    val songs = (trendingRes.getOrNull().orEmpty() + launch.trendingSongs)
                        .distinctBy { it.id }
                        .mapNotNull { it.toExploreSong() }
                    val playlists = (launch.charts + launch.topPlaylists)
                        .distinctBy { it.id }
                        .mapNotNull { it.toExplorePlaylist() }
                    val albums = launch.newAlbums.mapNotNull { it.toExploreAlbum() }
                    if (songs.isEmpty() && playlists.isEmpty() && albums.isEmpty()) {
                        (launchRes.exceptionOrNull() ?: trendingRes.exceptionOrNull())?.let { throw it }
                    }
                    ExploreSearchResults(songs = songs, albums = albums, playlists = playlists)
                }

                ExploreBrowseCategory.PLAYLISTS -> {
                    val launchDeferred = async { runCatching { api.getLaunchData(url, launchLangCookie) } }
                    val launchRes = launchDeferred.await()
                    val launch = launchRes.getOrNull() ?: SaavnLaunchData()
                    val playlistsRaw = (launch.topPlaylists + launch.charts).distinctBy { it.id }
                        .ifEmpty {
                            api.getTrendingPlaylists(
                                url,
                                if (isAll) "english" else langKey,
                                fallbackQuery.ifBlank { "top playlists" },
                                0,
                                20,
                            ).data?.results.orEmpty()
                        }
                    val playlists = playlistsRaw.mapNotNull { it.toExplorePlaylist() }
                    if (playlists.isEmpty()) {
                        launchRes.exceptionOrNull()?.let { throw it }
                    }
                    ExploreSearchResults(playlists = playlists)
                }

                ExploreBrowseCategory.ARTISTS -> {
                    val searchQuery = fallbackQuery.ifBlank { "Arijit Singh" }
                    val searchDeferred = async { runCatching { api.searchArtists(url, searchQuery, 0, 15) } }
                    val launchDeferred = async { runCatching { api.getLaunchData(url, launchLangCookie) } }
                    val searchRes = searchDeferred.await()
                    val launchRes = launchDeferred.await()
                    val searched = searchRes.getOrNull()?.data?.results.orEmpty()
                    val recos = launchRes.getOrNull()?.artistRecos.orEmpty()
                    val combined = if (isAll || langKey == "hindi") {
                        (recos + searched).distinctBy { it.id }
                    } else {
                        (searched + recos).distinctBy { it.id }
                    }
                    val artists = combined.mapNotNull { it.toExploreArtist() }
                    if (artists.isEmpty()) {
                        (searchRes.exceptionOrNull() ?: launchRes.exceptionOrNull())?.let { throw it }
                    }
                    ExploreSearchResults(artists = artists)
                }
            }
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

    /**
     * Resolves a JioSaavn artist detail page from a streaming track's Saavn ID and/or artist name.
     * Prefers structured artist IDs embedded on the song response, falling back to artist search.
     */
    suspend fun resolveArtistDetailForStream(
        saavnSongId: String?,
        artistName: String,
    ): Result<ExploreArtist> = runCatching {
        val url = baseUrl()
        val cleanName = artistName.trim()

        val idFromSong = if (!saavnSongId.isNullOrBlank()) {
            runCatching {
                val songDto = api.getSongById(url, saavnSongId, includeLyrics = false).data?.firstOrNull()
                val candidates = (songDto?.artists?.primary.orEmpty() + songDto?.artists?.all.orEmpty())
                    .filter { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() }
                val exact = candidates.firstOrNull {
                    cleanName.isNotBlank() && decodeSaavnHtml(it.name).equals(cleanName, ignoreCase = true)
                }
                val partial = candidates.firstOrNull {
                    cleanName.isNotBlank() && decodeSaavnHtml(it.name).contains(cleanName, ignoreCase = true)
                }
                (exact ?: partial ?: candidates.firstOrNull())?.id
            }.getOrNull()
        } else {
            null
        }

        val resolvedId = idFromSong ?: run {
            require(cleanName.isNotBlank()) { "Artist name unavailable" }
            val artists = api.searchArtists(url, cleanName, 0, 5).data?.results
                .orEmpty()
                .mapNotNull { it.toExploreArtist() }
            val exact = artists.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
            val partial = artists.firstOrNull { it.name.contains(cleanName, ignoreCase = true) }
            (exact ?: partial ?: artists.firstOrNull())?.id
                ?: throw IllegalStateException("No JioSaavn artist found for \"$cleanName\"")
        }

        getArtistDetail(resolvedId).getOrThrow()
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

    private fun <T> interleave(first: List<T>, second: List<T>): List<T> {
        val result = ArrayList<T>(first.size + second.size)
        val maxLen = maxOf(first.size, second.size)
        for (i in 0 until maxLen) {
            if (i < first.size) result.add(first[i])
            if (i < second.size) result.add(second[i])
        }
        return result
    }

    private fun <T> rotateList(items: List<T>, page: Int, limit: Int): List<T> {
        if (items.isEmpty() || limit <= 0) return emptyList()
        if (items.size <= limit || page <= 0) return items.take(limit)
        val offset = ((page * 4) % items.size).coerceAtLeast(0)
        return (items.drop(offset) + items.take(offset)).take(limit)
    }

    private fun pickThumbnailUrl(images: List<SaavnImageDto>?): String? {
        if (images.isNullOrEmpty()) return null
        return images.firstOrNull { it.quality?.contains("150") == true && !it.url.isNullOrBlank() }?.url
            ?: pickBestImageUrl(images)
    }
}
