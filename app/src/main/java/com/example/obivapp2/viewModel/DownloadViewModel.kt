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
            val videoList = mutableListOf<DownloadedVideo>()
            val context = getApplication<Application>().applicationContext

            try {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_MODIFIED,
                    MediaStore.Video.Media.DATA
                )

                // On filtre précisément sur le dossier "obivap movies"
                val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
                val selectionArgs = arrayOf("%/obivap movies/%")

                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    Log.d(TAG, "MediaStore query retournée. Nombre de lignes: ${cursor.count}")
                    
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn)
                        val size = cursor.getLong(sizeColumn)
                        val date = cursor.getLong(dateColumn)
                        val data = cursor.getString(dataColumn)

                        Log.d(TAG, "Vidéo cible trouvée: $name | Path: $data")

                        if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".mkv")) {
                            videoList.add(
                                DownloadedVideo(
                                    title = name.replace(".mp4", "").replace(".mkv", "").replace("_", " "),
                                    filePath = data,
                                    size = size,
                                    lastModified = date
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la requête MediaStore", e)
            }
            
            // Scan manuel en secours uniquement pour le dossier cible
            val legacyDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "obivap movies")
            if (legacyDir.exists() && legacyDir.isDirectory) {
                legacyDir.listFiles()?.filter { 
                    it.isFile && (it.extension.lowercase() == "mp4" || it.extension.lowercase() == "mkv") 
                }?.forEach { file ->
                    videoList.add(DownloadedVideo(
                        title = file.nameWithoutExtension.replace("_", " "),
                        filePath = file.absolutePath,
                        size = file.length(),
                        lastModified = file.lastModified()
                    ))
                }
            }

            val finalResult = videoList.distinctBy { it.filePath }.sortedByDescending { it.lastModified }
            Log.d(TAG, "Chargement terminé. Total vidéos dans le dossier cible: ${finalResult.size}")
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
