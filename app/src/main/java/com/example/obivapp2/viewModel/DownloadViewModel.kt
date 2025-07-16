package com.example.obivapp2.viewModel

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

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

    private val cookieJar = CookieJar.NO_COOKIES

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

    // Map to store download states for each video URL
    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()

    // Function to get or create a state flow for a specific URL
    fun getDownloadState(url: String): StateFlow<DownloadState> {
        return downloadStates.getOrPut(url) {
            MutableStateFlow(DownloadState.Idle)
        }
    }

    fun downloadM3U8(m3u8Url: String, context: Context, videoTitle: String? = null) {
        // Get or create state flow for this URL
        val stateFlow = downloadStates.getOrPut(m3u8Url) {
            MutableStateFlow(DownloadState.Idle)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                stateFlow.value = DownloadState.Downloading(0, 0, 0, 0)
                Log.d("DownloadViewModel", "Début du téléchargement M3U8: $m3u8Url")

                // Première requête pour obtenir les cookies
                val initialRequest = Request.Builder()
                    .url(m3u8Url)
                    .build()

                val response = client.newCall(initialRequest).execute()
                if (!response.isSuccessful) {
                    throw IOException("Erreur lors de la requête initiale: ${response.code}")
                }

                val m3u8Body = response.body?.string() ?: throw Exception("Erreur : fichier M3U8 introuvable")
                Log.d("DownloadViewModel", "Contenu du fichier M3U8:")
                m3u8Body.lines().forEach { Log.d("DownloadViewModel", it) }

                if (m3u8Body.contains("Cloudflare")) {
                    throw Exception("Protection Cloudflare détectée. Veuillez réessayer plus tard.")
                }

                val baseUrl = URL(m3u8Url)
                val baseUrlString = "${baseUrl.protocol}://${baseUrl.host}${baseUrl.path.substringBeforeLast("/")}"
                Log.d("DownloadViewModel", "URL de base: $baseUrlString")

                val segmentUrls = m3u8Body.lines()
                    .filter { it.trim().isNotEmpty() && !it.startsWith("#") && (it.endsWith(".ts") || it.endsWith(".m4s")) }
                    .map { segmentPath ->
                        if (segmentPath.startsWith("http")) {
                            segmentPath
                        } else {
                            "$baseUrlString/${segmentPath.trimStart('/')}"
                        }
                    }

                Log.d("DownloadViewModel", "Segments trouvés: ${segmentUrls.size}")
                if (segmentUrls.isEmpty()) {
                    throw Exception("Aucun segment vidéo trouvé dans le fichier M3U8")
                }

                // Créer le nom du fichier de sortie
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val sanitizedTitle = videoTitle?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "video"
                val fileName = "${sanitizedTitle}_${timestamp}.mp4"

                // Utiliser le dossier Downloads public
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = File(downloadDir, fileName)

                var totalDownloadedSize = 0L
                outputFile.outputStream().use { output ->
                    segmentUrls.forEachIndexed { index, segmentUrl ->
                        val progress = ((index + 1) * 100) / segmentUrls.size
                        stateFlow.value = DownloadState.Downloading(
                            progress = progress,
                            currentSegment = index + 1,
                            totalSegments = segmentUrls.size,
                            downloadedSize = totalDownloadedSize
                        )

                        Log.d("DownloadViewModel", "Téléchargement segment ${index + 1}/${segmentUrls.size}: $segmentUrl")
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
                                output.write(buffer, 0, bytes)
                                totalDownloadedSize += bytes
                                bytes = input.read(buffer)
                            }
                        } ?: throw Exception("Erreur lors du téléchargement du segment $index")

                        Log.d("DownloadViewModel", "Segment ${index + 1} téléchargé avec succès")
                    }
                }

                Log.d("DownloadViewModel", "Téléchargement terminé avec succès: ${outputFile.absolutePath}")
                stateFlow.value = DownloadState.Success(outputFile.absolutePath)

            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Erreur de téléchargement", e)
                stateFlow.value = DownloadState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}

