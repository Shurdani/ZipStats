package com.zipstats.app.tracking

import android.util.Log
import com.zipstats.app.model.Route
import com.zipstats.app.model.RoutePoint
import com.zipstats.app.model.RouteWeatherDecision
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.utils.DateUtils
import com.zipstats.app.utils.LocationUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class RouteSaveUseCase @Inject constructor(
    private val routeRepository: RouteRepository,
    private val recordRepository: RecordRepository,
    private val weatherMonitoring: WeatherMonitoringUseCase,
) {

    data class SaveResult(
        val message: String,
        val route: Route,
    )

    /**
     * Persiste una ruta completada: snapshot climático, creación, finalización y guardado en Firebase.
     * Opcionalmente añade la distancia al historial de registros del vehículo.
     */
    suspend fun saveCompletedRoute(
        points: List<RoutePoint>,
        scooter: Scooter,
        startTime: Long,
        endTime: Long,
        notes: String,
        timeInMotion: Long,
        weatherDecision: RouteWeatherDecision,
        addToRecords: Boolean,
    ): Result<SaveResult> {
        return try {
            if (points.isEmpty()) {
                return Result.failure(Exception("No se registraron puntos GPS"))
            }

            Log.d(TAG, "Guardando ruta con ${points.size} puntos GPS")

            weatherMonitoring.awaitWeatherLoadedIfNeeded()

            if (weatherMonitoring.hasActiveBadges()) {
                weatherMonitoring.fetchFinalWeatherSnapshot(points.last())
            }

            val weatherSnapshot = weatherMonitoring.captureRouteWeatherSnapshot()

            val baseRoute = routeRepository.createRouteFromPoints(
                points = points,
                scooterId = scooter.id,
                scooterName = scooter.nombre,
                startTime = startTime,
                endTime = endTime,
                notes = notes,
                timeInMotion = timeInMotion,
                vehicleType = scooter.vehicleType,
            )

            val finalRoute = routeRepository.finalizeRouteWithWeather(
                baseRoute = baseRoute,
                snap = weatherSnapshot,
                decision = weatherDecision,
            )

            val saveResult = routeRepository.saveRoute(finalRoute)
            if (saveResult.isFailure) {
                return Result.failure(
                    saveResult.exceptionOrNull() ?: Exception("Error desconocido al guardar")
                )
            }

            Log.d(TAG, "✅ Ruta guardada con éxito: ${finalRoute.id}")
            weatherMonitoring.resetAll()

            val distanceText = LocationUtils.formatNumberSpanish(finalRoute.totalDistance.roundToOneDecimal())
            var message = "Ruta guardada: $distanceText km"

            if (addToRecords) {
                val addedToRecords = addRouteToRecords(scooter, finalRoute)
                message += if (addedToRecords) {
                    "\nDistancia añadida a registros correctamente"
                } else {
                    "\nNo se pudo añadir a registros. Puedes añadirla desde el detalle de la ruta."
                }
            }

            Result.success(SaveResult(message = message, route = finalRoute))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al guardar ruta: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun addRouteToRecords(scooter: Scooter, route: Route): Boolean {
        return try {
            Log.d(TAG, "Intentando añadir ruta a registros para patinete: ${scooter.nombre}")

            val endMs = route.endTime ?: route.startTime
            val formattedDate = DateUtils.formatForApiFromMillis(endMs)

            val result = recordRepository.addRecordFromRouteDistance(
                vehiculo = scooter.nombre,
                distanceKm = route.totalDistance,
                fecha = formattedDate,
                scooterId = scooter.id.takeIf { it.isNotEmpty() },
            )
            result.onSuccess {
                Log.d(TAG, "✅ Registro guardado: ${scooter.nombre} +${route.totalDistance} km (ruta)")
            }
            result.onFailure { e ->
                Log.e(TAG, "❌ Error al guardar registro desde ruta: ${e.message}", e)
            }
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al procesar registros de kilometraje: ${e.message}", e)
            false
        }
    }

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }

    companion object {
        private const val TAG = "RouteSaveUseCase"
    }
}
