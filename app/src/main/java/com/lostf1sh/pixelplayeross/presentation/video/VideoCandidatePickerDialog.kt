package com.lostf1sh.pixelplayeross.presentation.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lostf1sh.pixelplayeross.R
import java.util.Locale

@Composable
fun VideoCandidatePickerDialog(
    viewModel: SyncedVideoViewModel,
    trackTitle: String,
    trackArtist: String,
    audioDurationMs: Long = 0L,
) {
    val state by viewModel.videoPickerState.collectAsStateWithLifecycle()
    val defaultSearchQuery by viewModel.currentSearchQuery.collectAsStateWithLifecycle()
    var videoLink by remember(trackTitle, trackArtist) { mutableStateOf("") }
    var searchQuery by remember(defaultSearchQuery) { mutableStateOf(defaultSearchQuery) }

    if (state == VideoPickerUiState.Hidden) return

    Dialog(
        onDismissRequest = viewModel::dismissVideoPicker,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                when (val picker = state) {
                    VideoPickerUiState.Hidden -> Unit
                    VideoPickerUiState.Loading -> {
                        Text(
                            text = stringResource(R.string.video_choose_video),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        VideoLinkInput(
                            videoLink = videoLink,
                            onVideoLinkChange = { videoLink = it },
                            onUseLink = { viewModel.selectVideoUrl(videoLink) },
                        )
                        Spacer(Modifier.height(8.dp))
                        VideoSearchInput(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = { viewModel.searchCustomQuery(searchQuery) },
                            audioDurationMs = audioDurationMs,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.video_searching, trackTitle))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = viewModel::dismissVideoPicker) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    }
                    is VideoPickerUiState.Error -> {
                        Text(
                            text = stringResource(R.string.video_search_failed),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        VideoLinkInput(
                            videoLink = videoLink,
                            onVideoLinkChange = { videoLink = it },
                            onUseLink = { viewModel.selectVideoUrl(videoLink) },
                        )
                        Spacer(Modifier.height(8.dp))
                        VideoSearchInput(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = { viewModel.searchCustomQuery(searchQuery) },
                            audioDurationMs = audioDurationMs,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(picker.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = viewModel::dismissVideoPicker) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    }
                    is VideoPickerUiState.Results -> {
                        Text(
                            text = stringResource(R.string.video_choose_video),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Pick the correct video for $trackTitle. Your selection will be remembered for this song.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        VideoLinkInput(
                            videoLink = videoLink,
                            onVideoLinkChange = { videoLink = it },
                            onUseLink = { viewModel.selectVideoUrl(videoLink) },
                        )
                        Spacer(Modifier.height(8.dp))
                        VideoSearchInput(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = { viewModel.searchCustomQuery(searchQuery) },
                            audioDurationMs = audioDurationMs,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (picker.candidates.isEmpty()) {
                            Text(
                                stringResource(R.string.video_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(picker.candidates, key = { it.videoId }) { candidate ->
                                    val isSelected = candidate.videoId == picker.savedVideoId
                                    Surface(
                                        onClick = { viewModel.selectVideo(candidate) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AsyncImage(
                                                model = candidate.thumbnailUrl
                                                    ?: "https://i.ytimg.com/vi/${candidate.videoId}/hqdefault.jpg",
                                                contentDescription = candidate.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .width(96.dp)
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = candidate.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    text = buildString {
                                                        if (isSelected) {
                                                            append(stringResource(R.string.video_candidate_saved_badge))
                                                            append(" • ")
                                                        }
                                                        candidate.durationSeconds?.let {
                                                            append(formatSeconds(it)).append(" • ")
                                                        }
                                                        append(candidate.channel)
                                                        append(" • ").append(stringResource(R.string.video_candidate_likely_match))
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (picker.savedVideoId != null) {
                                TextButton(onClick = viewModel::clearVideoOverride) {
                                    Text("Reset")
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            TextButton(onClick = viewModel::dismissVideoPicker) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoLinkInput(
    videoLink: String,
    onVideoLinkChange: (String) -> Unit,
    onUseLink: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = videoLink,
            onValueChange = onVideoLinkChange,
            placeholder = { Text(stringResource(R.string.video_link_placeholder), maxLines = 1) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onUseLink,
            enabled = videoLink.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(stringResource(R.string.video_use_link))
        }
    }
}

@Composable
internal fun VideoSearchInput(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    audioDurationMs: Long = 0L,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.video_search_query_placeholder), maxLines = 1) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (audioDurationMs > 0L) {
                Text(
                    text = stringResource(
                        R.string.video_song_duration_label,
                        formatSeconds((audioDurationMs / 1000L).toInt()),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }
            FilledTonalButton(
                onClick = onSearch,
                enabled = searchQuery.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(stringResource(R.string.video_search_button))
            }
        }
    }
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
