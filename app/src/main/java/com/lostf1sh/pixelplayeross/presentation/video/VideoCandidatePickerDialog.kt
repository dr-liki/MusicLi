package com.lostf1sh.pixelplayeross.presentation.video

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.R
import java.util.Locale

@Composable
fun VideoCandidatePickerDialog(
    viewModel: SyncedVideoViewModel,
    trackTitle: String,
    trackArtist: String,
) {
    val state by viewModel.videoPickerState.collectAsStateWithLifecycle()
    val defaultSearchQuery by viewModel.currentSearchQuery.collectAsStateWithLifecycle()
    var videoLink by remember(trackTitle, trackArtist) { mutableStateOf("") }
    var searchQuery by remember(defaultSearchQuery) { mutableStateOf(defaultSearchQuery) }

    when (val picker = state) {
        VideoPickerUiState.Hidden -> Unit
        VideoPickerUiState.Loading -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissVideoPicker,
                title = { Text(stringResource(R.string.video_choose_video)) },
                text = {
                    Column {
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
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.video_searching, trackTitle))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissVideoPicker) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        is VideoPickerUiState.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissVideoPicker,
                title = { Text(stringResource(R.string.video_search_failed)) },
                text = {
                    Column {
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
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(picker.message, color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissVideoPicker) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )
        }
        is VideoPickerUiState.Results -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissVideoPicker,
                title = { Text(stringResource(R.string.video_choose_video)) },
                text = {
                    Column {
                        Text(
                            text = "Pick the correct video for $trackTitle. Your selection will be remembered for this song.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
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
                        )
                        Spacer(Modifier.height(8.dp))
                        if (picker.candidates.isEmpty()) {
                            Text(
                                stringResource(R.string.video_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(280.dp)) {
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
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(
                                                candidate.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                buildString {
                                                    append(candidate.channel)
                                                    candidate.durationSeconds?.let {
                                                        append(" • ").append(formatSeconds(it))
                                                    }
                                                    append(" • ").append(stringResource(R.string.video_candidate_likely_match))
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissVideoPicker) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
                dismissButton = {
                    if (picker.savedVideoId != null) {
                        TextButton(onClick = viewModel::clearVideoOverride) {
                            Text("Reset")
                        }
                    }
                }
            )
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
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
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(8.dp))
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

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
