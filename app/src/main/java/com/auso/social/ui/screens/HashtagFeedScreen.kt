package com.auso.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.PostResponse
import kotlinx.coroutines.launch

/**
 * Shows all posts tagged with a given hashtag.
 * Reuses PostCard for rendering consistency with the main feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagFeedScreen(
    tag: String,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {}
) {
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(tag) {
        isLoading = true
        error = ""
        try {
            val token = AusoApiClient.getToken()
            if (token != null) {
                val response = AusoApiClient.api.getPostsByHashtag("Bearer $token", tag)
                if (response.isSuccessful) {
                    posts = response.body()?.posts ?: emptyList()
                } else {
                    error = "Error al cargar el hashtag"
                }
            }
        } catch (e: Exception) {
            error = "Error de conexión"
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "#$tag",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                error.isNotBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
                posts.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Aún no hay publicaciones con #$tag",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(posts, key = { it.post.id }) { postResponse ->
                            PostCard(
                                postResponse = postResponse,
                                onLikeClick = {
                                    coroutineScope.launch {
                                        try {
                                            val token = AusoApiClient.getToken()
                                            if (token != null) {
                                                val resp = AusoApiClient.api.toggleLike("Bearer $token", postResponse.post.id)
                                                if (resp.isSuccessful) {
                                                    val newLiked = resp.body()?.liked ?: !postResponse.isLiked
                                                    val newCount = resp.body()?.likesCount ?: postResponse.likesCount
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
                                onCommentClick = {},
                                onAuthorClick = onAuthorClick
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}
