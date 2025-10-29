package com.zipstats.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Records : Screen("records", "Historial de Viajes", Icons.Default.History)
    object Statistics : Screen("statistics", "Estadísticas", Icons.Default.BarChart)
    object Routes : Screen("routes", "Rutas", Icons.Default.Route)
    object Repairs : Screen("repairs", "Reparaciones", Icons.Default.Build)
    object Achievements : Screen("achievements", "Logros", Icons.Default.EmojiEvents)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object AccountSettings : Screen("account_settings", "Ajustes", Icons.Default.Settings)
    object ScootersManagement : Screen("scooters_management", "Mis Patinetes", Icons.Default.Build)
    object ScooterDetail : Screen("scooter_detail", "Detalle del Patinete", Icons.Default.Build)
    object Tracking : Screen("tracking", "Seguimiento GPS", Icons.Default.GpsFixed)
    object Login : Screen("login", "Iniciar sesión", Icons.AutoMirrored.Filled.Login)
    object Register : Screen("register", "Registro", Icons.Default.PersonAdd)
    object EmailVerification : Screen("email_verification", "Verificar Email", Icons.Default.Email)

    companion object {
        val bottomNavItems = listOf(
            Records,
            Statistics,
            Routes,
            Achievements,
            Profile
        )
    }
} 