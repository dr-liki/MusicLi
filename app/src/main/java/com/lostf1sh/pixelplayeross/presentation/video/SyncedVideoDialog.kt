package com.lostf1sh.pixelplayeross.presentation.video

import android.app.Activity
import android.content.ContextWrapper
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
fun SyncedVideoDialog(
    viewModel: SyncedVideoViewModel,
    trackTitle: String,
    trackArtist: String,
    currentPositionProvider: () -> Long,
    audioDurationMs: Long,
    isPlayingProvider: () -> Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    if (!isFullscreen) return

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    var showControls by remember { mutableStateOf(true) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableLongStateOf(currentPositionProvider()) }
    var isPlaying by remember { mutableStateOf(isPlayingProvider()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isSeeking) {
                currentPositionMs = currentPositionProvider()
            }
            isPlaying = isPlayingProvider()
            delay(200)
        }
    }

    LaunchedEffect(showControls, isSeeking) {
        if (showControls && !isSeeking) {
            delay(4500)
            showControls = false
        }
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
            ?: generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().firstOrNull()?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevBehavior = insetsController?.systemBarsBehavior
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (prevBehavior != null) insetsController.systemBarsBehavior = prevBehavior
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Dialog(
        onDismissRequest = { viewModel.setFullscreen(false) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler {
            viewModel.setFullscreen(false)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                is SyncedVideoUiState.Loading -> {
                    CircularProgressIndicator(color = Color.White)
                }
                is SyncedVideoUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(state.message, color = Color.White)
                        Spacer(Modifier.padding(top = 16.dp))
                        IconButton(onClick = { viewModel.setFullscreen(false) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
                is SyncedVideoUiState.Ready -> {
                    val player = viewModel.player

                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                player.setVideoSurfaceView(this)
                            }
                        },
                        update = { surfaceView ->
                            player.setVideoSurfaceView(surfaceView)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    DisposableEffect(Unit) {
                        onDispose {
                            player.clearVideoSurfaceView(null)
                        }
                    }

                    // Touch surface
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                showControls = !showControls
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
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(24.dp),
                        ) {
                            // Top Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackTitle,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = trackArtist,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                    )
                                }

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

                                Spacer(Modifier.width(8.dp))

                                IconButton(
                                    onClick = { viewModel.openVideoPicker(trackTitle, trackArtist, audioDurationMs) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.video_choose_video),
                                        tint = Color.White,
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                IconButton(onClick = { viewModel.setFullscreen(false) }) {
                                    Icon(
                                        imageVector = Icons.Filled.FullscreenExit,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White,
                                    )
                                }
                            }

                            // Center Play/Pause & Replay/Forward
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        val newPos = (currentPositionMs - 10_000L).coerceAtLeast(0L)
                                        currentPositionMs = newPos
                                        onSeekTo(newPos)
                                    },
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Replay10,
                                        contentDescription = "Replay 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        onPlayPause()
                                        isPlaying = !isPlaying
                                    },
                                    modifier = Modifier.size(72.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(56.dp),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val newPos = (currentPositionMs + 10_000L).coerceAtMost(audioDurationMs)
                                        currentPositionMs = newPos
                                        onSeekTo(newPos)
                                    },
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }

                            // Bottom Timeline & Duration
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                            ) {
                                val currentMs = if (isSeeking) seekPositionMs.toLong() else currentPositionMs
                                Slider(
                                    value = if (audioDurationMs > 0) currentMs.toFloat() / audioDurationMs else 0f,
                                    onValueChange = { frac ->
                                        isSeeking = true
                                        seekPositionMs = frac * audioDurationMs
                                    },
                                    onValueChangeFinished = {
                                        onSeekTo(seekPositionMs.toLong())
                                        currentPositionMs = seekPositionMs.toLong()
                                        isSeeking = false
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = formatTime(currentMs),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        text = formatTime(audioDurationMs),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
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
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
