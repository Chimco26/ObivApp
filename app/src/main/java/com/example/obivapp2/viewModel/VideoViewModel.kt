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
    private val _imageUrl = mutableStateOf<String?>(null)
    private val _videoUrlToShare = mutableStateOf<String?>(null)
    val videoUrl: State<String?> get() = _videoUrl
    val imageUrl: State<String?> get() = _imageUrl
    val videoUrlToShare: State<String?> get() = _videoUrlToShare

    fun resetLinkVideo(){
        _videoUrl.value = null
        _imageUrl.value = null
        _videoUrlToShare.value = null
    }

    fun isDataNull() : Boolean{
        return _videoUrl.value == null
    }

    // reçoit l'url en paramètre et va récuperer la page html.
    fun fetchLinkVideo(url: String) {
        viewModelScope.launch {
            try {
                val response: Response<ResponseBody> = RetrofitInstance.api.getVideo(url)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val htmlContent = responseBody.string()
                        extractJpgImage(htmlContent)
                        parseHtmlForVideoUrl(htmlContent)
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

    // reçois tout le block html et retrouve le lien de la plateforme "mayicloud".
    private suspend fun parseHtmlForVideoUrl(htmlContent: String) {
        val document: Document = Jsoup.parse(htmlContent)
        val iframes = document.select("iframe[src]")
        for (iframe in iframes) {
            val src = iframe.attr("src")
            if (src.contains("cloudhousr")) {
                _videoUrlToShare.value = src
                val videoId = src.substringAfterLast("/")
                fetchVideo(videoId)
            }
        }
    }

    // reçoit l'url du lien vers la plateforme et charge la page puis envoit toute la page à une autre fonction.
    private suspend fun fetchVideo(url: String) {
        withContext(Dispatchers.IO) {
            try {
                val response: Response<ResponseBody> = RetrofitInstance.apiCld.getVideo(url)
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val htmlContent = responseBody.string()
                        extractM3U8Link(htmlContent)
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

    // reçoit toute la page html de lecture de la video et retrouve le fichier m3u8 pour le renvoyer à l'utilisateur.
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
                    Log.e("LOG PATH","aaa"+normalizedData.substring(startIndex, endIndex))
                    _videoUrl.value = normalizedData.substring(startIndex, endIndex)
                }
            }
        }
    }

    private fun extractJpgImage(htmlContent: String): String? {
        val document: Document = Jsoup.parse(htmlContent)

        // Récupérer toutes les balises <img> dans le document
        val imgElements = document.getElementsByTag("img")

        // Parcourir les balises <img> pour trouver une image en .jpg
        for (element in imgElements) {
            val imgUrl = element.attr("src")
            if (imgUrl.endsWith(".jpg", ignoreCase = true)) {
                _imageUrl.value = imgUrl
            }
        }
        return null // Si aucune image en .jpg n'est trouvée, retourne null
    }

}