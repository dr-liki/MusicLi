package com.drliki.MusicLi.data.video

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal const val MIN_VIDEO_CANDIDATE_SCORE = 60
private const val SAME_LENGTH_TOLERANCE_SECONDS = 20
private val OFFICIAL_VIDEO_MARKERS = setOf(
    "official music video", "official video", "music video", "official visualizer", "video song", "full video"
)
private const val MIN_ARTIST_WORD_LENGTH = 4

private val UNRELATED_UPLOAD_WORDS = setOf(
    "bgmi", "pubg", "freefire", "game", "gameplay", "gaming", "gamer", "montage", "fortnite", "minecraft",
    "valorant", "gta", "roblox", "playthrough", "walkthrough", "esports", "tournament", "amv", "fmv",
    "edit", "edits", "status", "tiktok", "reels",
)

private val LABEL_CHANNELS = listOf(
    "tseries", "tips", "saregama", "sonymusic", "zeemusic", "yrf", "universal", "musicindia",
    "warner", "adityamusic", "timesmusic", "laharimusic", "speedrecords",
)
private val ARTIST_SEPARATOR = Regex("""\s*(?:,|&|/|\bfeat\.?\b|\bft\.?\b|\bx\b|\band\b)\s*""", RegexOption.IGNORE_CASE)

data class VideoSearchCandidate(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationSeconds: Int?,
    val matchScore: Int,
    val thumbnailUrl: String? = null,
)

/** Accepts a YouTube video ID or a standard YouTube watch/share URL. */
fun extractYoutubeVideoId(input: String): String? {
    val value = input.trim()
    if (Regex("^[A-Za-z0-9_-]{11}$").matches(value)) return value
    val match = Regex(
        "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#]*&)?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})",
        RegexOption.IGNORE_CASE,
    ).find(value) ?: return null
    return match.groupValues[1]
}

/**
 * Scores a search result as the video for a track, or null when it is not that
 * track at all.
 */
fun scoreVideoCandidate(
    candidateTitle: String,
    candidateArtist: String,
    candidateDurationSec: Int?,
    wantedTitle: String,
    wantedArtist: String,
    expectedDurationMs: Long?,
): Int? {
    val wantedTokens = TextMatch.tokens(TextMatch.baseTitle(wantedTitle))
        .ifEmpty { TextMatch.normalize(wantedTitle).split(' ').filter(String::isNotBlank).toSet() }
    val title = TextMatch.normalize(candidateTitle)
    val words = title.split(' ').toSet()
    if (wantedTokens.isEmpty() || !words.containsAll(wantedTokens)) return null

    if (!TextMatch.isSameTake(candidateTitle, wantedTitle, wantedArtist, candidateArtist)) return null
    val wantedWordsForUploads = TextMatch.normalize(wantedTitle).split(' ').toSet()
    if (UNRELATED_UPLOAD_WORDS.any { it in words && it !in wantedWordsForUploads } ||
        "free fire" in title || "e sports" in title
    ) return null

    fun compact(value: String) = TextMatch.normalize(value).replace(" ", "")
    val channel = compact(candidateArtist)
    if ("gaming" in channel || "gamer" in channel) return null
    val compactTitle = title.replace(" ", "")
    val fromLabel = LABEL_CHANNELS.any { it in channel }
    var artistOwnsChannel = false
    if (wantedArtist.isNotBlank()) {
        val artists = wantedArtist.split(ARTIST_SEPARATOR).map(::compact).filter { it.length >= 2 }
        artistOwnsChannel = artists.any { it in channel } ||
            (channel.length >= 2 && channel in compact(wantedArtist))
        if (!artistOwnsChannel && !fromLabel && artists.none { it in compactTitle }) return null
    }

    if (!artistOwnsChannel && !fromLabel && "vevo" !in channel) return null

    val wantedWords = TextMatch.normalize(wantedTitle).split(' ').toSet()
    fun unwanted(vararg variants: String) = variants.any { it in words && it !in wantedWords }

    var score = 40
    if (artistOwnsChannel) score += 30 else if (wantedArtist.isNotBlank()) score += 15
    if ("official music video" in title || "official video" in title) score += 50
    else if ("music video" in title || "official visualizer" in title || "video song" in title || "full video" in title) score += 30
    if ("mv" in words || " m v " in " $title ") score += 25
    if ("vevo" in channel) score += 35
    if (fromLabel) score += 20 else if ("official" in channel) score += 15

    if ("lyric" in words || "lyrics" in words) score -= 30
    if ("audio" in words || "topic" in words || candidateArtist.endsWith("- Topic")) score -= 40
    if (unwanted("live")) score -= 50
    if (unwanted("remix")) score -= 70
    if (unwanted("cover", "karaoke", "reaction")) score -= 80
    if (unwanted("mashup", "parody", "tribute")) score -= 90
    if (unwanted("slowed", "reverb", "sped", "8d")) score -= 80
    if (unwanted("teaser", "trailer", "preview", "bts", "short", "shorts")) score -= 100

    if (expectedDurationMs != null && expectedDurationMs > 30_000L &&
        candidateDurationSec != null && candidateDurationSec > 0
    ) {
        val expectedSec = (expectedDurationMs / 1000L).toInt()
        val delta = abs(candidateDurationSec - expectedSec)
        when {
            delta <= 15 -> score += 10
            delta <= 45 -> score += 3
        }
    }
    return score
}

fun isSameSongVideo(
    candidateTitle: String,
    candidateChannel: String,
    candidateSeconds: Int?,
    songTitle: String,
    songArtist: String,
    songDurationMs: Long?,
): Boolean {
    val wantedTitle = TextMatch.normalize(songTitle)
    if (wantedTitle.isBlank()) return false
    val title = TextMatch.normalize(candidateTitle)
    if (" $wantedTitle " !in " $title ") return false

    val candidateWords = (title.split(' ') + TextMatch.normalize(candidateChannel).split(' ')).toSet()
    val namesArtist = TextMatch.normalize(songArtist).split(' ')
        .any { it.length >= MIN_ARTIST_WORD_LENGTH && it in candidateWords }
    val claimsOfficialVideo = OFFICIAL_VIDEO_MARKERS.any { it in title }
    if (namesArtist || claimsOfficialVideo) return true

    if (candidateSeconds == null || candidateSeconds <= 0 || songDurationMs == null || songDurationMs <= 0) return false
    return abs(candidateSeconds - (songDurationMs / 1_000L).toInt()) <= SAME_LENGTH_TOLERANCE_SECONDS
}

internal object TextMatch {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}\\p{M}]+")
    private val MULTI_SPACE = Regex("\\s+")

    val TAKE_WORDS = setOf(
        "live", "remix", "remixes", "rmx", "karaoke", "cover", "instrumental", "slowed", "sped",
        "spedup", "nightcore", "acoustic", "demo", "extended", "unplugged", "rerecorded",
        "vip", "8d", "boosted", "bassboosted", "lofi", "reverb", "rework", "bootleg",
        "flip", "mashup", "medley", "reprise", "acapella", "acappella", "orchestral",
        "symphonic", "concert"
    )

    val MATCH_NOISE_WORDS = setOf(
        "official", "audio", "video", "visualizer", "lyrics", "lyric", "hd", "hq", "4k", "track", "music",
        "ost", "soundtrack", "lyrical", "full"
    )

    private val FEATURING_CLAUSE = Regex("(?i)[(\\[]\\s*(feat(?:uring)?|ft)\\.?\\s+.*?[)\\]]")
    private val SOUNDTRACK_OR_OST_CLAUSE =
        Regex("(?i)[(\\[]\\s*(from\\s+[\"']?|ost\\b|original\\s+(?:motion\\s+picture\\s+)?soundtrack)[^)\\]]*[)\\]]")
    private val VERSION_OR_LABEL_CLAUSE =
        Regex("(?i)[(\\[][^)\\]]*(official|music\\s*video|audio|video|visualizer|lyrics?|hd|hq|4k|live|remix|acoustic|demo|edit|remaster(?:ed)?|mono|stereo|deluxe|bonus|version|mix)[^)\\]]*[)\\]]")

    private val LATIN_DIACRITICS = Regex("(?<=[A-Za-z])\\p{M}+")

    fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        val nfd = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val stripped = nfd.replace(LATIN_DIACRITICS, "")
        val composed = Normalizer.normalize(stripped, Normalizer.Form.NFKC)
        return composed
            .replace(NON_WORD, " ")
            .trim()
            .replace(MULTI_SPACE, " ")
    }

    fun tokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .filter { it.isNotBlank() && it !in MATCH_NOISE_WORDS }
        .toSet()

    fun baseTitle(value: String): String = value
        .substringBefore(" | ")
        .substringBefore(" // ")
        .replace(FEATURING_CLAUSE, " ")
        .replace(SOUNDTRACK_OR_OST_CLAUSE, " ")
        .replace(VERSION_OR_LABEL_CLAUSE, " ")

    fun takes(title: String, ignore: Set<String> = emptySet()): Set<String> =
        normalize(title).split(' ').filter { it in TAKE_WORDS && it !in ignore }.toSet()

    fun isSameTake(candidateTitle: String, wantedTitle: String, vararg artists: String): Boolean {
        val ignore = artists.flatMap { normalize(it).split(' ') }.toSet()
        return takes(candidateTitle, ignore) == takes(wantedTitle, ignore)
    }
}
