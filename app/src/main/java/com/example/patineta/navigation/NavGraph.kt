package com.example.patineta.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.patineta.ui.achievements.AchievementsScreen
import com.example.patineta.ui.auth.AuthViewModel
import com.example.patineta.ui.auth.EmailVerificationScreen
import com.example.patineta.ui.auth.LoginScreen
import com.example.patineta.ui.auth.RegisterScreen
import com.example.patineta.ui.profile.AccountSettingsScreen
import com.example.patineta.ui.profile.ProfileScreen
import com.example.patineta.ui.profile.ScooterDetailScreen
import com.example.patineta.ui.profile.ScootersManagementScreen
import com.example.patineta.ui.records.RecordsHistoryScreen
import com.example.patineta.ui.repairs.RepairsScreen
import com.example.patineta.ui.statistics.StatisticsScreen
import com.example.patineta.ui.theme.ThemeMode
import com.example.patineta.navigation.Screen

@Composable
fun NavGraph(
    navController: NavHostController,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    pureBlackOledEnabled: Boolean,
    onPureBlackOledChange: (Boolean) -> Unit,
    authViewModel: com.example.patineta.ui.auth.AuthViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    
    // Determinar la pantalla de inicio basada en el estado de autenticación
    val startDestination = when (authState) {
        is com.example.patineta.ui.auth.AuthState.Success -> Screen.Records.route
        else -> Screen.Login.route
    }
    
    // Usar remember y derivedStateOf para la navegación
    val shouldNavigateToLogin = remember(authState) {
        authState is com.example.patineta.ui.auth.AuthState.Initial
    }
    
    val shouldNavigateToRecords = remember(authState) {
        authState is com.example.patineta.ui.auth.AuthState.Success
    }
    
    // Navegar automáticamente según el estado de autenticación
    LaunchedEffect(shouldNavigateToLogin) {
        if (shouldNavigateToLogin) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
    
    LaunchedEffect(shouldNavigateToRecords) {
        if (shouldNavigateToRecords) {
            if (navController.currentDestination?.route != Screen.Records.route) {
                navController.navigate(Screen.Records.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Records.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onRegisterClick = {
                    navController.navigate(Screen.Register.route)
                },
                onEmailVerification = {
                    navController.navigate(Screen.EmailVerification.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                navController = navController,
                onRegisterSuccess = {
                    navController.navigate(Screen.Records.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onEmailVerification = {
                    navController.navigate(Screen.EmailVerification.route)
                }
            )
        }

        composable(Screen.EmailVerification.route) {
            EmailVerificationScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.EmailVerification.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Records.route) {
            RecordsHistoryScreen(navController = navController)
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(navController = navController)
        }

        composable(
            route = Screen.Repairs.route + "/{scooterId}"
        ) { backStackEntry ->
            val scooterId = backStackEntry.arguments?.getString("scooterId") ?: ""
            RepairsScreen(navController = navController, scooterId = scooterId)
        }

        composable(Screen.Achievements.route) {
            AchievementsScreen(navController = navController)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                navController = navController,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColorEnabled = dynamicColorEnabled,
                onDynamicColorChange = onDynamicColorChange
            )
        }

        composable(Screen.Settings.route) {
            com.example.patineta.ui.settings.SettingsScreen(
                navController = navController,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColorEnabled = dynamicColorEnabled,
                onDynamicColorChange = onDynamicColorChange,
                pureBlackOledEnabled = pureBlackOledEnabled,
                onPureBlackOledChange = onPureBlackOledChange
            )
        }

        composable(Screen.AccountSettings.route) {
            AccountSettingsScreen(
                navController = navController,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColorEnabled = dynamicColorEnabled,
                onDynamicColorChange = onDynamicColorChange,
                pureBlackOledEnabled = pureBlackOledEnabled,
                onPureBlackOledChange = onPureBlackOledChange
            )
        }

        composable(Screen.ScootersManagement.route) {
            ScootersManagementScreen(navController = navController)
        }

        composable(
            route = Screen.ScooterDetail.route + "/{scooterId}"
        ) { backStackEntry ->
            val scooterId = backStackEntry.arguments?.getString("scooterId") ?: ""
            ScooterDetailScreen(
                navController = navController,
                scooterId = scooterId
            )
        }
    }
}
