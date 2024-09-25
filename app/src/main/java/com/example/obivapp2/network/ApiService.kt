import retrofit2.http.GET
import retrofit2.http.Url
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ApiService {
    @GET("m4tbnbwawm88e0q/home/obivap")
    suspend fun getHtmlPage(): String

    @GET
    suspend fun getVideo(@Url url: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("m4tbnbwawm88e0q/home/obivap")  // Complète l'URL ici
    fun searchMovie(@Field("searchword") searchword: String): Call<ResponseBody>
}
