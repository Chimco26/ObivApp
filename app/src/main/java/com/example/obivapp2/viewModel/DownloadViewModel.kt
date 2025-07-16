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
import android.app.Activity

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Int,
        val currentSegment: Int,
        val totalSegments: Int,
        val downloadedSize: Long
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
        stateFlow.value = DownloadState.Downloading(0, 0, 0, 0)
    }
}


