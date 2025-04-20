package com.example.nitpicker

import android.os.Bundle
import android.util.Log
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.nitpicker.screen.album.AlbumScreen
import com.example.nitpicker.screen.download.DownloadScreen
import com.example.nitpicker.screen.home.HomeViewModel
import com.example.nitpicker.ui.screens.HomeScreen
import com.example.nitpicker.screen.files.FilesScreen
import com.example.nitpicker.screen.local_album.LocalAlbumScreen
import com.example.nitpicker.ui.theme.NitpickerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.net.URLEncoder

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

                // Shared state for navigation debounce
                var lastNavTime by remember { mutableStateOf(0L) }
                val debounceDelay = 500L // 防抖延迟 (毫秒), 可调整

                // Add listener for navigation changes
                LaunchedEffect(navController) {
                    navController.addOnDestinationChangedListener { controller, destination, arguments ->
                        Log.d("NavigationFlow", "Navigated to: ${destination.route}")
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                // Log route and drawer state on recomposition
                Log.d("NavigationFlow", "Recomposed - Route: $currentRoute, Drawer: ${drawerState.currentValue}")

                // Determine if the drawer gesture should be enabled
                val gesturesEnabled = true // Only enable on home screen

                val configuration = LocalConfiguration.current
                val screenWidthDp = configuration.screenWidthDp.dp
                val screenHeightDp = configuration.screenHeightDp.dp

                val drawerWidth = if (screenWidthDp > screenHeightDp) {
                    screenWidthDp * 0.5f
                } else {
                    screenWidthDp * 0.7f
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = gesturesEnabled, // Control gesture based on route
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
                                closeDrawer = {
                                    Log.d("DrawerAction", "[${System.currentTimeMillis()}] DrawerItem: Requesting drawer close. Current Drawer State: ${drawerState.currentValue}")
                                    scope.launch {
                                        Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Attempting drawerState.close()")
                                        try {
                                            drawerState.close()
                                            Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: drawerState.close() completed.")
                                        } catch (e: Exception) {
                                            Log.e("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Error closing drawer", e)
                                        }
                                    }
                                },
                                lastNavTime = lastNavTime, // Pass state
                                debounceDelay = debounceDelay, // Pass delay
                                updateLastNavTime = { newTime -> lastNavTime = newTime } // Pass update lambda
                            )
                            DrawerItem(
                                label = "Downloads",
                                icon = Icons.Filled.Download,
                                route = "download_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = {
                                    Log.d("DrawerAction", "[${System.currentTimeMillis()}] DrawerItem: Requesting drawer close. Current Drawer State: ${drawerState.currentValue}")
                                    scope.launch {
                                        Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Attempting drawerState.close()")
                                        try {
                                            drawerState.close()
                                            Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: drawerState.close() completed.")
                                        } catch (e: Exception) {
                                            Log.e("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Error closing drawer", e)
                                        }
                                    }
                                },
                                lastNavTime = lastNavTime,
                                debounceDelay = debounceDelay,
                                updateLastNavTime = { newTime -> lastNavTime = newTime }
                            )
                            DrawerItem(
                                label = "Files",
                                icon = Icons.Filled.Folder,
                                route = "files_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = {
                                    Log.d("DrawerAction", "[${System.currentTimeMillis()}] DrawerItem: Requesting drawer close. Current Drawer State: ${drawerState.currentValue}")
                                    scope.launch {
                                        Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Attempting drawerState.close()")
                                        try {
                                            drawerState.close()
                                            Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: drawerState.close() completed.")
                                        } catch (e: Exception) {
                                            Log.e("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Error closing drawer", e)
                                        }
                                    }
                                },
                                lastNavTime = lastNavTime,
                                debounceDelay = debounceDelay,
                                updateLastNavTime = { newTime -> lastNavTime = newTime }
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
                                        Log.d("DrawerAction", "[${System.currentTimeMillis()}] HomeScreen: Menu icon clicked. Current Drawer State: ${drawerState.currentValue}")
                                        scope.launch {
                                            Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Attempting drawerState.open()")
                                            try {
                                                drawerState.open()
                                                Log.d("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: drawerState.open() completed.")
                                            } catch (e: Exception) {
                                                Log.e("DrawerAction", "[${System.currentTimeMillis()}] Coroutine: Error opening drawer", e)
                                            }
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
                                FilesScreen(navController = navController)
                            }
                            composable(
                                route = "local_album_screen/{folderPath}",
                                arguments = listOf(
                                    navArgument("folderPath") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                LocalAlbumScreen(navController = navController)
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
    navigateOnClick: Boolean = true,
    // Debounce parameters
    lastNavTime: Long,
    debounceDelay: Long,
    updateLastNavTime: (Long) -> Unit
) {
    val isSelected = currentRoute == route
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        selected = isSelected,
        onClick = {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNavTime > debounceDelay) { // Check debounce time
                updateLastNavTime(currentTime) // Update last navigation time

                closeDrawer() // Close drawer first

                if (navigateOnClick && currentRoute != route) {
                    Log.d("DrawerNav", "[${currentTime}] Debounced Navigating to '$route'")
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    Log.d("DrawerNav", "[${currentTime}] Skipping nav (already on route or !navigateOnClick)")
                }
            } else {
                Log.d("DrawerNav", "[${currentTime}] Skipping nav (debounced)")
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