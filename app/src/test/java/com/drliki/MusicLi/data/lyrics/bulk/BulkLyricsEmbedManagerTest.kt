package com.drliki.MusicLi.data.lyrics.bulk

import android.net.Uri
import com.google.gson.Gson
import com.drliki.MusicLi.data.database.SongEntity
import com.drliki.MusicLi.data.database.SourceType
import com.drliki.MusicLi.data.model.Lyrics
import com.drliki.MusicLi.data.model.Song
import com.drliki.MusicLi.data.model.SyncedLine
import com.drliki.MusicLi.data.network.lyrics.providers.BatchLyricsLookupResult
import com.drliki.MusicLi.data.network.lyrics.providers.LyricsProviderId
import com.drliki.MusicLi.data.repository.LyricsSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BulkLyricsEmbedManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `startNewRun and processQueueLoop embeds synced lyrics and skips already synced`() = runTest {
        val syncedSongFile = tempFolder.newFile("already_synced.mp3")
        val targetSongFile = tempFolder.newFile("needs_lyrics.mp3")
        val noMatchFile = tempFolder.newFile("no_match.mp3")

        val entities = listOf(
            songEntity(id = 1L, title = "Alpha Synced", path = syncedSongFile.absolutePath, lyrics = "[00:01.00]Already here"),
            songEntity(id = 2L, title = "Beta Missing", path = targetSongFile.absolutePath, lyrics = null),
            songEntity(id = 3L, title = "Gamma NoMatch", path = noMatchFile.absolutePath, lyrics = null),
        )

        val env = FakeBulkEnv(
            songs = entities,
            lookupHandler = { song, _ ->
                when (song.id) {
                    "2" -> BatchLyricsLookupResult.Matched(
                        sampleSearchResult(
                            providerId = LyricsProviderId.LRCLIB,
                            rawLyrics = "[00:05.00]Synced line",
                        ),
                    )
                    "3" -> BatchLyricsLookupResult.NotFound(listOf(LyricsProviderId.LRCLIB))
                    else -> error("Should not lookup already synced song ${song.id}")
                }
            },
        )
        val scheduler = FakeWorkScheduler()
        val checkpointFile = File(tempFolder.root, "bulk_state.json")
        val manager = BulkLyricsEmbedManager(
            context = null,
            env = env,
            userPreferencesRepository = null,
            gson = Gson(),
            checkpointFile = checkpointFile,
            scheduler = scheduler,
            clockMs = { 1_000_000L },
        )

        manager.startNewRun(
            BulkLyricsEmbedOptions(
                syncedOnly = true,
                skipExistingSynced = true,
                delayBetweenSongsMs = 0L,
            ),
        )
        assertEquals(1, scheduler.immediateCount)

        val endStatus = manager.processQueueLoop()
        val ui = manager.uiState.value

        assertEquals(BulkLyricsEmbedStatus.COMPLETED, endStatus)
        assertEquals(3, ui.totalCount)
        assertEquals(3, ui.processedCount)
        assertEquals(0, ui.remainingCount)
        assertEquals(1, ui.skippedCount)
        assertEquals(1, ui.embeddedCount)
        assertEquals(1, ui.notFoundCount)
        assertEquals(0, ui.failedCount)
        assertEquals(listOf(2L to "[00:05.00]Synced line"), env.embeddedCalls)
    }

    @Test
    fun `rate limit pauses without advancing index and persists checkpoint across restart`() = runTest {
        val song1File = tempFolder.newFile("track1.mp3")
        val song2File = tempFolder.newFile("track2.mp3")
        val entities = listOf(
            songEntity(id = 10L, title = "First", path = song1File.absolutePath),
            songEntity(id = 20L, title = "Second", path = song2File.absolutePath),
        )

        var rateLimitedFirst = true
        val env = FakeBulkEnv(
            songs = entities,
            lookupHandler = { song, _ ->
                if (song.id == "10" && rateLimitedFirst) {
                    BatchLyricsLookupResult.RateLimited(
                        providerId = LyricsProviderId.BETTER_LYRICS,
                        retryAfterMs = 45_000L,
                        providersTried = listOf(LyricsProviderId.LRCLIB, LyricsProviderId.BETTER_LYRICS),
                    )
                } else {
                    BatchLyricsLookupResult.Matched(
                        sampleSearchResult(
                            providerId = LyricsProviderId.LRCLIB,
                            rawLyrics = "[00:02.00]Resumed line",
                        ),
                    )
                }
            },
        )
        val scheduler = FakeWorkScheduler()
        val checkpointFile = File(tempFolder.root, "bulk_state.json")
        var nowMs = 500_000L

        val manager1 = BulkLyricsEmbedManager(
            context = null,
            env = env,
            userPreferencesRepository = null,
            gson = Gson(),
            checkpointFile = checkpointFile,
            scheduler = scheduler,
            clockMs = { nowMs },
        )

        manager1.startNewRun(BulkLyricsEmbedOptions(delayBetweenSongsMs = 0L))
        val firstRunStatus = manager1.processQueueLoop()

        assertEquals(BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT, firstRunStatus)
        assertEquals(listOf(45_000L), scheduler.delayedDelays)
        val pausedUi = manager1.uiState.value
        assertEquals(0, pausedUi.processedCount)
        assertEquals(2, pausedUi.remainingCount)
        assertEquals(545_000L, pausedUi.resumeAtEpochMs)
        assertEquals(LyricsProviderId.BETTER_LYRICS.displayName, pausedUi.rateLimitProviderLabel)

        // Simulate app/process restart restoring checkpoint from disk
        rateLimitedFirst = false
        nowMs = 545_500L
        val manager2 = BulkLyricsEmbedManager(
            context = null,
            env = env,
            userPreferencesRepository = null,
            gson = Gson(),
            checkpointFile = checkpointFile,
            scheduler = scheduler,
            clockMs = { nowMs },
        )
        assertEquals(BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT, manager2.uiState.value.status)
        assertTrue(manager2.prepareResumeFromWorker())

        val secondRunStatus = manager2.processQueueLoop()
        val doneUi = manager2.uiState.value
        assertEquals(BulkLyricsEmbedStatus.COMPLETED, secondRunStatus)
        assertEquals(2, doneUi.embeddedCount)
        assertEquals(0, doneUi.remainingCount)
        assertNull(doneUi.resumeAtEpochMs)
    }

    @Test
    fun `write permission failure pauses cleanly in NEEDS_PERMISSION on the same song`() = runTest {
        val songFile = tempFolder.newFile("locked.mp3")
        val entities = listOf(songEntity(id = 99L, title = "Locked Track", path = songFile.absolutePath))

        val env = FakeBulkEnv(
            songs = entities,
            lookupHandler = { _, _ ->
                BatchLyricsLookupResult.Matched(
                    sampleSearchResult(
                        providerId = LyricsProviderId.LRCLIB,
                        rawLyrics = "[00:01.00]Hi",
                    ),
                )
            },
            writeResultHandler = {
                BulkLyricsWriteResult(
                    success = false,
                    noWritePermission = true,
                    errorMessage = "No MediaStore write grant",
                )
            },
        )
        val scheduler = FakeWorkScheduler()
        val checkpointFile = File(tempFolder.root, "bulk_state.json")
        val manager = BulkLyricsEmbedManager(
            context = null,
            env = env,
            userPreferencesRepository = null,
            gson = Gson(),
            checkpointFile = checkpointFile,
            scheduler = scheduler,
            clockMs = { 1_000L },
        )

        manager.startNewRun(BulkLyricsEmbedOptions(delayBetweenSongsMs = 0L))
        val endStatus = manager.processQueueLoop()

        assertEquals(BulkLyricsEmbedStatus.NEEDS_PERMISSION, endStatus)
        assertEquals(0, manager.uiState.value.processedCount)
        assertEquals(1, manager.uiState.value.remainingCount)
        assertEquals(99L, manager.currentCheckpointForTest().pendingPermissionSongId)
    }

    private fun songEntity(
        id: Long,
        title: String,
        path: String,
        lyrics: String? = null,
    ) = SongEntity(
        id = id,
        title = title,
        artistName = "Artist $id",
        artistId = id,
        albumArtist = "Artist $id",
        albumName = "Album",
        albumId = 1L,
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        duration = 180_000L,
        genre = "Pop",
        filePath = path,
        parentDirectoryPath = File(path).parent.orEmpty(),
        lyrics = lyrics,
        sourceType = SourceType.LOCAL,
    )

    private fun sampleSearchResult(
        providerId: LyricsProviderId,
        rawLyrics: String,
    ) = LyricsSearchResult(
        id = "${providerId.name}:1",
        providerId = providerId,
        providerLabel = providerId.displayName,
        title = "Song",
        artistName = "Artist",
        albumName = "Album",
        durationSec = 180.0,
        hasSyncedContent = true,
        score = 100,
        lyrics = Lyrics(
            synced = listOf(SyncedLine(time = 1000, line = "Line")),
            areFromRemote = true,
        ),
        rawLyrics = rawLyrics,
    )

    private class FakeWorkScheduler : BulkLyricsWorkScheduler {
        var immediateCount = 0
        val delayedDelays = mutableListOf<Long>()
        var whenOnlineCount = 0
        var cancelCount = 0

        override fun enqueueImmediate() {
            immediateCount++
        }

        override fun enqueueDelayed(delayMs: Long) {
            delayedDelays += delayMs
        }

        override fun enqueueWhenOnline() {
            whenOnlineCount++
        }

        override fun cancel() {
            cancelCount++
        }
    }

    private class FakeBulkEnv(
        private val songs: List<SongEntity>,
        private val lookupHandler: suspend (Song, Boolean) -> BatchLyricsLookupResult,
        private val writeResultHandler: () -> BulkLyricsWriteResult = { BulkLyricsWriteResult(success = true) },
    ) : BulkLyricsEnvAdapter {
        val embeddedCalls = mutableListOf<Pair<Long, String>>()

        override suspend fun loadAllSongs(): List<SongEntity> = songs

        override suspend fun loadSongById(songId: Long): SongEntity? = songs.firstOrNull { it.id == songId }

        override suspend fun getStoredDbLyrics(songId: Long): String? = null

        override suspend fun readFileEmbeddedLyrics(filePath: String): String? = null

        override suspend fun lookupLyricsByPriority(
            song: Song,
            syncedOnly: Boolean,
        ): BatchLyricsLookupResult = lookupHandler(song, syncedOnly)

        override suspend fun embedSongLyrics(
            song: Song,
            lrcText: String,
            saveLrcSidecar: Boolean,
        ): BulkLyricsWriteResult {
            val res = writeResultHandler()
            if (res.success) {
                embeddedCalls += (song.id.toLong() to lrcText)
            }
            return res
        }

        override fun hasFileWritePermission(songId: Long): Boolean = true

        override fun getMediaStoreUri(songId: Long): Uri? = null

        override fun fileExists(path: String): Boolean = File(path).exists()
    }
}
