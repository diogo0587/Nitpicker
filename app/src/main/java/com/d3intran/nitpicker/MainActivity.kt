package com.d3intran.nitpicker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.* // Import layout package for WindowInsets
import androidx.compose.foundation.layout.WindowInsets // Import WindowInsets
import androidx.compose.foundation.layout.statusBars // Import statusBars
import androidx.compose.foundation.layout.navigationBars // Import navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding // Import windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb // Import toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
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
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NitpickerTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var lastNavTime by remember { mutableStateOf(0L) }
                val debounceDelay = 500L

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isImmersive = currentRoute?.startsWith("player_screen/") == true ||
                                  currentRoute?.startsWith("image_viewer_screen/") == true

                // --- Centralized System Bar Appearance Management ---
                LaunchedEffect(currentRoute, window) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    val homeBackgroundColor = Color(0xFF121212) // Define home background color
                    val defaultTopAppBarColor = Color(0xFF1E1E1E) // Define default TopAppBar color
                    val defaultBackgroundColor = Color(0xFF121212) // Define default background color

                    if (isImmersive) {
                        // Immersive screens: Transparent bars, light icons
                        window.statusBarColor = Color.Transparent.toArgb()
                        window.navigationBarColor = Color.Transparent.toArgb()
                        controller?.isAppearanceLightStatusBars = false
                        controller?.isAppearanceLightNavigationBars = false
                        Log.d("SystemUI_Main", "Route '$currentRoute' is immersive. Setting transparent bars.")
                    } else {
                        // Non-immersive screens:
                        // Set status bar color based on the route
                        val statusBarColor = if (currentRoute == "home_screen") {
                            homeBackgroundColor // Use home background for HomeScreen status bar
                        } else {
                            defaultTopAppBarColor // Use default TopAppBar color for others
                        }
                        window.statusBarColor = statusBarColor.toArgb()

                        // Set navigation bar color (using default background)
                        window.navigationBarColor = defaultBackgroundColor.toArgb()

                        // Set icon appearance (assuming dark backgrounds need light icons)
                        controller?.isAppearanceLightStatusBars = false
                        controller?.isAppearanceLightNavigationBars = false
                        Log.d("SystemUI_Main", "Route '$currentRoute' is NOT immersive. Setting status bar to ${statusBarColor}, nav bar to ${defaultBackgroundColor}.")
                    }
                }
                // --- End Centralized Management ---

                val gesturesEnabled = !isImmersive

                val configuration = LocalConfiguration.current
                val screenWidthDp = configuration.screenWidthDp.dp
                val screenHeightDp = configuration.screenHeightDp.dp
                val drawerWidth = if (screenWidthDp > screenHeightDp) screenWidthDp * 0.5f else screenWidthDp * 0.7f

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = gesturesEnabled,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .width(drawerWidth)
                                .fillMaxHeight(),
                            drawerContainerColor = Color(0xFF1E1E1E)
                        ) {
                            Spacer(Modifier.height(12.dp))
                            // --- Drawer Items ---
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
                        modifier = Modifier
                            .fillMaxSize()
                            // Apply navigation bar padding here if needed globally,
                            // or handle it within each screen's Scaffold content padding
                            .navigationBarsPadding(), // Apply nav bar padding to push content up
                        color = MaterialTheme.colorScheme.background // Use theme background
                    ) {
                        NavHost(navController = navController, startDestination = "home_screen") {
                            // --- Screen composables ---
                            // Ensure these composables DO NOT apply statusBarsPadding() themselves globally
                            // but DO apply it to their TopAppBar
                            composable("home_screen") {
                                HomeScreen(
                                    homeViewModel = hiltViewModel(),
                                    navController = navController,
                                    openDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                            composable("download_screen") { DownloadScreen(navController = navController) }
                            composable("files_screen") { FilesScreen(navController = navController) }
                            composable(
                                route = "album_screen/{albumUrl}/{albumTitle}",
                                arguments = listOf(
                                    navArgument("albumUrl") { type = NavType.StringType },
                                    navArgument("albumTitle") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                AlbumScreen(navController = navController)
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

// --- DrawerItem Composable (ensure it's updated as before) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    route: String,
    currentRoute: String?,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    scope: CoroutineScope,
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