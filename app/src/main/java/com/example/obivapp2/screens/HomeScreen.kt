package com.example.obivapp2.screens

import MainViewModel
import android.app.Activity
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.obivapp2.utils.Permissions
import com.example.obivapp2.utils.shareLink
import com.example.obivapp2.viewModel.DownloadState
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.VideoViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File


@Composable
fun HomeScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    videoViewModel: VideoViewModel
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

    // Add LaunchedEffect to fetch links when the screen starts
    LaunchedEffect(Unit) {
        try {
            errorMessage = null
            mainViewModel.fetchLinks()
            
            // Demander toutes les permissions nécessaires au démarrage
            (context as? Activity)?.let {
                Permissions.requestAllPermissions(it)
            }
        } catch (e: Exception) {
            errorMessage = "Erreur lors du chargement: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home Screen") })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Champ de recherche
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        mainViewModel.searchVideo(searchText)
                    },
                    label = { Text("Rechercher") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                // Afficher le chargement ou l'erreur
                when {
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    links.isEmpty() && !isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun résultat trouvé",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    else -> {
                        // Liste existante
                        LazyColumn(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            items(links) { linkData ->
                                val currentIndex = links.indexOf(linkData)
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = 4.dp,
                                    modifier = Modifier
                                        .animateContentSize()
                                        .padding(vertical = 4.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            videoViewModel.resetLinkVideo()
                                            videoViewModel.fetchLinkVideo(linkData.url, linkData.text)
                                            expandedItemIndex =
                                                if (expandedItemIndex == currentIndex) null else currentIndex
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .background(Color.White)
                                    ) {
                                        Text(
                                            text = linkData.text,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        // Afficher les boutons si l'élément est étendu
                                        if (expandedItemIndex == currentIndex) {
                                            if (videoViewModel.isDataNull()) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier
                                                        .size(100.dp)
                                                        .align(Alignment.CenterHorizontally),
                                                    color = Color.Black
                                                )
                                            } else {
                                                Row(
                                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(imageUrl),
                                                        contentDescription = "Votre image",
                                                        modifier = Modifier
                                                            .size(100.dp)
                                                            .clickable { isDialogOpen = true },
                                                    )
                                                    Column {
                                                        Row {
                                                            IconButton(onClick = {
                                                                videoViewModel.videoUrlToShare.value?.let {
                                                                    shareLink(
                                                                        context,
                                                                        it
                                                                    )
                                                                }
                                                            }) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Share,
                                                                    contentDescription = "Partager"
                                                                )
                                                            }
                                                            IconButton(onClick = {
                                                                navController.navigate("video")
                                                            }) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PlayArrow,
                                                                    contentDescription = "Ouvrir"
                                                                )
                                                            }

                                                            // Collect download state for this specific video URL
                                                            val videoUrl = videoViewModel.videoUrl.value
                                                            val downloadState by remember(videoUrl) {
                                                                videoUrl?.let { url ->
                                                                    downloadViewModel.getDownloadState(url)
                                                                } ?: MutableStateFlow(DownloadState.Idle)
                                                            }.collectAsState()

                                                            when (downloadState) {
                                                                is DownloadState.Idle -> {
                                                                    IconButton(onClick = {
                                                                        if (Permissions.hasStoragePermission(context)) {
                                                                            videoViewModel.videoUrl.value?.let {
                                                                                downloadViewModel.downloadM3U8(
                                                                                    it, 
                                                                                    context,
                                                                                    videoViewModel.title.value
                                                                                )
                                                                            }
                                                                        } else {
                                                                            (context as? Activity)?.let {
                                                                                Permissions.requestStoragePermission(it)
                                                                            }
                                                                        }
                                                                    }) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Download,
                                                                            contentDescription = "Télécharger"
                                                                        )
                                                                    }
                                                                }

                                                                is DownloadState.Downloading -> {
                                                                    val downloadingState = downloadState as DownloadState.Downloading
                                                                    Column(
                                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                                        modifier = Modifier.padding(8.dp)
                                                                    ) {
                                                                        CircularProgressIndicator(
                                                                            progress = downloadingState.progress / 100f,
                                                                            modifier = Modifier.size(24.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text(
                                                                            text = "${downloadingState.currentSegment}/${downloadingState.totalSegments}",
                                                                            style = MaterialTheme.typography.caption
                                                                        )
                                                                        Text(
                                                                            text = "${downloadingState.downloadedSize / (1024 * 1024)} Mo",
                                                                            style = MaterialTheme.typography.caption
                                                                        )
                                                                    }
                                                                }

                                                                is DownloadState.Success -> {
                                                                    val successState = downloadState as DownloadState.Success
                                                                    Column(
                                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                                        modifier = Modifier.padding(8.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.CheckCircle,
                                                                            contentDescription = "Succès",
                                                                            tint = Color.Green
                                                                        )
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text(
                                                                            text = "Téléchargé !",
                                                                            style = MaterialTheme.typography.caption
                                                                        )
                                                                        Text(
                                                                            text = "Dans Downloads/${successState.filePath.substringAfterLast("/")}",
                                                                            style = MaterialTheme.typography.caption,
                                                                            fontSize = 10.sp
                                                                        )
                                                                    }
                                                                }

                                                                is DownloadState.Error -> {
                                                                    val errorState = downloadState as DownloadState.Error
                                                                    Column(
                                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                                        modifier = Modifier.padding(8.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Error,
                                                                            contentDescription = "Erreur",
                                                                            tint = Color.Red
                                                                        )
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text(
                                                                            text = errorState.message,
                                                                            style = MaterialTheme.typography.caption,
                                                                            color = Color.Red
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        description?.let { Text(
                                                            text = it,
                                                            style = MaterialTheme.typography.body1,
                                                            modifier = Modifier.fillMaxWidth()) }
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

            // Overlay ProgressBar
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        if (isDialogOpen) {
            Dialog(
                onDismissRequest = {
                    isDialogOpen = false
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            isDialogOpen = false
                        }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUrl),
                        contentDescription = "Image agrandie",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}