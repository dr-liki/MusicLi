package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import android.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KuGouLyricsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.KUGOU

    override suspend fun search(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): List<LyricsProviderCandidate> {
        val cleanTitle = title.trim()
        val cleanArtist = artist?.trim().orEmpty()
        val keyword = listOfNotNull(
            cleanArtist.takeIf { it.isNotBlank() },
            cleanTitle.takeIf { it.isNotBlank() }
        ).joinToString(" - ").ifBlank { cleanTitle }
        if (keyword.isBlank()) return emptyList()

        val searchBuilder = "https://krcs.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "mobi")
            .addQueryParameter("keyword", keyword)
        val durMs = durationMs?.takeIf { it > 0L }
        if (durMs != null) {
            searchBuilder.addQueryParameter("duration", durMs.toString())
        }

        val searchJson = ProviderHttpClient.getJson(okHttpClient, searchBuilder.build().toString())
            ?: return emptyList()
        val candidates = searchJson.optJSONArray("candidates") ?: return emptyList()
        val maxCandidates = minOf(candidates.length(), 4)
        if (maxCandidates == 0) return emptyList()

        return coroutineScope {
            (0 until maxCandidates).map { idx ->
                async {
                    val item = candidates.optJSONObject(idx) ?: return@async null
                    val cid = item.optString("id").takeIf { it.isNotBlank() } ?: return@async null
                    val accessKey = item.optString("accesskey").takeIf { it.isNotBlank() } ?: return@async null
                    val downloadUrl = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
                        .addQueryParameter("ver", "1")
                        .addQueryParameter("client", "pc")
                        .addQueryParameter("id", cid)
                        .addQueryParameter("accesskey", accessKey)
                        .addQueryParameter("fmt", "lrc")
                        .addQueryParameter("charset", "utf8")
                        .build()
                        .toString()

                    val dlJson = ProviderHttpClient.getJson(okHttpClient, downloadUrl) ?: return@async null
                    val encoded = dlJson.optString("content").takeIf { it.isNotBlank() } ?: return@async null
                    val decodedLrc = runCatching {
                        String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@async null

                    val songTitle = item.optString("song").ifBlank { cleanTitle }
                    val singer = item.optString("singer").ifBlank { cleanArtist }
                    val itemDurMs = item.optLong("duration", -1L)
                    val durSec = itemDurMs.takeIf { it > 0L }?.div(1000.0)

                    LyricsProviderCandidate(
                        externalId = cid,
                        providerId = id,
                        title = songTitle,
                        artistName = singer,
                        albumName = "",
                        durationSec = durSec,
                        rawLyrics = decodedLrc,
                        hasSyncedContent = decodedLrc.contains('['),
                        hasWordSync = false,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }
}
