package com.auso.social.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.CreateImagePackPostRequest
import com.auso.social.network.model.CreateTextPostRequest
import com.auso.social.network.model.PostResponse
import com.auso.social.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Home screen - shows the post feed
 * FAB and create post dialog are handled by MainScreen
 */
@Composable
fun HomeScreen(
    tabName: String = "Amigos",
    authViewModel: AuthViewModel? = null,
    refreshTrigger: Int = 0,
    onAuthorClick: (String) -> Unit = {}
) {
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
    onRemoveImage: (Uri) -> Unit = {}
) {
    var content by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        title = {
            Text(
                text = "Crear publicacion",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = {
                        content = it
                        error = ""
                    },
                    placeholder = { Text("Que estas pensando?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Selected images thumbnails
                if (selectedImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                                // Remove button
                                IconButton(
                                    onClick = { onRemoveImage(uri) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Quitar imagen",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Add image button
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddImage,
                    enabled = !isPosting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (selectedImages.isEmpty()) "Agregar imagen" else "Agregar otra imagen"
                    )
                }

                if (error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                    if (content.isBlank() && selectedImages.isEmpty()) {
                        error = "Escribe algo o agrega una imagen"
                        return@TextButton
                    }
                    isPosting = true
                    coroutineScope.launch {
                        try {
                            val token = AusoApiClient.getToken()
                            if (token != null) {
                                val textContent = content.trim()

                                if (selectedImages.isEmpty()) {
                                    // Text-only post
                                    val request = CreateTextPostRequest(content = textContent)
                                    val response = AusoApiClient.api.createTextPost("Bearer $token", request)
                                    if (response.isSuccessful) {
                                        onPostCreated()
                                        onDismiss()
                                    } else {
                                        error = "Error al publicar"
                                    }
                                } else {
                                    // Post with images: create image-pack post, then upload images
                                    val request = CreateImagePackPostRequest(
                                        content = textContent.ifBlank { null },
                                        layoutMode = "carousel"
                                    )
                                    val postResponse = AusoApiClient.api.createImagePackPost("Bearer $token", request)
                                    if (postResponse.isSuccessful) {
                                        val postId = postResponse.body()?.id
                                        if (postId != null) {
                                            // Upload images to the post
                                            val fileParts = selectedImages.mapNotNull { uri ->
                                                AusoApiClient.uriToMultipartPart(context, uri)
                                            }
                                            if (fileParts.isNotEmpty()) {
                                                try {
                                                    AusoApiClient.api.addImagesToPost(
                                                        "Bearer $token",
                                                        postId,
                                                        fileParts
                                                    )
                                                } catch (_: Exception) {
                                                    // Images failed to upload but post was created
                                                }
                                            }
                                        }
                                        onPostCreated()
                                        onDismiss()
                                    } else {
                                        error = "Error al crear publicacion con imagenes"
                                    }
                                }
                            } else {
                                error = "No autenticado"
                            }
                        } catch (e: Exception) {
                            error = "Error de conexion: ${e.message}"
                        }
                        isPosting = false
                    }
                },
                enabled = !isPosting
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Publicar", color = MaterialTheme.colorScheme.primary)
                }
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

        // Post images - FULL WIDTH, no padding, no rounded corners
        if (postResponse.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val images = postResponse.images
            when {
                images.size == 1 -> {
                    // Single image - full width, no rounding
                    AsyncImage(
                        model = AusoApiClient.fullUrl(images[0].imageUrl),
                        contentDescription = "Imagen del post",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
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
                                    .heightIn(max = 220.dp),
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
                                        .heightIn(max = 180.dp),
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
                                                .heightIn(max = 180.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Show "+N more" overlay on last visible image
                                        val remaining = images.size - 4
                                        if (img == images.last() && remaining > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp)
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

        // Video player
        if (post.postType == "video" && postResponse.video != null) {
            VideoPlayer(
                videoUrl = AusoApiClient.fullUrl(postResponse.video.hlsMasterPlaylistUrl) ?: "",
                thumbnailUrl = AusoApiClient.fullUrl(postResponse.video.thumbnailUrl),
                duration = postResponse.video.duration
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
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    thumbnailUrl: String? = null,
    duration: Double = 0.0
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }

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
            .heightIn(max = 350.dp)
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Thumbnail + play button when not playing
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f)),
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
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u25B6",
                        fontSize = 28.sp,
                        color = androidx.compose.ui.graphics.Color.Black
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

        // Mute/unmute button
        IconButton(
            onClick = {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .size(32.dp)
        ) {
            Text(
                text = if (isMuted) "Mute" else "Sound",
                fontSize = 11.sp,
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}
