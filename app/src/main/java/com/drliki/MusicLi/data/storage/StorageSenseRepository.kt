package com.drliki.MusicLi.data.storage

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import com.drliki.MusicLi.data.explore.ExploreDownloadStatus
import com.drliki.MusicLi.data.explore.ExploreRepository
import com.drliki.MusicLi.data.explore.ExploreStreamCacheManager
import com.drliki.MusicLi.data.explore.ExploreTrackDownloader
import com.drliki.MusicLi.data.repository.LyricsRepository
import com.drliki.MusicLi.data.service.player.DualPlayerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageSenseBreakdown(
    val exploreStreamCacheBytes: Long = 0L,
    val exploreStreamCachedItems: Int = 0,
    val imageCacheBytes: Long = 0L,
    val tempAudioCacheBytes: Long = 0L,
    val lyricsCacheBytes: Long = 0L,
    val lyricsCacheCount: Int = 0,
    val exploreDownloadsBytes: Long = 0L,
    val exploreDownloadsCount: Int = 0,
) {
    val totalSafeCacheBytes: Long
        get() = exploreStreamCacheBytes + imageCacheBytes + tempAudioCacheBytes

    val totalManagedCacheBytes: Long
        get() = totalSafeCacheBytes + lyricsCacheBytes
}

@Singleton
class StorageSenseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exploreStreamCacheManager: ExploreStreamCacheManager,
    private val exploreRepository: ExploreRepository,
    private val exploreTrackDownloader: ExploreTrackDownloader,
    private val dualPlayerEngine: DualPlayerEngine,
    private val lyricsRepository: LyricsRepository,
    private val imageLoader: ImageLoader,
) {
    suspend fun inspectStorage(): StorageSenseBreakdown = withContext(Dispatchers.IO) {
        val streamBytes = exploreStreamCacheManager.measureCacheBytes()
        val streamItems = exploreStreamCacheManager.countCachedEntries()

        val imageDir = File(context.cacheDir, "image_cache")
        val externalArtworkDir = File(context.cacheDir, "external_artwork")
        val imageBytes = folderSize(imageDir) + folderSize(externalArtworkDir)

        val externalAudioDir = File(context.cacheDir, "external_audio")
        val strayExploreTemps = context.cacheDir.listFiles()
            ?.filter { it.isFile && (it.name.startsWith("explore_") || it.name.endsWith(".tmp")) }
            ?.sumOf { it.length() }
            ?: 0L
        val tempAudioBytes = folderSize(externalAudioDir) + strayExploreTemps

        val lyricsDir = File(context.filesDir, "lyrics")
        val lyricsFiles = lyricsDir.listFiles()?.filter { it.isFile }.orEmpty()
        val lyricsBytes = lyricsFiles.sumOf { it.length() }

        val completedDownloads = exploreTrackDownloader.downloads.value
            .filter { it.status == ExploreDownloadStatus.COMPLETED }
        val downloadsBytes = completedDownloads.sumOf { it.bytesWritten }

        StorageSenseBreakdown(
            exploreStreamCacheBytes = streamBytes,
            exploreStreamCachedItems = streamItems,
            imageCacheBytes = imageBytes,
            tempAudioCacheBytes = tempAudioBytes,
            lyricsCacheBytes = lyricsBytes,
            lyricsCacheCount = lyricsFiles.size,
            exploreDownloadsBytes = downloadsBytes,
            exploreDownloadsCount = completedDownloads.size,
        )
    }

    suspend fun clearExploreStreamCache() = withContext(Dispatchers.IO) {
        exploreStreamCacheManager.clearCache()
        exploreRepository.clearFeedCache()
        dualPlayerEngine.clearResolvedUriCache()
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearImageCache() = withContext(Dispatchers.IO) {
        runCatching { imageLoader.memoryCache?.clear() }
        runCatching { imageLoader.diskCache?.clear() }
        deleteDirContents(File(context.cacheDir, "image_cache"))
        deleteDirContents(File(context.cacheDir, "external_artwork"))
    }

    suspend fun clearTempAudioCache() = withContext(Dispatchers.IO) {
        deleteDirContents(File(context.cacheDir, "external_audio"))
        context.cacheDir.listFiles()
            ?.filter { it.isFile && (it.name.startsWith("explore_") || it.name.endsWith(".tmp")) }
            ?.forEach { runCatching { it.delete() } }
    }

    suspend fun clearLyricsCache() = withContext(Dispatchers.IO) {
        lyricsRepository.resetAllLyrics()
    }

    suspend fun clearAllSafeCaches() = withContext(Dispatchers.IO) {
        clearExploreStreamCache()
        clearImageCache()
        clearTempAudioCache()
    }

    private fun folderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun deleteDirContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }
}
