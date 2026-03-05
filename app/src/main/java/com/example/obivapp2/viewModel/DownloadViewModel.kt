package com.example.obivapp2.viewModel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.services.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.obivapp2.database.AppDatabase
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
    val lastModified: Long,
    val url: String? = null
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DownloadViewModel"
    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()
    private val linkDao = AppDatabase.getDatabase(application).linkDao()

    private val _downloadedVideos = MutableStateFlow<List<DownloadedVideo>>(emptyList())
    val downloadedVideos: StateFlow<List<DownloadedVideo>> = _downloadedVideos.asStateFlow()

    init {
        loadDownloadedVideos()
        setupDownloadEventListener()
        refreshDownloadStatesFromDb()
    }

    private fun refreshDownloadStatesFromDb() {
        viewModelScope.launch {
            linkDao.getAllLinks().collect { links ->
                links.forEach { link ->
                    if (link.filePath != null && File(link.filePath).exists()) {
                        val stateFlow = downloadStates.getOrPut(link.url) {
                            MutableStateFlow(DownloadState.Idle)
                        }
                        stateFlow.value = DownloadState.Success(link.filePath)
                    }
                }
            }
        }
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

            // Mise à jour vers le dossier privé (même que dans DownloadService)
            val app = getApplication<Application>()
            val downloadFolder = File(app.getExternalFilesDir(null), "obivap movies")

            if (downloadFolder.exists() && downloadFolder.isDirectory) {
                val files = downloadFolder.listFiles()
                files?.forEach { file ->
                    if (file.isFile && (file.extension.lowercase() == "mp4" || file.extension.lowercase() == "mkv")) {
                        val video = DownloadedVideo(
                            title = file.nameWithoutExtension.replace("_", " "),
                            filePath = file.absolutePath,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                        videoMap[video.filePath] = video
                    }
                }
            }

            val finalResult = videoMap.values.sortedByDescending { it.lastModified }
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
        val stateFlow = downloadStates.getOrPut(event.url) {
            MutableStateFlow(DownloadState.Idle)
        }
        
        when (event) {
            is DownloadService.DownloadEvent.Progress -> {
                stateFlow.value = DownloadState.Downloading(
                    event.progress,
                    event.downloadedSize,
                    event.totalSize,
                    event.isPaused
                )
            }
            is DownloadService.DownloadEvent.Complete -> {
                stateFlow.value = DownloadState.Success(event.filePath)
                loadDownloadedVideos()
            }
            is DownloadService.DownloadEvent.Error -> {
                stateFlow.value = DownloadState.Error(event.errorMessage)
            }
            is DownloadService.DownloadEvent.Paused -> {
                val currentState = stateFlow.value
                if (currentState is DownloadState.Downloading) {
                    stateFlow.value = currentState.copy(isPaused = true)
                }
            }
            is DownloadService.DownloadEvent.Resumed -> {
                val currentState = stateFlow.value
                if (currentState is DownloadState.Downloading) {
                    stateFlow.value = currentState.copy(isPaused = false)
                }
            }
            is DownloadService.DownloadEvent.Cancelled -> {
                stateFlow.value = DownloadState.Idle
            }
            else -> {}
        }
    }

    fun downloadM3U8(m3u8Url: String, context: Context, videoTitle: String? = null) {
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
        viewModelScope.launch {
            val file = File(video.filePath)
            if (file.exists()) file.delete()
            
            val allLinks = linkDao.getAllLinksOnce()
            val link = allLinks.find { it.filePath == video.filePath }
            link?.let {
                linkDao.delete(it.url)
                downloadStates[it.url]?.value = DownloadState.Idle
            }
            
            loadDownloadedVideos()
        }
    }

    fun shareVideoFile(context: Context, video: DownloadedVideo) {
        val file = File(video.filePath)
        if (!file.exists()) return

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "video/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Partager la vidéo"))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur partage: ${e.message}")
        }
    }
}

private suspend fun com.example.obivapp2.database.LinkDao.getAllLinksOnce(): List<com.example.obivapp2.viewModel.LinkData> {
    return this.getAllLinks().first()
}
