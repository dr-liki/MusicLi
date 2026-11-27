package com.drliki.MusicLi.data.explore

import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ExploreEndpointKind {
    DIRECT_JIOSAAVN,
    FALLBACK_MIRROR,
    CUSTOM_MIRROR,
}

data class ExploreConnectionDiagnostics(
    val configuredUrl: String = UserPreferencesRepository.DEFAULT_EXPLORE_API_BASE_URL,
    val lastUsedUrl: String = UserPreferencesRepository.DEFAULT_EXPLORE_API_BASE_URL,
    val endpointKind: ExploreEndpointKind = ExploreEndpointKind.DIRECT_JIOSAAVN,
    val usedFallback: Boolean = false,
    val lastFallbackReason: String? = null,
    val lastSuccessAtEpochMs: Long = 0L,
)

data class ExploreConnectionTestResult(
    val testedUrl: String,
    val reachable: Boolean,
    val latencyMs: Long,
    val resultCount: Int,
    val realEnglishTrackCount: Int,
    val karaokeOnly: Boolean,
    val sampleTitles: List<String>,
    val errorMessage: String? = null,
)

@Singleton
class JioSaavnApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private val _diagnostics = MutableStateFlow(ExploreConnectionDiagnostics())
    val diagnostics: StateFlow<ExploreConnectionDiagnostics> = _diagnostics.asStateFlow()

    @Volatile
    private var allowMirrorFallback: Boolean = true

    private val odSkylerHosts = ConcurrentHashMap.newKeySet<String>()

    private val exploreHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2))
            .retryOnConnectionFailure(true)
            .dns { hostname ->
                val addresses = Dns.SYSTEM.lookup(hostname)
                val ipv4 = addresses.filterIsInstance<Inet4Address>()
                val ipv6 = addresses.filterIsInstance<Inet6Address>()
                if (ipv4.isNotEmpty()) ipv4 + ipv6 else addresses
            }
            .build()
    }

    fun setAllowMirrorFallback(enabled: Boolean) {
        allowMirrorFallback = enabled
    }

    suspend fun testConnection(baseUrl: String): ExploreConnectionTestResult {
        val cleanUrl = UserPreferencesRepository.normalizeExploreApiBaseUrl(baseUrl)
        val started = System.currentTimeMillis()
        return runCatching {
            val response = searchSongsDirect(
                activeUrl = cleanUrl,
                query = "Coldplay Yellow",
                page = 0,
                limit = 10,
            )
            val latency = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            val songs = response.data?.results.orEmpty()
            val decodedTitles = songs.map { decodeSaavnHtml(it.name) }.filter { it.isNotBlank() }
            val realEnglish = songs.count { dto ->
                val title = decodeSaavnHtml(dto.name).lowercase()
                val isKaraoke = title.contains("karaoke") || title.contains("instrumental") || title.contains("piano version")
                val lang = dto.language.orEmpty().lowercase()
                !isKaraoke && (lang == "english" || title == "yellow")
            }
            ExploreConnectionTestResult(
                testedUrl = cleanUrl,
                reachable = true,
                latencyMs = latency,
                resultCount = songs.size,
                realEnglishTrackCount = realEnglish,
                karaokeOnly = songs.isNotEmpty() && realEnglish == 0,
                sampleTitles = decodedTitles.take(3),
            )
        }.getOrElse { err ->
            ExploreConnectionTestResult(
                testedUrl = cleanUrl,
                reachable = false,
                latencyMs = (System.currentTimeMillis() - started).coerceAtLeast(1L),
                resultCount = 0,
                realEnglishTrackCount = 0,
                karaokeOnly = false,
                sampleTitles = emptyList(),
                errorMessage = err.message ?: err.javaClass.simpleName,
            )
        }
    }

    suspend fun globalSearch(baseUrl: String, query: String): SaavnGlobalSearchResponse =
        executeWithFallback(baseUrl) { activeUrl ->
            if (isNativeSaavnApiUrl(activeUrl) || isOdSkylerMirror(activeUrl)) {
                val songs = searchSongsDirect(activeUrl, query, page = 0, limit = 10).data?.results.orEmpty()
                val albums = searchAlbumsDirect(activeUrl, query, page = 0, limit = 6).data?.results.orEmpty()
                SaavnGlobalSearchResponse(
                    success = songs.isNotEmpty() || albums.isNotEmpty(),
                    data = SaavnGlobalSearchResponse.Data(
                        songs = SaavnGlobalSearchResponse.Data.Songs(
                            results = songs.map { s ->
                                SaavnGlobalSearchResponse.Data.SongItemDto(
                                    id = s.id,
                                    title = s.name,
                                    image = s.image,
                                    album = s.album?.name,
                                    url = s.url,
                                    type = s.type,
                                    primaryArtists = s.artists?.primary.orEmpty().mapNotNull { it.name }.joinToString(", "),
                                    language = s.language,
                                )
                            }
                        ),
                        albums = SaavnGlobalSearchResponse.Data.Albums(
                            results = albums.map { a ->
                                SaavnGlobalSearchResponse.Data.AlbumItemDto(
                                    id = a.id,
                                    title = a.name,
                                    image = a.image,
                                    artist = a.artists?.primary.orEmpty().mapNotNull { it.name }.joinToString(", "),
                                    url = a.url,
                                    year = a.year?.toString(),
                                    language = a.language,
                                )
                            }
                        ),
                    ),
                )
            } else {
                getWrapper(activeUrl, "search", mapOf("query" to query))
            }
        }

    suspend fun searchSongs(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 25,
    ): SaavnSongSearchResponse = executeWithFallback(baseUrl) { activeUrl ->
        searchSongsDirect(activeUrl, query, page, limit)
    }

    suspend fun searchAlbums(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 20,
    ): SaavnAlbumsSearchResponse = executeWithFallback(baseUrl) { activeUrl ->
        searchAlbumsDirect(activeUrl, query, page, limit)
    }

    suspend fun searchArtists(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 20,
    ): SaavnArtistsSearchResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams(
                    "search.getArtistResults",
                    mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                ),
            )
            SaavnNativeParser.parseArtistsSearchResponse(raw)
        } else {
            executeRestWithOdSkylerAutoDetect(
                baseUrl = activeUrl,
                saavnDevBlock = {
                    getWrapper<SaavnArtistsSearchResponse>(
                        activeUrl,
                        "search/artists",
                        mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
                    )
                },
                odSkylerBlock = {
                    val raw = getRaw(
                        buildRestPath(activeUrl, "artists"),
                        mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                        isNative = false,
                    )
                    SaavnNativeParser.parseArtistsSearchResponse(raw)
                },
            )
        }
    }

    suspend fun searchPlaylists(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 20,
    ): SaavnPlaylistsSearchResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams(
                    "search.getPlaylistResults",
                    mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                ),
            )
            SaavnNativeParser.parsePlaylistsSearchResponse(raw)
        } else {
            executeRestWithOdSkylerAutoDetect(
                baseUrl = activeUrl,
                saavnDevBlock = {
                    getWrapper<SaavnPlaylistsSearchResponse>(
                        activeUrl,
                        "search/playlists",
                        mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
                    )
                },
                odSkylerBlock = {
                    val raw = getRaw(
                        buildRestPath(activeUrl, "playlists"),
                        mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                        isNative = false,
                    )
                    SaavnNativeParser.parsePlaylistsSearchResponse(raw)
                },
            )
        }
    }

    internal suspend fun getLaunchData(
        baseUrl: String,
        languageCookie: String = "english,hindi",
    ): SaavnLaunchData = executeWithFallback(baseUrl) { activeUrl ->
        when {
            isNativeSaavnApiUrl(activeUrl) -> {
                val raw = getRaw(
                    targetUrl = activeUrl,
                    params = nativeParams("webapi.getLaunchData"),
                    languageCookie = languageCookie,
                )
                SaavnNativeParser.parseLaunchData(raw)
            }
            isOdSkylerMirror(activeUrl) -> {
                val raw = getRaw(
                    targetUrl = buildRestPath(activeUrl, "home"),
                    params = mapOf("lang" to languageCookie),
                    isNative = false,
                )
                SaavnNativeParser.parseLaunchData(raw)
            }
            else -> SaavnLaunchData()
        }
    }

    internal suspend fun getNewReleases(
        baseUrl: String,
        languageCookie: String = "english,hindi",
        page: Int = 0,
        limit: Int = 30,
    ): SaavnNewReleasesResult = executeWithFallback(baseUrl) { activeUrl ->
        when {
            isNativeSaavnApiUrl(activeUrl) -> {
                val raw = getRaw(
                    targetUrl = activeUrl,
                    params = nativeParams(
                        "content.getAlbums",
                        mapOf(
                            "n" to limit.toString(),
                            "p" to (page + 1).coerceAtLeast(1).toString(),
                            "languages" to languageCookie,
                        ),
                    ),
                    languageCookie = languageCookie,
                )
                SaavnNativeParser.parseNewReleases(raw)
            }
            isOdSkylerMirror(activeUrl) -> {
                val raw = getRaw(
                    targetUrl = buildRestPath(activeUrl, "new"),
                    params = mapOf("lang" to languageCookie, "page" to (page + 1).coerceAtLeast(1).toString()),
                    isNative = false,
                )
                SaavnNativeParser.parseNewReleases(raw)
            }
            else -> SaavnNewReleasesResult()
        }
    }

    suspend fun getTrendingSongs(
        baseUrl: String,
        language: String,
        fallbackQuery: String,
        page: Int = 0,
        limit: Int = 20,
    ): SaavnSongSearchResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams(
                    "content.getTrending",
                    mapOf("entity_type" to "song", "entity_language" to language.lowercase().trim()),
                ),
            )
            val parsed = SaavnNativeParser.parseTrendingSongs(raw)
            val results = rotatePage(parsed.data?.results.orEmpty(), page, limit)
            if (results.isNotEmpty()) {
                SaavnSongSearchResponse(
                    success = true,
                    data = SaavnSongSearchResponse.Data(total = results.size, start = 0, results = results),
                )
            } else {
                searchSongsDirect(activeUrl, fallbackQuery, page, limit)
            }
        } else {
            searchSongsDirect(activeUrl, fallbackQuery, page, limit)
        }
    }

    suspend fun getTrendingAlbums(
        baseUrl: String,
        language: String,
        fallbackQuery: String,
        page: Int = 0,
        limit: Int = 15,
    ): SaavnAlbumsSearchResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams(
                    "content.getTrending",
                    mapOf("entity_type" to "album", "entity_language" to language.lowercase().trim()),
                ),
            )
            val parsed = SaavnNativeParser.parseTrendingAlbums(raw)
            val results = rotatePage(parsed.data?.results.orEmpty(), page, limit)
            if (results.isNotEmpty()) {
                SaavnAlbumsSearchResponse(
                    success = true,
                    data = SaavnAlbumsSearchResponse.Data(total = results.size, start = 0, results = results),
                )
            } else {
                searchAlbumsDirect(activeUrl, fallbackQuery, page, limit)
            }
        } else {
            searchAlbumsDirect(activeUrl, fallbackQuery, page, limit)
        }
    }

    suspend fun getTrendingPlaylists(
        baseUrl: String,
        language: String,
        fallbackQuery: String,
        page: Int = 0,
        limit: Int = 15,
    ): SaavnPlaylistsSearchResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams(
                    "content.getTrending",
                    mapOf("entity_type" to "playlist", "entity_language" to language.lowercase().trim()),
                ),
            )
            val parsed = SaavnNativeParser.parseTrendingPlaylists(raw)
            val results = rotatePage(parsed.data?.results.orEmpty(), page, limit)
            if (results.isNotEmpty()) {
                SaavnPlaylistsSearchResponse(
                    success = true,
                    data = SaavnPlaylistsSearchResponse.Data(total = results.size, start = 0, results = results),
                )
            } else {
                val searchRaw = getRaw(
                    activeUrl,
                    nativeParams(
                        "search.getPlaylistResults",
                        mapOf("q" to fallbackQuery, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                    ),
                )
                SaavnNativeParser.parsePlaylistsSearchResponse(searchRaw)
            }
        } else if (isOdSkylerMirror(activeUrl)) {
            val raw = getRaw(
                buildRestPath(activeUrl, "playlists"),
                mapOf("q" to fallbackQuery, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                isNative = false,
            )
            SaavnNativeParser.parsePlaylistsSearchResponse(raw)
        } else {
            getWrapper(activeUrl, "search/playlists", mapOf("query" to fallbackQuery, "page" to page.toString(), "limit" to limit.toString()))
        }
    }

    suspend fun getSongById(
        baseUrl: String,
        songId: String,
        includeLyrics: Boolean = true,
    ): SaavnSongResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams("song.getDetails", mapOf("pids" to songId)),
            )
            val parsed = SaavnNativeParser.parseSongResponse(raw)
            if (parsed.data.isNullOrEmpty()) {
                throw IllegalStateException("Song details unavailable from direct JioSaavn API")
            }
            parsed
        } else if (isOdSkylerMirror(activeUrl)) {
            val token = SaavnNativeParser.resolveToken(songId)
            val raw = getRaw(buildRestPath(activeUrl, "song"), mapOf("token" to token), isNative = false)
            SaavnNativeParser.parseSongResponse(raw)
        } else {
            getWrapper(activeUrl, "songs/$songId", mapOf("lyrics" to includeLyrics.toString()))
        }
    }

    suspend fun getSongsByIds(
        baseUrl: String,
        songIds: List<String>,
    ): SaavnSongResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams("song.getDetails", mapOf("pids" to songIds.joinToString(","))),
            )
            SaavnNativeParser.parseSongResponse(raw)
        } else if (isOdSkylerMirror(activeUrl)) {
            val songs = songIds.mapNotNull { id ->
                runCatching {
                    val token = SaavnNativeParser.resolveToken(id)
                    val raw = getRaw(buildRestPath(activeUrl, "song"), mapOf("token" to token), isNative = false)
                    SaavnNativeParser.parseSongResponse(raw).data?.firstOrNull()
                }.getOrNull()
            }
            SaavnSongResponse(success = songs.isNotEmpty(), data = songs)
        } else {
            getWrapper(activeUrl, "songs", mapOf("ids" to songIds.joinToString(",")))
        }
    }

    suspend fun getLyricsById(
        baseUrl: String,
        songId: String,
    ): SaavnLyricsResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                mapOf(
                    "__call" to "lyrics.getLyrics",
                    "_format" to "json",
                    "_marker" to "0",
                    "ctx" to "web6dot0",
                    "lyrics_id" to songId,
                ),
            )
            SaavnNativeParser.parseLyricsResponse(raw)
        } else {
            getWrapper(activeUrl, "songs/$songId/lyrics")
        }
    }

    suspend fun getSongSuggestions(
        baseUrl: String,
        songId: String,
        limit: Int = 15,
    ): SaavnSongResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams("reco.getreco", mapOf("pid" to songId, "n" to limit.toString())),
            )
            SaavnNativeParser.parseSongResponse("{\"songs\":$raw}")
        } else if (isOdSkylerMirror(activeUrl)) {
            SaavnSongResponse(success = false, data = emptyList())
        } else {
            getWrapper(activeUrl, "songs/$songId/suggestions", mapOf("limit" to limit.toString()))
        }
    }

    suspend fun getAlbumById(baseUrl: String, albumId: String): SaavnAlbumDetailResponse =
        executeWithFallback(baseUrl) { activeUrl ->
            if (isNativeSaavnApiUrl(activeUrl)) {
                val raw = getRaw(
                    activeUrl,
                    mapOf(
                        "__call" to "content.getAlbumDetails",
                        "_format" to "json",
                        "albumid" to albumId,
                    ),
                )
                val parsed = SaavnNativeParser.parseAlbumDetailResponse(raw)
                if (parsed.data?.songs.isNullOrEmpty()) {
                    throw IllegalStateException("Album details unavailable from direct JioSaavn API")
                }
                parsed
            } else if (isOdSkylerMirror(activeUrl)) {
                val token = SaavnNativeParser.resolveToken(albumId)
                val raw = getRaw(buildRestPath(activeUrl, "album"), mapOf("token" to token), isNative = false)
                SaavnNativeParser.parseAlbumDetailResponse(raw)
            } else {
                getWrapper(activeUrl, "albums", mapOf("id" to albumId))
            }
        }

    suspend fun getPlaylistById(
        baseUrl: String,
        playlistId: String,
        page: Int = 0,
        limit: Int = 100,
    ): SaavnPlaylistDetailResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams(
                    "playlist.getDetails",
                    mapOf(
                        "listid" to playlistId,
                        "p" to (page + 1).coerceAtLeast(1).toString(),
                        "n" to limit.toString(),
                    ),
                ),
            )
            val parsed = SaavnNativeParser.parsePlaylistDetailResponse(raw)
            if (parsed.data?.songs.isNullOrEmpty()) {
                throw IllegalStateException("Playlist details unavailable from direct JioSaavn API")
            }
            parsed
        } else if (isOdSkylerMirror(activeUrl)) {
            val token = SaavnNativeParser.resolveToken(playlistId)
            val raw = getRaw(
                buildRestPath(activeUrl, "playlist"),
                mapOf("token" to token, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                isNative = false,
            )
            SaavnNativeParser.parsePlaylistDetailResponse(raw)
        } else {
            getWrapper(activeUrl, "playlists", mapOf("id" to playlistId, "page" to page.toString(), "limit" to limit.toString()))
        }
    }

    suspend fun getArtistById(
        baseUrl: String,
        artistId: String,
        songCount: Int = 25,
        albumCount: Int = 15,
    ): SaavnArtistDetailResponse = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                activeUrl,
                nativeParams(
                    "artist.getArtistPageDetails",
                    mapOf(
                        "artistId" to artistId,
                        "n_song" to songCount.toString(),
                        "n_album" to albumCount.toString(),
                    ),
                ),
            )
            val parsed = SaavnNativeParser.parseArtistDetailResponse(raw)
            if (parsed.data == null) {
                throw IllegalStateException("Artist details unavailable from direct JioSaavn API")
            }
            parsed
        } else if (isOdSkylerMirror(activeUrl)) {
            val token = SaavnNativeParser.resolveToken(artistId)
            val raw = getRaw(buildRestPath(activeUrl, "artist"), mapOf("token" to token, "page" to "0"), isNative = false)
            SaavnNativeParser.parseArtistDetailResponse(raw)
        } else {
            getWrapper(
                activeUrl,
                "artists/$artistId",
                mapOf(
                    "page" to "0",
                    "songCount" to songCount.toString(),
                    "albumCount" to albumCount.toString(),
                ),
            )
        }
    }

    private suspend fun searchSongsDirect(
        activeUrl: String,
        query: String,
        page: Int,
        limit: Int,
    ): SaavnSongSearchResponse {
        if (!isNativeSaavnApiUrl(activeUrl)) {
            return executeRestWithOdSkylerAutoDetect(
                baseUrl = activeUrl,
                saavnDevBlock = {
                    getWrapper<SaavnSongSearchResponse>(
                        activeUrl,
                        "search/songs",
                        mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
                    )
                },
                odSkylerBlock = {
                    val raw = getRaw(
                        buildRestPath(activeUrl, "songs"),
                        mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                        isNative = false,
                    )
                    SaavnNativeParser.parseSongSearchResponse(raw)
                },
            )
        }

        val pageStr = (page + 1).coerceAtLeast(1).toString()
        val limitStr = limit.toString()
        val rawWeb = getRaw(
            targetUrl = activeUrl,
            params = nativeParams("search.getResults", mapOf("q" to query, "p" to pageStr, "n" to limitStr)),
            isAjaxSearch = true,
        )
        val parsedWeb = SaavnNativeParser.parseSongSearchResponse(rawWeb)
        if (!parsedWeb.data?.results.isNullOrEmpty()) return parsedWeb

        // Secondary native attempts on the same JioSaavn host (ctx=android + autocomplete.get)
        val rawAndroid = runCatching {
            getRaw(
                targetUrl = activeUrl,
                params = nativeParams(
                    "search.getResults",
                    mapOf("q" to query, "p" to pageStr, "n" to limitStr, "ctx" to "android"),
                ),
                isAjaxSearch = true,
            )
        }.getOrNull()
        val parsedAndroid = rawAndroid?.let(SaavnNativeParser::parseSongSearchResponse)
        if (parsedAndroid != null && !parsedAndroid.data?.results.isNullOrEmpty()) return parsedAndroid

        if (page == 0) {
            val autoIds = runCatching {
                val autoRaw = getRaw(
                    targetUrl = activeUrl,
                    params = mapOf("__call" to "autocomplete.get", "_format" to "json", "_marker" to "0", "ctx" to "web6dot0", "query" to query),
                    isAjaxSearch = true,
                )
                SaavnNativeParser.parseAutocompleteSongIds(autoRaw)
            }.getOrDefault(emptyList())
            if (autoIds.isNotEmpty()) {
                val detailsRaw = runCatching {
                    getRaw(activeUrl, nativeParams("song.getDetails", mapOf("pids" to autoIds.take(limit).joinToString(","))))
                }.getOrNull()
                val hydrated = detailsRaw?.let(SaavnNativeParser::parseSongResponse)?.data.orEmpty()
                if (hydrated.isNotEmpty()) {
                    return SaavnSongSearchResponse(
                        success = true,
                        data = SaavnSongSearchResponse.Data(total = hydrated.size, start = 0, results = hydrated),
                    )
                }
            }
        }
        return parsedWeb
    }

    private suspend fun searchAlbumsDirect(
        activeUrl: String,
        query: String,
        page: Int,
        limit: Int,
    ): SaavnAlbumsSearchResponse = if (isNativeSaavnApiUrl(activeUrl)) {
        val raw = getRaw(
            targetUrl = activeUrl,
            params = nativeParams(
                "search.getAlbumResults",
                mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
            ),
            isAjaxSearch = true,
        )
        SaavnNativeParser.parseAlbumsSearchResponse(raw)
    } else {
        executeRestWithOdSkylerAutoDetect(
            baseUrl = activeUrl,
            saavnDevBlock = {
                getWrapper<SaavnAlbumsSearchResponse>(
                    activeUrl,
                    "search/albums",
                    mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
                )
            },
            odSkylerBlock = {
                val raw = getRaw(
                    buildRestPath(activeUrl, "albums"),
                    mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
                    isNative = false,
                )
                SaavnNativeParser.parseAlbumsSearchResponse(raw)
            },
        )
    }

    private suspend fun <T> executeRestWithOdSkylerAutoDetect(
        baseUrl: String,
        saavnDevBlock: suspend () -> T,
        odSkylerBlock: suspend () -> T,
    ): T {
        val normalized = UserPreferencesRepository.normalizeExploreApiBaseUrl(baseUrl)
        if (odSkylerHosts.contains(normalized)) {
            return odSkylerBlock()
        }
        return try {
            saavnDevBlock()
        } catch (ce: CancellationException) {
            throw ce
        } catch (saavnErr: Throwable) {
            val shouldProbeOdSkyler = !normalized.equals(UserPreferencesRepository.FALLBACK_EXPLORE_API_BASE_URL, ignoreCase = true)
            if (!shouldProbeOdSkyler) throw saavnErr
            try {
                val res = odSkylerBlock()
                odSkylerHosts.add(normalized)
                res
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                throw saavnErr
            }
        }
    }

    private fun isOdSkylerMirror(baseUrl: String): Boolean =
        odSkylerHosts.contains(UserPreferencesRepository.normalizeExploreApiBaseUrl(baseUrl))

    private fun buildRestPath(baseUrl: String, segment: String): String {
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return cleanBase + segment
    }

    private suspend fun <T> executeWithFallback(
        primaryBaseUrl: String,
        block: suspend (baseUrl: String) -> T,
    ): T {
        val normalizedPrimary = UserPreferencesRepository.normalizeExploreApiBaseUrl(primaryBaseUrl)
        val primaryKind = if (isNativeSaavnApiUrl(normalizedPrimary)) {
            ExploreEndpointKind.DIRECT_JIOSAAVN
        } else if (normalizedPrimary.equals(UserPreferencesRepository.FALLBACK_EXPLORE_API_BASE_URL, ignoreCase = true)) {
            ExploreEndpointKind.FALLBACK_MIRROR
        } else {
            ExploreEndpointKind.CUSTOM_MIRROR
        }

        val primaryAttempt: Result<T> = try {
            Result.success(block(primaryBaseUrl))
        } catch (ce: CancellationException) {
            throw ce
        } catch (ioErr: IOException) {
            if (isNativeSaavnApiUrl(normalizedPrimary)) {
                try {
                    Result.success(block(primaryBaseUrl))
                } catch (ce: CancellationException) {
                    throw ce
                } catch (retryErr: Throwable) {
                    Result.failure(retryErr)
                }
            } else {
                Result.failure(ioErr)
            }
        } catch (err: Throwable) {
            Result.failure(err)
        }

        primaryAttempt.onSuccess { value ->
            _diagnostics.value = ExploreConnectionDiagnostics(
                configuredUrl = normalizedPrimary,
                lastUsedUrl = normalizedPrimary,
                endpointKind = primaryKind,
                usedFallback = false,
                lastFallbackReason = null,
                lastSuccessAtEpochMs = System.currentTimeMillis(),
            )
            return value
        }

        val primaryErr = primaryAttempt.exceptionOrNull()!!
        val fallback = UserPreferencesRepository.FALLBACK_EXPLORE_API_BASE_URL
        if (!allowMirrorFallback || normalizedPrimary.equals(fallback, ignoreCase = true)) {
            _diagnostics.value = _diagnostics.value.copy(
                configuredUrl = normalizedPrimary,
                endpointKind = primaryKind,
                usedFallback = false,
                lastFallbackReason = primaryErr.message ?: primaryErr.javaClass.simpleName,
            )
            throw primaryErr
        }

        return try {
            val result = block(fallback)
            _diagnostics.value = ExploreConnectionDiagnostics(
                configuredUrl = normalizedPrimary,
                lastUsedUrl = fallback,
                endpointKind = ExploreEndpointKind.FALLBACK_MIRROR,
                usedFallback = true,
                lastFallbackReason = primaryErr.message ?: primaryErr.javaClass.simpleName,
                lastSuccessAtEpochMs = System.currentTimeMillis(),
            )
            result
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            _diagnostics.value = _diagnostics.value.copy(
                configuredUrl = normalizedPrimary,
                endpointKind = primaryKind,
                usedFallback = false,
                lastFallbackReason = primaryErr.message ?: primaryErr.javaClass.simpleName,
            )
            throw primaryErr
        }
    }

    private suspend inline fun <reified T> getWrapper(
        baseUrl: String,
        path: String,
        params: Map<String, String> = emptyMap(),
    ): T {
        val body = getRaw(buildRestPath(baseUrl, path), params, isNative = false)
        return gson.fromJson(body, T::class.java)
            ?: throw IllegalStateException("Empty JSON response from JioSaavn API")
    }

    private suspend fun getRaw(
        targetUrl: String,
        params: Map<String, String> = emptyMap(),
        isNative: Boolean = isNativeSaavnApiUrl(targetUrl),
        languageCookie: String = "english",
        referer: String = "https://www.jiosaavn.com/",
        isAjaxSearch: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val parsed = targetUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid JioSaavn API URL: $targetUrl")
        val urlBuilder = parsed.newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val userAgent = if (isNative) {
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        } else {
            "MusicLi/1.0"
        }
        val cleanLangCookie = languageCookie.trim().lowercase().ifBlank { "english" }
        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .get()

        if (isNative) {
            requestBuilder
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", referer)
                .header(
                    "Cookie",
                    "DL=english; L=$cleanLangCookie; mm_latlong=19.0760%2C72.8777; geo=19.0760%2C72.8777%2CIN%2CMaharashtra%2CMumbai%2C400001",
                )
            if (isAjaxSearch) {
                requestBuilder.header("X-Requested-With", "XMLHttpRequest")
            }
        }
        val request = requestBuilder.build()

        exploreHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("JioSaavn API error HTTP ${response.code}")
            }
            response.body.string().also { body ->
                if (body.isBlank()) throw IllegalStateException("Empty JSON response from JioSaavn API")
            }
        }
    }

    private fun nativeParams(call: String, extra: Map<String, String> = emptyMap()): Map<String, String> =
        buildMap {
            put("__call", call)
            put("_format", "json")
            put("_marker", "0")
            put("api_version", "4")
            put("ctx", "web6dot0")
            putAll(extra)
        }

    private fun <T> rotatePage(items: List<T>, page: Int, limit: Int): List<T> {
        if (items.isEmpty() || limit <= 0) return emptyList()
        if (items.size <= limit || page <= 0) return items.take(limit)
        val offset = ((page * 5) % items.size).coerceAtLeast(0)
        val rotated = items.drop(offset) + items.take(offset)
        return rotated.take(limit)
    }
}
