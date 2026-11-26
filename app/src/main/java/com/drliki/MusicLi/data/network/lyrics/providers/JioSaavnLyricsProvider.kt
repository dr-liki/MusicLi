package com.drliki.MusicLi.data.network.lyrics.providers

import com.drliki.MusicLi.data.explore.ExploreRepository
import com.drliki.MusicLi.data.explore.JioSaavnApiClient
import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnLyricsProvider @Inject constructor(
    private val jioSaavnApiClient: JioSaavnApiClient,
    private val exploreRepository: ExploreRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.JIOSAAVN

    private inline fun <T> runJioSaavnCall(block: () -> T): T? {
        return try {
            block()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (rle: ProviderRateLimitException) {
            throw rle
        } catch (he: retrofit2.HttpException) {
            val code = he.code()
            val retryAfter = he.response()?.headers()?.get("Retry-After")
            if (code == 429 || (code == 503 && !retryAfter.isNullOrBlank())) {
                throw ProviderRateLimitException(
                    retryAfterMs = ProviderHttpClient.parseRetryAfterMs(retryAfter),
                    statusCode = code,
                    url = he.response()?.raw()?.request?.url?.toString().orEmpty(),
                    message = "JioSaavn HTTP $code rate limited",
                )
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

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

        val baseUrl = userPreferencesRepository.exploreApiBaseUrlFlow.first()
        val searchResponse = runJioSaavnCall {
            jioSaavnApiClient.searchSongs(baseUrl, query, page = 0, limit = 6)
        } ?: return emptyList()

        val rawDtos = searchResponse.data?.results.orEmpty()
        val targetDtos = rawDtos
            .filter { !it.id.isNullOrBlank() && (it.hasLyrics == true || !it.lyrics?.lyrics.isNullOrBlank()) }
            .ifEmpty { rawDtos.filter { !it.id.isNullOrBlank() }.take(3) }
            .take(4)

        if (targetDtos.isEmpty()) return emptyList()

        return coroutineScope {
            targetDtos.map { dto ->
                async {
                    val songId = dto.id ?: return@async null
                    val song = exploreRepository.resolveSong(songId, includeLyrics = true).getOrNull()
                        ?: return@async null
                    val normalized = song.lyricsText?.trim()?.takeIf { it.isNotBlank() }
                        ?: runJioSaavnCall {
                            jioSaavnApiClient.getLyricsById(baseUrl, songId).data?.lyrics
                        }?.let(::decodeHtml)?.trim()?.takeIf { it.isNotBlank() }
                        ?: return@async null

                    LyricsProviderCandidate(
                        externalId = song.id,
                        providerId = id,
                        title = song.title,
                        artistName = song.artist,
                        albumName = song.albumName,
                        durationSec = song.durationSeconds.takeIf { it > 0 }?.toDouble(),
                        rawLyrics = normalized,
                        hasSyncedContent = false,
                        hasWordSync = false,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun decodeHtml(input: String): String =
        input
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
}
