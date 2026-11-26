package com.drliki.MusicLi.data.explore

import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreRepositoryRefreshAndArtistTest {

    private fun fakePrefs(baseUrl: String = "https://saavn.example/api"): UserPreferencesRepository =
        mockk {
            every { exploreApiBaseUrlFlow } returns flowOf(baseUrl)
        }

    private fun songSearchResponse(songId: String, songTitle: String) = SaavnSongSearchResponse(
        success = true,
        data = SaavnSongSearchResponse.Data(
            results = listOf(
                SaavnSongDto(
                    id = songId,
                    name = songTitle,
                    duration = 200.0,
                    artists = SaavnArtistsGroupDto(
                        primary = listOf(SaavnArtistRefDto(id = "art_1", name = "Artist One"))
                    ),
                )
            )
        )
    )

    @Test
    fun `getHomeFeed advances page and bypasses cache on forceRefresh`() = runTest {
        val api = mockk<JioSaavnApiClient>()
        val repo = ExploreRepository(api, fakePrefs())

        coEvery { api.searchSongs(any(), any(), 0, any()) } returns songSearchResponse("s0", "Page Zero")
        coEvery { api.searchSongs(any(), any(), 1, any()) } returns songSearchResponse("s1", "Page One")
        coEvery { api.searchAlbums(any(), any(), any(), any()) } returns SaavnAlbumsSearchResponse(success = true)
        coEvery { api.searchPlaylists(any(), any(), any(), any()) } returns SaavnPlaylistsSearchResponse(success = true)
        coEvery { api.searchArtists(any(), any(), any(), any()) } returns SaavnArtistsSearchResponse(success = true)

        val first = repo.getHomeFeed("All", forceRefresh = false).getOrThrow()
        val cached = repo.getHomeFeed("All", forceRefresh = false).getOrThrow()
        val refreshed = repo.getHomeFeed("All", forceRefresh = true).getOrThrow()

        assertEquals("Page Zero", first.trendingSongs.single().title)
        assertEquals("Page Zero", cached.trendingSongs.single().title)
        assertEquals("Page One", refreshed.trendingSongs.single().title)
        coVerify(exactly = 1) { api.searchSongs(any(), "trending", 0, any()) }
        coVerify(exactly = 1) { api.searchSongs(any(), "trending", 1, any()) }
    }

    @Test
    fun `resolveArtistDetailForStream prefers embedded artist id on Saavn track`() = runTest {
        val api = mockk<JioSaavnApiClient>()
        val repo = ExploreRepository(api, fakePrefs())

        coEvery { api.getSongById(any(), "song_42", false) } returns SaavnSongResponse(
            success = true,
            data = listOf(
                SaavnSongDto(
                    id = "song_42",
                    name = "Track 42",
                    artists = SaavnArtistsGroupDto(
                        primary = listOf(
                            SaavnArtistRefDto(id = "art_10", name = "First Artist"),
                            SaavnArtistRefDto(id = "art_20", name = "Target Artist"),
                        )
                    )
                )
            )
        )
        coEvery { api.getArtistById(any(), "art_20", any(), any()) } returns SaavnArtistDetailResponse(
            success = true,
            data = SaavnArtistDetailResponse.Data(
                id = "art_20",
                name = "Target Artist",
                dominantType = "Singer",
                followerCount = 1234,
            )
        )

        val artist = repo.resolveArtistDetailForStream("song_42", "Target Artist").getOrThrow()

        assertEquals("art_20", artist.id)
        assertEquals("Target Artist", artist.name)
        coVerify(exactly = 0) { api.searchArtists(any(), any(), any(), any()) }
    }

    @Test
    fun `resolveArtistDetailForStream falls back to searchArtists when song id is null`() = runTest {
        val api = mockk<JioSaavnApiClient>()
        val repo = ExploreRepository(api, fakePrefs())

        coEvery { api.searchArtists(any(), "Arijit Singh", 0, 5) } returns SaavnArtistsSearchResponse(
            success = true,
            data = SaavnArtistsSearchResponse.Data(
                results = listOf(
                    SaavnArtistRefDto(id = "art_other", name = "Arijit Remix"),
                    SaavnArtistRefDto(id = "459320", name = "Arijit Singh"),
                )
            )
        )
        coEvery { api.getArtistById(any(), "459320", any(), any()) } returns SaavnArtistDetailResponse(
            success = true,
            data = SaavnArtistDetailResponse.Data(
                id = "459320",
                name = "Arijit Singh",
                dominantType = "Singer",
            )
        )

        val result = repo.resolveArtistDetailForStream(null, "Arijit Singh")

        assertTrue(result.isSuccess)
        assertEquals("459320", result.getOrThrow().id)
    }
}
