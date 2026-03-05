package com.example.obivapp2.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.obivapp2.utils.shareLink
import com.example.obivapp2.viewModel.DownloadState
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.FavoritesViewModel
import com.example.obivapp2.viewModel.VideoViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun FavoritesScreen(
    navController: NavController,
    favoritesViewModel: FavoritesViewModel,
    videoViewModel: VideoViewModel
) {
    val favorites by favoritesViewModel.allFavorites.collectAsState(initial = emptyList())
    var expandedUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val downloadViewModel: DownloadViewModel = viewModel()
    
    // Observer les données du VideoViewModel
    val currentVideoUrl by videoViewModel.videoUrl
    val currentImageUrl by videoViewModel.imageUrl
    val currentDescription by videoViewModel.description
    val currentVideoUrlToShare by videoViewModel.videoUrlToShare
    val currentTitle by videoViewModel.title

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mes Favoris") })
        }
    ) { paddingValues ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Vous n'avez pas encore de favoris")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp)
            ) {
                items(favorites) { favorite ->
                    val isExpanded = expandedUrl == favorite.url
                    
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = 4.dp,
                        modifier = Modifier
                            .animateContentSize()
                            .padding(vertical = 6.dp)
                            .fillMaxWidth()
                            .clickable {
                                if (!isExpanded) {
                                    videoViewModel.resetLinkVideo()
                                    videoViewModel.fetchLinkVideo(favorite.url, favorite.title)
                                    expandedUrl = favorite.url
                                } else {
                                    expandedUrl = null
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp).background(Color.White)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = favorite.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.subtitle1
                                )
                                IconButton(onClick = {
                                    favoritesViewModel.toggleFavorite(favorite, true)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer",
                                        tint = Color.Gray
                                    )
                                }
                            }

                            if (isExpanded) {
                                if (videoViewModel.isDataNull()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(48.dp),
                                            color = Color.Black
                                        )
                                    }
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(currentImageUrl ?: favorite.imageUrl),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(100.dp, 150.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Partager
                                                IconButton(onClick = { currentVideoUrlToShare?.let { shareLink(context, it) } }) {
                                                    Icon(Icons.Default.Share, contentDescription = "Partager", tint = MaterialTheme.colors.primary)
                                                }

                                                // Télécharger
                                                val videoUrl = currentVideoUrl
                                                val downloadState by remember(videoUrl) {
                                                    videoUrl?.let { url -> downloadViewModel.getDownloadState(url) } ?: MutableStateFlow(DownloadState.Idle)
                                                }.collectAsState()

                                                CreativeDownloadButton(
                                                    state = downloadState,
                                                    onDownloadClick = { currentVideoUrl?.let { downloadViewModel.downloadM3U8(it, context, currentTitle) } },
                                                    onPauseResumeClick = { currentVideoUrl?.let { downloadViewModel.togglePauseResume(it, context) } },
                                                    onCancelClick = { currentVideoUrl?.let { downloadViewModel.cancelDownload(it, context) } }
                                                )

                                                // Lire
                                                IconButton(onClick = { navController.navigate("video") }) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Ouvrir", tint = MaterialTheme.colors.primary)
                                                }
                                            }

                                            val displayDescription = currentDescription ?: favorite.description
                                            displayDescription?.let {
                                                Text(
                                                    text = it,
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    fontSize = 12.sp
                                                )
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
}
