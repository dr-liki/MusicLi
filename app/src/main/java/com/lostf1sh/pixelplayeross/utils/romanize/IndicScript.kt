package com.lostf1sh.pixelplayeross.utils.romanize

enum class IndicScript(
    val unicodeRange: IntRange,
    val usesSchwaDeletion: Boolean,
    private val extraRanges: List<IntRange> = emptyList()
) {
    DEVANAGARI(0x0900..0x097F, usesSchwaDeletion = true),
    GURMUKHI(0x0A00..0x0A7F, usesSchwaDeletion = true),
    TAMIL(0x0B80..0x0BFF, usesSchwaDeletion = false),
    TELUGU(0x0C00..0x0C7F, usesSchwaDeletion = false),
    KANNADA(0x0C80..0x0CFF, usesSchwaDeletion = false),
    MALAYALAM(0x0D00..0x0D7F, usesSchwaDeletion = false),
    URDU(
        unicodeRange = 0x0600..0x06FF,
        usesSchwaDeletion = false,
        extraRanges = listOf(0x0750..0x077F, 0xFB50..0xFDFF, 0xFE70..0xFEFF)
    );

    fun matches(codePoint: Int): Boolean {
        if (codePoint in unicodeRange) return true
        return extraRanges.any { codePoint in it }
    }

    companion object {
        fun fromCodePoint(codePoint: Int): IndicScript? =
            entries.firstOrNull { it.matches(codePoint) }

        fun containsAny(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (fromCodePoint(cp) != null) return true
                i += Character.charCount(cp)
            }
            return false
        }
    }
}

enum class RomanizationStyle(val storageValue: String) {
    SING_ALONG("sing_along"),
    COMPACT("compact");

    companion object {
        fun fromStorage(raw: String?): RomanizationStyle =
            entries.firstOrNull { it.storageValue == raw } ?: SING_ALONG
    }
}
