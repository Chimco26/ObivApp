package com.example.obivapp2.screens

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
import androidx.compose.foundation.layout.fillMaxSize
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
            val player = ExoPlayer.Builder(context).build().apply {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))

                setMediaSource(mediaSource)
                prepare()
                play()
            }

            exoPlayer = player

            onDispose {
                player.release()
                exoPlayer = null
            }
        } ?: onDispose { }

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