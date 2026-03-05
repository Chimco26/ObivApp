package com.example.obivapp2.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.obivapp2.database.AppDatabase
import com.example.obivapp2.ui.theme.GlassBorder
import com.example.obivapp2.ui.theme.GlassWhite
import com.example.obivapp2.ui.theme.LiquidAccent
import com.example.obivapp2.viewModel.LinkData
import com.example.obivapp2.viewModel.VideoViewModel
import kotlinx.coroutines.launch

@Composable
fun ContinueWatchingScreen(
    navController: NavController,
    videoViewModel: VideoViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linkDao = remember { AppDatabase.getDatabase(context).linkDao() }
    val continueWatchingList by linkDao.getContinueWatchingLinks().collectAsState(initial = emptyList())
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedLinkToDelete by remember { mutableStateOf<LinkData?>(null) }

    Scaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reprendre la lecture",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
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
                if (continueWatchingList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune vidéo en cours",
                            style = MaterialTheme.typography.body1,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    continueWatchingList.forEach { link ->
                        ContinueWatchingItem(
                            link = link,
                            onPlayClick = {
                                videoViewModel.setVideoData(
                                    url = link.url,
                                    title = link.text,
                                    imageUrl = link.imageUrl,
                                    description = "Reprendre la lecture"
                                )
                                navController.navigate("video")
                            },
                            onDeleteHistoryClick = {
                                selectedLinkToDelete = link
                                showDeleteDialog = true
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(100.dp))
            }

            if (showDeleteDialog && selectedLinkToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Supprimer de l'historique ?", color = Color.White) },
                    text = { Text("Voulez-vous retirer \"${selectedLinkToDelete?.text}\" de votre historique de lecture ?", color = Color.White) },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedLinkToDelete?.let { link ->
                                scope.launch {
                                    linkDao.updateProgress(link.url, 0, link.totalDuration, 0)
                                }
                            }
                            showDeleteDialog = false
                        }) {
                            Text("Supprimer", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
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

@Composable
fun ContinueWatchingItem(
    link: LinkData,
    onPlayClick: () -> Unit,
    onDeleteHistoryClick: () -> Unit
) {
    val progress = if (link.totalDuration > 0) {
        link.lastWatchedPosition.toFloat() / link.totalDuration.toFloat()
    } else 0f

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = GlassWhite,
        border = BorderStroke(1.dp, GlassBorder),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            ) {
                if (link.imageUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(link.imageUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = LiquidAccent,
                    backgroundColor = Color.White.copy(alpha = 0.3f)
                )
                
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                
                val timeLeft = (link.totalDuration - link.lastWatchedPosition) / 60000
                Text(
                    text = "Il reste environ $timeLeft min",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onDeleteHistoryClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer de l'historique",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
