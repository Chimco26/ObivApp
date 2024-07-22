package com.example.obivapp2.screens

import MainViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.obivapp2.viewModel.VideoViewModel


@Composable
fun HomeScreen(navController: NavController, mainViewModel: MainViewModel, videoViewModel: VideoViewModel) {
    val links by mainViewModel.links

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home Screen") })
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier.padding(8.dp)
        ) {
            items(links) { linkData ->
                // Ajouter une carte pour chaque élément pour un aspect distinct
                Card(
                    shape = RoundedCornerShape(8.dp),
                    elevation = 4.dp,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .clickable {
                            videoViewModel.fetchLinkVideo(linkData.url)
                            navController.navigate("video")
                        }
                ) {
                    Text(
                        text = linkData.text,
                        modifier = Modifier
                            .padding(16.dp)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}