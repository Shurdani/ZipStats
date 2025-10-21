package com.zipstats.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zipstats.app.navigation.Screen

@Composable
fun BottomNavigation(
    navController: NavController
) {
    val items = listOf(
        Screen.Records,
        Screen.Statistics,
        Screen.Routes,
        Screen.Achievements,
        Screen.Profile
    )

    NavigationBar(
        modifier = Modifier.height(64.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = when (screen) {
                            Screen.Records -> Icons.AutoMirrored.Filled.List
                            Screen.Statistics -> Icons.Default.BarChart
                            Screen.Routes -> Icons.Default.Route
                            Screen.Achievements -> Icons.Default.Star
                            Screen.Profile -> Icons.Default.Person
                            else -> Icons.AutoMirrored.Filled.List
                        },
                        contentDescription = null
                    )
                },
                label = { Text(text = when (screen) {
                    Screen.Records -> "Registros"
                    Screen.Statistics -> "Stats"
                    Screen.Routes -> "Rutas"
                    Screen.Achievements -> "Logros"
                    Screen.Profile -> "Perfil"
                    else -> ""
                }) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
} 