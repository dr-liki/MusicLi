package com.lostf1sh.pixelplayeross.data.video

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncedVideoRepo"
private const val CACHE_EXPIRY_MS = 3 * 60 * 60 * 1000L // 3 hours
private const val OFFSET_PREFS_NAME = "synced_video_offsets"
private const val OVERRIDE_PREFS_NAME = "synced_video_overrides"

@Singleton
class SyncedVideoRepository @Inject constructor(
    private val streamExtractor: YouTubeStreamExtractor,
    private val http: OkHttpClient,
    @ApplicationContext private val appContext: Context,
) {
    private val cache = ConcurrentHashMap<String, CachedVideoBundle>()
    private val savedOffsets by lazy { appContext.getSharedPreferences(OFFSET_PREFS_NAME, Context.MODE_PRIVATE) }
    private val savedOverrides by lazy { appContext.getSharedPreferences(OVERRIDE_PREFS_NAME, Context.MODE_PRIVATE) }

    private data class CachedVideoBundle(
        val bundle: SyncedVideoStreamBundle,
        val expiresAtMs: Long,
    )

    fun invalidate(videoId: String?) {
        if (!videoId.isNullOrBlank()) {
            cache.entries.removeIf { (key, cached) -> key == videoId || cached.bundle.videoId == videoId }
        }
    }

    fun savedVideoOverride(title: String, artist: String): String? {
        val key = videoOverrideKey(title, artist)
        return savedOverrides.getString(key, null)
    }

    fun saveVideoOverride(title: String, artist: String, videoId: String) {
        val key = videoOverrideKey(title, artist)
        savedOverrides.edit().putString(key, videoId).apply()
        invalidate(videoId)
    }

    fun clearVideoOverride(title: String, artist: String) {
        val key = videoOverrideKey(title, artist)
        val old = savedOverrides.getString(key, null)
        savedOverrides.edit().remove(key).apply()
        invalidate(old)
    }

    fun videoOverrideKey(title: String, artist: String): String =
        "${TextMatch.normalize(title)}|${TextMatch.normalize(artist)}"

    suspend fun resolveVideo(
        trackTitle: String,
        trackArtist: String,
        genre: String? = null,
        expectedDurationMs: Long? = null,
        selectedVideoId: String? = null,
        forceRefresh: Boolean = false,
    ): Result<SyncedVideoStreamBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val userOverride = selectedVideoId?.takeIf { it.isNotBlank() } ?: savedVideoOverride(trackTitle, trackArtist)
            val targetVideoId = userOverride
                ?: findOfficialVideoId(
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    expectedDurationMs = expectedDurationMs,
                )
                ?: findFallbackVideoId(
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    genre = genre,
                    expectedDurationMs = expectedDurationMs,
                )
                ?: throw IllegalStateException("No video found for $trackTitle — $trackArtist")

            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                cache[targetVideoId]?.let { cached ->
                    if (cached.expiresAtMs > now) {
                        return@runCatching cached.bundle
                    } else {
                        cache.remove(targetVideoId)
                    }
                }
            } else {
                cache.remove(targetVideoId)
            }

            val bundle = coroutineScope {
                val introOffsetMs = async { fetchIntroOffsetMs(targetVideoId) }
                val rawBundle = streamExtractor.resolveVideoStreamBundle(targetVideoId)
                rawBundle.copy(
                    introOffsetMs = introOffsetMs.await(),
                    isDirectOmv = true,
                )
            }

            cache[targetVideoId] = CachedVideoBundle(
                bundle = bundle,
                expiresAtMs = now + CACHE_EXPIRY_MS,
            )
            bundle
        }.onFailure { error ->
            Log.w(TAG, "Failed to resolve video stream for $trackTitle: ${error.message}", error)
        }
    }

    suspend fun searchVideoCandidates(
        trackTitle: String,
        trackArtist: String,
        genre: String? = null,
        expectedDurationMs: Long? = null,
    ): List<VideoSearchCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(trackTitle)
        val cleanArtist = trackArtist.takeUnless { it.equals("Unknown artist", ignoreCase = true) }.orEmpty()
        val queries = officialVideoSearchQueries(trackTitle, trackArtist)
        val found = linkedMapOf<String, RawVideoResult>()

        for (query in queries) {
            val results = runCatching { streamExtractor.searchVideos(query, limit = 15) }.getOrDefault(emptyList())
            for (res in results) {
                if (res.videoId.isNotBlank()) found.putIfAbsent(res.videoId, res)
            }
        }

        val officialCandidates = found.values.mapNotNull { candidate ->
            val score = scoreVideoCandidate(
                candidateTitle = candidate.title,
                candidateArtist = candidate.channel,
                candidateDurationSec = candidate.durationSeconds,
                wantedTitle = cleanTitle,
                wantedArtist = cleanArtist,
                expectedDurationMs = expectedDurationMs,
            )?.takeIf { it >= MIN_VIDEO_CANDIDATE_SCORE } ?: return@mapNotNull null

            VideoSearchCandidate(
                videoId = candidate.videoId,
                title = candidate.title,
                channel = candidate.channel,
                durationSeconds = candidate.durationSeconds,
                matchScore = score,
            )
        }

        if (officialCandidates.isNotEmpty()) {
            return@withContext officialCandidates.sortedWith(
                compareByDescending<VideoSearchCandidate> { it.matchScore }
                    .thenBy { durationDistance(expectedDurationMs, it.durationSeconds) }
            ).take(25)
        }

        // Tier 2: Fallback YouTube search with metadata key terms & tags (including 4K, 8K)
        val fallbackQuery = fallbackVideoSearchQuery(cleanTitle, cleanArtist, genre)
        searchByCustomQuery(fallbackQuery, expectedDurationMs, cleanTitle, cleanArtist)
    }

    suspend fun searchByCustomQuery(
        query: String,
        expectedDurationMs: Long? = null,
        wantedTitle: String? = null,
        wantedArtist: String? = null,
    ): List<VideoSearchCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = runCatching {
            streamExtractor.searchVideos(query.trim(), limit = 25)
        }.getOrDefault(emptyList())

        results.filter { it.videoId.isNotBlank() }.map { candidate ->
            val score = if (!wantedTitle.isNullOrBlank()) {
                scoreVideoCandidate(
                    candidateTitle = candidate.title,
                    candidateArtist = candidate.channel,
                    candidateDurationSec = candidate.durationSeconds,
                    wantedTitle = wantedTitle,
                    wantedArtist = wantedArtist.orEmpty(),
                    expectedDurationMs = expectedDurationMs,
                ) ?: calculateRelaxedScore(candidate, wantedTitle, wantedArtist.orEmpty(), expectedDurationMs)
            } else {
                calculateDurationScore(candidate.durationSeconds, expectedDurationMs)
            }

            VideoSearchCandidate(
                videoId = candidate.videoId,
                title = candidate.title,
                channel = candidate.channel,
                durationSeconds = candidate.durationSeconds,
                matchScore = score,
            )
        }.sortedWith(
            compareByDescending<VideoSearchCandidate> { it.matchScore }
                .thenBy { durationDistance(expectedDurationMs, it.durationSeconds) }
        ).take(25)
    }

    fun fallbackVideoSearchQuery(cleanTitle: String, cleanArtist: String, genre: String?): String {
        val g = genre?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }?.let { " $it" }.orEmpty()
        return "$cleanTitle $cleanArtist$g full video official video official lyrical 4K 8K".trim()
    }

    private fun durationDistance(expectedMs: Long?, actualSec: Int?): Long {
        val expected = expectedMs?.takeIf { it > 0 }?.div(1_000L) ?: return Long.MAX_VALUE
        val actual = actualSec?.takeIf { it > 0 }?.toLong() ?: return Long.MAX_VALUE
        return kotlin.math.abs(expected - actual)
    }

    private fun calculateRelaxedScore(
        candidate: RawVideoResult,
        wantedTitle: String,
        wantedArtist: String,
        expectedDurationMs: Long?,
    ): Int {
        var score = 100
        val lowerTitle = candidate.title.lowercase(java.util.Locale.ROOT)
        val lowerWantedTitle = wantedTitle.lowercase(java.util.Locale.ROOT)
        val lowerWantedArtist = wantedArtist.lowercase(java.util.Locale.ROOT)

        if (lowerWantedTitle.isNotBlank() && lowerTitle.contains(lowerWantedTitle)) {
            score += 150
        }
        if (lowerWantedArtist.isNotBlank() && (lowerTitle.contains(lowerWantedArtist) || candidate.channel.lowercase(java.util.Locale.ROOT).contains(lowerWantedArtist))) {
            score += 80
        }
        if (lowerTitle.contains("official")) score += 40
        if (lowerTitle.contains("video")) score += 30
        if (lowerTitle.contains("4k") || lowerTitle.contains("8k")) score += 20

        if (expectedDurationMs != null && expectedDurationMs > 0 && candidate.durationSeconds != null) {
            val diffSec = kotlin.math.abs(expectedDurationMs / 1000L - candidate.durationSeconds)
            if (diffSec <= 5) score += 60
            else if (diffSec <= 15) score += 30
            else if (diffSec > 60) score -= 40
        }
        return score.coerceAtLeast(10)
    }

    private fun calculateDurationScore(actualSec: Int?, expectedDurationMs: Long?): Int {
        var score = 100
        if (expectedDurationMs != null && expectedDurationMs > 0 && actualSec != null) {
            val diffSec = kotlin.math.abs(expectedDurationMs / 1000L - actualSec)
            if (diffSec <= 5) score += 80
            else if (diffSec <= 20) score += 40
            else if (diffSec > 60) score -= 30
        }
        return score.coerceAtLeast(10)
    }

    private suspend fun findFallbackVideoId(
        trackTitle: String,
        trackArtist: String,
        genre: String? = null,
        expectedDurationMs: Long? = null,
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(trackTitle)
        val cleanArtist = trackArtist.takeUnless { it.equals("Unknown artist", ignoreCase = true) }.orEmpty()
        val query = fallbackVideoSearchQuery(cleanTitle, cleanArtist, genre)
        val results = runCatching {
            streamExtractor.searchVideos(query, limit = 15)
        }.getOrNull().orEmpty()

        val candidates = results.filter { it.videoId.isNotBlank() }
        if (candidates.isEmpty()) return@withContext null

        val scored = candidates.map { track ->
            val score = scoreVideoCandidate(
                candidateTitle = track.title,
                candidateArtist = track.channel,
                candidateDurationSec = track.durationSeconds,
                wantedTitle = cleanTitle,
                wantedArtist = cleanArtist,
                expectedDurationMs = expectedDurationMs,
            ) ?: calculateRelaxedScore(track, cleanTitle, cleanArtist, expectedDurationMs)
            track to score
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull { it.second >= 120 }?.first ?: scored.firstOrNull()?.first
        best?.videoId
    }

    private suspend fun findOfficialVideoId(
        trackTitle: String,
        trackArtist: String,
        expectedDurationMs: Long? = null,
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(trackTitle)
        val cleanArtist = trackArtist.takeUnless { it.equals("Unknown artist", ignoreCase = true) }.orEmpty()
        val queries = officialVideoSearchQueries(trackTitle, trackArtist)

        for (query in queries) {
            val videoResults = runCatching {
                streamExtractor.searchVideos(query, limit = 15)
            }.getOrNull().orEmpty()

            val candidates = videoResults.filter { candidate ->
                candidate.videoId.isNotBlank() && isSameSongVideo(
                    candidateTitle = candidate.title,
                    candidateChannel = candidate.channel,
                    candidateSeconds = candidate.durationSeconds,
                    songTitle = cleanTitle,
                    songArtist = cleanArtist,
                    songDurationMs = expectedDurationMs,
                )
            }
            if (candidates.isEmpty()) continue

            val scored = candidates.mapNotNull { track ->
                val score = scoreVideoCandidate(
                    candidateTitle = track.title,
                    candidateArtist = track.channel,
                    candidateDurationSec = track.durationSeconds,
                    wantedTitle = cleanTitle,
                    wantedArtist = cleanArtist,
                    expectedDurationMs = expectedDurationMs,
                ) ?: return@mapNotNull null
                track to score
            }.sortedByDescending { it.second }

            val best = scored.firstOrNull { it.second >= MIN_VIDEO_CANDIDATE_SCORE }?.first
            if (best != null) {
                Log.i(TAG, "Found official video for '$trackTitle': ${best.videoId} (${best.title}) score=${scored.first().second}")
                return@withContext best.videoId
            }
        }
        null
    }

    suspend fun fetchIntroOffsetMs(videoId: String): Long = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext 0L
        runCatching {
            val request = okhttp3.Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&category=music_offtopic")
                .header("User-Agent", "PixelPlayerOSS/1.0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching 0L
                val body = response.body?.string() ?: return@runCatching 0L
                val array = org.json.JSONArray(body)
                for (i in 0 until array.length()) {
                    val seg = array.optJSONObject(i) ?: continue
                    val segment = seg.optJSONArray("segment") ?: continue
                    val start = segment.optDouble(0, 0.0)
                    val end = segment.optDouble(1, 0.0)
                    if (start <= 2.5 && end > start) {
                        return@runCatching ((end - start) * 1000.0).toLong()
                    }
                }
                0L
            }
        }.getOrDefault(0L)
    }

    private fun officialVideoSearchQueries(trackTitle: String, trackArtist: String): List<String> {
        val title = cleanTrackTitle(trackTitle)
        val artist = trackArtist.takeUnless { it.equals("Unknown artist", ignoreCase = true) }.orEmpty()
        return listOf(
            "$artist - $title Official Music Video",
            "$title $artist official video",
            "$title $artist",
        ).map(String::trim).filter(String::isNotBlank).distinct()
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(with.*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[(\[]\s*from\s+[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[feat\..*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*remaster(?:ed)?(?:\s*\d{4})?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(official\s*(?:music\s*)?video\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[official\s*(?:music\s*)?video\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(official\s*audio\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[official\s*audio\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(visualizer\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[visualizer\]""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
