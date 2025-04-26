package com.d3intran.nitpicker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat // <-- Import WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.d3intran.nitpicker.screen.album.AlbumScreen
import com.d3intran.nitpicker.screen.download.DownloadScreen
import com.d3intran.nitpicker.screen.home.HomeViewModel
import com.d3intran.nitpicker.ui.screens.HomeScreen
import com.d3intran.nitpicker.screen.files.FilesScreen
import com.d3intran.nitpicker.screen.local_album.LocalAlbumScreen
import com.d3intran.nitpicker.screen.player.PlayerScreen
import com.d3intran.nitpicker.screen.image.ImageViewerScreen
import com.d3intran.nitpicker.ui.theme.NitpickerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // --- Add this line for edge-to-edge display ---
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // --- End edge-to-edge configuration ---

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
                // Disable drawer gesture if the current route starts with "player_screen/"
                val gesturesEnabled = currentRoute?.startsWith("player_screen/") != true
                Log.d("NavigationFlow", "Gestures Enabled: $gesturesEnabled for route: $currentRoute")


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
                    gesturesEnabled = gesturesEnabled, // Use the calculated value
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .width(drawerWidth)
                                .fillMaxHeight(),
                            drawerContainerColor = Color(0xFF1E1E1E)
                        ) {
                            Spacer(Modifier.height(12.dp))
                            DrawerItem(
                                label = stringResource(id = R.string.drawer_home),
                                icon = Icons.Filled.Home,
                                route = "home_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = {
                                    scope.launch {
                                        try {
                                            drawerState.close()
                                        } catch (e: Exception) {
                                            Log.e("DrawerAction", "Error closing drawer", e)
                                        }
                                    }
                                },
                                scope = scope, // <-- 传递 scope
                                lastNavTime = lastNavTime,
                                debounceDelay = debounceDelay,
                                updateLastNavTime = { newTime -> lastNavTime = newTime }
                            )
                            DrawerItem(
                                label = stringResource(id = R.string.drawer_downloads),
                                icon = Icons.Filled.Download,
                                route = "download_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = {
                                    scope.launch {
                                        try {
                                            drawerState.close()
                                        } catch (e: Exception) {
                                            Log.e("DrawerAction", "Error closing drawer", e)
                                        }
                                    }
                                },
                                scope = scope, // <-- 传递 scope
                                lastNavTime = lastNavTime,
                                debounceDelay = debounceDelay,
                                updateLastNavTime = { newTime -> lastNavTime = newTime }
                            )
                            DrawerItem(
                                label = stringResource(id = R.string.drawer_files),
                                icon = Icons.Filled.Folder,
                                route = "files_screen",
                                currentRoute = currentRoute,
                                navController = navController,
                                closeDrawer = {
                                    scope.launch {
                                        try {
                                            drawerState.close()
                                        } catch (e: Exception) {
                                            Log.e("DrawerAction", "Error closing drawer", e)
                                        }
                                    }
                                },
                                scope = scope, // <-- 传递 scope
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
                                        scope.launch {
                                            try {
                                                drawerState.open()
                                            } catch (e: Exception) {
                                                Log.e("DrawerAction", "Error opening drawer", e)
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
                            composable(
                                route = "player_screen/{folderPath}/{initialIndex}",
                                arguments = listOf(
                                    navArgument("folderPath") { type = NavType.StringType },
                                    navArgument("initialIndex") { type = NavType.IntType }
                                )
                            ) { backStackEntry ->
                                // PlayerScreen will now draw under the status bar
                                PlayerScreen(navController = navController)
                            }

                            composable(
                                route = "image_viewer_screen/{folderPath}/{initialIndex}",
                                arguments = listOf(
                                    navArgument("folderPath") { type = NavType.StringType },
                                    navArgument("initialIndex") { type = NavType.IntType }
                                )
                            ) { backStackEntry ->
                                ImageViewerScreen(navController = navController)
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
    scope: CoroutineScope, // <-- 确保参数已添加
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

                if (navigateOnClick && currentRoute != route) {
                    Log.d("DrawerNav", "[${currentTime}] Debounced Navigating to '$route'")
                    // Close drawer slightly after starting navigation to avoid visual glitch
                    scope.launch { // Now scope is accessible
                         try {
                             closeDrawer()
                         } catch (e: Exception) {
                             Log.e("DrawerNav", "Error closing drawer in scope", e)
                         }
                    }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                     // If not navigating, just close the drawer
                     closeDrawer() // Use the passed lambda which captures the correct scope
                    Log.d("DrawerNav", "[${currentTime}] Skipping nav (already on route or !navigateOnClick), closing drawer.")
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