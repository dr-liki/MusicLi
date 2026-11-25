package com.lostf1sh.pixelplayeross.presentation.video

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
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
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.video.ANDROID_VR_USER_AGENT
import com.lostf1sh.pixelplayeross.data.video.ChunkedRangeDataSource
import com.lostf1sh.pixelplayeross.data.video.SyncedVideoRepository
import com.lostf1sh.pixelplayeross.data.video.SyncedVideoStreamBundle
import com.lostf1sh.pixelplayeross.data.video.VideoSearchCandidate
import com.lostf1sh.pixelplayeross.data.video.YOUTUBE_ORIGIN
import com.lostf1sh.pixelplayeross.data.video.YouTubeVideoStream
import com.lostf1sh.pixelplayeross.data.video.extractYoutubeVideoId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val isRefreshing: Boolean = false,
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
    private val userPreferencesRepository: UserPreferencesRepository,
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

    private val _currentSearchQuery = MutableStateFlow("")
    val currentSearchQuery: StateFlow<String> = _currentSearchQuery.asStateFlow()

    private var currentKey: String? = null
    private var loadJob: Job? = null
    private var videoSearchJob: Job? = null
    private var linkRefreshesLeft = 0

    private var currentTitle: String? = null
    private var currentArtist: String? = null
    private var currentGenre: String? = null
    private var currentDurationMs: Long? = null

    private data class VideoPickerRequest(
        val title: String,
        val artist: String,
        val genre: String?,
        val durationMs: Long?,
    )
    private var videoPickerRequest: VideoPickerRequest? = null

    private var _playerInitialized = false
    private var pendingInitialPositionMs: Long? = null

    @OptIn(UnstableApi::class)
    val player: ExoPlayer by lazy {
        _playerInitialized = true
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(ANDROID_VR_USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to ANDROID_VR_USER_AGENT,
                    "Origin" to YOUTUBE_ORIGIN,
                    "Referer" to "$YOUTUBE_ORIGIN/",
                )
            )
        val defaultDataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val chunkedFactory = DataSource.Factory {
            ChunkedRangeDataSource(
                upstream = defaultDataSourceFactory.createDataSource(),
                chunkSize = 512 * 1024L,
                refresher = { failedUri -> refreshStreamUrlSync(failedUri) },
            )
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

    private fun refreshStreamUrlSync(failedUri: Uri): ChunkedRangeDataSource.Replacement? {
        val ready = _uiState.value as? SyncedVideoUiState.Ready ?: return null
        val videoId = ready.bundle.videoId
        val title = currentTitle ?: return null
        val artist = currentArtist ?: return null
        return runCatching {
            runBlocking(Dispatchers.IO) {
                val freshBundle = videoRepository.resolveVideo(
                    trackTitle = title,
                    trackArtist = artist,
                    genre = currentGenre,
                    expectedDurationMs = currentDurationMs,
                    selectedVideoId = videoId,
                    forceRefresh = true,
                ).getOrNull() ?: return@runBlocking null

                val targetStream = if (ready.selectedHeight != null && ready.selectedHeight > 0) {
                    freshBundle.availableStreams.firstOrNull { it.height == ready.selectedHeight }
                        ?: freshBundle.defaultStream
                } else {
                    freshBundle.defaultStream
                }
                ChunkedRangeDataSource.Replacement(
                    uri = Uri.parse(targetStream.url),
                    requestHeaders = targetStream.requestHeaders,
                )
            }
        }.getOrNull()
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

    fun loadVideo(
        title: String,
        artist: String,
        genre: String? = null,
        durationMs: Long? = null,
        initialPositionMs: Long? = null,
    ) {
        currentTitle = title
        currentArtist = artist
        currentGenre = genre
        currentDurationMs = durationMs
        pendingInitialPositionMs = initialPositionMs

        val selectedVideoId = videoRepository.savedVideoOverride(title, artist)
        val key = "$title|$artist|$genre|$durationMs|$selectedVideoId"
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
        resolveStream(title, artist, genre, durationMs, selectedVideoId)
    }

    private fun resolveStream(
        title: String,
        artist: String,
        genre: String?,
        durationMs: Long?,
        selectedVideoId: String?,
    ) {
        loadJob = viewModelScope.launch {
            _uiState.value = SyncedVideoUiState.Loading
            videoRepository.resolveVideo(
                trackTitle = title,
                trackArtist = artist,
                genre = genre,
                expectedDurationMs = durationMs,
                selectedVideoId = selectedVideoId,
            )
                .onSuccess { bundle ->
                    val preferredHeight = userPreferencesRepository.preferredVideoQualityFlow.first()
                    val (activeStream, selectedHeight) = if (preferredHeight != null && preferredHeight > 0) {
                        val matching = bundle.availableStreams.firstOrNull { (it.height ?: 0) == preferredHeight }
                        if (matching != null) matching to preferredHeight
                        else bundle.defaultStream to null
                    } else {
                        bundle.defaultStream to null
                    }

                    _uiState.value = SyncedVideoUiState.Ready(
                        bundle = bundle,
                        activeStream = activeStream,
                        selectedHeight = selectedHeight,
                    )
                    val initialPos = pendingInitialPositionMs ?: 0L
                    pendingInitialPositionMs = null
                    prepareMediaItem(activeStream.url, initialPos)
                }
                .onFailure { error ->
                    _uiState.value = SyncedVideoUiState.Error(
                        message = error.message ?: "Unable to resolve video stream",
                        noMatchingVideo = error.message?.startsWith("No video found") == true,
                    )
                }
        }
    }

    private fun prepareMediaItem(url: String, startPositionMs: Long = 0L) {
        if (_playerInitialized || _isVideoActive.value) {
            val mediaItem = MediaItem.Builder().setUri(url).build()
            if (startPositionMs > 0L) {
                player.setMediaItem(mediaItem, startPositionMs)
            } else {
                player.setMediaItem(mediaItem)
            }
            player.prepare()
        }
    }

    fun selectQuality(height: Int?) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredVideoQuality(height)
        }
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
            val isPlaying = player.playWhenReady
            val mediaItem = MediaItem.Builder().setUri(targetStream.url).build()
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem, currentPos.coerceAtLeast(0L))
            player.prepare()
            if (isPlaying) player.play()
        }
    }

    fun refreshCurrentVideoLink(currentPositionMs: Long? = null) {
        val title = currentTitle ?: return
        val artist = currentArtist ?: return
        val genre = currentGenre
        val durationMs = currentDurationMs
        val ready = _uiState.value as? SyncedVideoUiState.Ready
        val targetPos = currentPositionMs ?: if (_playerInitialized) player.currentPosition else 0L
        val wasPlaying = if (_playerInitialized) player.playWhenReady else false

        if (ready != null) {
            videoRepository.invalidate(ready.bundle.videoId)
            _uiState.value = ready.copy(isRefreshing = true)
        } else {
            _uiState.value = SyncedVideoUiState.Loading
        }

        currentKey = null
        linkRefreshesLeft = MAX_LINK_REFRESHES_PER_SONG
        loadJob?.cancel()
        if (_playerInitialized) {
            player.stop()
            player.clearMediaItems()
        }
        runCatching { okHttpClient.connectionPool.evictAll() }

        loadJob = viewModelScope.launch {
            val selectedVideoId = videoRepository.savedVideoOverride(title, artist)
            videoRepository.resolveVideo(
                trackTitle = title,
                trackArtist = artist,
                genre = genre,
                expectedDurationMs = durationMs,
                selectedVideoId = selectedVideoId,
                forceRefresh = true,
            ).onSuccess { bundle ->
                val preferredHeight = userPreferencesRepository.preferredVideoQualityFlow.first()
                val (activeStream, selectedHeight) = if (preferredHeight != null && preferredHeight > 0) {
                    val matching = bundle.availableStreams.firstOrNull { (it.height ?: 0) == preferredHeight }
                    if (matching != null) matching to preferredHeight
                    else bundle.defaultStream to null
                } else {
                    bundle.defaultStream to null
                }

                _uiState.value = SyncedVideoUiState.Ready(
                    bundle = bundle,
                    activeStream = activeStream,
                    selectedHeight = selectedHeight,
                    isRefreshing = false,
                )

                if (_playerInitialized || _isVideoActive.value) {
                    val mediaItem = MediaItem.Builder().setUri(activeStream.url).build()
                    player.setMediaItem(mediaItem, targetPos.coerceAtLeast(0L))
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
            }.onFailure { error ->
                _uiState.value = SyncedVideoUiState.Error(
                    message = error.message ?: "Unable to refresh video stream",
                )
            }
        }
    }

    fun onVideoPlaybackError() {
        val ready = _uiState.value as? SyncedVideoUiState.Ready ?: return
        if (ready.selectedHeight != null) {
            selectQuality(null)
            return
        }
        if (linkRefreshesLeft > 0) {
            linkRefreshesLeft--
            refreshCurrentVideoLink()
        } else {
            _uiState.value = SyncedVideoUiState.Error("Video stream unavailable")
        }
    }

    fun openVideoPicker(title: String, artist: String, genre: String? = null, durationMs: Long? = null) {
        videoSearchJob?.cancel()
        val request = VideoPickerRequest(title, artist, genre, durationMs)
        videoPickerRequest = request
        val defaultQuery = videoRepository.fallbackVideoSearchQuery(title, artist, genre)
        _currentSearchQuery.value = defaultQuery
        _videoPickerState.value = VideoPickerUiState.Loading

        videoSearchJob = viewModelScope.launch {
            val candidates = runCatching {
                videoRepository.searchVideoCandidates(title, artist, genre, durationMs)
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

    fun searchCustomQuery(query: String) {
        videoSearchJob?.cancel()
        val request = videoPickerRequest ?: return
        _currentSearchQuery.value = query
        _videoPickerState.value = VideoPickerUiState.Loading

        videoSearchJob = viewModelScope.launch {
            val candidates = runCatching {
                videoRepository.searchByCustomQuery(
                    query = query,
                    expectedDurationMs = request.durationMs,
                    wantedTitle = request.title,
                    wantedArtist = request.artist,
                )
            }.getOrElse { error ->
                if (videoPickerRequest == request) {
                    _videoPickerState.value = VideoPickerUiState.Error(error.message ?: "Search failed")
                }
                return@launch
            }
            if (videoPickerRequest != request) return@launch
            _videoPickerState.value = VideoPickerUiState.Results(
                candidates = candidates,
                savedVideoId = videoRepository.savedVideoOverride(request.title, request.artist),
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
        videoRepository.saveVideoOverride(
            title = request.title,
            artist = request.artist,
            videoId = candidate.videoId,
            videoTitle = candidate.title,
            videoChannel = candidate.channel,
            durationSeconds = candidate.durationSeconds,
        )
        dismissVideoPicker()
        currentKey = null
        loadVideo(request.title, request.artist, request.genre, request.durationMs)
    }

    fun selectVideoUrl(input: String): Boolean {
        val videoId = extractYoutubeVideoId(input) ?: return false
        val request = videoPickerRequest ?: return false
        videoRepository.saveVideoOverride(request.title, request.artist, videoId)
        dismissVideoPicker()
        currentKey = null
        loadVideo(request.title, request.artist, request.genre, request.durationMs)
        return true
    }

    fun clearVideoOverride() {
        val request = videoPickerRequest ?: return
        videoRepository.clearVideoOverride(request.title, request.artist)
        dismissVideoPicker()
        currentKey = null
        loadVideo(request.title, request.artist, request.genre, request.durationMs)
    }

    @OptIn(UnstableApi::class)
    override fun onCleared() {
        super.onCleared()
        if (_playerInitialized) {
            player.release()
        }
    }
}
