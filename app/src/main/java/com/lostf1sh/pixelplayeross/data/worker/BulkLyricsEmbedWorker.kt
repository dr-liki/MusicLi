package com.lostf1sh.pixelplayeross.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lostf1sh.pixelplayeross.MainActivity
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.lyrics.bulk.BulkLyricsEmbedManager
import com.lostf1sh.pixelplayeross.data.lyrics.bulk.BulkLyricsEmbedStatus
import com.lostf1sh.pixelplayeross.data.lyrics.bulk.BulkLyricsEmbedUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@HiltWorker
class BulkLyricsEmbedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bulkLyricsEmbedManager: BulkLyricsEmbedManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        ensureNotificationChannel()

        // If WorkManager woke up from a delayed rate-limit or network wait, flip checkpoint back
        // to RUNNING in-place without calling resume() (which would REPLACE this worker).
        if (!bulkLyricsEmbedManager.prepareResumeFromWorker()) {
            return Result.success()
        }

        val stateBeforeLoop = bulkLyricsEmbedManager.uiState.value
        if (stateBeforeLoop.status != BulkLyricsEmbedStatus.RUNNING) {
            return Result.success()
        }

        runCatching {
            setForeground(createForegroundInfo(stateBeforeLoop))
        }.onFailure { err ->
            Timber.tag(TAG).w(err, "Unable to promote BulkLyricsEmbedWorker to foreground")
        }

        return try {
            val endStatus = bulkLyricsEmbedManager.processQueueLoop { progressState ->
                if (progressState.status == BulkLyricsEmbedStatus.RUNNING) {
                    runCatching {
                        setForegroundAsync(createForegroundInfo(progressState))
                    }
                }
            }

            val finalState = bulkLyricsEmbedManager.uiState.value
            when (endStatus) {
                BulkLyricsEmbedStatus.COMPLETED -> postStatusNotification(
                    title = applicationContext.getString(R.string.bulk_lyrics_notif_completed_title),
                    content = applicationContext.getString(
                        R.string.bulk_lyrics_notif_completed_body,
                        finalState.embeddedCount,
                        finalState.skippedCount,
                        finalState.notFoundCount,
                        finalState.failedCount,
                    ),
                )

                BulkLyricsEmbedStatus.PAUSED_RATE_LIMIT -> postStatusNotification(
                    title = applicationContext.getString(R.string.bulk_lyrics_notif_rate_limit_title),
                    content = finalState.errorMessage
                        ?: applicationContext.getString(R.string.bulk_lyrics_notif_rate_limit_body),
                )

                BulkLyricsEmbedStatus.NEEDS_PERMISSION -> postStatusNotification(
                    title = applicationContext.getString(R.string.bulk_lyrics_notif_permission_title),
                    content = applicationContext.getString(R.string.bulk_lyrics_notif_permission_body),
                )

                else -> Unit
            }

            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "BulkLyricsEmbedWorker failed unexpectedly")
            Result.failure()
        }
    }

    private fun createForegroundInfo(state: BulkLyricsEmbedUiState): ForegroundInfo {
        val total = state.totalCount.coerceAtLeast(1)
        val processed = state.processedCount.coerceIn(0, total)
        val currentTitle = state.currentSong?.title
            ?: applicationContext.getString(R.string.bulk_lyrics_status_preparing)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setContentTitle(applicationContext.getString(R.string.bulk_lyrics_notif_progress_title))
            .setContentText("$processed / ${state.totalCount} • $currentTitle")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(state.totalCount, processed, state.totalCount == 0)
            .setContentIntent(createLaunchPendingIntent())
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun postStatusNotification(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(createLaunchPendingIntent())
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { manager?.notify(STATUS_NOTIFICATION_ID, notification) }
    }

    private fun createLaunchPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            PROGRESS_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.bulk_lyrics_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = applicationContext.getString(R.string.bulk_lyrics_notif_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "BulkLyricsEmbedWorker"
        private const val CHANNEL_ID = "bulk_lyrics_embed"
        private const val PROGRESS_NOTIFICATION_ID = 8402
        private const val STATUS_NOTIFICATION_ID = 8403
    }
}
