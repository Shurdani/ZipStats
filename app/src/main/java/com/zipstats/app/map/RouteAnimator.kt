package com.zipstats.app.map

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.turf.TurfMeasurement

class RouteAnimator(
    private val mapboxMap: MapboxMap,
    private val route: List<Point>,
    private val speeds: List<Float?>, // Velocidades en m/s (puede ser null)
    private val onMarkerPositionChanged: (Point, bearing: Double, progress: Float, traveledPoints: List<Point>, currentSpeedKmh: Double) -> Unit,
    private val onAnimationEnd: (() -> Unit)? = null // Callback cuando la animación termine
) {

    var isFollowingCamera: Boolean = true
    var animationSpeedFactor = 2.0 // Velocidad por defecto 2x para rutas largas 
    
    private var animator: ValueAnimator? = null
    private val interpolator = LinearInterpolator()
    
    // ESTADO
    private var lastCameraBearing: Double? = null
    private var currentIndex: Float = 0f // Guardamos por dónde vamos
    private var currentZoom: Double = 15.0 // Zoom actual interpolado
    private var smoothedSpeed: Double = 15.0 // Velocidad suavizada con low-pass filter
    
    // Verificar si la animación ha terminado
    fun isAnimationComplete(): Boolean {
        if (route.size < 2) return true
        return currentIndex >= (route.size - 1).toFloat() - 0.01f // Con un pequeño margen para errores de punto flotante
    }
    
    // Distancias acumuladas para velocidad constante
    private val cumulativeDistances: List<Double> by lazy {
        val distances = mutableListOf<Double>()
        distances.add(0.0)
        for (i in 1 until route.size) {
            val segmentDistance = TurfMeasurement.distance(route[i - 1], route[i], "meters")
            distances.add(distances.last() + segmentDistance)
        }
        distances
    }

    fun startAnimation(startFromIndex: Float = 0f) {
        if (route.size < 2) return
        stopAnimation(resetIndex = false) // Paramos, pero no reseteamos el índice manual

        this.currentIndex = startFromIndex

        // Calculamos distancia total en metros
        val totalDistanceM = cumulativeDistances.last()
        val totalDistanceKm = totalDistanceM / 1000.0
        
        // Duración base ajustada por el factor de velocidad (velocidad constante)
        // 6000ms por km = ~60 km/h de velocidad de animación
        val baseDuration = (totalDistanceKm * 6000L / animationSpeedFactor).toLong().coerceIn(3000L, 90000L)
        
        // Ajustamos la duración según lo que falta por recorrer (basado en distancia)
        val startDistance = if (startFromIndex > 0) {
            cumulativeDistances[startFromIndex.toInt().coerceAtMost(route.size - 1)]
        } else {
            0.0
        }
        val remainingDistance = totalDistanceM - startDistance
        val progressPercent = if (totalDistanceM > 0) (startDistance / totalDistanceM).toFloat() else 0f
        val remainingDuration = (baseDuration * (1f - progressPercent)).toLong()

        if (remainingDuration <= 0 || remainingDistance <= 0) return

        // Inicializamos el bearing con el primer tramo
        lastCameraBearing = if (route.size > 1) TurfMeasurement.bearing(route[0], route[1]) else 0.0
        
        // Inicializar zoom y velocidad suavizada con el primer valor
        if (startFromIndex == 0f) {
            val initialSpeed = calculateCurrentSpeed(0)
            smoothedSpeed = initialSpeed
            currentZoom = smartZoom(initialSpeed)
        }

        // Animamos por distancia acumulada, no por índice
        animator = ValueAnimator.ofFloat(startDistance.toFloat(), totalDistanceM.toFloat()).apply {
            duration = remainingDuration
            interpolator = this@RouteAnimator.interpolator

            addUpdateListener { anim ->
                val currentDistance = anim.animatedValue as Float
                
                // Encontrar el segmento correspondiente basado en distancia
                val segmentIndex = findSegmentIndex(currentDistance.toDouble())
                val i = segmentIndex.first
                val segmentProgress = segmentIndex.second
                
                if (i >= route.size - 1) {
                    // Llegamos al final
                    val finalPos = route.last()
                    val finalBearing = if (route.size > 1) {
                        TurfMeasurement.bearing(route[route.size - 2], route.last())
                    } else 0.0
                    val finalSpeed = calculateCurrentSpeed(route.size - 1)
                    val traveledPoints = route.take(route.size)
                    onMarkerPositionChanged(finalPos, finalBearing, 1.0f, traveledPoints, finalSpeed)
                    if (isFollowingCamera) {
                        moveCameraCinematic(finalPos, lastCameraBearing ?: finalBearing, finalSpeed)
                    }
                    this@RouteAnimator.currentIndex = (route.size - 1).toFloat()
                    return@addUpdateListener
                }

                // 1. POSICIÓN (interpolación basada en distancia, no en índice)
                val p1 = route[i]
                val p2 = route[i + 1]
                val lat = p1.latitude() + (p2.latitude() - p1.latitude()) * segmentProgress
                val lon = p1.longitude() + (p2.longitude() - p1.longitude()) * segmentProgress
                val currentPos = Point.fromLngLat(lon, lat)

                // 2. RUMBO
                val lookAheadIndex = (i + 8).coerceAtMost(route.size - 1) // Aumentado de 4 a 8 para más estabilidad
                val lookAheadPoint = route[lookAheadIndex]
                val markerBearing = TurfMeasurement.bearing(currentPos, lookAheadPoint)

                // 3. CÁMARA SUAVE (más agresiva para mayor estabilidad)
                val routeBearing = TurfMeasurement.bearing(currentPos, lookAheadPoint)
                val sideOffset = 60.0 
                val targetCameraBearing = routeBearing + sideOffset
                // Factor reducido de 0.05f a 0.02f para suavizado más agresivo (más estable)
                val smoothedCameraBearing = interpolateAngle(lastCameraBearing ?: targetCameraBearing, targetCameraBearing, 0.02f)
                lastCameraBearing = smoothedCameraBearing

                // 4. PROGRESS (basado en distancia)
                val globalProgress = (currentDistance / totalDistanceM).toFloat().coerceIn(0f, 1f)
                this@RouteAnimator.currentIndex = i + segmentProgress

                // 5. TRAIL: Puntos recorridos hasta ahora (incluyendo el punto actual interpolado)
                val traveledPoints = if (i > 0) {
                    route.take(i) + currentPos
                } else {
                    listOf(currentPos)
                }

                // 6. VELOCIDAD ACTUAL (en km/h)
                val currentSpeedKmh = calculateCurrentSpeed(i, segmentProgress)

                onMarkerPositionChanged(currentPos, markerBearing, globalProgress, traveledPoints, currentSpeedKmh)

                if (isFollowingCamera) {
                    moveCameraCinematic(currentPos, smoothedCameraBearing, currentSpeedKmh)
                }
            }
            
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onAnimationEnd?.invoke()
                }
            })
            
            start()
        }
    }
    
    fun toggleSpeed() {
        // Alternar entre 2x (velocidad base) y 4x (velocidad doble)
        // Desde la perspectiva del usuario: "1x" (2x real) <-> "2x" (4x real)
        animationSpeedFactor = if (animationSpeedFactor == 2.0) 4.0 else 2.0
        // Reiniciar desde el punto actual con la nueva velocidad
        if (animator?.isRunning == true) {
            startAnimation(startFromIndex = currentIndex)
        }
    }

    fun pauseAnimation() {
        animator?.cancel()
    }

    fun resumeAnimation() {
        // Convertir índice actual a distancia para reanudar
        val currentDistance = if (currentIndex > 0 && currentIndex < route.size) {
            val i = currentIndex.toInt()
            val t = currentIndex - i
            if (i < cumulativeDistances.size - 1) {
                val segmentStart = cumulativeDistances[i]
                val segmentEnd = cumulativeDistances[i + 1]
                segmentStart + (segmentEnd - segmentStart) * t
            } else {
                cumulativeDistances.last()
            }
        } else {
            0.0
        }
        startAnimation(startFromIndex = currentIndex)
    }

    fun stopAnimation(resetIndex: Boolean = true) {
        animator?.cancel()
        animator = null
        if (resetIndex) currentIndex = 0f
    }
    
    // Encuentra el segmento y el progreso dentro del segmento basado en distancia acumulada
    private fun findSegmentIndex(distance: Double): Pair<Int, Float> {
        if (distance <= 0.0) return Pair(0, 0f)
        if (distance >= cumulativeDistances.last()) {
            return Pair(route.size - 2, 1f)
        }
        
        // Buscar el segmento que contiene esta distancia
        for (i in 0 until cumulativeDistances.size - 1) {
            val segmentStart = cumulativeDistances[i]
            val segmentEnd = cumulativeDistances[i + 1]
            
            if (distance >= segmentStart && distance <= segmentEnd) {
                val segmentLength = segmentEnd - segmentStart
                val progress = if (segmentLength > 0) {
                    ((distance - segmentStart) / segmentLength).toFloat()
                } else {
                    0f
                }
                return Pair(i, progress.coerceIn(0f, 1f))
            }
        }
        
        // Fallback
        return Pair(0, 0f)
    }

    // 1) Low-Pass Filter para suavizar velocidad (elimina saltos) - MÁS SUAVE
    private fun smoothSpeed(newSpeed: Double): Double {
        val alpha = 0.05 // cuanto más bajo, más suave (reducido de 0.15 para máximo suavizado)
        smoothedSpeed = alpha * newSpeed + (1 - alpha) * smoothedSpeed
        return smoothedSpeed
    }
    
    // 2) Interpolación continua de zoom (no saltar entre valores) - MÁS SUAVE
    private fun interpolateZoom(targetZoom: Double): Double {
        val factor = 0.03 // suavidad mayor → número más pequeño (reducido de 0.08 para máximo suavizado)
        currentZoom = currentZoom + (targetZoom - currentZoom) * factor
        return currentZoom
    }
    
    // Función para calcular zoom inteligente según velocidad - RANGO MÍNIMO
    private fun smartZoom(speedKmh: Double): Double {
        // Rango muy reducido (15.0 a 15.5) para evitar cambios bruscos que mareen
        return when {
            speedKmh < 5.0   -> 15.3  // muy lento - zoom ligeramente cercano
            speedKmh < 15.0  -> 15.2
            speedKmh < 25.0  -> 15.1
            else             -> 15.0  // rápido - zoom ligeramente lejano (rango mínimo)
        }
    }

    private fun moveCameraCinematic(target: Point, bearing: Double, currentSpeedKmh: Double) {
        // 1) Suavizar velocidad con low-pass filter
        val filteredSpeed = smoothSpeed(currentSpeedKmh)
        
        // 2) Calcular zoom objetivo basado en velocidad filtrada
        val targetZoom = smartZoom(filteredSpeed)
        
        // 3) Interpolar zoom continuamente (evita saltos)
        val finalZoom = interpolateZoom(targetZoom)
        
        val camera = CameraOptions.Builder()
            .center(target)
            .bearing(bearing)
            .pitch(55.0) // Vista Relive
            .zoom(finalZoom)
            .padding(com.mapbox.maps.EdgeInsets(0.0, 100.0, 300.0, 0.0))
            .build()
        
        // 3) Animación instantánea pero suave (sin easeTo agresivo)
        // setCamera directo con interpolación manual → la cámara fluye perfecta
        mapboxMap.setCamera(camera)
    }
    
    // Calcula la velocidad actual en km/h basada en el índice y progreso
    private fun calculateCurrentSpeed(segmentIndex: Int, segmentProgress: Float = 0f): Double {
        if (speeds.isEmpty() || segmentIndex >= speeds.size) {
            // Si no hay datos de velocidad, calcular basado en distancia/tiempo del segmento
            return 15.0 // Velocidad por defecto
        }
        
        // Obtener velocidad del punto actual (en m/s)
        val speedMs = speeds[segmentIndex] ?: run {
            // Si no hay velocidad en este punto, usar la del siguiente o anterior
            val nextSpeed = speeds.getOrNull(segmentIndex + 1)
            val prevSpeed = speeds.getOrNull(segmentIndex - 1)
            when {
                nextSpeed != null -> nextSpeed
                prevSpeed != null -> prevSpeed
                else -> 4.17f // ~15 km/h por defecto (4.17 m/s)
            }
        }
        
        // Convertir m/s a km/h
        return speedMs * 3.6
    }

    // MATEMÁTICAS: Interpola suavemente entre dos ángulos manejando el salto 360->0
    private fun interpolateAngle(current: Double, target: Double, factor: Float): Double {
        var diff = target - current
        // Normalizar la diferencia para tomar el camino más corto
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        
        // Aplicar el factor de suavizado
        return current + (diff * factor)
    }
}