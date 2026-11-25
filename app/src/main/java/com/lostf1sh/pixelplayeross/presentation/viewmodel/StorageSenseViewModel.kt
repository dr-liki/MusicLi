package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.storage.StorageSenseBreakdown
import com.lostf1sh.pixelplayeross.data.storage.StorageSenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageSenseUiState(
    val isLoading: Boolean = true,
    val isClearing: Boolean = false,
    val breakdown: StorageSenseBreakdown = StorageSenseBreakdown(),
    val streamCacheLimitMb: Int = 250,
    val autoCleanDays: Int = 14,
    val albumArtCacheLimitMb: Int = 200,
    val statusMessage: String? = null,
)

@HiltViewModel
class StorageSenseViewModel @Inject constructor(
    private val storageSenseRepository: StorageSenseRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _breakdown = MutableStateFlow(StorageSenseBreakdown())
    private val _isLoading = MutableStateFlow(true)
    private val _isClearing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StorageSenseUiState> = combine(
        _isLoading,
        _isClearing,
        _breakdown,
        userPreferencesRepository.exploreStreamCacheLimitMbFlow,
        userPreferencesRepository.exploreStreamCacheAutoCleanDaysFlow,
        userPreferencesRepository.albumArtCacheLimitMbFlow,
        _statusMessage,
    ) { values ->
        StorageSenseUiState(
            isLoading = values[0] as Boolean,
            isClearing = values[1] as Boolean,
            breakdown = values[2] as StorageSenseBreakdown,
            streamCacheLimitMb = values[3] as Int,
            autoCleanDays = values[4] as Int,
            albumArtCacheLimitMb = values[5] as Int,
            statusMessage = values[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageSenseUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _breakdown.value = storageSenseRepository.inspectStorage()
            _isLoading.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setStreamCacheLimitMb(limitMb: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setExploreStreamCacheLimitMb(limitMb)
            if (limitMb <= 0) {
                storageSenseRepository.clearExploreStreamCache()
            }
            _breakdown.value = storageSenseRepository.inspectStorage()
        }
    }

    fun setAutoCleanDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setExploreStreamCacheAutoCleanDays(days)
        }
    }

    fun clearExploreStreamCache() = runClear("Explore stream cache cleared") {
        storageSenseRepository.clearExploreStreamCache()
    }

    fun clearImageCache() = runClear("Album art cache cleared") {
        storageSenseRepository.clearImageCache()
    }

    fun clearTempAudioCache() = runClear("Temp audio cache cleared") {
        storageSenseRepository.clearTempAudioCache()
    }

    fun clearLyricsCache() = runClear("Lyrics cache cleared") {
        storageSenseRepository.clearLyricsCache()
    }

    fun clearAllSafeCaches() = runClear("All safe caches cleared") {
        storageSenseRepository.clearAllSafeCaches()
    }

    private fun runClear(doneMessage: String, action: suspend () -> Unit) {
        if (_isClearing.value) return
        viewModelScope.launch {
            _isClearing.value = true
            try {
                action()
                _breakdown.value = storageSenseRepository.inspectStorage()
                _statusMessage.value = doneMessage
            } finally {
                _isClearing.value = false
            }
        }
    }
}
