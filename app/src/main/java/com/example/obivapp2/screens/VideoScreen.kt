package com.example.obivapp2.screens

import android.app.Activity
import android.util.Log
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View.OnTouchListener
import androidx.core.view.GestureDetectorCompat
import kotlinx.coroutines.Job

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

    // Auto-masquage des contrôles
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
            playerView?.hideController()
        }
    }

    // Gestion du mode plein écran
    LaunchedEffect(isFullscreen) {
        window?.let { win ->
            val windowInsetsController = WindowCompat.getInsetsController(win, view)
            if (isFullscreen) {
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(videoUrl) {
        videoUrl?.let { url ->
            Log.d("VideoScreen", "=== INITIALISATION LECTEUR ===")
            
            val player = ExoPlayer.Builder(context).build().apply {
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
                player.release()
                exoPlayer = null
            }
        }

        onDispose { }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text(videoTitle ?: "Video Screen") },
                    actions = {
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, "Plein écran")
                        }
                    }
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
                            }

                            // Désactiver les contrôles par défaut
                            setOnClickListener(null)
                            
                            playerView = this
                        }
                    },
                    update = { view ->
                        view.setFullscreenButtonClickListener { isFullScreenMode ->
                            isFullscreen = isFullScreenMode
                        }
                    }
                )
            } ?: run {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
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
