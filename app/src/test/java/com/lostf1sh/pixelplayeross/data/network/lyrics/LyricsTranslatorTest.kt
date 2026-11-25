package com.lostf1sh.pixelplayeross.data.network.lyrics

import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.Locale

class LyricsTranslatorTest {

    private val translator = LyricsTranslator(OkHttpClient())

    @Test
    fun `resolvedLanguageTag uses explicit tag or falls back to system locale`() {
        assertEquals("es", translator.resolvedLanguageTag("es"))
        assertEquals("zh-CN", translator.resolvedLanguageTag(" zh-CN "))

        val expectedDefault = Locale.getDefault().language.ifBlank { "en" }
        assertEquals(expectedDefault, translator.resolvedLanguageTag("auto"))
        assertEquals(expectedDefault, translator.resolvedLanguageTag(""))
    }

    @Test
    fun `parseTranslationJson joins all translated sentence segments`() {
        val sampleResponse = """
            [
              [
                ["Hello world\n", "Hola mundo\n", null, null, 1],
                ["Good night", "Buenas noches", null, null, 1]
              ],
              null,
              "es"
            ]
        """.trimIndent()

        val parsed = LyricsTranslator.parseTranslationJson(sampleResponse)
        assertEquals("Hello world\nGood night", parsed)
        assertFalse(parsed.isBlank())
    }
}
