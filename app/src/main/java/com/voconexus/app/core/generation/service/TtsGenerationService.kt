package com.voconexus.app.core.generation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voconexus.app.MainActivity
import com.voconexus.app.VocoNexusApplication
import com.voconexus.app.core.data.db.GenerationJobStatus
import com.voconexus.app.core.generation.engine.GenerationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TtsGenerationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var activeJobId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val jobId = intent.getStringExtra(EXTRA_JOB_ID)

        val app = application as VocoNexusApplication
        val coordinator = app.container.generationCoordinator

        when (action) {
            ACTION_START -> {
                if (!jobId.isNullOrEmpty()) {
                    activeJobId = jobId
                    startForeground(NOTIFICATION_ID, buildNotification(this, "Starting generation...", 0, 0))
                    serviceScope.launch {
                        coordinator.executeJob(jobId)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_PAUSE -> {
                jobId?.let { id ->
                    serviceScope.launch { coordinator.requestPause(id) }
                }
            }
            ACTION_STOP -> {
                jobId?.let { id ->
                    serviceScope.launch { coordinator.requestStop(id) }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VocoNexus TTS Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of offline text-to-speech audio generation"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "voconexus_generation_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START = "com.voconexus.app.action.START_GENERATION"
        const val ACTION_PAUSE = "com.voconexus.app.action.PAUSE_GENERATION"
        const val ACTION_STOP = "com.voconexus.app.action.STOP_GENERATION"

        const val EXTRA_JOB_ID = "extra_job_id"

        fun startService(context: Context, jobId: String) {
            val intent = Intent(context, TtsGenerationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_JOB_ID, jobId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun buildNotification(
            context: Context,
            progressText: String,
            completed: Int,
            total: Int
        ): Notification {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingOpenIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("VocoNexus Audio Generation")
                .setContentText(progressText)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingOpenIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(total, completed, total == 0)
                .build()
        }
    }
}
