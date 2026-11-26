package com.drliki.MusicLi.data.network.lyrics.providers

enum class LyricsProviderId(
    val displayName: String,
    val supportsSynced: Boolean,
    val supportsWordSync: Boolean,
    val capabilitiesLabel: String,
    val attributionUrl: String? = null,
) {
    LRCLIB(
        displayName = "LRCLIB",
        supportsSynced = true,
        supportsWordSync = true,
        capabilitiesLabel = "Synced · Plain",
        attributionUrl = "https://lrclib.net"
    ),
    BETTER_LYRICS(
        displayName = "Better Lyrics",
        supportsSynced = true,
        supportsWordSync = true,
        capabilitiesLabel = "Word Sync · TTML",
        attributionUrl = "https://betterlyrics.org"
    ),
    LRC_RED(
        displayName = "lrc.red",
        supportsSynced = true,
        supportsWordSync = true,
        capabilitiesLabel = "Word Sync · LRC",
        attributionUrl = "https://lrc.red"
    ),
    PAXSENIX(
        displayName = "Paxsenix",
        supportsSynced = true,
        supportsWordSync = true,
        capabilitiesLabel = "Word Sync · Apple Music",
        attributionUrl = "https://lyrics.paxsenix.org"
    ),
    KUGOU(
        displayName = "KuGou",
        supportsSynced = true,
        supportsWordSync = true,
        capabilitiesLabel = "Synced · LRC"
    ),
    JIOSAAVN(
        displayName = "JioSaavn",
        supportsSynced = false,
        supportsWordSync = false,
        capabilitiesLabel = "Plain · Indic"
    ),
    YOUTUBE(
        displayName = "YouTube",
        supportsSynced = true,
        supportsWordSync = false,
        capabilitiesLabel = "Captions · Synced",
        attributionUrl = "https://www.youtube.com"
    ),
    NETEASE(
        displayName = "NetEase",
        supportsSynced = true,
        supportsWordSync = false,
        capabilitiesLabel = "Synced · Translation"
    ),
    LYRICS_OVH(
        displayName = "Lyrics.ovh",
        supportsSynced = false,
        supportsWordSync = false,
        capabilitiesLabel = "Plain Fallback",
        attributionUrl = "https://lyrics.ovh"
    );

    companion object {
        val DEFAULT_ORDER: List<LyricsProviderId> = listOf(
            LRCLIB,
            BETTER_LYRICS,
            LRC_RED,
            PAXSENIX,
            KUGOU,
            JIOSAAVN,
            YOUTUBE,
            NETEASE,
            LYRICS_OVH
        )

        val DEFAULT_ENABLED: Set<LyricsProviderId> = entries.toSet()

        fun fromNameOrNull(name: String?): LyricsProviderId? =
            entries.firstOrNull { it.name == name }

        fun parseOrder(raw: String?): List<LyricsProviderId> {
            val parsed = raw.orEmpty()
                .split(',')
                .mapNotNull { fromNameOrNull(it.trim()) }
                .distinct()
            val missing = DEFAULT_ORDER.filter { it !in parsed }
            return parsed + missing
        }

        fun parseEnabled(raw: String?): Set<LyricsProviderId> {
            if (raw.isNullOrBlank()) return DEFAULT_ENABLED
            val parsed = raw.split(',')
                .mapNotNull { fromNameOrNull(it.trim()) }
                .toSet()
            return parsed.ifEmpty { DEFAULT_ENABLED }
        }
    }
}

enum class LyricsSearchStrategy(val displayName: String, val storageValue: String) {
    AUTO_FASTEST("Auto Fastest", "AUTO_FASTEST"),
    PRIORITY_ORDER("Priority Order", "PRIORITY_ORDER");

    companion object {
        fun fromStorage(raw: String?): LyricsSearchStrategy =
            entries.firstOrNull { it.storageValue == raw } ?: AUTO_FASTEST
    }
}

data class LyricsProviderCandidate(
    val externalId: String,
    val providerId: LyricsProviderId,
    val title: String,
    val artistName: String,
    val albumName: String = "",
    val durationSec: Double? = null,
    val rawLyrics: String,
    val hasSyncedContent: Boolean,
    val hasWordSync: Boolean = false,
    val attributionUrl: String? = providerId.attributionUrl,
) {
    val compositeId: String get() = "${providerId.name}:$externalId"
}

interface LyricsProvider {
    val id: LyricsProviderId

    suspend fun search(
        title: String,
        artist: String?,
        album: String? = null,
        durationMs: Long? = null,
    ): List<LyricsProviderCandidate>
}
