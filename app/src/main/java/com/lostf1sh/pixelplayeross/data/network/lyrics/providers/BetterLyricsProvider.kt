package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BetterLyricsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.BETTER_LYRICS

    override suspend fun search(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): List<LyricsProviderCandidate> {
        val cleanTitle = title.trim()
        val cleanArtist = artist?.trim().orEmpty()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return emptyList()

        val urlBuilder = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", cleanTitle)
            .addQueryParameter("a", cleanArtist)
        if (!album.isNullOrBlank()) {
            urlBuilder.addQueryParameter("al", album.trim())
        }
        val durationSec = durationMs?.let { (it / 1000L).toInt() }?.takeIf { it > 0 }
        if (durationSec != null) {
            urlBuilder.addQueryParameter("d", durationSec.toString())
        }

        val json = ProviderHttpClient.getJson(okHttpClient, urlBuilder.build().toString()) ?: return emptyList()
        val ttml = json.optString("ttml").takeIf { it.isNotBlank() } ?: return emptyList()

        return listOf(
            LyricsProviderCandidate(
                externalId = "${cleanArtist.lowercase()}:${cleanTitle.lowercase()}",
                providerId = id,
                title = cleanTitle,
                artistName = cleanArtist,
                albumName = album.orEmpty(),
                durationSec = durationSec?.toDouble(),
                rawLyrics = ttml,
                hasSyncedContent = true,
                hasWordSync = ttml.contains("<span", ignoreCase = true),
            )
        )
    }
}
