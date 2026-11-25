package com.lostf1sh.pixelplayeross.data.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubeStreamExtractor"
const val YOUTUBE_ORIGIN = "https://www.youtube.com"
const val ANDROID_VR_USER_AGENT =
    "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1) gzip"
private const val VISIONOS_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
private const val YOUTUBE_WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** Higher is better for the same resolution: H.264 is the one every phone decodes well. */
internal fun videoCodecPreference(codec: String?): Int {
    val value = codec.orEmpty().lowercase()
    return when {
        value.startsWith("avc") -> 3
        value.startsWith("vp9") || value.startsWith("vp09") -> 2
        value.startsWith("hev") || value.startsWith("hvc") -> 1
        else -> 0
    }
}

internal fun videoCodecMime(codec: String?): String? {
    val value = codec.orEmpty().lowercase()
    return when {
        value.startsWith("avc") -> "video/avc"
        value.startsWith("vp9") || value.startsWith("vp09") -> "video/x-vnd.on2.vp9"
        value.startsWith("av01") -> "video/av01"
        value.startsWith("hev") || value.startsWith("hvc") -> "video/hevc"
        else -> null
    }
}

internal object VideoDecoderSupport {
    private val decoders: List<MediaCodecInfo> by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
        }.getOrDefault(emptyList())
    }

    fun canDecode(codec: String?, width: Int, height: Int): Boolean {
        val mime = videoCodecMime(codec) ?: return true
        if (decoders.isEmpty()) return true
        val w = if (width > 0) width else (height * 16 / 9 + 1) / 2 * 2
        return decoders.any { info ->
            runCatching {
                val type = info.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }
                    ?: return@runCatching false
                val video = info.getCapabilitiesForType(type).videoCapabilities
                    ?: return@runCatching false
                height <= 0 || video.isSizeSupported(w, height) || video.isSizeSupported(height, w)
            }.getOrDefault(false)
        }
    }
}

@Singleton
class YouTubeStreamExtractor @Inject constructor(
    private val http: OkHttpClient,
) {
    private val downloader = OkHttpNewPipeDownloader(http)

    @Volatile
    private var initialized = false

    private fun initialize() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    try {
                        NewPipe.init(downloader)
                    } catch (e: Exception) {
                        Log.w(TAG, "NewPipe already initialized or failed: ${e.message}")
                    }
                    initialized = true
                }
            }
        }
    }

    suspend fun resolveVideoStreamBundle(videoId: String): SyncedVideoStreamBundle = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        initialize()

        // 1. Try fast token-free InnerTube ANDROID_VR client (unbounded playback across all ranges)
        resolveVideoStreamBundleWithAndroidVrClient(videoId, now)?.let { return@withContext it }

        // 2. Try VISIONOS client fallback
        resolveVideoStreamBundleWithVisionOsClient(videoId, now)?.let { return@withContext it }

        // 3. Fall back to NewPipeExtractor
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IOException("YouTube video stream extraction failed for $videoId", error)
        }

        val allVideoStreams = buildList {
            addAll(info.videoStreams)
            addAll(info.videoOnlyStreams)
        }

        val validStreams = allVideoStreams.filter { stream ->
            stream.content.isNotBlank() && (
                stream.format?.mimeType?.contains("mp4", ignoreCase = true) == true ||
                stream.format?.mimeType?.contains("webm", ignoreCase = true) == true
            ) && VideoDecoderSupport.canDecode(
                stream.codec,
                stream.width,
                parseResolutionHeight(stream.resolution, stream.height),
            )
        }

        if (validStreams.isEmpty()) {
            throw IOException("No supported video streams for $videoId")
        }

        val streamsByHeight = validStreams
            .map { stream ->
                val h = parseResolutionHeight(stream.resolution, stream.height)
                h to stream
            }
            .filter { it.first > 0 }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, streams) ->
                streams.maxWithOrNull(
                    compareBy<org.schabi.newpipe.extractor.stream.VideoStream> {
                        videoCodecPreference(it.codec)
                    }.thenBy { it.bitrate }
                )!!
            }

        val availableStreams = streamsByHeight.keys.sortedDescending().map { height ->
            streamsByHeight[height]!!.toYouTubeVideoStream(videoId, info.duration, now)
        }

        if (availableStreams.isEmpty()) {
            throw IOException("No video stream resolutions available for $videoId")
        }

        // Default to 720p, if unavailable fall back to Auto / available best
        val defaultStream = availableStreams.firstOrNull { it.height == 720 }
            ?: availableStreams.firstOrNull { it.height == 1080 }
            ?: availableStreams.first()

        val pairedAudioUrl = info.audioStreams.maxByOrNull { it.bitrate }?.content
        SyncedVideoStreamBundle(
            videoId = videoId,
            defaultStream = defaultStream,
            availableStreams = availableStreams,
            pairedAudioStreamUrl = pairedAudioUrl,
            pairedAudioHeaders = mapOf(
                "User-Agent" to YOUTUBE_WEB_USER_AGENT,
                "Origin" to YOUTUBE_ORIGIN,
                "Referer" to "$YOUTUBE_ORIGIN/watch?v=$videoId",
            ),
        )
    }

    private suspend fun resolveVideoStreamBundleWithAndroidVrClient(
        videoId: String,
        now: Long,
    ): SyncedVideoStreamBundle? = withContext(Dispatchers.IO) {
        runCatching {
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "ANDROID_VR",
                            "clientVersion": "1.60.19",
                            "deviceMake": "Oculus",
                            "deviceModel": "Quest 3",
                            "osName": "Android",
                            "osVersion": "12",
                            "androidSdkVersion": "32",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": "$videoId",
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyD-p045F_WzU-vA_YgX20SCx4KAo&prettyPrint=false")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", ANDROID_VR_USER_AGENT)
                .header("X-YouTube-Client-Name", "28")
                .header("X-YouTube-Client-Version", "1.60.19")
                .header("Origin", YOUTUBE_ORIGIN)
                .header("Referer", "$YOUTUBE_ORIGIN/")
                .build()

            val root = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bodyStr = response.body?.string() ?: return@runCatching null
                JSONObject(bodyStr)
            }

            val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            if (!status.equals("OK", ignoreCase = true)) return@runCatching null

            val streamingData = root.optJSONObject("streamingData") ?: return@runCatching null
            val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return@runCatching null
            val durationSec = root.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toLongOrNull() ?: 0L

            var pairedAudioUrl: String? = null
            var bestAudioBitrate = 0
            val extractedStreams = mutableListOf<YouTubeVideoStream>()
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                val mime = f.optString("mimeType")
                if (mime.contains("audio", ignoreCase = true)) {
                    val aUrl = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val aBitrate = f.optInt("bitrate", 0)
                    if (aBitrate > bestAudioBitrate || (mime.contains("opus", ignoreCase = true) && pairedAudioUrl == null)) {
                        bestAudioBitrate = aBitrate
                        pairedAudioUrl = aUrl
                    }
                    continue
                }
                if (!mime.contains("video", ignoreCase = true)) continue
                val streamUrl = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                val height = f.optInt("height", 0).takeIf { it > 0 }
                    ?: parseResolutionHeight(f.optString("qualityLabel"), 0)
                val width = f.optInt("width", 0).takeIf { it > 0 }
                val bitrate = f.optInt("bitrate", 0)

                extractedStreams += YouTubeVideoStream(
                    videoId = videoId,
                    url = streamUrl,
                    itag = f.optInt("itag", 0).takeIf { it > 0 },
                    resolution = f.optString("qualityLabel").ifBlank { "${height}p" },
                    height = height,
                    width = width,
                    bitrate = bitrate,
                    mimeType = mime.substringBefore(";"),
                    durationMs = durationSec.takeIf { it > 0 }?.times(1_000L),
                    expiresAtEpochMs = now + 5 * 60 * 60 * 1000L,
                    requestHeaders = mapOf(
                        "User-Agent" to ANDROID_VR_USER_AGENT,
                        "Origin" to YOUTUBE_ORIGIN,
                        "Referer" to "$YOUTUBE_ORIGIN/",
                    ),
                )
            }
            if (extractedStreams.isEmpty()) return@runCatching null

            val streamsByHeight = extractedStreams
                .filter { (it.height ?: 0) > 0 }
                .groupBy { it.height!! }
                .mapValues { (_, streams) ->
                    streams.maxWithOrNull(
                        compareBy<YouTubeVideoStream> {
                            if (it.mimeType?.contains("mp4", ignoreCase = true) == true) 1 else 0
                        }.thenBy { it.bitrate ?: 0 }
                    )!!
                }

            val availableStreams = streamsByHeight.keys.sortedDescending().map { streamsByHeight[it]!! }
            if (availableStreams.isEmpty()) return@runCatching null

            val defaultStream = availableStreams.firstOrNull { it.height == 720 }
                ?: availableStreams.firstOrNull { it.height == 1080 }
                ?: availableStreams.first()

            SyncedVideoStreamBundle(
                videoId = videoId,
                defaultStream = defaultStream,
                availableStreams = availableStreams,
                pairedAudioStreamUrl = pairedAudioUrl,
                pairedAudioHeaders = mapOf(
                    "User-Agent" to ANDROID_VR_USER_AGENT,
                    "Origin" to YOUTUBE_ORIGIN,
                    "Referer" to "$YOUTUBE_ORIGIN/",
                ),
                clientName = "ANDROID_VR",
            )
        }.getOrNull()
    }

    private suspend fun resolveVideoStreamBundleWithVisionOsClient(
        videoId: String,
        now: Long,
    ): SyncedVideoStreamBundle? = withContext(Dispatchers.IO) {
        runCatching {
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "VISIONOS",
                            "clientVersion": "0.1",
                            "deviceMake": "Apple",
                            "deviceModel": "RealityDevice14,1",
                            "osName": "visionOS",
                            "osVersion": "1.3.21O771",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": "$videoId",
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc&prettyPrint=false")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", VISIONOS_USER_AGENT)
                .header("X-YouTube-Client-Name", "101")
                .header("X-YouTube-Client-Version", "0.1")
                .header("Origin", YOUTUBE_ORIGIN)
                .header("Referer", "$YOUTUBE_ORIGIN/")
                .build()

            val root = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bodyStr = response.body?.string() ?: return@runCatching null
                JSONObject(bodyStr)
            }

            val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            if (!status.equals("OK", ignoreCase = true)) return@runCatching null

            val streamingData = root.optJSONObject("streamingData") ?: return@runCatching null
            val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return@runCatching null
            val durationSec = root.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toLongOrNull() ?: 0L

            var pairedAudioUrl: String? = null
            var bestAudioBitrate = 0
            val extractedStreams = mutableListOf<YouTubeVideoStream>()
            for (i in 0 until adaptive.length()) {
                val f = adaptive.optJSONObject(i) ?: continue
                val mime = f.optString("mimeType")
                if (mime.contains("audio", ignoreCase = true)) {
                    val aUrl = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val aBitrate = f.optInt("bitrate", 0)
                    if (aBitrate > bestAudioBitrate || (mime.contains("opus", ignoreCase = true) && pairedAudioUrl == null)) {
                        bestAudioBitrate = aBitrate
                        pairedAudioUrl = aUrl
                    }
                    continue
                }
                if (!mime.contains("video", ignoreCase = true)) continue
                val streamUrl = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                val height = f.optInt("height", 0).takeIf { it > 0 }
                    ?: parseResolutionHeight(f.optString("qualityLabel"), 0)
                val width = f.optInt("width", 0).takeIf { it > 0 }
                val bitrate = f.optInt("bitrate", 0)

                extractedStreams += YouTubeVideoStream(
                    videoId = videoId,
                    url = streamUrl,
                    itag = f.optInt("itag", 0).takeIf { it > 0 },
                    resolution = f.optString("qualityLabel").ifBlank { "${height}p" },
                    height = height,
                    width = width,
                    bitrate = bitrate,
                    mimeType = mime.substringBefore(";"),
                    durationMs = durationSec.takeIf { it > 0 }?.times(1_000L),
                    expiresAtEpochMs = now + 5 * 60 * 60 * 1000L,
                    requestHeaders = mapOf(
                        "User-Agent" to VISIONOS_USER_AGENT,
                        "Origin" to YOUTUBE_ORIGIN,
                        "Referer" to "$YOUTUBE_ORIGIN/",
                    ),
                )
            }
            if (extractedStreams.isEmpty()) return@runCatching null

            val streamsByHeight = extractedStreams
                .filter { (it.height ?: 0) > 0 }
                .groupBy { it.height!! }
                .mapValues { (_, streams) ->
                    streams.maxWithOrNull(
                        compareBy<YouTubeVideoStream> {
                            if (it.mimeType?.contains("mp4", ignoreCase = true) == true) 1 else 0
                        }.thenBy { it.bitrate ?: 0 }
                    )!!
                }

            val availableStreams = streamsByHeight.keys.sortedDescending().map { streamsByHeight[it]!! }
            if (availableStreams.isEmpty()) return@runCatching null

            val defaultStream = availableStreams.firstOrNull { it.height == 720 }
                ?: availableStreams.firstOrNull { it.height == 1080 }
                ?: availableStreams.first()

            SyncedVideoStreamBundle(
                videoId = videoId,
                defaultStream = defaultStream,
                availableStreams = availableStreams,
                pairedAudioStreamUrl = pairedAudioUrl,
                pairedAudioHeaders = mapOf(
                    "User-Agent" to VISIONOS_USER_AGENT,
                    "Origin" to YOUTUBE_ORIGIN,
                    "Referer" to "$YOUTUBE_ORIGIN/",
                ),
                clientName = "VISIONOS",
            )
        }.getOrNull()
    }

    suspend fun searchVideos(query: String, limit: Int = 15): List<RawVideoResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        // Query YouTube Web search
        val candidates = mutableListOf<RawVideoResult>()
        runCatching {
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB",
                            "clientVersion": "2.20261011.05.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": ${JSONObject.quote(query)}
                }
            """.trimIndent()

            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", YOUTUBE_WEB_USER_AGENT)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val root = JSONObject(body)
                parseVideoRenderers(root, candidates, limit)
            }
        }

        // Fallback to NewPipe search if needed
        if (candidates.isEmpty()) {
            runCatching {
                initialize()
                val searchInfo = org.schabi.newpipe.extractor.search.SearchInfo.getInfo(
                    ServiceList.YouTube,
                    ServiceList.YouTube.searchQHFactory.fromQuery(query),
                )
                searchInfo.relatedItems.forEach { item ->
                    if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                        val vId = extractYoutubeVideoId(item.url) ?: return@forEach
                        candidates += RawVideoResult(
                            videoId = vId,
                            title = item.name.orEmpty(),
                            channel = item.uploaderName.orEmpty(),
                            durationSeconds = item.duration.takeIf { it > 0 }?.toInt(),
                            thumbnailUrl = item.thumbnails.lastOrNull()?.url?.takeIf { it.isNotBlank() }
                                ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg",
                        )
                    }
                }
            }
        }
        candidates.distinctBy { it.videoId }.take(limit)
    }

    private fun parseVideoRenderers(obj: Any?, out: MutableList<RawVideoResult>, limit: Int) {
        if (out.size >= limit) return
        when (obj) {
            is JSONObject -> {
                if (obj.has("videoRenderer")) {
                    val vr = obj.optJSONObject("videoRenderer")
                    if (vr != null) {
                        val vId = vr.optString("videoId")
                        val title = vr.optJSONObject("title")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            ?: vr.optJSONObject("title")?.optString("simpleText").orEmpty()
                        val channel = vr.optJSONObject("ownerText")
                            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            ?: vr.optJSONObject("shortBylineText")
                                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                        val durationText = vr.optJSONObject("lengthText")?.optString("simpleText").orEmpty()
                        val durationSec = parseDurationSeconds(durationText)
                        val thumbnails = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = if (thumbnails != null && thumbnails.length() > 0) {
                            thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                        } else {
                            null
                        }?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
                        if (vId.isNotBlank() && title.isNotBlank()) {
                            out += RawVideoResult(
                                videoId = vId,
                                title = title,
                                channel = channel,
                                durationSeconds = durationSec,
                                thumbnailUrl = thumbUrl,
                            )
                        }
                    }
                }
                for (key in obj.keys()) {
                    parseVideoRenderers(obj.opt(key), out, limit)
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until obj.length()) {
                    parseVideoRenderers(obj.opt(i), out, limit)
                }
            }
        }
    }

    private fun parseDurationSeconds(text: String): Int? {
        if (text.isBlank()) return null
        val parts = text.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private fun parseResolutionHeight(resolution: String?, height: Int): Int {
        if (height > 0) return height
        return Regex("""\d+""").find(resolution.orEmpty())?.value?.toIntOrNull() ?: 0
    }

    private fun org.schabi.newpipe.extractor.stream.VideoStream.toYouTubeVideoStream(
        videoId: String,
        durationSeconds: Long,
        now: Long,
    ): YouTubeVideoStream {
        val streamUrl = content
        return YouTubeVideoStream(
            videoId = videoId,
            url = streamUrl,
            itag = itag.takeIf { it >= 0 },
            mimeType = format?.mimeType,
            codec = codec?.takeIf(String::isNotBlank),
            resolution = resolution,
            width = width.takeIf { it > 0 },
            height = height.takeIf { it > 0 },
            bitrate = bitrate.takeIf { it > 0 },
            durationMs = itagItem?.approxDurationMs?.takeIf { it > 0 }
                ?: durationSeconds.takeIf { it > 0 }?.times(1_000L),
            requestHeaders = mapOf(
                "User-Agent" to YOUTUBE_WEB_USER_AGENT,
                "Origin" to YOUTUBE_ORIGIN,
                "Referer" to "$YOUTUBE_ORIGIN/watch?v=$videoId",
            ),
            expiresAtEpochMs = now + 5 * 60 * 60 * 1000L,
        )
    }
}

data class RawVideoResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationSeconds: Int?,
    val thumbnailUrl: String? = null,
)

private class OkHttpNewPipeDownloader(
    private val http: OkHttpClient,
) : Downloader() {
    override fun execute(request: Request): Response {
        val headers = request.headers()
        val requestBuilder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())

        headers.forEach { (name, values) ->
            values.forEach { value -> requestBuilder.addHeader(name, value) }
        }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            requestBuilder.header("User-Agent", YOUTUBE_WEB_USER_AGENT)
        }
        if (headers.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
        }
        if (headers.keys.none { it.equals("Origin", ignoreCase = true) }) {
            requestBuilder.header("Origin", YOUTUBE_ORIGIN)
        }
        if (headers.keys.none { it.equals("Referer", ignoreCase = true) }) {
            requestBuilder.header("Referer", "$YOUTUBE_ORIGIN/")
        }

        return http.newCall(requestBuilder.build()).execute().use { response ->
            Response(
                response.code,
                response.message,
                response.headers.names().associateWith(response.headers::values),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
