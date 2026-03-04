package com.example.obivapp2.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class NotificationHelper(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Téléchargements"
        private const val CHANNEL_DESCRIPTION = "Notifications de progression des téléchargements"
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> {
                    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
                    "%.1f Go".format(gb)
                }
                bytes >= 1024 * 1024 -> {
                    val mb = bytes.toDouble() / (1024 * 1024)
                    "%.0f Mo".format(mb)
                }
                bytes >= 1024 -> {
                    val kb = bytes.toDouble() / 1024
                    "%.0f Ko".format(kb)
                }
                else -> "$bytes octets"
            }
        }
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDownloadProgressNotification(
        notificationId: Int,
        title: String,
        progress: Int,
        downloadedSize: Long,
        totalSize: Long,
        isPaused: Boolean = false
    ) {
        val downloadedFormatted = formatFileSize(downloadedSize)
        val subText = if (totalSize > 0) {
            "$downloadedFormatted / ${formatFileSize(totalSize)} ($progress%)"
        } else {
            "$downloadedFormatted ($progress%)"
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (isPaused) "En pause" else "Téléchargement en cours...")
            .setProgress(100, progress, false)
            .setOngoing(!isPaused)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSubText(subText)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun showDownloadProgressNotificationWithActions(
        notificationId: Int,
        title: String,
        progress: Int,
        downloadedSize: Long,
        totalSize: Long,
        isPaused: Boolean = false,
        pauseResumePendingIntent: PendingIntent? = null,
        cancelPendingIntent: PendingIntent? = null
    ) {
        val downloadedFormatted = formatFileSize(downloadedSize)
        val subText = if (totalSize > 0) {
            "$downloadedFormatted / ${formatFileSize(totalSize)} ($progress%)"
        } else {
            "$downloadedFormatted ($progress%)"
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (isPaused) "En pause" else "Téléchargement en cours...")
            .setProgress(100, progress, false)
            .setOngoing(!isPaused)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (isPaused) "En pause - Appuyez sur Reprendre" else "Téléchargement en cours - Appuyez sur Pause pour arrêter"))

        pauseResumePendingIntent?.let {
            builder.addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "▶ Reprendre" else "⏸ Pause",
                it
            )
        }
        
        cancelPendingIntent?.let {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "❌ Annuler",
                it
            )
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun showDownloadCompleteNotification(
        notificationId: Int,
        title: String,
        filePath: String
    ) {
        val file = File(filePath)
        val locationMessage = "Fichier dans Downloads/obivap movies"
        
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingShareIntent = PendingIntent.getActivity(
            context,
            notificationId + 1000,
            Intent.createChooser(shareIntent, "Partager la vidéo"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Téléchargement terminé - $locationMessage")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                "Ouvrir",
                pendingOpenIntent
            )
            .addAction(
                android.R.drawable.ic_menu_share,
                "Partager",
                pendingShareIntent
            )

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun showDownloadErrorNotification(
        notificationId: Int,
        title: String,
        error: String
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText("Erreur : $error")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancelNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
