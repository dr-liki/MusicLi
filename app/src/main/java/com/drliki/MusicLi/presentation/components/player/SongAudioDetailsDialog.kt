package com.drliki.MusicLi.presentation.components.player

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.drliki.MusicLi.presentation.components.MusicLiDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drliki.MusicLi.R
import com.drliki.MusicLi.data.explore.isExploreStream
import com.drliki.MusicLi.data.model.Song
import com.drliki.MusicLi.presentation.components.SmartImage
import com.drliki.MusicLi.utils.AudioMetaUtils
import com.drliki.MusicLi.utils.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private data class ExtraAudioFileDetails(
    val channelCount: Int? = null,
    val bitsPerSample: Int? = null,
    val fileSizeBytes: Long? = null,
    val detectedMimeType: String? = null,
    val detectedBitrate: Int? = null,
    val detectedSampleRate: Int? = null,
)

@Composable
fun SongAudioDetailsDialog(
    song: Song,
    playbackMimeType: String?,
    playbackBitrate: Int?,
    playbackSampleRate: Int?,
    hasSyncedLyrics: Boolean,
    hasPlainLyrics: Boolean,
    onDismiss: () -> Unit,
    onOpenFullSongInfo: () -> Unit,
) {
    val context = LocalContext.current

    val extraDetails by produceState(
        initialValue = ExtraAudioFileDetails(),
        key1 = song.id,
        key2 = song.path
    ) {
        value = withContext(Dispatchers.IO) {
            inspectAudioFileDetails(context, song)
        }
    }

    val effectiveMime = playbackMimeType
        ?: song.mimeType
        ?: extraDetails.detectedMimeType
    val effectiveBitrate = playbackBitrate?.takeIf { it > 0 }
        ?: song.bitrate?.takeIf { it > 0 }
        ?: extraDetails.detectedBitrate
    val effectiveSampleRate = playbackSampleRate?.takeIf { it > 0 }
        ?: song.sampleRate?.takeIf { it > 0 }
        ?: extraDetails.detectedSampleRate

    val codecLabel = AudioMetaUtils.mimeTypeToFormat(effectiveMime)
        .takeIf { it != "-" }
        ?.uppercase(Locale.getDefault())
        ?: effectiveMime
        ?: "Unknown"

    val channelLabel = when (extraDetails.channelCount) {
        1 -> "Mono (1 ch)"
        2 -> "Stereo (2 ch)"
        null -> null
        else -> "${extraDetails.channelCount} channels"
    }

    val lyricsLabel = when {
        hasSyncedLyrics -> "Synced (.lrc)"
        hasPlainLyrics -> "Plain text"
        !song.lyrics.isNullOrBlank() -> "Embedded"
        else -> "None"
    }

    val locationLabel = when {
        song.isExploreStream -> "Explore Stream (JioSaavn)"
        song.contentUriString.startsWith("navidrome://") -> "Navidrome Cloud"
        song.contentUriString.startsWith("jellyfin://") -> "Jellyfin Cloud"
        song.contentUriString.startsWith("telegram://") -> "Telegram Cloud"
        song.path.isNotBlank() -> song.path
        else -> song.contentUriString
    }
    val isRemoteStream = song.isExploreStream ||
        song.contentUriString.startsWith("http") ||
        song.contentUriString.startsWith("navidrome://") ||
        song.contentUriString.startsWith("jellyfin://") ||
        song.contentUriString.startsWith("telegram://")

    MusicLiDialog(
        onDismissRequest = onDismiss,
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    shape = RoundedCornerShape(14.dp),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmText = "More Info",
        onConfirm = {
            onDismiss()
            onOpenFullSongInfo()
        },
        dismissText = "Close",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Audio Format",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    AudioDetailRow(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Codec / MIME",
                        value = if (!effectiveMime.isNullOrBlank() && !codecLabel.equals(effectiveMime, ignoreCase = true)) {
                            "$codecLabel ($effectiveMime)"
                        } else {
                            codecLabel
                        }
                    )
                    effectiveSampleRate?.let { sr ->
                        AudioDetailRow(
                            icon = Icons.Rounded.Info,
                            label = "Sample Rate",
                            value = String.format(Locale.US, "%.1f kHz (%d Hz)", sr / 1000.0, sr)
                        )
                    }
                    effectiveBitrate?.let { br ->
                        AudioDetailRow(
                            icon = Icons.Rounded.AudioFile,
                            label = "Bitrate",
                            value = "${br / 1000} kbps"
                        )
                    }
                    channelLabel?.let { ch ->
                        AudioDetailRow(
                            icon = Icons.Rounded.MusicNote,
                            label = "Channels",
                            value = extraDetails.bitsPerSample?.let { "$ch • $it-bit" } ?: ch
                        )
                    }
                    extraDetails.fileSizeBytes?.takeIf { it > 0L }?.let { size ->
                        AudioDetailRow(
                            icon = Icons.Rounded.Storage,
                            label = "File Size",
                            value = Formatter.formatShortFileSize(context, size)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Track Details",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    AudioDetailRow(
                        icon = Icons.Rounded.Schedule,
                        label = stringResource(R.string.song_info_label_duration),
                        value = formatDuration(song.duration)
                    )
                    if (song.album.isNotBlank()) {
                        AudioDetailRow(
                            icon = Icons.Rounded.Album,
                            label = stringResource(R.string.song_field_album),
                            value = buildString {
                                append(song.album)
                                if (song.year > 0) append(" (${song.year})")
                            }
                        )
                    }
                    if (!song.genre.isNullOrBlank()) {
                        AudioDetailRow(
                            icon = Icons.Rounded.MusicNote,
                            label = stringResource(R.string.song_field_genre),
                            value = song.genre
                        )
                    }
                    if (song.trackNumber > 0) {
                        AudioDetailRow(
                            icon = Icons.Rounded.Info,
                            label = "Track / Disc",
                            value = song.discNumber?.takeIf { it > 0 }?.let { "Track ${song.trackNumber} • Disc $it" }
                                ?: "Track ${song.trackNumber}"
                        )
                    }
                    AudioDetailRow(
                        icon = Icons.Rounded.Lyrics,
                        label = stringResource(R.string.lyrics),
                        value = lyricsLabel
                    )
                    AudioDetailRow(
                        icon = if (isRemoteStream) Icons.Rounded.Cloud else Icons.Rounded.AudioFile,
                        label = if (isRemoteStream) stringResource(R.string.song_info_label_provider) else stringResource(R.string.song_info_label_path),
                        value = locationLabel,
                        maxLines = 3
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    maxLines: Int = 2,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun inspectAudioFileDetails(context: android.content.Context, song: Song): ExtraAudioFileDetails {
    var fileSize: Long? = null
    if (song.path.isNotBlank()) {
        val file = File(song.path)
        if (file.exists() && file.isFile) {
            fileSize = file.length()
        }
    }
    if (fileSize == null && song.contentUriString.startsWith("content://")) {
        fileSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(song.contentUriString), "r")?.use {
                it.length.takeIf { len -> len > 0L }
            }
        }.getOrNull()
    }

    if (song.path.isBlank()) {
        return ExtraAudioFileDetails(fileSizeBytes = fileSize)
    }
    val file = File(song.path)
    if (!file.exists() || !file.canRead()) {
        return ExtraAudioFileDetails(fileSizeBytes = fileSize)
    }

    var channelCount: Int? = null
    var bitsPerSample: Int? = null
    var mimeType: String? = null
    var sampleRate: Int? = null
    var bitrate: Int? = null

    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(song.path)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                mimeType = mime
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && format.containsKey("bits-per-sample")) {
                    bitsPerSample = runCatching { format.getInteger("bits-per-sample") }.getOrNull()
                }
                break
            }
        }
    } catch (_: Exception) {
    } finally {
        runCatching { extractor.release() }
    }

    return ExtraAudioFileDetails(
        channelCount = channelCount,
        bitsPerSample = bitsPerSample,
        fileSizeBytes = fileSize,
        detectedMimeType = mimeType,
        detectedBitrate = bitrate,
        detectedSampleRate = sampleRate,
    )
}
