package com.drliki.MusicLi.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drliki.MusicLi.data.explore.ExploreAlbum
import com.drliki.MusicLi.data.explore.ExploreArtist
import com.drliki.MusicLi.data.explore.ExploreBrowseCategory
import com.drliki.MusicLi.data.explore.ExploreDownloadItem
import com.drliki.MusicLi.data.explore.ExploreDownloadStatus
import com.drliki.MusicLi.data.explore.ExploreHomeFeed
import com.drliki.MusicLi.data.explore.ExplorePlaylist
import com.drliki.MusicLi.data.explore.ExploreRepository
import com.drliki.MusicLi.data.explore.ExploreSearchResults
import com.drliki.MusicLi.data.explore.ExploreSong
import com.drliki.MusicLi.data.explore.ExploreTrackDownloader
import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExploreFilterTab(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
}

sealed interface ExploreDetailState {
    data object Hidden : ExploreDetailState
    data class Loading(val title: String) : ExploreDetailState
    data class AlbumDetail(val album: ExploreAlbum) : ExploreDetailState
    data class PlaylistDetail(val playlist: ExplorePlaylist) : ExploreDetailState
    data class ArtistDetail(val artist: ExploreArtist) : ExploreDetailState
    data class Error(val title: String, val message: String) : ExploreDetailState
}

data class ExploreUiState(
    val query: String = "",
    val selectedLanguage: String = "All",
    val selectedFilter: ExploreFilterTab = ExploreFilterTab.ALL,
    val isLoadingFeed: Boolean = true,
    val isRefreshingFeed: Boolean = false,
    val feed: ExploreHomeFeed = ExploreHomeFeed(),
    val feedError: String? = null,
    val isSearching: Boolean = false,
    val searchResults: ExploreSearchResults = ExploreSearchResults(),
    val searchError: String? = null,
    val recentSearches: List<String> = emptyList(),
)

data class ExploreDownloadsUiState(
    val active: List<ExploreDownloadItem> = emptyList(),
    val failed: List<ExploreDownloadItem> = emptyList(),
    val completed: List<ExploreDownloadItem> = emptyList(),
    val totalBytes: Long = 0L,
) {
    val totalCount: Int get() = active.size + failed.size + completed.size
}

data class ExploreOptionsState(
    val streamQualityKbps: Int = 320,
    val downloadQualityKbps: Int = 320,
    val downloadSafUri: String? = null,
    val downloadSubfolders: Boolean = true,
    val downloadLyrics: Boolean = true,
    val apiBaseUrl: String = UserPreferencesRepository.DEFAULT_EXPLORE_API_BASE_URL,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val exploreRepository: ExploreRepository,
    private val downloader: ExploreTrackDownloader,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow<ExploreDetailState>(ExploreDetailState.Hidden)
    val detailState: StateFlow<ExploreDetailState> = _detailState.asStateFlow()

    val downloads: StateFlow<List<ExploreDownloadItem>> = downloader.downloads
    val downloadedSongIds: StateFlow<Set<String>> = downloader.downloadedSongIds
    val preferredQualityKbps: StateFlow<Int> = userPreferencesRepository.exploreDownloadQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 320)

    val optionsState: StateFlow<ExploreOptionsState> = combine(
        combine(
            userPreferencesRepository.exploreStreamQualityFlow,
            userPreferencesRepository.exploreDownloadQualityFlow,
            userPreferencesRepository.exploreDownloadSafUriFlow,
        ) { streamQ, dlQ, safUri -> Triple(streamQ, dlQ, safUri) },
        combine(
            userPreferencesRepository.exploreDownloadSubfoldersFlow,
            userPreferencesRepository.exploreDownloadLyricsFlow,
            userPreferencesRepository.exploreApiBaseUrlFlow,
        ) { subfolders, lyrics, apiUrl -> Triple(subfolders, lyrics, apiUrl) },
    ) { first, second ->
        ExploreOptionsState(
            streamQualityKbps = first.first,
            downloadQualityKbps = first.second,
            downloadSafUri = first.third,
            downloadSubfolders = second.first,
            downloadLyrics = second.second,
            apiBaseUrl = second.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreOptionsState())

    fun setStreamQualityKbps(kbps: Int) {
        viewModelScope.launch { userPreferencesRepository.setExploreStreamQuality(kbps) }
    }

    fun setDownloadQualityKbps(kbps: Int) {
        viewModelScope.launch { userPreferencesRepository.setExploreDownloadQuality(kbps) }
    }

    fun setDownloadSafUri(uriString: String?) {
        viewModelScope.launch { userPreferencesRepository.setExploreDownloadSafUri(uriString) }
    }

    fun setDownloadSubfolders(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setExploreDownloadSubfolders(enabled) }
    }

    fun setDownloadLyrics(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setExploreDownloadLyrics(enabled) }
    }

    fun setApiBaseUrl(url: String?) {
        viewModelScope.launch { userPreferencesRepository.setExploreApiBaseUrl(url) }
    }

    val downloadsUiState: StateFlow<ExploreDownloadsUiState> = combine(downloader.downloads) { array ->
        val list = array[0]
        ExploreDownloadsUiState(
            active = list.filter {
                it.status == ExploreDownloadStatus.QUEUED || it.status == ExploreDownloadStatus.DOWNLOADING
            },
            failed = list.filter { it.status == ExploreDownloadStatus.FAILED },
            completed = list.filter { it.status == ExploreDownloadStatus.COMPLETED },
            totalBytes = list.filter { it.status == ExploreDownloadStatus.COMPLETED }.sumOf { it.bytesWritten },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreDownloadsUiState())

    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var activeBrowseCategory: ExploreBrowseCategory? = null

    init {
        loadFeed(language = "All")
        viewModelScope.launch {
            userPreferencesRepository.exploreRecentSearchesFlow.collect { history ->
                _uiState.update { it.copy(recentSearches = history) }
            }
        }
    }

    fun selectLanguage(language: String) {
        if (_uiState.value.selectedLanguage == language) return
        _uiState.update { it.copy(selectedLanguage = language) }
        loadFeed(language)
    }

    fun selectFilterTab(filter: ExploreFilterTab) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun loadFeed(language: String = _uiState.value.selectedLanguage, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasExistingContent = !_uiState.value.feed.let {
                it.trendingSongs.isEmpty() && it.topAlbums.isEmpty() &&
                    it.featuredPlaylists.isEmpty() && it.topArtists.isEmpty()
            }
            _uiState.update {
                if (forceRefresh && hasExistingContent) {
                    it.copy(isRefreshingFeed = true, feedError = null)
                } else {
                    it.copy(isLoadingFeed = true, isRefreshingFeed = false, feedError = null)
                }
            }
            exploreRepository.getHomeFeed(language, forceRefresh)
                .onSuccess { feed ->
                    _uiState.update {
                        it.copy(
                            isLoadingFeed = false,
                            isRefreshingFeed = false,
                            feed = feed,
                            feedError = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingFeed = false,
                            isRefreshingFeed = false,
                            feedError = err.message ?: "Unable to load Explore feed",
                        )
                    }
                }
        }
    }

    /**
     * Refreshes the active Explore content: re-runs the current search if a query is active,
     * otherwise fetches a fresh page of feed content bypassing the in-memory feed cache.
     */
    fun refreshExplore() {
        val currentQuery = _uiState.value.query.trim()
        val browseCat = activeBrowseCategory
        if (currentQuery.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                _uiState.update { it.copy(isRefreshingFeed = true) }
                if (browseCat != null) {
                    executeBrowseCategory(browseCat, currentQuery)
                } else {
                    executeSearch(currentQuery)
                }
                _uiState.update { it.copy(isRefreshingFeed = false) }
            }
        } else {
            loadFeed(language = _uiState.value.selectedLanguage, forceRefresh = true)
        }
    }

    fun onQueryChange(newQuery: String) {
        activeBrowseCategory = null
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        val trimmed = newQuery.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResults = ExploreSearchResults(), searchError = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(450)
            executeSearch(trimmed)
        }
    }

    fun submitSearch(query: String = _uiState.value.query) {
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isBlank()) return
        activeBrowseCategory = null
        val updatedHistory = (listOf(trimmed) + _uiState.value.recentSearches.filterNot {
            it.equals(trimmed, ignoreCase = true)
        }).take(10)
        _uiState.update { it.copy(query = trimmed, recentSearches = updatedHistory) }
        viewModelScope.launch {
            userPreferencesRepository.saveExploreRecentSearches(updatedHistory)
        }
        searchJob = viewModelScope.launch {
            executeSearch(trimmed)
        }
    }

    fun clearRecentSearches() {
        _uiState.update { it.copy(recentSearches = emptyList()) }
        viewModelScope.launch {
            userPreferencesRepository.saveExploreRecentSearches(emptyList())
        }
    }

    fun removeRecentSearch(query: String) {
        val updated = _uiState.value.recentSearches.filterNot {
            it.equals(query, ignoreCase = true)
        }
        _uiState.update { it.copy(recentSearches = updated) }
        viewModelScope.launch {
            userPreferencesRepository.saveExploreRecentSearches(updated)
        }
    }

    fun browseCategory(query: String, filter: ExploreFilterTab) {
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isBlank()) return
        activeBrowseCategory = null
        _uiState.update { it.copy(query = trimmed, selectedFilter = filter) }
        searchJob = viewModelScope.launch {
            executeSearch(trimmed)
        }
    }

    fun browseCategory(
        category: ExploreBrowseCategory,
        displayQuery: String,
        filter: ExploreFilterTab,
    ) {
        val trimmed = displayQuery.trim()
        searchJob?.cancel()
        if (trimmed.isBlank()) return
        activeBrowseCategory = category
        _uiState.update { it.copy(query = trimmed, selectedFilter = filter) }
        searchJob = viewModelScope.launch {
            executeBrowseCategory(category, trimmed)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        activeBrowseCategory = null
        _uiState.update {
            it.copy(query = "", isSearching = false, searchResults = ExploreSearchResults(), searchError = null)
        }
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        exploreRepository.searchAll(query)
            .onSuccess { results ->
                _uiState.update { it.copy(isSearching = false, searchResults = results, searchError = null) }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(isSearching = false, searchError = err.message ?: "Search failed")
                }
            }
    }

    private suspend fun executeBrowseCategory(
        category: ExploreBrowseCategory,
        fallbackQuery: String,
    ) {
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        exploreRepository.browseCategoryFeed(
            category = category,
            language = _uiState.value.selectedLanguage,
            fallbackQuery = fallbackQuery,
        )
            .onSuccess { results ->
                _uiState.update { it.copy(isSearching = false, searchResults = results, searchError = null) }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(isSearching = false, searchError = err.message ?: "Unable to load category")
                }
            }
    }

    fun openAlbum(album: ExploreAlbum) {
        detailJob?.cancel()
        _detailState.value = ExploreDetailState.Loading(album.title)
        detailJob = viewModelScope.launch {
            exploreRepository.getAlbumDetail(album.id)
                .onSuccess { detail -> _detailState.value = ExploreDetailState.AlbumDetail(detail) }
                .onFailure { err ->
                    _detailState.value = ExploreDetailState.Error(album.title, err.message ?: "Failed to load album")
                }
        }
    }

    fun openPlaylist(playlist: ExplorePlaylist) {
        detailJob?.cancel()
        _detailState.value = ExploreDetailState.Loading(playlist.title)
        detailJob = viewModelScope.launch {
            exploreRepository.getPlaylistDetail(playlist.id)
                .onSuccess { detail -> _detailState.value = ExploreDetailState.PlaylistDetail(detail) }
                .onFailure { err ->
                    _detailState.value = ExploreDetailState.Error(playlist.title, err.message ?: "Failed to load playlist")
                }
        }
    }

    fun openArtist(artist: ExploreArtist) {
        detailJob?.cancel()
        _detailState.value = ExploreDetailState.Loading(artist.name)
        detailJob = viewModelScope.launch {
            exploreRepository.getArtistDetail(artist.id)
                .onSuccess { detail -> _detailState.value = ExploreDetailState.ArtistDetail(detail) }
                .onFailure { err ->
                    _detailState.value = ExploreDetailState.Error(artist.name, err.message ?: "Failed to load artist")
                }
        }
    }

    /**
     * Opens the JioSaavn artist detail sheet for a streaming track by resolving the artist ID
     * from its Saavn track metadata or falling back to artist search by name.
     */
    fun openArtistFromStream(saavnSongId: String?, artistName: String) {
        val label = artistName.trim().ifBlank { "Artist" }
        detailJob?.cancel()
        _detailState.value = ExploreDetailState.Loading(label)
        detailJob = viewModelScope.launch {
            exploreRepository.resolveArtistDetailForStream(saavnSongId, artistName)
                .onSuccess { detail -> _detailState.value = ExploreDetailState.ArtistDetail(detail) }
                .onFailure { err ->
                    _detailState.value = ExploreDetailState.Error(label, err.message ?: "Failed to load artist")
                }
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        _detailState.value = ExploreDetailState.Hidden
    }

    fun downloadSong(song: ExploreSong, subfolder: String? = null) {
        downloader.enqueueSongDownload(song, subfolder)
    }

    fun downloadSongBySaavnId(saavnId: String) {
        viewModelScope.launch {
            exploreRepository.resolveSong(saavnId, includeLyrics = true).onSuccess { song ->
                downloader.enqueueSongDownload(song)
            }
        }
    }

    fun downloadCollection(collectionName: String, songs: List<ExploreSong>) {
        downloader.enqueueBatchDownload(collectionName, songs)
    }

    fun cancelDownload(downloadId: String) = downloader.cancelDownload(downloadId)
    fun retryDownload(downloadId: String) = downloader.retryDownload(downloadId)
    fun removeDownload(downloadId: String) = downloader.removeDownloadEntry(downloadId)
    fun clearCompletedDownloads() = downloader.clearCompleted()

    suspend fun resolvePlayableSong(song: ExploreSong): ExploreSong {
        if (song.streamUrlsByQuality.isNotEmpty()) return song
        return exploreRepository.resolveSong(song.id, includeLyrics = true).getOrElse { song }
    }

    suspend fun resolvePlayableQueue(songs: List<ExploreSong>): List<ExploreSong> {
        val resolved = mutableListOf<ExploreSong>()
        for (song in songs) {
            if (song.streamUrlsByQuality.isNotEmpty()) {
                resolved.add(song)
            } else {
                exploreRepository.resolveSong(song.id, includeLyrics = true).getOrNull()?.let { resolved.add(it) }
            }
        }
        return resolved
    }

    suspend fun currentPreferredBitrateKbps(): Int = userPreferencesRepository.exploreStreamQualityFlow.first()
}
