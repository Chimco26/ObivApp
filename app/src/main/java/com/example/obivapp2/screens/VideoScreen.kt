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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.example.obivapp2.database.AppDatabase
import com.example.obivapp2.ui.theme.LiquidAccent
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.LinkData
import com.example.obivapp2.viewModel.VideoViewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    navController: NavController,
    viewModel: VideoViewModel,
    downloadViewModel: DownloadViewModel
) {
    val videoUrl by viewModel.videoUrl
    val videoTitle by viewModel.title
    val imageUrl by viewModel.imageUrl
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
    
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val linkDao = remember { AppDatabase.getDatabase(context).linkDao() }

    val resetControlsTimer = {
        lastInteractionTime = System.currentTimeMillis()
        if (!showControls) {
            showControls = true
        }
        playerView?.showController()
    }

    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(5000)
            showControls = false
            playerView?.hideController()
        }
    }

    LaunchedEffect(isFullscreen) {
        try {
            if (activity?.isFinishing == true || activity?.isDestroyed == true) return@LaunchedEffect
            window?.let { win ->
                val windowInsetsController = WindowCompat.getInsetsController(win, view)
                if (isFullscreen) {
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        win.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    
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
                
                // Charger la position sauvegardée
                scope.launch {
                    val linkData = linkDao.getLinkByUrl(url)
                    linkData?.let {
                        if (it.lastWatchedPosition > 0 && it.lastWatchedPosition < it.totalDuration - 5000) {
                            seekTo(it.lastWatchedPosition)
                        }
                    }
                    playWhenReady = true
                }
            }

            exoPlayer = player
            
            // Sauvegarder la position périodiquement
            val progressJob = scope.launch {
                while (true) {
                    delay(5000)
                    exoPlayer?.let { p ->
                        if (p.isPlaying) {
                            linkDao.insert(
                                LinkData(
                                    url = url,
                                    text = videoTitle ?: "Sans titre",
                                    filePath = if (isLocalFile) url else null,
                                    lastWatchedPosition = p.currentPosition,
                                    totalDuration = p.duration,
                                    lastWatchedTimestamp = System.currentTimeMillis(),
                                    imageUrl = imageUrl
                                )
                            )
                        }
                    }
                }
            }

            onDispose {
                progressJob.cancel()
                exoPlayer?.let { p ->
                    scope.launch {
                        linkDao.insert(
                            LinkData(
                                url = url,
                                text = videoTitle ?: "Sans titre",
                                filePath = if (isLocalFile) url else null,
                                lastWatchedPosition = p.currentPosition,
                                totalDuration = p.duration,
                                lastWatchedTimestamp = System.currentTimeMillis(),
                                imageUrl = imageUrl
                            )
                        )
                        p.stop() // Arrêt propre de la lecture
                        p.release()
                    }
                }
                exoPlayer = null
            }
        }
        onDispose { }
    }

    Scaffold(
        backgroundColor = Color.Black,
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text(videoTitle ?: "Lecteur Vidéo", color = Color.White) },
                    backgroundColor = Color.Transparent,
                    elevation = 0.dp,
                    navigationIcon = {
                        IconButton(onClick = { 
                            exoPlayer?.stop() // On force l'arrêt
                            navController.popBackStack() 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
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
                            
                            controllerShowTimeoutMs = 0 
                            
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
                            
                            showController() 
                            playerView = this
                        }
                    },
                    update = { view ->
                        view.setFullscreenButtonClickListener { isFullScreenMode ->
                            isFullscreen = isFullScreenMode
                            resetControlsTimer()
                        }
                        view.resizeMode = if (isFullscreen) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM 
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                )
            } ?: run {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = LiquidAccent)
            }

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
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
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
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
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
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)
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
            exoPlayer?.stop() // On force l'arrêt aussi sur le retour arrière
            navController.popBackStack()
        }
    }
}
