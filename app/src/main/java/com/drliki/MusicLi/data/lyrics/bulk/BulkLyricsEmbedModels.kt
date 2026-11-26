package com.drliki.MusicLi.data.lyrics.bulk

import androidx.annotation.Keep
import com.drliki.MusicLi.data.network.lyrics.providers.LyricsProviderId

enum class BulkLyricsEmbedStatus {
    IDLE,
    RUNNING,
    PAUSED_USER,
    PAUSED_RATE_LIMIT,
    PAUSED_NETWORK,
    NEEDS_PERMISSION,
    COMPLETED,
}

enum class BulkLyricsLogOutcome {
    EMBEDDED,
    SKIPPED_EXISTING_SYNCED,
    NOT_FOUND,
    RATE_LIMITED,
    NETWORK_ERROR,
    PERMISSION_REQUIRED,
    FAILED,
}

@Keep
data class BulkLyricsLogEntry(
    val songId: Long,
    val songTitle: String,
    val artist: String,
    val outcome: BulkLyricsLogOutcome,
    val providerLabel: String? = null,
    val detail: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

@Keep
data class BulkLyricsEmbedOptions(
    val syncedOnly: Boolean = true,
    val skipExistingSynced: Boolean = true,
    val saveLrcSidecar: Boolean = false,
    val delayBetweenSongsMs: Long = 1_000L,
)

data class BulkLyricsCurrentSong(
    val songId: Long,
    val title: String,
    val artist: String,
    val statusText: String,
)

data class BulkLyricsEmbedUiState(
    val status: BulkLyricsEmbedStatus = BulkLyricsEmbedStatus.IDLE,
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val embeddedCount: Int = 0,
    val skippedCount: Int = 0,
    val notFoundCount: Int = 0,
    val failedCount: Int = 0,
    val currentSong: BulkLyricsCurrentSong? = null,
    val rateLimitProviderLabel: String? = null,
    val resumeAtEpochMs: Long? = null,
    val pendingPermissionCount: Int = 0,
    val options: BulkLyricsEmbedOptions = BulkLyricsEmbedOptions(),
    val priorityOrder: List<LyricsProviderId> = emptyList(),
    val enabledProviders: Set<LyricsProviderId> = emptySet(),
    val logs: List<BulkLyricsLogEntry> = emptyList(),
    val errorMessage: String? = null,
    val isScanningLibrary: Boolean = false,
    val retryableFailedCount: Int = 0,
) {
    val remainingCount: Int
        get() = (totalCount - processedCount).coerceAtLeast(0)

    val progressFraction: Float
        get() = if (totalCount > 0) {
            (processedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@Keep
internal data class BulkLyricsEmbedCheckpoint(
    val status: BulkLyricsEmbedStatus = BulkLyricsEmbedStatus.IDLE,
    val queuedSongIds: List<Long> = emptyList(),
    val nextIndex: Int = 0,
    val embeddedCount: Int = 0,
    val skippedCount: Int = 0,
    val notFoundCount: Int = 0,
    val failedCount: Int = 0,
    val failedSongIds: List<Long> = emptyList(),
    val resumeAtEpochMs: Long? = null,
    val rateLimitProviderId: String? = null,
    val pendingPermissionSongId: Long? = null,
    val options: BulkLyricsEmbedOptions = BulkLyricsEmbedOptions(),
    val logs: List<BulkLyricsLogEntry> = emptyList(),
    val errorMessage: String? = null,
) {
    fun toUiState(
        priorityOrder: List<LyricsProviderId>,
        enabledProviders: Set<LyricsProviderId>,
        currentSong: BulkLyricsCurrentSong? = null,
        pendingPermissionCount: Int = 0,
        isScanningLibrary: Boolean = false,
    ): BulkLyricsEmbedUiState {
        val providerLabel = rateLimitProviderId
            ?.let { raw -> runCatching { LyricsProviderId.valueOf(raw).displayName }.getOrNull() }
        return BulkLyricsEmbedUiState(
            status = status,
            totalCount = queuedSongIds.size,
            processedCount = nextIndex.coerceIn(0, queuedSongIds.size),
            embeddedCount = embeddedCount,
            skippedCount = skippedCount,
            notFoundCount = notFoundCount,
            failedCount = failedCount,
            currentSong = currentSong,
            rateLimitProviderLabel = providerLabel,
            resumeAtEpochMs = resumeAtEpochMs,
            pendingPermissionCount = pendingPermissionCount,
            options = options,
            priorityOrder = priorityOrder,
            enabledProviders = enabledProviders,
            logs = logs,
            errorMessage = errorMessage,
            isScanningLibrary = isScanningLibrary,
            retryableFailedCount = failedSongIds.size,
        )
    }
}
