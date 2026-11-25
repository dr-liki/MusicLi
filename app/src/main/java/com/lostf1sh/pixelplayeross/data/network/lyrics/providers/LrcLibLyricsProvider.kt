package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibApiService
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibLyricsProvider @Inject constructor(
    private val lrcLibApiService: LrcLibApiService,
) : LyricsProvider {

    override val id: LyricsProviderId = LyricsProviderId.LRCLIB

    private inline fun <T> runLrcLibCall(block: () -> T): T? {
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
                    message = "LrcLib HTTP $code rate limited",
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
        val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() }
        if (cleanTitle.isBlank()) return emptyList()

        val responses = coroutineScope {
            val queries = buildList<suspend () -> Array<LrcLibResponse>?> {
                if (!cleanArtist.isNullOrBlank()) {
                    add {
                        runLrcLibCall {
                            lrcLibApiService.searchLyrics(
                                query = "$cleanTitle $cleanArtist",
                                artistName = cleanArtist
                            )
                        }
                    }
                    add {
                        runLrcLibCall {
                            lrcLibApiService.searchLyrics(
                                trackName = cleanTitle,
                                artistName = cleanArtist
                            )
                        }
                    }
                    val durationSec = durationMs?.let { (it / 1000).toInt() }?.takeIf { it > 0 }
                    if (durationSec != null) {
                        add {
                            runLrcLibCall {
                                lrcLibApiService.getLyrics(
                                    trackName = cleanTitle,
                                    artistName = cleanArtist,
                                    albumName = album.orEmpty(),
                                    duration = durationSec
                                )?.let { arrayOf(it) }
                            }
                        }
                    }
                }
                add {
                    runLrcLibCall {
                        lrcLibApiService.searchLyrics(trackName = cleanTitle)
                    }
                }
                add {
                    runLrcLibCall {
                        lrcLibApiService.searchLyrics(query = cleanTitle)
                    }
                }
            }

            queries.map { block -> async { block() } }
                .awaitAll()
                .flatMap { it?.toList().orEmpty() }
                .distinctBy { it.id }
        }

        return responses.mapNotNull { r ->
            val raw = r.preferredLyricsContent() ?: return@mapNotNull null
            val hasSynced = r.hasSyncedContent
            LyricsProviderCandidate(
                externalId = r.id.toString(),
                providerId = id,
                title = r.name,
                artistName = r.artistName,
                albumName = r.albumName,
                durationSec = r.duration.takeIf { it > 0.0 },
                rawLyrics = raw,
                hasSyncedContent = hasSynced,
                hasWordSync = !r.lyricsfile.isNullOrBlank(),
            )
        }
    }
}
