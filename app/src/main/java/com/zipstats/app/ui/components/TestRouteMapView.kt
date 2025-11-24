package com.zipstats.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint

@Composable
fun TestRouteMapView(
    modifier: Modifier = Modifier
) {
    // Crear una ruta de prueba con puntos en Madrid
    val testRoute = remember {
        Route(
            id = "test",
            userId = "test",
            scooterId = "test",
            scooterName = "Test Scooter",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 3600000, // 1 hora después
            totalDistance = 5.2,
            totalDuration = 3600000,
            averageSpeed = 15.6,
            maxSpeed = 25.0,
            points = listOf(
                RoutePoint(40.4168, -3.7038, System.currentTimeMillis()), // Madrid centro
                RoutePoint(40.4178, -3.7048, System.currentTimeMillis() + 300000),
                RoutePoint(40.4188, -3.7058, System.currentTimeMillis() + 600000),
                RoutePoint(40.4198, -3.7068, System.currentTimeMillis() + 900000),
                RoutePoint(40.4208, -3.7078, System.currentTimeMillis() + 1200000),
                RoutePoint(40.4218, -3.7088, System.currentTimeMillis() + 1500000),
                RoutePoint(40.4228, -3.7098, System.currentTimeMillis() + 1800000),
                RoutePoint(40.4238, -3.7108, System.currentTimeMillis() + 2100000),
                RoutePoint(40.4248, -3.7118, System.currentTimeMillis() + 2400000),
                RoutePoint(40.4258, -3.7128, System.currentTimeMillis() + 2700000)
            ),
            isCompleted = true,
            notes = "Ruta de prueba"
        )
    }
    
    RouteMapView(
        route = testRoute,
        modifier = modifier
    )
}
