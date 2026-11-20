package com.lostf1sh.pixelplayeross.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncedVideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SyncedVideoEntity>)

    @Query("SELECT * FROM synced_videos WHERE song_key = :songKey")
    suspend fun getByKey(songKey: String): SyncedVideoEntity?

    @Query("SELECT * FROM synced_videos")
    suspend fun getAll(): List<SyncedVideoEntity>

    @Query("SELECT * FROM synced_videos")
    fun observeAll(): Flow<List<SyncedVideoEntity>>

    @Query("DELETE FROM synced_videos WHERE song_key = :songKey")
    suspend fun deleteByKey(songKey: String)

    @Query("DELETE FROM synced_videos")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<SyncedVideoEntity>) {
        deleteAll()
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }
}
