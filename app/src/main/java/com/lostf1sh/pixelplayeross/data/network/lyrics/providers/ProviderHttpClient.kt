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
internal object ProviderHttpClient {
    const val APP_USER_AGENT =
        "PixelPlayerOSS/1.0 (https://github.com/dr-liki/PixelPlayerOSS)"

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
