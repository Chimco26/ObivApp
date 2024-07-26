package com.example.obivapp2.viewModel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.HttpException
import java.io.IOException
import okhttp3.ResponseBody
import retrofit2.Response


class VideoViewModel : ViewModel() {
    private val _videoUrl = mutableStateOf<String?>(null)
    private val _videoUrlToShare = mutableStateOf<String?>(null)
    val videoUrl: State<String?> get() = _videoUrl
    val videoUrlToShare: State<String?> get() = _videoUrlToShare

    fun fetchLinkVideo(url: String) {
        viewModelScope.launch {
            try {
                val response: Response<ResponseBody> = RetrofitInstance.api.getVideo(url)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val htmlContent = responseBody.string()
                        val videoUrl = withContext(Dispatchers.IO) {
                            parseHtmlForVideoUrl(htmlContent)
                        }
                        _videoUrl.value = videoUrl
                    } else {
                        Log.e("FetchVideo", "Response body is null")
                    }
                } else {
                    Log.e("FetchVideo", "HTTP error: ${response.code()} ${response.message()}")
                }
            } catch (e: HttpException) {
                Log.e("FetchVideo", "HTTP error: ${e.code()} ${e.message()}")
            } catch (e: IOException) {
                Log.e("FetchVideo", "Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e("FetchVideo", "Unknown error: ${e.message}")
            }
        }
    }

    private suspend fun parseHtmlForVideoUrl(htmlContent: String): String? {
        val document: Document = Jsoup.parse(htmlContent)
        val iframes = document.select("iframe[src]")
        for (iframe in iframes) {
            val src = iframe.attr("src")
            if (src.contains("cldmax")) {
                val videoId = src.substringAfterLast("/")
                _videoUrlToShare.value = src
                return fetchVideo(videoId)
            }
        }

        // Retourne null si aucune URL ne correspond
        return null
    }

    private suspend fun fetchVideo(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response: Response<ResponseBody> = RetrofitInstance.apiCld.getVideo(url)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val htmlContent = responseBody.string()
                        return@withContext extractVideoLink(htmlContent)
                    } else {
                        Log.e("FetchVideo", "Response body is null")
                    }
                } else {
                    Log.e("FetchVideo", "HTTP error: ${response.code()} ${response.message()}")
                }
            } catch (e: HttpException) {
                Log.e("FetchVideo", "HTTP error: ${e.code()} ${e.message()}")
            } catch (e: IOException) {
                Log.e("FetchVideo", "Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e("FetchVideo", "Unknown error: ${e.message}")
            }
            return@withContext null
        }
    }

    private fun extractVideoLink(htmlContent: String): String? {
        val document: Document = Jsoup.parse(htmlContent)
        val scriptElements = document.getElementsByTag("script")

        for (element in scriptElements) {
            val scriptData = element.data()
            if (scriptData.contains("jwplayer")) {
                val startIndex = scriptData.indexOf("file: \"") + 7
                val endIndex = scriptData.indexOf("\"", startIndex)
                if (startIndex != -1 && endIndex != -1) {
                    return scriptData.substring(startIndex, endIndex)
                }
            }
        }
        return null
    }

}