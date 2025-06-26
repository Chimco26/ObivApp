package com.example.obivapp2.network

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response

interface ApiService {

    // 1. Récupère la page HTML principale (accueil)
    @GET("j5f9d3qwgkp/home/trokap")
    suspend fun getHtmlPage(): Response<ResponseBody>

    // 2. Récupère dynamiquement n'importe quelle page vidéo via URL
    @GET
    suspend fun getVideo(@Url url: String): Response<ResponseBody>

    // 3. Effectue une recherche via le formulaire de la page (si applicable)
    @FormUrlEncoded
    @POST("j5f9d3qwgkp/home/trokap")
    fun searchMovie(@Field("searchword") searchword: String): Call<ResponseBody>
}
