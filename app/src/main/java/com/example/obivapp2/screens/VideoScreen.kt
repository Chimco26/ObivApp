package com.example.obivapp2.screens

import android.app.Activity
import android.util.Log
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.obivapp2.viewModel.VideoViewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.obivapp2.viewModel.DownloadViewModel
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource


@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
fun VideoScreen(
    navController: NavController,
    viewModel: VideoViewModel,
    downloadViewModel: DownloadViewModel
) {
    val videoUrl by viewModel.videoUrl
    val context = LocalContext.current
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }

    DisposableEffect(videoUrl) {
        videoUrl?.let { url ->
            Log.d("VideoScreen", "=== INITIALISATION LECTEUR ===")
            Log.d("VideoScreen", "URL vidéo reçue: $url")
            
            val player = ExoPlayer.Builder(context).build().apply {
                // Ajouter un listener pour les erreurs
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VideoScreen", "🔴 ERREUR LECTURE: ${error.message}")
                        Log.e("VideoScreen", "Type d'erreur: ${error.errorCode}")
                        Log.e("VideoScreen", "Cause: ${error.cause}")
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_IDLE -> Log.d("VideoScreen", "État: IDLE")
                            Player.STATE_BUFFERING -> Log.d("VideoScreen", "État: BUFFERING")
                            Player.STATE_READY -> Log.d("VideoScreen", "✅ État: READY - Vidéo prête!")
                            Player.STATE_ENDED -> Log.d("VideoScreen", "État: ENDED")
                        }
                    }
                })

                val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                    setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    setAllowCrossProtocolRedirects(true)
                    
                    // Extraire le domaine de l'URL M3U8 pour le referer
                    val uri = Uri.parse(url)
                    val baseUrl = "${uri.scheme}://${uri.host}"
                    
                    Log.d("VideoScreen", "Domaine extrait pour referer: $baseUrl")
                    
                    // En-têtes complets pour éviter l'erreur 403
                    val headers = mapOf(
                        "Accept" to "*/*",
                        "Accept-Encoding" to "identity",
                        "Accept-Language" to "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7",
                        "Cache-Control" to "no-cache",
                        "Connection" to "keep-alive",
                        "Referer" to baseUrl,
                        "Origin" to baseUrl,
                        "Sec-Fetch-Site" to "same-origin",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Ch-Ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
                        "Sec-Ch-Ua-Mobile" to "?0",
                        "Sec-Ch-Ua-Platform" to "\"Windows\""
                    )
                    setDefaultRequestProperties(headers)
                }
                
                val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))

                Log.d("VideoScreen", "MediaSource créé avec HLS")
                setMediaSource(mediaSource)
                prepare()
                play()
                Log.d("VideoScreen", "Lecteur configuré et lecture lancée")
            }

            exoPlayer = player

            onDispose {
                Log.d("VideoScreen", "Libération du lecteur")
                player.release()
                exoPlayer = null
            }
        } ?: onDispose { 
            Log.d("VideoScreen", "Aucune URL vidéo disponible")
        }

        onDispose { }
    }

    Scaffold(
        topBar = {
            if (!isLandscape()) {
                TopAppBar(title = { Text("Video Screen") })
            }
        }
    ) { paddingValues ->

       // Button(onClick = { videoUrl?.let { downloadViewModel.downloadHlsStream(it) } }) {
         //   Text("Download HLS Stream")
        //}
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            exoPlayer?.let { player ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    val screenWidth = size.width
                                    val position = player.currentPosition
                                    if (offset.x < screenWidth / 2) {
                                        // Double tap on the left half
                                        player.seekTo(position - 5000)
                                    } else {
                                        // Double tap on the right half
                                        player.seekTo(position + 10000)
                                    }
                                }
                            )
                        }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            PlayerView(context).apply {
                                this.player = player
                            }
                        }
                    )
                    // 👇 Le bouton rotation dans un coin
                    ToggleOrientationButton(
                        // Aligné en bas à droite par exemple
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    )
                }
            } ?: run {
                Text(text = "Loading video...", modifier = Modifier.padding(16.dp))
            }
        }
        BackHandler {
            exoPlayer?.release()
            exoPlayer = null
            navController.popBackStack()
        }
    }
}

@Composable
fun ToggleOrientationButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isLandscape = remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            isLandscape.value = !isLandscape.value
            activity?.requestedOrientation = if (isLandscape.value)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isLandscape.value)
                Icons.Default.FullscreenExit
            else
                Icons.Default.Fullscreen,
            contentDescription = if (isLandscape.value) "Passer en portrait" else "Passer en paysage",
            tint = Color.Black
        )
    }
}
