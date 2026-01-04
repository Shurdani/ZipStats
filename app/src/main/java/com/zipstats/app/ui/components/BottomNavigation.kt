package com.zipstats.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
        tonalElevation = 4.dp // Sombra más sutil pero presente
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEachIndexed { index, screen ->
            val isSelected = currentRoute == screen.route
            val alpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.6f,
                animationSpec = tween(durationMillis = 200),
                label = "iconAlpha"
            )
            
            NavigationBarItem(
                icon = {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (screen) {
                                Screen.Records -> Icons.AutoMirrored.Filled.List
                                Screen.Statistics -> Icons.Default.BarChart
                                Screen.Routes -> Icons.Default.Route
                                Screen.Achievements -> Icons.Default.Star
                                Screen.Profile -> Icons.Default.Person
                                else -> Icons.AutoMirrored.Filled.List
                            },
                            contentDescription = null,
                            modifier = Modifier.alpha(alpha)
                        )
                        // Indicador superior para pestaña activa
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-8).dp)
                                    .width(32.dp)
                                    .height(3.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                                    )
                            )
                        }
                    }
                },
                label = { 
                    ZipStatsText(
                        text = when (screen) {
                            Screen.Records -> "Registros"
                            Screen.Statistics -> "Stats"
                            Screen.Routes -> "Rutas"
                            Screen.Achievements -> "Logros"
                            Screen.Profile -> "Perfil"
                            else -> ""
                        },
                        modifier = Modifier.alpha(alpha),
                        maxLines = 1
                    ) 
                },
                selected = isSelected,
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