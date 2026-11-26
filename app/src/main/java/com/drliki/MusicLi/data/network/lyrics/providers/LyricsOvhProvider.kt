package com.drliki.MusicLi.data.network.lyrics.providers

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsOvhProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.LYRICS_OVH

    override suspend fun search(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): List<LyricsProviderCandidate> {
        val cleanTitle = title.trim()
        val cleanArtist = artist?.trim().orEmpty()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return emptyList()

        val url = "https://api.lyrics.ovh/v1".toHttpUrl().newBuilder()
            .addPathSegment(cleanArtist)
            .addPathSegment(cleanTitle)
            .build()
            .toString()

        val json = ProviderHttpClient.getJson(okHttpClient, url) ?: return emptyList()
        val lyrics = json.optString("lyrics")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: return emptyList()

        return listOf(
            LyricsProviderCandidate(
                externalId = "${cleanArtist.lowercase()}:${cleanTitle.lowercase()}",
                providerId = id,
                title = cleanTitle,
                artistName = cleanArtist,
                albumName = album.orEmpty(),
                durationSec = durationMs?.takeIf { it > 0L }?.div(1000.0),
                rawLyrics = lyrics,
                hasSyncedContent = false,
                hasWordSync = false,
            )
        )
    }
}
