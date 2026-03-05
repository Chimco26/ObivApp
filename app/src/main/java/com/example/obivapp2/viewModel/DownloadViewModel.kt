package com.example.obivapp2.viewModel

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.services.DownloadService
import com.example.obivapp2.utils.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.app.Activity
import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Int,
        val downloadedSize: Long,
        val totalSize: Long,
        val isPaused: Boolean = false
    ) : DownloadState()
    data class Success(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

data class DownloadedVideo(
    val title: String,
    val filePath: String,
    val size: Long,
    val lastModified: Long
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DownloadViewModel"
    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()

    private val _downloadedVideos = MutableStateFlow<List<DownloadedVideo>>(emptyList())
    val downloadedVideos: StateFlow<List<DownloadedVideo>> = _downloadedVideos.asStateFlow()

    init {
        loadDownloadedVideos()
        setupDownloadEventListener()
    }

    fun getDownloadState(url: String): StateFlow<DownloadState> {
        return downloadStates.getOrPut(url) {
            MutableStateFlow(DownloadState.Idle)
        }
    }

    fun loadDownloadedVideos() {
        viewModelScope.launch {
            Log.d(TAG, "Démarrage de loadDownloadedVideos")
            val videoMap = mutableMapOf<String, DownloadedVideo>()
            val context = getApplication<Application>().applicationContext

            // 1. Scan manuel dans le dossier obivap movies
            val downloadFolder = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "obivap movies")
            if (downloadFolder.exists() && downloadFolder.isDirectory) {
                val files = downloadFolder.listFiles()
                Log.d(TAG, "Scan manuel: ${files?.size ?: 0} fichiers trouvés")
                files?.forEach { file ->
                    if (file.isFile && (file.extension.lowercase() == "mp4" || file.extension.lowercase() == "mkv")) {
                        val size = file.length()
                        Log.d(TAG, "Fichier trouvé: ${file.name}, Taille détectée: $size octets")
                        val video = DownloadedVideo(
                            title = file.nameWithoutExtension.replace("_", " "),
                            filePath = file.absolutePath,
                            size = size,
                            lastModified = file.lastModified()
                        )
                        videoMap[video.filePath] = video
                    }
                }
            } else {
                Log.w(TAG, "Le dossier 'obivap movies' n'existe pas ou n'est pas un répertoire")
            }

            // 2. MediaStore pour compléter/confirmer
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_MODIFIED,
                    MediaStore.Video.Media.DATA
                )
                val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
                val selectionArgs = arrayOf("%/obivap movies/%")

                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    Log.d(TAG, "MediaStore: ${cursor.count} vidéos trouvées via query")
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataColumn)
                        val msSize = cursor.getLong(sizeColumn)
                        
                        val existing = videoMap[path]
                        if (existing != null) {
                            // Si le scan manuel a renvoyé 0 mais MediaStore a une taille, on prend celle de MediaStore
                            if (existing.size <= 0 && msSize > 0) {
                                Log.d(TAG, "Mise à jour taille MediaStore pour $path: $msSize")
                                videoMap[path] = existing.copy(size = msSize)
                            }
                        } else {
                            val name = cursor.getString(nameColumn)
                            val date = cursor.getLong(dateColumn)
                            var finalSize = msSize
                            
                            // Si MediaStore dit 0, on tente une dernière lecture directe
                            if (finalSize <= 0) {
                                try {
                                    finalSize = File(path).length()
                                    Log.d(TAG, "Lecture directe fallback pour $path: $finalSize")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erreur lecture fallback: ${e.message}")
                                }
                            }

                            if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".mkv")) {
                                videoMap[path] = DownloadedVideo(
                                    title = name.replace(".mp4", "").replace(".mkv", "").replace("_", " "),
                                    filePath = path,
                                    size = finalSize,
                                    lastModified = date
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur durant la requête MediaStore: ${e.message}")
            }

            val finalResult = videoMap.values.sortedByDescending { it.lastModified }
            Log.d(TAG, "Chargement fini. Nombre total de vidéos prêtes: ${finalResult.size}")
            _downloadedVideos.value = finalResult
        }
    }

    fun setupDownloadEventListener() {
        viewModelScope.launch {
            DownloadService.downloadEvents.collect { event ->
                event?.let { handleDownloadEvent(it) }
            }
        }
    }

    private fun handleDownloadEvent(event: DownloadService.DownloadEvent) {
        when (event) {
            is DownloadService.DownloadEvent.Progress -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                stateFlow.value = DownloadState.Downloading(
                    event.progress,
                    event.downloadedSize,
                    event.totalSize,
                    event.isPaused
                )
            }
            is DownloadService.DownloadEvent.Complete -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                stateFlow.value = DownloadState.Success(event.filePath)
                loadDownloadedVideos()
            }
            is DownloadService.DownloadEvent.Error -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                stateFlow.value = DownloadState.Error(event.errorMessage)
            }
            is DownloadService.DownloadEvent.Paused -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                val currentState = stateFlow.value
                if (currentState is DownloadState.Downloading) {
                    stateFlow.value = currentState.copy(isPaused = true)
                }
            }
            is DownloadService.DownloadEvent.Resumed -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                val currentState = stateFlow.value
                if (currentState is DownloadState.Downloading) {
                    stateFlow.value = currentState.copy(isPaused = false)
                }
            }
            is DownloadService.DownloadEvent.Cancelled -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                stateFlow.value = DownloadState.Idle
            }
            is DownloadService.DownloadEvent.NetworkLost -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                val currentState = stateFlow.value
                if (currentState is DownloadState.Downloading) {
                    stateFlow.value = currentState.copy(isPaused = true)
                }
            }
            is DownloadService.DownloadEvent.NetworkRestored -> {
                val stateFlow = downloadStates.getOrPut(event.url) {
                    MutableStateFlow(DownloadState.Idle)
                }
                val currentState = stateFlow.value
                if (currentState is DownloadState.Downloading) {
                    stateFlow.value = currentState.copy(isPaused = false)
                }
            }
        }
    }

    fun downloadM3U8(m3u8Url: String, context: Context, videoTitle: String? = null) {
        val hasNotificationPermission = Permissions.hasNotificationPermission(context)
        if (!hasNotificationPermission) {
            (context as? Activity)?.let {
                Permissions.requestNotificationPermission(it)
                return
            }
        }

        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, m3u8Url)
            putExtra(DownloadService.EXTRA_TITLE, videoTitle)
        }

        context.startService(intent)

        val stateFlow = downloadStates.getOrPut(m3u8Url) {
            MutableStateFlow(DownloadState.Idle)
        }
        stateFlow.value = DownloadState.Downloading(0, 0, 0)
    }

    fun togglePauseResume(m3u8Url: String, context: Context) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_TOGGLE_PAUSE
            putExtra(DownloadService.EXTRA_URL, m3u8Url)
        }
        context.startService(intent)
    }

    fun cancelDownload(m3u8Url: String, context: Context) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, m3u8Url)
        }
        context.startService(intent)
    }

    fun deleteDownloadedVideo(video: DownloadedVideo) {
        val file = File(video.filePath)
        if (file.exists()) {
            file.delete()
            loadDownloadedVideos()
        }
    }
}
