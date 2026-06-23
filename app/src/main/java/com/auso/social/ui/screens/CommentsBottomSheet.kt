package com.auso.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.CommentResponse
import com.auso.social.network.model.CreateCommentRequest
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet that shows the comments of a post and lets the user post a new one.
 *
 * - Loads existing comments on open via GET /posts/{postId}/comments.
 * - Posting a comment calls POST /posts/{postId}/comments and prepends the new comment
 *   to the list. The caller is notified via [onCommentPosted] so it can record the
 *   "comment" impression for the recommender.
 *
 * @param postId the post whose comments are shown
 * @param onDismiss called when the user closes the sheet
 * @param onCommentPosted called after a comment is successfully posted (used to train the algo)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    onCommentPosted: () -> Unit = {},
    onCommentsChanged: (delta: Int) -> Unit = {},
    onAuthorClick: (String) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<CommentResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var newComment by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Track the number of comments posted during this session, so the caller can
    // update the PostCard's count when the sheet closes.
    var postedCount by remember { mutableStateOf(0) }

    // Load comments on first render
    LaunchedEffect(postId) {
        isLoading = true
        try {
            val token = AusoApiClient.getToken()
            if (token != null) {
                val response = AusoApiClient.api.getComments("Bearer $token", postId)
                if (response.isSuccessful) {
                    // Newest first — the backend returns oldest first, so we reverse
                    comments = (response.body() ?: emptyList()).reversed()
                }
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    // When the sheet closes, notify the caller of the net comment delta so the
    // PostCard can update its comment count without refetching the feed.
    val handleDismiss = {
        if (postedCount != 0) onCommentsChanged(postedCount)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = handleDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comentarios (${comments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
            HorizontalDivider()

            // Comments list
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    comments.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("💬", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Sé el primero en comentar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(comments, key = { it.comment.id }) { comment ->
                                CommentRow(comment = comment, onAuthorClick = onAuthorClick)
                            }
                        }
                    }
                }
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    placeholder = { Text("Escribe un comentario...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    enabled = !isPosting,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (isPosting || newComment.isBlank()) return@IconButton
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
                                val response = AusoApiClient.api.createComment(
                                    "Bearer $token",
                                    postId,
                                    CreateCommentRequest(content = newComment.trim())
                                )
                                if (response.isSuccessful) {
                                    val created = response.body()
                                    if (created != null) {
                                        comments = listOf(created) + comments
                                        postedCount += 1
                                    }
                                    newComment = ""
                                    onCommentPosted()
                                } else {
                                    error = "Error al comentar: ${response.code()}"
                                }
                            } catch (e: Exception) {
                                error = "Error: ${e.message}"
                            }
                            isPosting = false
                        }
                    },
                    enabled = !isPosting && newComment.isNotBlank()
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = if (newComment.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentResponse,
    onAuthorClick: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onAuthorClick(comment.authorUsername) },
            contentAlignment = Alignment.Center
        ) {
            if (!comment.authorProfilePhoto.isNullOrBlank()) {
                AsyncImage(
                    model = AusoApiClient.fullUrl(comment.authorProfilePhoto),
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = comment.authorDisplayName.take(1).ifBlank { "U" }.uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        // Body
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorDisplayName.ifBlank { comment.authorUsername },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onAuthorClick(comment.authorUsername) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "@${comment.authorUsername}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
