package com.drliki.MusicLi.data.explore

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.drliki.MusicLi.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class ExploreStreamCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDir = File(context.cacheDir, "explore_stream_cache")
    private val cacheLock = Any()

    @Volatile
    private var configuredLimitMb: Int = 250

    @Volatile
    private var simpleCache: SimpleCache? = null

    init {
        scope.launch {
            val limitMb = userPreferencesRepository.exploreStreamCacheLimitMbFlow.first()
            val autoCleanDays = userPreferencesRepository.exploreStreamCacheAutoCleanDaysFlow.first()
            configuredLimitMb = limitMb
            if (limitMb > 0) {
                getOrCreateCache(limitMb)
                if (autoCleanDays > 0) {
                    pruneExpiredStreams(autoCleanDays)
                }
            }
            userPreferencesRepository.exploreStreamCacheLimitMbFlow.collect { updatedLimit ->
                if (updatedLimit != configuredLimitMb) {
                    reconfigureLimit(updatedLimit)
                }
            }
        }
    }

    fun getCacheDirectory(): File = cacheDir

    private fun getOrCreateCache(limitMb: Int): SimpleCache? {
        if (limitMb <= 0) return null
        return synchronized(cacheLock) {
            simpleCache?.let { return@synchronized it }
            runCatching {
                cacheDir.mkdirs()
                val maxBytes = limitMb.toLong() * 1024L * 1024L
                SimpleCache(
                    cacheDir,
                    LeastRecentlyUsedCacheEvictor(maxBytes),
                    StandaloneDatabaseProvider(context),
                ).also { simpleCache = it }
            }.onFailure { err ->
                Timber.tag("ExploreStreamCache").w(err, "Failed creating Explore SimpleCache")
            }.getOrNull()
        }
    }

    private fun reconfigureLimit(limitMb: Int) {
        synchronized(cacheLock) {
            configuredLimitMb = limitMb
            runCatching { simpleCache?.release() }
            simpleCache = null
            if (limitMb <= 0) {
                deleteDirectoryContents(cacheDir)
            } else {
                getOrCreateCache(limitMb)
            }
        }
    }

    fun createDataSourceFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        return DataSource.Factory {
            val direct = upstreamFactory.createDataSource()
            val cache = if (configuredLimitMb > 0) getOrCreateCache(configuredLimitMb) else null
            if (cache == null) {
                direct
            } else {
                val cachedFactory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                SelectiveHttpCacheDataSource(
                    directDataSource = direct,
                    cachedDataSource = cachedFactory.createDataSource(),
                )
            }
        }
    }

    fun pruneExpiredStreams(maxAgeDays: Int) {
        if (maxAgeDays <= 0) return
        val cache = synchronized(cacheLock) { simpleCache } ?: return
        val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
        runCatching {
            val keys = cache.keys.toList()
            for (key in keys) {
                val spans = cache.getCachedSpans(key)
                val newestTimestamp = spans.maxOfOrNull { it.lastTouchTimestamp } ?: Long.MAX_VALUE
                if (newestTimestamp < cutoffMs) {
                    cache.removeResource(key)
                }
            }
        }.onFailure { err ->
            Timber.tag("ExploreStreamCache").w(err, "Failed pruning expired stream cache entries")
        }
    }

    fun clearCache(): Long {
        val before = measureCacheBytes()
        synchronized(cacheLock) {
            val cache = simpleCache
            if (cache != null) {
                runCatching {
                    val keys = cache.keys.toList()
                    keys.forEach { key -> runCatching { cache.removeResource(key) } }
                }
                runCatching { cache.release() }
                simpleCache = null
            }
            deleteDirectoryContents(cacheDir)
            if (configuredLimitMb > 0) {
                getOrCreateCache(configuredLimitMb)
            }
        }
        return before
    }

    fun measureCacheBytes(): Long {
        if (!cacheDir.exists()) return 0L
        return cacheDir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".uid") && !it.name.endsWith("db") && !it.name.endsWith("db-wal") && !it.name.endsWith("db-shm") }
            .sumOf { it.length() }
    }

    fun countCachedEntries(): Int {
        val cache = synchronized(cacheLock) { simpleCache }
        if (cache != null) {
            return runCatching { cache.keys.size }.getOrDefault(0)
        }
        if (!cacheDir.exists()) return 0
        return cacheDir.walkTopDown()
            .count { it.isFile && it.name.endsWith(".exo") }
    }

    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }

    private class SelectiveHttpCacheDataSource(
        private val directDataSource: DataSource,
        private val cachedDataSource: DataSource,
    ) : DataSource {
        private var activeDataSource: DataSource = directDataSource
        private val transferListeners = mutableListOf<TransferListener>()

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            directDataSource.addTransferListener(transferListener)
            cachedDataSource.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme?.lowercase()
            activeDataSource = if (scheme == "http" || scheme == "https") {
                cachedDataSource
            } else {
                directDataSource
            }
            return activeDataSource.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeDataSource.read(buffer, offset, length)
        }

        override fun getUri(): Uri? = activeDataSource.uri

        override fun getResponseHeaders(): Map<String, List<String>> = activeDataSource.responseHeaders

        override fun close() {
            activeDataSource.close()
        }
    }
}
