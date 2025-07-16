import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.viewModel.LinkData
import com.example.obivapp2.network.RetrofitInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainViewModel : ViewModel() {
    private val _links = mutableStateOf<List<LinkData>>(emptyList())
    val links: State<List<LinkData>> get() = _links

    fun fetchLinks() {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Début de la requête API...")
                val response = RetrofitInstance.api.getHtmlPage()
                if (response.isSuccessful) {
                    val html = response.body()?.string() ?: ""
                    Log.d("MainViewModel", "Réponse reçue, taille: ${html.length}")
                    Log.d("MainViewModel", "Premiers 200 caractères: ${html.take(200)}")
                    prepareList(html)
                } else {
                    Log.e("MainViewModel", "Erreur HTTP: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Erreur lors de la requête API: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    private fun prepareList(html: String) {
        try {
            Log.d("MainViewModel", "Début du parsing HTML")
            Log.d("MainViewModel", "Encodage de la chaîne: ${html.encodeToByteArray().contentToString().take(100)}")
            
            // Essayer de détecter l'encodage
            val detectedCharset = when {
                html.startsWith("\u00EF\u00BB\u00BF") -> "UTF-8"
                html.startsWith("\u00FE\u00FF") -> "UTF-16BE"
                html.startsWith("\u00FF\u00FE") -> "UTF-16LE"
                else -> "Unknown"
            }
            Log.d("MainViewModel", "Encodage détecté: $detectedCharset")
            
            val document = Jsoup.parse(html)
            Log.d("MainViewModel", "Document parsé, titre: ${document.title()}")
            
            // Log the full HTML structure
            Log.d("MainViewModel", "Structure HTML:\n${document.outerHtml().take(500)}")
            
            // Sélectionner tous les liens
            val allLinks = document.select("a")
            Log.d("MainViewModel", "Nombre total de liens trouvés: ${allLinks.size}")
            
            allLinks.forEach { link ->
                Log.d("MainViewModel", "Lien trouvé - href: ${link.attr("href")}, text: ${link.text()}")
            }
            
            val linksList = document.select("#hann a").mapNotNull { element ->
                val url = element.attr("href")
                val text = element.text().trim()
                
                if (url.isNotEmpty() && text.isNotEmpty()) {
                    Log.d("MainViewModel", "Lien valide trouvé: $text - $url")
                    LinkData(text = text, url = url)
                } else {
                    Log.d("MainViewModel", "Lien ignoré car vide")
                    null
                }
            }
            
            Log.d("MainViewModel", "Nombre de liens trouvés dans #hann: ${linksList.size}")
            _links.value = linksList
            
        } catch (e: Exception) {
            Log.e("MainViewModel", "Erreur lors du parsing HTML", e)
            e.printStackTrace()
        }
    }

    fun searchVideo(videoName: String){
        viewModelScope.launch {
            try {
                val responseBody = RetrofitInstance.api.searchMovie(videoName).await()
                responseBody?.string()?.let { html ->
                    prepareList(html)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}

suspend fun Call<ResponseBody>.await(): ResponseBody? = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback<ResponseBody> {
        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
            if (response.isSuccessful) {
                cont.resume(response.body())
            } else {
                cont.resume(null)
            }
        }

        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
            cont.resumeWithException(t)
        }
    })
}

