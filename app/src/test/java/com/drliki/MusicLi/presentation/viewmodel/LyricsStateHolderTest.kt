package com.drliki.MusicLi.presentation.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.drliki.MusicLi.data.media.SongMetadataEditor
import com.drliki.MusicLi.data.model.Lyrics
import com.drliki.MusicLi.data.model.Song
import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import com.drliki.MusicLi.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsStateHolderTest {

    @Test
    fun withPersistedLyrics_replacesAlbumArtUriWhenMetadataWriteRefreshesArtworkPath() {
        val originalSong = testSong(albumArtUriString = "file:///cache/song_art_1_old.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "New lyrics",
            refreshedAlbumArtUri = "file:///cache/song_art_1_new.jpg"
        )

        assertThat(updatedSong.lyrics).isEqualTo("New lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("file:///cache/song_art_1_new.jpg")
    }

    @Test
    fun withPersistedLyrics_keepsExistingAlbumArtUriWhenMetadataWriteDoesNotReturnOne() {
        val originalSong = testSong(albumArtUriString = "content://art/song_art_1.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "Imported lyrics",
            refreshedAlbumArtUri = null
        )

        assertThat(updatedSong.lyrics).isEqualTo("Imported lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("content://art/song_art_1.jpg")
    }

    @Test
    fun fetchLyricsForSong_usesStoredLyricsWithoutRemoteFetch() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor
        )
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = "content://art/song_art_1.jpg").copy(
            lyrics = "Stored lyrics"
        )
        val storedLyrics = Lyrics(plain = listOf("Stored lyrics"), areFromRemote = false)

        holder.initialize(backgroundScope, callback, state)
        coEvery { musicRepository.getStoredLyrics(song) } returns (storedLyrics to "Stored lyrics")

        holder.searchUiState.test {
            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Idle)

            holder.fetchLyricsForSong(
                song = song,
                forcePickResults = false,
                sourcePreference = com.drliki.MusicLi.data.model.LyricsSourcePreference.API_FIRST
            ) { "Lyrics already available" }

            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Loading)
            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Success(storedLyrics))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(holder.searchUiState.value).isEqualTo(LyricsSearchUiState.Success(storedLyrics))
        coVerify(exactly = 1) { musicRepository.getStoredLyrics(song) }
        coVerify(exactly = 0) { musicRepository.getLyricsFromRemote(any()) }
        coVerify(exactly = 0) { musicRepository.searchRemoteLyrics(any()) }
    }

    @Test
    fun fetchLyrics_pickResultPrefillsAlbumWhenArtistUnknownAndRetainsManualQuery() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor
        )
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = null).copy(
            artist = "<unknown>",
            album = "After Hours"
        )
        val sampleResult = com.drliki.MusicLi.data.repository.LyricsSearchResult(
            id = "res_1",
            providerId = com.drliki.MusicLi.data.network.lyrics.providers.LyricsProviderId.LRCLIB,
            providerLabel = "LRCLIB",
            title = "Indian Summer",
            artistName = "Blood Cultures",
            albumName = "After Hours",
            durationSec = 295.0,
            hasSyncedContent = false,
            lyrics = Lyrics(plain = listOf("line"), areFromRemote = true),
            rawLyrics = "line",
        )

        holder.initialize(backgroundScope, callback, state)
        coEvery { musicRepository.searchRemoteLyrics(song) } returns Result.success("Indian Summer" to listOf(sampleResult))
        coEvery {
            musicRepository.searchRemoteLyricsByQuery("Custom Title", "Custom Album")
        } returns Result.success("Custom Title Custom Album" to listOf(sampleResult))

        holder.searchUiState.test {
            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Idle)

            holder.fetchLyricsForSong(
                song = song,
                forcePickResults = true,
                sourcePreference = com.drliki.MusicLi.data.model.LyricsSourcePreference.API_FIRST
            ) { "" }

            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Loading)
            val initialPick = awaitItem() as LyricsSearchUiState.PickResult
            assertThat(initialPick.queryTitle).isEqualTo("Indian Summer")
            assertThat(initialPick.queryArtist).isEqualTo("After Hours")

            holder.searchLyricsManually("  Custom Title  ", "  Custom Album  ")

            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Loading)
            val updatedPick = awaitItem() as LyricsSearchUiState.PickResult
            assertThat(updatedPick.queryTitle).isEqualTo("Custom Title")
            assertThat(updatedPick.queryArtist).isEqualTo("Custom Album")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun testSong(albumArtUriString: String?): Song {
        return Song(
            id = "1",
            title = "Indian Summer",
            artist = "Blood Cultures",
            album = "Happy Birthday",
            path = "/music/indian-summer.mp3",
            contentUriString = "content://media/external/audio/media/1",
            albumArtUriString = albumArtUriString,
            duration = 295_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100,
            artistId = 1L,
            albumId = 1L
        )
    }

    private class RecordingLyricsLoadCallback : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) = Unit

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) = Unit
    }
}
