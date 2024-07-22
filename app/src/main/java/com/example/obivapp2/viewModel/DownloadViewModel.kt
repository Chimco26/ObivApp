package com.example.obivapp2.viewModel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.offline.*
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadManager: DownloadManager
    private val dataSourceFactory: DefaultDataSource.Factory
    private val renderersFactory: RenderersFactory

    init {
        val downloadCache = getDownloadCache()
        val databaseProvider = getDatabaseProvider()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(Util.getUserAgent(application, "yourApplicationName"))
        }
        dataSourceFactory = DefaultDataSource.Factory(application, httpDataSourceFactory)
        renderersFactory = DefaultRenderersFactory(application)
        downloadManager = DownloadManager(
            application,
            databaseProvider,
            downloadCache,
            httpDataSourceFactory
        )
    }

    fun downloadHlsStream(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadRequest = DownloadRequest.Builder(url, Uri.parse(url)).build()
            val downloadHelper = DownloadHelper.forHls(
                getApplication(),
                Uri.parse(url),
                dataSourceFactory,
                renderersFactory
            )
            downloadHelper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper) {
                    // La préparation est terminée, ajoutez la demande de téléchargement
                    downloadManager.addDownload(downloadRequest)
                }
                // Ajoutez les méthodes supplémentaires si nécessaires
                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    Log.e("Download", "Preparation error: ${e.message}")
                }
            })
        }
    }

    private fun getDownloadCache(): SimpleCache {
        val downloadContentDirectory = File(
            getApplication<Application>().getExternalFilesDir(null),
            "downloads"
        )
        val evictor = LeastRecentlyUsedCacheEvictor(1024 * 1024 * 100) // 100 MB
        return SimpleCache(downloadContentDirectory, evictor, getDatabaseProvider())
    }

    private fun getDatabaseProvider(): ExoDatabaseProvider {
        return ExoDatabaseProvider(getApplication())
    }
}


