package com.auso.social.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.PostResponse
import com.auso.social.network.model.UpdateProfileRequest
import com.auso.social.network.model.UserProfile
import com.auso.social.network.model.UserStats
import com.auso.social.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var profile by remember { mutableStateOf<UserProfile?>(currentUser) }
    var stats by remember { mutableStateOf<UserStats?>(null) }
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showInterestsEditor by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf("") }

    // Local URIs for preview before upload
    var localProfilePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var localCoverPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Load profile and user posts
    LaunchedEffect(Unit) {
        try {
            val token = AusoApiClient.getToken()
            if (token != null) {
                val response = AusoApiClient.api.getMyProfile("Bearer $token")
                if (response.isSuccessful) {
                    val body = response.body()
                    profile = body?.user
                    stats = body?.stats
                    authViewModel.loadProfile()
                }
                // Try to get user's posts (using feed for now)
                val feedResponse = AusoApiClient.api.getFeed("Bearer $token")
                if (feedResponse.isSuccessful) {
                    val allPosts = feedResponse.body()?.posts ?: emptyList()
                    // Filter to current user's posts
                    val userId = profile?.id ?: ""
                    posts = allPosts.filter { it.post.userId == userId }
                }
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    // Photo pickers
    val profilePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            localProfilePhotoUri = uri
            // Upload profile photo
            coroutineScope.launch {
                try {
                    val token = AusoApiClient.getToken()
                    if (token != null) {
                        val part = AusoApiClient.uriToMultipartPart(context, uri)
                        if (part != null) {
                            val response = AusoApiClient.api.uploadProfilePhoto("Bearer $token", part)
                            if (response.isSuccessful) {
                                // Reload profile to get updated photo URL
                                val profileResponse = AusoApiClient.api.getMyProfile("Bearer $token")
                                if (profileResponse.isSuccessful) {
                                    val body = profileResponse.body()
                                    profile = body?.user
                                    stats = body?.stats
                                    authViewModel.loadProfile()
                                }
                                localProfilePhotoUri = null
                            } else {
                                uploadError = "Error al subir foto de perfil (el servidor podria no soportarlo aun)"
                                // Keep local preview anyway
                            }
                        }
                    }
                } catch (e: Exception) {
                    uploadError = "Error de conexion al subir foto"
                }
            }
        }
    }

    val coverPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            localCoverPhotoUri = uri
            // Upload cover photo
            coroutineScope.launch {
                try {
                    val token = AusoApiClient.getToken()
                    if (token != null) {
                        val part = AusoApiClient.uriToMultipartPart(context, uri)
                        if (part != null) {
                            val response = AusoApiClient.api.uploadCoverPhoto("Bearer $token", part)
                            if (response.isSuccessful) {
                                // Reload profile to get updated photo URL
                                val profileResponse = AusoApiClient.api.getMyProfile("Bearer $token")
                                if (profileResponse.isSuccessful) {
                                    val body = profileResponse.body()
                                    profile = body?.user
                                    stats = body?.stats
                                    authViewModel.loadProfile()
                                }
                                localCoverPhotoUri = null
                            } else {
                                uploadError = "Error al subir foto de portada (el servidor podria no soportarlo aun)"
                            }
                        }
                    }
                } catch (e: Exception) {
                    uploadError = "Error de conexion al subir portada"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "@${profile?.username ?: ""}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar perfil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Upload error message
                if (uploadError.isNotBlank()) {
                    item {
                        Snackbar(
                            modifier = Modifier.padding(16.dp),
                            action = {
                                TextButton(onClick = { uploadError = "" }) {
                                    Text("OK")
                                }
                            }
                        ) {
                            Text(uploadError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Cover photo
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { coverPhotoLauncher.launch("image/*") },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Show local preview or server image
                        val coverUri = localCoverPhotoUri
                        val coverUrl = profile?.coverPhotoUrl
                        when {
                            coverUri != null -> {
                                AsyncImage(
                                    model = coverUri,
                                    contentDescription = "Portada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            !coverUrl.isNullOrBlank() -> {
                                AsyncImage(
                                    model = AusoApiClient.fullUrl(coverUrl),
                                    contentDescription = "Portada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        // Camera icon overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Cambiar portada",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Profile photo overlapping cover
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-48).dp)
                            .padding(start = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { profilePhotoLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            // Show local preview, server image, or initial
                            val photoUri = localProfilePhotoUri
                            val photoUrl = profile?.profilePhotoUrl
                            when {
                                photoUri != null -> {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Foto de perfil",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                !photoUrl.isNullOrBlank() -> {
                                    AsyncImage(
                                        model = AusoApiClient.fullUrl(photoUrl),
                                        contentDescription = "Foto de perfil",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> {
                                    val initial = (profile?.displayName ?: "U").take(1).uppercase()
                                    Text(
                                        text = initial,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    )
                                }
                            }
                            // Camera overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    contentDescription = "Cambiar foto",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Name, username, bio
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = profile?.displayName?.ifBlank { profile?.username ?: "" } ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "@${profile?.username ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!profile?.bio.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = profile?.bio ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        // Location, website
                        if (!profile?.location.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${profile?.location}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!profile?.website.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = profile?.website ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Stats row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        StatItem("Publicaciones", (stats?.postsCount ?: posts.size.toLong()).toInt())
                        StatItem("Seguidores", (stats?.followersCount ?: 0L).toInt())
                        StatItem("Siguiendo", (stats?.followingCount ?: 0L).toInt())
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }

                // User's posts
                if (posts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Aun no tienes publicaciones",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    items(posts.size) { index ->
                        val postResponse = posts[index]
                        PostCard(
                            postResponse = postResponse,
                            onLikeClick = {
                                coroutineScope.launch {
                                    try {
                                        val token = AusoApiClient.getToken()
                                        if (token != null) {
                                            AusoApiClient.api.toggleLike(
                                                "Bearer $token",
                                                postResponse.post.id
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onCommentClick = { /* TODO */ }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Edit interests button — lets the user re-pick their interest categories
                // so the recommendation algorithm learns from the new selection.
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showInterestsEditor = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Editar intereses")
                    }
                }

                // Logout button
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cerrar sesion")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Interests editor overlay — reuses InterestsScreen so the user can update
    // their interest categories (retrains the recommendation algorithm).
    if (showInterestsEditor) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            InterestsScreen(
                onComplete = { showInterestsEditor = false }
            )
        }
    }

    // Edit profile dialog
    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = { displayName, bio, location, website ->
                coroutineScope.launch {
                    try {
                        val token = AusoApiClient.getToken()
                        if (token != null) {
                            val request = UpdateProfileRequest(
                                displayName = displayName,
                                bio = bio,
                                location = location,
                                website = website
                            )
                            val response = AusoApiClient.api.updateProfile("Bearer $token", request)
                            if (response.isSuccessful) {
                                // Reload profile
                                val profileResponse = AusoApiClient.api.getMyProfile("Bearer $token")
                                if (profileResponse.isSuccessful) {
                                    val body = profileResponse.body()
                                    profile = body?.user
                                    stats = body?.stats
                                    authViewModel.loadProfile()
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun StatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun EditProfileDialog(
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onSave: (displayName: String?, bio: String?, location: String?, website: String?) -> Unit
) {
    var displayName by remember { mutableStateOf(profile?.displayName ?: "") }
    var bio by remember { mutableStateOf(profile?.bio ?: "") }
    var location by remember { mutableStateOf(profile?.location ?: "") }
    var website by remember { mutableStateOf(profile?.website ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Editar perfil",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Ubicacion") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Sitio web") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    displayName.ifBlank { null },
                    bio.ifBlank { null },
                    location.ifBlank { null },
                    website.ifBlank { null }
                )
            }) {
                Text("Guardar", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
