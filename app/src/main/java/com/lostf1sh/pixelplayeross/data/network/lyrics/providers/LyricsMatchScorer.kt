package com.lostf1sh.pixelplayeross.data.network.lyrics.providers

import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.utils.MultiLangRomanizer
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal enum class LyricsMatchMode {
    AUTOMATIC,
    CANDIDATE
}

internal data class ScoredLyricsCandidate(
    val candidate: LyricsProviderCandidate,
    val score: Int
)

internal object LyricsMatchScorer {
    private val BRACKETED_QUALIFIER_REGEX = Regex(
        """[\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]([^)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]*)[\)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]"""
    )
    private val FEATURE_QUALIFIER_REGEX = Regex("""\b(feat(?:uring)?|ft)\.?\b""", RegexOption.IGNORE_CASE)
    private val TITLE_SEPARATOR_REGEX = Regex("""\s*[-\u2013\u2014:\uFF0D\u00B7\u30FB]\s*""")
    private val TIMING_VARIANT_KEYWORDS = setOf(
        "remix", "mix", "mashup", "bootleg", "edit", "extended", "radio", "club",
        "vip", "dub", "live", "acoustic", "unplugged", "sped", "slowed", "nightcore",
        "instrumental", "karaoke", "cover", "demo", "version", "rework", "flip", "refix"
    )
    private val TITLE_DROP_QUALIFIERS = setOf(
        "explicit", "clean", "mono", "stereo", "official audio", "official video"
    )
    private val UNKNOWN_ARTISTS = setOf(
        "", "<unknown>", "unknown", "unknown artist", "various artists", "various"
    )
    private val ARTIST_CONNECTOR_TOKENS = setOf(
        "feat", "featuring", "ft", "and", "with", "x", "vs", "the"
    )

    fun rankCandidates(
        song: Song,
        candidates: List<LyricsProviderCandidate>,
        mode: LyricsMatchMode,
        providerOrder: List<LyricsProviderId> = LyricsProviderId.DEFAULT_ORDER,
    ): List<ScoredLyricsCandidate> {
        val songDurationSec = song.duration / 1000.0
        val orderMap = providerOrder.withIndex().associate { it.value to it.index }

        return candidates
            .mapNotNull { candidate ->
                val score = scoreCandidate(song, candidate, mode, songDurationSec) ?: return@mapNotNull null
                ScoredLyricsCandidate(candidate, score)
            }
            .sortedWith(
                compareByDescending<ScoredLyricsCandidate> { it.candidate.hasWordSync }
                    .thenByDescending { it.candidate.hasSyncedContent }
                    .thenByDescending { it.score }
                    .thenBy { orderMap[it.candidate.providerId] ?: Int.MAX_VALUE }
                    .thenBy {
                        val d = it.candidate.durationSec
                        if (d != null && songDurationSec > 0.0) abs(d - songDurationSec) else Double.MAX_VALUE
                    }
            )
    }

    fun scoreCandidate(
        song: Song,
        candidate: LyricsProviderCandidate,
        mode: LyricsMatchMode,
        songDurationSeconds: Double = song.duration / 1000.0,
    ): Int? {
        if (candidate.rawLyrics.isBlank()) return null
        if (!variantDescriptorsCompatible(song, candidate.title)) return null

        val hasSynced = candidate.hasSyncedContent
        val candDur = candidate.durationSec?.takeIf { it > 0.0 }
        val durationScore: Int = if (songDurationSeconds > 0.0 && candDur != null) {
            val tolerance = durationToleranceSeconds(songDurationSeconds, hasSynced, mode)
            val diff = abs(candDur - songDurationSeconds)
            if (diff > tolerance) return null
            (tolerance - diff).coerceAtLeast(0.0).toInt()
        } else {
            // Plain-only or caption sources without explicit duration must clear stricter title+artist gates.
            0
        }

        val titleScore = titleMatchScore(song.title, candidate.title, mode) ?: return null
        val artistScore = artistMatchScore(song.displayArtist, candidate.artistName)
        if (!isUnknownArtist(song.displayArtist) && artistScore == null) {
            // Allow YouTube channel-uploaded lyric videos if title matches strongly in candidate mode
            if (!(candidate.providerId == LyricsProviderId.YOUTUBE && mode == LyricsMatchMode.CANDIDATE && titleScore >= 60)) {
                return null
            }
        }

        val syncBonus = when {
            candidate.hasWordSync -> 15
            hasSynced -> 10
            else -> 0
        }
        return titleScore + (artistScore ?: 0) + durationScore + syncBonus
    }

    fun cleanTitleSmart(title: String): String {
        var cleaned = title.replace(Regex("^[\\d\\s.\\-\\uFF0D]+"), "")
        val splitRegex = Regex("""[-\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]""")
        cleaned = cleaned.split(splitRegex).firstOrNull() ?: cleaned
        return cleaned.trim()
    }

    private fun durationToleranceSeconds(
        songDurationSeconds: Double,
        hasSyncedLyrics: Boolean,
        mode: LyricsMatchMode,
    ): Double = when (mode) {
        LyricsMatchMode.AUTOMATIC -> {
            if (hasSyncedLyrics) (songDurationSeconds * 0.02).coerceIn(5.0, 8.0)
            else (songDurationSeconds * 0.04).coerceIn(8.0, 15.0)
        }
        LyricsMatchMode.CANDIDATE -> 18.0
    }

    private fun titleMatchScore(songTitle: String, responseTitle: String, mode: LyricsMatchMode): Int? {
        val songRaw = rawBaseTitleForMatching(songTitle)
        val responseRaw = rawBaseTitleForMatching(responseTitle)
        val songBase = normalizeForMatch(songRaw)
        val responseBase = normalizeForMatch(responseRaw)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 70

        val needsRomanization = MultiLangRomanizer.isScriptThatNeedsRomanization(songRaw) ||
            MultiLangRomanizer.isScriptThatNeedsRomanization(responseRaw)
        if (needsRomanization) {
            val songRoman = romanizedMatchKey(songRaw)
            val responseRoman = romanizedMatchKey(responseRaw)
            if (songRoman == responseRoman && songRoman.isNotBlank()) return 65
        }

        val songTokens = matchTokens(songBase)
        val responseTokens = matchTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        if (songTokens.size == 1 || responseTokens.size == 1) {
            if (songTokens == responseTokens) return 60
            if (needsRomanization) {
                val s1 = songBase.replace(" ", "")
                val s2 = responseBase.replace(" ", "")
                if (s1.isNotBlank() && s2.isNotBlank() && (s1.contains(s2) || s2.contains(s1))) return 55
            }
            return null
        }

        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return if (mode == LyricsMatchMode.AUTOMATIC) 58 else 54
        }

        val overlap = songTokens.intersect(responseTokens).size
        val songCoverage = overlap.toDouble() / songTokens.size
        val responseCoverage = overlap.toDouble() / responseTokens.size
        val requiredSongCoverage = if (mode == LyricsMatchMode.AUTOMATIC) 0.85 else 0.70
        val requiredResponseCoverage = if (mode == LyricsMatchMode.AUTOMATIC) 0.70 else 0.50

        return if (songCoverage >= requiredSongCoverage && responseCoverage >= requiredResponseCoverage) 45 else null
    }

    private fun artistMatchScore(songArtist: String, responseArtist: String): Int? {
        if (isUnknownArtist(songArtist)) return 0

        val songBase = normalizeForMatch(songArtist)
        val responseBase = normalizeForMatch(responseArtist)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 30
        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return 22
        }

        if (MultiLangRomanizer.isScriptThatNeedsRomanization(songArtist) ||
            MultiLangRomanizer.isScriptThatNeedsRomanization(responseArtist)
        ) {
            val songRoman = romanizedMatchKey(songArtist)
            val responseRoman = romanizedMatchKey(responseArtist)
            if (songRoman == responseRoman && songRoman.isNotBlank()) return 28
        }

        val songTokens = artistTokens(songBase)
        val responseTokens = artistTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        val overlap = songTokens.intersect(responseTokens).size
        val smallerCoverage = overlap.toDouble() / minOf(songTokens.size, responseTokens.size)
        return if (smallerCoverage >= 0.5) 12 else null
    }

    private fun variantDescriptorsCompatible(song: Song, candidateTitle: String): Boolean {
        val songVariants = timingVariantTokens(song.title) + timingVariantTokensFromFileName(song)
        val responseVariants = timingVariantTokens(candidateTitle)
        if (songVariants.isEmpty()) return responseVariants.isEmpty()
        return responseVariants == songVariants
    }

    private fun rawBaseTitleForMatching(title: String): String {
        var base = title.replace(Regex("""^\s*\d{1,3}\s*[\._-]\s+"""), "")
        base = BRACKETED_QUALIFIER_REGEX.replace(base) { match ->
            val qualifier = match.groupValues.getOrNull(1).orEmpty()
            if (shouldDropTitleQualifier(qualifier)) " " else " $qualifier "
        }
        var parts = TITLE_SEPARATOR_REGEX.split(base)
        while (parts.size > 1 && shouldDropTitleQualifier(parts.last())) {
            parts = parts.dropLast(1)
        }
        return parts.joinToString(" ")
    }

    private fun romanizedMatchKey(rawValue: String): String {
        val romanized = MultiLangRomanizer.romanizeLine(rawValue) ?: rawValue
        return normalizeForMatch(romanized)
    }

    private fun shouldDropTitleQualifier(value: String): Boolean {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return true
        return FEATURE_QUALIFIER_REGEX.containsMatchIn(value) ||
            timingVariantTokens(value).isNotEmpty() ||
            normalized in TITLE_DROP_QUALIFIERS
    }

    private fun timingVariantTokens(value: String): Set<String> {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return emptySet()
        val tokens = matchTokens(normalized)
        val variants = tokens.filter { it in TIMING_VARIANT_KEYWORDS }.toMutableSet()
        if (Regex("""\bmash\s+up\b""").containsMatchIn(normalized)) variants += "mashup"
        if ("versus" in tokens || "vs" in tokens) variants += "mashup"
        return variants
    }

    private fun timingVariantTokensFromFileName(song: Song): Set<String> {
        val fileName = if (song.path.isBlank()) "" else runCatching { File(song.path).nameWithoutExtension }.getOrDefault("")
        if (fileName.isBlank()) return emptySet()
        val variants = BRACKETED_QUALIFIER_REGEX
            .findAll(fileName)
            .flatMap { match -> timingVariantTokens(match.groupValues.getOrNull(1).orEmpty()) }
            .toMutableSet()
        val titleBase = normalizeForMatch(rawBaseTitleForMatching(song.title))
        if (titleBase.isBlank()) return variants
        TITLE_SEPARATOR_REGEX.split(fileName).forEach { part ->
            val normalizedPart = normalizeForMatch(part)
            if (normalizedPart.startsWith("$titleBase ")) {
                variants += timingVariantTokens(normalizedPart.removePrefix(titleBase).trim())
            }
        }
        return variants
    }

    private fun artistTokens(normalizedArtist: String): Set<String> =
        matchTokens(normalizedArtist).filterNot { it in ARTIST_CONNECTOR_TOKENS }.toSet()

    private fun matchTokens(normalizedValue: String): Set<String> =
        normalizedValue.split(' ').filter { it.isNotBlank() }.toSet()

    private fun containsWholePhrase(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return Regex("""(?:^|\s)${Regex.escape(needle)}(?:\s|$)""").containsMatchIn(haystack)
    }

    fun normalizeForMatch(value: String): String {
        val withoutDiacritics = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return withoutDiacritics
            .replace("&", " and ")
            .replace(Regex("""[\u2019'`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun isUnknownArtist(value: String): Boolean = normalizeForMatch(value) in UNKNOWN_ARTISTS
}
