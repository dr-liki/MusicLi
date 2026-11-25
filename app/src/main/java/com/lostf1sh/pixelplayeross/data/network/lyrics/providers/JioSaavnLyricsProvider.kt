package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import com.lostf1sh.pixelplayeross.data.explore.ExploreRepository
import com.lostf1sh.pixelplayeross.data.explore.JioSaavnApiClient
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
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
        val searchResponse = runCatching {
            jioSaavnApiClient.searchSongs(baseUrl, query, page = 0, limit = 6)
        }.getOrNull() ?: return emptyList()

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
                        ?: runCatching {
                            jioSaavnApiClient.getLyricsById(baseUrl, songId).data?.lyrics
                        }.getOrNull()?.let(::decodeHtml)?.trim()?.takeIf { it.isNotBlank() }
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
