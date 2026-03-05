package com.example.obivapp2.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.obivapp2.data.FavoriteMovie
import com.example.obivapp2.ui.theme.GlassBorder
import com.example.obivapp2.ui.theme.GlassWhite
import com.example.obivapp2.ui.theme.LiquidAccent
import com.example.obivapp2.utils.shareLink
import com.example.obivapp2.viewModel.DownloadState
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.FavoritesViewModel
import com.example.obivapp2.viewModel.VideoViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalFoundationApi::class)
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
    
    val currentVideoUrl by videoViewModel.videoUrl
    val currentImageUrl by videoViewModel.imageUrl
    val currentDescription by videoViewModel.description
    val currentVideoUrlToShare by videoViewModel.videoUrlToShare
    val currentTitle by videoViewModel.title

    var showTextDialog by remember { mutableStateOf(false) }
    var selectedFullText by remember { mutableStateOf("") }
    var dialogTitle by remember { mutableStateOf("Détails") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var favoriteToDelete by remember { mutableStateOf<FavoriteMovie?>(null) }

    Scaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Mes Favoris", color = Color.White) },
                backgroundColor = Color.Transparent,
                elevation = 0.dp
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Vous n'avez pas encore de favoris", color = Color.White.copy(alpha = 0.6f))
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
                            favorites.forEachIndexed { index, favorite ->
                                val isExpanded = expandedUrl == favorite.url
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .combinedClickable(
                                            onClick = {
                                                if (!isExpanded) {
                                                    videoViewModel.resetLinkVideo()
                                                    videoViewModel.fetchLinkVideo(favorite.url, favorite.title)
                                                    expandedUrl = favorite.url
                                                } else {
                                                    expandedUrl = null
                                                }
                                            },
                                            onLongClick = {
                                                selectedFullText = favorite.description ?: favorite.title
                                                dialogTitle = if (favorite.description != null) "Description" else "Titre complet"
                                                showTextDialog = true
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
                                            text = favorite.title,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.subtitle1,
                                            color = Color.White
                                        )
                                        IconButton(onClick = {
                                            favoriteToDelete = favorite
                                            showDeleteConfirmDialog = true
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Supprimer",
                                                tint = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        if (videoViewModel.isDataNull()) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(48.dp),
                                                    color = LiquidAccent
                                                )
                                            }
                                        } else {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.fillMaxWidth().height(150.dp)
                                            ) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(currentImageUrl ?: favorite.imageUrl),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(100.dp, 150.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                                
                                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        IconButton(onClick = { currentVideoUrlToShare?.let { shareLink(context, it) } }) {
                                                            Icon(Icons.Default.Share, contentDescription = "Partager", tint = Color.White)
                                                        }

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

                                                        IconButton(onClick = { navController.navigate("video") }) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = "Ouvrir", tint = Color.White)
                                                        }
                                                    }

                                                    val displayDescription = currentDescription ?: favorite.description
                                                    displayDescription?.let {
                                                        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                                            Text(
                                                                text = it,
                                                                modifier = Modifier.padding(top = 8.dp).clickable {
                                                                    selectedFullText = it
                                                                    dialogTitle = "Description"
                                                                    showTextDialog = true
                                                                },
                                                                fontSize = 12.sp,
                                                                color = Color.White.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                if (index < favorites.size - 1) {
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

            if (showTextDialog) {
                AlertDialog(
                    onDismissRequest = { showTextDialog = false },
                    title = { Text(dialogTitle, color = Color.White) },
                    text = { 
                        Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                            Text(selectedFullText, color = Color.White) 
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTextDialog = false }) {
                            Text("Fermer", color = LiquidAccent)
                        }
                    },
                    backgroundColor = Color(0xFF1A237E),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Supprimer des favoris ?", color = Color.White) },
                    text = { Text("Voulez-vous retirer \"${favoriteToDelete?.title}\" de vos favoris ?", color = Color.White) },
                    confirmButton = {
                        TextButton(onClick = {
                            favoriteToDelete?.let { favoritesViewModel.toggleFavorite(it, true) }
                            showDeleteConfirmDialog = false
                        }) {
                            Text("Supprimer", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Annuler", color = Color.White)
                        }
                    },
                    backgroundColor = Color(0xFF1A237E),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}
