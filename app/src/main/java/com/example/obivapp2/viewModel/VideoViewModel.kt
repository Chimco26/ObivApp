package com.example.obivapp2.viewModel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.network.ApiService
import com.example.obivapp2.network.RetrofitInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.HttpException
import java.io.IOException
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.net.URI
import kotlinx.coroutines.Job


class VideoViewModel : ViewModel() {
    private val _videoUrl = mutableStateOf<String?>(null)
    private val _imageUrl = mutableStateOf<String?>(null)
    private val _description = mutableStateOf<String?>(null)
    private val _videoUrlToShare = mutableStateOf<String?>(null)
    private val _title = mutableStateOf<String?>(null)
    private val _currentUrl = mutableStateOf<String?>(null)

    val videoUrl: State<String?> get() = _videoUrl
    val imageUrl: State<String?> get() = _imageUrl
    val description: State<String?> get() = _description
    val videoUrlToShare: State<String?> get() = _videoUrlToShare
    val title: State<String?> get() = _title
    val currentUrl: State<String?> get() = _currentUrl

    private var fetchJob: Job? = null

    fun resetLinkVideo(){
        Log.d("VideoViewModel", "resetLinkVideo called")
        fetchJob?.cancel()
        _videoUrl.value = null
        _imageUrl.value = null
        _videoUrlToShare.value = null
        _description.value = null
        _title.value = null
        _currentUrl.value = null
    }

    fun setVideoData(url: String?, title: String?, imageUrl: String?, description: String?) {
        _videoUrl.value = url
        _title.value = title
        _imageUrl.value = imageUrl
        _description.value = description
        _currentUrl.value = url
    }

    // Modification : on ne considère comme "vide" que si l'URL de la vidéo est absente
    fun isDataNull() : Boolean{
        return _videoUrl.value == null
    }

    fun fetchLinkVideo(url: String, title: String? = null) {
        Log.d("VideoViewModel", "fetchLinkVideo started for URL: $url")
        resetLinkVideo()
        _currentUrl.value = url
        _title.value = title
        
        fetchJob = viewModelScope.launch {
            try {
                val response: Response<ResponseBody> = RetrofitInstance.api.getVideo(url)
                Log.d("VideoViewModel", "Initial request status: ${response.code()}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val htmlContent = responseBody.string()
                        Log.d("VideoViewModel", "HTML received, length: ${htmlContent.length}")
                        extractImageData(htmlContent)
                        parseHtmlForVideoUrl(htmlContent)
                        extractDescription(htmlContent)
                    } else {
                        Log.e("VideoViewModel", "Response body is null")
                    }
                } else {
                    Log.e("VideoViewModel", "Request failed: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error in fetchLinkVideo: ${e.message}", e)
            }
        }
    }

    private suspend fun parseHtmlForVideoUrl(htmlContent: String) {
        val document: Document = Jsoup.parse(htmlContent)
        val iframes = document.select("iframe[src]")
        Log.d("VideoViewModel", "Found ${iframes.size} iframes")
        
        for (iframe in iframes) {
            val src = iframe.attr("src")
            Log.d("VideoViewModel", "Iframe src: $src")
            _videoUrlToShare.value = src
            val videoId = src.substringAfterLast("/")
            val newHost = extractHostFromUrl(src)
            Log.d("VideoViewModel", "Extracted Host: $newHost, VideoId: $videoId")
            fetchVideo(videoId, newHost)
        }
        
        if (iframes.isEmpty()) {
            Log.w("VideoViewModel", "No iframes found in the HTML content")
        }
    }

    private suspend fun fetchVideo(url: String, newHost: String) {
        Log.d("VideoViewModel", "fetchVideo (iframe) started: $newHost$url")
        if (newHost.isEmpty()) {
            Log.e("VideoViewModel", "Host is empty, skipping fetchVideo")
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                val myApiCldTest = Retrofit.Builder()
                    .baseUrl(newHost)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
                
                val response = myApiCldTest.getVideo(url)
                Log.d("VideoViewModel", "Iframe fetch status: ${response.code()}")
                
                if (response.isSuccessful) {
                    val htmlContent = response.body()?.string()
                    if (htmlContent != null) {
                        Log.d("VideoViewModel", "Iframe HTML length: ${htmlContent.length}")
                        extractM3U8Link(htmlContent)
                    } else {
                        Log.e("VideoViewModel", "Iframe body is null")
                    }
                } else {
                    Log.e("VideoViewModel", "Iframe request failed: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error in fetchVideo: ${e.message}", e)
            }
            Unit
        }
    }

    private fun extractM3U8Link(htmlContent: String) {
        val document: Document = Jsoup.parse(htmlContent)
        val scriptElements = document.getElementsByTag("script")
        Log.d("VideoViewModel", "Searching for m3u8 in ${scriptElements.size} scripts")

        for (element in scriptElements) {
            val scriptData = element.data()
            val normalizedData = scriptData.replace("\\s".toRegex(), "")
            if (normalizedData.contains("file:\"")) {
                val startIndex = normalizedData.indexOf("file:\"") + 6
                val endIndex = normalizedData.indexOf("\"", startIndex)
                if (startIndex != -1 && endIndex != -1) {
                    val extractedUrl = normalizedData.substring(startIndex, endIndex)
                    Log.d("VideoViewModel", "SUCCESS: Found m3u8 URL: $extractedUrl")
                    _videoUrl.value = extractedUrl
                    return
                }
            }
        }
        Log.e("VideoViewModel", "Failed to find 'file:\"' in any script tag")
    }

    private fun extractImageData(htmlContent: String) {
        val document: Document = Jsoup.parse(htmlContent)
        Log.d("VideoViewModel", "--- ANALYSE DES IMAGES ---")
        
        // 1. Chercher dans les meta tags (plus fiables)
        val ogImage = document.select("meta[property=og:image]").attr("content")
        val twitterImage = document.select("meta[name=twitter:image]").attr("content")
        
        Log.d("VideoViewModel", "Meta og:image: '$ogImage'")
        Log.d("VideoViewModel", "Meta twitter:image: '$twitterImage'")

        if (ogImage.isNotEmpty()) {
            Log.d("VideoViewModel", "✅ SUCCESS: Image trouvée via og:image: $ogImage")
            _imageUrl.value = ogImage
            return
        }
        if (twitterImage.isNotEmpty()) {
            Log.d("VideoViewModel", "✅ SUCCESS: Image trouvée via twitter:image: $twitterImage")
            _imageUrl.value = twitterImage
            return
        }

        // 2. Chercher les images dans le corps de la page
        val imgElements = document.getElementsByTag("img")
        Log.d("VideoViewModel", "Nombre de balises <img> trouvées: ${imgElements.size}")
        
        for ((index, element) in imgElements.withIndex()) {
            val src = element.attr("src")
            val dataSrc = element.attr("data-src")
            val lazySrc = element.attr("lazy-src")
            val alt = element.attr("alt")
            val className = element.className()
            
            Log.d("VideoViewModel", "Img[$index] -> src: '$src', data-src: '$dataSrc', lazy-src: '$lazySrc', alt: '$alt', class: '$className'")

            // On teste plusieurs attributs car certains sites utilisent le lazy-loading
            val potentialUrl = when {
                dataSrc.isNotEmpty() -> dataSrc
                lazySrc.isNotEmpty() -> lazySrc
                else -> src
            }

            if (potentialUrl.isNotEmpty()) {
                val lowerUrl = potentialUrl.lowercase()
                if (lowerUrl.endsWith(".jpg") || 
                    lowerUrl.endsWith(".jpeg") || 
                    lowerUrl.endsWith(".png") || 
                    lowerUrl.endsWith(".webp") ||
                    lowerUrl.contains("themoviedb.org") ||
                    className.contains("poster", ignoreCase = true)) {
                    
                    Log.d("VideoViewModel", "✅ SUCCESS: Image matchée: $potentialUrl")
                    _imageUrl.value = potentialUrl
                    return
                }
            }
        }
        
        Log.w("VideoViewModel", "❌ Aucune image n'a matché les filtres")
        _imageUrl.value = "https://via.placeholder.com/150"
    }

    private fun extractHostFromUrl(url: String): String {
        return try {
            val uri = URI(url)
            val hostWithScheme = "${uri.scheme}://${uri.host}"
            "$hostWithScheme/iframe/"
        } catch (e: Exception) {
            Log.e("VideoViewModel", "Error extracting host from $url: ${e.message}")
            ""
        }
    }

    private fun extractDescription(htmlContent: String) {
        val document: Document = Jsoup.parse(htmlContent)
        
        // 1. Chercher le texte dans "CANEVAS DU FILM"
        val canevasHeader = document.select("b i b:contains(CANEVAS DU FILM)").first()
        var text: String? = null
        
        if (canevasHeader != null) {
            val canevasParagraph = canevasHeader.parents().firstOrNull { it.tagName() == "p" }
                ?.nextElementSibling()
            text = canevasParagraph?.text()
        }
        
        // 2. Fallback sur la meta description si vide
        if (text.isNullOrEmpty()) {
            text = document.select("meta[name=description]").attr("content")
        }

        _description.value = if (!text.isNullOrEmpty()) text else "Aucune description disponible."
        Log.d("VideoViewModel", "Description: ${_description.value?.take(50)}...")
    }
}
