package com.auso.social.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.CreateTextPostRequest
import com.auso.social.network.model.PostResponse
import com.auso.social.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import kotlin.math.roundToInt

/**
 * Home screen - shows the post feed
 * FAB and create post dialog are handled by MainScreen
 */
@Composable
fun HomeScreen(
    tabName: String = "Amigos",
    authViewModel: AuthViewModel? = null,
    refreshTrigger: Int = 0,
    onAuthorClick: (String) -> Unit = {},
    onScrollDelta: (Float) -> Unit = {}
) {
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Track scroll direction and notify parent for top bar animation
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevScrollOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
        }.collect { (index, offset, isScrolling) ->
            if (isScrolling) {
                val delta = if (index != prevIndex) {
                    if (index > prevIndex) -50f else 50f
                } else {
                    (prevScrollOffset - offset).toFloat()
                }
                onScrollDelta(delta)
            }
            prevIndex = index
            prevScrollOffset = offset
        }
    }

    // Load feed - re-execute when tabName or refreshTrigger changes
    LaunchedEffect(tabName, refreshTrigger) {
        isLoading = true
        try {
            val token = AusoApiClient.getToken()
            if (token != null) {
                val response = AusoApiClient.api.getFeed("Bearer $token")
                if (response.isSuccessful) {
                    val feed = response.body()
                    posts = feed?.posts ?: emptyList()
                }
            }
        } catch (e: Exception) {
            // Silently fail
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cargando...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (posts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\uD83C\uDFE0",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Bienvenido a AUSO!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (tabName) {
                        "Amigos" -> "Sigue a usuarios para ver sus publicaciones aqui"
                        "Recomendado" -> "Las publicaciones recomendadas apareceran aqui"
                        "Explorar" -> "Explora publicaciones de toda la comunidad"
                        else -> "Sigue a usuarios para ver sus publicaciones aqui"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 88.dp // Space for FAB above bottom bar
                )
            ) {
                items(posts, key = { it.post.id }) { postResponse ->
                    PostCard(
                        postResponse = postResponse,
                        onLikeClick = {
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token != null) {
                                        val response = AusoApiClient.api.toggleLike(
                                            "Bearer $token",
                                            postResponse.post.id
                                        )
                                        if (response.isSuccessful) {
                                            // Update locally instead of full refresh
                                            val newLiked = response.body()?.liked ?: !postResponse.isLiked
                                            val newCount = response.body()?.likesCount ?: postResponse.likesCount
                                            posts = posts.map {
                                                if (it.post.id == postResponse.post.id) {
                                                    it.copy(isLiked = newLiked, likesCount = newCount)
                                                } else it
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        onCommentClick = { /* TODO: Comments */ },
                        onAuthorClick = onAuthorClick
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPostCreated: () -> Unit,
    selectedImages: List<Uri> = emptyList(),
    onAddImage: () -> Unit = {},
    onRemoveImage: (Uri) -> Unit = {},
    selectedVideo: Uri? = null,
    onAddVideo: () -> Unit = {},
    onRemoveVideo: () -> Unit = {}
) {
    var content by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        title = {
            Text(
                text = "Crear publicacion",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Text input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Que estas pensando?") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    enabled = !isPosting
                )

                // Media type selector
                if (!isPosting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Image button
                        OutlinedButton(
                            onClick = onAddImage,
                            modifier = Modifier.weight(1f),
                            enabled = selectedVideo == null
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Imagen", fontSize = 12.sp)
                        }

                        // Video button
                        OutlinedButton(
                            onClick = onAddVideo,
                            modifier = Modifier.weight(1f),
                            enabled = selectedImages.isEmpty()
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Video", fontSize = 12.sp)
                        }
                    }
                }

                // Selected images preview
                if (selectedImages.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedImages) { uri ->
                            Box(modifier = Modifier.size(80.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Imagen seleccionada",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (!isPosting) {
                                    IconButton(
                                        onClick = { onRemoveImage(uri) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(
                                                MaterialTheme.colorScheme.error,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Quitar",
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected video preview
                if (selectedVideo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Video seleccionado",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = selectedVideo.lastPathSegment ?: "Video",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isPosting) {
                            IconButton(onClick = onRemoveVideo) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar video",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Progress indicator
                if (isPosting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uploadProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error message
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isPosting) return@TextButton

                    when {
                        selectedVideo != null -> {
                            // Video post - upload as multipart
                            isPosting = true
                            error = ""
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token == null) {
                                        error = "No autenticado"
                                        isPosting = false
                                        return@launch
                                    }

                                    uploadProgress = "Subiendo video..."

                                    val inputStream = context.contentResolver.openInputStream(selectedVideo)
                                    if (inputStream == null) {
                                        error = "No se pudo abrir el video"
                                        isPosting = false
                                        return@launch
                                    }

                                    val bytes = inputStream.readBytes()
                                    inputStream.close()

                                    val videoPart = okhttp3.MultipartBody.Part.createFormData(
                                        "file",
                                        "video.mp4",
                                        okhttp3.RequestBody.create(
                                            "video/*".toMediaType(),
                                            bytes
                                        )
                                    )

                                    val response = AusoApiClient.api.createVideoPost(
                                        "Bearer $token",
                                        videoPart
                                    )
                                    if (response.isSuccessful) {
                                        uploadProgress = "Publicado!"
                                        onPostCreated()
                                        onDismiss()
                                    } else {
                                        error = "Error al publicar video: ${response.code()}"
                                    }
                                } catch (e: Exception) {
                                    error = "Error: ${e.message}"
                                } finally {
                                    isPosting = false
                                }
                            }
                        }
                        selectedImages.isNotEmpty() -> {
                            // Image post - upload as multipart
                            isPosting = true
                            error = ""
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token == null) {
                                        error = "No autenticado"
                                        isPosting = false
                                        return@launch
                                    }

                                    uploadProgress = "Subiendo imagenes..."
                                    val imageParts = selectedImages.mapIndexed { index, uri ->
                                        val inputStream = context.contentResolver.openInputStream(uri)!!
                                        val bytes = inputStream.readBytes()
                                        inputStream.close()
                                        okhttp3.MultipartBody.Part.createFormData(
                                            "files",
                                            "image_$index.jpg",
                                            okhttp3.RequestBody.create(
                                                "image/*".toMediaType(),
                                                bytes
                                            )
                                        )
                                    }

                                    val response = AusoApiClient.api.createImagePost(
                                        "Bearer $token",
                                        imageParts
                                    )
                                    if (response.isSuccessful) {
                                        onPostCreated()
                                        onDismiss()
                                    } else {
                                        error = "Error al publicar: ${response.code()}"
                                    }
                                } catch (e: Exception) {
                                    error = "Error: ${e.message}"
                                } finally {
                                    isPosting = false
                                }
                            }
                        }
                        content.isNotBlank() -> {
                            // Text post
                            isPosting = true
                            error = ""
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token == null) {
                                        error = "No autenticado"
                                        isPosting = false
                                        return@launch
                                    }

                                    val request = CreateTextPostRequest(content = content)
                                    val response = AusoApiClient.api.createTextPost(
                                        "Bearer $token",
                                        request
                                    )
                                    if (response.isSuccessful) {
                                        onPostCreated()
                                        onDismiss()
                                    } else {
                                        error = "Error al publicar: ${response.code()}"
                                    }
                                } catch (e: Exception) {
                                    error = "Error: ${e.message}"
                                } finally {
                                    isPosting = false
                                }
                            }
                        }
                    }
                },
                enabled = !isPosting && (content.isNotBlank() || selectedImages.isNotEmpty() || selectedVideo != null),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isPosting) "Publicando..." else "Publicar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isPosting
            ) {
                Text("Cancelar")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun PostCard(
    postResponse: PostResponse,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onAuthorClick: (String) -> Unit = {}
) {
    val post = postResponse.post
    val context = LocalContext.current
    var isSaved by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Screen dimensions for responsive content sizing
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    // Max media height: ~55% of screen to leave room for other post elements and nav
    val maxMediaHeightDp = (screenHeightDp * 0.55).roundToInt()

    val cardColor = if (post.backgroundColor != null) {
        try {
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(post.backgroundColor))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.surface
        }
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor)
    ) {
        // Horizontal divider at top of each post
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 0.5.dp
        )

        // Author row with horizontal padding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Author avatar - clickable
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onAuthorClick(postResponse.authorUsername) },
                contentAlignment = Alignment.Center
            ) {
                if (!postResponse.authorProfilePhoto.isNullOrBlank()) {
                    AsyncImage(
                        model = AusoApiClient.fullUrl(postResponse.authorProfilePhoto),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = postResponse.authorDisplayName.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Author name and username - clickable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAuthorClick(postResponse.authorUsername) }
            ) {
                Text(
                    text = postResponse.authorDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (post.backgroundColor != null)
                        androidx.compose.ui.graphics.Color.White
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "@${postResponse.authorUsername}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (post.backgroundColor != null)
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3-dot menu
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Mas opciones",
                        tint = if (post.backgroundColor != null)
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("No me interesa") },
                        onClick = {
                            showMoreMenu = false
                            // TODO: Implement "not interested" logic
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Block,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Reportar") },
                        onClick = {
                            showMoreMenu = false
                            // TODO: Implement report logic
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // Post content with horizontal padding for text
        if (post.content.isNotBlank()) {
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (post.backgroundColor != null)
                    androidx.compose.ui.graphics.Color.White
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Post images - FULL WIDTH, calculated max height based on screen
        if (postResponse.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val images = postResponse.images
            when {
                images.size == 1 -> {
                    // Single image - full width, responsive max height
                    AsyncImage(
                        model = AusoApiClient.fullUrl(images[0].imageUrl),
                        contentDescription = "Imagen del post",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxMediaHeightDp.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                images.size == 2 -> {
                    // Two images side by side, full width
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        images.forEach { img ->
                            AsyncImage(
                                model = AusoApiClient.fullUrl(img.imageUrl),
                                contentDescription = "Imagen del post",
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(max = (maxMediaHeightDp * 0.6).roundToInt().dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                else -> {
                    // 3+ images - grid-like layout
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // First row: up to 2 images
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            images.take(2).forEach { img ->
                                AsyncImage(
                                    model = AusoApiClient.fullUrl(img.imageUrl),
                                    contentDescription = "Imagen del post",
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = (maxMediaHeightDp * 0.5).roundToInt().dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        // Second row: remaining images
                        if (images.size > 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                images.drop(2).take(2).forEach { img ->
                                    Box(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        AsyncImage(
                                            model = AusoApiClient.fullUrl(img.imageUrl),
                                            contentDescription = "Imagen del post",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = (maxMediaHeightDp * 0.5).roundToInt().dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Show "+N more" overlay on last visible image
                                        val remaining = images.size - 4
                                        if (img == images.last() && remaining > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height((maxMediaHeightDp * 0.5).roundToInt().dp)
                                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "+$remaining mas",
                                                    color = androidx.compose.ui.graphics.Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Video player - uses aspect ratio from video data for proper sizing
        if (post.postType == "video" && postResponse.video != null) {
            VideoPlayer(
                videoUrl = AusoApiClient.fullUrl(postResponse.video.hlsMasterPlaylistUrl) ?: "",
                thumbnailUrl = AusoApiClient.fullUrl(postResponse.video.thumbnailUrl),
                duration = postResponse.video.duration,
                videoWidth = postResponse.video.width,
                videoHeight = postResponse.video.height,
                maxMediaHeightDp = maxMediaHeightDp
            )
        }

        // Poll
        if (post.postType == "poll" && postResponse.poll != null) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = postResponse.poll.poll.question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                postResponse.poll.options.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                if (postResponse.poll.totalVotes > 0)
                                    opt.votesCount.toFloat() / postResponse.poll.totalVotes.toFloat()
                                else 0f
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${opt.option.optionText} (${opt.votesCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }

        // Actions row with horizontal padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Like button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                IconButton(onClick = onLikeClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (postResponse.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (postResponse.isLiked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = postResponse.likesCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Comment button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                IconButton(onClick = onCommentClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comentar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = postResponse.commentsCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Share button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                IconButton(
                    onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Publicado por @${postResponse.authorUsername} en AUSO\n\n${post.content}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir via"))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Save/Bookmark button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                IconButton(
                    onClick = { isSaved = !isSaved },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Guardar",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Video player composable using ExoPlayer (Media3) with HLS support.
 * Shows thumbnail initially, plays on tap.
 * Calculates proper height based on video aspect ratio to prevent overlapping.
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    thumbnailUrl: String? = null,
    duration: Double = 0.0,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    maxMediaHeightDp: Int = 400
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }

    // Calculate aspect ratio height based on video dimensions
    val configuration = LocalConfiguration.current
    val screenWidthPx = configuration.screenWidthDp
    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        16f / 9f // default to 16:9
    }
    // Calculate video height in dp based on screen width and aspect ratio
    val calculatedHeightDp = (screenWidthPx / aspectRatio).roundToInt()
    // Clamp to max media height
    val videoHeightDp = minOf(calculatedHeightDp, maxMediaHeightDp)

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
            volume = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(videoHeightDp.dp)
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Thumbnail + play button when not playing
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Tap to play/pause
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.play()
                        isPlaying = true
                    }
                }
        )

        // Duration label
        if (duration > 0) {
            val minutes = (duration / 60).toInt()
            val seconds = (duration % 60).toInt()
            Text(
                text = String.format("%d:%02d", minutes, seconds),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Mute/unmute button with proper icon
        IconButton(
            onClick = {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .size(32.dp)
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
