package com.example.obivapp2.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.obivapp2.utils.NotificationHelper
import com.example.obivapp2.viewModel.DownloadState
import kotlinx.coroutines.*
import okhttp3.*
import java.io.*
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import kotlinx.coroutines.CancellationException

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var notificationHelper: NotificationHelper? = null
    private val cookieJar = CookieJar.NO_COOKIES
    private var currentJob: Job? = null
    private var currentFile: File? = null

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Origin", URL(original.url.toString()).let { "${it.protocol}://${it.host}" })
                .header("Connection", "keep-alive")
                .header("Referer", URL(original.url.toString()).let { "${it.protocol}://${it.host}/" })
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")

            chain.proceed(requestBuilder.build())
        }
        .build()

    companion object {
        const val ACTION_START_DOWNLOAD = "com.example.obivapp2.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.obivapp2.action.CANCEL_DOWNLOAD"
        const val ACTION_TOGGLE_PAUSE = "com.example.obivapp2.action.TOGGLE_PAUSE"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_service_channel"
        private const val TEMP_FILE_SUFFIX = ".partial"
        
        // SharedFlow pour la communication avec le ViewModel
        private val _downloadEvents = MutableStateFlow<DownloadEvent?>(null)
        val downloadEvents: MutableStateFlow<DownloadEvent?> = _downloadEvents
    }

    sealed class DownloadEvent {
        data class Progress(
            val url: String,
            val progress: Int,
            val downloadedSize: Long,
            val totalSize: Long,
            val isPaused: Boolean
        ) : DownloadEvent()
        
        data class Complete(val url: String, val filePath: String) : DownloadEvent()
        data class Error(val url: String, val errorMessage: String) : DownloadEvent()
        data class Paused(val url: String) : DownloadEvent()
        data class Resumed(val url: String) : DownloadEvent()
        data class Cancelled(val url: String) : DownloadEvent()
    }

    private var isPaused = false
    private var activeDownloads = mutableMapOf<String, Job>()
    private var downloadSizes = mutableMapOf<String, Long>()
    private var downloadProgress = mutableMapOf<String, Int>()
    private var downloadedSizes = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Service de téléchargement",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Canal pour le service de téléchargement en arrière-plan"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundNotification(videoTitle: String?): NotificationCompat.Builder {
        // Créer l'intent d'annulation
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(videoTitle ?: "Téléchargement en cours")
            .setContentText("Service actif")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Annuler",
                cancelPendingIntent
            )
    }

    private fun emitDownloadEvent(event: DownloadEvent) {
        _downloadEvents.value = event
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val title = intent.getStringExtra(EXTRA_TITLE)
                
                if (url != null && !activeDownloads.containsKey(url)) {
                    // Démarrer le service en premier plan
                    startForeground(NOTIFICATION_ID, createForegroundNotification(title).build())
                    startDownload(url, title)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) {
                    activeDownloads[url]?.cancel()
                    activeDownloads.remove(url)
                    downloadSizes.remove(url)
                    downloadProgress.remove(url)
                    downloadedSizes.remove(url)
                    emitDownloadEvent(DownloadEvent.Cancelled(url))
                }
                if (activeDownloads.isEmpty()) {
                    stopSelf()
                }
            }
            ACTION_TOGGLE_PAUSE -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) {
                    isPaused = !isPaused
                    if (isPaused) {
                        emitDownloadEvent(DownloadEvent.Paused(url))
                    } else {
                        emitDownloadEvent(DownloadEvent.Resumed(url))
                    }
                    // Mettre à jour la notification avec l'état de pause
                    updateNotificationForPause(url)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun cancelDownload() {
        currentJob?.cancel()
        cleanupPartialFile()
        stopSelf()
    }

    private fun cleanupPartialFile() {
        currentFile?.let { file ->
            if (file.exists() && file.name.endsWith(TEMP_FILE_SUFFIX)) {
                file.delete()
                Log.d("DownloadService", "Fichier partiel supprimé: ${file.name}")
            }
        }
    }

    private suspend fun calculateTotalSize(segmentUrls: List<String>): Long {
        var totalSize = 0L
        for (url in segmentUrls) {
            try {
                val request = Request.Builder().url(url).head().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    totalSize += response.header("Content-Length")?.toLongOrNull() ?: 0L
                }
            } catch (e: Exception) {
                Log.e("DownloadService", "Erreur lors du calcul de la taille pour $url", e)
            }
        }
        return totalSize
    }

    private fun updateNotificationForPause(url: String) {
        val downloadSize = downloadSizes[url] ?: 0L
        val downloadedSize = downloadedSizes[url] ?: 0L
        val progress = downloadProgress[url] ?: 0
        
        notificationHelper?.showDownloadProgressNotification(
            notificationId = url.hashCode(),
            title = "Téléchargement ${if (isPaused) "en pause" else "en cours"}",
            progress = progress,
            downloadedSize = downloadedSize,
            totalSize = downloadSize,
            isPaused = isPaused
        )
    }

    private fun startDownload(m3u8Url: String, videoTitle: String?) {
        val notificationId = m3u8Url.hashCode()

        val job = serviceScope.launch {
            try {
                Log.d("DownloadService", "Début du téléchargement M3U8: $m3u8Url")

                val initialRequest = Request.Builder()
                    .url(m3u8Url)
                    .build()

                val response = client.newCall(initialRequest).execute()
                if (!response.isSuccessful) {
                    throw IOException("Erreur lors de la requête initiale: ${response.code}")
                }

                val m3u8Body = response.body?.string() ?: throw Exception("Erreur : fichier M3U8 introuvable")
                
                if (m3u8Body.contains("Cloudflare")) {
                    throw Exception("Protection Cloudflare détectée. Veuillez réessayer plus tard.")
                }

                val baseUrl = URL(m3u8Url)
                val baseUrlString = "${baseUrl.protocol}://${baseUrl.host}${baseUrl.path.substringBeforeLast("/")}"

                val segmentUrls = m3u8Body.lines()
                    .filter { it.trim().isNotEmpty() && !it.startsWith("#") && (it.endsWith(".ts") || it.endsWith(".m4s")) }
                    .map { segmentPath ->
                        if (segmentPath.startsWith("http")) {
                            segmentPath
                        } else {
                            "$baseUrlString/${segmentPath.trimStart('/')}"
                        }
                    }

                if (segmentUrls.isEmpty()) {
                    throw Exception("Aucun segment vidéo trouvé dans le fichier M3U8")
                }

                // Calculer la taille totale
                val totalSize = calculateTotalSize(segmentUrls)
                downloadSizes[m3u8Url] = totalSize

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val sanitizedTitle = videoTitle?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "video"
                val fileName = "${sanitizedTitle}_${timestamp}.mp4"
                val tempFileName = "$fileName$TEMP_FILE_SUFFIX"

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val tempFile = File(downloadDir, tempFileName)
                val finalFile = File(downloadDir, fileName)
                
                currentFile = tempFile

                var totalDownloadedSize = 0L
                tempFile.outputStream().use { output ->
                    segmentUrls.forEachIndexed { index, segmentUrl ->
                        // Vérifier si le job a été annulé
                        ensureActive()

                        // Gérer la pause
                        while (isPaused) {
                            delay(500)
                            ensureActive()
                        }

                        val progress = if (totalSize > 0) {
                            ((totalDownloadedSize * 100) / totalSize).toInt()
                        } else {
                            ((index + 1) * 100) / segmentUrls.size
                        }

                        // Mettre à jour les maps locales
                        downloadProgress[m3u8Url] = progress
                        downloadedSizes[m3u8Url] = totalDownloadedSize

                        // Émettre l'événement de progression
                        emitDownloadEvent(DownloadEvent.Progress(m3u8Url, progress, totalDownloadedSize, totalSize, isPaused))

                        notificationHelper?.showDownloadProgressNotification(
                            notificationId = notificationId,
                            title = sanitizedTitle,
                            progress = progress,
                            downloadedSize = totalDownloadedSize,
                            totalSize = totalSize,
                            isPaused = isPaused
                        )

                        val segmentRequest = Request.Builder()
                            .url(segmentUrl)
                            .build()

                        val segmentResponse = client.newCall(segmentRequest).execute()
                        if (!segmentResponse.isSuccessful) {
                            throw Exception("Erreur lors du téléchargement du segment $index: ${segmentResponse.code}")
                        }

                        val inputStream: InputStream? = segmentResponse.body?.byteStream()
                        inputStream?.use { input ->
                            val buffer = ByteArray(8192)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                ensureActive() // Vérifier l'annulation pendant l'écriture
                                
                                // Gérer la pause pendant l'écriture
                                while (isPaused) {
                                    delay(500)
                                    ensureActive()
                                }
                                
                                output.write(buffer, 0, bytes)
                                totalDownloadedSize += bytes
                                bytes = input.read(buffer)
                            }
                        } ?: throw Exception("Erreur lors du téléchargement du segment $index")
                    }
                }

                // Renommer le fichier temporaire en fichier final
                if (!tempFile.renameTo(finalFile)) {
                    throw Exception("Erreur lors de la finalisation du fichier")
                }

                // Émettre l'événement de completion
                emitDownloadEvent(DownloadEvent.Complete(m3u8Url, finalFile.absolutePath))

                notificationHelper?.showDownloadCompleteNotification(
                    notificationId = notificationId,
                    title = sanitizedTitle,
                    filePath = finalFile.absolutePath
                )

                // Nettoyer les maps
                activeDownloads.remove(m3u8Url)
                downloadSizes.remove(m3u8Url)
                downloadProgress.remove(m3u8Url)
                downloadedSizes.remove(m3u8Url)

                // Arrêter le service si c'était le dernier téléchargement
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }

            } catch (e: CancellationException) {
                Log.d("DownloadService", "Téléchargement annulé")
                cleanupPartialFile()
                emitDownloadEvent(DownloadEvent.Cancelled(m3u8Url))
                notificationHelper?.showDownloadErrorNotification(
                    notificationId = notificationId,
                    title = videoTitle ?: "Téléchargement",
                    error = "Téléchargement annulé"
                )
                activeDownloads.remove(m3u8Url)
                downloadSizes.remove(m3u8Url)
                downloadProgress.remove(m3u8Url)
                downloadedSizes.remove(m3u8Url)
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("DownloadService", "Erreur de téléchargement", e)
                cleanupPartialFile()
                emitDownloadEvent(DownloadEvent.Error(m3u8Url, e.message ?: "Erreur inconnue"))
                notificationHelper?.showDownloadErrorNotification(
                    notificationId = notificationId,
                    title = videoTitle ?: "Téléchargement",
                    error = e.message ?: "Erreur inconnue"
                )
                activeDownloads.remove(m3u8Url)
                downloadSizes.remove(m3u8Url)
                downloadProgress.remove(m3u8Url)
                downloadedSizes.remove(m3u8Url)
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        
        activeDownloads[m3u8Url] = job
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cancelDownload()
        serviceScope.cancel()
    }
} 