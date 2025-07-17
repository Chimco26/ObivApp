package com.example.obivapp2.screens

import android.app.Activity
import android.util.Log
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.obivapp2.viewModel.VideoViewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.obivapp2.viewModel.DownloadViewModel
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.Job

@Composable
fun VideoScreen(
    navController: NavController,
    viewModel: VideoViewModel,
    downloadViewModel: DownloadViewModel
) {
    val videoUrl by viewModel.videoUrl
    val videoTitle by viewModel.title
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val window = activity?.window
    val scope = rememberCoroutineScope()
    
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playerView: StyledPlayerView? by remember { mutableStateOf(null) }
    var showControls by remember { mutableStateOf(true) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var isWaitingForDoubleTap by remember { mutableStateOf(false) }

    // Gestion du mode plein écran
    LaunchedEffect(isFullscreen) {
        try {
            // Vérifier que l'activité est toujours valide
            if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                return@LaunchedEffect
            }
            
            window?.let { win ->
                val windowInsetsController = WindowCompat.getInsetsController(win, view)
                
                if (isFullscreen) {
                    // Cacher les barres système
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    
                    // Attendre un peu que les barres soient masquées
                    delay(50)
                    
                    // Vérifier à nouveau que l'activité est valide
                    if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                        return@LaunchedEffect
                    }
                    
                    // Changer l'orientation
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    // Vérifier à nouveau que l'activité est valide
                    if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                        return@LaunchedEffect
                    }
                    
                    // Changer l'orientation d'abord
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    
                    // Afficher les barres système
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        } catch (e: Exception) {
            Log.e("VideoScreen", "Erreur lors de la transition plein écran", e)
        }
    }

    // Auto-masquage des contrôles
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
            playerView?.hideController()
        }
    }

    DisposableEffect(videoUrl) {
        videoUrl?.let { url ->
            val player = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VideoScreen", "Erreur lecture: ${error.message}")
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> Log.d("VideoScreen", "Vidéo prête")
                        }
                    }
                })

                val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                    setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    setAllowCrossProtocolRedirects(true)
                    
                    val uri = Uri.parse(url)
                    val baseUrl = "${uri.scheme}://${uri.host}"
                    
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

                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }

            exoPlayer = player

            onDispose {
                try {
                    player.release()
                    exoPlayer = null
                } catch (e: Exception) {
                    Log.e("VideoScreen", "Erreur lors de la libération du lecteur", e)
                }
            }
        }

        onDispose { }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text(videoTitle ?: "Video Screen") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (!isFullscreen) paddingValues else PaddingValues(0.dp))
        ) {
            // Lecteur vidéo
            exoPlayer?.let { player ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        StyledPlayerView(context).apply {
                            this.player = player
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowFastForwardButton(false)
                            setShowRewindButton(false)
                            controllerHideOnTouch = false
                            controllerAutoShow = false
                            controllerShowTimeoutMs = 3000
                            
                            // Créer un détecteur de gestes personnalisé
                            val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDown(e: MotionEvent): Boolean {
                                    isWaitingForDoubleTap = true
                                    tapJob?.cancel()
                                    tapJob = scope.launch {
                                        delay(300) // Attendre pour voir si c'est un double tap
                                        if (isWaitingForDoubleTap) {
                                            // Si on arrive ici, c'était un simple tap
                                            showControls = !showControls
                                            if (showControls) {
                                                playerView?.showController()
                                            } else {
                                                playerView?.hideController()
                                            }
                                            isWaitingForDoubleTap = false
                                        }
                                    }
                                    return true
                                }

                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    tapJob?.cancel()
                                    isWaitingForDoubleTap = false
                                    val screenWidth = width
                                    if (e.x < screenWidth / 2) {
                                        player.seekTo(player.currentPosition - 10000)
                                    } else {
                                        player.seekTo(player.currentPosition + 10000)
                                    }
                                    return true
                                }

                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    return true // On ne fait rien ici car géré dans onDown
                                }
                            })

                            // Configurer le gestionnaire de toucher
                            setOnTouchListener { v, event ->
                                try {
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN -> {
                                            v.performClick()
                                        }
                                        MotionEvent.ACTION_UP -> {
                                            if (!isWaitingForDoubleTap) {
                                                tapJob?.cancel()
                                            }
                                        }
                                    }
                                    gestureDetector.onTouchEvent(event)
                                    true
                                } catch (e: Exception) {
                                    Log.e("VideoScreen", "Erreur dans le gestionnaire de toucher", e)
                                    false
                                }
                            }

                            // Désactiver les contrôles par défaut
                            setOnClickListener(null)
                            
                            playerView = this
                        }
                    },
                    update = { view ->
                        try {
                            view.setFullscreenButtonClickListener { isFullScreenMode ->
                                isFullscreen = isFullScreenMode
                            }
                        } catch (e: Exception) {
                            Log.e("VideoScreen", "Erreur dans la mise à jour du PlayerView", e)
                        }
                    }
                )
            } ?: run {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Bouton de sortie du plein écran (visible seulement en mode plein écran)
            if (isFullscreen) {
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.FullscreenExit,
                        contentDescription = "Sortir du plein écran",
                        tint = Color.White
                    )
                }
            }

            // Indicateurs de double tap
            if (showControls) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            "-10s",
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            "+10s",
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    // Gestion du retour arrière
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            try {
                exoPlayer?.release()
                exoPlayer = null
                navController.popBackStack()
            } catch (e: Exception) {
                Log.e("VideoScreen", "Erreur lors de la sortie", e)
                navController.popBackStack()
            }
        }
    }
}
