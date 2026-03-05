package com.example.obivapp2.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.obivapp2.ui.theme.GlassBorder
import com.example.obivapp2.ui.theme.GlassWhite
import com.example.obivapp2.ui.theme.LiquidAccent
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.DownloadedVideo
import com.example.obivapp2.viewModel.VideoViewModel
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    downloadViewModel: DownloadViewModel,
    videoViewModel: VideoViewModel
) {
    val downloadedVideos by downloadViewModel.downloadedVideos.collectAsState()
    var showTextDialog by remember { mutableStateOf(false) }
    var selectedFullText by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var videoToDelete by remember { mutableStateOf<DownloadedVideo?>(null) }

    Scaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Mes Téléchargements", color = Color.White) },
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
                if (downloadedVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune vidéo téléchargée",
                            style = MaterialTheme.typography.body1,
                            color = Color.White.copy(alpha = 0.6f)
                        )
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
                            downloadedVideos.forEachIndexed { index, video ->
                                DownloadedVideoItem(
                                    video = video,
                                    onPlayClick = {
                                        videoViewModel.setVideoData(
                                            url = video.filePath,
                                            title = video.title,
                                            imageUrl = null,
                                            description = "Vidéo téléchargée localement"
                                        )
                                        navController.navigate("video")
                                    },
                                    onDeleteClick = {
                                        videoToDelete = video
                                        showDeleteConfirmDialog = true
                                    },
                                    onLongClick = {
                                        selectedFullText = video.title
                                        showTextDialog = true
                                    }
                                )
                                
                                if (index < downloadedVideos.size - 1) {
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
                    title = { Text("Titre complet", color = Color.White) },
                    text = { Text(selectedFullText, color = Color.White) },
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
                    title = { Text("Suppression définitive ?", color = Color.White) },
                    text = { Text("Voulez-vous supprimer définitivement \"${videoToDelete?.title}\" de votre appareil ? Cette action est irréversible.", color = Color.White) },
                    confirmButton = {
                        TextButton(onClick = {
                            videoToDelete?.let { downloadViewModel.deleteDownloadedVideo(it) }
                            showDeleteConfirmDialog = false
                        }) {
                            Text("Supprimer définitivement", color = Color.Red)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadedVideoItem(
    video: DownloadedVideo,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val formattedSize = remember(video.size) {
        manualFormatSize(video.size)
    }
    
    LaunchedEffect(video.size) {
        Log.d("DownloadsScreen", "DEBUG SIZE - Titre: ${video.title}, Raw: ${video.size}, Formatted: $formattedSize")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlayClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
            Text(
                text = "$formattedSize • MP4",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Supprimer",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

fun manualFormatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.size - 1) {
        value /= 1024.0
        index++
    }
    
    // Si l'unité est Mo, Ko ou Octets (index < 3 pour GB), on n'affiche pas de virgule
    val pattern = if (index < 3) "%.0f %s" else "%.2f %s"
    return String.format(Locale.US, pattern, value, units[index])
}
