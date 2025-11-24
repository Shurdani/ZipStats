package com.zipstats.app.navigation

// IMPORTANTE: Usamos la navegación estándar de Compose, no Accompanist si ya estamos en 2.8.5+
// Si usas 2.8.5, 'AnimatedNavHost' ya es parte de 'androidx.navigation.compose.NavHost'
// Pero si tu proyecto aún depende de Accompanist para algo, mantenlo.
// Voy a asumir que usas la librería oficial androidx.navigation.compose.NavHost
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    // authViewModel: com.zipstats.app.ui.auth.AuthViewModel // No se usa en el código, se puede quitar si quieres
) {
    // Usamos NavHost estándar (que soporta animaciones desde la versión 2.7.0+)
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

        // 5. Records (Home)
        composable(route = Screen.Records.route) {
            RecordsHistoryScreen(navController = navController)
        }

        // 6. Statistics
        composable(route = Screen.Statistics.route) {
            StatisticsScreen(navController = navController)
        }

        // 7. Routes
        composable(route = Screen.Routes.route) {
            RoutesScreen(navController = navController)
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

        // 10. Profile (con argumentos)
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

        // 11. Profile (sin argumentos - compatibilidad)
        composable(route = Screen.Profile.route) {
            ProfileScreen(
                navController = navController,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColorEnabled = dynamicColorEnabled,
                onDynamicColorChange = onDynamicColorChange,
                openAddVehicleDialog = false
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
                        // Limpiar historial hasta Routes
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