package com.auso.social.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.auso.social.network.AusoApiClient
import com.auso.social.ui.components.MagicProgressBar
import com.auso.social.network.model.CreateTextPostRequest
import com.auso.social.network.model.CreateImpressionRequest
import com.auso.social.network.model.PostResponse
import com.auso.social.network.model.UserProfile
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
    isTopBarVisible: Boolean = true,
    onVideoOverlayChanged: (Boolean) -> Unit = {},
    onHashtagClick: (String) -> Unit = {}
) {
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Current logged-in user id — used to detect own posts and hide the Follow button
    val currentUserId by (authViewModel?.currentUser ?: kotlinx.coroutines.flow.MutableStateFlow<UserProfile?>(null))
        .collectAsState()
    val currentUserIdValue = currentUserId?.id

    // Video detail overlay state
    var videoDetailPost by remember { mutableStateOf<PostResponse?>(null) }

    // Map of postId -> ExoPlayer: track each feed video's player by post ID
    // so we share the CORRECT player with the overlay when a specific video is tapped
    val videoPlayerMap = remember { mutableStateMapOf<String, androidx.media3.exoplayer.ExoPlayer>() }

    // Map of postId -> playback position (ms): lets the feed resume the video from
    // the exact point where the overlay left off, and vice versa.
    val videoResumePositions = remember { mutableStateMapOf<String, Long>() }

    // Comments bottom sheet — set to a postId to open it
    var commentsPostId by remember { mutableStateOf<String?>(null) }

    // Skip detection: track which posts the user has "seen" (visible ≥2s) without
    // any positive interaction. When such a post scrolls out of view, we fire a
    // "skip" impression (negative signal for the recommender).
    val seenPostTimestamps = remember { mutableStateMapOf<String, Long>() }
    val interactedPosts = remember { mutableStateMapOf<String, Boolean>() }

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

    // Skip detection: when the list settles, mark the currently visible posts as "seen"
    // (timestamp them). When a post that was seen for ≥2s scrolls out of view AND the user
    // never interacted with it (no like/comment/share/watch), fire a "skip" impression.
    LaunchedEffect(listState) {
        snapshotFlow {
            // Use a stable set of visible post IDs + the current post list
            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size)
        }.collect { (startIdx, endIdx) ->
            val visibleIds = posts.subList(
                startIdx.coerceIn(0, posts.size),
                endIdx.coerceIn(0, posts.size)
            ).map { it.post.id }

            // Mark newly-visible posts as seen (record timestamp)
            for (id in visibleIds) {
                if (!seenPostTimestamps.containsKey(id)) {
                    seenPostTimestamps[id] = System.currentTimeMillis()
                }
            }

            // Detect posts that left the viewport — fire skip if seen ≥2s and not interacted
            val leftViewport = seenPostTimestamps.keys - visibleIds
            for (id in leftViewport) {
                val seenAt = seenPostTimestamps.remove(id) ?: continue
                val dwellMs = System.currentTimeMillis() - seenAt
                val interacted = interactedPosts.remove(id) == true
                if (!interacted && dwellMs >= 2000L) {
                    // Fire skip impression in the background (negative signal for the algo)
                    coroutineScope.launch {
                        try {
                            val token = AusoApiClient.getToken() ?: return@launch
                            AusoApiClient.api.recordImpression(
                                "Bearer $token", id,
                                CreateImpressionRequest(impressionType = "skip")
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    // Auto-play: detect which video post is most visible (closest to viewport center)
    // Includes a 800ms debounce so videos don't start loading during fast scrolling
    LaunchedEffect(Unit) {
        var lastDetectedId: String? = null
        var debounceJob: kotlinx.coroutines.Job? = null

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
            // Include overlay state so changes trigger re-evaluation
            Pair(bestVideoId, videoDetailPost)
        }.collect { (bestVideoId, overlayPost) ->
            // Cancel previous debounce
            debounceJob?.cancel()

            if (overlayPost != null) {
                // Overlay is open — pause all feed videos
                if (currentlyPlayingVideoId != null) {
                    onVideoPlayChanged(null)
                }
                return@collect
            }

            if (bestVideoId != null && bestVideoId != currentlyPlayingVideoId) {
                // Debounce: wait 800ms before actually starting playback
                // This prevents loading videos during fast scrolling
                val targetId = bestVideoId
                debounceJob = coroutineScope.launch {
                    kotlinx.coroutines.delay(800)
                    // Only play if the target hasn't changed during the delay
                    if (targetId == bestVideoId && videoDetailPost == null) {
                        onVideoPlayChanged(targetId)
                    }
                }
            } else if (bestVideoId == null && currentlyPlayingVideoId != null) {
                onVideoPlayChanged(null)
            }
        }
    }

    // Load feed — uses the recommendation algorithm for "Recomendado", the standard
    // chronological followees+self feed for "Amigos", and the global feed for "Explorar".
    LaunchedEffect(tabName, refreshTrigger) {
        isLoading = true
        try {
            val token = AusoApiClient.getToken()
            if (token != null) {
                val response = when (tabName) {
                    "Recomendado" -> AusoApiClient.api.getRecommendedFeed("Bearer $token")
                    "Explorar" -> AusoApiClient.api.getFeed("Bearer $token", postType = null)
                    else -> AusoApiClient.api.getFeed("Bearer $token")
                }
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

    // Video detail overlay — drawn on top of feed content (not replacing it)
    Box(modifier = Modifier.fillMaxSize()) {
        // Main feed content
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
                            // Mark as interacted (prevents a skip impression)
                            interactedPosts[postResponse.post.id] = true
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
                                            // Train the recommender: a like is a strong positive signal
                                            try {
                                                AusoApiClient.api.recordImpression(
                                                    "Bearer $token",
                                                    postResponse.post.id,
                                                    CreateImpressionRequest(impressionType = "like")
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        onCommentClick = {
                            // Mark as interacted (prevents a skip impression) and open the sheet
                            interactedPosts[postResponse.post.id] = true
                            commentsPostId = postResponse.post.id
                        },
                        onAuthorClick = onAuthorClick,
                        onHashtagClick = onHashtagClick,
                        onShareClick = {
                            // Mark as interacted (prevents a skip impression)
                            interactedPosts[postResponse.post.id] = true
                            // Train the recommender: a share is the strongest positive signal
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token != null) {
                                        AusoApiClient.api.recordImpression(
                                            "Bearer $token",
                                            postResponse.post.id,
                                            CreateImpressionRequest(impressionType = "share")
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        onWatched = {
                            // Mark as interacted (prevents a skip impression)
                            interactedPosts[postResponse.post.id] = true
                            // Train the recommender: a 3s+ view is a mild positive signal
                            coroutineScope.launch {
                                try {
                                    val token = AusoApiClient.getToken()
                                    if (token != null) {
                                        AusoApiClient.api.recordImpression(
                                            "Bearer $token",
                                            postResponse.post.id,
                                            CreateImpressionRequest(impressionType = "view")
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        isCurrentlyPlaying = currentlyPlayingVideoId == postResponse.post.id,
                        onVideoPlayRequest = { videoId ->
                            onVideoPlayChanged(videoId)
                        },
                        isGlobalMuted = isGlobalMuted,
                        onMuteChanged = onMuteChanged,
                        onVideoClick = {
                            // Open video detail overlay — pause feed but keep player alive
                            onVideoPlayChanged(null)
                            videoDetailPost = postResponse
                        },
                        onVideoPlayerRef = { player ->
                            if (player != null) {
                                videoPlayerMap[postResponse.post.id] = player
                            } else {
                                videoPlayerMap.remove(postResponse.post.id)
                            }
                        },
                        isVideoOverlayShowing = videoDetailPost?.post?.id == postResponse.post.id,
                        currentUserId = currentUserIdValue,
                        videoResumePosition = videoResumePositions[postResponse.post.id] ?: 0L
                    )
                }
            }
        }

        // Video detail overlay — on top of everything, so main content stays behind
        if (videoDetailPost != null) {
            LaunchedEffect(Unit) { onVideoOverlayChanged(true) }
            val overlayPostId = videoDetailPost!!.post.id
            VideoDetailOverlay(
                postResponse = videoDetailPost!!,
                isMuted = isGlobalMuted,
                onMuteChanged = onMuteChanged,
                onBack = { videoDetailPost = null; onVideoOverlayChanged(false) },
                onAuthorClick = onAuthorClick,
                sharedPlayer = videoPlayerMap[overlayPostId],
                currentUserId = currentUserIdValue,
                // When the overlay closes, save the final position so the feed can resume there.
                // Also restore playback in the feed (so the video keeps playing where it left off).
                onClosePosition = { finalPos ->
                    videoResumePositions[overlayPostId] = finalPos
                    onVideoPlayChanged(overlayPostId)
                }
            )
        }

        // Comments bottom sheet — opened when the user taps the comment action
        if (commentsPostId != null) {
            val pid = commentsPostId!!
            CommentsBottomSheet(
                postId = pid,
                onDismiss = { commentsPostId = null },
                onAuthorClick = onAuthorClick,
                onCommentPosted = {
                    // Train the recommender: a comment is a strong positive signal
                    coroutineScope.launch {
                        try {
                            val token = AusoApiClient.getToken() ?: return@launch
                            AusoApiClient.api.recordImpression(
                                "Bearer $token", pid,
                                CreateImpressionRequest(impressionType = "comment")
                            )
                        } catch (_: Exception) {}
                    }
                }
            )
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
    // Post type selector: null = picker visible; otherwise the chosen type
    var postType by remember { mutableStateOf<String?>(null) }

    var content by remember { mutableStateOf("") }
    var videoTitle by remember { mutableStateOf("") }
    var videoDescription by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) } // 0f..1f real progress (upload phase)
    var uploadProgressText by remember { mutableStateOf("") }
    var processingProgress by remember { mutableStateOf(-1f) } // -1 = idle; 0..1 = converting
    var processingElapsed by remember { mutableStateOf(0) } // seconds elapsed in conversion
    var error by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Full-screen dialog — different layouts for picker vs form
    Dialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isPosting,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ════════ Top app bar (always visible) ════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (!isPosting) onDismiss() }) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (postType == null) "Crear publicacion" else when (postType) {
                            "text" -> "Publicacion de texto"
                            "image" -> "Publicacion de imagen"
                            "video" -> "Publicacion de video"
                            "poll" -> "Encuesta"
                            else -> "Crear publicacion"
                        },
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Publish button (only when a type is selected)
                    if (postType != null) {
                        TextButton(
                            onClick = {
                                if (isPosting) return@TextButton
                                when (postType) {
                                    "video" -> {
                                        if (selectedVideo == null) { error = "Selecciona un video"; return@TextButton }
                                        isPosting = true; error = ""
                                        coroutineScope.launch {
                                            try {
                                                val token = AusoApiClient.getToken()
                                                if (token == null) { error = "No autenticado"; isPosting = false; return@launch }
                                                uploadProgress = 0f
                                                uploadProgressText = "Preparando video..."
                                                val inputStream = context.contentResolver.openInputStream(selectedVideo)
                                                if (inputStream == null) { error = "No se pudo abrir el video"; isPosting = false; return@launch }
                                                val bytes = inputStream.readBytes(); inputStream.close()
                                                val videoPart = okhttp3.MultipartBody.Part.createFormData(
                                                    "file",
                                                    "video.mp4",
                                                    com.auso.social.network.ProgressRequestBody(
                                                        bytes,
                                                        "video/*".toMediaType(),
                                                        onProgress = { pct ->
                                                            uploadProgress = pct
                                                            val totalKb = bytes.size / 1024
                                                            val sentKb = (pct * totalKb).toInt()
                                                            uploadProgressText = "Subiendo video... $sentKb / $totalKb KB (${(pct * 100).toInt()}%)"
                                                        }
                                                    )
                                                )
                                                val parts = mutableListOf(videoPart)
                                                if (videoTitle.isNotBlank()) {
                                                    parts.add(
                                                        okhttp3.MultipartBody.Part.createFormData(
                                                            "title", videoTitle,
                                                            okhttp3.RequestBody.create("text/plain".toMediaType(), videoTitle)
                                                        )
                                                    )
                                                }
                                                if (videoDescription.isNotBlank()) {
                                                    parts.add(
                                                        okhttp3.MultipartBody.Part.createFormData(
                                                            "description", videoDescription,
                                                            okhttp3.RequestBody.create("text/plain".toMediaType(), videoDescription)
                                                        )
                                                    )
                                                }
                                                uploadProgressText = "Procesando en el servidor..."
                                                processingProgress = 0f
                                                val response = AusoApiClient.api.createVideoPost("Bearer $token", parts)
                                                if (!response.isSuccessful) {
                                                    error = "Error al publicar video: ${response.code()}"
                                                    isPosting = false
                                                    processingProgress = -1f
                                                    return@launch
                                                }
                                                // After upload, poll getPost until the video is fully processed
                                                // (post_videos row exists with a non-empty hls_master_playlist_url).
                                                val postId = response.body()?.post?.id
                                                if (postId.isNullOrBlank()) {
                                                    error = "Respuesta inválida del servidor"
                                                    isPosting = false
                                                    processingProgress = -1f
                                                    return@launch
                                                }
                                                uploadProgress = 1f
                                                uploadProgressText = ""
                                                val startTime = System.currentTimeMillis()
                                                var processed = false
                                                while (!processed) {
                                                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                                                    processingElapsed = elapsedSec
                                                    processingProgress = (elapsedSec / 60f).coerceIn(0f, 0.99f)
                                                    kotlinx.coroutines.delay(2000)
                                                    try {
                                                        val pollResp = AusoApiClient.api.getPost("Bearer $token", postId)
                                                        if (pollResp.isSuccessful) {
                                                            val video = pollResp.body()?.video
                                                            if (video != null && video.hlsMasterPlaylistUrl.isNotBlank()) {
                                                                processed = true
                                                            }
                                                        }
                                                    } catch (_: Exception) { /* retry next iteration */ }
                                                    // Safety timeout: 5 minutes
                                                    if (elapsedSec > 300) {
                                                        error = "El video está tardando demasiado en procesarse. Cierra y revisa más tarde."
                                                        isPosting = false
                                                        processingProgress = -1f
                                                        return@launch
                                                    }
                                                }
                                                processingProgress = 1f
                                                processingElapsed = 0
                                                onPostCreated(); onDismiss()
                                            } catch (e: Exception) { error = "Error: ${e.message}" }
                                            finally {
                                                isPosting = false
                                                processingProgress = -1f
                                            }
                                        }
                                    }
                                    "image" -> {
                                        if (selectedImages.isEmpty()) { error = "Selecciona al menos una imagen"; return@TextButton }
                                        isPosting = true; error = ""
                                        coroutineScope.launch {
                                            try {
                                                val token = AusoApiClient.getToken()
                                                if (token == null) { error = "No autenticado"; isPosting = false; return@launch }
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
                                    "text" -> {
                                        if (content.isBlank()) { error = "Escribe algo"; return@TextButton }
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
                            enabled = !isPosting && when (postType) {
                                "text" -> content.isNotBlank()
                                "image" -> selectedImages.isNotEmpty()
                                "video" -> selectedVideo != null
                                else -> false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) { Text(if (isPosting) "..." else "Publicar") }
                    } else {
                        Spacer(modifier = Modifier.width(72.dp)) // balance the Cancelar button
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // ════════ Body ════════
                if (postType == null) {
                    // ─── Picker ───
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Selecciona el tipo de publicacion",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PostTypeChip(
                                label = "Texto",
                                icon = Icons.Default.TextFields,
                                modifier = Modifier.weight(1f),
                                onClick = { postType = "text" }
                            )
                            PostTypeChip(
                                label = "Imagen",
                                icon = Icons.Default.Image,
                                modifier = Modifier.weight(1f),
                                onClick = { postType = "image" }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PostTypeChip(
                                label = "Video",
                                icon = Icons.Default.Movie,
                                modifier = Modifier.weight(1f),
                                onClick = { postType = "video" }
                            )
                            PostTypeChip(
                                label = "Encuesta",
                                icon = Icons.Default.Poll,
                                modifier = Modifier.weight(1f),
                                enabled = false, // TODO: implement poll creation
                                onClick = { postType = "poll" }
                            )
                        }
                    }
                } else {
                    // ─── Type-specific form (full screen) ───
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                    ) {
                        // Header chip + change-type button
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(when (postType) {
                                        "text" -> "Texto"
                                        "image" -> "Imagen"
                                        "video" -> "Video"
                                        "poll" -> "Encuesta"
                                        else -> ""
                                    }) },
                                    leadingIcon = {
                                        Icon(
                                            when (postType) {
                                                "text" -> Icons.Default.TextFields
                                                "image" -> Icons.Default.Image
                                                "video" -> Icons.Default.Movie
                                                "poll" -> Icons.Default.Poll
                                                else -> Icons.Default.Edit
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                                if (!isPosting) {
                                    TextButton(onClick = {
                                        postType = null
                                        content = ""
                                        videoTitle = ""
                                        videoDescription = ""
                                        error = ""
                                    }) { Text("Cambiar tipo") }
                                }
                            }
                        }

                        // ─── TEXT post fields ───
                        if (postType == "text") {
                            item {
                                OutlinedTextField(
                                    value = content,
                                    onValueChange = { content = it },
                                    label = { Text("Que estas pensando?") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4,
                                    maxLines = 8,
                                    enabled = !isPosting
                                )
                            }
                        }

                        // ─── IMAGE post fields ───
                        if (postType == "image") {
                            item {
                                if (!isPosting) {
                                    OutlinedButton(
                                        onClick = onAddImage,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (selectedImages.isEmpty()) "Anadir imagenes" else "Anadir mas")
                                    }
                                }
                            }
                            if (selectedImages.isNotEmpty()) {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(selectedImages) { uri ->
                                            Box(modifier = Modifier.size(120.dp)) {
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
                                                            .size(28.dp)
                                                            .background(MaterialTheme.colorScheme.error, CircleShape)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                OutlinedTextField(
                                    value = content,
                                    onValueChange = { content = it },
                                    label = { Text("Descripcion (opcional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4,
                                    enabled = !isPosting
                                )
                            }
                        }

                        // ─── VIDEO post fields ───
                        if (postType == "video") {
                            item {
                                if (!isPosting) {
                                    OutlinedButton(
                                        onClick = onAddVideo,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = selectedVideo == null
                                    ) {
                                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (selectedVideo == null) "Seleccionar video" else "Cambiar video")
                                    }
                                }
                            }

                            // Video preview
                            if (selectedVideo != null) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black)
                                    ) {
                                        AndroidView(
                                            factory = { ctx ->
                                                android.widget.VideoView(ctx).apply {
                                                    setVideoURI(selectedVideo)
                                                    setOnPreparedListener { mp -> mp.isLooping = true }
                                                    start()
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (!isPosting) {
                                            IconButton(
                                                onClick = onRemoveVideo,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(32.dp)
                                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Quitar video", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Text(
                                            text = selectedVideo.lastPathSegment ?: "video",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                OutlinedTextField(
                                    value = videoTitle,
                                    onValueChange = { videoTitle = it },
                                    label = { Text("Titulo del video") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                    enabled = !isPosting && selectedVideo != null
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = videoDescription,
                                    onValueChange = { videoDescription = it },
                                    label = { Text("Descripcion del video") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 5,
                                    enabled = !isPosting && selectedVideo != null
                                )
                            }
                        }

                        // ─── POLL placeholder ───
                        if (postType == "poll") {
                            item {
                                Text(
                                    "Creacion de encuestas proximamente",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // ─── Upload + processing progress ───
                        if (isPosting) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Phase 1: upload (real %)
                                    if (uploadProgress < 1f && processingProgress < 0f) {
                                        LinearProgressIndicator(
                                            progress = { uploadProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            uploadProgressText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        // Phase 2: server-side conversion (indeterminate-ish bar that fills slowly)
                                        LinearProgressIndicator(
                                            progress = { processingProgress.coerceAtLeast(0.05f) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Procesando video en el servidor... ${processingElapsed}s",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Esto puede tardar unos minutos según la duración del video.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (error.isNotBlank()) {
                            item {
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pill-style button used in the post type picker. */
@Composable
private fun PostTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}

/**
 * Small clickable pill that displays a #hashtag and invokes [onClick] when tapped.
 * Used in PostCard to let users navigate to the hashtag feed.
 */
@Composable
fun HashtagChip(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chipColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(chipColor.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "#$tag",
            color = chipColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Renders a video's title and description BELOW the video (not as an overlay).
 *
 * - Title is shown in full (single line, ellipsized).
 * - Description is collapsed to [collapsedMaxLines] lines by default. A "Ver más" /
 *   "Ver menos" TextButton toggles between collapsed and fully expanded.
 *
 * Used in the feed PostCard and in the VideoDetailOverlay scrollable area.
 *
 * @param title video title (optional). When null/blank, only the description is shown.
 * @param description video description (optional). When null/blank, only the title is shown.
 * @param collapsedMaxLines number of description lines when collapsed (default 2).
 * @param titleColor color for the title text.
 * @param descriptionColor color for the description text.
 * @param accentColor color for the "Ver más / Ver menos" button text.
 */
@Composable
fun VideoDetailsSection(
    title: String?,
    description: String?,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 2,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    if (title.isNullOrBlank() && description.isNullOrBlank()) return

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!description.isNullOrBlank()) {
            if (!title.isNullOrBlank()) Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = descriptionColor,
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            // "Ver más" / "Ver menos" toggle — only show if the description is long enough
            // to actually be truncated. We approximate this by line count: if the description
            // has more newlines than collapsedMaxLines, or is reasonably long, show the button.
            // Compose can't easily measure truncated state, so we use a heuristic.
            val lineCount = description.count { it == '\n' } + 1
            val isLikelyTruncated = lineCount > collapsedMaxLines || description.length > collapsedMaxLines * 40
            if (isLikelyTruncated) {
                Text(
                    text = if (expanded) "Ver menos" else "Ver más",
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                        .padding(top = 2.dp)
                )
            }
        }
    }
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
    onVideoClick: () -> Unit = {},
    onVideoPlayerRef: (androidx.media3.exoplayer.ExoPlayer?) -> Unit = {},
    isVideoOverlayShowing: Boolean = false,
    currentUserId: String? = null,
    videoResumePosition: Long = 0L,
    onHashtagClick: (String) -> Unit = {},
    onShareClick: () -> Unit = {},
    onWatched: () -> Unit = {}
) {
    val post = postResponse.post
    val context = LocalContext.current
    var isSaved by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    // Double-tap like animation
    var showLikeAnimation by remember { mutableStateOf(false) }
    val likeScale = remember { Animatable(0f) }

    // Whether this post belongs to the logged-in user — hides the Follow button
    val isOwnPost = currentUserId != null && post.userId == currentUserId

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

        // ═══════════ AUTHOR ROW: avatar + name/profession + follow + menu ═══════════
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Avatar
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
            Spacer(modifier = Modifier.width(10.dp))
            // Name + profession/bio
            Column(
                modifier = Modifier.weight(1f).clickable { onAuthorClick(postResponse.authorUsername) }
            ) {
                Text(
                    text = postResponse.authorDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onCardColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = postResponse.authorBio?.takeIf { it.isNotBlank() }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = onCardColorVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Follow button — hidden for the logged-in user's own posts
            if (!isOwnPost) {
                TextButton(
                    onClick = { /* TODO: Follow API */ },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (postResponse.isFollowingAuthor) "Siguiendo" else "Seguir",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (postResponse.isFollowingAuthor) onCardColorVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }
            // More menu
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
            Text(text = post.content, style = MaterialTheme.typography.bodyLarge, color = onCardColor, maxLines = 10, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
        }

        // Hashtags — clickable chips that open the hashtag feed
        if (postResponse.hashtags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(postResponse.hashtags) { tag ->
                    HashtagChip(tag = tag, onClick = { onHashtagClick(tag) }, chipColor = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary)
                }
            }
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
                onClick = onVideoClick,
                onPlayerRef = onVideoPlayerRef,
                isOverlayShowing = isVideoOverlayShowing,
                resumePosition = videoResumePosition,
                onDoubleClick = onLikeClick,
                onWatched = onWatched
            )

            // Video title + description BELOW the video (with "Ver más" expansion)
            val videoTitle = postResponse.video.title.takeIf { it.isNotBlank() }
            val videoDescription = postResponse.video.description.takeIf { it.isNotBlank() }
            if (videoTitle != null || videoDescription != null) {
                VideoDetailsSection(
                    title = videoTitle,
                    description = videoDescription,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    collapsedMaxLines = 2,
                    titleColor = onCardColor,
                    descriptionColor = onCardColorVariant,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ═══════════ ACTIONS ROW: like, comment, repost, share, save ═══════════
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onLikeClick() }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (postResponse.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (postResponse.isLiked) Color(0xFFFF2D55) else onCardColorVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (postResponse.likesCount > 0) {
                    Text(
                        text = formatCount(postResponse.likesCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (postResponse.isLiked) Color(0xFFFF2D55) else onCardColorVariant
                    )
                }
            }
            // Comment
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCommentClick() }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comentar", tint = onCardColorVariant, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                if (postResponse.commentsCount > 0) {
                    Text(text = formatCount(postResponse.commentsCount), style = MaterialTheme.typography.labelSmall, color = onCardColorVariant)
                }
            }
            // Repost
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* TODO: Repost API */ }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Repeat, contentDescription = "Repostear", tint = onCardColorVariant, modifier = Modifier.size(20.dp))
            }
            // Share
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Publicado por @${postResponse.authorUsername} en AUSO\n\n${post.content}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir via"))
                        // Notify the parent so it can record the share impression
                        onShareClick()
                    }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Share, contentDescription = "Compartir", tint = onCardColorVariant, modifier = Modifier.size(20.dp))
            }
            // Bookmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isSaved = !isSaved }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Guardar",
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else onCardColorVariant,
                    modifier = Modifier.size(20.dp)
                )
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
 * Format large counts: 1.2K, 3.5M, etc.
 */
private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

/**
 * Lightweight video player for the feed.
 * - When NOT playing: shows only thumbnail + play button (NO ExoPlayer created = zero resources)
 * - When playing: creates ExoPlayer, renders video surface, shows progress bar
 * This prevents all videos from loading/buffering when scrolling the feed.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onClick: () -> Unit = {},
    onPlayerRef: (androidx.media3.exoplayer.ExoPlayer?) -> Unit = {},
    isOverlayShowing: Boolean = false,
    title: String? = null,
    description: String? = null,
    resumePosition: Long = 0L,
    onDoubleClick: () -> Unit = {},
    onWatched: () -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    // Track whether we already fired the "watched" signal for this playback session
    // (avoid spamming the backend with view impressions every 200ms)
    var watchedFired by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenWidthPx = configuration.screenWidthDp
    val screenHeightPx = configuration.screenHeightDp
    val isVerticalVideo = videoWidth > 0 && videoHeight > 0 && videoWidth < videoHeight
    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else 16f / 9f
    val calculatedHeightDp = (screenWidthPx / aspectRatio).roundToInt()
    val videoHeightDp = if (isVerticalVideo) {
        minOf(calculatedHeightDp, (screenHeightPx * 0.80).roundToInt())
    } else {
        minOf(calculatedHeightDp, maxMediaHeightDp)
    }

    // ═══════════ KEY OPTIMIZATION: Only create ExoPlayer when actually playing ═══════════
    // This prevents all visible videos from loading/buffering during scroll
    if (isAutoPlay) {
        // ---- ACTIVE PLAYBACK MODE: ExoPlayer is created and video renders ----
        val exoPlayer = remember {
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
                // Resume from saved position (continuity from overlay → feed)
                if (resumePosition > 0L) seekTo(resumePosition)
                prepare()
                playWhenReady = true
                volume = if (isMuted) 0f else 1f
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            }
        }

        DisposableEffect(exoPlayer) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                    onPlayChanged(playing)
                }
            }
            exoPlayer.addListener(listener)
            onDispose { exoPlayer.removeListener(listener) }
        }

        LaunchedEffect(exoPlayer) { onPlayerRef(exoPlayer) }

        LaunchedEffect(isPlaying) {
            while (isPlaying) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (totalDuration <= 0L) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                // Fire the "viewed" impression once the user has watched ≥3 seconds
                if (!watchedFired && currentPosition >= 3000L) {
                    watchedFired = true
                    onWatched()
                }
                kotlinx.coroutines.delay(200)
            }
        }

        LaunchedEffect(isOverlayShowing) {
            if (isOverlayShowing) return@LaunchedEffect
            exoPlayer.playWhenReady = true
        }

        LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }

        DisposableEffect(Unit) {
            onDispose {
                onPlayerRef(null)
                exoPlayer.release()
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(videoHeightDp.dp)) {
            // Video surface
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = if (isOverlayShowing) null else exoPlayer
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { view ->
                    view.player = if (isOverlayShowing) null else exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )

            // Tap (single) to open overlay; double tap to like — heart animation overlay
            var showHeartAnim by remember { mutableStateOf(false) }
            val heartScale = remember { Animatable(0f) }
            if (showHeartAnim) {
                LaunchedEffect(showHeartAnim) {
                    heartScale.snapTo(0f)
                    heartScale.animateTo(1.3f)
                    heartScale.animateTo(0f)
                    showHeartAnim = false
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick() },
                        onDoubleClick = {
                            onDoubleClick()
                            showHeartAnim = true
                        }
                    )
            )
            if (showHeartAnim) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(90.dp).scale(heartScale.value)
                    )
                }
            }

            // Mute button — icon only, no background
            IconButton(
                onClick = { onMuteChanged(!isMuted) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
            ) {
                Icon(
                    if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Progress bar — animated magical power waves
            if (isPlaying) {
                val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                MagicProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    trackColor = Color.White.copy(alpha = 0.2f),
                    heightDp = 3.dp,
                )
            }
        }
    } else {
        // ---- THUMBNAIL MODE: No ExoPlayer, just thumbnail + play button ----
        // Zero video resources consumed while scrolling
        onPlayerRef(null)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(videoHeightDp.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Thumbnail image
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // Play button overlay
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            // Duration badge
            if (duration > 0) {
                val minutes = (duration / 60).toInt()
                val seconds = (duration % 60).toInt()
                Text(
                    text = String.format("%d:%02d", minutes, seconds),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Full-screen video detail overlay
 * - Horizontal videos: YouTube-style (video top, details below)
 * - Vertical videos: TikTok-style (video full, action buttons right, info bottom-left)
 * Supports: system back button, swipe down to dismiss
 */
@Composable
fun VideoDetailOverlay(
    postResponse: PostResponse,
    isMuted: Boolean = false,
    onMuteChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    sharedPlayer: androidx.media3.exoplayer.ExoPlayer? = null,
    currentUserId: String? = null,
    onClosePosition: (Long) -> Unit = {}
) {
    val post = postResponse.post
    val context = LocalContext.current
    var isSaved by remember { mutableStateOf(false) }

    // Whether this post belongs to the logged-in user — hides the Follow button
    val isOwnPost = currentUserId != null && post.userId == currentUserId

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Swipe-to-dismiss state
    var offsetY by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = with(LocalDensity.current) { 200.dp.toPx() }

    // Intercept system back button to close overlay instead of app
    BackHandler(onBack = onBack)

    // Calculate video aspect ratio — support vertical (portrait) videos
    val videoWidth = postResponse.video?.width ?: 0
    val videoHeight = postResponse.video?.height ?: 0
    val videoAspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        16f / 9f
    }
    val isVerticalVideo = videoAspectRatio < 1f

    // The overlay ALWAYS creates its own ExoPlayer — this avoids all shared player
    // lifecycle issues (feed releasing the player from the map, surface handoff timing, etc.)
    // If a sharedPlayer was provided, we seek to its current position for continuity.
    val sharedPlayerStartPosition = sharedPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val videoUrl = AusoApiClient.fullUrl(postResponse.video?.hlsMasterPlaylistUrl) ?: ""
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            if (sharedPlayerStartPosition > 0L) {
                seekTo(sharedPlayerStartPosition)
            }
            prepare()
            playWhenReady = true
            volume = if (isMuted) 0f else 1f
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        }
    }

    // Pause the shared player (feed's player) to avoid double buffering
    if (sharedPlayer != null) {
        LaunchedEffect(Unit) { sharedPlayer?.playWhenReady = false }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (playing) totalDuration = exoPlayer.duration.coerceAtLeast(0L) }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            // Report the final playback position so the feed can resume from there
            onClosePosition(exoPlayer.currentPosition.coerceAtLeast(0L))
            exoPlayer.release()
        }
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

    // Full screen overlay — respects system bars
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .graphicsLayer { translationY = offsetY }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (offsetY > dismissThreshold) onBack()
                        offsetY = 0f
                    },
                    onDragCancel = { offsetY = 0f }
                )
            }
    ) {
        if (isVerticalVideo) {
            // ═══════════ TIKTOK LAYOUT for vertical videos ═══════════
            // Video fills the whole area
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

            // Back button top-start
            if (showControls) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(36.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            // Right side action buttons (TikTok-style)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Avatar
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .clickable { onAuthorClick(postResponse.authorUsername) },
                    contentAlignment = Alignment.Center
                ) {
                    if (!postResponse.authorProfilePhoto.isNullOrBlank()) {
                        AsyncImage(model = AusoApiClient.fullUrl(postResponse.authorProfilePhoto), contentDescription = "Avatar", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(text = postResponse.authorDisplayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                // Like
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (postResponse.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (postResponse.isLiked) Color(0xFFFF2D55) else Color.White, modifier = Modifier.size(28.dp))
                    Text("${postResponse.likesCount}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                // Comment
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text("${postResponse.commentsCount}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                // Share
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val shareIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "Publicado por @${postResponse.authorUsername} en AUSO\n\n${post.content}"); type = "text/plain" }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir via"))
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text("Compartir", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                // Bookmark
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { isSaved = !isSaved }, modifier = Modifier.size(28.dp)) {
                        Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null, tint = if (isSaved) Color.White else Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text("Guardar", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                // Mute
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { onMuteChanged(!isMuted) }, modifier = Modifier.size(28.dp)) {
                        Icon(if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text(if (isMuted) "Silencio" else "Sonido", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }

            // Bottom-left: author info + title + description with "Ver más" (TikTok-style)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 100.dp)
                    .fillMaxWidth(0.78f)
            ) {
                Text(text = "@${postResponse.authorUsername}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                // Video title + description (collapses to 2 lines, expands with "Ver más")
                VideoDetailsSection(
                    title = postResponse.video?.title?.takeIf { it.isNotBlank() },
                    description = postResponse.video?.description?.takeIf { it.isNotBlank() }
                        ?: post.content.takeIf { it.isNotBlank() },
                    collapsedMaxLines = 2,
                    titleColor = Color.White,
                    descriptionColor = Color.White.copy(alpha = 0.9f),
                    accentColor = Color.White
                )
            }

            // Progress bar at very bottom — animated magical power waves
            val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
            MagicProgressBar(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 88.dp),
                trackColor = Color.White.copy(alpha = 0.2f),
                heightDp = 3.dp,
            )

        } else {
            // ═══════════ YOUTUBE LAYOUT for horizontal videos ═══════════
            Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Video area with correct aspect ratio
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(videoAspectRatio).background(Color.Black)) {
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

                    // Back button top-start
                    if (showControls) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(36.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }

                    // Controls: play/pause + seekbar + mute
                    androidx.compose.animation.AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (exoPlayer.isPlaying) { exoPlayer.pause() } else { exoPlayer.play() }
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(if (exoPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                            Slider(
                                value = progress,
                                onValueChange = { fraction -> val seekPosition = (fraction * totalDuration).toLong(); exoPlayer.seekTo(seekPosition); currentPosition = seekPosition },
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            )
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
                            // Follow button — hidden for the logged-in user's own posts
                            if (!isOwnPost) {
                                Button(onClick = { /* TODO: Follow */ }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                                    Text(if (postResponse.isFollowingAuthor) "Siguiendo" else "Seguir", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Video title + description with "Ver más" expansion (collapses to 3 lines)
                    val videoTitle = postResponse.video?.title?.takeIf { it.isNotBlank() }
                    val videoDescription = postResponse.video?.description?.takeIf { it.isNotBlank() }
                    if (videoTitle != null || videoDescription != null) {
                        item {
                            VideoDetailsSection(
                                title = videoTitle,
                                description = videoDescription,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                                collapsedMaxLines = 3
                            )
                        }
                    }

                    // Post content (caption)
                    if (post.content.isNotBlank()) {
                        item {
                            Text(text = post.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }

                    // Actions row — no comment icon here (comments live in the section below)
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
    }
}
