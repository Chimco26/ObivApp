package com.example.obivapp2.screens

import MainViewModel
import android.app.Activity
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.obivapp2.data.FavoriteMovie
import com.example.obivapp2.ui.theme.GlassBorder
import com.example.obivapp2.ui.theme.GlassWhite
import com.example.obivapp2.ui.theme.LiquidAccent
import com.example.obivapp2.utils.Permissions
import com.example.obivapp2.utils.shareLink
import com.example.obivapp2.viewModel.DownloadState
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.FavoritesViewModel
import com.example.obivapp2.viewModel.VideoViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun CreativeDownloadButton(
    state: DownloadState,
    onDownloadClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(targetState = state, label = "DownloadTransition")
    
    val backgroundColor by transition.animateColor(label = "Color") { s ->
        when (s) {
            is DownloadState.Success -> Color(0xFF4CAF50)
            is DownloadState.Error -> Color(0xFFF44336)
            is DownloadState.Downloading -> if (s.isPaused) Color.Gray else LiquidAccent
            else -> LiquidAccent
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.animateContentSize()
    ) {
        if (state is DownloadState.Idle) {
            IconButton(onClick = onDownloadClick) {
                Icon(Icons.Default.Download, contentDescription = "Télécharger", tint = Color.White)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(backgroundColor.copy(alpha = 0.2f), CircleShape)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                    val progress = when (state) {
                        is DownloadState.Downloading -> state.progress / 100f
                        is DownloadState.Success -> 1f
                        else -> 0f
                    }
                    CircularProgressIndicator(progress = 1f, color = Color.White.copy(alpha = 0.1f), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    CircularProgressIndicator(progress = progress, color = backgroundColor, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    
                    if (state is DownloadState.Downloading) {
                        IconButton(onClick = onPauseResumeClick, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    } else if (state is DownloadState.Success) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else if (state is DownloadState.Error) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                
                if (state is DownloadState.Downloading) {
                    Text(text = "${state.progress}%", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 2.dp))
                    IconButton(onClick = onCancelClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    videoViewModel: VideoViewModel,
    favoritesViewModel: FavoritesViewModel
) {
    val links by mainViewModel.links
    val isLoading by mainViewModel.isLoading
    var searchText by remember { mutableStateOf("") }
    var expandedItemIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val imageUrl by videoViewModel.imageUrl
    val description by videoViewModel.description
    var isDialogOpen by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var selectedDescription by remember { mutableStateOf("") }
    val downloadViewModel: DownloadViewModel = viewModel()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { 
            searchText = ""
            expandedItemIndex = null
            mainViewModel.fetchLinks() 
        }
    )

    LaunchedEffect(Unit) {
        try {
            errorMessage = null
            mainViewModel.fetchLinks()
            (context as? Activity)?.let { Permissions.requestAllPermissions(it) }
            downloadViewModel.setupDownloadEventListener()
        } catch (e: Exception) {
            errorMessage = "Erreur lors du chargement: ${e.message}"
        }
    }

    Scaffold(
        backgroundColor = Color.Transparent,
        topBar = { 
            TopAppBar(
                title = { Text("Obiv App", color = Color.White) },
                backgroundColor = Color.Transparent,
                elevation = 0.dp
            ) 
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).pullRefresh(pullRefreshState)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Le champ de recherche reste ici, en dehors du contenu défilable
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; mainViewModel.searchVideo(searchText) },
                    label = { Text("Rechercher", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = LiquidAccent,
                        focusedBorderColor = LiquidAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        backgroundColor = Color.White.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // La zone défilable commence ici
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (errorMessage != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(text = errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                        }
                    } else if (links.isEmpty() && !isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Aucun résultat trouvé", color = Color.White, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = GlassWhite,
                            border = BorderStroke(1.dp, GlassBorder),
                            elevation = 0.dp
                        ) {
                            Column {
                                links.forEachIndexed { index, linkData ->
                                    val isFav by favoritesViewModel.isFavorite(linkData.url).collectAsState(initial = false)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                            .combinedClickable(
                                                onClick = {
                                                    videoViewModel.resetLinkVideo()
                                                    videoViewModel.fetchLinkVideo(linkData.url, linkData.text)
                                                    expandedItemIndex = if (expandedItemIndex == index) null else index
                                                },
                                                onLongClick = {
                                                    if (expandedItemIndex == index && description != null) {
                                                        selectedDescription = description!!
                                                        showDescriptionDialog = true
                                                    } else {
                                                        selectedDescription = linkData.text
                                                        showDescriptionDialog = true
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = linkData.text,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.subtitle1,
                                                color = Color.White
                                            )
                                            IconButton(onClick = {
                                                favoritesViewModel.toggleFavorite(
                                                    FavoriteMovie(
                                                        url = linkData.url,
                                                        title = linkData.text,
                                                        imageUrl = if (expandedItemIndex == index) imageUrl else null,
                                                        description = if (expandedItemIndex == index) description else null,
                                                        videoUrl = if (expandedItemIndex == index) videoViewModel.videoUrl.value else null
                                                    ),
                                                    isFav
                                                )
                                            }) {
                                                Icon(
                                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Favori",
                                                    tint = if (isFav) Color.Red else Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        if (expandedItemIndex == index) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            if (videoViewModel.isDataNull()) {
                                                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(48.dp), color = LiquidAccent)
                                                }
                                            } else {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp), 
                                                    modifier = Modifier.fillMaxWidth().height(150.dp)
                                                ) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(imageUrl),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.size(100.dp, 150.dp).clip(RoundedCornerShape(12.dp)).clickable { isDialogOpen = true }
                                                    )
                                                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            IconButton(onClick = { videoViewModel.videoUrlToShare.value?.let { shareLink(context, it) } }) {
                                                                Icon(Icons.Default.Share, contentDescription = "Partager", tint = Color.White)
                                                            }
                                                            
                                                            val videoUrl = videoViewModel.videoUrl.value
                                                            val downloadState by remember(videoUrl) {
                                                                videoUrl?.let { url -> downloadViewModel.getDownloadState(url) } ?: MutableStateFlow(DownloadState.Idle)
                                                            }.collectAsState()

                                                            CreativeDownloadButton(
                                                                state = downloadState,
                                                                onDownloadClick = { videoViewModel.videoUrl.value?.let { downloadViewModel.downloadM3U8(it, context, videoViewModel.title.value) } },
                                                                onPauseResumeClick = { videoViewModel.videoUrl.value?.let { downloadViewModel.togglePauseResume(it, context) } },
                                                                onCancelClick = { videoViewModel.videoUrl.value?.let { downloadViewModel.cancelDownload(it, context) } }
                                                            )

                                                            IconButton(onClick = { navController.navigate("video") }) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = "Ouvrir", tint = Color.White)
                                                            }
                                                        }
                                                        
                                                        description?.let { 
                                                            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                                                Text(text = it, modifier = Modifier.padding(top = 8.dp).clickable { 
                                                                    selectedDescription = it
                                                                    showDescriptionDialog = true
                                                                }, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (index < links.size - 1) {
                                        Divider(
                                            color = Color.White.copy(alpha = 0.1f),
                                            thickness = 1.dp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
            
            PullRefreshIndicator(
                refreshing = isLoading, 
                state = pullRefreshState, 
                modifier = Modifier.align(Alignment.TopCenter), 
                backgroundColor = GlassWhite, 
                contentColor = LiquidAccent
            )
        }

        if (isDialogOpen) {
            Dialog(onDismissRequest = { isDialogOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable { isDialogOpen = false }) {
                    Image(painter = rememberAsyncImagePainter(imageUrl), contentDescription = null, modifier = Modifier.align(Alignment.Center).fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
                }
            }
        }

        if (showDescriptionDialog) {
            AlertDialog(
                onDismissRequest = { showDescriptionDialog = false },
                title = { Text("Détails", color = Color.White) },
                text = { 
                    Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        Text(selectedDescription, color = Color.White) 
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDescriptionDialog = false }) {
                        Text("Fermer", color = LiquidAccent)
                    }
                },
                backgroundColor = Color(0xFF1A237E),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
