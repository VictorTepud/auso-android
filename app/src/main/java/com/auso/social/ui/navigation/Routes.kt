package com.auso.social.ui.navigation

/**
 * Navigation routes for AUSO
 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val HOME = "home"
    const val SEARCH = "search"
    const val VIDEOS = "videos"
    const val APPS = "apps"
    const val PROFILE = "profile"
}

/**
 * Bottom navigation items
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)
