package com.example.obivapp2

import MainViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.obivapp2.screens.HomeScreen
import com.example.obivapp2.screens.VideoScreen
import com.example.obivapp2.screens.FavoritesScreen
import com.example.obivapp2.screens.DownloadsScreen
import com.example.obivapp2.screens.ContinueWatchingScreen
import com.example.obivapp2.viewModel.DownloadViewModel
import com.example.obivapp2.viewModel.VideoViewModel
import com.example.obivapp2.viewModel.FavoritesViewModel
import com.example.obivapp2.utils.Permissions
import com.example.obivapp2.ui.theme.ObivApp2Theme
import com.example.obivapp2.ui.theme.LiquidBackgroundStart
import com.example.obivapp2.ui.theme.LiquidBackgroundEnd
import com.example.obivapp2.ui.theme.GlassBorder
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by lazy { MainViewModel() }
    private val videoViewModel: VideoViewModel by lazy { VideoViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Permissions.requestAllPermissions(this)

        setContent {
            ObivApp2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNavHost(
    mainViewModel: MainViewModel,
    videoViewModel: VideoViewModel,
    downloadViewModel: DownloadViewModel,
    favoritesViewModel: FavoritesViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val items = listOf(
        NavigationItem("home", "Accueil", Icons.Default.Home),
        NavigationItem("favorites", "Favoris", Icons.Default.Favorite),
        NavigationItem("continue", "En cours", Icons.Default.History),
        NavigationItem("downloads", "Téléchargements", Icons.Default.FileDownload)
    )

    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(LiquidBackgroundStart, LiquidBackgroundEnd)
                )
            )
    ) {
        NavHost(
            navController = navController,
            startDestination = "main_tabs",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("main_tabs") {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondBoundsPageCount = 1
                    ) { page ->
                        when (items[page].route) {
                            "home" -> HomeScreen(navController, mainViewModel, videoViewModel, favoritesViewModel)
                            "favorites" -> FavoritesScreen(navController, favoritesViewModel, videoViewModel)
                            "continue" -> ContinueWatchingScreen(navController, videoViewModel)
                            "downloads" -> {
                                val isSelected = pagerState.currentPage == page
                                LaunchedEffect(isSelected) {
                                    if (isSelected) downloadViewModel.loadDownloadedVideos()
                                }
                                DownloadsScreen(navController, downloadViewModel, videoViewModel)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                            .height(56.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        backgroundColor = Color.White.withAlpha(0.85f),
                        border = BorderStroke(1.dp, GlassBorder),
                        elevation = 4.dp
                    ) {
                        BottomNavigation(
                            backgroundColor = Color.Transparent,
                            contentColor = Color.White,
                            elevation = 0.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items.forEachIndexed { index, item ->
                                val selected = pagerState.currentPage == index
                                BottomNavigationItem(
                                    icon = { 
                                        Icon(
                                            item.icon, 
                                            contentDescription = item.label,
                                            modifier = Modifier.size(24.dp)
                                        ) 
                                    },
                                    label = null,
                                    alwaysShowLabel = false,
                                    selected = selected,
                                    selectedContentColor = Color(0xFF1A237E),
                                    unselectedContentColor = Color.Gray,
                                    onClick = {
                                        coroutineScope.launch {
                                            // Utilisation de scrollToPage pour un saut direct sans défilement visuel
                                            pagerState.scrollToPage(index)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            composable("video") { 
                VideoScreen(navController, videoViewModel, downloadViewModel)
            }
        }
    }
}

fun Color.withAlpha(alpha: Float): Color = this.copy(alpha = alpha)

data class NavigationItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
