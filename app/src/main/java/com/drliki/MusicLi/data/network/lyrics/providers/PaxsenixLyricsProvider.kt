package com.drliki.MusicLi.data.network.lyrics.providers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaxsenixLyricsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.PAXSENIX

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

        val tracks = findAppleMusicTracks(query).ifEmpty { return emptyList() }.take(3)

        return coroutineScope {
            tracks.map { track ->
                async {
                    val lyricsUrl = "https://lyrics.paxsenix.org/apple-music/lyrics".toHttpUrl().newBuilder()
                        .addQueryParameter("id", track.trackId)
                        .addQueryParameter("ttml", "true")
                        .addQueryParameter("v", "2")
                        .build()
                        .toString()

                    val body = ProviderHttpClient.getText(okHttpClient, lyricsUrl) ?: return@async null
                    val (rawLyrics, hasSynced, hasWordSync) = parsePaxsenixPayload(body) ?: return@async null

                    LyricsProviderCandidate(
                        externalId = track.trackId,
                        providerId = id,
                        title = track.title,
                        artistName = track.artist,
                        albumName = track.album,
                        durationSec = track.durationSec,
                        rawLyrics = rawLyrics,
                        hasSyncedContent = hasSynced,
                        hasWordSync = hasWordSync,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    internal data class TrackStub(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Double?,
    )

    private suspend fun findAppleMusicTracks(query: String): List<TrackStub> {
        val searchUrl = "https://lyrics.paxsenix.org/apple-music/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
            .toString()
        val paxBody = ProviderHttpClient.getText(okHttpClient, searchUrl)
        val paxTracks = paxBody?.let(::parseAppleMusicSearchResults).orEmpty()
        if (paxTracks.isNotEmpty()) return paxTracks

        val itunesUrl = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "4")
            .build()
            .toString()
        val itunesJson = ProviderHttpClient.getJson(okHttpClient, itunesUrl) ?: return emptyList()
        val results = itunesJson.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val trackId = item.optLong("trackId", 0L).takeIf { it > 0L }?.toString() ?: continue
                val title = item.optString("trackName").takeIf { it.isNotBlank() } ?: continue
                val artist = item.optString("artistName").ifBlank { "Unknown" }
                val album = item.optString("collectionName")
                val dur = item.optDouble("trackTimeMillis", -1.0).takeIf { !it.isNaN() && it > 0.0 }?.div(1000.0)
                add(TrackStub(trackId, title, artist, album, dur))
            }
        }
    }

    companion object {
        internal fun parseAppleMusicSearchResults(body: String): List<TrackStub> {
            val trimmed = body.trim()
            val array: JSONArray = when {
                trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
                trimmed.startsWith("{") -> {
                    val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
                    obj.optJSONArray("results")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("tracks")
                        ?: return emptyList()
                }
                else -> return emptyList()
            }
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").ifBlank { item.optLong("trackId", 0L).takeIf { it > 0 }?.toString().orEmpty() }
                    if (id.isBlank()) continue
                    val name = item.optString("name").ifBlank { item.optString("trackName") }
                    if (name.isBlank()) continue
                    val artist = item.optString("artistName").ifBlank { item.optString("artist") }
                    val album = item.optString("albumName").ifBlank { item.optString("collectionName") }
                    val durMs = item.optDouble("duration", -1.0).let { d ->
                        when {
                            d.isNaN() || d <= 0.0 -> item.optDouble("trackTimeMillis", -1.0)
                            d < 1000.0 -> d * 1000.0
                            else -> d
                        }
                    }
                    val durSec = durMs.takeIf { !it.isNaN() && it > 0.0 }?.div(1000.0)
                    add(TrackStub(id, name, artist, album, durSec))
                }
            }
        }

        internal fun parsePaxsenixPayload(body: String): Triple<String, Boolean, Boolean>? {
            val trimmed = body.trim()
            if (trimmed.isBlank()) return null

            if (trimmed.startsWith("<")) {
                return Triple(trimmed, true, trimmed.contains("<span", ignoreCase = true))
            }

            if (trimmed.startsWith("{")) {
                val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
                val ttml = obj.optString("ttml").takeIf { it.isNotBlank() }
                if (ttml != null) {
                    return Triple(ttml, true, ttml.contains("<span", ignoreCase = true))
                }
                val lrc = obj.optString("lrc").takeIf { it.isNotBlank() }
                if (lrc != null) {
                    return Triple(lrc, true, lrc.contains("<"))
                }
                val content = obj.optJSONArray("content") ?: obj.optJSONArray("lyrics")
                if (content != null) {
                    val lrcFromArray = jsonLinesToLrc(content)
                    if (lrcFromArray != null) {
                        return Triple(lrcFromArray, true, lrcFromArray.contains("<"))
                    }
                }
                val plain = obj.optString("lyrics").takeIf { it.isNotBlank() && !it.startsWith("[") }
                if (plain != null) {
                    return Triple(plain, false, false)
                }
            } else if (trimmed.startsWith("[")) {
                val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return null
                val lrc = jsonLinesToLrc(arr) ?: return null
                return Triple(lrc, true, lrc.contains("<"))
            }

            return null
        }

        private fun jsonLinesToLrc(array: JSONArray): String? {
            val sb = StringBuilder()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val ms = obj.optLong("timestamp", obj.optLong("time", -1L))
                val lineText = obj.optString("text")
                    .ifBlank { obj.optString("line") }
                    .ifBlank { obj.optString("words") }
                if (ms < 0L || lineText.isBlank()) continue
                sb.append('[').append(formatLrcMs(ms)).append(']').append(lineText.trim()).append('\n')
            }
            return sb.toString().trim().takeIf { it.isNotBlank() }
        }

        private fun formatLrcMs(ms: Long): String {
            val clamped = ms.coerceAtLeast(0L)
            val totalSeconds = clamped / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            val centis = (clamped % 1000L) / 10L
            return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centis)
        }
    }
}
