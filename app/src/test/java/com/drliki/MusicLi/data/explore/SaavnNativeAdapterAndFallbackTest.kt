package com.drliki.MusicLi.data.explore

import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class SaavnNativeAdapterAndFallbackTest {

    private fun encryptSaavnUrl(plainUrl: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec("38346591".toByteArray(StandardCharsets.US_ASCII), "DES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(plainUrl.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    @Test
    fun `isNativeSaavnApiUrl distinguishes direct api_php from REST mirrors`() {
        assertTrue(isNativeSaavnApiUrl("https://www.jiosaavn.com/api.php"))
        assertTrue(isNativeSaavnApiUrl("https://www.jiosaavn.com/api.php/"))
        assertTrue(isNativeSaavnApiUrl("https://mirror.example.org/custom/api.php"))
        assertFalse(isNativeSaavnApiUrl("https://melotune.harshbits.com/api/"))
        assertFalse(isNativeSaavnApiUrl("https://saavn.example/api"))
    }

    @Test
    fun `normalizeExploreApiBaseUrl preserves php endpoints and appends slash to REST mirrors`() {
        assertEquals(
            UserPreferencesRepository.DEFAULT_EXPLORE_API_BASE_URL,
            UserPreferencesRepository.normalizeExploreApiBaseUrl("   "),
        )
        assertEquals(
            "https://www.jiosaavn.com/api.php",
            UserPreferencesRepository.normalizeExploreApiBaseUrl("https://www.jiosaavn.com/api.php/"),
        )
        assertEquals(
            "https://example.org/api.php",
            UserPreferencesRepository.normalizeExploreApiBaseUrl("https://example.org/api.php"),
        )
        assertEquals(
            "https://melotune.harshbits.com/api/",
            UserPreferencesRepository.normalizeExploreApiBaseUrl("https://melotune.harshbits.com/api"),
        )
    }

    @Test
    fun `decryptSaavnMediaUrl decrypts DES ECB payload and upgrades http to https`() {
        val encrypted = encryptSaavnUrl("http://aac.saavncdn.com/123/yellow_96.mp4")
        val decrypted = decryptSaavnMediaUrl(encrypted)

        assertEquals("https://aac.saavncdn.com/123/yellow_96.mp4", decrypted)
        assertNull(decryptSaavnMediaUrl(null))
        assertNull(decryptSaavnMediaUrl("   "))
        assertNull(decryptSaavnMediaUrl("not-valid-base64!!!"))
    }

    @Test
    fun `buildDownloadUrlsFromDecrypted emits 96 160 and 320 kbps variants when 320 available`() {
        val variants320 = buildDownloadUrlsFromDecrypted(
            decryptedUrl = "https://aac.saavncdn.com/123/yellow_96.mp4?token=abc",
            has320Kbps = true,
        )
        assertEquals(listOf("96kbps", "160kbps", "320kbps"), variants320.map { it.quality })
        assertEquals(
            "https://aac.saavncdn.com/123/yellow_320.mp4?token=abc",
            variants320.last().url,
        )

        val variants160Only = buildDownloadUrlsFromDecrypted(
            decryptedUrl = "https://aac.saavncdn.com/123/yellow_96.mp4",
            has320Kbps = false,
        )
        assertEquals(listOf("96kbps", "160kbps"), variants160Only.map { it.quality })

        val noBitrateMatch = buildDownloadUrlsFromDecrypted(
            decryptedUrl = "https://aac.saavncdn.com/123/yellow.mp4",
            has320Kbps = true,
        )
        assertEquals(1, noBitrateMatch.size)
        assertEquals("320kbps", noBitrateMatch.single().quality)
    }

    @Test
    fun `buildSaavnImages expands 50 150 and 500 variants over https`() {
        val images = buildSaavnImages("http://c.saavncdn.com/191/Parachutes-English-2000-150x150.jpg")
        assertEquals(listOf("50x50", "150x150", "500x500"), images.map { it.quality })
        assertEquals(
            "https://c.saavncdn.com/191/Parachutes-English-2000-500x500.jpg",
            images.last().url,
        )
    }

    @Test
    fun `SaavnNativeParser parses song search trending album playlist artist and lyrics responses`() {
        val encrypted = encryptSaavnUrl("https://aac.saavncdn.com/593/yellow_96.mp4")
        val songItemJson = """
            {
              "id": "sg_yellow",
              "title": "Yellow",
              "type": "song",
              "year": "2000",
              "language": "english",
              "explicit_content": "0",
              "play_count": "9001",
              "image": "http://c.saavncdn.com/191/Parachutes-150x150.jpg",
              "perma_url": "https://www.jiosaavn.com/song/yellow/sg_yellow",
              "more_info": {
                "album_id": "alb_1",
                "album": "Parachutes",
                "duration": "266",
                "encrypted_media_url": "$encrypted",
                "320kbps": "true",
                "has_lyrics": "true",
                "artistMap": {
                  "primary_artists": [
                    {"id": "art_cp", "name": "Coldplay", "role": "music"}
                  ]
                }
              }
            }
        """.trimIndent()

        val songSearch = SaavnNativeParser.parseSongSearchResponse(
            """{"total": 1, "start": 0, "results": [$songItemJson]}""",
        )
        assertTrue(songSearch.success == true)
        val song = songSearch.data?.results?.single()!!
        assertEquals("Yellow", song.name)
        assertEquals("Coldplay", song.artists?.primary?.single()?.name)
        assertEquals("https://aac.saavncdn.com/593/yellow_320.mp4", song.downloadUrl?.last()?.url)

        val trendingSongs = SaavnNativeParser.parseTrendingSongs("[$songItemJson]")
        assertTrue(trendingSongs.success == true)
        assertEquals("sg_yellow", trendingSongs.data?.results?.single()?.id)

        val albumDetail = SaavnNativeParser.parseAlbumDetailResponse(
            """
            {
              "albumid": "alb_1",
              "title": "Parachutes",
              "year": "2000",
              "language": "english",
              "image": "https://c.saavncdn.com/191/Parachutes-150x150.jpg",
              "songs": [$songItemJson]
            }
            """.trimIndent(),
        )
        assertTrue(albumDetail.success == true)
        assertEquals("Parachutes", albumDetail.data?.name)
        assertEquals(1, albumDetail.data?.songs?.size)

        val playlistDetail = SaavnNativeParser.parsePlaylistDetailResponse(
            """
            {
              "id": "pl_1",
              "title": "English Top Hits",
              "list_count": "1",
              "list": [$songItemJson]
            }
            """.trimIndent(),
        )
        assertTrue(playlistDetail.success == true)
        assertEquals("English Top Hits", playlistDetail.data?.name)
        assertEquals(1, playlistDetail.data?.songs?.size)

        val artistDetail = SaavnNativeParser.parseArtistDetailResponse(
            """
            {
              "artistId": "art_cp",
              "name": "Coldplay",
              "follower_count": "500000",
              "dominantLanguage": "english",
              "topSongs": [$songItemJson],
              "topAlbums": [{"id": "alb_1", "title": "Parachutes", "year": "2000", "language": "english"}]
            }
            """.trimIndent(),
        )
        assertTrue(artistDetail.success == true)
        assertEquals("Coldplay", artistDetail.data?.name)
        assertEquals(500000, artistDetail.data?.followerCount)
        assertEquals(1, artistDetail.data?.topSongs?.size)
        assertEquals(1, artistDetail.data?.topAlbums?.size)

        val lyrics = SaavnNativeParser.parseLyricsResponse(
            """{"lyrics": "Look at the stars", "lyrics_copyright": "Sony"}""",
        )
        assertTrue(lyrics.success == true)
        assertEquals("Look at the stars", lyrics.data?.lyrics)
    }

    @Test
    fun `JioSaavnApiClient automatically falls back to melotune mirror when primary native endpoint fails`() =
        runTest {
            val requestedUrls = mutableListOf<String>()
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val req = chain.request()
                    val urlStr = req.url.toString()
                    requestedUrls += urlStr
                    if (req.url.host == "www.jiosaavn.com") {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(503)
                            .message("Service Unavailable")
                            .body("unavailable".toResponseBody("text/plain".toMediaType()))
                            .build()
                    } else {
                        val fallbackJson = """
                            {
                              "success": true,
                              "data": {
                                "total": 1,
                                "start": 0,
                                "results": [
                                  {"id": "fb_1", "name": "Fallback Song", "language": "kannada"}
                                ]
                              }
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(fallbackJson.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                })
                .build()

            val api = JioSaavnApiClient(client, Gson())
            val res = api.searchSongs(
                baseUrl = UserPreferencesRepository.DEFAULT_EXPLORE_API_BASE_URL,
                query = "Mungaru Male",
                page = 0,
                limit = 10,
            )

            assertTrue(requestedUrls.any { it.startsWith("https://www.jiosaavn.com/api.php") })
            assertTrue(requestedUrls.any { it.startsWith("https://melotune.harshbits.com/api/search/songs") })
            assertEquals("fb_1", res.data?.results?.single()?.id)
            assertEquals("Fallback Song", res.data?.results?.single()?.name)
        }

    @Test
    fun `JioSaavnApiClient does not recurse infinitely when fallback URL itself fails`() = runTest {
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                callCount++
                val req = chain.request()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("err".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()

        val api = JioSaavnApiClient(client, Gson())
        val ex = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                api.searchSongs(UserPreferencesRepository.FALLBACK_EXPLORE_API_BASE_URL, "test")
            }
        }
        assertTrue(ex.message?.contains("HTTP 500") == true)
        assertEquals(1, callCount)
    }

    @Test
    fun `SaavnNativeParser parses webapi getLaunchData and content getAlbums new releases`() {
        val encrypted = encryptSaavnUrl("https://aac.saavncdn.com/593/yellow_96.mp4")
        val launchJson = """
            {
              "new_trending": [
                {
                  "id": "sg_tr",
                  "title": "Trending Track",
                  "type": "song",
                  "language": "english",
                  "image": "https://c.saavncdn.com/1/tr-150x150.jpg",
                  "more_info": {
                    "album_id": "alb_tr",
                    "album": "Trending Album",
                    "duration": "190",
                    "encrypted_media_url": "$encrypted",
                    "320kbps": "true",
                    "artistMap": { "primary_artists": [{"id": "a1", "name": "Coldplay"}] }
                  }
                }
              ],
              "new_albums": [
                {
                  "id": "alb_new",
                  "title": "New English LP",
                  "type": "album",
                  "year": "2026",
                  "language": "english",
                  "image": "https://c.saavncdn.com/2/alb-150x150.jpg"
                }
              ],
              "charts": [
                {
                  "id": "ch_1",
                  "title": "International : India Superhits Top 50",
                  "type": "playlist",
                  "count": 50,
                  "image": "https://c.saavncdn.com/editorial/chart.jpg"
                }
              ],
              "top_playlists": [
                {
                  "id": "pl_fresh",
                  "title": "Fresh Dance Hits",
                  "type": "playlist",
                  "more_info": { "song_count": "30" }
                }
              ],
              "artist_recos": [
                {
                  "id": "459320",
                  "title": "Arijit Singh",
                  "subtitle": "Artist Radio",
                  "type": "radio_station",
                  "image": "https://c.saavncdn.com/artists/arijit.jpg"
                }
              ]
            }
        """.trimIndent()

        val launch = SaavnNativeParser.parseLaunchData(launchJson)
        assertEquals(1, launch.trendingSongs.size)
        assertEquals("sg_tr", launch.trendingSongs.single().id)
        assertEquals("alb_new", launch.newAlbums.single().id)
        assertEquals(50, launch.charts.single().songCount)
        assertEquals(30, launch.topPlaylists.single().songCount)
        assertEquals("Artist", launch.artistRecos.single().role)

        val newReleases = SaavnNativeParser.parseNewReleases(
            """
            {
              "count": 2,
              "last_page": false,
              "data": [
                {
                  "id": "sg_rel",
                  "title": "Single Drop",
                  "type": "song",
                  "language": "kannada",
                  "more_info": {
                    "album_id": "alb_single",
                    "album": "Single Drop",
                    "encrypted_media_url": "$encrypted",
                    "320kbps": "true"
                  }
                },
                {
                  "id": "alb_full",
                  "title": "Full Kannada Album",
                  "type": "album",
                  "language": "kannada"
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, newReleases.songs.size)
        assertEquals(listOf("alb_full", "alb_single"), newReleases.albums.map { it.id })
    }

    @Test
    fun `JioSaavnApiClient sends Mumbai geo and language cookies with Referer on native calls`() = runTest {
        var capturedCookie: String? = null
        var capturedReferer: String? = null
        var capturedCall: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedCookie = req.header("Cookie")
                capturedReferer = req.header("Referer")
                capturedCall = req.url.queryParameter("__call")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val api = JioSaavnApiClient(client, Gson())
        api.getLaunchData(
            baseUrl = UserPreferencesRepository.DEFAULT_EXPLORE_API_BASE_URL,
            languageCookie = "kannada",
        )

        assertEquals("webapi.getLaunchData", capturedCall)
        assertEquals("https://www.jiosaavn.com/", capturedReferer)
        assertTrue(capturedCookie?.contains("L=kannada") == true)
        assertTrue(capturedCookie?.contains("DL=english") == true)
        assertTrue(capturedCookie?.contains("Mumbai") == true)
    }

    @Test
    fun `ExploreRepository browseCategoryFeed uses native endpoints for native URL and searchAll for REST mirrors`() =
        runTest {
            val api = mockk<JioSaavnApiClient>()
            val nativePrefs = mockk<UserPreferencesRepository> {
                every { exploreApiBaseUrlFlow } returns flowOf("https://www.jiosaavn.com/api.php")
            }
            val repo = ExploreRepository(api, nativePrefs)

            coEvery { api.getLaunchData(any(), "english") } returns SaavnLaunchData(
                charts = listOf(
                    SaavnPlaylistsSearchResponse.Data.ItemDto(
                        id = "ch_eng",
                        name = "Most Searched Songs - English",
                        songCount = 50,
                    )
                ),
                newAlbums = listOf(SaavnAlbumSearchItemDto(id = "alb_eng", name = "Fresh LP")),
            )
            coEvery { api.getNewReleases(any(), "english", 0, 30) } returns SaavnNewReleasesResult(
                albums = listOf(SaavnAlbumSearchItemDto(id = "alb_rel", name = "Latest Release")),
            )
            coEvery { api.getTrendingSongs(any(), "english", any(), 0, 24) } returns SaavnSongSearchResponse(
                success = true,
                data = SaavnSongSearchResponse.Data(
                    results = listOf(SaavnSongDto(id = "s_eng", name = "Believer")),
                ),
            )

            val newReleases = repo.browseCategoryFeed(
                ExploreBrowseCategory.NEW_RELEASES,
                language = "English",
                fallbackQuery = "English New Releases",
            ).getOrThrow()
            assertEquals(listOf("Latest Release", "Fresh LP"), newReleases.albums.map { it.title })

            val charts = repo.browseCategoryFeed(
                ExploreBrowseCategory.CHARTS,
                language = "English",
                fallbackQuery = "English Charts",
            ).getOrThrow()
            assertEquals("Believer", charts.songs.single().title)
            assertEquals("Most Searched Songs - English", charts.playlists.single().title)
        }
}
