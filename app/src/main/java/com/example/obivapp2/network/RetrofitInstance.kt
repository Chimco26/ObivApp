import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object RetrofitInstance {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://niztal.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
//    val apiCld: ApiService by lazy {
//        Retrofit.Builder()
//            .baseUrl("https://tromcloud.com/iframe/")
//            .addConverterFactory(ScalarsConverterFactory.create())
//            .build()
//            .create(ApiService::class.java)
//    }
}
