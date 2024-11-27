package com.example.obivapp2.screens

import MainViewModel
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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.obivapp2.utils.shareLink
import com.example.obivapp2.viewModel.DownloadState
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.VideoViewModel
import java.io.File


@Composable
fun HomeScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    videoViewModel: VideoViewModel
) {
    val links by mainViewModel.links
    var searchText by remember { mutableStateOf("") }
    var expandedItemIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val imageUrl by videoViewModel.imageUrl
    val description by videoViewModel.description
    var isDialogOpen by remember { mutableStateOf(false) }
    val downloadViewModel: DownloadViewModel = viewModel()
    val downloadState by downloadViewModel.downloadState.collectAsState()





    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home Screen") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
            LazyColumn(
                contentPadding = paddingValues,
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
                                videoViewModel.fetchLinkVideo(linkData.url)
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
                                            painter = rememberAsyncImagePainter(imageUrl), // Remplacez par votre ressource d'image
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
                                                when (downloadState) {
                                                    is DownloadState.Idle -> {
                                                        // Afficher le bouton pour démarrer le téléchargement si rien n'est en cours
                                                        IconButton(onClick = {
                                                            videoViewModel.videoUrl.value?.let {
                                                                downloadViewModel.downloadM3U8(
                                                                    it, context
                                                                )
                                                            }
                                                        }) {
                                                            Icon(
                                                                imageVector = Icons.Default.ShoppingCart,
                                                                contentDescription = "Télécharger"
                                                            )
                                                        }
                                                    }

                                                    is DownloadState.Downloading -> {
                                                        // Afficher un indicateur de progression pendant le téléchargement
                                                        CircularProgressIndicator()
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        Text(text = "Téléchargement en cours...")
                                                    }

                                                    is DownloadState.Success -> {
                                                        // Afficher un message de succès quand le téléchargement est terminé
                                                        Text(text = "Téléchargement terminé avec succès !")
                                                    }

                                                    is DownloadState.Error -> {
                                                        // Afficher le message d'erreur si le téléchargement échoue
                                                        val errorMessage =
                                                            (downloadState as DownloadState.Error).message
                                                        Text(text = "Erreur : $errorMessage")
                                                    }
                                                }

                                                if (isDialogOpen) {
                                                    Dialog(
                                                        onDismissRequest = {
                                                            // Fermer le Dialog lorsque l'utilisateur clique en dehors
                                                            isDialogOpen = false
                                                        },
                                                        properties = DialogProperties(
                                                            usePlatformDefaultWidth = false
                                                        ) // Pour que l'image prenne tout l'écran si nécessaire
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize() // Le Box prend tout l'écran pour afficher l'image en grand
                                                                .clickable {
                                                                    // Fermer le Dialog lorsque l'image agrandie est cliquée
                                                                    isDialogOpen = false
                                                                }
                                                        ) {
                                                            Image(
                                                                painter = rememberAsyncImagePainter(
                                                                    imageUrl
                                                                ),
                                                                contentDescription = "Image agrandie",
                                                                modifier = Modifier
                                                                    .align(Alignment.Center) // Centrer l'image dans le Dialog
                                                                    .fillMaxSize(), // Faire en sorte que l'image occupe tout l'espace disponible
                                                                contentScale = ContentScale.Fit // Adapter l'image en grand sans la couper
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