package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LyricsOrchestratorTest {

    private val sampleSong = Song(
        id = "1",
        title = "Kesariya",
        artist = "Arijit Singh",
        artistId = 10L,
        album = "Brahmastra",
        albumId = 20L,
        path = "/music/kesariya.mp3",
        duration = 268_000L,
        albumArtUriString = null,
        contentUriString = "content://media/1",
        mimeType = "audio/mpeg",
        bitrate = 320,
        sampleRate = 44100,
    )

    private fun fakeProvider(
        id: LyricsProviderId,
        delayMs: Long = 0L,
        throws: Boolean = false,
        synced: Boolean = true,
        rawLyrics: String = if (synced) "[00:10.00]Kesariya tera ishq hai piya" else "Kesariya tera ishq hai piya",
    ): LyricsProvider = object : LyricsProvider {
        override val id: LyricsProviderId = id
        override suspend fun search(
            title: String,
            artist: String?,
            album: String?,
            durationMs: Long?,
        ): List<LyricsProviderCandidate> {
            if (delayMs > 0L) delay(delayMs)
            if (throws) throw RuntimeException("Simulated failure")
            return listOf(
                LyricsProviderCandidate(
                    externalId = "id-${id.name}",
                    providerId = id,
                    title = "Kesariya",
                    artistName = "Arijit Singh",
                    durationSec = 268.0,
                    rawLyrics = rawLyrics,
                    hasSyncedContent = synced,
                    hasWordSync = false,
                )
            )
        }
    }

    @Test
    fun `priority order scans lower ranked provider when preferSynced is true and higher only has plain`() = runTest {
        val orchestrator = MultiProviderLyricsOrchestrator(
            providersById = mapOf(
                LyricsProviderId.JIOSAAVN to fakeProvider(LyricsProviderId.JIOSAAVN, synced = false),
                LyricsProviderId.LRCLIB to fakeProvider(LyricsProviderId.LRCLIB, synced = true),
            ),
            strategyProvider = { LyricsSearchStrategy.PRIORITY_ORDER },
            preferSyncedProvider = { true },
            enabledProvidersProvider = { setOf(LyricsProviderId.JIOSAAVN, LyricsProviderId.LRCLIB) },
            providerOrderProvider = { listOf(LyricsProviderId.JIOSAAVN, LyricsProviderId.LRCLIB) },
        )

        val result = orchestrator.fetchBestMatch(sampleSong)
        assertNotNull(result)
        assertEquals(LyricsProviderId.LRCLIB, result!!.providerId)
    }

    @Test
    fun `auto fastest returns fast synced winner immediately over slower providers`() = runTest {
        val orchestrator = MultiProviderLyricsOrchestrator(
            providersById = mapOf(
                LyricsProviderId.LRCLIB to fakeProvider(LyricsProviderId.LRCLIB, delayMs = 600L, synced = true),
                LyricsProviderId.LRC_RED to fakeProvider(LyricsProviderId.LRC_RED, delayMs = 15L, synced = true),
                LyricsProviderId.NETEASE to fakeProvider(LyricsProviderId.NETEASE, throws = true),
            ),
            strategyProvider = { LyricsSearchStrategy.AUTO_FASTEST },
            preferSyncedProvider = { true },
            enabledProvidersProvider = { setOf(LyricsProviderId.LRCLIB, LyricsProviderId.LRC_RED, LyricsProviderId.NETEASE) },
            providerOrderProvider = { listOf(LyricsProviderId.LRCLIB, LyricsProviderId.LRC_RED, LyricsProviderId.NETEASE) },
        )

        val result = orchestrator.fetchBestMatch(sampleSong)
        assertNotNull(result)
        assertEquals(LyricsProviderId.LRC_RED, result!!.providerId)
    }

    @Test
    fun `auto fastest waits within grace window when plain arrives before slower synced`() = runTest {
        val orchestrator = MultiProviderLyricsOrchestrator(
            providersById = mapOf(
                LyricsProviderId.JIOSAAVN to fakeProvider(LyricsProviderId.JIOSAAVN, delayMs = 10L, synced = false),
                LyricsProviderId.BETTER_LYRICS to fakeProvider(LyricsProviderId.BETTER_LYRICS, delayMs = 80L, synced = true),
            ),
            strategyProvider = { LyricsSearchStrategy.AUTO_FASTEST },
            preferSyncedProvider = { true },
            enabledProvidersProvider = { setOf(LyricsProviderId.JIOSAAVN, LyricsProviderId.BETTER_LYRICS) },
            providerOrderProvider = { listOf(LyricsProviderId.JIOSAAVN, LyricsProviderId.BETTER_LYRICS) },
            syncedGraceWindowMs = 500L,
        )

        val result = orchestrator.fetchBestMatch(sampleSong)
        assertNotNull(result)
        assertEquals(LyricsProviderId.BETTER_LYRICS, result!!.providerId)
    }

    @Test
    fun `provider order and enabled serialization round-trips safely with defaults`() {
        val customOrder = LyricsProviderId.parseOrder("YOUTUBE,JIOSAAVN")
        assertEquals(LyricsProviderId.YOUTUBE, customOrder[0])
        assertEquals(LyricsProviderId.JIOSAAVN, customOrder[1])
        assertEquals(LyricsProviderId.DEFAULT_ORDER.size, customOrder.size)

        val parsedEnabled = LyricsProviderId.parseEnabled("")
        assertEquals(LyricsProviderId.DEFAULT_ENABLED, parsedEnabled)
    }
}
