package com.example.nitpicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nitpicker.screen.album.AlbumScreen
import com.example.nitpicker.screen.download.DownloadScreen
import com.example.nitpicker.screen.home.HomeViewModel
import com.example.nitpicker.ui.screens.HomeScreen
import com.example.nitpicker.ui.theme.NitpickerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NitpickerTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val configuration = LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp.dp
                val drawerWidth = screenWidth * 0.7f

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .width(drawerWidth)
                                .fillMaxHeight(),
                            drawerContainerColor = Color(0xFF1E1E1E)
                        ) {
                            Spacer(Modifier.height(12.dp))
                            DrawerItem(
                                label = "Home",
                                icon = Icons.Filled.Home,
                                route = "home_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = { scope.launch { drawerState.close() } }
                            )
                            DrawerItem(
                                label = "Downloads",
                                icon = Icons.Filled.Download,
                                route = "download_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = { scope.launch { drawerState.close() } }
                            )
                            DrawerItem(
                                label = "Files",
                                icon = Icons.Filled.Folder,
                                route = "files_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = { scope.launch { drawerState.close() } },
                                navigateOnClick = false
                            )
                        }
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavHost(navController = navController, startDestination = "home_screen") {
                            composable("home_screen") {
                                val homeViewModel: HomeViewModel = hiltViewModel()
                                HomeScreen(
                                    navController = navController,
                                    homeViewModel = homeViewModel,
                                    openDrawer = {
                                        scope.launch {
                                            drawerState.open()
                                        }
                                    }
                                )
                            }
                            composable(
                                route = "album_screen/{albumUrl}/{albumTitle}",
                                arguments = listOf(
                                    navArgument("albumUrl") { type = NavType.StringType },
                                    navArgument("albumTitle") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val albumUrl = backStackEntry.arguments?.getString("albumUrl")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                                val albumTitle = backStackEntry.arguments?.getString("albumTitle")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                                AlbumScreen(navController = navController)
                            }
                            composable("download_screen") {
                                DownloadScreen(navController = navController)
                            }
                            composable("files_screen") {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Files Screen (Not Implemented)", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    route: String,
    currentRoute: String?,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    navigateOnClick: Boolean = true
) {
    val isSelected = currentRoute == route
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        selected = isSelected,
        onClick = {
            closeDrawer()
            if (navigateOnClick && currentRoute != route) {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            selectedContainerColor = Color(0xFF303030),
            unselectedTextColor = Color.White.copy(alpha = 0.8f),
            selectedTextColor = Color.White,
            unselectedIconColor = Color.White.copy(alpha = 0.8f),
            selectedIconColor = Color.White
        )
    )
}