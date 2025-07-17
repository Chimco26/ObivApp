package com.example.obivapp2.viewModel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.services.DownloadService
import com.example.obivapp2.utils.NotificationHelper
import com.example.obivapp2.utils.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.app.Activity

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

class DownloadViewModel : ViewModel() {
    // Map to store download states for each video URL
    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()

    // Function to get or create a state flow for a specific URL
    fun getDownloadState(url: String): StateFlow<DownloadState> {
        return downloadStates.getOrPut(url) {
            MutableStateFlow(DownloadState.Idle)
        }
    }

    // Function to setup event listener for download service
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
        }
    }

    fun downloadM3U8(m3u8Url: String, context: Context, videoTitle: String? = null) {
        // Vérifier la permission des notifications
        val hasNotificationPermission = Permissions.hasNotificationPermission(context)
        if (!hasNotificationPermission) {
            (context as? Activity)?.let {
                Permissions.requestNotificationPermission(it)
                return
            }
        }

        // Créer l'intent pour démarrer le service
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, m3u8Url)
            putExtra(DownloadService.EXTRA_TITLE, videoTitle)
        }

        // Démarrer le service
        context.startService(intent)

        // Mettre à jour l'état local
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

    override fun onCleared() {
        super.onCleared()
        // Cleanup if needed
    }
}


