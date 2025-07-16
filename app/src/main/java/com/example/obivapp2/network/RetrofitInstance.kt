package com.example.obivapp2.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import okhttp3.ResponseBody
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.nio.charset.Charset
import okio.GzipSource
import okio.buffer
import okio.source

object RetrofitInstance {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Encoding", "gzip")  // On ne demande que gzip pour simplifier
                    .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cache-Control", "max-age=0")
                    .header("Cookie", "g=true")
                    .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    .build()

                val response = chain.proceed(request)
                
                Log.d("RetrofitInstance", "Headers de la réponse:")
                response.headers.forEach { (name, value) ->
                    Log.d("RetrofitInstance", "$name: $value")
                }

                val originalBody = response.body
                if (originalBody == null) {
                    Log.e("RetrofitInstance", "Corps de la réponse null")
                    return@addInterceptor response
                }

                val contentEncoding = response.header("Content-Encoding")
                Log.d("RetrofitInstance", "Content-Encoding: $contentEncoding")

                val bytes = originalBody.bytes()
                Log.d("RetrofitInstance", "Taille des données brutes: ${bytes.size}")
                Log.d("RetrofitInstance", "Premiers octets: ${bytes.take(20).joinToString(",")}")

                val decompressedString = try {
                    when (contentEncoding?.lowercase()) {
                        "gzip" -> {
                            val gzipSource = GzipSource(Buffer().write(bytes))
                            val buffer = gzipSource.buffer()
                            val result = buffer.readString(Charset.forName("UTF-8"))
                            gzipSource.close()
                            result
                        }
                        else -> String(bytes, Charset.forName("UTF-8"))
                    }
                } catch (e: Exception) {
                    Log.e("RetrofitInstance", "Erreur lors de la décompression", e)
                    String(bytes, Charset.forName("UTF-8"))
                }

                Log.d("RetrofitInstance", "Premiers caractères décompressés: ${decompressedString.take(100)}")

                response.newBuilder()
                    .body(ResponseBody.create(
                        "text/html; charset=UTF-8".toMediaTypeOrNull(),
                        decompressedString
                    ))
                    .removeHeader("Content-Encoding")
                    .build()
            }
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://avobiv.com/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
