package com.example.obivapp2

import MainViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.obivapp2.screens.HomeScreen
import com.example.obivapp2.screens.VideoScreen
import com.example.obivapp2.screens.FavoritesScreen
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.VideoViewModel
import com.example.obivapp2.viewModel.FavoritesViewModel

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by lazy { MainViewModel() }
    private val videoViewModel: VideoViewModel by lazy { VideoViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val downloadViewModel: DownloadViewModel = viewModel()
                    val favoritesViewModel: FavoritesViewModel = viewModel()
                    AppNavHost(mainViewModel, videoViewModel, downloadViewModel, favoritesViewModel)
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
    downloadViewModel: DownloadViewModel,
    favoritesViewModel: FavoritesViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavigationItem("home", "Accueil", Icons.Default.Home),
        NavigationItem("favorites", "Favoris", Icons.Default.Favorite)
    )

    Scaffold(
        bottomBar = {
            if (currentDestination?.route in listOf("home", "favorites")) {
                BottomNavigation {
                    items.forEach { item ->
                        BottomNavigationItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { 
                HomeScreen(navController, mainViewModel, videoViewModel, favoritesViewModel) 
            }
            composable("favorites") { 
                FavoritesScreen(navController, favoritesViewModel, videoViewModel)
            }
            composable("video") { 
                VideoScreen(navController, videoViewModel, downloadViewModel) 
            }
        }
    }
}

data class NavigationItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
