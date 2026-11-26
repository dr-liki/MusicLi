package com.drliki.MusicLi.presentation.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.drliki.MusicLi.R
import com.drliki.MusicLi.data.lyrics.bulk.BulkLyricsEmbedStatus
import com.drliki.MusicLi.data.lyrics.bulk.BulkLyricsEmbedUiState
import com.drliki.MusicLi.data.lyrics.bulk.BulkLyricsLogEntry
import com.drliki.MusicLi.data.lyrics.bulk.BulkLyricsLogOutcome
import com.drliki.MusicLi.presentation.components.CollapsibleCommonTopBar
import com.drliki.MusicLi.presentation.navigation.Screen
import com.drliki.MusicLi.presentation.viewmodel.BulkLyricsEmbedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkLyricsEmbedScreen(
    navController: NavController,
    viewModel: BulkLyricsEmbedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val eligibleLibraryCount by viewModel.eligibleLibraryCount.collectAsStateWithLifecycle()
    val pendingPermissionBatch by viewModel.pendingPermissionBatch.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onPermissionBatchResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pendingPermissionBatch) {
        val batch = pendingPermissionBatch ?: return@LaunchedEffect
        val request = IntentSenderRequest.Builder(batch.intentSender).build()
        permissionLauncher.launch(request)
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.status, uiState.resumeAtEpochMs) {
        if (uiState.status == BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT && uiState.resumeAtEpochMs != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val maxTopBarHeight = 128.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val topBarHeight = remember(maxTopBarHeightPx) { Animatable(maxTopBarHeightPx) }
    val collapseFraction = if (maxTopBarHeightPx > minTopBarHeightPx) {
        1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    } else {
        0f
    }

    val nestedScrollConnection = remember(maxTopBarHeightPx, minTopBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta >= 0f) return Offset.Zero
                val previous = topBarHeight.value
                val next = (previous + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = next - previous
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(next) }
                }
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                val previous = topBarHeight.value
                val next = (previous + available.y).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumedDelta = next - previous
                if (consumedDelta.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(next) }
                }
                return Offset(0f, consumedDelta)
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val canExpand = lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
            val expand = canExpand && topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2f
            topBarHeight.animateTo(
                targetValue = if (expand) maxTopBarHeightPx else minTopBarHeightPx,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        val currentTopBarHeight = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeight + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "priority_sequence_card") {
                PrioritySequenceBanner(
                    uiState = uiState,
                    onEditPriority = {
                        navController.navigate(Screen.LyricsSettings.createRoute("item_lyrics_source_priority"))
                    },
                )
            }

            item(key = "progress_and_controls_card") {
                ProgressAndControlsCard(
                    uiState = uiState,
                    eligibleLibraryCount = eligibleLibraryCount,
                    nowMs = nowMs,
                    onStart = viewModel::requestStart,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStopReset = viewModel::stopAndReset,
                    onGrantPermission = viewModel::requestGrantPermissionsAndResume,
                    onRetryFailed = viewModel::retryFailed,
                )
            }

            item(key = "options_section") {
                BulkOptionsSection(
                    uiState = uiState,
                    onSyncedOnlyChange = viewModel::setSyncedOnly,
                    onSkipExistingSyncedChange = viewModel::setSkipExistingSynced,
                    onSaveLrcSidecarChange = viewModel::setSaveLrcSidecar,
                    onDelayChange = viewModel::setDelayBetweenSongsMs,
                )
            }

            if (uiState.logs.isNotEmpty()) {
                item(key = "logs_header") {
                    Text(
                        text = stringResource(R.string.bulk_lyrics_recent_activity),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }

                items(
                    items = uiState.logs,
                    key = { entry -> "${entry.songId}_${entry.timestampMs}_${entry.outcome.name}" },
                ) { entry ->
                    BulkLogRow(entry = entry)
                }
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.bulk_lyrics_screen_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeight,
            onBackClick = { navController.popBackStack() },
        )
    }
}

@Composable
private fun PrioritySequenceBanner(
    uiState: BulkLyricsEmbedUiState,
    onEditPriority: () -> Unit,
) {
    val activeProviders = uiState.priorityOrder.filter { it in uiState.enabledProviders }
    val chainText = if (activeProviders.isEmpty()) {
        stringResource(R.string.bulk_lyrics_no_providers_enabled)
    } else {
        activeProviders.mapIndexed { idx, provider -> "${idx + 1}. ${provider.displayName}" }
            .joinToString("  •  ")
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.bulk_lyrics_priority_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TextButton(
                    onClick = onEditPriority,
                    enabled = uiState.status != BulkLyricsEmbedStatus.RUNNING,
                ) {
                    Text(stringResource(R.string.bulk_lyrics_edit_priority))
                }
            }

            Text(
                text = chainText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Text(
                text = stringResource(R.string.bulk_lyrics_priority_card_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ProgressAndControlsCard(
    uiState: BulkLyricsEmbedUiState,
    eligibleLibraryCount: Int?,
    nowMs: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopReset: () -> Unit,
    onGrantPermission: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val statusTitle = when {
                uiState.isScanningLibrary -> stringResource(R.string.bulk_lyrics_status_scanning)
                uiState.status == BulkLyricsEmbedStatus.IDLE -> stringResource(R.string.bulk_lyrics_status_idle)
                uiState.status == BulkLyricsEmbedStatus.RUNNING -> stringResource(R.string.bulk_lyrics_status_running)
                uiState.status == BulkLyricsEmbedStatus.PAUSED_USER -> stringResource(R.string.bulk_lyrics_status_paused_user)
                uiState.status == BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT -> stringResource(R.string.bulk_lyrics_status_paused_rate_limit)
                uiState.status == BulkLyricsEmbedStatus.PAUSED_NETWORK -> stringResource(R.string.bulk_lyrics_status_paused_network)
                uiState.status == BulkLyricsEmbedStatus.NEEDS_PERMISSION -> stringResource(R.string.bulk_lyrics_status_needs_permission)
                uiState.status == BulkLyricsEmbedStatus.COMPLETED -> stringResource(R.string.bulk_lyrics_status_completed)
                else -> ""
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (uiState.status == BulkLyricsEmbedStatus.IDLE && eligibleLibraryCount != null) {
                        Text(
                            text = stringResource(R.string.bulk_lyrics_ready_songs_count, eligibleLibraryCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (uiState.totalCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.bulk_lyrics_done_left_summary,
                                uiState.processedCount,
                                uiState.totalCount,
                                uiState.remainingCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (uiState.isScanningLibrary || uiState.status == BulkLyricsEmbedStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                }
            }

            if (uiState.totalCount > 0) {
                LinearProgressIndicator(
                    progress = { uiState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCounterTile(
                        label = stringResource(R.string.bulk_lyrics_stat_done),
                        value = uiState.processedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCounterTile(
                        label = stringResource(R.string.bulk_lyrics_stat_left),
                        value = uiState.remainingCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCounterTile(
                        label = stringResource(R.string.bulk_lyrics_stat_embedded),
                        value = uiState.embeddedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCounterTile(
                        label = stringResource(R.string.bulk_lyrics_stat_skipped),
                        value = uiState.skippedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCounterTile(
                        label = stringResource(R.string.bulk_lyrics_stat_not_found),
                        value = uiState.notFoundCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCounterTile(
                        label = stringResource(R.string.bulk_lyrics_stat_failed),
                        value = uiState.failedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            uiState.currentSong?.let { current ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (current.artist.isNotBlank()) {
                            Text(
                                text = current.artist,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = current.statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (uiState.status == BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT) {
                val secondsLeft = ((uiState.resumeAtEpochMs ?: nowMs) - nowMs)
                    .coerceAtLeast(0L) / 1000L
                val providerText = uiState.rateLimitProviderLabel ?: "provider"
                StatusCalloutBanner(
                    icon = Icons.Rounded.Timer,
                    text = stringResource(
                        R.string.bulk_lyrics_rate_limit_countdown,
                        providerText,
                        secondsLeft.toInt(),
                    ),
                    isError = false,
                )
            } else if (uiState.status == BulkLyricsEmbedStatus.PAUSED_NETWORK) {
                StatusCalloutBanner(
                    icon = Icons.Outlined.CloudOff,
                    text = stringResource(R.string.bulk_lyrics_network_paused_banner),
                    isError = false,
                )
            } else {
                val errMsg = uiState.errorMessage
                if (!errMsg.isNullOrBlank()) {
                    StatusCalloutBanner(
                        icon = if (uiState.status == BulkLyricsEmbedStatus.NEEDS_PERMISSION) {
                            Icons.Outlined.Security
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        text = errMsg,
                        isError = uiState.status == BulkLyricsEmbedStatus.NEEDS_PERMISSION,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (uiState.status) {
                    BulkLyricsEmbedStatus.IDLE, BulkLyricsEmbedStatus.COMPLETED -> {
                        Button(
                            onClick = onStart,
                            enabled = !uiState.isScanningLibrary && uiState.enabledProviders.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                if (uiState.status == BulkLyricsEmbedStatus.COMPLETED) {
                                    stringResource(R.string.bulk_lyrics_run_again)
                                } else {
                                    stringResource(R.string.bulk_lyrics_start)
                                },
                            )
                        }
                        if (uiState.retryableFailedCount > 0) {
                            OutlinedButton(onClick = onRetryFailed) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(stringResource(R.string.bulk_lyrics_retry_failed, uiState.retryableFailedCount))
                            }
                        } else if (uiState.totalCount > 0) {
                            OutlinedButton(onClick = onStopReset) {
                                Text(stringResource(R.string.bulk_lyrics_reset))
                            }
                        }
                    }

                    BulkLyricsEmbedStatus.RUNNING -> {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Pause, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.bulk_lyrics_pause))
                        }
                        OutlinedButton(onClick = onStopReset) {
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.bulk_lyrics_stop))
                        }
                    }

                    BulkLyricsEmbedStatus.PAUSED_USER,
                    BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT,
                    BulkLyricsEmbedStatus.PAUSED_NETWORK,
                    -> {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.bulk_lyrics_resume))
                        }
                        OutlinedButton(onClick = onStopReset) {
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.bulk_lyrics_stop))
                        }
                    }

                    BulkLyricsEmbedStatus.NEEDS_PERMISSION -> {
                        Button(
                            onClick = onGrantPermission,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Security, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.bulk_lyrics_grant_permission))
                        }
                        OutlinedButton(onClick = onStopReset) {
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.bulk_lyrics_stop))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCounterTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusCalloutBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isError: Boolean,
) {
    val bgColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val fgColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = fgColor)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = fgColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkOptionsSection(
    uiState: BulkLyricsEmbedUiState,
    onSyncedOnlyChange: (Boolean) -> Unit,
    onSkipExistingSyncedChange: (Boolean) -> Unit,
    onSaveLrcSidecarChange: (Boolean) -> Unit,
    onDelayChange: (Long) -> Unit,
) {
    val editable = uiState.status != BulkLyricsEmbedStatus.RUNNING
    LyricsSettingsSubsection(
        title = stringResource(R.string.bulk_lyrics_options_section),
        addBottomSpace = false,
    ) {
        SwitchSettingItem(
            title = stringResource(R.string.bulk_lyrics_opt_synced_only_title),
            subtitle = stringResource(R.string.bulk_lyrics_opt_synced_only_desc),
            checked = uiState.options.syncedOnly,
            enabled = editable,
            onCheckedChange = onSyncedOnlyChange,
            leadingIcon = {
                Icon(Icons.Outlined.Lyrics, null, tint = MaterialTheme.colorScheme.secondary)
            },
        )
        SwitchSettingItem(
            title = stringResource(R.string.bulk_lyrics_opt_skip_existing_title),
            subtitle = stringResource(R.string.bulk_lyrics_opt_skip_existing_desc),
            checked = uiState.options.skipExistingSynced,
            enabled = editable,
            onCheckedChange = onSkipExistingSyncedChange,
            leadingIcon = {
                Icon(Icons.Outlined.SkipNext, null, tint = MaterialTheme.colorScheme.secondary)
            },
        )
        SwitchSettingItem(
            title = stringResource(R.string.bulk_lyrics_opt_sidecar_title),
            subtitle = stringResource(R.string.bulk_lyrics_opt_sidecar_desc),
            checked = uiState.options.saveLrcSidecar,
            enabled = editable,
            onCheckedChange = onSaveLrcSidecarChange,
            leadingIcon = {
                Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary)
            },
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.HourglassTop, null, tint = MaterialTheme.colorScheme.secondary)
                    Column {
                        Text(
                            text = stringResource(R.string.bulk_lyrics_opt_delay_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.bulk_lyrics_opt_delay_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500L to "0.5s", 1_000L to "1.0s", 2_000L to "2.0s").forEach { (ms, label) ->
                        FilterChip(
                            selected = uiState.options.delayBetweenSongsMs == ms,
                            enabled = editable,
                            onClick = { onDelayChange(ms) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkLogRow(entry: BulkLyricsLogEntry) {
    val (outcomeLabel, icon, tint) = when (entry.outcome) {
        BulkLyricsLogOutcome.EMBEDDED -> Triple(
            entry.providerLabel?.let { "Embedded ($it)" } ?: "Embedded",
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.primary,
        )
        BulkLyricsLogOutcome.SKIPPED_EXISTING_SYNCED -> Triple(
            "Skipped",
            Icons.Outlined.SkipNext,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BulkLyricsLogOutcome.NOT_FOUND -> Triple(
            "Not found",
            Icons.Outlined.Lyrics,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BulkLyricsLogOutcome.RATE_LIMITED -> Triple(
            entry.providerLabel?.let { "Rate limited ($it)" } ?: "Rate limited",
            Icons.Rounded.Timer,
            MaterialTheme.colorScheme.tertiary,
        )
        BulkLyricsLogOutcome.NETWORK_ERROR -> Triple(
            "Network paused",
            Icons.Outlined.CloudOff,
            MaterialTheme.colorScheme.tertiary,
        )
        BulkLyricsLogOutcome.PERMISSION_REQUIRED -> Triple(
            "Permission needed",
            Icons.Outlined.Security,
            MaterialTheme.colorScheme.error,
        )
        BulkLyricsLogOutcome.FAILED -> Triple(
            "Failed",
            Icons.Rounded.ErrorOutline,
            MaterialTheme.colorScheme.error,
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.songTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = listOfNotNull(
                    entry.artist.takeIf { it.isNotBlank() },
                    entry.detail?.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = outcomeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}
