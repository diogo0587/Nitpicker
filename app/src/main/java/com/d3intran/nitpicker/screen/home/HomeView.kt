package com.d3intran.nitpicker.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.d3intran.nitpicker.R
import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.SearchMode
import com.d3intran.nitpicker.screen.home.HomeViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    navController: NavController,
    openDrawer: () -> Unit
) {
    DisposableEffect(Unit) {
        Log.d("CompositionLifecycle", "HomeScreen Composed")
        onDispose {
            Log.d("CompositionLifecycle", "HomeScreen Disposed")
        }
    }

    val uiState by homeViewModel.uiState.collectAsState()
    Log.d("HomeScreenState", "Recomposing HomeScreen. isLoading: ${uiState.isLoading}, error: ${uiState.error}, albums: ${uiState.albums.size}")
    val searchText by homeViewModel.searchText.collectAsState()

    val unknownError = stringResource(R.string.error_unknown)
    val errorTitle = stringResource(R.string.home_error_title)
    val noResultsText = stringResource(R.string.home_no_results)
    val initialPromptText = stringResource(R.string.home_initial_prompt)

    // --- Set System Bar Color for HomeScreen ---
    // val view = LocalView.current  // <-- REMOVE or COMMENT OUT
    val homeBackgroundColor = Color(0xFF121212) // HomeScreen's background color
    // You might have had a DisposableEffect here previously to set the color, remove that too if present.

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = homeBackgroundColor
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // --- Header ---
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            Log.d("DrawerAction", "[${System.currentTimeMillis()}] HomeScreen: IconButton onClick triggered.")
                            openDrawer()
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.home_open_drawer),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_title),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // --- Removed AI Stats Section ---

            // --- Dynamic Top Tags Grid ---
            item {
                CategoryGridSection(
                    topTags = uiState.allTagsWithCount.take(7),
                    viewModel = homeViewModel,
                    onNavigateToAllTags = { navController.navigate("all_tags_screen") }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Search Section ---
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TabRow(
                        selectedTabIndex = if (uiState.searchMode == SearchMode.LOCAL) 0 else 1,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[if (uiState.searchMode == SearchMode.LOCAL) 0 else 1]),
                                color = Color(0xFF6D28D9)
                            )
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Tab(
                            selected = uiState.searchMode == SearchMode.LOCAL,
                            onClick = { homeViewModel.setSearchMode(SearchMode.LOCAL) },
                            text = { Text("Local AI") }
                        )
                        Tab(
                            selected = uiState.searchMode == SearchMode.ONLINE,
                            onClick = { homeViewModel.setSearchMode(SearchMode.ONLINE) },
                            text = { Text("Online Albums") }
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = searchText,
                            onValueChange = { homeViewModel.updateSearchText(it) },
                            placeholder = {
                                Text(if (uiState.searchMode == SearchMode.LOCAL) "Search local AI tags/faces..." else "Search online albums...")
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                unfocusedTextColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedContainerColor = Color(0xFF252525),
                                focusedContainerColor = Color(0xFF252525),
                                unfocusedPlaceholderColor = Color(0xFFAAAAAA),
                                focusedPlaceholderColor = Color(0xFFAAAAAA),
                                cursorColor = Color(0xFF6D28D9),
                                focusedIndicatorColor = Color(0xFF6D28D9),
                                unfocusedIndicatorColor = Color(0xFF333333)
                            ),
                            shape = RoundedCornerShape(50),
                            singleLine = true
                        )
                        if (searchText.isNotEmpty()) {
                            Button(
                                onClick = { homeViewModel.executeSearch() },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .height(40.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6D28D9),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Search", fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Status text ---
            if (uiState.currentPage > 0) {
                item {
                    Text(
                        text = if (uiState.totalPages <= 1)
                            stringResource(R.string.home_page_info_count, uiState.albums.size)
                        else
                            stringResource(R.string.home_page_info_pages, uiState.currentPage, uiState.totalPages),
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // --- Results ---
            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = Color(0xFF6D28D9)) }
                    }
                }
                uiState.error != null -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = errorTitle, color = Color.White)
                            Text(text = uiState.error ?: unknownError, color = Color(0xFFAAAAAA), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { homeViewModel.retry() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9))
                            ) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
                uiState.localResults.isNotEmpty() -> {
                    item {
                        Text("Local AI Insights", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(uiState.localResults) { result ->
                        val index = uiState.localResults.indexOf(result)
                        LocalSearchResultItem(result) {
                            homeViewModel.openMedia(index)
                            navController.navigate("media_viewer?uri=")
                        }
                    }
                }
                uiState.albums.isNotEmpty() -> {
                    item {
                        Text("Online Albums", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(items = uiState.albums, key = { it.url }) { album ->
                        AlbumItem(album = album, onAlbumClick = { clickedAlbum ->
                            val encodedUrl = java.net.URLEncoder.encode(clickedAlbum.url, "UTF-8")
                            val encodedTitle = java.net.URLEncoder.encode(clickedAlbum.title, "UTF-8")
                            navController.navigate("album_screen/$encodedUrl/$encodedTitle")
                        })
                    }
                }
                uiState.currentPage == 0 && !uiState.isLoading && uiState.error == null -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Search to discover AI insights or browse categories above", color = Color(0xFFAAAAAA), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }

            // --- Pagination ---
            if (uiState.totalPages > 1) {
                item {
                    PaginationControls(
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages,
                        onPageSelected = { homeViewModel.loadPage(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumItem(album: Album, onAlbumClick: (Album) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onAlbumClick(album) },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF121212)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = album.title,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.home_album_file_count, album.fileCount),
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit
) {
    val ellipsis = stringResource(R.string.home_pagination_ellipsis)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaginationButton(
            text = stringResource(R.string.home_pagination_previous),
            onClick = { onPageSelected(currentPage - 1) },
            enabled = currentPage > 1
        )

        val visiblePages = calculateVisiblePages(currentPage, totalPages)

        if (visiblePages.first > 1) {
            PaginationButton(
                text = "1",
                onClick = { onPageSelected(1) }
            )

            if (visiblePages.first > 2) {
                Text(
                    text = ellipsis,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        for (page in visiblePages.first..visiblePages.second) {
            PaginationButton(
                text = page.toString(),
                onClick = { onPageSelected(page) },
                isSelected = page == currentPage
            )
        }

        if (visiblePages.second < totalPages) {
            if (visiblePages.second < totalPages - 1) {
                Text(
                    text = ellipsis,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            PaginationButton(
                text = totalPages.toString(),
                onClick = { onPageSelected(totalPages) }
            )
        }

        PaginationButton(
            text = stringResource(R.string.home_pagination_next),
            onClick = { onPageSelected(currentPage + 1) },
            enabled = currentPage < totalPages
        )
    }
}

@Composable
fun PaginationButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isSelected: Boolean = false
) {
    val backgroundColor = when {
        !enabled -> Color(0xFF1E1E1E).copy(alpha = 0.5f)
        isSelected -> Color(0xFF6D28D9)
        else -> Color(0xFF1E1E1E)
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(width = 40.dp, height = 36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = Color.White,
            disabledContainerColor = backgroundColor,
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text)
    }
}

@Composable
private fun calculateVisiblePages(currentPage: Int, totalPages: Int): Pair<Int, Int> {
    val isMobile = LocalConfiguration.current.screenWidthDp <= 640
    val pageRange = if (isMobile) 1 else 2

    var startPage = Math.max(1, currentPage - pageRange)
    var endPage = Math.min(totalPages, currentPage + pageRange)

    if (endPage - startPage < pageRange * 2 && totalPages > pageRange * 2 + 1) {
        if (startPage == 1) {
            endPage = Math.min(1 + pageRange * 2, totalPages)
        } else if (endPage == totalPages) {
            startPage = Math.max(1, totalPages - pageRange * 2)
        }
    }

    return Pair(startPage, endPage)
}



// Category data class
data class CategoryItem(
    val label: String,
    val emoji: String,
    val color: Long,
    val count: Int? = null,
    val action: () -> Unit
)

@Composable
fun CategoryGridSection(
    topTags: List<Pair<String, Int>>,
    viewModel: HomeViewModel,
    onNavigateToAllTags: () -> Unit
) {
    // Generate a fixed palette for the dynamic tags
    val colors = listOf(0xFF6D28D9, 0xFF0D9488, 0xFFDC2626, 0xFF16A34A, 0xFF2563EB, 0xFFEA580C, 0xFFD97706)
    
    // Take exactly 7 tags (or less if not enough exist)
    val dynamicTags = topTags.take(7)
    
    // Map them to CategoryItem format
    val categories = dynamicTags.mapIndexed { index, tagPair ->
        CategoryItem(
            label = tagPair.first.replaceFirstChar { it.uppercase() },
            emoji = "\uD83C\uDFF7", // Generic tag emoji
            color = colors[index % colors.size],
            count = tagPair.second
        ) {
            viewModel.setSearchMode(SearchMode.LOCAL)
            viewModel.updateSearchText(tagPair.first)
            viewModel.executeSearch()
        }
    }.toMutableList()

    // Always append the "All Tags" button at the end
    categories.add(
        CategoryItem("All Tags", "\uD83D\uDCDC", 0xFF64748B, null) { onNavigateToAllTags() }
    )

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(
            "Top AI Tags",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        categories.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { cat ->
                    Card(
                        onClick = cat.action,
                        modifier = Modifier.weight(1f).height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(cat.color).copy(alpha = 0.18f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.emoji, fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    cat.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (cat.count != null) {
                                    Text(
                                        "${cat.count} items",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LocalSearchResultItem(item: com.d3intran.nitpicker.model.LocalFileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF252525)),
                contentAlignment = Alignment.Center
            ) {
                coil.compose.AsyncImage(
                    model = item.thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.path.substringAfterLast('/'), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.tags.take(3).forEach { tag ->
                        Text("#$tag", color = Color(0xFFA78BFA), fontSize = 11.sp)
                    }
                }
            }
            if (item.faceCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                    Text(item.faceCount.toString(), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}

