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
        fetchJob?.cancel()
        _videoUrl.value = null
        _imageUrl.value = null
        _videoUrlToShare.value = null
        _description.value = null
        _title.value = null
        _currentUrl.value = null
    }

    fun isDataNull() : Boolean{
        return (_videoUrl.value == null) || (_imageUrl.value == null) || (_description.value == null) || (_videoUrlToShare.value == null)
    }

    fun fetchLinkVideo(url: String, title: String? = null) {
        resetLinkVideo()
        _currentUrl.value = url
        _title.value = title
        
        fetchJob = viewModelScope.launch {
            try {
                val response: Response<ResponseBody> = RetrofitInstance.api.getVideo(url)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val htmlContent = responseBody.string()
                        extractJpgImage(htmlContent)
                        parseHtmlForVideoUrl(htmlContent)
                        extractCanevasText(htmlContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("FetchVideo", "Error: ${e.message}")
            }
        }
    }

    private suspend fun parseHtmlForVideoUrl(htmlContent: String) {
        val document: Document = Jsoup.parse(htmlContent)
        val iframes = document.select("iframe[src]")
        for (iframe in iframes) {
            val src = iframe.attr("src")
            _videoUrlToShare.value = src
            val videoId = src.substringAfterLast("/")
            val newHost = extractHostFromUrl(src)
            fetchVideo(videoId, newHost)
        }
    }

    private suspend fun fetchVideo(url: String, newHost: String) {
        withContext(Dispatchers.IO) {
            try {
                val myApiCldTest = Retrofit.Builder()
                    .baseUrl(newHost)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
                
                val response = myApiCldTest.getVideo(url)
                
                if (response.isSuccessful) {
                    val htmlContent = response.body()?.string()
                    if (htmlContent != null) {
                        extractM3U8Link(htmlContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error: ${e.message}")
            }
            Unit // Assure que le bloc withContext ne retourne pas le résultat du if
        }
    }

    private fun extractM3U8Link(htmlContent: String) {
        val document: Document = Jsoup.parse(htmlContent)
        val scriptElements = document.getElementsByTag("script")

        for (element in scriptElements) {
            val scriptData = element.data()
            val normalizedData = scriptData.replace("\\s".toRegex(), "")
            if (normalizedData.contains("file:\"")) {
                val startIndex = normalizedData.indexOf("file:\"") + 6
                val endIndex = normalizedData.indexOf("\"", startIndex)
                if (startIndex != -1 && endIndex != -1) {
                    val extractedUrl = normalizedData.substring(startIndex, endIndex)
                    _videoUrl.value = extractedUrl
                    return
                }
            }
        }
    }

    private fun extractJpgImage(htmlContent: String): String? {
        val document: Document = Jsoup.parse(htmlContent)
        val imgElements = document.getElementsByTag("img")
        for (element in imgElements) {
            val imgUrl = element.attr("src")
            if (imgUrl.endsWith(".jpg", ignoreCase = true)) {
                _imageUrl.value = imgUrl
            }
        }
        return null
    }

    private fun extractHostFromUrl(url: String): String {
        return try {
            val uri = URI(url)
            val hostWithScheme = "${uri.scheme}://${uri.host}"
            "$hostWithScheme/iframe/"
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractCanevasText(htmlContent: String): String? {
        val document: Document = Jsoup.parse(htmlContent)
        val canevasHeader = document.select("b i b:contains(CANEVAS DU FILM)").first()
        if (canevasHeader != null) {
            val canevasParagraph = canevasHeader.parents().firstOrNull { it.tagName() == "p" }
                ?.nextElementSibling()
            _description.value = canevasParagraph?.text()
        }
        return null
    }
}
