package com.lostf1sh.pixelplayeross.data.explore

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
        get(baseUrl, "search", mapOf("query" to query))

    suspend fun searchSongs(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 25,
    ): SaavnSongSearchResponse = get(
        baseUrl,
        "search/songs",
        mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
    )

    suspend fun searchAlbums(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 20,
    ): SaavnAlbumsSearchResponse = get(
        baseUrl,
        "search/albums",
        mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
    )

    suspend fun searchArtists(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 20,
    ): SaavnArtistsSearchResponse = get(
        baseUrl,
        "search/artists",
        mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
    )

    suspend fun searchPlaylists(
        baseUrl: String,
        query: String,
        page: Int = 0,
        limit: Int = 20,
    ): SaavnPlaylistsSearchResponse = get(
        baseUrl,
        "search/playlists",
        mapOf("query" to query, "page" to page.toString(), "limit" to limit.toString()),
    )

    suspend fun getSongById(
        baseUrl: String,
        songId: String,
        includeLyrics: Boolean = true,
    ): SaavnSongResponse = get(
        baseUrl,
        "songs/$songId",
        mapOf("lyrics" to includeLyrics.toString()),
    )

    suspend fun getSongsByIds(
        baseUrl: String,
        songIds: List<String>,
    ): SaavnSongResponse = get(
        baseUrl,
        "songs",
        mapOf("ids" to songIds.joinToString(",")),
    )

    suspend fun getLyricsById(
        baseUrl: String,
        songId: String,
    ): SaavnLyricsResponse = get(baseUrl, "songs/$songId/lyrics")

    suspend fun getSongSuggestions(
        baseUrl: String,
        songId: String,
        limit: Int = 15,
    ): SaavnSongResponse = get(
        baseUrl,
        "songs/$songId/suggestions",
        mapOf("limit" to limit.toString()),
    )

    suspend fun getAlbumById(baseUrl: String, albumId: String): SaavnAlbumDetailResponse =
        get(baseUrl, "albums", mapOf("id" to albumId))

    suspend fun getPlaylistById(
        baseUrl: String,
        playlistId: String,
        page: Int = 0,
        limit: Int = 100,
    ): SaavnPlaylistDetailResponse = get(
        baseUrl,
        "playlists",
        mapOf("id" to playlistId, "page" to page.toString(), "limit" to limit.toString()),
    )

    suspend fun getArtistById(
        baseUrl: String,
        artistId: String,
        songCount: Int = 25,
        albumCount: Int = 15,
    ): SaavnArtistDetailResponse = get(
        baseUrl,
        "artists/$artistId",
        mapOf(
            "page" to "0",
            "songCount" to songCount.toString(),
            "albumCount" to albumCount.toString(),
        ),
    )

    private suspend inline fun <reified T> get(
        baseUrl: String,
        path: String,
        params: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val parsed = (cleanBase + path).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid JioSaavn API URL: $cleanBase$path")
        val urlBuilder = parsed.newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "PixelPlayerOSS/1.0")
            .header("Accept", "application/json")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("JioSaavn API error HTTP ${response.code}")
            }
            val body = response.body.string()
            gson.fromJson(body, T::class.java)
                ?: throw IllegalStateException("Empty JSON response from JioSaavn API")
        }
    }
}
