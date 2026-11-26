package com.drliki.MusicLi.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drliki.MusicLi.data.lyrics.bulk.BulkLyricsEmbedManager
import com.drliki.MusicLi.data.lyrics.bulk.BulkLyricsEmbedOptions
import com.drliki.MusicLi.data.lyrics.bulk.BulkLyricsEmbedUiState
import com.drliki.MusicLi.data.lyrics.bulk.PendingWritePermissionBatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BulkLyricsEmbedViewModel @Inject constructor(
    private val bulkLyricsEmbedManager: BulkLyricsEmbedManager,
) : ViewModel() {

    val uiState: StateFlow<BulkLyricsEmbedUiState> = bulkLyricsEmbedManager.uiState
    val eligibleLibraryCount: StateFlow<Int?> = bulkLyricsEmbedManager.eligibleLibraryCount

    private val _pendingPermissionBatch = MutableStateFlow<PendingWritePermissionBatch?>(null)
    val pendingPermissionBatch: StateFlow<PendingWritePermissionBatch?> = _pendingPermissionBatch.asStateFlow()

    private var pendingStartAfterPermission: Boolean = false
    private var pendingResumeAfterPermission: Boolean = false
    private var pendingRetryAfterPermission: Boolean = false

    init {
        viewModelScope.launch {
            bulkLyricsEmbedManager.refreshEligibleLibraryCount()
        }
    }

    fun setSyncedOnly(enabled: Boolean) {
        viewModelScope.launch {
            bulkLyricsEmbedManager.updateOptions { it.copy(syncedOnly = enabled) }
        }
    }

    fun setSkipExistingSynced(enabled: Boolean) {
        viewModelScope.launch {
            bulkLyricsEmbedManager.updateOptions { it.copy(skipExistingSynced = enabled) }
        }
    }

    fun setSaveLrcSidecar(enabled: Boolean) {
        viewModelScope.launch {
            bulkLyricsEmbedManager.updateOptions { it.copy(saveLrcSidecar = enabled) }
        }
    }

    fun setDelayBetweenSongsMs(delayMs: Long) {
        viewModelScope.launch {
            bulkLyricsEmbedManager.updateOptions { it.copy(delayBetweenSongsMs = delayMs) }
        }
    }

    /**
     * Checks Android 11+ MediaStore write permission batches upfront before starting the queue.
     */
    fun requestStart() {
        viewModelScope.launch {
            val nextBatch = bulkLyricsEmbedManager.createNextWritePermissionBatch()
            if (nextBatch != null) {
                pendingStartAfterPermission = true
                pendingResumeAfterPermission = false
                _pendingPermissionBatch.value = nextBatch
            } else {
                bulkLyricsEmbedManager.startNewRun()
            }
        }
    }

    fun requestGrantPermissionsAndResume() {
        viewModelScope.launch {
            val nextBatch = bulkLyricsEmbedManager.createNextWritePermissionBatch()
            if (nextBatch != null) {
                pendingResumeAfterPermission = true
                pendingStartAfterPermission = false
                _pendingPermissionBatch.value = nextBatch
            } else {
                bulkLyricsEmbedManager.resume()
            }
        }
    }

    fun onPermissionBatchResult(granted: Boolean) {
        val currentBatch = _pendingPermissionBatch.value
        _pendingPermissionBatch.value = null
        if (currentBatch == null) return

        viewModelScope.launch {
            bulkLyricsEmbedManager.onWritePermissionBatchResult(
                granted = granted,
                batchSongIds = currentBatch.songIds,
            )
            if (!granted) {
                pendingStartAfterPermission = false
                pendingResumeAfterPermission = false
                pendingRetryAfterPermission = false
                return@launch
            }

            val nextBatch = bulkLyricsEmbedManager.createNextWritePermissionBatch()
            if (nextBatch != null) {
                _pendingPermissionBatch.value = nextBatch
                return@launch
            }

            when {
                pendingStartAfterPermission -> {
                    pendingStartAfterPermission = false
                    bulkLyricsEmbedManager.startNewRun()
                }
                pendingResumeAfterPermission -> {
                    pendingResumeAfterPermission = false
                    bulkLyricsEmbedManager.resume()
                }
                pendingRetryAfterPermission -> {
                    pendingRetryAfterPermission = false
                    bulkLyricsEmbedManager.retryFailed()
                }
            }
        }
    }

    fun pause() {
        viewModelScope.launch {
            bulkLyricsEmbedManager.pause()
        }
    }

    fun resume() {
        viewModelScope.launch {
            bulkLyricsEmbedManager.resume()
        }
    }

    fun stopAndReset() {
        viewModelScope.launch {
            pendingStartAfterPermission = false
            pendingResumeAfterPermission = false
            _pendingPermissionBatch.value = null
            bulkLyricsEmbedManager.stopAndReset()
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            val nextBatch = bulkLyricsEmbedManager.createNextWritePermissionBatch()
            if (nextBatch != null) {
                pendingRetryAfterPermission = true
                pendingResumeAfterPermission = false
                pendingStartAfterPermission = false
                _pendingPermissionBatch.value = nextBatch
            } else {
                bulkLyricsEmbedManager.retryFailed()
            }
        }
    }
}
