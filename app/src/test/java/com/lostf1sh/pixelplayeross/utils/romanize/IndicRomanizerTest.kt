package com.lostf1sh.pixelplayeross.utils.romanize

import com.lostf1sh.pixelplayeross.utils.MultiLangRomanizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IndicRomanizerTest {

    @Test
    fun `hindi devanagari handles inherent schwa, schwa deletion, and nukta cleanly`() {
        assertEquals("kamal", IndicRomanizer.romanize("कमल"))
        assertEquals("dil", IndicRomanizer.romanize("दिल"))
        assertEquals("sapnaa", IndicRomanizer.romanize("सपना"))
        assertEquals("kahaanee", IndicRomanizer.romanize("कहानी"))
        assertEquals("prem", IndicRomanizer.romanize("प्रेम"))
        assertEquals("zamaanaa", IndicRomanizer.romanize("ज़माना"))
        assertEquals("na", IndicRomanizer.romanize("न"))
        assertEquals("main", IndicRomanizer.romanize("मैं"))
    }

    @Test
    fun `marathi devanagari supports retroflex l and marathi gy to dny heuristic`() {
        assertEquals("baal dnyaan", IndicRomanizer.romanize("बाळ ज्ञान"))
    }

    @Test
    fun `punjabi gurmukhi handles inherent vowels and addak gemination`() {
        assertEquals("man", IndicRomanizer.romanize("ਮਨ"))
        assertEquals("rabb", IndicRomanizer.romanize("ਰੱਬ"))
        assertEquals("dil", IndicRomanizer.romanize("ਦਿਲ"))
    }

    @Test
    fun `kannada and telugu preserve word-final vowels and assimilate anusvara`() {
        assertEquals("kannada", IndicRomanizer.romanize("ಕನ್ನಡ"))
        assertEquals("manasu", IndicRomanizer.romanize("ಮನಸು"))
        assertEquals("premam", IndicRomanizer.romanize("ಪ್ರೇಮಂ"))

        assertEquals("telugu", IndicRomanizer.romanize("తెలుగు"))
        assertEquals("prema", IndicRomanizer.romanize("ప్రేమ"))
        assertEquals("nenu", IndicRomanizer.romanize("నేను"))
        assertEquals("sangeetam", IndicRomanizer.romanize("సంగీతం"))
    }

    @Test
    fun `tamil and malayalam handle chillu, voicing, and special consonants`() {
        assertEquals("kaadhal", IndicRomanizer.romanize("காதல்"))
        assertEquals("mazhai", IndicRomanizer.romanize("மழை"))
        assertEquals("thangam", IndicRomanizer.romanize("தங்கம்"))

        assertEquals("malayaalam", IndicRomanizer.romanize("മലയാളം"))
        assertEquals("malayalam", IndicRomanizer.romanize("മലയാളം", RomanizationStyle.COMPACT))
        assertEquals("mazha", IndicRomanizer.romanize("മഴ"))
        assertEquals("avan", IndicRomanizer.romanize("അവൻ"))
        assertEquals("athu", IndicRomanizer.romanize("അതു്"))
    }

    @Test
    fun `mixed english and indic lines preserve english and compact style shortens long vowels`() {
        assertEquals("Baby ninna kannalli", IndicRomanizer.romanize("Baby ನಿನ್ನ ಕಣ್ಣಲ್ಲಿ"))
        assertEquals("sapna", IndicRomanizer.romanize("सपना", RomanizationStyle.COMPACT))
        assertEquals("kahani", IndicRomanizer.romanize("कहानी", RomanizationStyle.COMPACT))

        assertTrue(MultiLangRomanizer.isScriptThatNeedsRomanization("ಕನ್ನಡ"))
        assertTrue(MultiLangRomanizer.isScriptThatNeedsRomanization("தமிழ்"))
        assertTrue(MultiLangRomanizer.isScriptThatNeedsRomanization("తెలుగు"))
        assertTrue(MultiLangRomanizer.isScriptThatNeedsRomanization("മലയാളം"))
        assertTrue(MultiLangRomanizer.isScriptThatNeedsRomanization("हिन्दी"))
        assertTrue(MultiLangRomanizer.isScriptThatNeedsRomanization("ਪੰਜਾਬੀ"))
        assertFalse(MultiLangRomanizer.isScriptThatNeedsRomanization("English Lyrics"))
    }
}
