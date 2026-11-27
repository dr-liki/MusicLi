package com.drliki.MusicLi.presentation.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import com.drliki.MusicLi.presentation.components.MusicLiDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drliki.MusicLi.R
import com.drliki.MusicLi.data.explore.ExploreDownloadItem
import com.drliki.MusicLi.data.explore.ExploreDownloadStatus
import com.drliki.MusicLi.presentation.components.MiniPlayerHeight
import com.drliki.MusicLi.presentation.viewmodel.ExploreViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreDownloadsScreen(
    onBack: () -> Unit,
    onOpenStorageSense: () -> Unit = {},
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.downloadsUiState.collectAsStateWithLifecycle()
    val optionsState by viewModel.optionsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showApiUrlDialog by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            viewModel.setDownloadSafUri(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.explore_downloads_title),
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
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.completed.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearCompletedDownloads) {
                            Text("Clear completed")
                        }
                    }
                    IconButton(onClick = { showOptionsSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Explore options",
                        )
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
                bottom = MiniPlayerHeight + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Formatter.formatShortFileSize(context, state.totalBytes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${state.completed.size} completed · ${state.totalCount} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.totalCount == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "No Explore downloads yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Downloaded tracks with metadata and .lrc lyrics will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.active.isNotEmpty()) {
                item {
                    Text(
                        text = "Active (${state.active.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(state.active, key = { it.downloadId }) { item ->
                    ExploreDownloadRowCard(
                        item = item,
                        onCancelOrRemove = { viewModel.cancelDownload(item.downloadId) },
                    )
                }
            }

            if (state.failed.isNotEmpty()) {
                item {
                    Text(
                        text = "Failed (${state.failed.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(state.failed, key = { it.downloadId }) { item ->
                    ExploreDownloadRowCard(
                        item = item,
                        onRetry = { viewModel.retryDownload(item.downloadId) },
                        onCancelOrRemove = { viewModel.removeDownload(item.downloadId) },
                    )
                }
            }

            if (state.completed.isNotEmpty()) {
                item {
                    Text(
                        text = "Completed (${state.completed.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.completed, key = { it.downloadId }) { item ->
                    ExploreDownloadRowCard(
                        item = item,
                        onCancelOrRemove = { viewModel.removeDownload(item.downloadId) },
                    )
                }
            }
        }
    }

    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.setcat_explore_downloads_section),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = stringResource(R.string.setcat_explore_stream_quality_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(320, 160, 96).forEach { kbps ->
                        FilterChip(
                            selected = optionsState.streamQualityKbps == kbps,
                            onClick = { viewModel.setStreamQualityKbps(kbps) },
                            label = { Text("$kbps kbps") },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.setcat_explore_quality_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(320, 160, 96).forEach { kbps ->
                        FilterChip(
                            selected = optionsState.downloadQualityKbps == kbps,
                            onClick = { viewModel.setDownloadQualityKbps(kbps) },
                            label = { Text("$kbps kbps") },
                        )
                    }
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.setcat_explore_folder_title)) },
                    supportingContent = {
                        Text(
                            optionsState.downloadSafUri?.let { Uri.parse(it).lastPathSegment ?: it }
                                ?: stringResource(R.string.setcat_explore_folder_default),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.FolderOpen, null) },
                    trailingContent = {
                        if (optionsState.downloadSafUri != null) {
                            TextButton(onClick = { viewModel.setDownloadSafUri(null) }) {
                                Text(stringResource(R.string.setcat_explore_folder_reset))
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { folderLauncher.launch(null) },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.setcat_explore_subfolders_title)) },
                    supportingContent = { Text(stringResource(R.string.setcat_explore_subfolders_subtitle)) },
                    leadingContent = { Icon(Icons.Rounded.HighQuality, null) },
                    trailingContent = {
                        Switch(
                            checked = optionsState.downloadSubfolders,
                            onCheckedChange = viewModel::setDownloadSubfolders,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.setDownloadSubfolders(!optionsState.downloadSubfolders) },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.setcat_explore_lyrics_title)) },
                    supportingContent = { Text(stringResource(R.string.setcat_explore_lyrics_subtitle)) },
                    leadingContent = { Icon(Icons.Rounded.Lyrics, null) },
                    trailingContent = {
                        Switch(
                            checked = optionsState.downloadLyrics,
                            onCheckedChange = viewModel::setDownloadLyrics,
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.setDownloadLyrics(!optionsState.downloadLyrics) },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.setcat_explore_api_url_title)) },
                    supportingContent = {
                        Text(
                            optionsState.apiBaseUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Public, null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showApiUrlDialog = true },
                )

                ListItem(
                    headlineContent = { Text("Storage Sense") },
                    supportingContent = { Text("Manage Explore stream cache & app storage") },
                    leadingContent = { Icon(Icons.Rounded.Storage, null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showOptionsSheet = false
                            onOpenStorageSense()
                        },
                )
            }
        }
    }

    if (showApiUrlDialog) {
        var draftUrl by remember(optionsState.apiBaseUrl) {
            mutableStateOf(optionsState.apiBaseUrl)
        }
        MusicLiDialog(
            onDismissRequest = { showApiUrlDialog = false },
            icon = Icons.Rounded.Public,
            title = stringResource(R.string.setcat_explore_api_url_dialog_title),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                viewModel.setApiBaseUrl(draftUrl)
                showApiUrlDialog = false
            },
            dismissText = "Reset",
            onDismiss = {
                viewModel.setApiBaseUrl(null)
                showApiUrlDialog = false
            }
        ) {
            OutlinedTextField(
                value = draftUrl,
                onValueChange = { draftUrl = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExploreDownloadRowCard(
    item: ExploreDownloadItem,
    onRetry: (() -> Unit)? = null,
    onCancelOrRemove: () -> Unit,
) {
    val context = LocalContext.current
    val statusIcon: ImageVector = when (item.status) {
        ExploreDownloadStatus.QUEUED -> Icons.Rounded.HourglassTop
        ExploreDownloadStatus.DOWNLOADING -> Icons.Rounded.HourglassTop
        ExploreDownloadStatus.COMPLETED -> Icons.Rounded.CheckCircle
        ExploreDownloadStatus.FAILED -> Icons.Rounded.Error
    }
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.song.thumbnailUrl ?: item.song.artworkUrl,
                    contentDescription = item.song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(item.song.artist)
                            append(" • ")
                            append(item.statusMessage)
                            if (item.status == ExploreDownloadStatus.COMPLETED) {
                                if (item.lrcSaved) append(" • +LRC")
                                if (item.bytesWritten > 0L) {
                                    append(" • ").append(Formatter.formatShortFileSize(context, item.bytesWritten))
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == ExploreDownloadStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onRetry != null) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                    }
                }
                IconButton(onClick = onCancelOrRemove) {
                    Icon(
                        imageVector = if (item.status == ExploreDownloadStatus.DOWNLOADING || item.status == ExploreDownloadStatus.QUEUED) {
                            Icons.Rounded.Close
                        } else {
                            Icons.Rounded.Delete
                        },
                        contentDescription = "Remove",
                    )
                }
            }
            if (item.status == ExploreDownloadStatus.DOWNLOADING) {
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
