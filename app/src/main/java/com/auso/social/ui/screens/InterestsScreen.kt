package com.auso.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auso.social.network.AusoApiClient
import com.auso.social.network.model.Category
import com.auso.social.network.model.SetInterestsRequest
import kotlinx.coroutines.launch

/**
 * Onboarding screen shown after registration.
 * The user MUST select at least 1 category — this seeds the recommendation
 * algorithm. Shown right after the account is created, before the main feed.
 */
@Composable
fun InterestsScreen(
    onComplete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    // Load categories on first render
    LaunchedEffect(Unit) {
        try {
            val token = AusoApiClient.getToken()
            if (token == null) { error = "No autenticado"; isLoading = false; return@LaunchedEffect }
            val response = AusoApiClient.api.listCategories("Bearer $token")
            if (response.isSuccessful) {
                categories = response.body() ?: emptyList()
            } else {
                error = "No se pudieron cargar las categorías"
            }
        } catch (e: Exception) {
            error = "Error de conexión: ${e.message}"
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Text(
            text = "¿Qué te interesa?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Selecciona al menos una categoría. La usaremos para recomendarte contenido que te guste.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(categories, key = { it.id }) { category ->
                    val isSelected = selected.contains(category.id)
                    CategoryCard(
                        category = category,
                        isSelected = isSelected,
                        onClick = {
                            selected = if (isSelected) selected - category.id
                            else selected + category.id
                        }
                    )
                }
            }
        }

        // Sticky bottom button
        Button(
            onClick = {
                if (isSaving || selected.isEmpty()) return@Button
                isSaving = true
                coroutineScope.launch {
                    try {
                        val token = AusoApiClient.getToken()
                        if (token == null) { error = "No autenticado"; isSaving = false; return@launch }
                        val response = AusoApiClient.api.setInterests(
                            "Bearer $token",
                            SetInterestsRequest(categoryIds = selected.toList())
                        )
                        if (response.isSuccessful) {
                            onComplete()
                        } else {
                            error = "Error al guardar: ${response.code()}"
                        }
                    } catch (e: Exception) {
                        error = "Error: ${e.message}"
                    }
                    isSaving = false
                }
            },
            enabled = selected.isNotEmpty() && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (selected.isEmpty()) "Selecciona al menos una" else "Continuar (${selected.size})",
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = category.icon,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                    .padding(2.dp)
            )
        }
    }
}
