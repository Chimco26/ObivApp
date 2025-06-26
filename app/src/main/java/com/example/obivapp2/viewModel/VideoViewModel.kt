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


class VideoViewModel : ViewModel() {
    private val _videoUrl = mutableStateOf<String?>(null)
    private val _imageUrl = mutableStateOf<String?>(null)
    private val _description = mutableStateOf<String?>(null)
    private val _videoUrlToShare = mutableStateOf<String?>(null)
    val videoUrl: State<String?> get() = _videoUrl
    val imageUrl: State<String?> get() = _imageUrl
    val description: State<String?> get() = _description
    val videoUrlToShare: State<String?> get() = _videoUrlToShare

    fun resetLinkVideo(){
        _videoUrl.value = null
        _imageUrl.value = null
        _videoUrlToShare.value = null
        _description.value = null
    }

    fun isDataNull() : Boolean{
        return (_videoUrl.value == null) || (_imageUrl.value == null) || (_description.value == null) || (_videoUrlToShare.value == null)
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
                        extractCanevasText(htmlContent)
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
//            if (src.contains("tromcloud")) {
//            }
                _videoUrlToShare.value = src
                val videoId = src.substringAfterLast("/")
                val newHost = extractHostFromUrl(src)
                fetchVideo(videoId, newHost)
        }
    }

    // reçoit l'url du lien vers la plateforme et charge la page puis envoit toute la page à une autre fonction.
    private suspend fun fetchVideo(url: String, newHost: String) {
        withContext(Dispatchers.IO) {
            try {
                val myApiCldTest: ApiService by lazy {
                    Retrofit.Builder()
                        .baseUrl(newHost)
                        .addConverterFactory(ScalarsConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)
                }
                val response: Response<ResponseBody> = myApiCldTest.getVideo(url)
//                val response: Response<ResponseBody> = RetrofitInstance.apiCld.getVideo(url)
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

    private fun extractHostFromUrl(url: String): String {
        return try {
            val uri = URI(url)
            val hostWithScheme = "${uri.scheme}://${uri.host}" // Construit l'hôte complet avec le schéma
            "$hostWithScheme/iframe/" // Ajoute "/iframe/" à la fin
        } catch (e: Exception) {
            "" // En cas d'erreur, retourne une chaîne vide
        }
    }

    private fun extractCanevasText(htmlContent: String): String? {
        val document: Document = Jsoup.parse(htmlContent)

        // Trouver l'élément contenant le titre "CANEVAS DU FILM"
        val canevasHeader = document.select("b i b:contains(CANEVAS DU FILM)").first()

        if (canevasHeader != null) {
            // Naviguer pour trouver le paragraphe suivant
            val canevasParagraph = canevasHeader.parents().firstOrNull { it.tagName() == "p" }
                ?.nextElementSibling()

            // Extraire le texte du paragraphe s'il existe
            _description.value = canevasParagraph?.text()
        }
        return null // Si le texte n'est pas trouvé
    }


}