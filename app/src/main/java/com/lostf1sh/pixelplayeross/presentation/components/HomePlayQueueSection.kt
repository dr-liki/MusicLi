package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.collections.immutable.ImmutableList

private val HomePlayQueueCardWidth = 132.dp
private val HomePlayQueueCardRadius = 18.dp
private val HomePlayQueueArtRadius = 14.dp

@Composable
fun HomePlayQueueSection(
    queue: ImmutableList<Song>,
    currentSongId: String?,
    onOpenQueueClick: () -> Unit,
    onArtworkClick: (song: Song, isCurrentSong: Boolean) -> Unit,
    onTextClick: (song: Song) -> Unit,
    onRemoveSong: (songId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) return

    val listState = rememberLazyListState()
    val activeIndex = remember(queue, currentSongId) {
        if (currentSongId == null) -1 else queue.indexOfFirst { it.id == currentSongId }
    }
    val itemKeys = remember(queue) {
        val counts = HashMap<String, Int>(queue.size)
        queue.map { song ->
            val count = counts.getOrDefault(song.id, 0)
            counts[song.id] = count + 1
            if (count == 0) song.id else "${song.id}#$count"
        }
    }

    LaunchedEffect(activeIndex, queue.size) {
        if (activeIndex >= 0 && activeIndex < queue.size) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_play_queue_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            FilledTonalButton(
                onClick = onOpenQueueClick,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.home_play_queue_open),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(
                items = queue,
                key = { index, _ -> itemKeys[index] },
                contentType = { _, _ -> "home_play_queue_card" },
            ) { index, song ->
                val isCurrent = if (activeIndex >= 0) index == activeIndex else song.id == currentSongId
                HomePlayQueueCard(
                    song = song,
                    isCurrentSong = isCurrent,
                    onArtworkClick = { onArtworkClick(song, isCurrent) },
                    onTextClick = { onTextClick(song) },
                    onRemoveClick = { onRemoveSong(song.id) },
                )
            }
        }
    }
}

@Composable
private fun HomePlayQueueCard(
    song: Song,
    isCurrentSong: Boolean,
    onArtworkClick: () -> Unit,
    onTextClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderStroke = if (isCurrentSong) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
    } else {
        null
    }

    Card(
        shape = RoundedCornerShape(HomePlayQueueCardRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.width(HomePlayQueueCardWidth),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top: Square Album Art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(HomePlayQueueArtRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onArtworkClick),
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    targetSize = SmartImageCompactListTargetSize,
                    modifier = Modifier.fillMaxSize(),
                )

                // Single icon at bottom-right: Play icon for currently playing song, Remove (X) for others
                if (isCurrentSong) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.home_play_queue_playing_cd),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = onRemoveClick,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.home_play_queue_remove_cd),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Bottom: Song name & Artist name (tapping opens Song Info / options sheet)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTextClick)
                    .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
