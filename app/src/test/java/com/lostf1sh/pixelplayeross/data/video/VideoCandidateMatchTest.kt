package com.lostf1sh.pixelplayeross.data.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoCandidateMatchTest {

    private fun matches(
        title: String,
        channel: String = "Label Records",
        seconds: Int? = null,
        song: String,
        artist: String = "Asha Singer",
        durationMs: Long? = null,
    ) = isSameSongVideo(title, channel, seconds, song, artist, durationMs)

    @Test
    fun rejectsADifferentSongWithASimilarName() {
        assertFalse(matches("Haseena", channel = "Crishna", seconds = 200, song = "Hosanna", artist = "Crishna", durationMs = 200_000))
    }

    @Test
    fun rejectsAnOfficialVideoForAnotherSong() {
        assertFalse(
            matches(
                "Abhijay Sharma - sheher (Official Music Video) | dir. harsh",
                channel = "Abhijay Sharma",
                seconds = 180,
                song = "Qissa",
                artist = "Abhijay Sharma",
                durationMs = 180_000,
            ),
        )
    }

    @Test
    fun acceptsTitleWithExtrasWhenItNamesTheArtist() {
        assertTrue(matches("Aasmaan Jaley(Official Video) | Abhay J | Abhijeet S", song = "Aasmaan Jaley", artist = "Abhijeet Srivastava"))
    }

    @Test
    fun acceptsLabelUploadWhenLengthMatches() {
        assertTrue(matches("Kasturi | Amar Prem Ki Prem Kahani", channel = "Tips", seconds = 240, song = "Kasturi", durationMs = 233_000))
    }

    @Test
    fun rejectsSameTitleWithOtherArtistAndLength() {
        assertFalse(matches("Kaagaz", channel = "Other Person", seconds = 400, song = "Kaagaz", durationMs = 205_000))
    }

    @Test
    fun rejectsWhenLengthIsUnknownAndArtistIsAbsent() {
        assertFalse(matches("Kaagaz", song = "Kaagaz", durationMs = 205_000))
    }

    @Test
    fun extractYoutubeVideoIdFromVariousFormats() {
        assertEquals("dQw4w9WgXcQ", extractYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", extractYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", extractYoutubeVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", extractYoutubeVideoId("dQw4w9WgXcQ"))
        assertNull(extractYoutubeVideoId("not_a_valid_url_at_all"))
        assertNull(extractYoutubeVideoId("invalid"))
    }
}
