package com.zipstats.app.ui.routes

// Eliminado snapshot en tiempo real; usaremos Static Maps HTTP para evitar cierres
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import com.zipstats.app.model.Route
import com.zipstats.app.model.Scooter
import com.zipstats.app.repository.AppOverlayRepository
import com.zipstats.app.repository.RecordRepository
import com.zipstats.app.repository.RouteRepository
import com.zipstats.app.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val scooterRepository: VehicleRepository,
    private val recordRepository: RecordRepository,
    private val appOverlayRepository: AppOverlayRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // 1. INPUTS (Lo que el usuario o la red cambian)
    private val _selectedScooter = MutableStateFlow<String?>(null)
    val selectedScooter: StateFlow<String?> = _selectedScooter.asStateFlow()

    // Cargamos los patinetes del usuario (esto está bien como estaba)
    private val _userScooters = MutableStateFlow<List<Scooter>>(emptyList())
    val userScooters: StateFlow<List<Scooter>> = _userScooters.asStateFlow()

    // Mensajes de error
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 2. LÓGICA REACTIVA (El corazón de la optimización)

    // Obtenemos el flujo "Maestro" del repositorio.
    // Suponemos que getUserRoutesFlow() retorna un Flow<List<Route>> que se mantiene actualizado.
    private val allRoutesFlow = routeRepository.getUserRoutesFlow()
        .catch { e ->
            _errorMessage.value = e.message
            emit(emptyList())
        }
        .flowOn(Dispatchers.IO) // OPTIMIZACIÓN 1: La recolección del repo va en hilo IO

    // 3. OUTPUTS (Lo que ve la UI)

    val uiState: StateFlow<RoutesUiState> = combine(
        allRoutesFlow,
        _selectedScooter
    ) { allRoutes: List<Route>, selectedId: String? ->

        // ESTE BLOQUE AHORA SE EJECUTARÁ EN BACKGROUND
        // No bloqueará la UI aunque haya 10,000 rutas
        val filteredList = if (selectedId == null) {
            allRoutes
        } else {
            allRoutes.filter { it.scooterId == selectedId }
        }

        if (filteredList.isEmpty() && allRoutes.isEmpty()) {
            RoutesUiState.Success
        } else {
            RoutesUiState.Success
        }

    }
        .flowOn(Dispatchers.Default) // OPTIMIZACIÓN 2: Mueve el cálculo de arriba a CPU Background
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoutesUiState.Loading
    )

    // Esta es la lista que tu UI (RecyclerView) debe observar
    val routes: StateFlow<List<Route>> = combine(
        allRoutesFlow,
        _selectedScooter
    ) { allRoutes, selectedId ->
        if (selectedId == null) allRoutes
        else allRoutes.filter { it.scooterId == selectedId } // O it.vehicle == selectedId
    }
        .flowOn(Dispatchers.Default) // OPTIMIZACIÓN 2: Mueve el cálculo de arriba a CPU Background
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadUserScooters()
        // Ya no hace falta llamar a loadRoutes() explícitamente porque
        // 'val routes' usa stateIn, lo que inicia la suscripción automáticamente.
    }

    // --- ACCIONES DEL USUARIO ---

    fun setSelectedScooter(scooterId: String?) {
        // ¡Súper simple! Solo actualizamos el ID.
        // La magia del 'combine' arriba se encarga de filtrar y actualizar 'routes'.
        _selectedScooter.value = scooterId
    }

    fun loadUserScooters() {
        viewModelScope.launch {
            try {
                val scooters = scooterRepository.getUserScooters()
                _userScooters.value = scooters
                // Marcar vehículos como listos después de cargarlos
                appOverlayRepository.setVehiclesReady(true)
            } catch (e: Exception) {
                // Error silencioso, pero aún marcamos como ready para no bloquear la app
                appOverlayRepository.setVehiclesReady(true)
            }
        }
    }

    fun deleteRoute(routeId: String) {
        viewModelScope.launch {
            try {
                // Al borrar en el repositorio, si Firebase emite cambios,
                // 'allRoutesFlow' se actualizará solo, y 'routes' se recalculará solo.
                val result = routeRepository.deleteRoute(routeId)
                if (!result.isSuccess) {
                    _errorMessage.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Verifica si una ruta ya fue añadida a los registros
     */
    suspend fun isRouteAddedToRecords(route: Route): Boolean {
        return try {
            val allRecords = recordRepository.getRecords().first()
            val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val formattedDate = com.zipstats.app.utils.DateUtils.formatForApi(routeDate)
            
            // Buscar si hay un registro del mismo patinete en la misma fecha
            // con una distancia que incluya la distancia de la ruta
            val recordsForScooter = allRecords.filter { record ->
                record.patinete == route.scooterName && record.fecha == formattedDate 
            }
            
            // Si hay registros del mismo día, verificar si la distancia coincide
            recordsForScooter.any { record ->
                val difference = record.diferencia
                kotlin.math.abs(difference - route.totalDistance) < 0.1 // Tolerancia de 0.1 km
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Añade una ruta a los registros
     */
    fun addRouteToRecords(route: Route) {
        viewModelScope.launch {
            try {
                // Obtener la fecha de la ruta
                val routeDate = java.time.Instant.ofEpochMilli(route.startTime)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                val formattedDate = com.zipstats.app.utils.DateUtils.formatForApi(routeDate)
                
                // Obtener el último registro del patinete
                val allRecords = recordRepository.getRecords().first()
                val lastRecord = allRecords
                    .filter { record -> record.patinete == route.scooterName }
                    .maxByOrNull { record -> record.fecha }
                
                val newKilometraje = if (lastRecord != null) {
                    lastRecord.kilometraje + route.totalDistance
                } else {
                    route.totalDistance
                }
                
                // Añadir el registro
                recordRepository.addRecord(
                    vehiculo = route.scooterName,
                    kilometraje = newKilometraje,
                    fecha = formattedDate
                ).onSuccess {
                    _message.value = "Ruta añadida a registros: ${String.format("%.1f", route.totalDistance)} km"
                }.onFailure { e ->
                    _errorMessage.value = "Error al añadir a registros: ${e.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al añadir a registros: ${e.message}"
            }
        }
    }
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    fun clearMessage() {
        _message.value = null
    }
    
    /**
     * Comparte una ruta con una imagen del mapa generada usando un MapView real
     * Esta función debe ser llamada desde una Activity/Fragment que tenga acceso al MapView
     */
    fun shareRouteWithRealMap(route: Route, mapView: com.mapbox.maps.MapView) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    // Tomar snapshot directamente
                    tomarSnapshotYCompartir(route, mapView)
                }
            } catch (e: Exception) {
                Log.e("RoutesVM", "Error al configurar mapa: ${e.message}", e)
                _errorMessage.value = "Error al configurar mapa: ${e.message}"
            }
        }
    }
    
    /**
     * Toma la snapshot del mapa y la comparte
     */
    private fun tomarSnapshotYCompartir(route: Route, mapView: com.mapbox.maps.MapView) {
        val mapboxMap = mapView.getMapboxMap()
        
        // En Mapbox, snapshot se llama desde MapView
        mapView.snapshot { snapshotBitmap ->
            if (snapshotBitmap == null) {
                _errorMessage.value = "Error al generar imagen del mapa"
                return@snapshot
            }

            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        // ¡¡APLICAR REDIMENSIONADO INMEDIATAMENTE!!
                        val mapaReducido = redimensionarBitmap(snapshotBitmap, 1080)
                        
                        // Crear imagen final combinando el mapa con la información de la ruta
                        val finalBitmap = createFinalRouteImage(route, mapaReducido)
                        
                        // Crear directorio temporal para compartir
                        val shareDir = File(context.cacheDir, "shared_routes")
                        if (!shareDir.exists()) {
                            shareDir.mkdirs()
                        }
                        
                        // Crear archivo temporal
                        val imageFile = File(shareDir, "route_${route.id}.png")
                        
                        // Guardar bitmap como PNG
                        FileOutputStream(imageFile).use { out ->
                            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        
                        // Crear URI con FileProvider
                        val imageUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            imageFile
                        )
                        
                        // Crear mensaje para compartir
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        val date = dateFormat.format(Date(route.startTime))
                        val shareMessage = """
                            🛴 Mi ruta en ${route.scooterName}
                            
                            📅 Fecha: $date
                            📍 Distancia: ${String.format("%.1f", route.totalDistance)} km
                            ⏱️ Duración: ${route.durationFormatted}
                            ⚡ Velocidad media: ${String.format("%.1f", route.averageSpeed)} km/h
                            🚀 Velocidad máxima: ${String.format("%.1f", route.maxSpeed)} km/h
                            
                            #ZipStats
                        """.trimIndent()
                        
                        // Crear intent para compartir
                        withContext(Dispatchers.Main) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, imageUri)
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = android.content.ClipData.newUri(
                                    context.contentResolver,
                                    "shared_route",
                                    imageUri
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            // Conceder permisos de lectura a todas las apps objetivo
                            val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
                            if (resInfoList.isEmpty()) {
                                _errorMessage.value = "No hay apps para compartir la imagen"
                                return@withContext
                            }
                            for (resolveInfo in resInfoList) {
                                val packageName = resolveInfo.activityInfo.packageName
                                context.grantUriPermission(
                                    packageName,
                                    imageUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }

                            // Abrir chooser
                            val chooser = Intent.createChooser(intent, "Compartir ruta").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                _errorMessage.value = "No se pudo abrir el selector de compartir: ${e.message}"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RoutesVM", "Error al procesar snapshot: ${e.message}", e)
                    _errorMessage.value = "Error al procesar imagen: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Crea la imagen final combinando el mapa con la información de la ruta
     */
    private fun createFinalRouteImage(route: Route, mapBitmap: Bitmap): Bitmap {
        val width = 1080
        val height = 1920
        val mapHeight = (height * 0.7f).toInt()

        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        canvas.drawColor(Color.WHITE)
        
        // Centrar el mapa en el área asignada
        val mapWidth = mapBitmap.width
        val mapHeightActual = mapBitmap.height
        val scaleX = width.toFloat() / mapWidth
        val scaleY = mapHeight.toFloat() / mapHeightActual
        val scale = minOf(scaleX, scaleY)
        
        val scaledWidth = (mapWidth * scale).toInt()
        val scaledHeight = (mapHeightActual * scale).toInt()
        val offsetX = (width - scaledWidth) / 2
        val offsetY = (mapHeight - scaledHeight) / 2
        
        val scaledBitmap = Bitmap.createScaledBitmap(mapBitmap, scaledWidth, scaledHeight, true)
        canvas.drawBitmap(scaledBitmap, offsetX.toFloat(), offsetY.toFloat(), null)
        
        // Dibujar información de la ruta
        drawRouteInfo(canvas, route, width, mapHeight)
        
        return finalBitmap
    }

    /**
     * Comparte una ruta con una imagen del mapa generada
     */
    fun shareRouteWithMap(route: Route) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Generar imagen del mapa con la ruta
                    val bitmap = generateRouteMapImage(route)
                    
                    // Crear directorio temporal para compartir
                    val shareDir = File(context.cacheDir, "shared_routes")
                    if (!shareDir.exists()) {
                        shareDir.mkdirs()
                    }
                    
                    // Crear archivo temporal
                    val imageFile = File(shareDir, "route_${route.id}.png")
                    
                    // Guardar bitmap como PNG
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    
                    // Crear URI con FileProvider
                    val imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        imageFile
                    )
                    
                    // Crear mensaje para compartir
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val date = dateFormat.format(Date(route.startTime))
                    val shareMessage = """
                        🛴 Mi ruta en ${route.scooterName}
                        
                        📅 Fecha: $date
                        📍 Distancia: ${String.format("%.1f", route.totalDistance)} km
                        ⏱️ Duración: ${route.durationFormatted}
                        ⚡ Velocidad media: ${String.format("%.1f", route.averageSpeed)} km/h
                        🚀 Velocidad máxima: ${String.format("%.1f", route.maxSpeed)} km/h
                        
                        #ZipStats
                    """.trimIndent()
                    
                    // Crear intent para compartir
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*" // más compatible
                            putExtra(Intent.EXTRA_STREAM, imageUri)
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            // Usar ClipData para que Android 13+ propague permisos correctamente
                            clipData = android.content.ClipData.newUri(
                                context.contentResolver,
                                "shared_route",
                                imageUri
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        // Conceder permisos de lectura a todas las apps objetivo
                        val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
                        if (resInfoList.isEmpty()) {
                            _errorMessage.value = "No hay apps para compartir la imagen"
                            return@withContext
                        }
                        for (resolveInfo in resInfoList) {
                            val packageName = resolveInfo.activityInfo.packageName
                            context.grantUriPermission(
                                packageName,
                                imageUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }

                        // Volver al chooser directo (era estable) con permisos
                        val chooser = Intent.createChooser(intent, "Compartir ruta").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(chooser)
                        } catch (e: Exception) {
                            _errorMessage.value = "No se pudo abrir el selector de compartir: ${'$'}{e.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RoutesVM", "Error al compartir ruta: ${e.message}", e)
                _errorMessage.value = "Error al compartir ruta: ${e.message}"
            }
        }
    }
    
    /**
     * Genera una imagen del mapa con la ruta dibujada
     * Formato vertical (9:16) para móviles
     */
    private fun generateRouteMapImage(route: Route): Bitmap {
        // Dimensiones verticales optimizadas para móviles (1080x1920)
        val width = 1080
        val height = 1920
        val mapHeight = (height * 0.7f).toInt() // 70% para el mapa

        val routePoints = route.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (routePoints.isEmpty()) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        // TODO: Implementar Mapbox Static Images API para generar mapas estáticos
        // Por ahora, usar fallback
        val mapBitmap: Bitmap? = null

        // Componer imagen final
        val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)
        canvas.drawColor(Color.WHITE)
        
        if (mapBitmap != null) {
            // Centrar el mapa en el área asignada
            val mapWidth = mapBitmap.width
            val mapHeightActual = mapBitmap.height
            val scaleX = width.toFloat() / mapWidth
            val scaleY = mapHeight.toFloat() / mapHeightActual
            val scale = minOf(scaleX, scaleY)
            
            val scaledWidth = (mapWidth * scale).toInt()
            val scaledHeight = (mapHeightActual * scale).toInt()
            val offsetX = (width - scaledWidth) / 2
            val offsetY = (mapHeight - scaledHeight) / 2
            
            val scaledBitmap = Bitmap.createScaledBitmap(mapBitmap, scaledWidth, scaledHeight, true)
            canvas.drawBitmap(scaledBitmap, offsetX.toFloat(), offsetY.toFloat(), null)
        } else {
            // Fallback mejorado con mejor centrado
            drawFallbackMap(canvas, routePoints, width, mapHeight)
        }

        // Dibujar información de la ruta
        drawRouteInfo(canvas, route, width, mapHeight)

        return finalBitmap
    }
    
    /**
     * Redimensiona un Bitmap a un tamaño máximo sin deformarlo.
     * @param bitmapOriginal El bitmap que quieres reducir.
     * @param tamanoMaximo El ancho o alto máximo que quieres que tenga (en píxeles).
     * @return El nuevo bitmap redimensionado.
     */
    private fun redimensionarBitmap(bitmapOriginal: Bitmap, tamanoMaximo: Int): Bitmap {
        val anchoOriginal = bitmapOriginal.width
        val altoOriginal = bitmapOriginal.height

        // Si ya es más pequeño que el máximo, no hacemos nada
        if (anchoOriginal <= tamanoMaximo && altoOriginal <= tamanoMaximo) {
            return bitmapOriginal
        }

        val ratio = minOf(
            tamanoMaximo.toFloat() / anchoOriginal,
            tamanoMaximo.toFloat() / altoOriginal
        )

        val nuevoAncho = (anchoOriginal * ratio).toInt()
        val nuevoAlto = (altoOriginal * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmapOriginal, nuevoAncho, nuevoAlto, true)
    }
    
    /**
     * Calcula el zoom óptimo para mostrar toda la ruta
     */
    private fun calculateOptimalZoom(routePoints: List<Point>, width: Int, height: Int): Int {
        if (routePoints.isEmpty()) return 14
        val lats = routePoints.map { it.latitude() }
        val lngs = routePoints.map { it.longitude() }
        val latDiff = (lats.maxOrNull() ?: 0.0) - (lats.minOrNull() ?: 0.0)
        val lngDiff = (lngs.maxOrNull() ?: 0.0) - (lngs.minOrNull() ?: 0.0)
        val maxDiff = maxOf(latDiff, lngDiff)
        
        return when {
            maxDiff > 10.0 -> 6
            maxDiff > 5.0 -> 8
            maxDiff > 1.0 -> 10
            maxDiff > 0.5 -> 12
            maxDiff > 0.1 -> 14
            else -> 16
        }
    }
    
    /**
     * Dibuja un mapa de fallback mejorado cuando no se puede cargar el mapa real
     */
    private fun drawFallbackMap(canvas: Canvas, routePoints: List<Point>, width: Int, mapHeight: Int) {
        // Fondo con gradiente sutil que simula un mapa
        val mapPaint = Paint().apply {
            color = Color.rgb(240, 248, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), mapHeight.toFloat(), mapPaint)
        
        // Dibujar líneas de cuadrícula para simular un mapa
        val gridPaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        // Líneas verticales
        for (i in 0..10) {
            val x = (width * i / 10f)
            canvas.drawLine(x, 0f, x, mapHeight.toFloat(), gridPaint)
        }
        
        // Líneas horizontales
        for (i in 0..10) {
            val y = (mapHeight * i / 10f)
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        
        // Calcular bounds con padding
        val lats = routePoints.map { it.latitude() }
        val lngs = routePoints.map { it.longitude() }
        val latRange = ((lats.maxOrNull() ?: 0.0) - (lats.minOrNull() ?: 0.0)).coerceAtLeast(1e-6)
        val lngRange = ((lngs.maxOrNull() ?: 0.0) - (lngs.minOrNull() ?: 0.0)).coerceAtLeast(1e-6)
        val padLat = latRange * 0.2 // Aumentar padding para mejor centrado
        val padLng = lngRange * 0.2
        val minLat = (lats.minOrNull() ?: 0.0) - padLat
        val minLng = (lngs.minOrNull() ?: 0.0) - padLng
        val maxLat = (lats.maxOrNull() ?: 0.0) + padLat
        val maxLng = (lngs.maxOrNull() ?: 0.0) + padLng
        val adjLat = maxLat - minLat
        val adjLng = maxLng - minLng
        
        // Calcular escala manteniendo aspecto
        val scaleX = width / adjLng
        val scaleY = mapHeight / adjLat
        val scale = minOf(scaleX, scaleY)
        val usedWidth = (adjLng * scale).toFloat()
        val usedHeight = (adjLat * scale).toFloat()
        val offsetX = ((width - usedWidth) / 2f)
        val offsetY = ((mapHeight - usedHeight) / 2f)
        
        fun px(p: Point): Pair<Float, Float> {
            val x = offsetX + ((p.longitude() - minLng) * scale).toFloat()
            val y = offsetY + (usedHeight - ((p.latitude() - minLat) * scale).toFloat())
            return x to y
        }
        
        // Dibujar ruta con mejor calidad y sombra
        val shadowPaint = Paint().apply {
            color = Color.argb(50, 0, 0, 0) // Sombra negra semi-transparente
            strokeWidth = 20f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        
        val routePaint = Paint().apply {
            color = Color.rgb(33, 150, 243)
            strokeWidth = 16f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        
        for (i in 0 until routePoints.size - 1) {
            val a = px(routePoints[i])
            val b = px(routePoints[i + 1])
            // Dibujar sombra primero (ligeramente desplazada)
            canvas.drawLine(a.first + 2f, a.second + 2f, b.first + 2f, b.second + 2f, shadowPaint)
            // Dibujar ruta principal
            canvas.drawLine(a.first, a.second, b.first, b.second, routePaint)
        }
        
        // Marcadores de inicio/fin mejorados
        val start = px(routePoints.first())
        val end = px(routePoints.last())
        val borderPaint = Paint().apply { 
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        val startPaint = Paint().apply { 
            color = Color.rgb(76, 175, 80)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val endPaint = Paint().apply { 
            color = Color.rgb(244, 67, 54)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Dibujar marcadores con sombra
        val markerShadowPaint = Paint().apply {
            color = Color.argb(50, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Sombra
        canvas.drawCircle(start.first + 2f, start.second + 2f, 24f, markerShadowPaint)
        canvas.drawCircle(end.first + 2f, end.second + 2f, 24f, markerShadowPaint)
        
        // Marcadores
        canvas.drawCircle(start.first, start.second, 24f, startPaint)
        canvas.drawCircle(start.first, start.second, 24f, borderPaint)
        canvas.drawCircle(end.first, end.second, 24f, endPaint)
        canvas.drawCircle(end.first, end.second, 24f, borderPaint)
    }
    
    /**
     * Dibuja la información de la ruta en la parte inferior
     */
    private fun drawRouteInfo(canvas: Canvas, route: Route, width: Int, mapHeight: Int) {
        val infoPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 48f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val titlePaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 72f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 42f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val statPaint = Paint().apply {
            color = Color.rgb(33, 150, 243)
            textSize = 64f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            color = Color.rgb(120, 120, 120)
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        var yPos = mapHeight + 100f
        canvas.drawText("🛴 ${route.scooterName}", width / 2f, yPos, titlePaint)
        yPos += 80f
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val date = dateFormat.format(Date(route.startTime))
        canvas.drawText(date, width / 2f, yPos, subtitlePaint)

        yPos += 120f
        val col1X = width * 0.25f
        val col2X = width * 0.5f
        val col3X = width * 0.75f
        canvas.drawText(String.format("%.2f", route.totalDistance), col1X, yPos, statPaint)
        canvas.drawText("km", col1X, yPos + 60f, labelPaint)
        canvas.drawText(formatDurationShort(route.totalDuration), col2X, yPos, statPaint)
        canvas.drawText("tiempo", col2X, yPos + 60f, labelPaint)
        canvas.drawText(String.format("%.1f", route.averageSpeed), col3X, yPos, statPaint)
        canvas.drawText("km/h media", col3X, yPos + 60f, labelPaint)

        yPos += 120f
        canvas.drawText("Vel. máxima: ${String.format("%.1f", route.maxSpeed)} km/h", width / 2f, yPos, infoPaint)
        yPos += 80f
        val watermarkPaint = Paint().apply {
            color = Color.rgb(180, 180, 180)
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ZipStats", width / 2f, yPos, watermarkPaint)
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes % 60)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds % 60)
            else -> String.format("%ds", seconds)
        }
    }
    
    private fun formatDurationShort(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> String.format("%d:%02d", hours, minutes % 60)
            else -> String.format("%d min", minutes)
        }
    }
}

sealed class RoutesUiState {
    object Loading : RoutesUiState()
    object Success : RoutesUiState()
    data class Error(val message: String) : RoutesUiState()
}
