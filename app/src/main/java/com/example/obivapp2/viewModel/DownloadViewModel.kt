package com.example.obivapp2.viewModel

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed class DownloadState {
    object Idle : DownloadState() // Aucun téléchargement lancé
    object Downloading : DownloadState() // Téléchargement en cours
    object Success : DownloadState() // Téléchargement terminé
    data class Error(val message: String) : DownloadState() // Erreur pendant le téléchargement
}


class DownloadViewModel : ViewModel() {

    private val client = OkHttpClient()

    // Suivi de l'état du téléchargement
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    // Fonction pour télécharger les vidéos M3U8
    fun downloadM3U8(m3u8Url: String, context: Context) {
        val outputDir: File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Démarre le téléchargement (mettre à jour l'état)
                _downloadState.value = DownloadState.Downloading

                // Téléchargement du fichier M3U8
                val request = Request.Builder().url(m3u8Url).build()
                val response = client.newCall(request).execute()
                val m3u8Body = response.body?.string() ?: throw Exception("Erreur : fichier M3U8 introuvable")

                val segmentUrls = m3u8Body.lines().filter { it.endsWith(".ts") }
                segmentUrls.forEachIndexed { index, segmentUrl ->
                    val segmentRequest = Request.Builder().url(segmentUrl).build()
                    val segmentResponse = client.newCall(segmentRequest).execute()

                    val inputStream: InputStream? = segmentResponse.body?.byteStream()
                    val outputFile = File(outputDir, "segment_$index.ts")

                    inputStream?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Téléchargement terminé avec succès
                _downloadState.value = DownloadState.Success
            } catch (e: Exception) {
                // En cas d'erreur, on met à jour l'état avec un message d'erreur
                _downloadState.value = DownloadState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}

