package com.drliki.MusicLi.data.network.lyrics.providers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetEaseLyricsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.NETEASE

    override suspend fun search(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): List<LyricsProviderCandidate> {
        val cleanTitle = title.trim()
        val cleanArtist = artist?.trim().orEmpty()
        val query = listOfNotNull(
            cleanTitle.takeIf { it.isNotBlank() },
            cleanArtist.takeIf { it.isNotBlank() }
        ).joinToString(" ")
        if (query.isBlank()) return emptyList()

        val headers = mapOf("Referer" to "https://music.163.com")
        val searchUrl = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "4")
            .build()
            .toString()

        val searchJson = ProviderHttpClient.getJson(okHttpClient, searchUrl, headers) ?: return emptyList()
        val songs = searchJson.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val maxSongs = minOf(songs.length(), 4)
        if (maxSongs == 0) return emptyList()

        return coroutineScope {
            (0 until maxSongs).map { idx ->
                async {
                    val songObj = songs.optJSONObject(idx) ?: return@async null
                    val songId = songObj.optLong("id", 0L).takeIf { it > 0L } ?: return@async null

                    val lyricUrl = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
                        .addQueryParameter("os", "pc")
                        .addQueryParameter("id", songId.toString())
                        .addQueryParameter("lv", "-1")
                        .addQueryParameter("tv", "-1")
                        .build()
                        .toString()

                    val lyricJson = ProviderHttpClient.getJson(okHttpClient, lyricUrl, headers) ?: return@async null
                    val rawLrc = lyricJson.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
                        ?: return@async null

                    val trackTitle = songObj.optString("name").ifBlank { cleanTitle }
                    val artistsArr = songObj.optJSONArray("artists")
                    val trackArtist = buildList {
                        if (artistsArr != null) {
                            for (a in 0 until artistsArr.length()) {
                                artistsArr.optJSONObject(a)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }.joinToString(", ").ifBlank { cleanArtist }
                    val trackAlbum = songObj.optJSONObject("album")?.optString("name").orEmpty()
                    val durSec = songObj.optLong("duration", -1L).takeIf { it > 0L }?.div(1000.0)

                    LyricsProviderCandidate(
                        externalId = songId.toString(),
                        providerId = id,
                        title = trackTitle,
                        artistName = trackArtist,
                        albumName = trackAlbum,
                        durationSec = durSec,
                        rawLyrics = rawLrc,
                        hasSyncedContent = rawLrc.contains('['),
                        hasWordSync = false,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }
}
