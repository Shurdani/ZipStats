package com.zipstats.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zipstats.app.di.AppOverlayRepositoryEntryPoint
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.ui.achievements.AchievementsScreen
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
import com.zipstats.app.ui.splash.SplashScreen
import com.zipstats.app.ui.statistics.StatisticsScreen
import com.zipstats.app.ui.theme.ColorTheme
import com.zipstats.app.ui.theme.ThemeMode
import com.zipstats.app.ui.tracking.TrackingScreen
import dagger.hilt.android.EntryPointAccessors

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
    onPureBlackOledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    // OPTIMIZACIÓN: Obtener el repositorio UNA SOLA VEZ para todo el grafo
    // En lugar de obtenerlo dentro de cada composable repetidamente.
    val appOverlayRepository: AppOverlayRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppOverlayRepositoryEntryPoint::class.java
        ).appOverlayRepository()
    }
    
    // Este estado se comparte y observa una sola vez
    val vehiclesReady by appOverlayRepository.vehiclesReady.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // 1. Splash
        composable(Screen.Splash.route) {
            SplashScreen(navController = navController)
        }

        // 2. Login
        composable(route = Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Routes.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onRegisterClick = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        // 3. Register
        composable(route = Screen.Register.route) {
            RegisterScreen(
                navController = navController,
                onRegisterSuccess = {
                    navController.navigate(Screen.Routes.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 4. Email Verification
        composable(route = Screen.EmailVerification.route) {
            EmailVerificationScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.EmailVerification.route) { inclusive = true }
                    }
                }
            )
        }

        // 5. Records
        composable(route = Screen.Records.route) {
            // Usamos la variable 'vehiclesReady' que ya calculamos arriba
            if (vehiclesReady) {
                RecordsHistoryScreen(navController = navController)
            }
        }

        // 6. Statistics
        composable(route = Screen.Statistics.route) {
            if (vehiclesReady) {
                StatisticsScreen(navController = navController)
            }
        }

        // 7. Routes (Home)
        composable(route = Screen.Routes.route) {
            if (vehiclesReady) {
                RoutesScreen(navController = navController)
            }
        }

        // 8. Repairs
        composable(route = Screen.Repairs.route + "/{scooterId}") { backStackEntry ->
            val scooterId = backStackEntry.arguments?.getString("scooterId") ?: ""
            RepairsScreen(navController = navController, scooterId = scooterId)
        }

        // 9. Achievements
        composable(route = Screen.Achievements.route) {
            AchievementsScreen(navController = navController)
        }

        // 10. Profile (UNIFICADO)
        // Este bloque maneja TANTO la navegación normal como la que lleva parámetros
        composable(
            route = "${Screen.Profile.route}?openAddVehicle={openAddVehicle}",
            arguments = listOf(
                navArgument("openAddVehicle") {
                    type = NavType.BoolType
                    defaultValue = false // Esto hace que el argumento sea opcional
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

        // 12. Account Settings
        composable(route = Screen.AccountSettings.route) {
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

        // 13. Scooters Management
        composable(route = Screen.ScootersManagement.route) {
            ScootersManagementScreen(navController = navController)
        }

        // 14. Scooter Detail
        composable(route = Screen.ScooterDetail.route + "/{scooterId}") { backStackEntry ->
            val scooterId = backStackEntry.arguments?.getString("scooterId") ?: ""
            ScooterDetailScreen(
                navController = navController,
                scooterId = scooterId
            )
        }

        // 15. Tracking
        composable(route = Screen.Tracking.route) {
            TrackingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRoutes = {
                    navController.navigate(Screen.Routes.route) {
                        // Limpiar historial hasta Routes para evitar volver atrás al tracking
                        popUpTo(Screen.Routes.route) {
                            inclusive = true
                            saveState = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}