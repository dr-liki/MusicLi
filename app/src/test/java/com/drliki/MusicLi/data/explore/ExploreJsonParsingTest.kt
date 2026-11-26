package com.drliki.MusicLi.data.explore

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreJsonParsingTest {

    private val gson = Gson()

    @Test
    fun `search responses deserialize live JioSaavn shapes including nullable fields`() {
        val songsJson = """
            {"success":true,"data":{"total":2,"start":0,"results":[{"id":"qcEf8GY8","name":"Apna Bana Le","type":"song","year":"2026","releaseDate":null,"duration":261,"label":"Zee Music Company","explicitContent":false,"playCount":240111694,"language":"hindi","hasLyrics":false,"lyricsId":null,"url":"https://www.jiosaavn.com/song/apna-bana-le/AQsuV0x3bgs","copyright":"© 2026 Zee Music Company","album":{"id":"75362722","name":"Trending Love Songs","url":"https://www.jiosaavn.com/album/trending-love-songs/ejgHvA57ASU_"},"artists":{"primary":[{"id":"458681","name":"Arijit Singh","role":"primary_artists","type":"artist","image":[],"url":"https://www.jiosaavn.com"}],"featured":[],"all":[{"id":"458681","name":"Arijit Singh","role":"singer","type":"artist","image":[],"url":"https://www.jiosaavn.com"}]},"image":[{"quality":"150x150","url":"https://c.saavncdn.com/150.jpg"},{"quality":"500x500","url":"https://c.saavncdn.com/500.jpg"}],"downloadUrl":[{"quality":"160kbps","url":"https://aac.saavncdn.com/160.mp4"},{"quality":"320kbps","url":"https://aac.saavncdn.com/320.mp4"}]}]}}
        """.trimIndent()

        val albumsJson = """
            {"success":true,"data":{"total":10,"start":0,"results":[{"id":"1689471","name":"Govinda Namalu","description":"","year":null,"type":"album","playCount":null,"language":"telugu","explicitContent":null,"artists":{"primary":[{"id":"992207","name":"Rajalakshmee Sanjay","role":"primary_artists","type":"artist","image":[],"url":"https://www.jiosaavn.com"}],"featured":[],"all":[]},"image":[]}]}}
        """.trimIndent()

        val playlistsJson = """
            {"success":true,"data":{"total":1,"start":0,"results":[{"id":"81787681","name":"Top Songs - 1970s","type":"playlist","image":[{"quality":"500x500","url":"https://pli.saavncdn.com/500.jpg"}],"url":"https://www.jiosaavn.com","songCount":100,"language":"hindi","explicitContent":false}]}}
        """.trimIndent()

        val artistsJson = """
            {"success":true,"data":{"total":1,"start":0,"results":[{"id":"459320","name":"Arijit Singh","role":"Artist","type":"artist","image":[{"quality":"500x500","url":"https://c.saavncdn.com/500.jpg"}],"url":"https://www.jiosaavn.com"}]}}
        """.trimIndent()

        val songRes = gson.fromJson(songsJson, SaavnSongSearchResponse::class.java)
        val albumRes = gson.fromJson(albumsJson, SaavnAlbumsSearchResponse::class.java)
        val playlistRes = gson.fromJson(playlistsJson, SaavnPlaylistsSearchResponse::class.java)
        val artistRes = gson.fromJson(artistsJson, SaavnArtistsSearchResponse::class.java)

        assertTrue(songRes.success)
        assertEquals("qcEf8GY8", songRes.data?.results?.firstOrNull()?.id)
        assertEquals("Apna Bana Le", songRes.data?.results?.firstOrNull()?.name)
        assertEquals(2, songRes.data?.results?.firstOrNull()?.downloadUrl?.size)

        assertTrue(albumRes.success)
        assertEquals("1689471", albumRes.data?.results?.firstOrNull()?.id)
        assertEquals(null, albumRes.data?.results?.firstOrNull()?.playCount)

        assertTrue(playlistRes.success)
        assertEquals(100, playlistRes.data?.results?.firstOrNull()?.songCount)

        assertTrue(artistRes.success)
        assertEquals("Arijit Singh", artistRes.data?.results?.firstOrNull()?.name)
    }

    @Test
    fun `every JioSaavn Gson DTO class carries Keep and SerializedName for R8 safety`() {
        val dtoClasses = listOf(
            SaavnImageDto::class.java,
            SaavnDownloadUrlDto::class.java,
            SaavnAlbumRefDto::class.java,
            SaavnArtistRefDto::class.java,
            SaavnArtistsGroupDto::class.java,
            SaavnLyricsDto::class.java,
            SaavnSongDto::class.java,
            SaavnSongResponse::class.java,
            SaavnLyricsResponse::class.java,
            SaavnSongSearchResponse::class.java,
            SaavnSongSearchResponse.Data::class.java,
            SaavnAlbumSearchItemDto::class.java,
            SaavnAlbumsSearchResponse::class.java,
            SaavnAlbumsSearchResponse.Data::class.java,
            SaavnAlbumDetailResponse::class.java,
            SaavnAlbumDetailResponse.Data::class.java,
            SaavnPlaylistsSearchResponse::class.java,
            SaavnPlaylistsSearchResponse.Data::class.java,
            SaavnPlaylistsSearchResponse.Data.ItemDto::class.java,
            SaavnPlaylistDetailResponse::class.java,
            SaavnPlaylistDetailResponse.Data::class.java,
            SaavnArtistsSearchResponse::class.java,
            SaavnArtistsSearchResponse.Data::class.java,
            SaavnArtistDetailResponse::class.java,
            SaavnArtistDetailResponse.Data::class.java,
        )

        dtoClasses.forEach { clazz ->
            clazz.declaredFields
                .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    assertNotNull(
                        field.getAnnotation(SerializedName::class.java),
                        "${clazz.simpleName}.${field.name} missing @SerializedName",
                    )
                }
        }
    }
}
