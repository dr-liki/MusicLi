package com.lostf1sh.pixelplayeross.presentation.video

import android.os.SystemClock
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackParameters
import com.lostf1sh.pixelplayeross.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun SyncedCoverVideoPlayer(
    viewModel: SyncedVideoViewModel,
    trackTitle: String,
    trackArtist: String,
    genre: String? = null,
    currentPositionProvider: () -> Long,
    audioDurationMs: Long,
    isPlayingProvider: () -> Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showControls by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastSeekTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = uiState) {
            is SyncedVideoUiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.padding(top = 12.dp))
                    Text(
                        text = "Loading official video…",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
            }
            is SyncedVideoUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = state.message,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.padding(top = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.loadVideo(trackTitle, trackArtist, genre, audioDurationMs, initialPositionMs = currentPositionProvider()) },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Retry")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.openVideoPicker(trackTitle, trackArtist, genre, audioDurationMs) },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Choose Video")
                        }
                    }
                }
            }
            is SyncedVideoUiState.Ready -> {
                val player = viewModel.player
                val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()

                // High-frame-rate SurfaceView
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            player.setVideoSurfaceView(this)
                        }
                    },
                    update = { surfaceView ->
                        if (!isFullscreen) {
                            player.setVideoSurfaceView(surfaceView)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                DisposableEffect(Unit) {
                    onDispose {
                        if (!viewModel.isFullscreen.value) {
                            player.clearVideoSurfaceView(null)
                        }
                    }
                }

                // Phase-Locked Loop (PLL) Drift Correction Loop (50ms tick)
                LaunchedEffect(state.bundle.videoId) {
                    while (isActive) {
                        val introOffset = state.bundle.introOffsetMs
                        val audioPositionMs = currentPositionProvider()
                        val isPlaying = isPlayingProvider()
                        val targetVideoPos = (audioPositionMs + introOffset).coerceAtLeast(0L)
                        val currentVideoPos = player.currentPosition
                        val driftMs = currentVideoPos - targetVideoPos
                        val absDrift = abs(driftMs)
                        val now = SystemClock.elapsedRealtime()

                        if (isPlaying) {
                            if (!player.playWhenReady) {
                                player.play()
                            }
                            if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                                player.prepare()
                            }
                        } else {
                            if (player.playWhenReady) {
                                player.pause()
                            }
                        }

                        val isBuffering = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                        if (!isBuffering && absDrift > 600L && now - lastSeekTimeMs > 1000L) {
                            // Hard drift: seek directly to target position
                            lastSeekTimeMs = now
                            player.seekTo(targetVideoPos)
                            player.playbackParameters = PlaybackParameters(1.0f)
                        } else if (!isBuffering && absDrift <= 600L) {
                            when {
                                driftMs > 200L -> player.playbackParameters = PlaybackParameters(0.85f)
                                driftMs < -200L -> player.playbackParameters = PlaybackParameters(1.15f)
                                driftMs > 60L -> player.playbackParameters = PlaybackParameters(0.95f)
                                driftMs < -60L -> player.playbackParameters = PlaybackParameters(1.05f)
                                else -> {
                                    if (player.playbackParameters.speed != 1.0f) {
                                        player.playbackParameters = PlaybackParameters(1.0f)
                                    }
                                }
                            }
                        }
                        delay(50)
                    }
                }

                // Tap detector and control overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTapTimeMs < 300L) {
                                // Double tap handled
                            } else {
                                showControls = !showControls
                            }
                            lastTapTimeMs = now
                        }
                )

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(12.dp),
                    ) {
                        // Top Bar: Quality, Choose Video, Fullscreen, Close
                        Row(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Quality Badge Pill
                            Surface(
                                onClick = { showQualityDialog = true },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.HighQuality,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = state.currentQualityLabel,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            // "Refresh Link" Button
                            IconButton(
                                onClick = { viewModel.refreshCurrentVideoLink() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.video_refresh_link),
                                    tint = Color.White,
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // "Choose Video" Candidate Picker Icon Button (Option 3)
                            IconButton(
                                onClick = { viewModel.openVideoPicker(trackTitle, trackArtist, genre, audioDurationMs) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.video_choose_video),
                                    tint = Color.White,
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // Fullscreen Expand Button
                            IconButton(
                                onClick = { viewModel.setFullscreen(true) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // Close Video Button (Returns to Album Cover)
                            IconButton(
                                onClick = { viewModel.setVideoActive(false) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close Video",
                                    tint = Color.White,
                                )
                            }
                        }

                        // Center Controls: Replay 10s, Forward 10s
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    val newPos = (currentPositionProvider() - 10_000L).coerceAtLeast(0L)
                                    onSeekTo(newPos)
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Replay 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            IconButton(
                                onClick = {
                                    val newPos = (currentPositionProvider() + 10_000L).coerceAtMost(audioDurationMs)
                                    onSeekTo(newPos)
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }

                if (showQualityDialog) {
                    VideoQualityDialog(
                        bundle = state.bundle,
                        selectedHeight = state.selectedHeight,
                        onSelectQuality = { viewModel.selectQuality(it) },
                        onDismiss = { showQualityDialog = false },
                    )
                }
            }
        }
    }

    // Video Candidate Picker Dialog (Option 3)
    VideoCandidatePickerDialog(
        viewModel = viewModel,
        trackTitle = trackTitle,
        trackArtist = trackArtist,
    )
}
