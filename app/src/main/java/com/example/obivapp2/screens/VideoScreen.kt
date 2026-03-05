package com.example.obivapp2.screens

import android.app.Activity
import android.util.Log
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.obivapp2.viewModel.DownloadViewModel
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
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
    
    // Source de vérité pour le timer d'auto-masquage
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Logique centrale : Réinitialise le timer et force l'affichage de TOUS les contrôles
    val resetControlsTimer = {
        lastInteractionTime = System.currentTimeMillis()
        if (!showControls) {
            showControls = true
        }
        playerView?.showController()
    }

    // Masquage automatique centralisé (5 secondes d'inactivité)
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(5000)
            showControls = false
            playerView?.hideController()
        }
    }

    // Gestion du mode plein écran et des insets
    LaunchedEffect(isFullscreen) {
        try {
            if (activity?.isFinishing == true || activity?.isDestroyed == true) return@LaunchedEffect
            window?.let { win ->
                val windowInsetsController = WindowCompat.getInsetsController(win, view)
                if (isFullscreen) {
                    // Masquer les barres système
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    
                    // Autoriser l'utilisation de l'espace de l'encoche (notch)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        win.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    
                    // Permettre au contenu de s'étendre sur tout l'écran (Edge-to-Edge)
                    WindowCompat.setDecorFitsSystemWindows(win, false)

                    delay(50)
                    if (activity?.isFinishing == true || activity?.isDestroyed == true) return@LaunchedEffect
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    if (activity?.isFinishing == true || activity?.isDestroyed == true) return@LaunchedEffect
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        win.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                    WindowCompat.setDecorFitsSystemWindows(win, true)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoScreen", "Erreur plein écran", e)
        }
    }

    // Nettoyage lors de la sortie de l'écran pour restaurer les paramètres de la fenêtre
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    win.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(videoUrl) {
        videoUrl?.let { url ->
            val isLocalFile = url.startsWith("/") || url.startsWith("file://")
            val isMp4 = url.lowercase().endsWith(".mp4") || url.lowercase().endsWith(".mkv")
            
            val player = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VideoScreen", "Erreur lecture ($url): ${error.message}")
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        resetControlsTimer()
                    }
                })

                val mediaItem = MediaItem.fromUri(Uri.parse(url))
                val mediaSource = if (isLocalFile || isMp4) {
                    ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                        .createMediaSource(mediaItem)
                } else {
                    HlsMediaSource.Factory(DefaultHttpDataSource.Factory().apply {
                        setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                        setAllowCrossProtocolRedirects(true)
                    }).createMediaSource(mediaItem)
                }

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
                TopAppBar(title = { Text(videoTitle ?: "Lecteur Vidéo") })
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (!isFullscreen) paddingValues else PaddingValues(0.dp))
                .background(Color.Black)
        ) {
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
                            
                            // On laisse notre code Compose gérer le timeout
                            controllerShowTimeoutMs = 0 
                            
                            // Synchronisation de l'état
                            setControllerVisibilityListener(object : StyledPlayerView.ControllerVisibilityListener {
                                override fun onVisibilityChanged(visibility: Int) {
                                    showControls = visibility == android.view.View.VISIBLE
                                    if (showControls) lastInteractionTime = System.currentTimeMillis()
                                }
                            })
                            
                            val gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDown(e: MotionEvent): Boolean {
                                    isWaitingForDoubleTap = true
                                    tapJob?.cancel()
                                    tapJob = scope.launch {
                                        delay(300)
                                        if (isWaitingForDoubleTap) {
                                            if (showControls) {
                                                hideController()
                                                showControls = false
                                            } else {
                                                resetControlsTimer()
                                            }
                                            isWaitingForDoubleTap = false
                                        }
                                    }
                                    return true
                                }

                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    tapJob?.cancel()
                                    isWaitingForDoubleTap = false
                                    if (e.x < width / 2) player.seekTo(player.currentPosition - 10000)
                                    else player.seekTo(player.currentPosition + 10000)
                                    resetControlsTimer()
                                    return true
                                }
                            })

                            setOnTouchListener { v, event ->
                                gestureDetector.onTouchEvent(event)
                                true
                            }
                            
                            // Forcer l'affichage au lancement
                            showController() 
                            playerView = this
                        }
                    },
                    update = { view ->
                        view.setFullscreenButtonClickListener { isFullScreenMode ->
                            isFullscreen = isFullScreenMode
                            resetControlsTimer()
                        }
                        // Forcer le mode ZOOM en plein écran pour éliminer toutes les bandes noires
                        view.resizeMode = if (isFullscreen) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM 
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                )
            } ?: run {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Flèches personnalisées (liées à la même logique showControls)
            if (showControls && exoPlayer != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { 
                            exoPlayer?.let { it.seekTo(it.currentPosition - 10000) }
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 48.dp)
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }

                    IconButton(
                        onClick = { 
                            exoPlayer?.let { it.seekTo(it.currentPosition + 10000) }
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 48.dp)
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            }

            if (isFullscreen) {
                IconButton(
                    onClick = { 
                        isFullscreen = false 
                        resetControlsTimer()
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.FullscreenExit, null, tint = Color.White)
                }
            }
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            resetControlsTimer()
        } else {
            exoPlayer?.release()
            exoPlayer = null
            navController.popBackStack()
        }
    }
}
