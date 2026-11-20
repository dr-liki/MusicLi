package com.lostf1sh.pixelplayeross.presentation.video

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lostf1sh.pixelplayeross.data.video.ChunkedRangeDataSource
import com.lostf1sh.pixelplayeross.data.video.SyncedVideoRepository
import com.lostf1sh.pixelplayeross.data.video.SyncedVideoStreamBundle
import com.lostf1sh.pixelplayeross.data.video.VideoSearchCandidate
import com.lostf1sh.pixelplayeross.data.video.YouTubeVideoStream
import com.lostf1sh.pixelplayeross.data.video.extractYoutubeVideoId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.math.abs

private const val TAG = "SyncedVideoViewModel"
private const val MAX_LINK_REFRESHES_PER_SONG = 2

sealed interface SyncedVideoUiState {
    data object Loading : SyncedVideoUiState
    data class Ready(
        val bundle: SyncedVideoStreamBundle,
        val activeStream: YouTubeVideoStream,
        val selectedHeight: Int? = null,
    ) : SyncedVideoUiState {
        val stream: YouTubeVideoStream get() = activeStream
        val isAuto: Boolean get() = selectedHeight == null || selectedHeight <= 0
        val currentQualityLabel: String
            get() = if (isAuto) "Auto" else activeStream.displayQuality
    }
    data class Error(val message: String, val noMatchingVideo: Boolean = false) : SyncedVideoUiState
}

sealed interface VideoPickerUiState {
    data object Hidden : VideoPickerUiState
    data object Loading : VideoPickerUiState
    data class Results(val candidates: List<VideoSearchCandidate>, val savedVideoId: String?) : VideoPickerUiState
    data class Error(val message: String) : VideoPickerUiState
}

@HiltViewModel
class SyncedVideoViewModel @Inject constructor(
    private val videoRepository: SyncedVideoRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncedVideoUiState>(SyncedVideoUiState.Loading)
    val uiState: StateFlow<SyncedVideoUiState> = _uiState.asStateFlow()

    private val _isVideoActive = MutableStateFlow(false)
    val isVideoActive: StateFlow<Boolean> = _isVideoActive.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _videoPickerState = MutableStateFlow<VideoPickerUiState>(VideoPickerUiState.Hidden)
    val videoPickerState: StateFlow<VideoPickerUiState> = _videoPickerState.asStateFlow()

    private var currentKey: String? = null
    private var loadJob: Job? = null
    private var videoSearchJob: Job? = null
    private var linkRefreshesLeft = 0

    private data class VideoPickerRequest(
        val title: String,
        val artist: String,
        val durationMs: Long?,
    )
    private var videoPickerRequest: VideoPickerRequest? = null

    private var _playerInitialized = false

    @OptIn(UnstableApi::class)
    val player: ExoPlayer by lazy {
        _playerInitialized = true
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val defaultDataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val chunkedFactory = DataSource.Factory {
            ChunkedRangeDataSource(defaultDataSourceFactory.createDataSource(), chunkSize = 256 * 1024L)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .build()

        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(chunkedFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.w(TAG, "Video ExoPlayer error: ${error.message}")
                        onVideoPlaybackError()
                    }
                })
            }
    }

    fun setVideoActive(active: Boolean) {
        _isVideoActive.value = active
        if (!active) {
            _isFullscreen.value = false
            if (_playerInitialized) {
                player.pause()
            }
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
    }

    fun toggleVideo() {
        setVideoActive(!_isVideoActive.value)
    }

    fun loadVideo(title: String, artist: String, durationMs: Long? = null) {
        val selectedVideoId = videoRepository.savedVideoOverride(title, artist)
        val key = "$title|$artist|$durationMs|$selectedVideoId"
        if (currentKey == key && (_uiState.value is SyncedVideoUiState.Ready || loadJob?.isActive == true)) return

        loadJob?.cancel()
        if (currentKey != null && currentKey != key) {
            if (_playerInitialized) {
                player.stop()
                player.clearMediaItems()
            }
        }
        currentKey = key
        linkRefreshesLeft = MAX_LINK_REFRESHES_PER_SONG
        resolveStream(title, artist, durationMs, selectedVideoId)
    }

    private fun resolveStream(
        title: String,
        artist: String,
        durationMs: Long?,
        selectedVideoId: String?,
    ) {
        loadJob = viewModelScope.launch {
            _uiState.value = SyncedVideoUiState.Loading
            videoRepository.resolveVideo(
                trackTitle = title,
                trackArtist = artist,
                expectedDurationMs = durationMs,
                selectedVideoId = selectedVideoId,
            )
                .onSuccess { bundle ->
                    _uiState.value = SyncedVideoUiState.Ready(
                        bundle = bundle,
                        activeStream = bundle.defaultStream,
                        selectedHeight = null,
                    )
                    prepareMediaItem(bundle.defaultStream.url)
                }
                .onFailure { error ->
                    _uiState.value = SyncedVideoUiState.Error(
                        message = error.message ?: "Unable to resolve video stream",
                        noMatchingVideo = error.message?.startsWith("No verified music video found") == true,
                    )
                }
        }
    }

    private fun prepareMediaItem(url: String) {
        if (_playerInitialized || _isVideoActive.value) {
            val mediaItem = MediaItem.Builder().setUri(url).build()
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    fun selectQuality(height: Int?) {
        val current = _uiState.value as? SyncedVideoUiState.Ready ?: return
        val targetStream = if (height == null || height <= 0) {
            current.bundle.defaultStream
        } else {
            current.bundle.availableStreams.firstOrNull { (it.height ?: 0) == height }
                ?: current.bundle.availableStreams.minByOrNull { abs((it.height ?: 0) - height) }
                ?: current.bundle.defaultStream
        }
        _uiState.value = current.copy(
            activeStream = targetStream,
            selectedHeight = height,
        )
        if (_playerInitialized) {
            val currentPos = player.currentPosition
            val isPlaying = player.isPlaying
            val mediaItem = MediaItem.Builder().setUri(targetStream.url).build()
            player.setMediaItem(mediaItem)
            player.seekTo(currentPos)
            player.prepare()
            if (isPlaying) player.play()
        }
    }

    fun onVideoPlaybackError() {
        val ready = _uiState.value as? SyncedVideoUiState.Ready ?: return
        if (ready.selectedHeight != null) {
            selectQuality(null)
            return
        }
        videoRepository.invalidate(ready.bundle.videoId)
        if (_playerInitialized) {
            player.stop()
            player.clearMediaItems()
        }
        if (linkRefreshesLeft > 0) {
            linkRefreshesLeft--
            currentKey = null
            ready.bundle.let {
                // reload current song
            }
        } else {
            _uiState.value = SyncedVideoUiState.Error("Video stream unavailable")
        }
    }

    fun openVideoPicker(title: String, artist: String, durationMs: Long?) {
        videoSearchJob?.cancel()
        val request = VideoPickerRequest(title, artist, durationMs)
        videoPickerRequest = request
        _videoPickerState.value = VideoPickerUiState.Loading

        videoSearchJob = viewModelScope.launch {
            val candidates = runCatching {
                videoRepository.searchVideoCandidates(title, artist, durationMs)
            }.getOrElse { error ->
                if (videoPickerRequest == request) {
                    _videoPickerState.value = VideoPickerUiState.Error(error.message ?: "Could not search videos")
                }
                return@launch
            }
            if (videoPickerRequest != request) return@launch
            _videoPickerState.value = VideoPickerUiState.Results(
                candidates = candidates,
                savedVideoId = videoRepository.savedVideoOverride(title, artist),
            )
        }
    }

    fun dismissVideoPicker() {
        videoSearchJob?.cancel()
        videoSearchJob = null
        _videoPickerState.value = VideoPickerUiState.Hidden
        videoPickerRequest = null
    }

    fun selectVideo(candidate: VideoSearchCandidate) {
        val request = videoPickerRequest ?: return
        videoRepository.saveVideoOverride(request.title, request.artist, candidate.videoId)
        dismissVideoPicker()
        currentKey = null
        loadVideo(request.title, request.artist, request.durationMs)
    }

    fun selectVideoUrl(input: String): Boolean {
        val videoId = extractYoutubeVideoId(input) ?: return false
        val request = videoPickerRequest ?: return false
        videoRepository.saveVideoOverride(request.title, request.artist, videoId)
        dismissVideoPicker()
        currentKey = null
        loadVideo(request.title, request.artist, request.durationMs)
        return true
    }

    fun clearVideoOverride() {
        val request = videoPickerRequest ?: return
        videoRepository.clearVideoOverride(request.title, request.artist)
        dismissVideoPicker()
        currentKey = null
        loadVideo(request.title, request.artist, request.durationMs)
    }

    @OptIn(UnstableApi::class)
    override fun onCleared() {
        super.onCleared()
        if (_playerInitialized) {
            player.release()
        }
    }
}
