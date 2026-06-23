package com.auso.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.HashtagWithCount
import com.auso.social.network.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search screen — searches users (default) and hashtags (when query starts with '#').
 * When the query is empty, shows trending hashtags so the user can explore.
 */
@Composable
fun SearchScreen(
    onSearch: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onCommunityClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var trending by remember { mutableStateOf<List<HashtagWithCount>>(emptyList()) }
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var hashtags by remember { mutableStateOf<List<com.auso.social.network.model.Hashtag>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Load trending hashtags on first render (shown when query is empty)
    LaunchedEffect(Unit) {
        try {
            val token = AusoApiClient.getToken()
            if (token != null) {
                val response = AusoApiClient.api.trendingHashtags("Bearer $token")
                if (response.isSuccessful) {
                    trending = response.body() ?: emptyList()
                }
            }
        } catch (_: Exception) {}
    }

    // Debounced search — 400ms after the user stops typing
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        if (searchQuery.isBlank()) {
            users = emptyList()
            hashtags = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        isLoading = true
        coroutineScope.launch {
            try {
                val token = AusoApiClient.getToken()
                if (token != null) {
                    if (searchQuery.startsWith("#")) {
                        // Hashtag search — strip the '#'
                        val q = searchQuery.removePrefix("#").trim()
                        val response = AusoApiClient.api.searchHashtags("Bearer $token", q)
                        if (response.isSuccessful) {
                            hashtags = response.body() ?: emptyList()
                            users = emptyList()
                        }
                    } else {
                        // User search
                        val response = AusoApiClient.api.searchUsers("Bearer $token", searchQuery.trim())
                        if (response.isSuccessful) {
                            users = response.body() ?: emptyList()
                            hashtags = emptyList()
                        }
                    }
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
            },
            placeholder = { Text("Buscar personas o #hashtags...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        when {
            // ─── Empty query → trending hashtags + hint ───
            searchQuery.isBlank() -> {
                if (trending.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🔍", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Descubre en AUSO",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Busca personas o #hashtags",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Trending",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        LazyColumn {
                            items(trending, key = { it.tag }) { ht ->
                                TrendingHashtagRow(
                                    hashtag = ht,
                                    onClick = { onHashtagClick(ht.tag) }
                                )
                            }
                        }
                    }
                }
            }

            // ─── Loading ───
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                }
            }

            // ─── Hashtag results ───
            searchQuery.startsWith("#") && hashtags.isNotEmpty() -> {
                LazyColumn {
                    items(hashtags, key = { it.id }) { ht ->
                        HashtagSearchRow(
                            tag = ht.tag,
                            usageCount = ht.usageCount,
                            onClick = { onHashtagClick(ht.tag) }
                        )
                    }
                }
            }

            // ─── User results ───
            !searchQuery.startsWith("#") && users.isNotEmpty() -> {
                LazyColumn {
                    items(users, key = { it.id }) { user ->
                        UserSearchRow(
                            user = user,
                            onClick = { onUserClick(user.username) }
                        )
                    }
                }
            }

            // ─── No results ───
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Sin resultados para \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingHashtagRow(hashtag: HashtagWithCount, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#${hashtag.tag}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${hashtag.usageCount} publicaciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HashtagSearchRow(tag: String, usageCount: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "#$tag", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "$usageCount publicaciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UserSearchRow(user: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (user.displayName.ifBlank { user.username }).take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.displayName.ifBlank { user.username }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
