package com.drliki.MusicLi.data.explore

import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    suspend fun globalSearch(baseUrl: String, query: String): SaavnGlobalSearchResponse =
        executeWithFallback(baseUrl) { activeUrl ->
            if (isNativeSaavnApiUrl(activeUrl)) {
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
            getWrapper(activeUrl, "search/artists", mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()))
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
            getWrapper(activeUrl, "search/playlists", mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()))
        }
    }

    internal suspend fun getLaunchData(
        baseUrl: String,
        languageCookie: String = "english,hindi",
    ): SaavnLaunchData = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
            val raw = getRaw(
                targetUrl = activeUrl,
                params = nativeParams("webapi.getLaunchData"),
                languageCookie = languageCookie,
            )
            SaavnNativeParser.parseLaunchData(raw)
        } else {
            SaavnLaunchData()
        }
    }

    internal suspend fun getNewReleases(
        baseUrl: String,
        languageCookie: String = "english,hindi",
        page: Int = 0,
        limit: Int = 30,
    ): SaavnNewReleasesResult = executeWithFallback(baseUrl) { activeUrl ->
        if (isNativeSaavnApiUrl(activeUrl)) {
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
        } else {
            SaavnNewReleasesResult()
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
            getWrapper(activeUrl, "search/songs", mapOf("query" to fallbackQuery, "page" to page.toString(), "limit" to limit.toString()))
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
            getWrapper(activeUrl, "search/albums", mapOf("query" to fallbackQuery, "page" to page.toString(), "limit" to limit.toString()))
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
    ): SaavnSongSearchResponse = if (isNativeSaavnApiUrl(activeUrl)) {
        val raw = getRaw(
            targetUrl = activeUrl,
            params = nativeParams(
                "search.getResults",
                mapOf("q" to query, "p" to (page + 1).coerceAtLeast(1).toString(), "n" to limit.toString()),
            ),
            isAjaxSearch = true,
        )
        SaavnNativeParser.parseSongSearchResponse(raw)
    } else {
        getWrapper(activeUrl, "search/songs", mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()))
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
        getWrapper(activeUrl, "search/albums", mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()))
    }

    private suspend fun <T> executeWithFallback(
        primaryBaseUrl: String,
        block: suspend (baseUrl: String) -> T,
    ): T {
        return try {
            block(primaryBaseUrl)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (primaryErr: Throwable) {
            val fallback = UserPreferencesRepository.FALLBACK_EXPLORE_API_BASE_URL
            val normalizedPrimary = UserPreferencesRepository.normalizeExploreApiBaseUrl(primaryBaseUrl)
            if (normalizedPrimary.equals(fallback, ignoreCase = true)) {
                throw primaryErr
            }
            try {
                block(fallback)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                throw primaryErr
            }
        }
    }

    private suspend inline fun <reified T> getWrapper(
        baseUrl: String,
        path: String,
        params: Map<String, String> = emptyMap(),
    ): T {
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val body = getRaw(cleanBase + path, params, isNative = false)
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

        okHttpClient.newCall(request).execute().use { response ->
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
