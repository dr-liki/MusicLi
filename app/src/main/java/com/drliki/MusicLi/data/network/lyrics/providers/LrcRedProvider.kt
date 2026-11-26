package com.drliki.MusicLi.data.network.lyrics.providers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcRedProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.LRC_RED

    override suspend fun search(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): List<LyricsProviderCandidate> {
        val cleanTitle = title.trim()
        val cleanArtist = artist?.trim().orEmpty()
        val q = listOfNotNull(
            cleanTitle.takeIf { it.isNotBlank() },
            cleanArtist.takeIf { it.isNotBlank() }
        ).joinToString(" ")
        if (q.isBlank()) return emptyList()

        val searchUrl = "https://lrc.red/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .build()
            .toString()

        val searchJson = ProviderHttpClient.getJson(okHttpClient, searchUrl) ?: return emptyList()
        val hits = searchJson.optJSONArray("hits") ?: return emptyList()
        val maxHits = minOf(hits.length(), 4)
        if (maxHits == 0) return emptyList()

        return coroutineScope {
            (0 until maxHits).map { idx ->
                async {
                    val hit = hits.optJSONObject(idx) ?: return@async null
                    val isrc = hit.optString("isrc").takeIf { it.isNotBlank() } ?: return@async null
                    val detailUrl = "https://lrc.red/s/$isrc.json"
                    val detail = ProviderHttpClient.getJson(okHttpClient, detailUrl) ?: return@async null
                    val files = detail.optJSONObject("files") ?: return@async null
                    val ttmlUrl = files.optString("ttml").takeIf { it.isNotBlank() }
                    val lrcUrl = files.optString("lrc").takeIf { it.isNotBlank() }
                    val fileUrl = ttmlUrl ?: lrcUrl ?: return@async null
                    val rawLyrics = ProviderHttpClient.getText(okHttpClient, fileUrl) ?: return@async null

                    val hitTitle = detail.optString("title").ifBlank { hit.optString("title", cleanTitle) }
                    val hitArtist = detail.optString("artist").ifBlank { hit.optString("artist", cleanArtist) }
                    val hitAlbum = detail.optString("album").ifBlank { hit.optString("album") }
                    val hitDur = detail.optDouble("duration", hit.optDouble("duration", -1.0))
                        .takeIf { !it.isNaN() && it > 0.0 }
                    val syncType = detail.optString("sync").lowercase()
                    val pageUrl = detail.optString("page").takeIf { it.isNotBlank() } ?: "https://lrc.red/s/$isrc"

                    LyricsProviderCandidate(
                        externalId = isrc,
                        providerId = id,
                        title = hitTitle,
                        artistName = hitArtist,
                        albumName = hitAlbum,
                        durationSec = hitDur,
                        rawLyrics = rawLyrics,
                        hasSyncedContent = true,
                        hasWordSync = ttmlUrl != null || syncType == "word",
                        attributionUrl = pageUrl,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }
}
