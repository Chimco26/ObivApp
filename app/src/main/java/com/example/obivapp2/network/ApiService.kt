import retrofit2.http.GET
import retrofit2.http.Url
import okhttp3.ResponseBody
import retrofit2.Response

interface ApiService {
    @GET("m4tbnbwawm88e0q/home/obivap")
    suspend fun getHtmlPage(): String

    @GET
    suspend fun getVideo(@Url url: String): Response<ResponseBody>
}
