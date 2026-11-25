package com.lostf1sh.pixelplayeross.data.explore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.google.gson.Gson
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.database.LyricsDao
import com.lostf1sh.pixelplayeross.data.database.LyricsEntity
import com.lostf1sh.pixelplayeross.data.network.lyrics.LrcLibApiService
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExploreDownloader"
private const val EXPLORE_CHANNEL_ID = "explore_downloads_channel"
private const val EXPLORE_NOTIFICATION_BASE_ID = 4100

enum class ExploreDownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

data class ExploreDownloadItem(
    val downloadId: String,
    val song: ExploreSong,
    val subfolder: String? = null,
    val status: ExploreDownloadStatus = ExploreDownloadStatus.QUEUED,
    val progress: Float = 0f,
    val statusMessage: String = "Queued",
    val errorMessage: String? = null,
    val outputUriString: String? = null,
    val bytesWritten: Long = 0L,
    val lrcSaved: Boolean = false,
)

@Singleton
class ExploreTrackDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exploreRepository: ExploreRepository,
    private val lrcLibApiService: LrcLibApiService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val lyricsDao: LyricsDao,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val persistLock = Any()
    private val persistenceFile = File(context.filesDir, "explore_downloads.json")

    private val _downloads = MutableStateFlow<List<ExploreDownloadItem>>(emptyList())
    val downloads: StateFlow<List<ExploreDownloadItem>> = _downloads.asStateFlow()

    private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

    init {
        scope.launch {
            loadPersistedDownloads()
            scanExistingDownloadedTitles()
        }
    }

    private suspend fun loadPersistedDownloads() {
        val loaded = runCatching {
            val json = synchronized(persistLock) {
                if (!persistenceFile.exists()) return@runCatching emptyList<ExploreDownloadItem>()
                persistenceFile.readText(Charsets.UTF_8)
            }
            if (json.isBlank()) return@runCatching emptyList<ExploreDownloadItem>()
            gson.fromJson(json, Array<PersistedExploreDownloadRecord>::class.java)
                ?.mapNotNull { record -> record.toExploreDownloadItem() }
                .orEmpty()
        }.getOrElse { err ->
            Timber.tag(TAG).w(err, "Failed loading persisted explore downloads")
            emptyList()
        }

        if (loaded.isEmpty()) return

        var mutated = false
        val reconciled = mutableListOf<ExploreDownloadItem>()
        val validIds = mutableSetOf<String>()

        for (item in loaded) {
            when (item.status) {
                ExploreDownloadStatus.QUEUED, ExploreDownloadStatus.DOWNLOADING -> {
                    mutated = true
                    reconciled += item.copy(
                        status = ExploreDownloadStatus.FAILED,
                        progress = 0f,
                        errorMessage = "Interrupted when app closed",
                        statusMessage = "Interrupted when app closed",
                    )
                }
                ExploreDownloadStatus.COMPLETED -> {
                    if (doesOutputExist(item)) {
                        reconciled += item
                        validIds += item.song.id
                    } else {
                        mutated = true
                    }
                }
                ExploreDownloadStatus.FAILED -> {
                    reconciled += item
                }
            }
        }

        _downloads.value = reconciled
        if (validIds.isNotEmpty()) {
            _downloadedSongIds.update { it + validIds }
        }
        if (mutated) {
            persistDownloadsSnapshot()
        }
    }

    private fun doesOutputExist(item: ExploreDownloadItem): Boolean {
        val uriStr = item.outputUriString
        if (uriStr.isNullOrBlank()) {
            return isTitleFilePresent(item.song.title)
        }
        return when {
            uriStr.startsWith("content://") -> runCatching {
                val uri = Uri.parse(uriStr)
                context.contentResolver.query(uri, null, null, null, null)?.use { it.moveToFirst() } == true
            }.getOrElse {
                runCatching {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(uriStr), "r")?.use { } != null
                }.getOrDefault(false)
            }
            uriStr.startsWith("file://") -> runCatching {
                Uri.parse(uriStr).path?.let { File(it).exists() } ?: false
            }.getOrDefault(false)
            else -> runCatching { File(uriStr).exists() }.getOrDefault(false)
        }
    }

    private fun isTitleFilePresent(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val safe = sanitizeFilename(title)
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "PixelPlayerOSS"
        )
        return File(musicDir, "$safe.m4a").exists()
    }

    private fun scanExistingDownloadedTitles() {
        val defaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "PixelPlayerOSS"
        )
        if (!defaultDir.exists()) return
        // Pre-populate known TrackUIDs and recover any pre-persistence downloaded files
        runCatching {
            val foundIds = mutableSetOf<String>()
            val existingIds = _downloads.value.map { it.song.id }.toMutableSet()
            val recoveredItems = mutableListOf<ExploreDownloadItem>()

            defaultDir.walkTopDown().maxDepth(3).forEach { file ->
                if (file.isFile && file.extension.equals("m4a", ignoreCase = true) && file.canRead()) {
                    runCatching {
                        val audioFile = AudioFileIO.read(file)
                        val tag = audioFile.tag
                        val uidField = tag?.getFirstField("----:${context.packageName}:TrackUID")
                        val uid = uidField?.toString()?.trim()
                        if (!uid.isNullOrBlank()) {
                            foundIds.add(uid)
                            if (existingIds.add(uid)) {
                                val title = tag?.getFirst(FieldKey.TITLE).orEmpty().ifBlank { file.nameWithoutExtension }
                                val artist = tag?.getFirst(FieldKey.ARTIST).orEmpty().ifBlank { "Unknown Artist" }
                                val album = tag?.getFirst(FieldKey.ALBUM).orEmpty()
                                val year = tag?.getFirst(FieldKey.YEAR)?.toIntOrNull() ?: 0
                                val track = tag?.getFirst(FieldKey.TRACK)?.toIntOrNull() ?: 0
                                val durationSec = runCatching { audioFile.audioHeader.trackLength }.getOrDefault(0)
                                val lrcSibling = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
                                recoveredItems += ExploreDownloadItem(
                                    downloadId = "${uid}_recovered",
                                    song = ExploreSong(
                                        id = uid,
                                        title = title,
                                        artist = artist,
                                        albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST).orEmpty().ifBlank { artist },
                                        albumId = "",
                                        albumName = album,
                                        year = year,
                                        releaseDate = null,
                                        durationSeconds = durationSec,
                                        language = null,
                                        label = null,
                                        copyright = null,
                                        hasLyrics = false,
                                        lyricsText = null,
                                        artworkUrl = null,
                                        thumbnailUrl = null,
                                        streamUrlsByQuality = emptyMap(),
                                        saavnUrl = null,
                                        trackNumber = track,
                                    ),
                                    status = ExploreDownloadStatus.COMPLETED,
                                    progress = 1f,
                                    statusMessage = "Saved in Music/PixelPlayerOSS",
                                    outputUriString = Uri.fromFile(file).toString(),
                                    bytesWritten = file.length(),
                                    lrcSaved = lrcSibling.exists(),
                                )
                            }
                        }
                    }
                }
            }
            if (foundIds.isNotEmpty()) {
                _downloadedSongIds.update { it + foundIds }
            }
            if (recoveredItems.isNotEmpty()) {
                _downloads.update { current ->
                    val currentIds = current.map { it.song.id }.toSet()
                    current + recoveredItems.filterNot { it.song.id in currentIds }
                }
                persistDownloadsSnapshot()
            }
        }
    }

    fun isDownloaded(saavnSongId: String, title: String? = null): Boolean {
        if (_downloadedSongIds.value.contains(saavnSongId)) return true
        if (_downloads.value.any { it.song.id == saavnSongId && it.status == ExploreDownloadStatus.COMPLETED }) {
            return true
        }
        if (!title.isNullOrBlank()) {
            val safe = sanitizeFilename(title)
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "PixelPlayerOSS"
            )
            if (File(musicDir, "$safe.m4a").exists()) return true
        }
        return false
    }

    fun enqueueSongDownload(song: ExploreSong, subfolder: String? = null) {
        val existingActive = _downloads.value.firstOrNull {
            it.song.id == song.id &&
                (it.status == ExploreDownloadStatus.QUEUED || it.status == ExploreDownloadStatus.DOWNLOADING)
        }
        if (existingActive != null) return

        val downloadId = "${song.id}_${System.currentTimeMillis()}"
        val item = ExploreDownloadItem(
            downloadId = downloadId,
            song = song,
            subfolder = subfolder,
            status = ExploreDownloadStatus.QUEUED,
            statusMessage = "Queued",
        )
        _downloads.update { list -> listOf(item) + list.filterNot { it.song.id == song.id } }
        persistDownloadsSnapshot()

        val job = scope.launch {
            executeDownload(downloadId, song, subfolder)
        }
        activeJobs[downloadId] = job
    }

    fun enqueueBatchDownload(collectionName: String, songs: List<ExploreSong>) {
        if (songs.isEmpty()) return
        scope.launch {
            val useSubfolders = userPreferencesRepository.exploreDownloadSubfoldersFlow.first()
            val folder = if (useSubfolders) sanitizeFilename(collectionName) else null
            songs.forEach { song ->
                if (!isDownloaded(song.id, song.title)) {
                    enqueueSongDownload(song, subfolder = folder)
                }
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        activeJobs.remove(downloadId)?.cancel()
        _downloads.update { list ->
            list.map {
                if (it.downloadId == downloadId) {
                    it.copy(
                        status = ExploreDownloadStatus.FAILED,
                        errorMessage = "Cancelled",
                        statusMessage = "Cancelled",
                    )
                } else it
            }
        }
        persistDownloadsSnapshot()
    }

    fun retryDownload(downloadId: String) {
        val item = _downloads.value.firstOrNull { it.downloadId == downloadId } ?: return
        enqueueSongDownload(item.song, item.subfolder)
    }

    fun removeDownloadEntry(downloadId: String) {
        activeJobs.remove(downloadId)?.cancel()
        val removed = _downloads.value.firstOrNull { it.downloadId == downloadId }
        _downloads.update { list -> list.filterNot { it.downloadId == downloadId } }
        if (removed != null) {
            _downloadedSongIds.update { it - removed.song.id }
        }
        persistDownloadsSnapshot()
    }

    fun clearCompleted() {
        _downloads.update { list -> list.filterNot { it.status == ExploreDownloadStatus.COMPLETED } }
        persistDownloadsSnapshot()
    }

    private suspend fun executeDownload(downloadId: String, initialSong: ExploreSong, subfolder: String?) {
        val notifId = EXPLORE_NOTIFICATION_BASE_ID + (downloadId.hashCode() and 0x7FFF)
        val tempAudio = File(context.cacheDir, "explore_${initialSong.id}_${System.currentTimeMillis()}.m4a")
        val tempArtwork = File(context.cacheDir, "explore_art_${initialSong.id}_${System.currentTimeMillis()}.jpg")

        try {
            updateItem(downloadId) {
                it.copy(status = ExploreDownloadStatus.DOWNLOADING, progress = 0.05f, statusMessage = "Resolving stream…")
            }

            val resolvedSong = if (initialSong.streamUrlsByQuality.isEmpty()) {
                exploreRepository.resolveSong(initialSong.id, includeLyrics = true).getOrElse { initialSong }
            } else {
                initialSong
            }

            val preferredKbps = userPreferencesRepository.exploreDownloadQualityFlow.first()
            val streamUrl = resolvedSong.selectStreamUrl(preferredKbps)
                ?: exploreRepository.resolveSong(resolvedSong.id, includeLyrics = true).getOrNull()?.selectStreamUrl(preferredKbps)
                ?: throw IllegalStateException("No audio stream URL available for ${resolvedSong.title}")

            showProgressNotification(notifId, resolvedSong.title, "Downloading audio…", 10, false)
            updateItem(downloadId) {
                it.copy(song = resolvedSong, progress = 0.1f, statusMessage = "Downloading audio…")
            }

            // 1. Download AAC/M4A stream
            val audioBytes = downloadUrlToFile(streamUrl, tempAudio) { written, total ->
                if (total > 0L) {
                    val frac = 0.1f + (written.toFloat() / total.toFloat()) * 0.55f
                    updateItem(downloadId) { it.copy(progress = frac, bytesWritten = written) }
                }
            }

            // 2. Fetch synchronized lyrics (LRCLIB first, then JioSaavn plain lyrics fallback)
            val includeLyrics = userPreferencesRepository.exploreDownloadLyricsFlow.first()
            var lyricsContent: String? = null
            if (includeLyrics) {
                updateItem(downloadId) { it.copy(progress = 0.70f, statusMessage = "Fetching synced lyrics…") }
                showProgressNotification(notifId, resolvedSong.title, "Fetching synced lyrics…", 70, false)
                lyricsContent = fetchSyncedOrPlainLyrics(resolvedSong)
            }

            // 3. Download high-res cover artwork
            val artworkUrl = resolvedSong.artworkUrl ?: resolvedSong.thumbnailUrl
            if (!artworkUrl.isNullOrBlank()) {
                updateItem(downloadId) { it.copy(progress = 0.80f, statusMessage = "Downloading artwork…") }
                runCatching { downloadUrlToFile(artworkUrl, tempArtwork) }
            }

            // 4. Embed full M4A metadata via JAudioTagger
            updateItem(downloadId) { it.copy(progress = 0.88f, statusMessage = "Embedding metadata…") }
            showProgressNotification(notifId, resolvedSong.title, "Embedding metadata…", 88, false)
            embedM4aTags(
                file = tempAudio,
                song = resolvedSong,
                artworkFile = tempArtwork.takeIf { it.exists() && it.length() > 0L },
                lyrics = lyricsContent,
            )

            // 5. Save to internal storage (SAF tree URI if set, otherwise MediaStore Music/PixelPlayerOSS)
            updateItem(downloadId) { it.copy(progress = 0.94f, statusMessage = "Saving to storage…") }
            val safeTitle = sanitizeFilename(resolvedSong.title)
            val safUriString = userPreferencesRepository.exploreDownloadSafUriFlow.first()
            val savedResult = if (!safUriString.isNullOrBlank()) {
                saveToSafTree(
                    treeUri = Uri.parse(safUriString),
                    subfolder = subfolder,
                    safeTitle = safeTitle,
                    audioFile = tempAudio,
                    lyrics = lyricsContent,
                )
            } else {
                saveToDefaultMediaStore(
                    subfolder = subfolder,
                    safeTitle = safeTitle,
                    song = resolvedSong,
                    audioFile = tempAudio,
                    lyrics = lyricsContent,
                )
            }

            // Cache lyrics in Room if MediaStore ID is available
            if (!lyricsContent.isNullOrBlank() && savedResult.mediaStoreId != null) {
                runCatching {
                    lyricsDao.insert(LyricsEntity(songId = savedResult.mediaStoreId, content = lyricsContent))
                }
            }

            // Trigger MediaScanner + Library sync so downloaded track appears cleanly in Library
            savedResult.scannedPath?.let { path ->
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/mp4"), null)
            }
            runCatching {
                WorkManager.getInstance(context).enqueue(SyncWorker.incrementalSyncWork())
            }

            _downloadedSongIds.update { it + resolvedSong.id }
            updateItem(downloadId) {
                it.copy(
                    status = ExploreDownloadStatus.COMPLETED,
                    progress = 1f,
                    statusMessage = "Saved to ${savedResult.displayFolder}",
                    outputUriString = savedResult.uriString,
                    bytesWritten = audioBytes,
                    lrcSaved = savedResult.lrcSaved,
                )
            }
            showCompletedNotification(notifId, resolvedSong.title, "Saved to ${savedResult.displayFolder}")
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed download for ${initialSong.title}")
            updateItem(downloadId) {
                it.copy(
                    status = ExploreDownloadStatus.FAILED,
                    errorMessage = e.message ?: "Download failed",
                    statusMessage = e.message ?: "Download failed",
                )
            }
            showFailedNotification(notifId, initialSong.title, e.message ?: "Download failed")
        } finally {
            activeJobs.remove(downloadId)
            runCatching { if (tempAudio.exists()) tempAudio.delete() }
            runCatching { if (tempArtwork.exists()) tempArtwork.delete() }
        }
    }

    private fun downloadUrlToFile(
        url: String,
        dest: File,
        onBytes: ((Long, Long) -> Unit)? = null,
    ): Long {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} downloading asset")
            }
            val body = response.body
            val total = body.contentLength()
            var written = 0L
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        onBytes?.invoke(written, total)
                    }
                    output.flush()
                }
            }
            return written
        }
    }

    private suspend fun fetchSyncedOrPlainLyrics(song: ExploreSong): String? {
        val cleanTitle = song.title.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim().ifBlank { song.title }
        val primaryArtist = song.artist.substringBefore(",").trim().ifBlank { song.artist }

        // 1. Exact get on LRCLIB
        val exact = runCatching {
            lrcLibApiService.getLyrics(
                trackName = cleanTitle,
                artistName = primaryArtist,
                albumName = song.albumName,
                duration = song.durationSeconds,
            )
        }.getOrNull()
        exact?.takeIf { it.hasSyncedContent }?.preferredLyricsContent()?.let { return it }

        // 2. Search LRCLIB by track + artist
        val searchHits = runCatching {
            lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = primaryArtist)
        }.getOrNull().orEmpty()
        searchHits.firstOrNull { it.hasSyncedContent }?.preferredLyricsContent()?.let { return it }

        // 3. Broad query on LRCLIB
        val broadHits = runCatching {
            lrcLibApiService.searchLyrics(query = "$primaryArtist $cleanTitle")
        }.getOrNull().orEmpty()
        broadHits.firstOrNull { it.hasSyncedContent }?.preferredLyricsContent()?.let { return it }

        // 4. Plain LRCLIB or JioSaavn lyrics fallback
        return exact?.preferredLyricsContent()
            ?: searchHits.firstOrNull()?.preferredLyricsContent()
            ?: song.lyricsText?.takeIf { it.isNotBlank() }
    }

    private fun embedM4aTags(
        file: File,
        song: ExploreSong,
        artworkFile: File?,
        lyrics: String?,
    ) {
        runCatching {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, song.title)
            tag.setField(FieldKey.ARTIST, song.artist)
            if (song.albumArtist.isNotBlank()) tag.setField(FieldKey.ALBUM_ARTIST, song.albumArtist)
            if (song.albumName.isNotBlank()) tag.setField(FieldKey.ALBUM, song.albumName)
            if (song.year > 0) tag.setField(FieldKey.YEAR, song.year.toString())
            if (song.trackNumber > 0) tag.setField(FieldKey.TRACK, song.trackNumber.toString())
            if (!song.language.isNullOrBlank()) tag.setField(FieldKey.LANGUAGE, song.language)
            if (!song.label.isNullOrBlank()) tag.setField(FieldKey.RECORD_LABEL, song.label)
            if (!song.copyright.isNullOrBlank()) tag.setField(FieldKey.COPYRIGHT, song.copyright)
            if (!lyrics.isNullOrBlank()) tag.setField(FieldKey.LYRICS, lyrics)
            if (song.id.isNotBlank()) {
                tag.setField(Mp4TagReverseDnsField("----", context.packageName, "TrackUID", song.id))
            }
            if (artworkFile != null && artworkFile.exists()) {
                val artwork = ArtworkFactory.createArtworkFromFile(artworkFile)
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            audioFile.tag = tag
            audioFile.commit()
        }.onFailure { err ->
            Timber.tag(TAG).w(err, "JAudioTagger metadata embed failed; saving raw M4A")
        }
    }

    private data class SavedFileResult(
        val uriString: String,
        val displayFolder: String,
        val scannedPath: String? = null,
        val mediaStoreId: Long? = null,
        val lrcSaved: Boolean = false,
    )

    private fun saveToDefaultMediaStore(
        subfolder: String?,
        safeTitle: String,
        song: ExploreSong,
        audioFile: File,
        lyrics: String?,
    ): SavedFileResult {
        val folderSuffix = if (!subfolder.isNullOrBlank()) {
            "PixelPlayerOSS/${sanitizeFilename(subfolder)}"
        } else {
            "PixelPlayerOSS"
        }
        val relativeAudioDir = "${Environment.DIRECTORY_MUSIC}/$folderSuffix/"

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            put(MediaStore.Audio.Media.ALBUM, song.albumName.ifBlank { song.title })
            if (song.year > 0) put(MediaStore.Audio.Media.YEAR, song.year)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativeAudioDir)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    folderSuffix
                )
                targetDir.mkdirs()
                put(MediaStore.Audio.Media.DATA, File(targetDir, "$safeTitle.m4a").absolutePath)
            }
        }

        val resolver = context.contentResolver
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val audioUri = resolver.insert(audioCollection, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(audioUri)?.use { out ->
            audioFile.inputStream().use { inp -> inp.copyTo(out) }
        } ?: throw IllegalStateException("Failed to open MediaStore output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val publish = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(audioUri, publish, null, null)
        }

        val mediaStoreId = audioUri.lastPathSegment?.toLongOrNull()
        val scannedPath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), folderSuffix),
                "$safeTitle.m4a"
            ).absolutePath
        } else null

        // Save standalone .lrc file
        var lrcSaved = false
        if (!lyrics.isNullOrBlank()) {
            lrcSaved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveLrcToMediaStoreDocuments(safeTitle, lyrics)
            } else {
                runCatching {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        folderSuffix
                    )
                    dir.mkdirs()
                    File(dir, "$safeTitle.lrc").writeText(lyrics, Charsets.UTF_8)
                    true
                }.getOrDefault(false)
            }
        }

        return SavedFileResult(
            uriString = audioUri.toString(),
            displayFolder = relativeAudioDir.trimEnd('/'),
            scannedPath = scannedPath,
            mediaStoreId = mediaStoreId,
            lrcSaved = lrcSaved,
        )
    }

    private fun saveLrcToMediaStoreDocuments(safeTitle: String, lyrics: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = context.contentResolver
            val docsUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val lrcValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$safeTitle.lrc")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/PixelPlayerOSS/Lyrics/")
            }
            val uri = resolver.insert(docsUri, lrcValues) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { out ->
                out.write(lyrics.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    private fun saveToSafTree(
        treeUri: Uri,
        subfolder: String?,
        safeTitle: String,
        audioFile: File,
        lyrics: String?,
    ): SavedFileResult {
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        var targetParentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

        if (!subfolder.isNullOrBlank()) {
            val safeSub = sanitizeFilename(subfolder)
            targetParentUri = findOrCreateSafDirectory(treeUri, rootDocId, safeSub) ?: targetParentUri
        }

        val audioDocUri = DocumentsContract.createDocument(
            resolver,
            targetParentUri,
            "audio/mp4",
            "$safeTitle.m4a",
        ) ?: throw IllegalStateException("Could not create .m4a file in selected SAF directory")

        resolver.openOutputStream(audioDocUri)?.use { out ->
            audioFile.inputStream().use { inp -> inp.copyTo(out) }
        } ?: throw IllegalStateException("Could not write .m4a file in selected SAF directory")

        var lrcSaved = false
        if (!lyrics.isNullOrBlank()) {
            lrcSaved = runCatching {
                val lrcDocUri = DocumentsContract.createDocument(
                    resolver,
                    targetParentUri,
                    "application/octet-stream",
                    "$safeTitle.lrc",
                ) ?: return@runCatching false
                resolver.openOutputStream(lrcDocUri)?.use { out ->
                    out.write(lyrics.toByteArray(Charsets.UTF_8))
                }
                true
            }.getOrDefault(false)
        }

        val folderLabel = buildString {
            append(treeUri.lastPathSegment ?: "Custom Folder")
            if (!subfolder.isNullOrBlank()) append("/${sanitizeFilename(subfolder)}")
        }
        return SavedFileResult(
            uriString = audioDocUri.toString(),
            displayFolder = folderLabel,
            lrcSaved = lrcSaved,
        )
    }

    private fun findOrCreateSafDirectory(treeUri: Uri, parentDocId: String, folderName: String): Uri? {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)
                    val mime = cursor.getString(mimeIdx)
                    if (name == folderName && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val docId = cursor.getString(idIdx)
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
            }
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        return runCatching {
            DocumentsContract.createDocument(
                resolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                folderName,
            )
        }.getOrNull()
    }

    private fun updateItem(downloadId: String, transform: (ExploreDownloadItem) -> ExploreDownloadItem) {
        var updatedStatus: ExploreDownloadStatus? = null
        _downloads.update { list ->
            list.map { item ->
                if (item.downloadId == downloadId) {
                    transform(item).also { updatedStatus = it.status }
                } else {
                    item
                }
            }
        }
        if (updatedStatus == ExploreDownloadStatus.COMPLETED || updatedStatus == ExploreDownloadStatus.FAILED) {
            persistDownloadsSnapshot()
        }
    }

    private fun persistDownloadsSnapshot() {
        val snapshot = _downloads.value.map { PersistedExploreDownloadRecord.fromItem(it) }
        runCatching {
            val payload = gson.toJson(snapshot)
            synchronized(persistLock) {
                persistenceFile.parentFile?.mkdirs()
                val tmp = File(persistenceFile.parentFile, "explore_downloads.json.tmp")
                tmp.writeText(payload, Charsets.UTF_8)
                if (!tmp.renameTo(persistenceFile)) {
                    persistenceFile.writeText(payload, Charsets.UTF_8)
                    tmp.delete()
                }
            }
        }.onFailure { err ->
            Timber.tag(TAG).w(err, "Failed persisting explore downloads snapshot")
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(EXPLORE_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    EXPLORE_CHANNEL_ID,
                    "Explore Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "JioSaavn track download progress and completion"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun showProgressNotification(notifId: Int, title: String, content: String, progress: Int, indeterminate: Boolean) {
        ensureNotificationChannel()
        val builder = NotificationCompat.Builder(context, EXPLORE_CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { manager?.notify(notifId, builder.build()) }
    }

    private fun showCompletedNotification(notifId: Int, title: String, content: String) {
        ensureNotificationChannel()
        val builder = NotificationCompat.Builder(context, EXPLORE_CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setContentTitle("Downloaded: $title")
            .setContentText(content)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { manager?.notify(notifId, builder.build()) }
    }

    private fun showFailedNotification(notifId: Int, title: String, content: String) {
        ensureNotificationChannel()
        val builder = NotificationCompat.Builder(context, EXPLORE_CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setContentTitle("Download failed: $title")
            .setContentText(content)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { manager?.notify(notifId, builder.build()) }
    }

    companion object {
        fun sanitizeFilename(name: String): String {
            val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .trim { it == '.' || it.isWhitespace() }
            return if (cleaned.isBlank()) "Track" else cleaned
        }
    }
}

@Keep
private data class PersistedExploreDownloadRecord(
    val downloadId: String = "",
    val song: PersistedExploreSong? = null,
    val subfolder: String? = null,
    val status: String = ExploreDownloadStatus.FAILED.name,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val outputUriString: String? = null,
    val bytesWritten: Long = 0L,
    val lrcSaved: Boolean = false,
) {
    fun toExploreDownloadItem(): ExploreDownloadItem? {
        val mappedSong = song?.toExploreSong() ?: return null
        if (downloadId.isBlank()) return null
        val mappedStatus = runCatching { ExploreDownloadStatus.valueOf(status) }
            .getOrDefault(ExploreDownloadStatus.FAILED)
        return ExploreDownloadItem(
            downloadId = downloadId,
            song = mappedSong,
            subfolder = subfolder,
            status = mappedStatus,
            progress = progress,
            statusMessage = statusMessage,
            errorMessage = errorMessage,
            outputUriString = outputUriString,
            bytesWritten = bytesWritten,
            lrcSaved = lrcSaved,
        )
    }

    companion object {
        fun fromItem(item: ExploreDownloadItem): PersistedExploreDownloadRecord =
            PersistedExploreDownloadRecord(
                downloadId = item.downloadId,
                song = PersistedExploreSong.fromSong(item.song),
                subfolder = item.subfolder,
                status = item.status.name,
                progress = item.progress,
                statusMessage = item.statusMessage,
                errorMessage = item.errorMessage,
                outputUriString = item.outputUriString,
                bytesWritten = item.bytesWritten,
                lrcSaved = item.lrcSaved,
            )
    }
}

@Keep
private data class PersistedExploreSong(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val albumArtist: String = "",
    val albumId: String = "",
    val albumName: String = "",
    val year: Int = 0,
    val releaseDate: String? = null,
    val durationSeconds: Int = 0,
    val language: String? = null,
    val label: String? = null,
    val copyright: String? = null,
    val hasLyrics: Boolean = false,
    val lyricsText: String? = null,
    val artworkUrl: String? = null,
    val thumbnailUrl: String? = null,
    val streamUrlsByQuality: Map<Int, String> = emptyMap(),
    val saavnUrl: String? = null,
    val explicitContent: Boolean = false,
    val trackNumber: Int = 0,
) {
    fun toExploreSong(): ExploreSong? {
        if (id.isBlank()) return null
        return ExploreSong(
            id = id,
            title = title,
            artist = artist,
            albumArtist = albumArtist,
            albumId = albumId,
            albumName = albumName,
            year = year,
            releaseDate = releaseDate,
            durationSeconds = durationSeconds,
            language = language,
            label = label,
            copyright = copyright,
            hasLyrics = hasLyrics,
            lyricsText = lyricsText,
            artworkUrl = artworkUrl,
            thumbnailUrl = thumbnailUrl,
            streamUrlsByQuality = streamUrlsByQuality,
            saavnUrl = saavnUrl,
            explicitContent = explicitContent,
            trackNumber = trackNumber,
        )
    }

    companion object {
        fun fromSong(song: ExploreSong): PersistedExploreSong =
            PersistedExploreSong(
                id = song.id,
                title = song.title,
                artist = song.artist,
                albumArtist = song.albumArtist,
                albumId = song.albumId,
                albumName = song.albumName,
                year = song.year,
                releaseDate = song.releaseDate,
                durationSeconds = song.durationSeconds,
                language = song.language,
                label = song.label,
                copyright = song.copyright,
                hasLyrics = song.hasLyrics,
                lyricsText = song.lyricsText,
                artworkUrl = song.artworkUrl,
                thumbnailUrl = song.thumbnailUrl,
                streamUrlsByQuality = song.streamUrlsByQuality,
                saavnUrl = song.saavnUrl,
                explicitContent = song.explicitContent,
                trackNumber = song.trackNumber,
            )
    }
}
