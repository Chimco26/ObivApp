package com.example.obivapp2

import MainViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.obivapp2.screens.HomeScreen
import com.example.obivapp2.screens.VideoScreen
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.VideoViewModel

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by lazy { MainViewModel() } // Assume a ViewModelProvider setup
    private val videoViewModel: VideoViewModel by lazy { VideoViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val downloadViewModel: DownloadViewModel = viewModel()
                    AppNavHost(mainViewModel, videoViewModel, downloadViewModel)
                }
            }
        }
        mainViewModel.fetchLinks()
    }
}

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel,
    videoViewModel: VideoViewModel,
    downloadViewModel: DownloadViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, mainViewModel, videoViewModel) }
        composable("video") { VideoScreen(navController, videoViewModel, downloadViewModel) }
    }
}
