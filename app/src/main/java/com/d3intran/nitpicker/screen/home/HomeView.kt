package com.d3intran.nitpicker.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavController
import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.screen.home.HomeViewModel
import com.d3intran.nitpicker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    navController: NavController,
    openDrawer: () -> Unit
) {
    // Log composition start and end
    DisposableEffect(Unit) {
        Log.d("CompositionLifecycle", "HomeScreen Composed")
        onDispose {
            Log.d("CompositionLifecycle", "HomeScreen Disposed")
        }
    }

    val uiState by homeViewModel.uiState.collectAsState()
    Log.d("HomeScreenState", "Recomposing HomeScreen. isLoading: ${uiState.isLoading}, error: ${uiState.error}, albums: ${uiState.albums.size}") // Log state on recomposition
    val searchText by homeViewModel.searchText.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212) // 暗色背景
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Box to hold Menu Icon and Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                // Menu Icon Button - Top Left
                IconButton(
                    onClick = {
                        Log.d("DrawerAction", "[${System.currentTimeMillis()}] HomeScreen: IconButton onClick triggered.")
                        openDrawer() // Call the lambda passed from MainActivity
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Drawer",
                        tint = Color.White
                    )
                }

                // 标题 - Centered
                Text(
                    text = "Nitpicker",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 搜索框与按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp)
            ) {
                TextField(
                    value = searchText,
                    onValueChange = { homeViewModel.updateSearchText(it) },
                    placeholder = { Text("Search album...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier
                        .fillMaxWidth(),
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

                // "find" 按钮
                Button(
                    onClick = { homeViewModel.searchAlbums() },
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
                    Text(
                        text = "find",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 页面信息
            if (uiState.currentPage > 0) {
                Text(
                    text = if (uiState.totalPages <= 1)
                        "find ${uiState.albums.size} albums"
                    else
                        "page ${uiState.currentPage}/${uiState.totalPages}",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // 专辑列表
            Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                when {
                    uiState.isLoading -> {
                        // 加载中
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6D28D9))
                        }
                    }

                    uiState.error != null -> {
                        // 错误状态
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Error when searching",
                                color = Color.White
                            )
                            Text(
                                text = uiState.error ?: "",
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { homeViewModel.retry() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6D28D9)
                                )
                            ) {
                                Text("retry")
                            }
                        }
                    }

                    uiState.albums.isEmpty() && uiState.currentPage > 0 -> {
                        // 没有结果
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results found",
                                color = Color(0xFFAAAAAA)
                            )
                        }
                    }

                    uiState.albums.isNotEmpty() -> {
                        // 显示专辑列表
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = uiState.albums,
                                key = { album -> album.url } // 添加 key，使用 album.url 作为唯一标识符
                            ) { album ->
                                AlbumItem(album = album, onAlbumClick = { clickedAlbum ->
                                    // Encode URL and Title to be safe navigation arguments
                                    val encodedUrl = java.net.URLEncoder.encode(clickedAlbum.url, "UTF-8")
                                    val encodedTitle = java.net.URLEncoder.encode(clickedAlbum.title, "UTF-8")
                                    // Navigate to album screen
                                    navController.navigate("album_screen/$encodedUrl/$encodedTitle")
                                })
                            }
                        }
                    }

                    uiState.albums.isEmpty() && uiState.currentPage == 0 && !uiState.isLoading && uiState.error == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Please enter an artist name",
                                color = Color(0xFFAAAAAA)
                            )
                        }
                    }
                }
            }

            // 分页控件
            if (uiState.totalPages > 1) {
                PaginationControls(
                    currentPage = uiState.currentPage,
                    totalPages = uiState.totalPages,
                    onPageSelected = { homeViewModel.loadPage(it) }
                )
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
            // 专辑图标
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

            // 专辑信息
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
                    text = "${album.file} files",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }

            // 箭头图标
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一页按钮
        PaginationButton(
            text = "←",
            onClick = { onPageSelected(currentPage - 1) },
            enabled = currentPage > 1
        )
        
        // 页码按钮
        val visiblePages = calculateVisiblePages(currentPage, totalPages)
        
        if (visiblePages.first > 1) {
            PaginationButton(
                text = "1",
                onClick = { onPageSelected(1) }
            )
            
            if (visiblePages.first > 2) {
                Text(
                    text = "...",
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
                    text = "...",
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            PaginationButton(
                text = totalPages.toString(),
                onClick = { onPageSelected(totalPages) }
            )
        }
        
        // 下一页按钮
        PaginationButton(
            text = "→",
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