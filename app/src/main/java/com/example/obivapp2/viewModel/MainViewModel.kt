import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.viewModel.LinkData
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainViewModel : ViewModel() {
    private val _links = mutableStateOf<List<LinkData>>(emptyList())
    val links: State<List<LinkData>> get() = _links

    fun fetchLinks() {
        viewModelScope.launch {
            try {
                val html = RetrofitInstance.api.getHtmlPage()
                prepareList(html)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun prepareList(html: String) {
        val document = Jsoup.parse(html)

        // Filtrer les liens contenant "b" juste avant "/obivap"
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
                val call = RetrofitInstance.api.searchMovie(videoName)

                // Appel asynchrone avec Retrofit
                call.enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful) {
                            val responseBody = response.body()
                            if (responseBody != null) {
                                // Convertir la réponse brute en String
                                prepareList(responseBody.string())
                            }
                        } else {
                            // Gérer les erreurs ici (par ex., code HTTP non 200)
                        }
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        // Gérer les erreurs de connexion
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
