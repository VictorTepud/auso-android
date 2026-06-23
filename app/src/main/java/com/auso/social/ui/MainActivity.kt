package com.auso.social.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auso.social.data.ThemeManager
import com.auso.social.ui.navigation.Routes
import com.auso.social.ui.screens.InterestsScreen
import com.auso.social.ui.screens.LoginScreen
import com.auso.social.ui.screens.RegisterScreen
import com.auso.social.ui.theme.AUSOTheme
import com.auso.social.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeManager = remember { ThemeManager(context) }
            val themeMode by themeManager.themeFlow.collectAsState(initial = ThemeManager.THEME_SYSTEM)

            AUSOTheme(themeMode = themeMode) {
                AUSOApp(themeManager = themeManager)
            }
        }
    }
}

@Composable
fun AUSOApp(themeManager: ThemeManager) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val isCheckingAuth by authViewModel.isCheckingAuth.collectAsState()

    // Show splash while checking auth state
    if (isCheckingAuth) {
        SplashScreen()
        return
    }

    val startDestination = if (isLoggedIn) Routes.MAIN else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            route = Routes.LOGIN,
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.REGISTER,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    // After register, send the user to pick interests (mandatory ≥1 category)
                    navController.navigate(Routes.INTERESTS) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.INTERESTS,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            }
        ) {
            InterestsScreen(
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.INTERESTS) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.MAIN,
            enterTransition = { fadeIn(animationSpec = tween(500)) }
        ) {
            MainScreen(
                authViewModel = authViewModel,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                themeManager = themeManager
            )
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AUSO",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AURA SOCIAL",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}
