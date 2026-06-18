package com.auso.social.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    onScrollDirection: (Int) -> Unit = {},
    currentlyPlayingVideoId: String? = null,
    onVideoPlayChanged: (String?) -> Unit = {},
    isGlobalMuted: Boolean = false,
    onMuteChanged: (Boolean) -> Unit = {},
    topBarHeightDp: androidx.compose.ui.unit.Dp = 112.dp,
    isTopBarVisible: Boolean = true
) {
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Video detail overlay state
    var videoDetailPost by remember { mutableStateOf<PostResponse?>(null) }

    // Track scroll direction
    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        var accumulatedDelta = 0f

        snapshotFlow {
            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            val delta = if (index != prevIndex) {
                if (index > prevIndex) -300f else 300f
            } else {
                (prevOffset - offset).toFloat()
            }
            accumulatedDelta += delta
            when {
                accumulatedDelta < -30 -> {
                    onScrollDirection(-1)
                    accumulatedDelta = 0f
                }
                accumulatedDelta > 30 -> {
                    onScrollDirection(1)
                    accumulatedDelta = 0f
                }
            }
            prevIndex = index
            prevOffset = offset
        }
    }

    // Auto-play: detect which video post is most visible (closest to viewport center)
    LaunchedEffect(Unit) {
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportCenter = listState.layoutInfo.viewportStartOffset +
                (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset) / 2

            var bestVideoId: String? = null
            var bestDistance = Int.MAX_VALUE

            for (itemInfo in visibleItems) {
                val index = itemInfo.index
                if (index < 0 || index >= posts.size) continue
                val post = posts[index]
                if (post.post.postType != "video" || post.video == null) continue

                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val distance = kotlin.math.abs(itemCenter - viewportCenter)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestVideoId = post.post.id
                }
            }
            bestVideoId
        }.collect { bestVideoId ->
            if (bestVideoId != null && bestVideoId != currentlyPlayingVideoId) {
                onVideoPlayChanged(bestVideoId)
            } else if (bestVideoId == null && currentlyPlayingVideoId != null) {
                onVideoPlayChanged(null)
            }
        }
    }

    // Load feed
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

    // Video detail overlay — shown on top of everything
    if (videoDetailPost != null) {
        VideoDetailOverlay(
            postResponse = videoDetailPost!!,
            isMuted = isGlobalMuted,
            onMuteChanged = onMuteChanged,
            onBack = { videoDetailPost = null },
            onAuthorClick = onAuthorClick
        )
        return
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
                Text(text = "\uD83C\uDFE0", style = MaterialTheme.typography.displayLarge)
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
                    top = topBarHeightDp + 8.dp,
                    bottom = 88.dp
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
                        onAuthorClick = onAuthorClick,
                        isCurrentlyPlaying = currentlyPlayingVideoId == postResponse.post.id,
                        onVideoPlayRequest = { videoId ->
                            onVideoPlayChanged(videoId)
                        },
                        isGlobalMuted = isGlobalMuted,
                        onMuteChanged = onMuteChanged,
                        onVideoClick = {
                            // Open video detail overlay
                            videoDetailPost = postResponse
                        }
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
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Que estas pensando?") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    enabled = !isPosting
                )

                if (!isPosting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddImage,
                            modifier = Modifier.weight(1f),
                            enabled = selectedVideo == null
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Imagen", fontSize = 12.sp)
                        }
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

                if (selectedImages.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                            .background(MaterialTheme.colorScheme.error, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedVideo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Video seleccionado", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(selectedVideo.lastPathSegment ?: "Video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!isPosting) {
                            IconButton(onClick = onRemoveVideo) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar video", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (isPosting) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(uploadProgress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isPosting) return@TextButton
                    when {
                        selectedVideo != null -> {
                            isPosting = true; error = ""
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token == null) { error = "No autenticado"; isPosting = false; return@launch }
                                    uploadProgress = "Subiendo video..."
                                    val inputStream = context.contentResolver.openInputStream(selectedVideo)
                                    if (inputStream == null) { error = "No se pudo abrir el video"; isPosting = false; return@launch }
                                    val bytes = inputStream.readBytes(); inputStream.close()
                                    val videoPart = okhttp3.MultipartBody.Part.createFormData("file", "video.mp4", okhttp3.RequestBody.create("video/*".toMediaType(), bytes))
                                    val response = AusoApiClient.api.createVideoPost("Bearer $token", videoPart)
                                    if (response.isSuccessful) { uploadProgress = "Publicado!"; onPostCreated(); onDismiss() }
                                    else { error = "Error al publicar video: ${response.code()}" }
                                } catch (e: Exception) { error = "Error: ${e.message}" }
                                finally { isPosting = false }
                            }
                        }
                        selectedImages.isNotEmpty() -> {
                            isPosting = true; error = ""
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token == null) { error = "No autenticado"; isPosting = false; return@launch }
                                    uploadProgress = "Subiendo imagenes..."
                                    val imageParts = selectedImages.mapIndexed { index, uri ->
                                        val inputStream = context.contentResolver.openInputStream(uri)!!
                                        val bytes = inputStream.readBytes(); inputStream.close()
                                        okhttp3.MultipartBody.Part.createFormData("files", "image_$index.jpg", okhttp3.RequestBody.create("image/*".toMediaType(), bytes))
                                    }
                                    val response = AusoApiClient.api.createImagePost("Bearer $token", imageParts)
                                    if (response.isSuccessful) { onPostCreated(); onDismiss() }
                                    else { error = "Error al publicar: ${response.code()}" }
                                } catch (e: Exception) { error = "Error: ${e.message}" }
                                finally { isPosting = false }
                            }
                        }
                        content.isNotBlank() -> {
                            isPosting = true; error = ""
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token == null) { error = "No autenticado"; isPosting = false; return@launch }
                                    val request = CreateTextPostRequest(content = content)
                                    val response = AusoApiClient.api.createTextPost("Bearer $token", request)
                                    if (response.isSuccessful) { onPostCreated(); onDismiss() }
                                    else { error = "Error al publicar: ${response.code()}" }
                                } catch (e: Exception) { error = "Error: ${e.message}" }
                                finally { isPosting = false }
                            }
                        }
                    }
                },
                enabled = !isPosting && (content.isNotBlank() || selectedImages.isNotEmpty() || selectedVideo != null),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text(if (isPosting) "Publicando..." else "Publicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isPosting) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    postResponse: PostResponse,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    isCurrentlyPlaying: Boolean = false,
    onVideoPlayRequest: (String?) -> Unit = {},
    isGlobalMuted: Boolean = false,
    onMuteChanged: (Boolean) -> Unit = {},
    onVideoClick: () -> Unit = {}
) {
    val post = postResponse.post
    val context = LocalContext.current
    var isSaved by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    // Double-tap like animation
    var showLikeAnimation by remember { mutableStateOf(false) }
    val likeScale = remember { Animatable(0f) }

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val maxMediaHeightDp = (screenHeightDp * 0.80).roundToInt()

    val cardColor = if (post.backgroundColor != null) {
        try { Color(android.graphics.Color.parseColor(post.backgroundColor)) }
        catch (e: Exception) { MaterialTheme.colorScheme.surface }
    } else MaterialTheme.colorScheme.surface

    val onCardColor = if (post.backgroundColor != null) Color.White else MaterialTheme.colorScheme.onSurface
    val onCardColorVariant = if (post.backgroundColor != null) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = {
                    onLikeClick()
                    showLikeAnimation = true
                }
            )
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

        // Author row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable { onAuthorClick(postResponse.authorUsername) },
                contentAlignment = Alignment.Center
            ) {
                if (!postResponse.authorProfilePhoto.isNullOrBlank()) {
                    AsyncImage(model = AusoApiClient.fullUrl(postResponse.authorProfilePhoto), contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(text = postResponse.authorDisplayName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).clickable { onAuthorClick(postResponse.authorUsername) }) {
                Text(text = postResponse.authorDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onCardColor)
                Text(text = "@${postResponse.authorUsername}", style = MaterialTheme.typography.bodySmall, color = onCardColorVariant)
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Mas opciones", tint = onCardColorVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(text = { Text("No me interesa") }, onClick = { showMoreMenu = false }, leadingIcon = { Icon(Icons.Outlined.Block, null, modifier = Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text("Reportar") }, onClick = { showMoreMenu = false }, leadingIcon = { Icon(Icons.Outlined.Flag, null, modifier = Modifier.size(18.dp)) })
                }
            }
        }

        // Post content
        if (post.content.isNotBlank()) {
            Text(text = post.content, style = MaterialTheme.typography.bodyLarge, color = onCardColor, maxLines = 10, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Post images
        if (postResponse.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val images = postResponse.images
            when {
                images.size == 1 -> {
                    AsyncImage(model = AusoApiClient.fullUrl(images[0].imageUrl), contentDescription = "Imagen del post", modifier = Modifier.fillMaxWidth().heightIn(max = maxMediaHeightDp.dp), contentScale = ContentScale.Fit)
                }
                images.size == 2 -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        images.forEach { img ->
                            AsyncImage(model = AusoApiClient.fullUrl(img.imageUrl), contentDescription = "Imagen del post", modifier = Modifier.weight(1f).heightIn(max = (maxMediaHeightDp * 0.6).roundToInt().dp), contentScale = ContentScale.Crop)
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            images.take(2).forEach { img ->
                                AsyncImage(model = AusoApiClient.fullUrl(img.imageUrl), contentDescription = "Imagen del post", modifier = Modifier.weight(1f).heightIn(max = (maxMediaHeightDp * 0.5).roundToInt().dp), contentScale = ContentScale.Crop)
                            }
                        }
                        if (images.size > 2) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                images.drop(2).take(2).forEach { img ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        AsyncImage(model = AusoApiClient.fullUrl(img.imageUrl), contentDescription = "Imagen del post", modifier = Modifier.fillMaxWidth().heightIn(max = (maxMediaHeightDp * 0.5).roundToInt().dp), contentScale = ContentScale.Crop)
                                        val remaining = images.size - 4
                                        if (img == images.last() && remaining > 0) {
                                            Box(modifier = Modifier.fillMaxWidth().height((maxMediaHeightDp * 0.5).roundToInt().dp).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                                Text("+$remaining mas", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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

        // Video player — simplified: tap to open detail overlay
        if (post.postType == "video" && postResponse.video != null) {
            VideoPlayerFeed(
                postId = post.id,
                videoUrl = AusoApiClient.fullUrl(postResponse.video.hlsMasterPlaylistUrl) ?: "",
                thumbnailUrl = AusoApiClient.fullUrl(postResponse.video.thumbnailUrl),
                duration = postResponse.video.duration,
                videoWidth = postResponse.video.width,
                videoHeight = postResponse.video.height,
                maxMediaHeightDp = maxMediaHeightDp,
                isAutoPlay = isCurrentlyPlaying,
                onPlayChanged = { playing -> onVideoPlayRequest(if (playing) post.id else null) },
                isMuted = isGlobalMuted,
                onMuteChanged = onMuteChanged,
                onClick = onVideoClick
            )
        }

        // Actions row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
                IconButton(onClick = onLikeClick, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = if (postResponse.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like", tint = if (postResponse.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Text(text = postResponse.likesCount.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
                IconButton(onClick = onCommentClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comentar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Text(text = postResponse.commentsCount.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
                IconButton(onClick = {
                    val shareIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "Publicado por @${postResponse.authorUsername} en AUSO\n\n${post.content}"); type = "text/plain" }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir via"))
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
                IconButton(onClick = { isSaved = !isSaved }, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "Guardar", tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Double-tap like heart animation overlay
        if (showLikeAnimation) {
            LaunchedEffect(showLikeAnimation) {
                likeScale.snapTo(0f)
                likeScale.animateTo(1.2f)
                likeScale.animateTo(0f)
                showLikeAnimation = false
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(80.dp).scale(likeScale.value)
                )
            }
        }
    }
}

/**
 * Minimal video player for the feed — only thumbnail + play + timeline at bottom + mute
 * Single tap = open detail overlay; controls show play/pause, mute, and seekbar
 */
@Composable
fun VideoPlayerFeed(
    postId: String,
    videoUrl: String,
    thumbnailUrl: String? = null,
    duration: Double = 0.0,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    maxMediaHeightDp: Int = 400,
    isAutoPlay: Boolean = false,
    onPlayChanged: (Boolean) -> Unit = {},
    isMuted: Boolean = false,
    onMuteChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }

    val configuration = LocalConfiguration.current
    val screenWidthPx = configuration.screenWidthDp
    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else 16f / 9f
    val calculatedHeightDp = (screenWidthPx / aspectRatio).roundToInt()
    val videoHeightDp = minOf(calculatedHeightDp, maxMediaHeightDp)

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = false
            volume = if (isMuted) 0f else 1f
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) { isPlaying = false; onPlayChanged(false) }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (playing) totalDuration = exoPlayer.duration.coerceAtLeast(0L) }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (totalDuration <= 0L) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(200)
        }
    }

    // Auto-hide controls after 3s
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) { kotlinx.coroutines.delay(3000); showControls = false }
    }

    LaunchedEffect(isAutoPlay) {
        if (isAutoPlay) { exoPlayer.playWhenReady = true; isPlaying = true; showControls = false }
        else { exoPlayer.playWhenReady = false; isPlaying = false }
    }

    LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(modifier = Modifier.fillMaxWidth().height(videoHeightDp.dp)) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // Thumbnail when not playing
        if (!isPlaying && !isAutoPlay) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(model = thumbnailUrl, contentDescription = "Thumbnail", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = Color.Black, modifier = Modifier.size(32.dp))
                }
                if (duration > 0) {
                    val minutes = (duration / 60).toInt(); val seconds = (duration % 60).toInt()
                    Text(text = String.format("%d:%02d", minutes, seconds), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }

        // Tap to toggle controls
        if (isPlaying || isAutoPlay) {
            Box(modifier = Modifier.fillMaxSize().clickable { showControls = !showControls })
        }

        // Minimal controls: seekbar + play/pause + mute at bottom edge
        AnimatedVisibility(
            visible = showControls && (isPlaying || isAutoPlay),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause
                IconButton(onClick = {
                    if (exoPlayer.isPlaying) { exoPlayer.pause(); isPlaying = false; onPlayChanged(false) }
                    else { exoPlayer.play(); isPlaying = true; onPlayChanged(true) }
                }, modifier = Modifier.size(28.dp)) {
                    Icon(if (exoPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                // Seekbar
                val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                Slider(
                    value = progress,
                    onValueChange = { fraction -> val seekPosition = (fraction * totalDuration).toLong(); exoPlayer.seekTo(seekPosition); currentPosition = seekPosition },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                )
                // Mute
                IconButton(onClick = { onMuteChanged(!isMuted) }, modifier = Modifier.size(28.dp)) {
                    Icon(if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Full-screen video detail overlay (YouTube-style)
 * Video at top, controls/details/likes in middle, comments at bottom
 */
@Composable
fun VideoDetailOverlay(
    postResponse: PostResponse,
    isMuted: Boolean = false,
    onMuteChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {}
) {
    val post = postResponse.post
    val context = LocalContext.current
    var isSaved by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val videoUrl = AusoApiClient.fullUrl(postResponse.video?.hlsMasterPlaylistUrl) ?: ""
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
            volume = if (isMuted) 0f else 1f
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) { isPlaying = false }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (playing) totalDuration = exoPlayer.duration.coerceAtLeast(0L) }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (totalDuration <= 0L) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(200)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) { kotlinx.coroutines.delay(4000); showControls = false }
    }

    LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }

    // Full screen overlay
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Video area
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )

            // Tap to toggle controls
            Box(modifier = Modifier.fillMaxSize().clickable { showControls = !showControls })

            // Back button always visible top-start
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(36.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            // Controls overlay
            androidx.compose.animation.AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause
                    IconButton(onClick = {
                        if (exoPlayer.isPlaying) { exoPlayer.pause(); isPlaying = false }
                        else { exoPlayer.play(); isPlaying = true }
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(if (exoPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    // Time elapsed
                    val elapsedSec = (currentPosition / 1000).toInt()
                    val totalSec = (totalDuration / 1000).toInt()
                    Text(
                        text = String.format("%d:%02d / %d:%02d", elapsedSec / 60, elapsedSec % 60, totalSec / 60, totalSec % 60),
                        color = Color.White, style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Seekbar
                    val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = progress,
                        onValueChange = { fraction -> val seekPosition = (fraction * totalDuration).toLong(); exoPlayer.seekTo(seekPosition); currentPosition = seekPosition },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    )
                    // Mute
                    IconButton(onClick = { onMuteChanged(!isMuted) }, modifier = Modifier.size(28.dp)) {
                        Icon(if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Scrollable content below video: details + likes + comments
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Author row
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .clickable { onAuthorClick(postResponse.authorUsername) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!postResponse.authorProfilePhoto.isNullOrBlank()) {
                            AsyncImage(model = AusoApiClient.fullUrl(postResponse.authorProfilePhoto), contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(text = postResponse.authorDisplayName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = postResponse.authorDisplayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(text = "@${postResponse.authorUsername}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { /* TODO: Follow */ }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("Seguir", fontSize = 13.sp)
                    }
                }
            }

            // Post content
            if (post.content.isNotBlank()) {
                item {
                    Text(text = post.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
            }

            // Actions row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (postResponse.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (postResponse.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${postResponse.likesCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${postResponse.commentsCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    IconButton(onClick = {
                        val shareIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "Publicado por @${postResponse.authorUsername} en AUSO\n\n${post.content}"); type = "text/plain" }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir via"))
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { isSaved = !isSaved }, modifier = Modifier.size(36.dp)) {
                        Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null, tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }

            // Comments placeholder
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text("Comentarios", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    // Comment input
                    var commentText by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { Text("Escribe un comentario...") },
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { /* TODO: Post comment */ commentText = "" }, modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
                            Icon(Icons.Filled.Send, contentDescription = "Enviar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("No hay comentarios aun", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).wrapContentWidth(Alignment.CenterHorizontally))
                }
            }
        }
    }
}
