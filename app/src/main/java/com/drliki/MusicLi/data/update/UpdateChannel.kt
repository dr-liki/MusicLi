package com.drliki.MusicLi.data.update

/**
 * Where builds of a channel are published. Both Stable and Alpha builds are published on
 * GitHub Releases (`dr-liki/MusicLi`).
 */
enum class UpdateChannel(val storageKey: String) {
    STABLE("stable"),
    ALPHA("alpha");

    companion object {
        fun fromStorageKey(key: String?): UpdateChannel? = entries.firstOrNull { it.storageKey == key }

        /**
         * The channel the running build was distributed through. CI-built alphas (and PR builds)
         * carry a pre-release suffix; stable releases use a plain semantic tag.
         */
        fun ofVersionName(versionName: String): UpdateChannel =
            if ('-' in versionName) ALPHA else STABLE
    }
}

/** `1.0.0` as published by the stable release workflow on GitHub Releases. */
data class StableVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<StableVersion> {

    override fun compareTo(other: StableVersion): Int = compareValuesBy(
        this, other, StableVersion::major, StableVersion::minor, StableVersion::patch
    )

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun parse(value: String): StableVersion? {
            val groups = PATTERN.matchEntire(value.trim())?.groupValues ?: return null
            return StableVersion(
                major = groups[1].toIntOrNull() ?: return null,
                minor = groups[2].toIntOrNull() ?: return null,
                patch = groups[3].toIntOrNull() ?: return null,
            )
        }
    }
}

/** `1.0.0-alpha.16` as published by the alpha-release workflow. */
data class AlphaVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int,
) : Comparable<AlphaVersion> {

    override fun compareTo(other: AlphaVersion): Int = compareValuesBy(
        this, other, AlphaVersion::major, AlphaVersion::minor, AlphaVersion::patch, AlphaVersion::build
    )

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)-alpha\.(\d+)$""")

        fun parse(value: String): AlphaVersion? {
            val groups = PATTERN.matchEntire(value.trim())?.groupValues ?: return null
            return AlphaVersion(
                major = groups[1].toIntOrNull() ?: return null,
                minor = groups[2].toIntOrNull() ?: return null,
                patch = groups[3].toIntOrNull() ?: return null,
                build = groups[4].toIntOrNull() ?: return null,
            )
        }
    }
}

/** A published build that can be offered to the user. */
data class AppRelease(
    val channel: UpdateChannel,
    val versionName: String,
    /** Optional version code when known; GitHub Releases compare semantically via versionName. */
    val versionCode: Long?,
    val apkUrl: String,
    val apkSizeBytes: Long?,
    val pageUrl: String,
)

/** Whether [this] release is newer than the running build. */
internal fun AppRelease.isNewerThan(installedVersionName: String, installedVersionCode: Long): Boolean =
    when (channel) {
        UpdateChannel.STABLE -> {
            val candidate = StableVersion.parse(versionName)
            if (candidate != null) {
                val installedStable = StableVersion.parse(installedVersionName)
                if (installedStable != null) {
                    candidate > installedStable
                } else {
                    val installedAlpha = AlphaVersion.parse(installedVersionName)
                    if (installedAlpha != null) {
                        candidate >= StableVersion(installedAlpha.major, installedAlpha.minor, installedAlpha.patch)
                    } else {
                        true
                    }
                }
            } else {
                (versionCode ?: 0L) > installedVersionCode
            }
        }
        UpdateChannel.ALPHA -> {
            val candidate = AlphaVersion.parse(versionName) ?: return false
            val installed = AlphaVersion.parse(installedVersionName) ?: return true
            candidate > installed
        }
    }
