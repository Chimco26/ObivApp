package com.example.obivapp2.screens

import MainViewModel
import android.app.Activity
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
            is DownloadState.Downloading -> if (s.isPaused) Color.Gray else MaterialTheme.colors.primary
            else -> MaterialTheme.colors.primary
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.animateContentSize()
    ) {
        if (state is DownloadState.Idle) {
            IconButton(onClick = onDownloadClick) {
                Icon(Icons.Default.Download, contentDescription = "Télécharger", tint = MaterialTheme.colors.primary)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(backgroundColor.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                    val progress = when (state) {
                        is DownloadState.Downloading -> state.progress / 100f
                        is DownloadState.Success -> 1f
                        else -> 0f
                    }
                    CircularProgressIndicator(progress = 1f, color = backgroundColor.copy(alpha = 0.2f), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    CircularProgressIndicator(progress = progress, color = backgroundColor, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    
                    if (state is DownloadState.Downloading) {
                        IconButton(onClick = onPauseResumeClick, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, tint = backgroundColor, modifier = Modifier.size(12.dp))
                        }
                    } else if (state is DownloadState.Success) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = backgroundColor, modifier = Modifier.size(16.dp))
                    } else if (state is DownloadState.Error) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = backgroundColor, modifier = Modifier.size(16.dp))
                    }
                }
                
                if (state is DownloadState.Downloading) {
                    Text(text = "${state.progress}%", fontSize = 10.sp, color = backgroundColor, modifier = Modifier.padding(horizontal = 2.dp))
                    IconButton(onClick = onCancelClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
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
    val downloadViewModel: DownloadViewModel = viewModel()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { searchText = ""; mainViewModel.fetchLinks() }
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
        topBar = { TopAppBar(title = { Text("Obiv App") }) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).pullRefresh(pullRefreshState)) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; mainViewModel.searchVideo(searchText) },
                    label = { Text("Rechercher") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )

                if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                    }
                } else if (links.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Aucun résultat trouvé", modifier = Modifier.padding(16.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(8.dp).fillMaxSize()) {
                        items(links) { linkData ->
                            val currentIndex = links.indexOf(linkData)
                            val isFav by favoritesViewModel.isFavorite(linkData.url).collectAsState(initial = false)

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                elevation = 4.dp,
                                modifier = Modifier.animateContentSize().padding(vertical = 6.dp).fillMaxWidth().clickable {
                                    videoViewModel.resetLinkVideo()
                                    videoViewModel.fetchLinkVideo(linkData.url, linkData.text)
                                    expandedItemIndex = if (expandedItemIndex == currentIndex) null else currentIndex
                                }
                            ) {
                                Column(modifier = Modifier.padding(16.dp).background(Color.White)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = linkData.text,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.subtitle1
                                        )
                                        IconButton(onClick = {
                                            favoritesViewModel.toggleFavorite(
                                                FavoriteMovie(
                                                    url = linkData.url,
                                                    title = linkData.text,
                                                    imageUrl = if (expandedItemIndex == currentIndex) imageUrl else null,
                                                    description = if (expandedItemIndex == currentIndex) description else null,
                                                    videoUrl = if (expandedItemIndex == currentIndex) videoViewModel.videoUrl.value else null
                                                ),
                                                isFav
                                            )
                                        }) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Favori",
                                                tint = if (isFav) Color.Red else Color.Gray
                                            )
                                        }
                                    }

                                    if (expandedItemIndex == currentIndex) {
                                        if (videoViewModel.isDataNull()) {
                                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(48.dp), color = Color.Black)
                                            }
                                        } else {
                                            // Mettre à jour les données du favori si on vient de l'ouvrir et qu'on l'ajoute
                                            // En réalité, toggleFavorite utilisera les valeurs actuelles des State
                                            
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(imageUrl),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(100.dp, 150.dp).clip(RoundedCornerShape(8.dp)).clickable { isDialogOpen = true }
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        IconButton(onClick = { videoViewModel.videoUrlToShare.value?.let { shareLink(context, it) } }) {
                                                            Icon(Icons.Default.Share, contentDescription = "Partager", tint = MaterialTheme.colors.primary)
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
                                                            Icon(Icons.Default.PlayArrow, contentDescription = "Ouvrir", tint = MaterialTheme.colors.primary)
                                                        }
                                                    }
                                                    
                                                    description?.let { 
                                                        Text(text = it, modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (links.isNotEmpty()) {
                PullRefreshIndicator(refreshing = isLoading, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter), backgroundColor = Color.White, contentColor = MaterialTheme.colors.primary)
            }

            if (isLoading && links.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colors.primary, modifier = Modifier.size(48.dp))
                }
            }
        }

        if (isDialogOpen) {
            Dialog(onDismissRequest = { isDialogOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().clickable { isDialogOpen = false }) {
                    Image(painter = rememberAsyncImagePainter(imageUrl), contentDescription = null, modifier = Modifier.align(Alignment.Center).fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
