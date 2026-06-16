package com.auso.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.CreateTextPostRequest
import com.auso.social.network.model.FeedResponse
import com.auso.social.network.model.PostResponse
import com.auso.social.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Home screen - shows the post feed
 * FAB is handled by MainScreen
 */
@Composable
fun HomeScreen(
    tabName: String = "Amigos",
    authViewModel: AuthViewModel? = null
) {
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Load feed
    LaunchedEffect(tabName) {
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
                    text = "🏠",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "¡Bienvenido a AUSO!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (tabName) {
                        "Amigos" -> "Sigue a usuarios para ver sus publicaciones aquí"
                        "Recomendado" -> "Las publicaciones recomendadas aparecerán aquí"
                        "Explorar" -> "Explora publicaciones de toda la comunidad"
                        else -> "Sigue a usuarios para ver sus publicaciones aquí"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = 80.dp // Space for FAB above bottom bar
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
                                            val feedResponse = AusoApiClient.api.getFeed("Bearer $token")
                                            if (feedResponse.isSuccessful) {
                                                posts = feedResponse.body()?.posts ?: emptyList()
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        onCommentClick = { /* TODO: Comments */ }
                    )
                }
            }
        }
    }

    // Create post dialog - triggered externally if needed
    if (showCreateDialog) {
        CreatePostDialog(
            onDismiss = { showCreateDialog = false },
            onPostCreated = {
                coroutineScope.launch {
                    try {
                        val token = AusoApiClient.getToken()
                        if (token != null) {
                            val response = AusoApiClient.api.getFeed("Bearer $token")
                            if (response.isSuccessful) {
                                posts = response.body()?.posts ?: emptyList()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        )
    }
}

// Public function to show create post dialog from MainScreen FAB
@Composable
fun rememberCreatePostState(): MutableState<Boolean> {
    return remember { mutableStateOf(false) }
}

@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPostCreated: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        title = {
            Text(
                text = "Crear publicación",
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
                    placeholder = { Text("¿Qué estás pensando?") },
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
                    if (content.isBlank()) {
                        error = "Escribe algo para publicar"
                        return@TextButton
                    }
                    isPosting = true
                    coroutineScope.launch {
                        try {
                            val token = AusoApiClient.getToken()
                            if (token != null) {
                                val request = CreateTextPostRequest(content = content.trim())
                                val response = AusoApiClient.api.createTextPost("Bearer $token", request)
                                if (response.isSuccessful) {
                                    onPostCreated()
                                    onDismiss()
                                } else {
                                    error = "Error al publicar"
                                }
                            } else {
                                error = "No autenticado"
                            }
                        } catch (e: Exception) {
                            error = "Error de conexión: ${e.message}"
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
    onCommentClick: () -> Unit
) {
    val post = postResponse.post

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (post.backgroundColor != null) {
                try {
                    android.graphics.Color.parseColor(post.backgroundColor)
                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(post.backgroundColor))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.surface
                }
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Author row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = postResponse.authorDisplayName.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
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
            }

            // Post content
            if (post.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (post.backgroundColor != null)
                        androidx.compose.ui.graphics.Color.White
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Images chip
            if (postResponse.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AssistChip(
                    onClick = {},
                    label = { Text("${postResponse.images.size} imagen(es)") },
                    modifier = Modifier.padding(2.dp)
                )
            }

            // Video chip
            if (post.postType == "video") {
                Spacer(modifier = Modifier.height(12.dp))
                AssistChip(
                    onClick = {},
                    label = { Text("🎬 Video") },
                    modifier = Modifier.padding(2.dp)
                )
            }

            // Poll
            if (post.postType == "poll" && postResponse.poll != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    Text(
                        text = "📊 ${postResponse.poll.poll.question}",
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

            // Actions
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
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
            }
        }
    }
}
