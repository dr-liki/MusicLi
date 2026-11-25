package com.lostf1sh.pixelplayeross.utils.romanize

enum class IndicScript(val unicodeRange: IntRange, val usesSchwaDeletion: Boolean) {
    DEVANAGARI(0x0900..0x097F, usesSchwaDeletion = true),
    GURMUKHI(0x0A00..0x0A7F, usesSchwaDeletion = true),
    TAMIL(0x0B80..0x0BFF, usesSchwaDeletion = false),
    TELUGU(0x0C00..0x0C7F, usesSchwaDeletion = false),
    KANNADA(0x0C80..0x0CFF, usesSchwaDeletion = false),
    MALAYALAM(0x0D00..0x0D7F, usesSchwaDeletion = false);

    companion object {
        fun fromCodePoint(codePoint: Int): IndicScript? =
            entries.firstOrNull { codePoint in it.unicodeRange }

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
