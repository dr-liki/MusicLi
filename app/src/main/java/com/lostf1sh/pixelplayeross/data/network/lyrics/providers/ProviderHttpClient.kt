package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shared lightweight HTTP helper for lyric providers with bounded timeouts.
 */
internal class ProviderRateLimitException(
    val retryAfterMs: Long? = null,
    val statusCode: Int = 429,
    val url: String = "",
    message: String = "HTTP $statusCode rate limited",
) : java.io.IOException(message)

internal object ProviderHttpClient {
    const val APP_USER_AGENT =
        "PixelPlayerOSS/1.0 (https://github.com/dr-liki/PixelPlayerOSS)"

    fun parseRetryAfterMs(retryAfterHeader: String?): Long? {
        val trimmed = retryAfterHeader?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        trimmed.toLongOrNull()?.let { seconds ->
            return (seconds.coerceAtLeast(1L)) * 1_000L
        }
        return runCatching {
            val instant = java.time.ZonedDateTime.parse(
                trimmed,
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
            ).toInstant()
            (instant.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1_000L)
        }.getOrNull()
    }

    suspend fun getText(
        okHttpClient: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 4_500L,
    ): String? = withContext(Dispatchers.IO) {
        val client = okHttpClient.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", APP_USER_AGENT)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val retryAfterHeader = response.header("Retry-After")
            if (response.code == 429 || (response.code == 503 && !retryAfterHeader.isNullOrBlank())) {
                throw ProviderRateLimitException(
                    retryAfterMs = parseRetryAfterMs(retryAfterHeader),
                    statusCode = response.code,
                    url = url,
                    message = "HTTP ${response.code} rate limited from $url",
                )
            }
            if (!response.isSuccessful) return@withContext null
            response.body.string().takeIf { it.isNotBlank() }
        }
    }

    suspend fun getJson(
        okHttpClient: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 4_500L,
    ): JSONObject? {
        val text = getText(okHttpClient, url, headers, timeoutMs) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }
}
