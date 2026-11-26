package com.drliki.MusicLi.utils.romanize

/**
 * Rule- and lexicon-assisted offline romanizer for Urdu (Perso-Arabic / Nastaliq) lyrics.
 */
internal object UrduRomanizer {

    private val commonWords: Map<String, String> = mapOf(
        "دل" to "dil",
        "عشق" to "ishq",
        "محبت" to "mohabbat",
        "پیار" to "pyaar",
        "خدا" to "khuda",
        "زندگی" to "zindagi",
        "دنیا" to "duniya",
        "رات" to "raat",
        "راتیں" to "raatein",
        "دن" to "din",
        "شام" to "shaam",
        "صبح" to "subah",
        "خواب" to "khwaab",
        "یاد" to "yaad",
        "یادیں" to "yaadein",
        "آنکھ" to "aankh",
        "آنکھیں" to "aankhein",
        "آنکھوں" to "aankhon",
        "آنسو" to "aansoo",
        "ہونٹ" to "hont",
        "سانس" to "saans",
        "سانسیں" to "saansein",
        "جان" to "jaan",
        "جاناں" to "jaanaan",
        "صنم" to "sanam",
        "یار" to "yaar",
        "درد" to "dard",
        "غم" to "gham",
        "خوشی" to "khushi",
        "ساتھ" to "saath",
        "پاس" to "paas",
        "دور" to "door",
        "تنہا" to "tanha",
        "تیرا" to "tera",
        "تیری" to "teri",
        "تیرے" to "tere",
        "میرا" to "mera",
        "میری" to "meri",
        "میرے" to "mere",
        "ہمارا" to "hamaara",
        "ہماری" to "hamaari",
        "ہمارے" to "hamaare",
        "ہم" to "hum",
        "تم" to "tum",
        "تمہیں" to "tumhein",
        "تمہارا" to "tumhaara",
        "تمہاری" to "tumhaari",
        "تمہارے" to "tumhaare",
        "میں" to "main",
        "مجھے" to "mujhe",
        "مجھ" to "mujh",
        "تجھے" to "tujhe",
        "تجھ" to "tujh",
        "اس" to "us",
        "اسے" to "use",
        "انہیں" to "unhein",
        "اپنا" to "apna",
        "اپنی" to "apni",
        "اپنے" to "apne",
        "کوئی" to "koi",
        "کچھ" to "kuchh",
        "سب" to "sab",
        "کبھی" to "kabhi",
        "ابھی" to "abhi",
        "یہاں" to "yahaan",
        "وہاں" to "wahaan",
        "جہاں" to "jahaan",
        "کہاں" to "kahaan",
        "جب" to "jab",
        "تب" to "tab",
        "اب" to "ab",
        "کیوں" to "kyun",
        "کیسے" to "kaise",
        "کیسا" to "kaisa",
        "کیسی" to "kaisi",
        "کون" to "kaun",
        "کیا" to "kya",
        "ہاں" to "haan",
        "نہ" to "na",
        "نہیں" to "nahin",
        "مت" to "mat",
        "بھی" to "bhi",
        "ہی" to "hi",
        "تو" to "to",
        "اور" to "aur",
        "مگر" to "magar",
        "لیکن" to "lekin",
        "کیونکہ" to "kyunke",
        "اگر" to "agar",
        "کہ" to "ke",
        "یہ" to "ye",
        "وہ" to "wo",
        "جو" to "jo",
        "کا" to "ka",
        "کی" to "ki",
        "کے" to "ke",
        "کو" to "ko",
        "سے" to "se",
        "پر" to "par",
        "تک" to "tak",
        "بن" to "bin",
        "بنا" to "bina",
        "ہے" to "hai",
        "ہیں" to "hain",
        "ہوں" to "hoon",
        "تھا" to "tha",
        "تھی" to "thi",
        "تھے" to "the",
        "تھیں" to "thin",
        "ہو" to "ho",
        "ہوا" to "hua",
        "ہوئی" to "hui",
        "ہوئے" to "hue",
        "ہوگا" to "hoga",
        "ہوگی" to "hogi",
        "ہونگے" to "honge",
        "کر" to "kar",
        "کرنا" to "karna",
        "کرتا" to "karta",
        "کرتی" to "karti",
        "کرتے" to "karte",
        "رہا" to "raha",
        "رہی" to "rahi",
        "رہے" to "rahe",
        "گیا" to "gaya",
        "گئی" to "gayi",
        "گئے" to "gaye",
        "آیا" to "aaya",
        "آئی" to "aayi",
        "آئے" to "aaye",
        "لگا" to "laga",
        "لگی" to "lagi",
        "لگے" to "lage",
        "دیا" to "diya",
        "لیا" to "liya",
        "سکتا" to "sakta",
        "چاہیے" to "chahiye",
        "محسوس" to "mehsoos",
        "مسکان" to "muskaan",
        "ہنسی" to "hansi",
        "رونا" to "rona",
        "گیت" to "geet",
        "ساز" to "saaz",
        "آواز" to "aawaaz",
        "پھول" to "phool",
        "چاند" to "chaand",
        "تارے" to "taare",
        "آسمان" to "aasmaan",
        "زمین" to "zameen",
        "ہوا" to "hawa",
        "بارش" to "baarish",
        "موسم" to "mausam",
        "وقت" to "waqt",
        "لمحہ" to "lamha",
        "پل" to "pal",
        "سفر" to "safar",
        "راستہ" to "raasta",
        "منزل" to "manzil",
        "سایہ" to "saaya",
        "روشنی" to "roshni",
        "اندھیرا" to "andhera",
        "خاموش" to "khaamosh",
        "خاموشی" to "khaamoshi",
        "سلام" to "salaam",
        "الوداع" to "alvida",
        "شکریہ" to "shukriya",
        "وعدہ" to "waada",
        "بھروسہ" to "bharosa",
        "سچ" to "sach",
        "جھوٹ" to "jhoot",
        "جگ" to "jag",
        "نیند" to "neend",
        "تیز" to "tez",
        "پانی" to "paani",
        "آگ" to "aag",
        "خوشبو" to "khushboo",
        "رنگ" to "rang"
    )

    private val aspiratedBaseMap: Map<Char, String> = mapOf(
        'ب' to "bh",
        'پ' to "ph",
        'ت' to "th",
        'ٹ' to "th",
        'ج' to "jh",
        'چ' to "chh",
        'د' to "dh",
        'ڈ' to "dh",
        'ڑ' to "rh",
        'ک' to "kh",
        'گ' to "gh",
        'ر' to "rh",
        'ل' to "lh",
        'م' to "mh",
        'ن' to "nh"
    )

    private val consonantMap: Map<Char, String> = mapOf(
        'ب' to "b",
        'پ' to "p",
        'ت' to "t",
        'ٹ' to "t",
        'ث' to "s",
        'ج' to "j",
        'چ' to "ch",
        'ح' to "h",
        'خ' to "kh",
        'د' to "d",
        'ڈ' to "d",
        'ذ' to "z",
        'ر' to "r",
        'ڑ' to "r",
        'ز' to "z",
        'ژ' to "zh",
        'س' to "s",
        'ش' to "sh",
        'ص' to "s",
        'ض' to "z",
        'ط' to "t",
        'ظ' to "z",
        'غ' to "gh",
        'ف' to "f",
        'ق' to "q",
        'ک' to "k",
        'گ' to "g",
        'ل' to "l",
        'م' to "m",
        'ن' to "n",
        'ں' to "n",
        'ھ' to "h"
    )

    private val punctuationMap: Map<Char, String> = mapOf(
        '،' to ",",
        '۔' to ".",
        '؟' to "?",
        '؛' to ";",
        '٪' to "%"
    )

    private val urduDigits: Map<Char, Char> = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private val diacritics = setOf(
        '\u064E', // Zabar (a)
        '\u0650', // Zer (i)
        '\u064F', // Pesh (u)
        '\u0651', // Shadda (gemination)
        '\u0652', // Jazm / Sukun
        '\u064B', // Tanwin Fathatan (an)
        '\u064C', // Tanwin Dammatan (un)
        '\u064D', // Tanwin Kasratan (in)
        '\u0670', // Superscript Alef (aa)
        '\u0654', // Hamza above
        '\u0655', // Hamza below
        '\u0653'  // Maddah above
    )

    fun romanizeWord(token: String, style: RomanizationStyle = RomanizationStyle.SING_ALONG): String {
        if (token.isEmpty()) return token
        val normalized = normalizeArabicVariants(token)
        commonWords[normalized]?.let { return applyStyle(it, style) }

        val withoutHarakat = buildString(normalized.length) {
            for (ch in normalized) {
                if (ch !in diacritics && ch != '\u0640') append(ch)
            }
        }
        if (withoutHarakat != normalized) {
            commonWords[withoutHarakat]?.let { return applyStyle(it, style) }
        }

        val tokens = romanizeByRules(normalized)
        return applyStyle(tokens, style)
    }

    private fun normalizeArabicVariants(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            append(
                when (ch) {
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    'ة' -> 'ہ'
                    'أ', 'إ' -> 'ا'
                    'ؤ' -> 'و'
                    'ئ' -> 'ی'
                    '\u200C' -> ' ' // ZWNJ boundary
                    else -> ch
                }
            )
        }
    }

    private fun romanizeByRules(word: String): String {
        val sb = StringBuilder(word.length * 2)
        val n = word.length
        var i = 0
        var lastWasConsonant = false
        var lastConsonantText = ""

        while (i < n) {
            val ch = word[i]
            val next = word.getOrNull(i + 1)

            punctuationMap[ch]?.let {
                sb.append(it)
                lastWasConsonant = false
                lastConsonantText = ""
                i++
                continue
            }
            urduDigits[ch]?.let {
                sb.append(it)
                lastWasConsonant = false
                lastConsonantText = ""
                i++
                continue
            }
            if (ch == ' ' || ch == '\u0640') {
                if (ch == ' ') sb.append(' ')
                lastWasConsonant = false
                lastConsonantText = ""
                i++
                continue
            }

            // Harakat / diacritics
            when (ch) {
                '\u064E' -> { sb.append('a'); lastWasConsonant = false; i++; continue }
                '\u0650' -> { sb.append('i'); lastWasConsonant = false; i++; continue }
                '\u064F' -> { sb.append('u'); lastWasConsonant = false; i++; continue }
                '\u0652' -> { lastWasConsonant = false; i++; continue } // explicit sukun
                '\u064B' -> { sb.append("an"); lastWasConsonant = false; i++; continue }
                '\u064C' -> { sb.append("un"); lastWasConsonant = false; i++; continue }
                '\u064D' -> { sb.append("in"); lastWasConsonant = false; i++; continue }
                '\u0670' -> { sb.append("aa"); lastWasConsonant = false; i++; continue }
                '\u0651' -> {
                    if (lastConsonantText.isNotEmpty()) sb.append(lastConsonantText)
                    i++
                    continue
                }
                '\u0654' -> {
                    // Izafat on final gol-he (ۂ)
                    if (i == n - 1) sb.append("-e-")
                    i++
                    continue
                }
                in diacritics -> { i++; continue }
            }

            // Do-chashmi he aspiration digraph (e.g. بھ، کھ، پھ، دھ)
            if (next == 'ھ' && ch in aspiratedBaseMap) {
                val asp = aspiratedBaseMap.getValue(ch)
                if (lastWasConsonant && !hasExplicitVowelAfterPrev(word, i - 1)) {
                    sb.append('a')
                }
                sb.append(asp)
                lastWasConsonant = true
                lastConsonantText = asp
                i += 2
                continue
            }

            when (ch) {
                'آ' -> {
                    sb.append("aa")
                    lastWasConsonant = false
                    lastConsonantText = ""
                }
                'ا' -> {
                    val isStart = i == 0 || word[i - 1] == ' '
                    if (isStart) {
                        // Word-initial alef carries short vowel unless followed by و/ی long vowel
                        if (next != 'و' && next != 'ی') {
                            sb.append('a')
                        }
                    } else {
                        sb.append("aa")
                    }
                    lastWasConsonant = false
                    lastConsonantText = ""
                }
                'و' -> {
                    val isStart = i == 0 || word[i - 1] == ' '
                    if (isStart || next in setOf('ا', 'آ', 'ی', 'ے', '\u064E', '\u0650')) {
                        if (lastWasConsonant) sb.append('a')
                        sb.append('w')
                        lastWasConsonant = true
                        lastConsonantText = "w"
                    } else if (i == n - 1) {
                        sb.append("o")
                        lastWasConsonant = false
                    } else {
                        sb.append("oo")
                        lastWasConsonant = false
                    }
                }
                'ی' -> {
                    val isStart = i == 0 || word[i - 1] == ' '
                    if (isStart || next in setOf('ا', 'آ', 'و', 'ے', '\u064E', '\u064F')) {
                        if (lastWasConsonant) sb.append('a')
                        sb.append('y')
                        lastWasConsonant = true
                        lastConsonantText = "y"
                    } else {
                        sb.append("ee")
                        lastWasConsonant = false
                    }
                }
                'ے' -> {
                    sb.append('e')
                    lastWasConsonant = false
                    lastConsonantText = ""
                }
                'ہ', 'ۂ' -> {
                    val isStart = i == 0 || word[i - 1] == ' '
                    if (isStart) {
                        sb.append('h')
                        lastWasConsonant = true
                        lastConsonantText = "h"
                    } else if (i == n - 1 || word.getOrNull(i + 1) == ' ') {
                        if (ch == 'ۂ') sb.append("a-e") else sb.append('a')
                        lastWasConsonant = false
                        lastConsonantText = ""
                    } else {
                        if (lastWasConsonant) sb.append('e')
                        sb.append('h')
                        lastWasConsonant = true
                        lastConsonantText = "h"
                    }
                }
                'ع' -> {
                    val isStart = i == 0 || word[i - 1] == ' '
                    if (isStart && next !in setOf('\u064E', '\u0650', '\u064F', 'ا', 'و', 'ی')) {
                        sb.append('a')
                    } else if (i == n - 1) {
                        sb.append('a')
                    }
                    lastWasConsonant = false
                    lastConsonantText = ""
                }
                'ء' -> {
                    // Hamza separator in words like آؤ / نئی when not mapped directly
                    lastWasConsonant = false
                    lastConsonantText = ""
                }
                else -> {
                    val mapped = consonantMap[ch]
                    if (mapped != null) {
                        // Light epenthetic vowel inside unvoweled consonant clusters so unknown words remain readable
                        if (lastWasConsonant && ch != 'ں' && shouldInsertLinkVowel(word, i)) {
                            sb.append('a')
                        }
                        sb.append(mapped)
                        lastWasConsonant = ch != 'ں'
                        lastConsonantText = mapped
                    } else {
                        sb.append(ch)
                        lastWasConsonant = false
                        lastConsonantText = ""
                    }
                }
            }
            i++
        }
        return sb.toString()
    }

    private fun hasExplicitVowelAfterPrev(word: String, prevIndex: Int): Boolean {
        if (prevIndex < 0) return true
        return word[prevIndex] in diacritics
    }

    private fun shouldInsertLinkVowel(word: String, index: Int): Boolean {
        val next = word.getOrNull(index + 1)
        // If this is the penultimate or final consonant of an unvoweled stem and preceded by consonant, link with 'a'
        // unless this is the final consonant ending a CVC closed syllable.
        val isFinal = index == word.length - 1 || word[index + 1] == ' '
        val prevIsDiacritic = index > 0 && word[index - 1] in diacritics
        if (prevIsDiacritic) return false
        if (isFinal) return false
        return next in consonantMap
    }

    private fun applyStyle(text: String, style: RomanizationStyle): String {
        if (style == RomanizationStyle.SING_ALONG) return text
        return text
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("oo", "u")
    }
}
