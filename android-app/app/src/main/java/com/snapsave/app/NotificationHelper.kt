package com.snapsave.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_PROGRESS = "progress"
        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002
        const val NOTIFICATION_ID_ERROR = 1003

        private var instance: NotificationHelper? = null

        fun init(context: Context) {
            instance = NotificationHelper(context.applicationContext)
            instance?.createNotificationChannels()
        }

        fun showDownloadComplete(context: Context, filename: String, filePath: String) {
            instance?.showCompleteNotification(filename, filePath)
        }

        fun showDownloadError(context: Context, filename: String, error: String) {
            instance?.showErrorNotification(filename, error)
        }

        fun showProgressNotification(context: Context, filename: String, progress: Int, speed: String) {
            instance?.showProgressNotificationInternal(filename, progress, speed)
        }

        fun cancelProgressNotification() {
            instance?.cancelProgress()
        }
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download completion notifications"
                setShowBadge(true)
            }

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                setShowBadge(false)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(downloadChannel)
            notificationManager.createNotificationChannel(progressChannel)
        }
    }

    private fun showCompleteNotification(filename: String, filePath: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(filePath), getMimeType(filename))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(filename)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("File saved to: $filePath"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_COMPLETE + filename.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun showErrorNotification(filename: String, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(filename)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Error: $error"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_ERROR + filename.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun showProgressNotificationInternal(filename: String, progress: Int, speed: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading...")
            .setContentText(filename)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_PROGRESS,
                notification
            )
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun cancelProgress() {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun getMimeType(filename: String): String {
        return when {
            filename.endsWith(".mp4") -> "video/mp4"
            filename.endsWith(".webm") -> "video/webm"
            filename.endsWith(".mp3") -> "audio/mpeg"
            filename.endsWith(".m4a") -> "audio/mp4"
            filename.endsWith(".opus") -> "audio/opus"
            filename.endsWith(".ogg") -> "audio/ogg"
            filename.endsWith(".wav") -> "audio/wav"
            else -> "application/octet-stream"
        }
    }
}
