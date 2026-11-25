package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.LyricsSearchResult
import com.lostf1sh.pixelplayeross.utils.LyricsUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates all remote lyric providers with user-configured search strategy
 * (Auto Fastest concurrent race vs Priority Order waterfall), enabled filters,
 * synced preference grace windows, and rolling latency metrics.
 */
@Singleton
class MultiProviderLyricsOrchestrator(
    private val providersById: Map<LyricsProviderId, LyricsProvider>,
    private val strategyProvider: suspend () -> LyricsSearchStrategy,
    private val preferSyncedProvider: suspend () -> Boolean,
    private val enabledProvidersProvider: suspend () -> Set<LyricsProviderId>,
    private val providerOrderProvider: suspend () -> List<LyricsProviderId>,
    private val perProviderTimeoutMs: Long = DEFAULT_PROVIDER_TIMEOUT_MS,
    private val syncedGraceWindowMs: Long = DEFAULT_SYNCED_GRACE_WINDOW_MS,
) {
    @Inject
    constructor(
        lrcLibLyricsProvider: LrcLibLyricsProvider,
        betterLyricsProvider: BetterLyricsProvider,
        lrcRedProvider: LrcRedProvider,
        paxsenixLyricsProvider: PaxsenixLyricsProvider,
        kuGouLyricsProvider: KuGouLyricsProvider,
        jioSaavnLyricsProvider: JioSaavnLyricsProvider,
        youTubeLyricsProvider: YouTubeLyricsProvider,
        netEaseLyricsProvider: NetEaseLyricsProvider,
        lyricsOvhProvider: LyricsOvhProvider,
        userPreferencesRepository: UserPreferencesRepository,
    ) : this(
        providersById = listOf(
            lrcLibLyricsProvider,
            betterLyricsProvider,
            lrcRedProvider,
            paxsenixLyricsProvider,
            kuGouLyricsProvider,
            jioSaavnLyricsProvider,
            youTubeLyricsProvider,
            netEaseLyricsProvider,
            lyricsOvhProvider,
        ).associateBy { it.id },
        strategyProvider = { userPreferencesRepository.lyricsSearchStrategyFlow.first() },
        preferSyncedProvider = { userPreferencesRepository.lyricsPreferSyncedFlow.first() },
        enabledProvidersProvider = { userPreferencesRepository.enabledLyricsProvidersFlow.first() },
        providerOrderProvider = { userPreferencesRepository.lyricsProviderOrderFlow.first() },
    )

    companion object {
        private const val TAG = "MultiLyricsOrchestrator"
        private const val DEFAULT_PROVIDER_TIMEOUT_MS = 4_500L
        private const val DEFAULT_SYNCED_GRACE_WINDOW_MS = 1_200L
    }

    private val ewmaLatencyMs = ConcurrentHashMap<LyricsProviderId, Long>()

    fun lastKnownLatencyMs(providerId: LyricsProviderId): Long? = ewmaLatencyMs[providerId]

    private suspend fun activeOrderedProviders(): List<LyricsProvider> {
        val enabled = enabledProvidersProvider()
        val order = providerOrderProvider()
        return order.filter { it in enabled }.mapNotNull { providersById[it] }
    }

    suspend fun fetchBestMatch(song: Song): LyricsSearchResult? {
        val strategy = strategyProvider()
        val preferSynced = preferSyncedProvider()
        val ordered = activeOrderedProviders()
        if (ordered.isEmpty()) return null

        return when (strategy) {
            LyricsSearchStrategy.AUTO_FASTEST -> fetchFastest(song, ordered, preferSynced)
            LyricsSearchStrategy.PRIORITY_ORDER -> fetchByPriority(song, ordered, preferSynced)
        }
    }

    suspend fun searchCandidates(song: Song): List<LyricsSearchResult> {
        val ordered = activeOrderedProviders()
        if (ordered.isEmpty()) return emptyList()

        val rawCandidates = collectAllCandidates(
            providers = ordered,
            title = song.title,
            artist = song.displayArtist,
            album = song.album,
            durationMs = song.duration
        )
        if (rawCandidates.isEmpty()) return emptyList()

        val orderList = ordered.map { it.id }
        val ranked = LyricsMatchScorer.rankCandidates(
            song = song,
            candidates = rawCandidates,
            mode = LyricsMatchMode.CANDIDATE,
            providerOrder = orderList
        )
        return ranked.mapNotNull { (candidate, score) ->
            toSearchResult(candidate, score)
        }.distinctBy { "${it.providerId.name}|${LyricsMatchScorer.normalizeForMatch(it.title)}|${it.hasSyncedContent}" }
    }

    suspend fun searchByQuery(title: String, artist: String?): List<LyricsSearchResult> {
        val ordered = activeOrderedProviders()
        if (ordered.isEmpty()) return emptyList()

        val rawCandidates = collectAllCandidates(
            providers = ordered,
            title = title,
            artist = artist,
            album = null,
            durationMs = null
        )
        if (rawCandidates.isEmpty()) return emptyList()

        val orderMap = ordered.map { it.id }.withIndex().associate { it.value to it.index }
        return rawCandidates
            .mapNotNull { toSearchResult(it, score = 0) }
            .sortedWith(
                compareByDescending<LyricsSearchResult> { it.hasWordSync }
                    .thenByDescending { it.hasSyncedContent }
                    .thenBy { orderMap[it.providerId] ?: Int.MAX_VALUE }
            )
            .distinctBy { "${it.providerId.name}|${LyricsMatchScorer.normalizeForMatch(it.title)}|${it.rawLyrics.take(80)}" }
    }

    private suspend fun fetchByPriority(
        song: Song,
        orderedProviders: List<LyricsProvider>,
        preferSynced: Boolean,
    ): LyricsSearchResult? {
        var plainFallback: LyricsSearchResult? = null
        val orderList = orderedProviders.map { it.id }

        for (provider in orderedProviders) {
            val startedAt = System.currentTimeMillis()
            val candidates = runSafely(provider) {
                provider.search(song.title, song.displayArtist, song.album, song.duration)
            }
            recordLatency(provider.id, System.currentTimeMillis() - startedAt)
            if (candidates.isEmpty()) continue

            val scored = LyricsMatchScorer.rankCandidates(
                song = song,
                candidates = candidates,
                mode = LyricsMatchMode.AUTOMATIC,
                providerOrder = orderList
            )
            val best = scored.firstOrNull()?.let { (cand, score) -> toSearchResult(cand, score) } ?: continue
            if (best.hasSyncedContent || !preferSynced) {
                return best
            }
            if (plainFallback == null) {
                plainFallback = best
            }
        }
        return plainFallback
    }

    private suspend fun fetchFastest(
        song: Song,
        orderedProviders: List<LyricsProvider>,
        preferSynced: Boolean,
    ): LyricsSearchResult? = supervisorScope {
        val orderList = orderedProviders.map { it.id }
        val channel = Channel<Pair<LyricsProviderId, LyricsSearchResult?>>(capacity = orderedProviders.size)

        val jobs = orderedProviders.map { provider ->
            launch {
                val startedAt = System.currentTimeMillis()
                val candidates = runSafely(provider) {
                    provider.search(song.title, song.displayArtist, song.album, song.duration)
                }
                recordLatency(provider.id, System.currentTimeMillis() - startedAt)
                val best = if (candidates.isEmpty()) {
                    null
                } else {
                    LyricsMatchScorer.rankCandidates(
                        song = song,
                        candidates = candidates,
                        mode = LyricsMatchMode.AUTOMATIC,
                        providerOrder = orderList
                    ).firstOrNull()?.let { (cand, score) -> toSearchResult(cand, score) }
                }
                channel.trySend(provider.id to best)
            }
        }

        try {
            var remaining = orderedProviders.size
            val pendingSyncedCapable = orderedProviders
                .filter { it.id.supportsSynced }
                .map { it.id }
                .toMutableSet()
            var bestPlain: LyricsSearchResult? = null
            var graceDeadlineMs: Long? = null

            while (remaining > 0) {
                val receiveTimeout = graceDeadlineMs?.let { (it - System.currentTimeMillis()).coerceAtLeast(1L) }
                val message = if (receiveTimeout != null) {
                    withTimeoutOrNull(receiveTimeout) { channel.receive() }
                } else {
                    channel.receive()
                }

                if (message == null) {
                    // Grace window expired while waiting for slower synced provider
                    return@supervisorScope bestPlain
                }

                val (providerId, result) = message
                remaining--
                pendingSyncedCapable.remove(providerId)

                if (result != null) {
                    if (result.hasSyncedContent || !preferSynced || pendingSyncedCapable.isEmpty()) {
                        return@supervisorScope result
                    }
                    if (bestPlain == null || result.score > bestPlain.score) {
                        bestPlain = result
                    }
                    if (graceDeadlineMs == null) {
                        graceDeadlineMs = System.currentTimeMillis() + syncedGraceWindowMs
                    }
                } else if (bestPlain != null && pendingSyncedCapable.isEmpty()) {
                    return@supervisorScope bestPlain
                }
            }

            bestPlain
        } finally {
            jobs.forEach { it.cancel() }
            channel.close()
        }
    }

    private suspend fun collectAllCandidates(
        providers: List<LyricsProvider>,
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
    ): List<LyricsProviderCandidate> = supervisorScope {
        val results = mutableListOf<LyricsProviderCandidate>()
        val channel = Channel<List<LyricsProviderCandidate>>(capacity = providers.size)
        providers.forEach { provider ->
            launch {
                val startedAt = System.currentTimeMillis()
                val found = runSafely(provider) {
                    provider.search(title, artist, album, durationMs)
                }
                recordLatency(provider.id, System.currentTimeMillis() - startedAt)
                channel.trySend(found)
            }
        }
        repeat(providers.size) {
            results += channel.receive()
        }
        channel.close()
        results
    }

    private suspend fun runSafely(
        provider: LyricsProvider,
        block: suspend () -> List<LyricsProviderCandidate>,
    ): List<LyricsProviderCandidate> {
        return try {
            withTimeoutOrNull(perProviderTimeoutMs) { block() }.orEmpty()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.tag(TAG).d(t, "Lyrics provider ${provider.id.name} failed")
            emptyList()
        }
    }

    private fun recordLatency(providerId: LyricsProviderId, elapsedMs: Long) {
        val prev = ewmaLatencyMs[providerId]
        ewmaLatencyMs[providerId] = if (prev == null) elapsedMs else ((prev * 7 + elapsedMs * 3) / 10)
    }

    private fun toSearchResult(candidate: LyricsProviderCandidate, score: Int): LyricsSearchResult? {
        val parsed = LyricsUtils.parseLyrics(candidate.rawLyrics).copy(areFromRemote = true)
        if (parsed.synced.isNullOrEmpty() && parsed.plain.isNullOrEmpty()) return null
        val isSynced = candidate.hasSyncedContent && !parsed.synced.isNullOrEmpty()
        val canonicalRaw = if (candidate.rawLyrics.trimStart().startsWith("<")) {
            // Convert TTML once for Room/LRC storage while keeping rich parsed synced lines
            com.lostf1sh.pixelplayeross.utils.TtmlLyricsParser.parseToEnhancedLrc(candidate.rawLyrics)
                ?: candidate.rawLyrics
        } else {
            candidate.rawLyrics
        }

        return LyricsSearchResult(
            id = candidate.compositeId,
            providerId = candidate.providerId,
            providerLabel = candidate.providerId.displayName,
            title = candidate.title,
            artistName = candidate.artistName,
            albumName = candidate.albumName,
            durationSec = candidate.durationSec,
            hasSyncedContent = isSynced,
            hasWordSync = candidate.hasWordSync,
            attributionUrl = candidate.attributionUrl,
            score = score,
            lyrics = parsed,
            rawLyrics = canonicalRaw,
        )
    }
}
