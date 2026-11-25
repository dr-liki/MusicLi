package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import com.lostf1sh.pixelplayeross.presentation.components.subcomps.formatDurationMsBadge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderParsingTest {

    @Test
    fun `paxsenix parser supports ttml and timestamped json arrays`() {
        val ttmlRes = PaxsenixLyricsProvider.parsePaxsenixPayload(
            """{"ttml":"<tt><body><div><p begin=\"00:05.00\"><span>Hello</span></p></div></body></tt>"}"""
        )
        assertNotNull(ttmlRes)
        assertTrue(ttmlRes!!.second)
        assertTrue(ttmlRes.third)

        val jsonArrRes = PaxsenixLyricsProvider.parsePaxsenixPayload(
            """[{"timestamp":12500,"text":"Line one"},{"timestamp":18000,"text":"Line two"}]"""
        )
        assertNotNull(jsonArrRes)
        assertTrue(jsonArrRes!!.second)
        assertFalse(jsonArrRes.third)
        assertTrue(jsonArrRes.first.contains("[00:12.50]Line one"))
    }

    @Test
    fun `youtube caption parser converts vtt and xml tracks to clean lrc while filtering noise cues`() {
        val vtt = """
            WEBVTT

            00:04.200 --> 00:07.000
            [Music]

            00:08.500 --> 00:12.000
            First lyric line

            00:12.500 --> 00:15.000
            Second <i>lyric</i> line

            00:16.000 --> 00:19.000
            Third lyric line
        """.trimIndent()

        val lrc = YouTubeLyricsProvider.parseCaptionsToLrc(vtt)
        assertNotNull(lrc)
        assertFalse(lrc!!.contains("[Music]"))
        assertTrue(lrc.contains("[00:08.50]First lyric line"))
        assertTrue(lrc.contains("[00:12.50]Second lyric line"))
        assertTrue(lrc.contains("[00:16.00]Third lyric line"))
    }

    @Test
    fun `duration badge formatter displays rounded mm ss`() {
        assertEquals("03:45", formatDurationMsBadge(225_000L))
        assertEquals("04:00", formatDurationMsBadge(239_600L))
    }
}
