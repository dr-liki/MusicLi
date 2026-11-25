package com.lostf1sh.pixelplayeross.data.network.lyrics

import com.lostf1sh.pixelplayeross.data.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsTranslator @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val cache = ConcurrentHashMap<String, Lyrics>()

    fun clearCacheForSong(songId: String) {
        val prefix = "$songId|"
        cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    fun resolvedLanguageTag(targetSetting: String): String {
        val clean = targetSetting.trim()
        if (clean.isNotBlank() && !clean.equals("auto", ignoreCase = true)) {
            return clean
        }
        val sys = Locale.getDefault().language
        return if (sys.isNullOrBlank()) "en" else sys
    }

    suspend fun translateLyrics(
        songId: String,
        lyrics: Lyrics,
        targetLanguageSetting: String,
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        runCatching {
            val targetLang = resolvedLanguageTag(targetLanguageSetting)
            val cacheKey = "$songId|$targetLang"
            cache[cacheKey]?.let { return@runCatching it }

            val synced = lyrics.synced
            val plain = lyrics.plain
            if (!synced.isNullOrEmpty() || !plain.isNullOrEmpty()) {
                val updatedSynced = if (!synced.isNullOrEmpty()) {
                    val texts = synced.map { it.line }
                    val translated = translateLineBatch(texts, targetLang)
                    synced.mapIndexed { idx, line ->
                        val tr = translated.getOrNull(idx)?.takeIf {
                            it.isNotBlank() && !it.equals(line.line, ignoreCase = true)
                        }
                        line.copy(translation = tr ?: line.translation)
                    }
                } else {
                    synced
                }
                val updatedTranslations = if (!plain.isNullOrEmpty()) {
                    val translated = translateLineBatch(plain, targetLang)
                    plain.mapIndexed { idx, original ->
                        translated.getOrNull(idx)?.takeIf {
                            it.isNotBlank() && !it.equals(original, ignoreCase = true)
                        } ?: lyrics.plainTranslations?.getOrNull(idx)
                    }
                } else {
                    lyrics.plainTranslations
                }
                val result = lyrics.copy(
                    synced = updatedSynced,
                    plainTranslations = updatedTranslations
                )
                cache[cacheKey] = result
                return@runCatching result
            }

            throw IllegalStateException("No lyric lines available to translate")
        }
    }

    private fun translateLineBatch(lines: List<String>, targetLang: String): List<String?> {
        if (lines.isEmpty()) return emptyList()
        val results = MutableList<String?>(lines.size) { null }
        val nonBlankIndices = lines.indices.filter { lines[it].isNotBlank() }
        if (nonBlankIndices.isEmpty()) return results

        // Chunk non-blank lines into ~1200-char payloads, falling back per-line if sentence splitting merges lines.
        var cursor = 0
        while (cursor < nonBlankIndices.size) {
            val batchIndices = mutableListOf<Int>()
            var chars = 0
            while (cursor < nonBlankIndices.size && batchIndices.size < 18) {
                val idx = nonBlankIndices[cursor]
                val len = lines[idx].length
                if (batchIndices.isNotEmpty() && chars + len > 1200) break
                batchIndices.add(idx)
                chars += len + 1
                cursor++
            }
            val joined = batchIndices.joinToString("\n") { lines[it].replace('\n', ' ') }
            val translatedText = requestTranslation(joined, targetLang)
            val split = translatedText.split('\n').map(String::trim)
            if (split.size == batchIndices.size) {
                batchIndices.forEachIndexed { offset, lineIdx ->
                    results[lineIdx] = split[offset]
                }
            } else {
                batchIndices.forEach { lineIdx ->
                    results[lineIdx] = runCatching {
                        requestTranslation(lines[lineIdx], targetLang).trim()
                    }.getOrNull()
                }
            }
        }
        return results
    }

    private fun requestTranslation(text: String, targetLang: String): String {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", targetLang)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PixelPlayerOSS/1.0")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Translation HTTP ${response.code}")
            }
            val body = response.body.string()
            return parseTranslationJson(body)
        }
    }

    companion object {
        internal fun parseTranslationJson(body: String): String {
            val outer = JSONArray(body)
            val sentences = outer.optJSONArray(0) ?: return ""
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val segment = sentences.optJSONArray(i)?.optString(0) ?: continue
                sb.append(segment)
            }
            return sb.toString()
        }
    }
}
