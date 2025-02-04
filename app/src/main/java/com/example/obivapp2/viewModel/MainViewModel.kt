package com.example.obivapp2.viewModel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.database.LinkDao
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainViewModel(private val linkDao: LinkDao) : ViewModel() {
    private val _links = mutableStateOf<List<LinkData>>(emptyList())
    val links: State<List<LinkData>> get() = _links

    init {
        // Charger les liens depuis la base au démarrage
        viewModelScope.launch {
            _links.value = linkDao.getAllLinks()
        }
    }

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

        val linksList = document.select("a[href]").mapNotNull {
            val url = it.attr("href")
            val parentDiv = it.closest("div")
            if (url.contains("b/niztal") && parentDiv != null && parentDiv.select(".trend_info").isEmpty()) {
                LinkData(text = it.text(), url = url)
            } else {
                null
            }
        }

        // Sauvegarde en base et mise à jour de la liste
        viewModelScope.launch {
            linksList.forEach { linkDao.insert(it) } // Room ignore les doublons si @Insert(onConflict = IGNORE)
            _links.value = linkDao.getAllLinks()
        }
    }

    fun searchVideo(videoName: String) {
        viewModelScope.launch {
            try {
                val call = RetrofitInstance.api.searchMovie(videoName)

                call.enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful) {
                            val responseBody = response.body()
                            if (responseBody != null) {
                                prepareList(responseBody.string())
                            }
                        }
                    }

                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        t.printStackTrace()
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
