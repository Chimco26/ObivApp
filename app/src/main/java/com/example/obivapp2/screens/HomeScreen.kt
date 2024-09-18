package com.example.obivapp2.screens

import MainViewModel
import android.content.Context
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.obivapp2.R
import com.example.obivapp2.utils.shareLink
import com.example.obivapp2.viewModel.VideoViewModel


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
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .clickable {
//                            if (expandedItemIndex == currentIndex){
//                                expandedItemIndex = null
//                                videoViewModel.resetLinkVideo()
//                            } else{
//                                expandedItemIndex = currentIndex
//                                videoViewModel.fetchLinkVideo(linkData.url)
//                            }
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
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(imageUrl), // Remplacez par votre ressource d'image
                                        contentDescription = "Votre image",
                                        modifier = Modifier.size(100.dp) // Ajustez la taille selon vos besoins
                                    )
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
                                    IconButton(onClick = { /* Télécharger la vidéo */ }) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = "Télécharger"
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