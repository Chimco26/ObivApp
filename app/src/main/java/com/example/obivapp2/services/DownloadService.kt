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

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var notificationHelper: NotificationHelper? = null
    private val cookieJar = CookieJar.NO_COOKIES
    private var currentJob: Job? = null
    private var currentFile: File? = null

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
        data class NetworkLost(val url: String) : DownloadEvent()
        data class NetworkRestored(val url: String) : DownloadEvent()
    }

    private var activeDownloads = mutableMapOf<String, Job>()
    private var downloadSizes = mutableMapOf<String, Long>()
    private var downloadProgress = mutableMapOf<String, Int>()
    private var downloadedSizes = mutableMapOf<String, Long>()
    private var pausedDownloads = mutableSetOf<String>()
    private var partialFiles = mutableMapOf<String, File>()

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
                    pausedDownloads.remove(url)
                    // Nettoyer impérativement le fichier partiel
                    cleanupPartialFileForUrl(url)
                    emitDownloadEvent(DownloadEvent.Cancelled(url))
                }
                if (activeDownloads.isEmpty()) {
                    stopSelf()
                }
            }
            ACTION_TOGGLE_PAUSE, ACTION_TOGGLE_PAUSE_FROM_NOTIFICATION -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url != null) {
                    if (pausedDownloads.contains(url)) {
                        // Reprendre le téléchargement
                        pausedDownloads.remove(url)
                        Log.d("DownloadService", "Reprise du téléchargement pour: $url")
                        emitDownloadEvent(DownloadEvent.Resumed(url))
                    } else {
                        // Mettre en pause le téléchargement
                        pausedDownloads.add(url)
                        Log.d("DownloadService", "Mise en pause du téléchargement pour: $url")
                        emitDownloadEvent(DownloadEvent.Paused(url))
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
    
    private fun cleanupPartialFileForUrl(url: String) {
        partialFiles[url]?.let { file ->
            if (file.exists() && file.name.endsWith(TEMP_FILE_SUFFIX)) {
                if (file.delete()) {
                    Log.d("DownloadService", "Fichier partiel supprimé pour $url: ${file.name}")
                } else {
                    Log.e("DownloadService", "Échec de suppression du fichier partiel: ${file.name}")
                }
            }
        }
        partialFiles.remove(url)
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
        val isPaused = pausedDownloads.contains(url)
        
        Log.d("DownloadService", "Mise à jour notification pour $url - Pause: $isPaused, Progression: $progress%")
        
        // Créer les intents pour les actions de la notification
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
            url.hashCode() + 1000, // Différent request code pour éviter les conflits
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        notificationHelper?.showDownloadProgressNotificationWithActions(
            notificationId = url.hashCode(),
            title = "Téléchargement ${if (isPaused) "en pause" else "en cours"}",
            progress = progress,
            downloadedSize = downloadedSize,
            totalSize = downloadSize,
            isPaused = isPaused,
            pauseResumePendingIntent = pauseResumePendingIntent,
            cancelPendingIntent = cancelPendingIntent
        )
    }

    private fun startDownload(m3u8Url: String, videoTitle: String?) {
        val notificationId = m3u8Url.hashCode()

        val job = serviceScope.launch {
            try {
                Log.d("DownloadService", "Début du téléchargement M3U8: $m3u8Url")

                                // Variables pour stocker les données du M3U8
                var segmentUrls: List<String> = emptyList()
                var totalSize: Long = 0L
                
                // Utiliser la gestion réseau pour le téléchargement M3U8
                executeWithNetworkRetry({
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

                    segmentUrls = m3u8Body.lines()
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
                    totalSize = calculateTotalSize(segmentUrls)
                    downloadSizes[m3u8Url] = totalSize
                }, m3u8Url)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val sanitizedTitle = videoTitle?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "video"
                val fileName = "${sanitizedTitle}_${timestamp}.mp4"
                val tempFileName = "$fileName$TEMP_FILE_SUFFIX"

                // Créer le dossier personnalisé pour les vidéos
                val baseDownloadDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ : utiliser le dossier Downloads de l'app
                    File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "obivap movies")
                } else {
                    // Android < 10 : utiliser le dossier Downloads public
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "obivap movies")
                }
                val customDir = baseDownloadDir
                
                Log.d("DownloadService", "Dossier de base: ${baseDownloadDir.absolutePath}")
                Log.d("DownloadService", "Dossier personnalisé: ${customDir.absolutePath}")
                Log.d("DownloadService", "Dossier existe: ${customDir.exists()}")
                Log.d("DownloadService", "Dossier peut écrire: ${customDir.canWrite()}")
                
                // Créer le dossier s'il n'existe pas
                if (!customDir.exists()) {
                    Log.d("DownloadService", "Tentative de création du dossier...")
                    if (!customDir.mkdirs()) {
                        Log.e("DownloadService", "Échec de création du dossier: ${customDir.absolutePath}")
                        throw Exception("Impossible de créer le dossier de destination: ${customDir.absolutePath}")
                    } else {
                        Log.d("DownloadService", "Dossier créé avec succès: ${customDir.absolutePath}")
                    }
                } else {
                    Log.d("DownloadService", "Dossier existe déjà: ${customDir.absolutePath}")
                }
                
                val tempFile = File(customDir, tempFileName)
                val finalFile = File(customDir, fileName)
                
                currentFile = tempFile
                // Enregistrer le fichier partiel pour ce téléchargement
                partialFiles[m3u8Url] = tempFile

                var totalDownloadedSize = 0L
                tempFile.outputStream().use { output ->
                    segmentUrls.forEachIndexed { index, segmentUrl ->
                        // Vérifier si le job a été annulé
                        ensureActive()

                        // Gérer la pause pour cette URL spécifique
                        while (pausedDownloads.contains(m3u8Url)) {
                            Log.d("DownloadService", "Téléchargement en pause pour $m3u8Url - attente...")
                            delay(500)
                            ensureActive()
                        }

                        val progress = if (totalSize > 0) {
                            ((totalDownloadedSize * 100) / totalSize).toInt()
                        } else {
                            ((index + 1) * 100) / segmentUrls.size
                        }

                        // Émettre l'événement de progression
                        val isPaused = pausedDownloads.contains(m3u8Url)
                        emitDownloadEvent(DownloadEvent.Progress(m3u8Url, progress, totalDownloadedSize, totalSize, isPaused))

                        // Mettre à jour la notification seulement si la progression a changé de 1% ou plus
                        val lastProgress = downloadProgress[m3u8Url] ?: 0
                        if (progress - lastProgress >= 1 || progress == 100 || isPaused) {
                            // Mettre à jour les maps locales
                            downloadProgress[m3u8Url] = progress
                            downloadedSizes[m3u8Url] = totalDownloadedSize
                            updateNotificationForPause(m3u8Url)
                        }

                        // Utiliser la gestion réseau pour chaque segment
                        executeWithNetworkRetry(suspend {
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
                                    while (pausedDownloads.contains(m3u8Url)) {
                                        Log.d("DownloadService", "Écriture en pause pour $m3u8Url - attente...")
                                        delay(500)
                                        ensureActive()
                                    }
                                    
                                    output.write(buffer, 0, bytes)
                                    totalDownloadedSize += bytes
                                    bytes = input.read(buffer)
                                }
                            } ?: throw Exception("Erreur lors du téléchargement du segment $index")
                        }, segmentUrl)
                    }
                }

                // Renommer le fichier temporaire en fichier final
                if (!tempFile.renameTo(finalFile)) {
                    throw Exception("Erreur lors de la finalisation du fichier")
                }

                // Annuler la notification de progression
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(notificationId)

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
                pausedDownloads.remove(m3u8Url)

                // Arrêter le service si c'était le dernier téléchargement
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }

            } catch (e: CancellationException) {
                Log.d("DownloadService", "Téléchargement annulé")
                cleanupPartialFileForUrl(m3u8Url)
                
                // Annuler la notification de progression
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(notificationId)
                
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
                pausedDownloads.remove(m3u8Url)
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("DownloadService", "Erreur de téléchargement", e)
                cleanupPartialFileForUrl(m3u8Url)
                
                // Annuler la notification de progression
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(notificationId)
                
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
                pausedDownloads.remove(m3u8Url)
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        
        activeDownloads[m3u8Url] = job
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("DownloadService", "Réseau disponible")
                    if (!isNetworkAvailable) {
                        isNetworkAvailable = true
                        networkRetryCount = 0
                        // Reprendre les téléchargements en pause à cause du réseau
                        resumeDownloadsAfterNetworkRestore()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d("DownloadService", "Réseau perdu")
                    isNetworkAvailable = false
                    // Mettre en pause les téléchargements actifs
                    pauseDownloadsDueToNetworkLoss()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    
                    if (hasInternet && isValidated && !isNetworkAvailable) {
                        Log.d("DownloadService", "Réseau validé et disponible")
                        isNetworkAvailable = true
                        networkRetryCount = 0
                        resumeDownloadsAfterNetworkRestore()
                    }
                }
            }

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
        }
    }

    private fun pauseDownloadsDueToNetworkLoss() {
        activeDownloads.keys.forEach { url ->
            if (!pausedDownloads.contains(url)) {
                pausedDownloads.add(url)
                emitDownloadEvent(DownloadEvent.NetworkLost(url))
                Log.d("DownloadService", "Téléchargement mis en pause à cause de la perte de réseau: $url")
            }
        }
    }

    private fun resumeDownloadsAfterNetworkRestore() {
        val urlsToResume = pausedDownloads.toList()
        urlsToResume.forEach { url ->
            pausedDownloads.remove(url)
            emitDownloadEvent(DownloadEvent.NetworkRestored(url))
            Log.d("DownloadService", "Téléchargement repris après restauration du réseau: $url")
        }
    }

    private fun isNetworkConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager?.activeNetworkInfo
            activeNetworkInfo?.isConnected == true
        }
    }

    private suspend fun waitForNetworkConnection(): Boolean {
        var attempts = 0
        
        while (!isNetworkConnected() && attempts < NetworkConfig.MAX_WAIT_FOR_NETWORK_ATTEMPTS) {
            Log.d("DownloadService", "Attente de la connexion réseau... Tentative ${attempts + 1}/${NetworkConfig.MAX_WAIT_FOR_NETWORK_ATTEMPTS}")
            delay(NetworkConfig.NETWORK_RETRY_DELAY_MS)
            attempts++
        }
        
        return isNetworkConnected()
    }

    private suspend fun executeWithNetworkRetry(operation: suspend () -> Unit, url: String) {
        var lastException: Exception? = null
        
        repeat(NetworkConfig.MAX_NETWORK_RETRIES) { attempt ->
            try {
                if (!isNetworkConnected()) {
                    Log.d("DownloadService", "Pas de connexion réseau, attente... Tentative ${attempt + 1}/${NetworkConfig.MAX_NETWORK_RETRIES}")
                    if (!waitForNetworkConnection()) {
                        throw Exception("Pas de connexion réseau disponible après ${NetworkConfig.MAX_NETWORK_RETRIES} tentatives")
                    }
                }
                
                operation()
                return // Succès, sortir de la fonction
                
            } catch (e: Exception) {
                lastException = e
                
                val isNetworkError = e is SocketTimeoutException || 
                                   e is UnknownHostException ||
                                   NetworkConfig.NETWORK_ERROR_KEYWORDS.any { keyword ->
                                       e.message?.contains(keyword, ignoreCase = true) == true
                                   }
                
                if (isNetworkError) {
                    Log.w("DownloadService", "Erreur réseau pour $url (tentative ${attempt + 1}/${NetworkConfig.MAX_NETWORK_RETRIES}): ${e.message}")
                    
                    if (attempt < NetworkConfig.MAX_NETWORK_RETRIES - 1) {
                        delay(NetworkConfig.NETWORK_RETRY_DELAY_MS * (attempt + 1)) // Délai progressif
                        return@repeat
                    }
                } else {
                    // Erreur non liée au réseau, ne pas réessayer
                    throw e
                }
            }
        }
        
        // Si on arrive ici, toutes les tentatives ont échoué
        throw lastException ?: Exception("Échec après ${NetworkConfig.MAX_NETWORK_RETRIES} tentatives")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cancelDownload()
        serviceScope.cancel()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
    }
} 