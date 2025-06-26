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
        val document = Jsoup.parse(html)

        // Filtrer les liens contenant "b" juste avant "/trokap"
        val linksList = document.select("a[href]").mapNotNull {
            val url = it.attr("href")
            val parentDiv = it.closest("div")
            if (url.contains("b/trokap") && parentDiv != null && parentDiv.select(".trend_info").isEmpty()) {
                LinkData(text = it.text(), url = url)
            } else {
                null // Ignorer les liens qui ne correspondent pas
            }
        }

        _links.value = linksList
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

