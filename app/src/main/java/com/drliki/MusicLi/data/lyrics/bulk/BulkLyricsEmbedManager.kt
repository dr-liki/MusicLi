package com.drliki.MusicLi.data.lyrics.bulk

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import androidx.annotation.VisibleForTesting
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.drliki.MusicLi.data.database.LyricsDao
import com.drliki.MusicLi.data.database.MusicDao
import com.drliki.MusicLi.data.database.SongEntity
import com.drliki.MusicLi.data.database.SourceType
import com.drliki.MusicLi.data.database.toSong
import com.drliki.MusicLi.data.media.AudioMetadataReader
import com.drliki.MusicLi.data.media.MetadataEditError
import com.drliki.MusicLi.data.media.SongMetadataEditor
import com.drliki.MusicLi.data.model.Song
import com.drliki.MusicLi.data.network.lyrics.providers.BatchLyricsLookupResult
import com.drliki.MusicLi.data.network.lyrics.providers.LyricsProviderId
import com.drliki.MusicLi.data.network.lyrics.providers.MultiProviderLyricsOrchestrator
import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import com.drliki.MusicLi.data.repository.LyricsRepository
import com.drliki.MusicLi.data.repository.LyricsSearchResult
import com.drliki.MusicLi.data.worker.BulkLyricsEmbedWorker
import com.drliki.MusicLi.utils.LyricsUtils
import com.drliki.MusicLi.utils.MediaStorePermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal interface BulkLyricsWorkScheduler {
    fun enqueueImmediate()
    fun enqueueDelayed(delayMs: Long)
    fun enqueueWhenOnline()
    fun cancel()
}

private class WorkManagerBulkLyricsScheduler(
    private val context: Context,
) : BulkLyricsWorkScheduler {
    private fun connectedConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun enqueueImmediate() {
        val request = OneTimeWorkRequestBuilder<BulkLyricsEmbedWorker>()
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun enqueueDelayed(delayMs: Long) {
        val safeDelay = delayMs.coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<BulkLyricsEmbedWorker>()
            .setInitialDelay(safeDelay, TimeUnit.MILLISECONDS)
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun enqueueWhenOnline() {
        val request = OneTimeWorkRequestBuilder<BulkLyricsEmbedWorker>()
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "bulk_lyrics_embed"
    }
}

internal data class BulkLyricsWriteResult(
    val success: Boolean,
    val noWritePermission: Boolean = false,
    val errorMessage: String? = null,
)

internal interface BulkLyricsEnvAdapter {
    suspend fun loadAllSongs(): List<SongEntity>
    suspend fun loadSongById(songId: Long): SongEntity?
    suspend fun getStoredDbLyrics(songId: Long): String?
    suspend fun readFileEmbeddedLyrics(filePath: String): String?
    suspend fun lookupLyricsByPriority(song: Song, syncedOnly: Boolean): BatchLyricsLookupResult
    suspend fun embedSongLyrics(
        song: Song,
        lrcText: String,
        saveLrcSidecar: Boolean,
    ): BulkLyricsWriteResult

    fun hasFileWritePermission(songId: Long): Boolean
    fun getMediaStoreUri(songId: Long): Uri?
    fun fileExists(path: String): Boolean
}

private class RealBulkLyricsEnvAdapter(
    private val context: Context,
    private val musicDao: MusicDao,
    private val lyricsDao: LyricsDao,
    private val lyricsRepository: LyricsRepository,
    private val songMetadataEditor: SongMetadataEditor,
    private val orchestrator: MultiProviderLyricsOrchestrator,
) : BulkLyricsEnvAdapter {

    override suspend fun loadAllSongs(): List<SongEntity> = musicDao.getAllSongsList()

    override suspend fun loadSongById(songId: Long): SongEntity? = musicDao.getSongByIdOnce(songId)

    override suspend fun getStoredDbLyrics(songId: Long): String? = lyricsDao.getLyrics(songId)?.content

    override suspend fun readFileEmbeddedLyrics(filePath: String): String? = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext null
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        AudioMetadataReader.read(file, readArtwork = false, readCustomMetadata = false)?.lyrics
    }

    override suspend fun lookupLyricsByPriority(
        song: Song,
        syncedOnly: Boolean,
    ): BatchLyricsLookupResult = orchestrator.fetchSyncedByPriorityForBatch(
        song = song,
        syncedOnly = syncedOnly,
    )

    override suspend fun embedSongLyrics(
        song: Song,
        lrcText: String,
        saveLrcSidecar: Boolean,
    ): BulkLyricsWriteResult {
        val songId = song.id.toLongOrNull()
            ?: return BulkLyricsWriteResult(success = false, errorMessage = "Invalid song id: ${song.id}")

        val existingMeta = withContext(Dispatchers.IO) {
            val file = File(song.path)
            if (file.exists()) {
                AudioMetadataReader.read(file, readArtwork = false, readCustomMetadata = false)
            } else {
                null
            }
        }

        val editResult = songMetadataEditor.editSongMetadata(
            songId = songId,
            newTitle = existingMeta?.title?.takeIf { it.isNotBlank() } ?: song.title,
            newArtist = existingMeta?.artist?.takeIf { it.isNotBlank() } ?: song.artist,
            newAlbum = existingMeta?.album?.takeIf { it.isNotBlank() } ?: song.album,
            newAlbumArtist = existingMeta?.albumArtist ?: song.albumArtist,
            newComposer = existingMeta?.composer,
            newGenre = existingMeta?.genre ?: song.genre.orEmpty(),
            newLyrics = lrcText,
            newTrackNumber = existingMeta?.trackNumber ?: song.trackNumber,
            newDiscNumber = existingMeta?.discNumber ?: song.discNumber,
            newReplayGainTrackGainDb = null,
            newReplayGainAlbumGainDb = null,
            coverArtUpdate = null,
        )

        if (!editResult.success) {
            return BulkLyricsWriteResult(
                success = false,
                noWritePermission = editResult.error == MetadataEditError.NO_WRITE_PERMISSION,
                errorMessage = editResult.errorMessage ?: editResult.error?.name ?: "Metadata write failed",
            )
        }

        lyricsRepository.updateLyrics(songId, lrcText)
        musicDao.updateLyrics(songId, lrcText)

        if (saveLrcSidecar && song.path.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val audioFile = File(song.path)
                    val parent = audioFile.parentFile
                    if (parent != null && parent.exists()) {
                        val lrcFile = File(parent, "${audioFile.nameWithoutExtension}.lrc")
                        lrcFile.writeText(lrcText, Charsets.UTF_8)
                    }
                }.onFailure { err ->
                    Timber.tag(TAG).w(err, "Optional .lrc sidecar write failed for ${song.path}")
                }
            }
        }

        return BulkLyricsWriteResult(success = true)
    }

    override fun hasFileWritePermission(songId: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return true
        }
        val uri = MediaStorePermissionHelper.getMediaStoreUri(context, songId) ?: return false
        return runCatching {
            context.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    override fun getMediaStoreUri(songId: Long): Uri? =
        MediaStorePermissionHelper.getMediaStoreUri(context, songId)

    override fun fileExists(path: String): Boolean = path.isNotBlank() && File(path).exists()

    companion object {
        private const val TAG = "BulkLyricsEmbedManager"
    }
}

data class PendingWritePermissionBatch(
    val intentSender: IntentSender,
    val songIds: List<Long>,
    val totalPendingCount: Int,
)

@Singleton
class BulkLyricsEmbedManager internal constructor(
    private val context: Context?,
    private val env: BulkLyricsEnvAdapter,
    private val userPreferencesRepository: UserPreferencesRepository?,
    private val gson: Gson,
    private val checkpointFile: File,
    private val scheduler: BulkLyricsWorkScheduler,
    private val clockMs: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        musicDao: MusicDao,
        lyricsDao: LyricsDao,
        lyricsRepository: LyricsRepository,
        songMetadataEditor: SongMetadataEditor,
        orchestrator: MultiProviderLyricsOrchestrator,
        userPreferencesRepository: UserPreferencesRepository,
        gson: Gson,
    ) : this(
        context = context,
        env = RealBulkLyricsEnvAdapter(
            context = context,
            musicDao = musicDao,
            lyricsDao = lyricsDao,
            lyricsRepository = lyricsRepository,
            songMetadataEditor = songMetadataEditor,
            orchestrator = orchestrator,
        ),
        userPreferencesRepository = userPreferencesRepository,
        gson = gson,
        checkpointFile = File(context.filesDir, CHECKPOINT_FILE_NAME),
        scheduler = WorkManagerBulkLyricsScheduler(context),
        clockMs = { System.currentTimeMillis() },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private val workerRunMutex = Mutex()
    private val sessionGrantedSongIds = mutableSetOf<Long>()

    private var checkpoint: BulkLyricsEmbedCheckpoint = BulkLyricsEmbedCheckpoint()
    private var currentSong: BulkLyricsCurrentSong? = null
    private var isScanningLibrary: Boolean = false
    private var priorityOrder: List<LyricsProviderId> = LyricsProviderId.DEFAULT_ORDER
    private var enabledProviders: Set<LyricsProviderId> = LyricsProviderId.DEFAULT_ORDER.toSet()

    private val _uiState = MutableStateFlow(BulkLyricsEmbedUiState())
    val uiState: StateFlow<BulkLyricsEmbedUiState> = _uiState.asStateFlow()

    private val _eligibleLibraryCount = MutableStateFlow<Int?>(null)
    val eligibleLibraryCount: StateFlow<Int?> = _eligibleLibraryCount.asStateFlow()

    init {
        restoreCheckpointFromDisk()
        observeProviderPreferences()
    }

    private fun observeProviderPreferences() {
        val prefs = userPreferencesRepository ?: return
        scope.launch {
            combine(
                prefs.lyricsProviderOrderFlow,
                prefs.enabledLyricsProvidersFlow,
            ) { order, enabled -> order to enabled }.collect { (order, enabled) ->
                stateMutex.withLock {
                    priorityOrder = order
                    enabledProviders = enabled
                    emitUiStateLocked()
                }
            }
        }
    }

    private fun restoreCheckpointFromDisk() {
        val loaded = runCatching {
            if (!checkpointFile.exists()) {
                BulkLyricsEmbedCheckpoint()
            } else {
                val raw = checkpointFile.readText(Charsets.UTF_8)
                gson.fromJson(raw, BulkLyricsEmbedCheckpoint::class.java) ?: BulkLyricsEmbedCheckpoint()
            }
        }.getOrElse { err ->
            Timber.tag(TAG).w(err, "Failed to restore bulk lyrics checkpoint; resetting")
            BulkLyricsEmbedCheckpoint()
        }

        // If the OS killed the worker mid-song while RUNNING, present it as PAUSED_USER until resumed.
        checkpoint = if (loaded.status == BulkLyricsEmbedStatus.RUNNING) {
            loaded.copy(status = BulkLyricsEmbedStatus.PAUSED_USER)
        } else {
            loaded
        }
        emitUiStateUnchecked()
    }

    suspend fun refreshEligibleLibraryCount() {
        val count = withContext(Dispatchers.IO) {
            filterEditableLocalSongs(env.loadAllSongs()).size
        }
        _eligibleLibraryCount.value = count
    }

    suspend fun updateOptions(transform: (BulkLyricsEmbedOptions) -> BulkLyricsEmbedOptions) {
        stateMutex.withLock {
            val updated = transform(checkpoint.options)
            checkpoint = checkpoint.copy(options = updated)
            saveCheckpointLocked()
            emitUiStateLocked()
        }
    }

    /**
     * Scans eligible local tracks, builds the sequential queue, persists the checkpoint,
     * and enqueues [BulkLyricsEmbedWorker].
     */
    suspend fun startNewRun(optionsOverride: BulkLyricsEmbedOptions? = null) {
        val effectiveOptions = stateMutex.withLock {
            isScanningLibrary = true
            val opts = optionsOverride ?: checkpoint.options
            checkpoint = checkpoint.copy(options = opts, errorMessage = null)
            emitUiStateLocked()
            opts
        }

        val songs = try {
            withContext(Dispatchers.IO) {
                filterEditableLocalSongs(env.loadAllSongs())
                    .sortedWith(
                        compareBy<SongEntity>(
                            { it.title.lowercase() },
                            { it.artistName.lowercase() },
                            { it.id },
                        ),
                    )
            }
        } finally {
            stateMutex.withLock {
                isScanningLibrary = false
                emitUiStateLocked()
            }
        }

        if (songs.isEmpty()) {
            stateMutex.withLock {
                checkpoint = checkpoint.copy(
                    status = BulkLyricsEmbedStatus.COMPLETED,
                    queuedSongIds = emptyList(),
                    nextIndex = 0,
                    embeddedCount = 0,
                    skippedCount = 0,
                    notFoundCount = 0,
                    failedCount = 0,
                    failedSongIds = emptyList(),
                    logs = emptyList(),
                    resumeAtEpochMs = null,
                    rateLimitProviderId = null,
                    pendingPermissionSongId = null,
                    errorMessage = "No editable local songs found in library.",
                )
                currentSong = null
                saveCheckpointLocked()
                emitUiStateLocked()
            }
            return
        }

        stateMutex.withLock {
            checkpoint = BulkLyricsEmbedCheckpoint(
                status = BulkLyricsEmbedStatus.RUNNING,
                queuedSongIds = songs.map { it.id },
                nextIndex = 0,
                options = effectiveOptions,
            )
            currentSong = null
            saveCheckpointLocked()
            emitUiStateLocked()
        }
        _eligibleLibraryCount.value = songs.size
        scheduler.enqueueImmediate()
    }

    suspend fun pause() {
        scheduler.cancel()
        stateMutex.withLock {
            if (checkpoint.status == BulkLyricsEmbedStatus.RUNNING ||
                checkpoint.status == BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT ||
                checkpoint.status == BulkLyricsEmbedStatus.PAUSED_NETWORK
            ) {
                checkpoint = checkpoint.copy(
                    status = BulkLyricsEmbedStatus.PAUSED_USER,
                    resumeAtEpochMs = null,
                    rateLimitProviderId = null,
                )
                currentSong = null
                saveCheckpointLocked()
                emitUiStateLocked()
            }
        }
    }

    /**
     * Transitions a scheduled wakeup from [BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT] or
     * [BulkLyricsEmbedStatus.PAUSED_NETWORK] back to [BulkLyricsEmbedStatus.RUNNING] without
     * re-enqueueing WorkManager.
     */
    suspend fun prepareResumeFromWorker(): Boolean {
        return stateMutex.withLock {
            val canResume = (checkpoint.status == BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT ||
                checkpoint.status == BulkLyricsEmbedStatus.PAUSED_NETWORK ||
                checkpoint.status == BulkLyricsEmbedStatus.RUNNING) &&
                checkpoint.nextIndex < checkpoint.queuedSongIds.size
            if (!canResume) return@withLock false
            checkpoint = checkpoint.copy(
                status = BulkLyricsEmbedStatus.RUNNING,
                resumeAtEpochMs = null,
                rateLimitProviderId = null,
                pendingPermissionSongId = null,
                errorMessage = null,
            )
            saveCheckpointLocked()
            emitUiStateLocked()
            true
        }
    }

    suspend fun resume() {
        val shouldStart = stateMutex.withLock {
            val remaining = checkpoint.nextIndex < checkpoint.queuedSongIds.size
            if (!remaining) return@withLock false
            checkpoint = checkpoint.copy(
                status = BulkLyricsEmbedStatus.RUNNING,
                resumeAtEpochMs = null,
                rateLimitProviderId = null,
                pendingPermissionSongId = null,
                errorMessage = null,
            )
            saveCheckpointLocked()
            emitUiStateLocked()
            true
        }
        if (shouldStart) {
            scheduler.enqueueImmediate()
        }
    }

    suspend fun stopAndReset() {
        scheduler.cancel()
        stateMutex.withLock {
            checkpoint = BulkLyricsEmbedCheckpoint(options = checkpoint.options)
            currentSong = null
            saveCheckpointLocked()
            emitUiStateLocked()
        }
    }

    suspend fun retryFailed() {
        val retryIds = stateMutex.withLock { checkpoint.failedSongIds.distinct() }
        if (retryIds.isEmpty()) return

        stateMutex.withLock {
            checkpoint = BulkLyricsEmbedCheckpoint(
                status = BulkLyricsEmbedStatus.RUNNING,
                queuedSongIds = retryIds,
                nextIndex = 0,
                options = checkpoint.options,
            )
            currentSong = null
            saveCheckpointLocked()
            emitUiStateLocked()
        }
        scheduler.enqueueImmediate()
    }

    /**
     * Checks remaining queued songs (or all eligible local songs when idle) for missing Android 11+
     * MediaStore write access and returns an [IntentSender] for up to [maxBatchSize] songs at a time.
     */
    suspend fun createNextWritePermissionBatch(maxBatchSize: Int = DEFAULT_PERMISSION_BATCH_SIZE): PendingWritePermissionBatch? {
        val appContext = context ?: return null
        val targetSongIds = withContext(Dispatchers.IO) {
            val queuedRemaining = stateMutex.withLock {
                if (checkpoint.nextIndex < checkpoint.queuedSongIds.size) {
                    checkpoint.queuedSongIds.drop(checkpoint.nextIndex)
                } else {
                    emptyList()
                }
            }
            val candidates = if (queuedRemaining.isNotEmpty()) {
                queuedRemaining
            } else {
                filterEditableLocalSongs(env.loadAllSongs()).map { it.id }
            }

            val pendingSongId = stateMutex.withLock { checkpoint.pendingPermissionSongId }
            val orderedCandidates = if (pendingSongId != null && pendingSongId in candidates) {
                listOf(pendingSongId) + candidates.filterNot { it == pendingSongId }
            } else {
                candidates
            }

            orderedCandidates.filter { id ->
                id !in sessionGrantedSongIds && !env.hasFileWritePermission(id)
            }
        }

        if (targetSongIds.isEmpty()) return null

        val batchIds = targetSongIds.take(maxBatchSize)
        val uris = batchIds.mapNotNull { env.getMediaStoreUri(it) }
        if (uris.isEmpty()) return null

        val sender = MediaStorePermissionHelper.createWriteRequestIntentSender(appContext, uris) ?: return null
        return PendingWritePermissionBatch(
            intentSender = sender,
            songIds = batchIds,
            totalPendingCount = targetSongIds.size,
        )
    }

    suspend fun onWritePermissionBatchResult(granted: Boolean, batchSongIds: List<Long>) {
        if (granted) {
            stateMutex.withLock {
                sessionGrantedSongIds.addAll(batchSongIds)
                if (checkpoint.pendingPermissionSongId in batchSongIds) {
                    checkpoint = checkpoint.copy(pendingPermissionSongId = null, errorMessage = null)
                }
                emitUiStateLocked()
            }
        } else {
            stateMutex.withLock {
                if (checkpoint.status == BulkLyricsEmbedStatus.RUNNING ||
                    checkpoint.status == BulkLyricsEmbedStatus.NEEDS_PERMISSION
                ) {
                    checkpoint = checkpoint.copy(
                        status = BulkLyricsEmbedStatus.NEEDS_PERMISSION,
                        errorMessage = "Write permission was denied. Grant file modification access to continue embedding lyrics.",
                    )
                    saveCheckpointLocked()
                    emitUiStateLocked()
                }
            }
        }
    }

    /**
     * Executes queued items sequentially in priority order until complete, paused, rate-limited,
     * network-blocked, or awaiting write permission. Invoked by [BulkLyricsEmbedWorker].
     */
    suspend fun processQueueLoop(
        onProgress: suspend (BulkLyricsEmbedUiState) -> Unit = {},
    ): BulkLyricsEmbedStatus = workerRunMutex.withLock {
        var performedNetworkLookup = false

        while (currentCoroutineContext().isActive) {
            val snapshot = stateMutex.withLock {
                if (checkpoint.status != BulkLyricsEmbedStatus.RUNNING) {
                    return@withLock null
                }
                if (checkpoint.nextIndex >= checkpoint.queuedSongIds.size) {
                    checkpoint = checkpoint.copy(
                        status = BulkLyricsEmbedStatus.COMPLETED,
                        resumeAtEpochMs = null,
                        rateLimitProviderId = null,
                        pendingPermissionSongId = null,
                        errorMessage = null,
                    )
                    currentSong = null
                    saveCheckpointLocked()
                    emitUiStateLocked()
                    return@withLock null
                }
                checkpoint to checkpoint.queuedSongIds[checkpoint.nextIndex]
            } ?: break

            val (currentCheckpoint, songId) = snapshot
            val entity = env.loadSongById(songId)
            if (entity == null || !env.fileExists(entity.filePath)) {
                stateMutex.withLock {
                    if (checkpoint.status != BulkLyricsEmbedStatus.RUNNING) return@withLock
                    checkpoint = checkpoint.copy(
                        nextIndex = checkpoint.nextIndex + 1,
                        failedCount = checkpoint.failedCount + 1,
                        failedSongIds = (checkpoint.failedSongIds + songId).distinct(),
                        logs = appendLog(
                            checkpoint.logs,
                            BulkLyricsLogEntry(
                                songId = songId,
                                songTitle = entity?.title ?: "Song #$songId",
                                artist = entity?.artistName.orEmpty(),
                                outcome = BulkLyricsLogOutcome.FAILED,
                                detail = "Audio file missing",
                            ),
                        ),
                    )
                    currentSong = null
                    saveCheckpointLocked()
                    emitUiStateLocked()
                }
                onProgress(_uiState.value)
                continue
            }

            val song = entity.toSong()
            updateCurrentSong(songId, song.title, song.displayArtist, "Checking existing lyrics...", onProgress)

            if (currentCheckpoint.options.skipExistingSynced && hasExistingSyncedLyrics(songId, entity)) {
                stateMutex.withLock {
                    if (checkpoint.status != BulkLyricsEmbedStatus.RUNNING) return@withLock
                    checkpoint = checkpoint.copy(
                        nextIndex = checkpoint.nextIndex + 1,
                        skippedCount = checkpoint.skippedCount + 1,
                        failedSongIds = checkpoint.failedSongIds - songId,
                        logs = appendLog(
                            checkpoint.logs,
                            BulkLyricsLogEntry(
                                songId = songId,
                                songTitle = song.title,
                                artist = song.displayArtist,
                                outcome = BulkLyricsLogOutcome.SKIPPED_EXISTING_SYNCED,
                                detail = "Already has synced lyrics",
                            ),
                        ),
                    )
                    currentSong = null
                    saveCheckpointLocked()
                    emitUiStateLocked()
                }
                onProgress(_uiState.value)
                continue
            }

            val delayMs = currentCheckpoint.options.delayBetweenSongsMs
            if (performedNetworkLookup && delayMs > 0L) {
                delay(delayMs)
            }
            performedNetworkLookup = true

            val stillRunning = stateMutex.withLock { checkpoint.status == BulkLyricsEmbedStatus.RUNNING }
            if (!stillRunning) break

            updateCurrentSong(songId, song.title, song.displayArtist, "Searching providers in priority order...", onProgress)

            val lookupResult = env.lookupLyricsByPriority(
                song = song,
                syncedOnly = currentCheckpoint.options.syncedOnly,
            )

            when (lookupResult) {
                is BatchLyricsLookupResult.Matched -> {
                    val result = lookupResult.result
                    val lrcText = canonicalLrcText(result)
                    if (lrcText.isBlank()) {
                        recordNotFound(songId, song)
                        onProgress(_uiState.value)
                        continue
                    }

                    updateCurrentSong(
                        songId = songId,
                        title = song.title,
                        artist = song.displayArtist,
                        statusText = "Embedding into audio file (${result.providerLabel})...",
                        onProgress = onProgress,
                    )

                    val writeResult = env.embedSongLyrics(
                        song = song,
                        lrcText = lrcText,
                        saveLrcSidecar = currentCheckpoint.options.saveLrcSidecar,
                    )

                    if (writeResult.success) {
                        stateMutex.withLock {
                            if (checkpoint.status != BulkLyricsEmbedStatus.RUNNING) return@withLock
                            checkpoint = checkpoint.copy(
                                nextIndex = checkpoint.nextIndex + 1,
                                embeddedCount = checkpoint.embeddedCount + 1,
                                failedSongIds = checkpoint.failedSongIds - songId,
                                logs = appendLog(
                                    checkpoint.logs,
                                    BulkLyricsLogEntry(
                                        songId = songId,
                                        songTitle = song.title,
                                        artist = song.displayArtist,
                                        outcome = BulkLyricsLogOutcome.EMBEDDED,
                                        providerLabel = result.providerLabel,
                                        detail = if (result.hasSyncedContent) "Synced lyrics embedded" else "Plain lyrics embedded",
                                    ),
                                ),
                            )
                            currentSong = null
                            saveCheckpointLocked()
                            emitUiStateLocked()
                        }
                        onProgress(_uiState.value)
                    } else if (writeResult.noWritePermission) {
                        stateMutex.withLock {
                            sessionGrantedSongIds.remove(songId)
                            checkpoint = checkpoint.copy(
                                status = BulkLyricsEmbedStatus.NEEDS_PERMISSION,
                                pendingPermissionSongId = songId,
                                errorMessage = "Permission required to modify ${song.title}",
                                logs = appendLog(
                                    checkpoint.logs,
                                    BulkLyricsLogEntry(
                                        songId = songId,
                                        songTitle = song.title,
                                        artist = song.displayArtist,
                                        outcome = BulkLyricsLogOutcome.PERMISSION_REQUIRED,
                                        detail = writeResult.errorMessage,
                                    ),
                                ),
                            )
                            currentSong = null
                            saveCheckpointLocked()
                            emitUiStateLocked()
                        }
                        onProgress(_uiState.value)
                        break
                    } else {
                        stateMutex.withLock {
                            if (checkpoint.status != BulkLyricsEmbedStatus.RUNNING) return@withLock
                            checkpoint = checkpoint.copy(
                                nextIndex = checkpoint.nextIndex + 1,
                                failedCount = checkpoint.failedCount + 1,
                                failedSongIds = (checkpoint.failedSongIds + songId).distinct(),
                                logs = appendLog(
                                    checkpoint.logs,
                                    BulkLyricsLogEntry(
                                        songId = songId,
                                        songTitle = song.title,
                                        artist = song.displayArtist,
                                        outcome = BulkLyricsLogOutcome.FAILED,
                                        providerLabel = result.providerLabel,
                                        detail = writeResult.errorMessage,
                                    ),
                                ),
                            )
                            currentSong = null
                            saveCheckpointLocked()
                            emitUiStateLocked()
                        }
                        onProgress(_uiState.value)
                    }
                }

                is BatchLyricsLookupResult.NotFound -> {
                    recordNotFound(songId, song)
                    onProgress(_uiState.value)
                }

                is BatchLyricsLookupResult.RateLimited -> {
                    val cooldownMs = (lookupResult.retryAfterMs ?: DEFAULT_RATE_LIMIT_COOLDOWN_MS)
                        .coerceIn(MIN_RATE_LIMIT_COOLDOWN_MS, MAX_RATE_LIMIT_COOLDOWN_MS)
                    val resumeAt = clockMs() + cooldownMs
                    stateMutex.withLock {
                        checkpoint = checkpoint.copy(
                            status = BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT,
                            resumeAtEpochMs = resumeAt,
                            rateLimitProviderId = lookupResult.providerId.name,
                            errorMessage = "Rate limited by ${lookupResult.providerId.displayName}. Auto-resuming in ${cooldownMs / 1000L}s.",
                            logs = appendLog(
                                checkpoint.logs,
                                BulkLyricsLogEntry(
                                    songId = songId,
                                    songTitle = song.title,
                                    artist = song.displayArtist,
                                    outcome = BulkLyricsLogOutcome.RATE_LIMITED,
                                    providerLabel = lookupResult.providerId.displayName,
                                    detail = "Paused for ${cooldownMs / 1000L}s cooldown",
                                ),
                            ),
                        )
                        currentSong = null
                        saveCheckpointLocked()
                        emitUiStateLocked()
                    }
                    scheduler.enqueueDelayed(cooldownMs)
                    onProgress(_uiState.value)
                    break
                }

                is BatchLyricsLookupResult.NetworkError -> {
                    stateMutex.withLock {
                        checkpoint = checkpoint.copy(
                            status = BulkLyricsEmbedStatus.PAUSED_NETWORK,
                            errorMessage = "Network unavailable. Will auto-resume when connection returns.",
                            logs = appendLog(
                                checkpoint.logs,
                                BulkLyricsLogEntry(
                                    songId = songId,
                                    songTitle = song.title,
                                    artist = song.displayArtist,
                                    outcome = BulkLyricsLogOutcome.NETWORK_ERROR,
                                    providerLabel = lookupResult.providerId?.displayName,
                                    detail = lookupResult.message ?: "Offline",
                                ),
                            ),
                        )
                        currentSong = null
                        saveCheckpointLocked()
                        emitUiStateLocked()
                    }
                    scheduler.enqueueWhenOnline()
                    onProgress(_uiState.value)
                    break
                }
            }
        }

        val finalStatus = stateMutex.withLock { checkpoint.status }
        onProgress(_uiState.value)
        return finalStatus
    }

    private suspend fun recordNotFound(songId: Long, song: Song) {
        stateMutex.withLock {
            if (checkpoint.status != BulkLyricsEmbedStatus.RUNNING) return@withLock
            checkpoint = checkpoint.copy(
                nextIndex = checkpoint.nextIndex + 1,
                notFoundCount = checkpoint.notFoundCount + 1,
                failedSongIds = checkpoint.failedSongIds - songId,
                logs = appendLog(
                    checkpoint.logs,
                    BulkLyricsLogEntry(
                        songId = songId,
                        songTitle = song.title,
                        artist = song.displayArtist,
                        outcome = BulkLyricsLogOutcome.NOT_FOUND,
                        detail = if (checkpoint.options.syncedOnly) {
                            "No synced lyrics matched"
                        } else {
                            "No lyrics matched"
                        },
                    ),
                ),
            )
            currentSong = null
            saveCheckpointLocked()
            emitUiStateLocked()
        }
    }

    private suspend fun updateCurrentSong(
        songId: Long,
        title: String,
        artist: String,
        statusText: String,
        onProgress: suspend (BulkLyricsEmbedUiState) -> Unit,
    ) {
        stateMutex.withLock {
            currentSong = BulkLyricsCurrentSong(
                songId = songId,
                title = title,
                artist = artist,
                statusText = statusText,
            )
            emitUiStateLocked()
        }
        onProgress(_uiState.value)
    }

    private suspend fun hasExistingSyncedLyrics(songId: Long, entity: SongEntity): Boolean {
        if (isSyncedText(entity.lyrics)) return true
        if (isSyncedText(env.getStoredDbLyrics(songId))) return true
        return isSyncedText(env.readFileEmbeddedLyrics(entity.filePath))
    }

    private fun isSyncedText(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return LyricsUtils.parseLyrics(raw).synced?.isNotEmpty() == true
    }

    private fun canonicalLrcText(result: LyricsSearchResult): String {
        val rawTrimmed = result.rawLyrics.trim()
        val parsed = result.lyrics
        val formattedFromParsed = LyricsUtils.toLrcString(parsed, preferSynced = true).trim()
        // If raw text is already standard/enhanced LRC and not KuGou raw tags, keep canonical raw;
        // otherwise use LyricsUtils.toLrcString() so KuGou/TTML always embed as standard LRC.
        val isKugouRaw = rawTrimmed.startsWith("[") && Regex("^\\[\\d+,\\d+]").containsMatchIn(rawTrimmed)
        return when {
            isKugouRaw && formattedFromParsed.isNotBlank() -> formattedFromParsed
            rawTrimmed.isNotBlank() && !rawTrimmed.startsWith("<") -> rawTrimmed
            else -> formattedFromParsed
        }
    }

    private fun filterEditableLocalSongs(entities: List<SongEntity>): List<SongEntity> {
        return entities.filter { entity ->
            entity.id > 0L &&
                entity.sourceType == SourceType.LOCAL &&
                MediaStorePermissionHelper.canUseSongIdForMediaStoreRequest(entity.contentUriString) &&
                env.fileExists(entity.filePath)
        }
    }

    private fun appendLog(
        existing: List<BulkLyricsLogEntry>,
        entry: BulkLyricsLogEntry,
    ): List<BulkLyricsLogEntry> = (listOf(entry) + existing).take(MAX_LOG_ENTRIES)

    private fun saveCheckpointLocked() {
        runCatching {
            checkpointFile.parentFile?.mkdirs()
            val tmpFile = File(checkpointFile.parentFile, "${checkpointFile.name}.tmp")
            tmpFile.writeText(gson.toJson(checkpoint), Charsets.UTF_8)
            if (checkpointFile.exists()) {
                checkpointFile.delete()
            }
            if (!tmpFile.renameTo(checkpointFile)) {
                tmpFile.copyTo(checkpointFile, overwrite = true)
                tmpFile.delete()
            }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            Timber.tag(TAG).e(err, "Failed to persist bulk lyrics embed checkpoint")
        }
    }

    private fun emitUiStateLocked() {
        emitUiStateUnchecked()
    }

    private fun emitUiStateUnchecked() {
        _uiState.value = checkpoint.toUiState(
            priorityOrder = priorityOrder,
            enabledProviders = enabledProviders,
            currentSong = currentSong,
            isScanningLibrary = isScanningLibrary,
        )
    }

    @VisibleForTesting
    internal fun currentCheckpointForTest(): BulkLyricsEmbedCheckpoint = checkpoint

    companion object {
        private const val TAG = "BulkLyricsEmbedManager"
        private const val CHECKPOINT_FILE_NAME = "bulk_lyrics_embed_state.json"
        private const val MAX_LOG_ENTRIES = 80
        private const val DEFAULT_PERMISSION_BATCH_SIZE = 150
        internal const val DEFAULT_RATE_LIMIT_COOLDOWN_MS = 60_000L
        internal const val MIN_RATE_LIMIT_COOLDOWN_MS = 10_000L
        internal const val MAX_RATE_LIMIT_COOLDOWN_MS = 15 * 60_000L
    }
}
