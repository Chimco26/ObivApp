import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.obivapp2.viewModel.LinkData
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

class MainViewModel : ViewModel() {
    private val _links = mutableStateOf<List<LinkData>>(emptyList())
    val links: State<List<LinkData>> get() = _links

    fun fetchLinks() {
        viewModelScope.launch {
            try {
                val html = RetrofitInstance.api.getHtmlPage()
                val document = Jsoup.parse(html)
                val linksList = document.select("a[href]").map {
                    LinkData(text = it.text(), url = it.attr("href"))
                }
                _links.value = linksList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
