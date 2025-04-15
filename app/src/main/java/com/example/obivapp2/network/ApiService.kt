import retrofit2.http.GET
import retrofit2.http.Url
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ApiService {
    @GET("j5f9d3qwgkp/home/trokap")
    suspend fun getHtmlPage(): String

    @GET
    suspend fun getVideo(@Url url: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("j5f9d3qwgkp/home/trokap")  // Complète l'URL ici
    fun searchMovie(@Field("searchword") searchword: String): Call<ResponseBody>
}
