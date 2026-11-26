package com.drliki.MusicLi.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "synced_videos",
    indices = [
        Index("video_id"),
    ]
)
data class SyncedVideoEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_key")
    @SerializedName(value = "songKey", alternate = ["song_key"])
    val songKey: String,

    @ColumnInfo(name = "title")
    @SerializedName("title")
    val title: String,

    @ColumnInfo(name = "artist")
    @SerializedName("artist")
    val artist: String,

    @ColumnInfo(name = "video_id")
    @SerializedName(value = "videoId", alternate = ["video_id"])
    val videoId: String,

    @ColumnInfo(name = "video_title")
    @SerializedName(value = "videoTitle", alternate = ["video_title"])
    val videoTitle: String? = null,

    @ColumnInfo(name = "video_channel")
    @SerializedName(value = "videoChannel", alternate = ["video_channel"])
    val videoChannel: String? = null,

    @ColumnInfo(name = "duration_seconds")
    @SerializedName(value = "durationSeconds", alternate = ["duration_seconds"])
    val durationSeconds: Int? = null,

    @ColumnInfo(name = "is_manual_override")
    @SerializedName(value = "isManualOverride", alternate = ["is_manual_override"])
    val isManualOverride: Boolean = false,

    @ColumnInfo(name = "updated_at_ms")
    @SerializedName(value = "updatedAtMs", alternate = ["updated_at_ms"])
    val updatedAtMs: Long = System.currentTimeMillis(),
)
