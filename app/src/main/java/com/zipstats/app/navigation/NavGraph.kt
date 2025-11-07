package com.zipstats.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.zipstats.app.ui.achievements.AchievementsScreen
import com.zipstats.app.ui.auth.AuthViewModel
import com.zipstats.app.ui.auth.EmailVerificationScreen
import com.zipstats.app.ui.auth.LoginScreen
import com.zipstats.app.ui.auth.RegisterScreen
import com.zipstats.app.ui.profile.AccountSettingsScreen
import com.zipstats.app.ui.profile.ProfileScreen
import com.zipstats.app.ui.profile.ScooterDetailScreen
import com.zipstats.app.ui.profile.ScootersManagementScreen
import com.zipstats.app.ui.records.RecordsHistoryScreen
import com.zipstats.app.ui.repairs.RepairsScreen
import com.zipstats.app.ui.routes.RoutesScreen
import com.zipstats.app.ui.statistics.StatisticsScreen
import com.zipstats.app.ui.theme.ColorTheme
import com.zipstats.app.ui.theme.ThemeMode
import com.zipstats.app.ui.tracking.TrackingScreen
import com.zipstats.app.navigation.Screen

@Composable
fun NavGraph(
    navController: NavHostController,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    currentColorTheme: ColorTheme,
    onColorThemeChange: (ColorTheme) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    pureBlackOledEnabled: Boolean,
    onPureBlackOledChange: (Boolean) -> Unit,
    authViewModel: com.zipstats.app.ui.auth.AuthViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    
    // Determinar la pantalla de inicio basada en el estado de autenticación
    val startDestination = when (authState) {
        is com.zipstats.app.ui.auth.AuthState.Success -> Screen.Records.route
        else -> Screen.Login.route
    }
    
    // Usar remember y derivedStateOf para la navegación
    val shouldNavigateToLogin = remember(authState) {
        authState is com.zipstats.app.ui.auth.AuthState.Initial
    }
    
    // Recordar el estado anterior para detectar transiciones
    var previousAuthState by remember { mutableStateOf<com.zipstats.app.ui.auth.AuthState?>(null) }

    // Navegar automáticamente según el estado de autenticación
    LaunchedEffect(shouldNavigateToLogin) {
        if (shouldNavigateToLogin) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
    
    LaunchedEffect(authState) {
        val previous = previousAuthState
        previousAuthState = authState

        if (authState is com.zipstats.app.ui.auth.AuthState.Success && previous !is com.zipstats.app.ui.auth.AuthState.Success) {
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

        composable(Screen.Routes.route) {
            RoutesScreen(navController = navController)
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

        composable(
            route = "${Screen.Profile.route}?openAddVehicle={openAddVehicle}",
            arguments = listOf(
                androidx.navigation.navArgument("openAddVehicle") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val openAddVehicle = backStackEntry.arguments?.getBoolean("openAddVehicle") ?: false
            ProfileScreen(
                navController = navController,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColorEnabled = dynamicColorEnabled,
                onDynamicColorChange = onDynamicColorChange,
                openAddVehicleDialog = openAddVehicle
            )
        }
        
        // Ruta sin parámetros para mantener compatibilidad
        composable(Screen.Profile.route) {
            ProfileScreen(
                navController = navController,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColorEnabled = dynamicColorEnabled,
                onDynamicColorChange = onDynamicColorChange,
                openAddVehicleDialog = false
            )
        }

        composable(Screen.AccountSettings.route) {
            AccountSettingsScreen(
                navController = navController,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                currentColorTheme = currentColorTheme,
                onColorThemeChange = onColorThemeChange,
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

        composable(Screen.Tracking.route) {
            TrackingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRoutes = {
                    navController.navigate(Screen.Routes.route) {
                        // Limpiar el stack de navegación para evitar volver a Tracking
                        popUpTo(Screen.Tracking.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
