package com.drliki.MusicLi.presentation.viewmodel

import com.drliki.MusicLi.MainCoroutineExtension
import com.drliki.MusicLi.data.explore.ExploreHomeFeed
import com.drliki.MusicLi.data.explore.ExploreRepository
import com.drliki.MusicLi.data.explore.ExploreSearchResults
import com.drliki.MusicLi.data.explore.ExploreSong
import com.drliki.MusicLi.data.explore.ExploreTrackDownloader
import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class ExploreViewModelV120Test {

    private fun createViewModel(
        recentSearchesFlow: MutableStateFlow<List<String>> = MutableStateFlow(listOf("Arijit", "Lo-fi", "Shreya")),
        exploreRepository: ExploreRepository = mockk(relaxed = true),
        userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true),
    ): ExploreViewModel {
        every { userPreferencesRepository.exploreRecentSearchesFlow } returns recentSearchesFlow
        every { userPreferencesRepository.exploreDownloadQualityFlow } returns flowOf(320)
        every { userPreferencesRepository.exploreStreamQualityFlow } returns flowOf(320)
        every { userPreferencesRepository.exploreDownloadSafUriFlow } returns flowOf(null)
        every { userPreferencesRepository.exploreDownloadSubfoldersFlow } returns flowOf(true)
        every { userPreferencesRepository.exploreDownloadLyricsFlow } returns flowOf(true)
        every { userPreferencesRepository.exploreApiBaseUrlFlow } returns flowOf("https://saavn.example/api")

        coEvery { exploreRepository.getHomeFeed(any(), any()) } returns Result.success(ExploreHomeFeed())
        coEvery { exploreRepository.searchAll(any()) } returns Result.success(
            ExploreSearchResults(
                songs = listOf(
                    ExploreSong(
                        id = "song_1",
                        title = "Track One",
                        artist = "Artist One",
                        albumArtist = "Artist One",
                        albumId = "alb_1",
                        albumName = "Album One",
                        year = 2026,
                        releaseDate = null,
                        durationSeconds = 180,
                        language = "hindi",
                        label = null,
                        copyright = null,
                        hasLyrics = false,
                        lyricsText = null,
                        artworkUrl = null,
                        thumbnailUrl = null,
                        streamUrlsByQuality = emptyMap(),
                        saavnUrl = null,
                    )
                )
            )
        )

        val downloader = mockk<ExploreTrackDownloader>(relaxed = true) {
            every { downloads } returns MutableStateFlow(emptyList())
            every { downloadedSongIds } returns MutableStateFlow(emptySet())
        }

        return ExploreViewModel(
            exploreRepository = exploreRepository,
            downloader = downloader,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    @Test
    fun `removeRecentSearch removes matching item case-insensitively and persists list`() = runTest {
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        val viewModel = createViewModel(userPreferencesRepository = prefs)
        advanceUntilIdle()

        viewModel.removeRecentSearch("lo-fi")
        advanceUntilIdle()

        assertEquals(listOf("Arijit", "Shreya"), viewModel.uiState.value.recentSearches)
        coVerify { prefs.saveExploreRecentSearches(listOf("Arijit", "Shreya")) }
    }

    @Test
    fun `browseCategory sets filter and executes search without mutating recent searches`() = runTest {
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        val repo = mockk<ExploreRepository>(relaxed = true)
        val viewModel = createViewModel(
            exploreRepository = repo,
            userPreferencesRepository = prefs,
        )
        advanceUntilIdle()

        viewModel.browseCategory("Hindi new releases", ExploreFilterTab.ALBUMS)
        advanceUntilIdle()

        assertEquals("Hindi new releases", viewModel.uiState.value.query)
        assertEquals(ExploreFilterTab.ALBUMS, viewModel.uiState.value.selectedFilter)
        assertEquals(listOf("Arijit", "Lo-fi", "Shreya"), viewModel.uiState.value.recentSearches)
        assertEquals(1, viewModel.uiState.value.searchResults.songs.size)
        coVerify(exactly = 1) { repo.searchAll("Hindi new releases") }
        coVerify(exactly = 0) { prefs.saveExploreRecentSearches(any()) }
    }
}
