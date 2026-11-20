package com.lostf1sh.pixelplayeross.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lostf1sh.pixelplayeross.data.backup.model.BackupSection
import com.lostf1sh.pixelplayeross.data.database.SyncedVideoDao
import com.lostf1sh.pixelplayeross.data.database.SyncedVideoEntity
import com.lostf1sh.pixelplayeross.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncedVideosModuleHandler @Inject constructor(
    private val syncedVideoDao: SyncedVideoDao,
    @BackupGson private val gson: Gson,
) : BackupModuleHandler {

    override val section = BackupSection.SYNCED_VIDEOS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(syncedVideoDao.getAll())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        syncedVideoDao.getAll().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, SyncedVideoEntity::class.java).type
        val videos: List<SyncedVideoEntity> = gson.fromJson(payload, type) ?: emptyList()
        syncedVideoDao.replaceAll(videos)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)
}
