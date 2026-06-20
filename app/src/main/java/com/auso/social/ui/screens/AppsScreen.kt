package com.auso.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auso.social.data.ThemeManager
import kotlinx.coroutines.launch

/**
 * Apps screen - tools and theme settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    themeManager: ThemeManager? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val currentTheme by themeManager?.themeFlow?.collectAsState(initial = ThemeManager.THEME_SYSTEM)
        ?: remember { mutableStateOf(ThemeManager.THEME_SYSTEM) }

    val tools = listOf(
        AppTool("Comunidades", "Explora y crea comunidades", Icons.Default.Public),
        AppTool("Grupos", "Únete a grupos de interés", Icons.Default.Groups),
        AppTool("Encuestas", "Crea y vota en encuestas", Icons.Default.Public),
        AppTool("Canales", "Canales dentro de comunidades", Icons.Default.Public),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Apps y Herramientas",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══════════ THEME SELECTOR ═══════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Tema",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Tema",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Theme options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ThemeOptionCard(
                                label = "Oscuro",
                                icon = Icons.Default.DarkMode,
                                isSelected = currentTheme == ThemeManager.THEME_DARK,
                                previewColor = Color(0xFF000000),
                                previewTextColor = Color(0xFFE0E0E0),
                                onClick = {
                                    coroutineScope.launch {
                                        themeManager?.setTheme(ThemeManager.THEME_DARK)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionCard(
                                label = "Claro",
                                icon = Icons.Default.LightMode,
                                isSelected = currentTheme == ThemeManager.THEME_LIGHT,
                                previewColor = Color(0xFFFFFFFF),
                                previewTextColor = Color(0xFF1A1A1A),
                                onClick = {
                                    coroutineScope.launch {
                                        themeManager?.setTheme(ThemeManager.THEME_LIGHT)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionCard(
                                label = "Sistema",
                                icon = Icons.Default.Settings,
                                isSelected = currentTheme == ThemeManager.THEME_SYSTEM,
                                previewColor = null, // Dynamic
                                previewTextColor = null,
                                onClick = {
                                    coroutineScope.launch {
                                        themeManager?.setTheme(ThemeManager.THEME_SYSTEM)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ═══════════ OTHER TOOLS ═══════════
            items(tools) { tool ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { /* TODO */ }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.name,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual theme option card with visual preview
 */
@Composable
private fun ThemeOptionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    previewColor: Color?,
    previewTextColor: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline

    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visual preview circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    CircleShape
                )
                .background(previewColor ?: MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (previewColor != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = previewTextColor ?: Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // System: split half dark / half light
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .align(Alignment.CenterStart)
                            .background(Color(0xFF000000))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .align(Alignment.CenterEnd)
                            .background(Color(0xFFFFFFFF))
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(20.dp).align(Alignment.Center)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

data class AppTool(
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
