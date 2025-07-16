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
    private val _title = mutableStateOf<String?>(null)
    val videoUrl: State<String?> get() = _videoUrl
    val imageUrl: State<String?> get() = _imageUrl
    val description: State<String?> get() = _description
    val videoUrlToShare: State<String?> get() = _videoUrlToShare
    val title: State<String?> get() = _title

    fun resetLinkVideo(){
        _videoUrl.value = null
        _imageUrl.value = null
        _videoUrlToShare.value = null
        _description.value = null
        _title.value = null
    }

    fun isDataNull() : Boolean{
        return (_videoUrl.value == null) || (_imageUrl.value == null) || (_description.value == null) || (_videoUrlToShare.value == null)
    }

    // reçoit l'url en paramètre et va récuperer la page html.
    fun fetchLinkVideo(url: String, title: String? = null) {
        _title.value = title
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
                Log.d("VideoViewModel", "=== FETCH VIDEO ===")
                Log.d("VideoViewModel", "URL: $url")
                Log.d("VideoViewModel", "Host: $newHost")
                
                val myApiCldTest: ApiService by lazy {
                    Retrofit.Builder()
                        .baseUrl(newHost)
                        .addConverterFactory(ScalarsConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)
                }
                
                Log.d("VideoViewModel", "Envoi requête à: $newHost$url")
                val response: Response<ResponseBody> = myApiCldTest.getVideo(url)
                
                Log.d("VideoViewModel", "Code réponse: ${response.code()}")
                Log.d("VideoViewModel", "Message: ${response.message()}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val htmlContent = responseBody.string()
                        Log.d("VideoViewModel", "HTML reçu - Taille: ${htmlContent.length}")
                        Log.d("VideoViewModel", "Premiers 300 caractères: ${htmlContent.take(300)}")
                        extractM3U8Link(htmlContent)
                    } else {
                        Log.e("VideoViewModel", "Response body is null")
                    }
                } else {
                    Log.e("VideoViewModel", "HTTP error: ${response.code()} ${response.message()}")
                }
            } catch (e: HttpException) {
                Log.e("VideoViewModel", "HTTP error: ${e.code()} ${e.message()}")
            } catch (e: IOException) {
                Log.e("VideoViewModel", "Network error: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Unknown error: ${e.message}", e)
            }
            return@withContext null
        }
    }

    // reçoit toute la page html de lecture de la video et retrouve le fichier m3u8 pour le renvoyer à l'utilisateur.
    private fun extractM3U8Link(htmlContent: String) {
        Log.d("VideoViewModel", "=== DEBUT EXTRACTION M3U8 ===")
        Log.d("VideoViewModel", "Taille HTML reçu: ${htmlContent.length}")
        
        val document: Document = Jsoup.parse(htmlContent)
        val scriptElements = document.getElementsByTag("script")
        
        Log.d("VideoViewModel", "Nombre de scripts trouvés: ${scriptElements.size}")

        for ((index, element) in scriptElements.withIndex()) {
            val scriptData = element.data()
            Log.d("VideoViewModel", "Script $index - Taille: ${scriptData.length}")
            
            val normalizedData = scriptData.replace("\\s".toRegex(), "")
            if (normalizedData.contains("file:\"")) {
                Log.d("VideoViewModel", "Script $index contient 'file:\"'")
                Log.d("VideoViewModel", "Contenu du script: ${scriptData.take(500)}...")
                
                val startIndex = normalizedData.indexOf("file:\"") + 6
                val endIndex = normalizedData.indexOf("\"", startIndex)
                if (startIndex != -1 && endIndex != -1) {
                    val extractedUrl = normalizedData.substring(startIndex, endIndex)
                    Log.d("VideoViewModel", "🎯 M3U8 TROUVE: $extractedUrl")
                    
                    // Test de l'URL M3U8
                    viewModelScope.launch {
                        testM3U8Url(extractedUrl)
                    }
                    
                    _videoUrl.value = extractedUrl
                    return
                } else {
                    Log.e("VideoViewModel", "Erreur parsing: startIndex=$startIndex, endIndex=$endIndex")
                }
            } else {
                Log.d("VideoViewModel", "Script $index ne contient pas 'file:\"'")
                if (scriptData.isNotEmpty()) {
                    Log.d("VideoViewModel", "Premiers caractères: ${scriptData.take(100)}")
                }
            }
        }
        Log.e("VideoViewModel", "❌ AUCUN M3U8 TROUVE!")
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

    private suspend fun testM3U8Url(url: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("VideoViewModel", "🔍 TEST M3U8: $url")
                
                val testRetrofit = Retrofit.Builder()
                    .baseUrl("https://example.com/") // URL de base factice
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
                
                val response = testRetrofit.getVideo(url)
                Log.d("VideoViewModel", "Réponse M3U8 - Code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val content = response.body()?.string()?.take(200) ?: ""
                    Log.d("VideoViewModel", "✅ M3U8 accessible - Contenu: $content")
                } else {
                    Log.e("VideoViewModel", "❌ M3U8 inaccessible - ${response.code()}: ${response.message()}")
                }
                
            } catch (e: Exception) {
                Log.e("VideoViewModel", "❌ Erreur test M3U8: ${e.message}")
            }
        }
    }


}