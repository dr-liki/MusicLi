package com.drliki.MusicLi.utils.romanize

/**
 * Syllable-aware offline romanizer for Indic lyrics across:
 * Hindi & Marathi (Devanagari), Punjabi (Gurmukhi), Kannada, Telugu, Tamil, and Malayalam.
 */
object IndicRomanizer {

    private data class WordSyllable(
        var stem: String,
        var vowel: String,
        val explicitVowel: Boolean,
        val hasVirama: Boolean,
        var implicitSchwa: Boolean,
        val endsWithConsonantOnly: Boolean = false,
        val isGeminated: Boolean = false,
        val postmodifiers: MutableList<PostModifier> = mutableListOf()
    )

    private enum class PostModifier { ANUSVARA, CHANDRABINDU, VISARGA }

    fun containsIndicScript(text: String): Boolean = IndicScript.containsAny(text)

    fun romanize(text: String, style: RomanizationStyle = RomanizationStyle.SING_ALONG): String {
        if (text.isBlank()) return text
        val lineHasMarathiChars = text.indexOf('\u0933') >= 0 ||
            text.indexOf('\u0972') >= 0 ||
            text.indexOf('\u0911') >= 0
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val script = IndicScript.fromCodePoint(cp)
            if (script == null) {
                out.appendCodePoint(cp)
                i += Character.charCount(cp)
                continue
            }
            val runStart = i
            var j = i + Character.charCount(cp)
            while (j < text.length) {
                val nextCp = text.codePointAt(j)
                if (IndicScript.fromCodePoint(nextCp) != script) break
                j += Character.charCount(nextCp)
            }
            val token = text.substring(runStart, j)
            out.append(romanizeToken(token, script, style, lineHasMarathiChars))
            i = j
        }
        return out.toString()
    }

    private fun romanizeToken(
        token: String,
        script: IndicScript,
        style: RomanizationStyle,
        lineHasMarathiChars: Boolean
    ): String {
        if (script == IndicScript.URDU) {
            return UrduRomanizer.romanizeWord(token, style)
        }
        val table = tables[script] ?: return token
        val isMarathiHint = script == IndicScript.DEVANAGARI && lineHasMarathiChars

        val units = mutableListOf<WordSyllable>()
        var i = 0
        var pendingGeminate = false

        while (i < token.length) {
            val ch = token[i]

            // Digits / standalone symbols
            table.symbols[ch]?.let { sym ->
                units.add(
                    WordSyllable(
                        stem = sym,
                        vowel = "",
                        explicitVowel = true,
                        hasVirama = false,
                        implicitSchwa = false,
                        endsWithConsonantOnly = true
                    )
                )
                i++
                continue
            }

            // Gurmukhi Addak doubles the onset of the next consonant
            if (script == IndicScript.GURMUKHI && ch == '\u0A71') {
                pendingGeminate = true
                i++
                continue
            }

            // Modifiers attached to preceding syllable
            when (ch) {
                table.anusvara, table.tippi, table.bindi -> {
                    if (units.isNotEmpty()) {
                        units.last().postmodifiers.add(PostModifier.ANUSVARA)
                    } else {
                        units.add(WordSyllable("n", "", true, false, false, true))
                    }
                    i++
                    continue
                }
                table.chandrabindu -> {
                    if (units.isNotEmpty()) {
                        units.last().postmodifiers.add(PostModifier.CHANDRABINDU)
                    }
                    i++
                    continue
                }
                table.visarga -> {
                    if (units.isNotEmpty()) {
                        units.last().postmodifiers.add(PostModifier.VISARGA)
                    } else {
                        units.add(WordSyllable("h", "", true, false, false, true))
                    }
                    i++
                    continue
                }
            }

            // Malayalam chillu consonants carry no vowel at all
            table.chillus[ch]?.let { chillu ->
                units.add(
                    WordSyllable(
                        stem = chillu,
                        vowel = "",
                        explicitVowel = true,
                        hasVirama = true,
                        implicitSchwa = false,
                        endsWithConsonantOnly = true
                    )
                )
                i++
                continue
            }

            // Independent vowel or Gurmukhi bearer + optional matra
            if (script == IndicScript.GURMUKHI && (ch == '\u0A72' || ch == '\u0A73')) {
                val next = token.getOrNull(i + 1)
                val matraVowel = next?.let { table.matras[it] }
                if (matraVowel != null) {
                    units.add(
                        WordSyllable(
                            stem = "",
                            vowel = formatVowel(matraVowel, style),
                            explicitVowel = true,
                            hasVirama = false,
                            implicitSchwa = false
                        )
                    )
                    i += 2
                    continue
                }
            }

            table.independentVowels[ch]?.let { v ->
                units.add(
                    WordSyllable(
                        stem = "",
                        vowel = formatVowel(v, style),
                        explicitVowel = true,
                        hasVirama = false,
                        implicitSchwa = false
                    )
                )
                i++
                continue
            }

            // Special 3-char conjunct ligatures in Devanagari (क्ष, त्र, ज्ञ, श्र)
            if (script == IndicScript.DEVANAGARI && i + 2 < token.length && token[i + 1] == '\u094D') {
                val trigram = token.substring(i, i + 3)
                val specialStem = when (trigram) {
                    "क्ष" -> "ksh"
                    "त्र" -> "tr"
                    "ज्ञ" -> if (isMarathiHint) "dny" else "gy"
                    "श्र" -> "shr"
                    else -> null
                }
                if (specialStem != null) {
                    i += 3
                    var vowel = ""
                    var explicitVowel = false
                    var hasVirama = false
                    var implicitSchwa = true
                    if (i < token.length) {
                        val after = token[i]
                        if (after == table.virama) {
                            hasVirama = true
                            implicitSchwa = false
                            i++
                        } else if (table.matras.containsKey(after)) {
                            val rawMatra = table.matras.getValue(after)
                            vowel = formatVowel(rawMatra, style)
                            explicitVowel = true
                            implicitSchwa = false
                            i++
                        }
                    }
                    if (implicitSchwa) vowel = "a"
                    units.add(
                        WordSyllable(
                            stem = specialStem,
                            vowel = vowel,
                            explicitVowel = explicitVowel,
                            hasVirama = hasVirama,
                            implicitSchwa = implicitSchwa
                        )
                    )
                    continue
                }
            }

            // Consonant (+ optional Nukta)
            var baseStem: String? = null
            var step = 1
            if (table.nukta != null && i + 1 < token.length && token[i + 1] == table.nukta) {
                baseStem = table.nuktaConsonants[ch]
                if (baseStem != null) step = 2
            }
            if (baseStem == null) {
                baseStem = table.consonants[ch]
            }

            if (baseStem != null) {
                i += step
                // Malayalam Samvruthokaram: ു (u) + ് (virama) remains short "u"
                var vowel = ""
                var explicitVowel = false
                var hasVirama = false
                var implicitSchwa = true

                if (i < token.length) {
                    val after = token[i]
                    if (after == table.virama) {
                        hasVirama = true
                        implicitSchwa = false
                        i++
                    } else if (table.matras.containsKey(after)) {
                        var rawMatra = table.matras.getValue(after)
                        i++
                        // Tamil / Malayalam split vowel signs like ெ + ா -> ொ or െ + ൗ -> ൌ
                        if (i < token.length && table.splitMatraAuMark == token[i] &&
                            (after == '\u0BC6' || after == '\u0D46')
                        ) {
                            rawMatra = "au"
                            i++
                        } else if (script == IndicScript.MALAYALAM &&
                            after == '\u0D41' &&
                            i < token.length &&
                            token[i] == table.virama
                        ) {
                            // Samvruthokaram ു്
                            i++
                        }
                        vowel = formatVowel(rawMatra, style)
                        explicitVowel = true
                        implicitSchwa = false
                    }
                }

                // Tamil Grantha Sri ligature or contextual stop adjustment handled later
                val resolvedStem = if (pendingGeminate) {
                    pendingGeminate = false
                    geminateOnset(baseStem)
                } else {
                    baseStem
                }

                if (implicitSchwa) vowel = "a"

                units.add(
                    WordSyllable(
                        stem = resolvedStem,
                        vowel = vowel,
                        explicitVowel = explicitVowel,
                        hasVirama = hasVirama,
                        implicitSchwa = implicitSchwa
                    )
                )
                continue
            }

            // Orphaned matra / virama / unknown mark inside script block
            table.matras[ch]?.let { matra ->
                if (units.isNotEmpty() && units.last().implicitSchwa) {
                    units.last().vowel = formatVowel(matra, style)
                    units.last().implicitSchwa = false
                } else {
                    units.add(WordSyllable("", formatVowel(matra, style), true, false, false))
                }
                i++
                continue
            }
            if (ch == table.virama || ch == table.nukta || ch == table.avagraha) {
                i++
                continue
            }

            units.add(WordSyllable(ch.toString(), "", true, false, false, endsWithConsonantOnly = true))
            i++
        }

        if (units.isEmpty()) return ""

        // Tamil & Malayalam contextual stop voicing (nasal+stop & intervocalic softening)
        if (script == IndicScript.TAMIL) {
            applyTamilPhonetics(units)
        } else if (script == IndicScript.MALAYALAM) {
            applyMalayalamPhonetics(units)
        }

        // Schwa deletion for Hindi, Marathi, Punjabi
        if (script.usesSchwaDeletion) {
            applyIndoAryanSchwaDeletion(units)
        }

        // Assemble final Latin string with contextual nasal assimilation
        val sb = StringBuilder(token.length * 2)
        for (idx in units.indices) {
            val u = units[idx]
            sb.append(u.stem)
            sb.append(u.vowel)

            val nextStem = units.getOrNull(idx + 1)?.stem.orEmpty()
            for (mod in u.postmodifiers) {
                when (mod) {
                    PostModifier.ANUSVARA -> {
                        sb.append(
                            resolveNasal(
                                nextStem = nextStem,
                                isWordFinal = idx == units.lastIndex,
                                script = script
                            )
                        )
                    }
                    PostModifier.CHANDRABINDU -> sb.append("n")
                    PostModifier.VISARGA -> sb.append("h")
                }
            }
        }
        return sb.toString()
    }

    private fun applyIndoAryanSchwaDeletion(units: List<WordSyllable>) {
        if (units.size <= 1) {
            // Keep single syllable's inherent 'a' (e.g., "न" -> "na", "च" -> "cha").
            return
        }

        val deleted = BooleanArray(units.size)

        // Step 1: Word-final schwa deletion unless preceded by a cluster requiring a release vowel.
        val lastIdx = units.lastIndex
        val last = units[lastIdx]
        if (last.implicitSchwa && !last.endsWithConsonantOnly) {
            val prev = units.getOrNull(lastIdx - 1)
            val prevEndedInVirama = prev?.hasVirama == true && prev.vowel.isEmpty()
            val keepFinalForCluster = prevEndedInVirama && (
                last.stem in setOf("y", "r", "l", "v", "n") ||
                    (prev.stem in setOf("sh", "s", "ksh", "t", "d", "p", "b") && last.stem in setOf("y", "n"))
                )
            if (!keepFinalForCluster) {
                last.vowel = ""
                last.implicitSchwa = false
                deleted[lastIdx] = true
            }
        }

        // Step 2: Right-to-left medial schwa deletion for open VC syllables (idx >= 1, never initial consonant).
        for (idx in lastIdx - 1 downTo 1) {
            val cur = units[idx]
            if (!cur.implicitSchwa || cur.endsWithConsonantOnly) continue
            if (cur.postmodifiers.isNotEmpty()) continue
            val prev = units[idx - 1]
            val next = units[idx + 1]

            // Do not create triple clusters if previous or next already lacks a vowel.
            if (prev.vowel.isEmpty() || next.vowel.isEmpty()) continue
            if (deleted[idx + 1]) continue

            cur.vowel = ""
            cur.implicitSchwa = false
            deleted[idx] = true
        }
    }

    private fun applyTamilPhonetics(units: MutableList<WordSyllable>) {
        for (idx in units.indices) {
            val cur = units[idx]
            val prev = units.getOrNull(idx - 1)
            val prevKilledSame = prev != null && prev.hasVirama && prev.vowel.isEmpty() && prev.stem == cur.stem

            if (prev != null && prev.hasVirama && prev.vowel.isEmpty()) {
                when {
                    // Nasal + stop voicing
                    prev.stem == "ng" && cur.stem == "k" -> { prev.stem = "n"; cur.stem = "g" }
                    prev.stem == "ny" && cur.stem == "ch" -> { prev.stem = "n"; cur.stem = "j" }
                    prev.stem == "n" && cur.stem == "t" -> cur.stem = "d"
                    prev.stem == "n" && cur.stem == "th" -> cur.stem = "dh"
                    prev.stem == "m" && cur.stem == "p" -> cur.stem = "b"
                    prev.stem == "n" && cur.stem == "rr" -> {
                        prev.stem = "n"; cur.stem = "dr"
                    }
                    prev.stem == "rr" && cur.stem == "rr" -> {
                        prev.stem = "t"; cur.stem = "tr"
                    }
                    prev.stem == "ch" && cur.stem == "ch" -> {
                        prev.stem = "c"; cur.stem = "ch"
                    }
                }
            } else if (!prevKilledSame && idx > 0 && prev != null && prev.vowel.isNotEmpty()) {
                // Intervocalic single Tamil stops soften naturally in sing-along pronunciation.
                cur.stem = when (cur.stem) {
                    "k" -> "g"
                    "t" -> "d"
                    "th" -> "dh"
                    "p" -> "b"
                    "ch" -> "s"
                    else -> cur.stem
                }
            } else if (idx == 0 && cur.stem == "rr") {
                cur.stem = "r"
            }

            if (cur.stem == "rr") cur.stem = "r"
        }
    }

    private fun applyMalayalamPhonetics(units: List<WordSyllable>) {
        for (idx in units.indices) {
            val cur = units[idx]
            val prev = units.getOrNull(idx - 1)
            if (prev != null && prev.hasVirama && prev.vowel.isEmpty()) {
                when {
                    prev.stem == "ng" && cur.stem == "k" -> { prev.stem = "n"; cur.stem = "g" }
                    prev.stem == "ny" && cur.stem == "ch" -> { prev.stem = "n"; cur.stem = "j" }
                    prev.stem == "n" && cur.stem == "t" -> cur.stem = "d"
                    prev.stem == "n" && cur.stem == "th" -> cur.stem = "dh"
                    prev.stem == "m" && cur.stem == "p" -> cur.stem = "b"
                    prev.stem == "rr" && cur.stem == "rr" -> { prev.stem = "t"; cur.stem = "t" }
                }
            }
            if (cur.stem == "rr") cur.stem = "r"
        }
    }

    private fun resolveNasal(nextStem: String, isWordFinal: Boolean, script: IndicScript): String {
        if (isWordFinal) {
            return when (script) {
                IndicScript.KANNADA, IndicScript.TELUGU, IndicScript.MALAYALAM, IndicScript.TAMIL -> "m"
                IndicScript.DEVANAGARI, IndicScript.GURMUKHI, IndicScript.URDU -> "n"
            }
        }
        return when {
            nextStem.startsWith("p") || nextStem.startsWith("b") || nextStem.startsWith("m") -> "m"
            else -> "n"
        }
    }

    private fun geminateOnset(stem: String): String = when {
        stem.isEmpty() -> stem
        stem == "ch" -> "cch"
        stem == "chh" -> "chchh"
        stem == "sh" -> "ssh"
        else -> stem.first() + stem
    }

    private fun formatVowel(raw: String, style: RomanizationStyle): String {
        if (style == RomanizationStyle.SING_ALONG) return raw
        return when (raw) {
            "aa" -> "a"
            "ee" -> "i"
            "oo" -> "u"
            else -> raw
        }
    }

    private class ScriptTable(
        val consonants: Map<Char, String>,
        val nuktaConsonants: Map<Char, String> = emptyMap(),
        val chillus: Map<Char, String> = emptyMap(),
        val independentVowels: Map<Char, String>,
        val matras: Map<Char, String>,
        val symbols: Map<Char, String> = emptyMap(),
        val virama: Char,
        val nukta: Char? = null,
        val anusvara: Char,
        val tippi: Char = '\u0000',
        val bindi: Char = '\u0000',
        val chandrabindu: Char = '\u0000',
        val visarga: Char = '\u0000',
        val avagraha: Char = '\u0000',
        val splitMatraAuMark: Char = '\u0000'
    )

    private val tables: Map<IndicScript, ScriptTable> = mapOf(
        IndicScript.DEVANAGARI to ScriptTable(
            consonants = mapOf(
                'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
                'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
                'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
                'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'ऩ' to "n",
                'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
                'य' to "y", 'र' to "r", 'ऱ' to "r", 'ल' to "l", 'ळ' to "l", 'ऴ' to "zh", 'व' to "v",
                'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
                '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z",
                '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y"
            ),
            nuktaConsonants = mapOf(
                'क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z",
                'ड' to "r", 'ढ' to "rh", 'फ' to "f", 'य' to "y"
            ),
            independentVowels = mapOf(
                'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
                'ऋ' to "ri", 'ए' to "e", 'ऎ' to "e", 'ऍ' to "ae", 'ॲ' to "ae", 'ऐ' to "ai",
                'ओ' to "o", 'ऒ' to "o", 'ऑ' to "o", 'औ' to "au"
            ),
            matras = mapOf(
                'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
                'ृ' to "ri", 'ॄ' to "ri", 'ॅ' to "ae", 'ॆ' to "e", 'े' to "e",
                'ै' to "ai", 'ॉ' to "o", 'ॊ' to "o", 'ो' to "o", 'ौ' to "au"
            ),
            symbols = mapOf(
                'ॐ' to "Om", '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
                '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9"
            ),
            virama = '्',
            nukta = '़',
            anusvara = 'ं',
            chandrabindu = 'ँ',
            visarga = 'ः',
            avagraha = 'ऽ'
        ),
        IndicScript.GURMUKHI to ScriptTable(
            consonants = mapOf(
                'ਕ' to "k", 'ਖ' to "kh", 'ਗ' to "g", 'ਘ' to "gh", 'ਙ' to "ng",
                'ਚ' to "ch", 'ਛ' to "chh", 'ਜ' to "j", 'ਝ' to "jh", 'ਞ' to "ny",
                'ਟ' to "t", 'ਠ' to "th", 'ਡ' to "d", 'ਢ' to "dh", 'ਣ' to "n",
                'ਤ' to "t", 'ਥ' to "th", 'ਦ' to "d", 'ਧ' to "dh", 'ਨ' to "n",
                'ਪ' to "p", 'ਫ' to "ph", 'ਬ' to "b", 'ਭ' to "bh", 'ਮ' to "m",
                'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v", '\u0A5C' to "r",
                'ਸ' to "s", 'ਹ' to "h",
                '\u0A33' to "l", '\u0A36' to "sh", '\u0A59' to "kh", '\u0A5A' to "g", '\u0A5B' to "z", '\u0A5E' to "f"
            ),
            nuktaConsonants = mapOf(
                'ਸ' to "sh", 'ਖ' to "kh", 'ਗ' to "g", 'ਜ' to "z", 'ਫ' to "f", 'ਲ' to "l"
            ),
            independentVowels = mapOf(
                'ਅ' to "a", 'ਆ' to "aa", 'ਇ' to "i", 'ਈ' to "ee", 'ਉ' to "u", 'ਊ' to "oo",
                'ਏ' to "e", 'ਐ' to "ai", 'ਓ' to "o", 'ਔ' to "au",
                'ੲ' to "e", 'ੳ' to "o"
            ),
            matras = mapOf(
                'ਾ' to "aa", 'ਿ' to "i", 'ੀ' to "ee", 'ੁ' to "u", 'ੂ' to "oo",
                'ੇ' to "e", 'ੈ' to "ai", 'ੋ' to "o", 'ੌ' to "au"
            ),
            symbols = mapOf(
                'ੴ' to "Ek Onkar", '੦' to "0", '੧' to "1", '੨' to "2", '੩' to "3", '੪' to "4",
                '੫' to "5", '੬' to "6", '੭' to "7", '੮' to "8", '੯' to "9"
            ),
            virama = '੍',
            nukta = '਼',
            anusvara = 'ਂ',
            tippi = 'ੰ',
            bindi = 'ਂ',
            visarga = 'ਃ'
        ),
        IndicScript.KANNADA to ScriptTable(
            consonants = mapOf(
                'ಕ' to "k", 'ಖ' to "kh", 'ಗ' to "g", 'ಘ' to "gh", 'ಙ' to "ng",
                'ಚ' to "ch", 'ಛ' to "chh", 'ಜ' to "j", 'ಝ' to "jh", 'ಞ' to "ny",
                'ಟ' to "t", 'ಠ' to "th", 'ಡ' to "d", 'ಢ' to "dh", 'ಣ' to "n",
                'ತ' to "t", 'ಥ' to "th", 'ದ' to "d", 'ಧ' to "dh", 'ನ' to "n",
                'ಪ' to "p", 'ಫ' to "ph", 'ಬ' to "b", 'ಭ' to "bh", 'ಮ' to "m",
                'ಯ' to "y", 'ರ' to "r", 'ಱ' to "r", 'ಲ' to "l", 'ಳ' to "l", 'ೞ' to "zh", 'ವ' to "v",
                'ಶ' to "sh", 'ಷ' to "sh", 'ಸ' to "s", 'ಹ' to "h"
            ),
            independentVowels = mapOf(
                'ಅ' to "a", 'ಆ' to "aa", 'ಇ' to "i", 'ಈ' to "ee", 'ಉ' to "u", 'ಊ' to "oo",
                'ಋ' to "ru", 'ಎ' to "e", 'ಏ' to "ee", 'ಐ' to "ai",
                'ಒ' to "o", 'ಓ' to "oo", 'ಔ' to "au"
            ),
            matras = mapOf(
                'ಾ' to "aa", 'ಿ' to "i", 'ೀ' to "ee", 'ು' to "u", 'ೂ' to "oo",
                'ೃ' to "ru", 'ೆ' to "e", 'ೇ' to "e", 'ೈ' to "ai",
                'ೊ' to "o", 'ೋ' to "o", 'ೌ' to "au"
            ),
            symbols = mapOf(
                '೦' to "0", '೧' to "1", '೨' to "2", '೩' to "3", '೪' to "4",
                '೫' to "5", '೬' to "6", '೭' to "7", '೮' to "8", '೯' to "9"
            ),
            virama = '್',
            nukta = '಼',
            anusvara = 'ಂ',
            chandrabindu = 'ಁ',
            visarga = 'ಃ',
            avagraha = 'ಽ'
        ),
        IndicScript.TELUGU to ScriptTable(
            consonants = mapOf(
                'క' to "k", 'ఖ' to "kh", 'గ' to "g", 'ఘ' to "gh", 'ఙ' to "ng",
                'చ' to "ch", 'ఛ' to "chh", 'జ' to "j", 'ఝ' to "jh", 'ఞ' to "ny",
                'ట' to "t", 'ఠ' to "th", 'డ' to "d", 'ఢ' to "dh", 'ణ' to "n",
                'త' to "t", 'థ' to "th", 'ద' to "d", 'ధ' to "dh", 'న' to "n",
                'ప' to "p", 'ఫ' to "ph", 'బ' to "b", 'భ' to "bh", 'మ' to "m",
                'య' to "y", 'ర' to "r", 'ఱ' to "r", 'ల' to "l", 'ళ' to "l", 'ఴ' to "zh", 'వ' to "v",
                'శ' to "sh", 'ష' to "sh", 'స' to "s", 'హ' to "h",
                'ౘ' to "ts", 'ౙ' to "dz"
            ),
            independentVowels = mapOf(
                'అ' to "a", 'ఆ' to "aa", 'ఇ' to "i", 'ఈ' to "ee", 'ఉ' to "u", 'ఊ' to "oo",
                'ఋ' to "ru", 'ఎ' to "e", 'ఏ' to "ee", 'ఐ' to "ai",
                'ఒ' to "o", 'ఓ' to "oo", 'ఔ' to "au"
            ),
            matras = mapOf(
                'ా' to "aa", 'ి' to "i", 'ీ' to "ee", 'ు' to "u", 'ూ' to "oo",
                'ృ' to "ru", 'ె' to "e", 'ే' to "e", 'ై' to "ai",
                'ొ' to "o", 'ో' to "o", 'ౌ' to "au"
            ),
            symbols = mapOf(
                '౦' to "0", '౧' to "1", '౨' to "2", '౩' to "3", '౪' to "4",
                '౫' to "5", '౬' to "6", '౭' to "7", '౮' to "8", '౯' to "9"
            ),
            virama = '్',
            anusvara = 'ం',
            chandrabindu = 'ఁ',
            visarga = 'ః',
            avagraha = 'ఽ'
        ),
        IndicScript.TAMIL to ScriptTable(
            consonants = mapOf(
                'க' to "k", 'ங' to "ng", 'ச' to "ch", 'ஜ' to "j", 'ஞ' to "ny",
                'ட' to "t", 'ண' to "n", 'த' to "th", 'ந' to "n", 'ன' to "n",
                'ப' to "p", 'ம' to "m", 'ய' to "y", 'ர' to "r", 'ற' to "rr",
                'ல' to "l", 'ள' to "l", 'ழ' to "zh", 'வ' to "v",
                'ஶ' to "sh", 'ஷ' to "sh", 'ஸ' to "s", 'ஹ' to "h"
            ),
            independentVowels = mapOf(
                'அ' to "a", 'ஆ' to "aa", 'இ' to "i", 'ஈ' to "ee", 'உ' to "u", 'ஊ' to "oo",
                'எ' to "e", 'ஏ' to "ee", 'ஐ' to "ai", 'ஒ' to "o", 'ஓ' to "oo", 'ஔ' to "au"
            ),
            matras = mapOf(
                'ா' to "aa", 'ி' to "i", 'ీ' to "ee", 'ு' to "u", 'ூ' to "oo",
                'ெ' to "e", 'ே' to "ee", 'ை' to "ai",
                'ொ' to "o", 'ோ' to "oo", 'ௌ' to "au"
            ),
            symbols = mapOf(
                'ஃ' to "h", '௦' to "0", '௧' to "1", '௨' to "2", '௩' to "3", '௪' to "4",
                '௫' to "5", '௬' to "6", '௭' to "7", '௮' to "8", '௯' to "9"
            ),
            virama = '்',
            anusvara = 'ஂ',
            visarga = 'ஃ',
            splitMatraAuMark = 'ௗ'
        ),
        IndicScript.MALAYALAM to ScriptTable(
            consonants = mapOf(
                'ക' to "k", 'ഖ' to "kh", 'ഗ' to "g", 'ഘ' to "gh", 'ങ' to "ng",
                'ച' to "ch", 'ഛ' to "chh", 'ജ' to "j", 'ഝ' to "jh", 'ഞ' to "ny",
                'ട' to "t", 'ഠ' to "th", 'ഡ' to "d", 'ഢ' to "dh", 'ണ' to "n",
                'ത' to "th", 'ഥ' to "th", 'ദ' to "d", 'ധ' to "dh", 'ന' to "n", 'ഩ' to "n",
                'പ' to "p", 'ഫ' to "ph", 'ബ' to "b", 'ഭ' to "bh", 'മ' to "m",
                'യ' to "y", 'ര' to "r", 'റ' to "rr", 'ല' to "l", 'ള' to "l", 'ഴ' to "zh", 'വ' to "v",
                'ശ' to "sh", 'ഷ' to "sh", 'സ' to "s", 'ഹ' to "h", 'ഺ' to "tt"
            ),
            chillus = mapOf(
                'ൺ' to "n", 'ൻ' to "n", 'ർ' to "r", 'ൽ' to "l", 'ൾ' to "l", 'ൿ' to "k", 'ൎ' to "r"
            ),
            independentVowels = mapOf(
                'അ' to "a", 'ആ' to "aa", 'ഇ' to "i", 'ഈ' to "ee", 'ഉ' to "u", 'ഊ' to "oo",
                'ഋ' to "ru", 'എ' to "e", 'ഏ' to "ee", 'ഐ' to "ai",
                'ഒ' to "o", 'ഓ' to "oo", 'ഔ' to "au"
            ),
            matras = mapOf(
                'ാ' to "aa", 'ി' to "i", 'ീ' to "ee", 'ു' to "u", 'ൂ' to "oo",
                'ൃ' to "ru", 'െ' to "e", 'േ' to "ee", 'ൈ' to "ai",
                'ൊ' to "o", 'ോ' to "oo", 'ൌ' to "au", 'ൗ' to "au"
            ),
            symbols = mapOf(
                '൦' to "0", '൧' to "1", '൨' to "2", '൩' to "3", '൪' to "4",
                '൫' to "5", '൬' to "6", '൭' to "7", '൮' to "8", '൯' to "9"
            ),
            virama = '്',
            anusvara = 'ം',
            chandrabindu = 'ഁ',
            visarga = 'ഃ',
            avagraha = 'ഽ',
            splitMatraAuMark = 'ൗ'
        )
    )
}
