package com.auso.social.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auso.social.network.AusoApiClient
import com.auso.social.ui.navigation.Routes
import com.auso.social.ui.screens.*
import com.auso.social.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Main screen with bottom navigation bar and top bar
 * Top bar hides/shows on scroll with animation
 * No HorizontalPager — tabs switch only via dropdown to avoid heavy rendering
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit = {}
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Feed filter tabs for Home — NO pager, just switch directly
    val feedTabs = listOf("Amigos", "Recomendado", "Explorar")
    var selectedFeedTab by remember { mutableIntStateOf(0) }

    // Dropdown menu state
    var showFeedMenu by remember { mutableStateOf(false) }

    // Top bar visibility state - animated hide/show on scroll
    var isTopBarVisible by remember { mutableStateOf(true) }
    // Measure actual top bar height (includes status bar + app bar)
    var measuredTopBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topBarHeightDp = if (measuredTopBarHeightPx > 0) {
        with(density) { measuredTopBarHeightPx.toDp() }
    } else {
        112.dp
    }

    // Global mute state for all videos - default is unmuted (audio ON)
    var isGlobalMuted by remember { mutableStateOf(false) }

    // Currently playing video id - only one plays at a time
    var currentlyPlayingVideoId by remember { mutableStateOf<String?>(null) }

    // Track if video overlay is open (to hide top bar)
    var isVideoOverlayOpen by remember { mutableStateOf(false) }

    // Bottom nav
    val bottomNavItems = listOf(
        BottomNavItem(Routes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(Routes.SEARCH, "Buscar", Icons.Filled.Search, Icons.Outlined.Search),
        BottomNavItem(Routes.VIDEOS, "Videos", Icons.Filled.Movie, Icons.Outlined.Movie),
        BottomNavItem(Routes.APPS, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps)
    )

    var selectedBottomTab by remember { mutableIntStateOf(0) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showUserProfileScreen by remember { mutableStateOf(false) }
    var viewingUsername by remember { mutableStateOf("") }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Image picker for post creation
    var postImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val postImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { postImageUris = postImageUris + it }
    }

    // Video picker for post creation
    var postVideoUri by remember { mutableStateOf<Uri?>(null) }
    val postVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { postVideoUri = it }
    }

    // Profile screen overlay
    if (showProfileScreen) {
        ProfileScreen(
            authViewModel = authViewModel,
            onBack = { showProfileScreen = false },
            onLogout = onLogout
        )
        return
    }

    // Other user's profile screen overlay
    if (showUserProfileScreen) {
        UserProfileScreen(
            username = viewingUsername,
            onBack = { showUserProfileScreen = false },
            onAuthorClick = { username ->
                viewingUsername = username
                showUserProfileScreen = true
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.height(56.dp)
            ) {
                bottomNavItems.forEachIndexed { index, item ->
                    val selected = selectedBottomTab == index
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = null,
                        selected = selected,
                        onClick = { selectedBottomTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // For Videos tab (TikTok-style), we want full-screen behind the bottom nav
        val bottomPadding = if (selectedBottomTab == 2) 0.dp else innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding)
        ) {
            // Content — no pager, only render the selected tab
            when (selectedBottomTab) {
                0 -> {
                    HomeScreen(
                        tabName = feedTabs[selectedFeedTab],
                        authViewModel = authViewModel,
                        refreshTrigger = refreshTrigger,
                        onAuthorClick = { username ->
                            viewingUsername = username
                            showUserProfileScreen = true
                        },
                        onScrollDirection = { direction ->
                            when {
                                direction > 0 -> isTopBarVisible = true
                                direction < 0 -> isTopBarVisible = false
                            }
                        },
                        currentlyPlayingVideoId = currentlyPlayingVideoId,
                        onVideoPlayChanged = { videoId ->
                            currentlyPlayingVideoId = videoId
                        },
                        isGlobalMuted = isGlobalMuted,
                        onMuteChanged = { muted ->
                            isGlobalMuted = muted
                        },
                        topBarHeightDp = topBarHeightDp,
                        isTopBarVisible = isTopBarVisible,
                        onVideoOverlayChanged = { isOpen ->
                            isVideoOverlayOpen = isOpen
                        }
                    )
                }
                1 -> SearchScreen()
                2 -> VideosScreen(
                    isGlobalMuted = isGlobalMuted,
                    onMuteChanged = { muted ->
                        isGlobalMuted = muted
                    },
                    onAuthorClick = { username ->
                        viewingUsername = username
                        showUserProfileScreen = true
                    }
                )
                3 -> AppsScreen()
            }

            // Floating top bar — overlays the content, slides in/out, hidden when video overlay is open
            AnimatedVisibility(
                visible = isTopBarVisible && !isVideoOverlayOpen && selectedBottomTab != 2,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            measuredTopBarHeightPx = coordinates.size.height
                        },
                    shadowElevation = 3.dp,
                    tonalElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    when (selectedBottomTab) {
                        0 -> {
                            TopAppBar(
                                windowInsets = WindowInsets.statusBars,
                                title = {
                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .clickable { showFeedMenu = true }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = feedTabs[selectedFeedTab],
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 18.sp
                                            )
                                            Icon(
                                                Icons.Filled.ArrowDropDown,
                                                contentDescription = "Cambiar filtro",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showFeedMenu,
                                            onDismissRequest = { showFeedMenu = false }
                                        ) {
                                            feedTabs.forEachIndexed { index, tab ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            tab,
                                                            fontWeight = if (index == selectedFeedTab) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (index == selectedFeedTab) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedFeedTab = index
                                                        showFeedMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                ),
                                actions = {
                                    IconButton(onClick = { /* TODO: Open search */ }) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = "Buscar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    IconButton(onClick = { /* TODO: Open notifications */ }) {
                                        Icon(
                                            Icons.Filled.Notifications,
                                            contentDescription = "Notificaciones",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable { showProfileScreen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val photoUrl = currentUser?.profilePhotoUrl
                                        if (!photoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = AusoApiClient.fullUrl(photoUrl),
                                                contentDescription = "Foto de perfil",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            val displayName = currentUser?.displayName ?: ""
                                            val initial = displayName.take(1).ifBlank { "U" }
                                            Text(
                                                text = initial.uppercase(),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            )
                        }
                        1 -> {
                            TopAppBar(
                                windowInsets = WindowInsets.statusBars,
                                title = {
                                    Text(
                                        text = "Buscar",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                            )
                        }
                        2 -> {
                            TopAppBar(
                                windowInsets = WindowInsets.statusBars,
                                title = {
                                    Text(
                                        text = "Videos",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                            )
                        }
                        3 -> {
                            TopAppBar(
                                windowInsets = WindowInsets.statusBars,
                                title = {
                                    Text(
                                        text = "Apps",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

            // FAB only on Home tab
            if (selectedBottomTab == 0) {
                FloatingActionButton(
                    onClick = { showCreatePostDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Crear post")
                }
            }
        }
    }

    // Create post dialog
    if (showCreatePostDialog) {
        CreatePostDialog(
            onDismiss = {
                showCreatePostDialog = false
                postImageUris = emptyList()
                postVideoUri = null
            },
            onPostCreated = { refreshTrigger++ },
            selectedImages = postImageUris,
            onAddImage = { postImagePicker.launch("image/*") },
            onRemoveImage = { uri -> postImageUris = postImageUris - uri },
            selectedVideo = postVideoUri,
            onAddVideo = { postVideoPicker.launch("video/*") },
            onRemoveVideo = { postVideoUri = null }
        )
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)
