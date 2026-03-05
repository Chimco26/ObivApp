package com.example.obivapp2.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.obivapp2.utils.NotificationHelper
import com.example.obivapp2.utils.NetworkConfig
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
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer
import android.media.MediaScannerConnection
import com.example.obivapp2.database.AppDatabase
import com.example.obivapp2.viewModel.LinkData

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var notificationHelper: NotificationHelper? = null
    private val cookieJar = CookieJar.NO_COOKIES

    // Gestion du réseau
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkAvailable = true
    private var networkRetryCount = 0

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NetworkConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
        const val ACTION_TOGGLE_PAUSE_FROM_NOTIFICATION = "com.example.obivapp2.action.TOGGLE_PAUSE_FROM_NOTIFICATION"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_service_channel"
        private const val TEMP_FILE_SUFFIX = ".partial"
        
        // SharedFlow pour la communication avec le ViewModel
        private val _downloadEvents = MutableStateFlow<DownloadEvent?>(null)
        val downloadEvents: MutableStateFlow<DownloadEvent?> = _downloadEvents
    }

    sealed class DownloadEvent(open val url: String) {
        data class Progress(
            override val url: String,
            val progress: Int,
            val downloadedSize: Long,
            val totalSize: Long,
            val isPaused: Boolean
        ) : DownloadEvent(url)
        
        data class Complete(override val url: String, val filePath: String) : DownloadEvent(url)
        data class Error(override val url: String, val errorMessage: String) : DownloadEvent(url)
        data class Paused(override val url: String) : DownloadEvent(url)
        data class Resumed(override val url: String) : DownloadEvent(url)
        data class Cancelled(override val url: String) : DownloadEvent(url)
        data class NetworkLost(override val url: String) : DownloadEvent(url)
        data class NetworkRestored(override val url: String) : DownloadEvent(url)
    }

    private var activeDownloads = mutableMapOf<String, Job>()
    private var downloadSizes = mutableMapOf<String, Long>()
    private var downloadProgress = mutableMapOf<String, Int>()
    private var downloadedSizes = mutableMapOf<String, Long>()
    private var pausedDownloads = mutableSetOf<String>()
    private var partialFiles = mutableMapOf<String, File>()
    private var videoTitles = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        createNotificationChannel()
        setupNetworkMonitoring()
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
            .setContentTitle(videoTitle ?: "Téléchargement")
            .setContentText("Initialisation...")
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

    private fun cleanupDownloadData(url: String) {
        activeDownloads.remove(url)
        downloadSizes.remove(url)
        downloadProgress.remove(url)
        downloadedSizes.remove(url)
        pausedDownloads.remove(url)
        videoTitles.remove(url)
        cleanupPartialFileForUrl(url)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val title = intent.getStringExtra(EXTRA_TITLE)
                
                if (url != null && !activeDownloads.containsKey(url)) {
                    if (activeDownloads.isEmpty()) {
                        startForeground(NOTIFICATION_ID, createForegroundNotification(title).build())
                    }
                    startDownload(url, title)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) {
                    activeDownloads[url]?.cancel()
                    cleanupDownloadData(url)
                    emitDownloadEvent(DownloadEvent.Cancelled(url))
                    
                    val notificationId = if (activeDownloads.keys.firstOrNull() == url) NOTIFICATION_ID else url.hashCode()
                    notificationHelper?.cancelNotification(notificationId)
                }
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            }
            ACTION_TOGGLE_PAUSE, ACTION_TOGGLE_PAUSE_FROM_NOTIFICATION -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) {
                    if (pausedDownloads.contains(url)) {
                        pausedDownloads.remove(url)
                        emitDownloadEvent(DownloadEvent.Resumed(url))
                    } else {
                        pausedDownloads.add(url)
                        emitDownloadEvent(DownloadEvent.Paused(url))
                    }
                    updateNotificationForPause(url)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun cleanupPartialFileForUrl(url: String) {
        partialFiles[url]?.let { file ->
            if (file.exists() && file.name.endsWith(TEMP_FILE_SUFFIX)) {
                file.delete()
            }
        }
        partialFiles.remove(url)
    }

    private fun updateNotificationForPause(url: String) {
        val downloadSize = downloadSizes[url] ?: 0L
        val downloadedSize = downloadedSizes[url] ?: 0L
        val progress = downloadProgress[url] ?: 0
        val isPaused = pausedDownloads.contains(url)
        val videoTitle = videoTitles[url] ?: "Vidéo"
        
        // Utiliser NOTIFICATION_ID (1) pour le téléchargement principal, et url.hashCode() pour les autres
        val notificationId = if (activeDownloads.keys.firstOrNull() == url) NOTIFICATION_ID else url.hashCode()
        
        val pauseResumeIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE_FROM_NOTIFICATION
            putExtra(EXTRA_URL, url)
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            url.hashCode(),
            pauseResumeIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_URL, url)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            url.hashCode() + 1000,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        notificationHelper?.showDownloadProgressNotificationWithActions(
            notificationId = notificationId,
            title = videoTitle,
            progress = progress,
            downloadedSize = downloadedSize,
            totalSize = downloadSize,
            isPaused = isPaused,
            pauseResumePendingIntent = pauseResumePendingIntent,
            cancelPendingIntent = cancelPendingIntent
        )
    }

    private fun startDownload(m3u8Url: String, videoTitle: String?) {
        videoTitles[m3u8Url] = videoTitle ?: "Vidéo"

        val job = serviceScope.launch {
            // Ajouter à la liste des téléchargements actifs immédiatement
            activeDownloads[m3u8Url] = coroutineContext[Job]!!

            try {
                var segmentUrls: List<String> = emptyList()

                executeWithNetworkRetry({
                    val initialRequest = Request.Builder().url(m3u8Url).build()
                    val response = client.newCall(initialRequest).execute()
                    if (!response.isSuccessful) throw IOException("Erreur: ${response.code}")

                    val m3u8Body = response.body?.string() ?: throw Exception("M3U8 introuvable")
                    if (m3u8Body.contains("Cloudflare")) throw Exception("Protection Cloudflare détectée")

                    val baseUrl = URL(m3u8Url)
                    val baseUrlString = "${baseUrl.protocol}://${baseUrl.host}${baseUrl.path.substringBeforeLast("/")}"

                    segmentUrls = m3u8Body.lines()
                        .filter { it.trim().isNotEmpty() && !it.startsWith("#") && (it.endsWith(".ts") || it.endsWith(".m4s")) }
                        .map { segmentPath ->
                            if (segmentPath.startsWith("http")) segmentPath else "$baseUrlString/${segmentPath.trimStart('/')}"
                        }
                    if (segmentUrls.isEmpty()) throw Exception("Aucun segment trouvé")
                }, m3u8Url)

                val sanitizedTitle = videoTitle?.let { title ->
                    title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                } ?: "video"
                
                val fileName = "${sanitizedTitle}.mp4"
                val tempFileName = "$fileName$TEMP_FILE_SUFFIX"
                val customDir = File(getExternalFilesDir(null), "obivap movies")

                if (!customDir.exists()) customDir.mkdirs()
                
                val tempFile = File(customDir, tempFileName)
                val finalFile = File(customDir, fileName)
                partialFiles[m3u8Url] = tempFile

                var totalDownloadedSize = 0L
                tempFile.outputStream().use { output ->
                    segmentUrls.forEachIndexed { index, segmentUrl ->
                        ensureActive()
                        while (pausedDownloads.contains(m3u8Url)) {
                            delay(500)
                            ensureActive()
                        }

                        val progress = ((index + 1) * 100) / segmentUrls.size
                        val isPaused = pausedDownloads.contains(m3u8Url)
                        emitDownloadEvent(DownloadEvent.Progress(m3u8Url, progress, totalDownloadedSize, 0L, isPaused))

                        val lastProgress = downloadProgress[m3u8Url] ?: 0
                        if (progress > lastProgress || isPaused) {
                            downloadProgress[m3u8Url] = progress
                            downloadedSizes[m3u8Url] = totalDownloadedSize
                            updateNotificationForPause(m3u8Url)
                        }

                        executeWithNetworkRetry({
                            val segmentRequest = Request.Builder().url(segmentUrl).build()
                            val segmentResponse = client.newCall(segmentRequest).execute()
                            if (!segmentResponse.isSuccessful) throw Exception("Erreur segment $index: ${segmentResponse.code}")

                            val body = segmentResponse.body ?: throw Exception("Corps de segment vide")
                            body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                var bytes = input.read(buffer)
                                while (bytes >= 0) {
                                    ensureActive()
                                    while (pausedDownloads.contains(m3u8Url)) {
                                        delay(500)
                                        ensureActive()
                                    }
                                    output.write(buffer, 0, bytes)
                                    totalDownloadedSize += bytes
                                    bytes = input.read(buffer)
                                }
                            }
                        }, segmentUrl)
                    }
                }

                if (!tempFile.renameTo(finalFile)) throw Exception("Erreur finalisation")

                val linkDao = AppDatabase.getDatabase(this@DownloadService).linkDao()
                linkDao.insert(LinkData(url = m3u8Url, text = videoTitle ?: sanitizedTitle, filePath = finalFile.absolutePath))

                // Déterminer l'ID de notification à utiliser pour la fin
                val notificationId = if (activeDownloads.keys.firstOrNull() == m3u8Url) NOTIFICATION_ID else m3u8Url.hashCode()

                emitDownloadEvent(DownloadEvent.Complete(m3u8Url, finalFile.absolutePath))
                notificationHelper?.showDownloadCompleteNotification(notificationId, sanitizedTitle, finalFile.absolutePath)

                cleanupDownloadData(m3u8Url)
                
                // Si d'autres téléchargements sont en cours, promouvoir le suivant en foreground
                val nextUrl = activeDownloads.keys.firstOrNull()
                if (nextUrl != null && m3u8Url != nextUrl) {
                    updateNotificationForPause(nextUrl)
                }

                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }

            } catch (e: CancellationException) {
                cleanupPartialFileForUrl(m3u8Url)
                val notificationId = if (activeDownloads.keys.firstOrNull() == m3u8Url) NOTIFICATION_ID else m3u8Url.hashCode()
                notificationHelper?.cancelNotification(notificationId)
                emitDownloadEvent(DownloadEvent.Cancelled(m3u8Url))
                cleanupDownloadData(m3u8Url)
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            } catch (e: Exception) {
                cleanupPartialFileForUrl(m3u8Url)
                val notificationId = if (activeDownloads.keys.firstOrNull() == m3u8Url) NOTIFICATION_ID else m3u8Url.hashCode()
                emitDownloadEvent(DownloadEvent.Error(m3u8Url, e.message ?: "Erreur inconnue"))
                notificationHelper?.showDownloadErrorNotification(notificationId, videoTitle ?: "Téléchargement", e.message ?: "Erreur inconnue")
                cleanupDownloadData(m3u8Url)
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!isNetworkAvailable) {
                        isNetworkAvailable = true
                        networkRetryCount = 0
                        resumeDownloadsAfterNetworkRestore()
                    }
                }
                override fun onLost(network: Network) {
                    isNetworkAvailable = false
                    pauseDownloadsDueToNetworkLoss()
                }
            }
            val networkRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
        }
    }

    private fun pauseDownloadsDueToNetworkLoss() {
        activeDownloads.keys.forEach { url ->
            if (!pausedDownloads.contains(url)) {
                pausedDownloads.add(url)
                emitDownloadEvent(DownloadEvent.NetworkLost(url))
            }
        }
    }

    private fun resumeDownloadsAfterNetworkRestore() {
        val urlsToResume = pausedDownloads.toList()
        urlsToResume.forEach { url ->
            pausedDownloads.remove(url)
            emitDownloadEvent(DownloadEvent.NetworkRestored(url))
        }
    }

    private fun isNetworkConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager?.activeNetworkInfo?.isConnected == true
        }
    }

    private suspend fun waitForNetworkConnection(): Boolean {
        var attempts = 0
        while (!isNetworkConnected() && attempts < NetworkConfig.MAX_WAIT_FOR_NETWORK_ATTEMPTS) {
            delay(NetworkConfig.NETWORK_RETRY_DELAY_MS)
            attempts++
        }
        return isNetworkConnected()
    }

    private suspend fun executeWithNetworkRetry(operation: suspend () -> Unit, url: String) {
        var lastException: Exception? = null
        repeat(NetworkConfig.MAX_NETWORK_RETRIES) { attempt ->
            try {
                if (!isNetworkConnected() && !waitForNetworkConnection()) throw Exception("Pas de réseau")
                operation()
                return
            } catch (e: Exception) {
                lastException = e
                val isNetworkError = e is SocketTimeoutException || e is UnknownHostException || NetworkConfig.NETWORK_ERROR_KEYWORDS.any { e.message?.contains(it, ignoreCase = true) == true }
                if (isNetworkError && attempt < NetworkConfig.MAX_NETWORK_RETRIES - 1) {
                    delay(NetworkConfig.NETWORK_RETRY_DELAY_MS * (attempt + 1))
                    return@repeat
                } else throw e
            }
        }
        throw lastException ?: Exception("Échec après ${NetworkConfig.MAX_NETWORK_RETRIES} tentatives")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
    }
}
