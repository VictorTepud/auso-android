package com.auso.social.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.PostResponse
import kotlinx.coroutines.launch

/**
 * TikTok-style vertical video feed screen.
 * Uses VerticalPager for swipe-to-next video experience.
 * Only plays the current visible video; all others are paused.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideosScreen(
    isGlobalMuted: Boolean = false,
    onMuteChanged: (Boolean) -> Unit = {},
    onAuthorClick: (String) -> Unit = {}
) {
    var videoPosts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Load video-only feed
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val token = AusoApiClient.getToken()
            if (token != null) {
                val response = AusoApiClient.api.getFeed("Bearer $token", postType = "video")
                if (response.isSuccessful) {
                    val feed = response.body()
                    // Filter to only posts that actually have video data
                    videoPosts = feed?.posts?.filter { it.video != null } ?: emptyList()
                }
            }
        } catch (_: Exception) {
            // Silently fail
        }
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cargando videos...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else if (videoPosts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay videos aún",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Publica un video para verlo aquí",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    } else {
        // TikTok-style VerticalPager
        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { videoPosts.size }
        )

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1 // Pre-load 1 page ahead for smooth transitions
        ) { page ->
            val postResponse = videoPosts[page]
            val isCurrentPage = pagerState.currentPage == page

            TikTokVideoItem(
                postResponse = postResponse,
                isPlaying = isCurrentPage,
                isMuted = isGlobalMuted,
                onMuteChanged = onMuteChanged,
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
                                    videoPosts = videoPosts.map {
                                        if (it.post.id == postResponse.post.id) {
                                            it.copy(isLiked = newLiked, likesCount = newCount)
                                        } else it
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                },
                onAuthorClick = onAuthorClick,
                onShareClick = {
                    val context = it
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Publicado por @${postResponse.authorUsername} en AUSO\n\n${postResponse.post.content}"
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir via"))
                }
            )
        }
    }
}

/**
 * Full-screen TikTok-style video item.
 * Video fills entire screen with action buttons on the right side
 * and author info on the bottom-left.
 */
@Composable
fun TikTokVideoItem(
    postResponse: PostResponse,
    isPlaying: Boolean,
    isMuted: Boolean,
    onMuteChanged: (Boolean) -> Unit = {},
    onLikeClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onShareClick: (android.content.Context) -> Unit = {}
) {
    val post = postResponse.post
    val context = LocalContext.current
    var isSaved by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }

    // Player state
    var isActuallyPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }

    val videoUrl = AusoApiClient.fullUrl(postResponse.video?.hlsMasterPlaylistUrl) ?: ""

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = false
            volume = if (isMuted) 0f else 1f
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        }
    }

    // Listener for playback state
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isActuallyPlaying = playing
                if (playing) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls, isActuallyPlaying) {
        if (showControls && isActuallyPlaying) {
            kotlinx.coroutines.delay(4000)
            showControls = false
        }
    }

    // Play/pause based on visibility
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.playWhenReady = true
            showControls = true
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    // Update mute state
    LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }

    // Position tracker
    LaunchedEffect(isActuallyPlaying) {
        while (isActuallyPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (totalDuration <= 0L) totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(200)
        }
    }

    // Release player when leaving composition
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // Full screen layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video surface - fills entire area
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

        // Tap area to toggle controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isActuallyPlaying) {
                        showControls = !showControls
                    } else {
                        exoPlayer.play()
                        showControls = true
                    }
                }
        )

        // Buffering indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        // Play button overlay (when paused / not yet playing)
        if (!isActuallyPlaying && !isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        exoPlayer.play()
                        showControls = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // ═══════════ RIGHT SIDE ACTION BUTTONS (TikTok-style) ═══════════
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Avatar with follow indicator
            Box(
                modifier = Modifier.size(46.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onAuthorClick(postResponse.authorUsername) }
                        .align(Alignment.Center),
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
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                // Follow + badge
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.BottomCenter)
                        .offset(y = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Seguir",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Like
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onLikeClick() }
            ) {
                Icon(
                    if (postResponse.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (postResponse.isLiked) Color(0xFFFF2D55) else Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatCount(postResponse.likesCount),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }

            // Comment
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    formatCount(postResponse.commentsCount),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }

            // Share
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onShareClick(context) }
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Compartir",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }

            // Bookmark
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isSaved = !isSaved }
            ) {
                Icon(
                    if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Guardar",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }

            // Mute
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onMuteChanged(!isMuted) }
            ) {
                Icon(
                    if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (isMuted) "Silencio" else "Sonido",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }
        }

        // ═══════════ BOTTOM-LEFT INFO (TikTok-style) ═══════════
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 80.dp)
                .fillMaxWidth(0.7f)
        ) {
            // Username
            Text(
                text = "@${postResponse.authorUsername}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Description
            if (post.content.isNotBlank()) {
                Text(
                    text = post.content,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Music / duration info
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = postResponse.authorDisplayName,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }
        }

        // ═══════════ PROGRESS BAR ═══════════
        if (isActuallyPlaying || currentPosition > 0) {
            val progress = if (totalDuration > 0)
                (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
            else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 56.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

/**
 * Format large counts like TikTok: 1.2K, 3.5M, etc.
 */
private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
