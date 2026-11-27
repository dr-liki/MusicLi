package com.drliki.MusicLi.presentation.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.drliki.MusicLi.presentation.components.MusicLiDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drliki.MusicLi.R
import com.drliki.MusicLi.presentation.components.MiniPlayerHeight
import com.drliki.MusicLi.presentation.viewmodel.StorageSenseViewModel

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun StorageSenseScreen(
    onBack: () -> Unit,
    onOpenExploreDownloads: () -> Unit,
    viewModel: StorageSenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showConfirmLyricsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    val breakdown = state.breakdown

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.storage_sense_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading && !state.isClearing) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh sizes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading || state.isClearing) {
                item {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Summary Hero Card + Breakdown Bar
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cache Footprint",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = Formatter.formatShortFileSize(context, breakdown.totalManagedCacheBytes),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        StorageBreakdownBar(
                            streamBytes = breakdown.exploreStreamCacheBytes,
                            imageBytes = breakdown.imageCacheBytes,
                            tempBytes = breakdown.tempAudioCacheBytes,
                            lyricsBytes = breakdown.lyricsCacheBytes,
                        )

                        Button(
                            onClick = viewModel::clearAllSafeCaches,
                            enabled = !state.isClearing && breakdown.totalSafeCacheBytes > 0L,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Clear Safe Caches (${Formatter.formatShortFileSize(context, breakdown.totalSafeCacheBytes)})"
                            )
                        }
                    }
                }
            }

            // Explore Stream Cache Card
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CacheIconBadge(
                                icon = Icons.Rounded.Stream,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Explore Stream Cache",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${Formatter.formatShortFileSize(context, breakdown.exploreStreamCacheBytes)} • ${breakdown.exploreStreamCachedItems} cached streams",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(
                                onClick = viewModel::clearExploreStreamCache,
                                enabled = !state.isClearing &&
                                    (breakdown.exploreStreamCacheBytes > 0L || breakdown.exploreStreamCachedItems > 0),
                            ) {
                                Text("Clear")
                            }
                        }

                        Text(
                            text = "Stream cache size limit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                0 to "Off",
                                100 to "100 MB",
                                250 to "250 MB",
                                500 to "500 MB",
                                1024 to "1 GB",
                            ).forEach { (limitMb, label) ->
                                FilterChip(
                                    selected = state.streamCacheLimitMb == limitMb,
                                    onClick = { viewModel.setStreamCacheLimitMb(limitMb) },
                                    label = { Text(label) },
                                )
                            }
                        }

                        Text(
                            text = "Auto-clean streams older than",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                0 to "Never",
                                7 to "7 days",
                                14 to "14 days",
                                30 to "30 days",
                            ).forEach { (days, label) ->
                                FilterChip(
                                    selected = state.autoCleanDays == days,
                                    onClick = { viewModel.setAutoCleanDays(days) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }

            // Individual App Cache Cards
            item {
                CacheCategoryCard(
                    icon = Icons.Rounded.Image,
                    tint = MaterialTheme.colorScheme.secondary,
                    title = "Album Art & Image Cache",
                    subtitle = "${Formatter.formatShortFileSize(context, breakdown.imageCacheBytes)} used (limit ${state.albumArtCacheLimitMb} MB)",
                    clearEnabled = !state.isClearing && breakdown.imageCacheBytes > 0L,
                    onClear = viewModel::clearImageCache,
                )
            }

            item {
                CacheCategoryCard(
                    icon = Icons.Rounded.AudioFile,
                    tint = MaterialTheme.colorScheme.tertiary,
                    title = "Temp Audio & Export Cache",
                    subtitle = "${Formatter.formatShortFileSize(context, breakdown.tempAudioCacheBytes)} temporary files",
                    clearEnabled = !state.isClearing && breakdown.tempAudioCacheBytes > 0L,
                    onClear = viewModel::clearTempAudioCache,
                )
            }

            item {
                CacheCategoryCard(
                    icon = Icons.Rounded.Lyrics,
                    tint = MaterialTheme.colorScheme.secondary,
                    title = "Cached Lyrics Files",
                    subtitle = "${Formatter.formatShortFileSize(context, breakdown.lyricsCacheBytes)} (${breakdown.lyricsCacheCount} cached tracks)",
                    clearEnabled = !state.isClearing && (breakdown.lyricsCacheBytes > 0L || breakdown.lyricsCacheCount > 0),
                    onClear = { showConfirmLyricsDialog = true },
                )
            }

            // Explore Downloads summary (safe informational card)
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CacheIconBadge(
                            icon = Icons.Rounded.DownloadDone,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Explore Offline Downloads",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${breakdown.exploreDownloadsCount} saved tracks (${Formatter.formatShortFileSize(context, breakdown.exploreDownloadsBytes)}) — preserved when clearing caches",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onOpenExploreDownloads) {
                            Text("Manage")
                        }
                    }
                }
            }
        }
    }

    if (showConfirmLyricsDialog) {
        MusicLiDialog(
            onDismissRequest = { showConfirmLyricsDialog = false },
            icon = Icons.Rounded.DeleteSweep,
            isDestructive = true,
            title = "Clear Cached Lyrics?",
            confirmText = "Clear",
            onConfirm = {
                showConfirmLyricsDialog = false
                viewModel.clearLyricsCache()
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showConfirmLyricsDialog = false }
        ) {
            Text(
                text = "This clears locally cached and manually downloaded lyrics files from Storage Sense. Embedded audio file tags and .lrc sidecar files are kept.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageBreakdownBar(
    streamBytes: Long,
    imageBytes: Long,
    tempBytes: Long,
    lyricsBytes: Long,
) {
    val total = (streamBytes + imageBytes + tempBytes + lyricsBytes).coerceAtLeast(1L)
    val segments = listOf(
        streamBytes to MaterialTheme.colorScheme.primary,
        imageBytes to MaterialTheme.colorScheme.secondary,
        tempBytes to MaterialTheme.colorScheme.tertiary,
        lyricsBytes to MaterialTheme.colorScheme.outline,
    ).filter { it.first > 0L }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (segments.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                segments.forEach { (bytes, color) ->
                    val fraction = (bytes.toFloat() / total.toFloat()).coerceAtLeast(0.03f)
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheCategoryCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    clearEnabled: Boolean,
    onClear: () -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CacheIconBadge(icon = icon, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onClear,
                enabled = clearEnabled,
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun CacheIconBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
