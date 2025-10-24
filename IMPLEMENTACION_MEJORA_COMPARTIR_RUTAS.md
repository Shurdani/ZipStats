# 🎨 Hoja de Ruta: Mejora Estética de "Compartir Ruta"

## 📋 Objetivo

Rediseñar la imagen generada para compartir rutas en redes sociales, transformándola en una versión más moderna, limpia y atractiva con un mapa minimalista que haga destacar la ruta.

---

## ✅ Recursos Ya Creados

Los siguientes archivos ya han sido creados en el proyecto:

### 1. **Estilo de Mapa Personalizado**
- 📁 **Ubicación:** `app/src/main/res/raw/map_style_light.json`
- ✨ **Características:**
  - Mapa desaturado con aspecto "lavado" y limpio
  - POIs de negocios completamente ocultos
  - Etiquetas de carreteras removidas
  - Geometría simplificada para menos distracción

### 2. **Recursos Gráficos (Iconos SVG)**
- 📁 **Ubicación:** `app/src/main/res/drawable/`
- 🎨 **Iconos creados:**
  - `ic_route_marker_green.xml` - Marcador circular verde lima (#39FF14)
  - `ic_distance.xml` - Icono de distancia (pin de ubicación)
  - `ic_timer.xml` - Icono de tiempo (cronómetro)
  - `ic_speed.xml` - Icono de velocidad (velocímetro)
  - `ic_scooter.xml` - Icono de patinete/vehículo

---

## 🚀 Tareas de Implementación

## **TAREA 1: Aplicar Estilo de Mapa Personalizado**

### 📍 Ubicación del Código
Archivo: `app/src/main/java/com/zipstats/app/ui/components/CapturableMapView.kt`

### 🔧 Modificación Necesaria

En la función `CapturableMapView`, dentro del callback `getMapAsync`, **justo después de la línea 123** (después de `Log.d("CapturableMapView", "✅ Mapa cargado correctamente")`), agregar el siguiente código:

```kotlin
// NUEVO: Aplicar estilo personalizado al mapa
try {
    val success = googleMap.setMapStyle(
        MapStyleOptions.loadRawResourceStyle(
            context,
            R.raw.map_style_light
        )
    )
    if (!success) {
        Log.e("CapturableMapView", "⚠️ El parseo del estilo falló")
    } else {
        Log.d("CapturableMapView", "✅ Estilo de mapa aplicado correctamente")
    }
} catch (e: Resources.NotFoundException) {
    Log.e("CapturableMapView", "❌ No se encontró el archivo de estilo", e)
} catch (e: Exception) {
    Log.e("CapturableMapView", "❌ Error al aplicar estilo del mapa", e)
}
```

### 📦 Importaciones Necesarias
Agregar al inicio del archivo:

```kotlin
import com.google.android.gms.maps.model.MapStyleOptions
import android.content.res.Resources
```

### 📋 Resultado Esperado
- El mapa tendrá un aspecto limpio y minimalista
- Los POIs estarán ocultos
- Las etiquetas de carreteras no se mostrarán
- La ruta destacará sobre el fondo desaturado

---

## **TAREA 2: Mejorar Visualización de Rutas y Marcadores**

### 📍 Ubicación del Código
Archivo: `app/src/main/java/com/zipstats/app/ui/components/CapturableMapView.kt`

### 🔧 Modificación 2.1: Estilizar la Polyline

**REEMPLAZAR** las líneas 139-144 (definición de `polylineOptions`):

```kotlin
// CÓDIGO ANTIGUO (ELIMINAR)
val polylineOptions = PolylineOptions()
    .addAll(routePoints)
    .color(0xFF2196F3.toInt())
    .width(10f)
googleMap.addPolyline(polylineOptions)
```

**POR:**

```kotlin
// CÓDIGO NUEVO
val polylineOptions = PolylineOptions()
    .addAll(routePoints)
    .color(0xFF39FF14.toInt()) // Verde lima brillante
    .width(15f) // Grosor aumentado
    .jointType(JointType.ROUND) // Esquinas redondeadas
    .startCap(RoundCap()) // Extremo inicio redondeado
    .endCap(RoundCap()) // Extremo final redondeado
googleMap.addPolyline(polylineOptions)
```

### 📦 Importaciones Necesarias
Agregar al inicio del archivo:

```kotlin
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
```

### 🔧 Modificación 2.2: Crear Marcadores Personalizados

**CREAR** una nueva función auxiliar al final del archivo (antes del cierre de clase), alrededor de la línea 295:

```kotlin
/**
 * Crea un BitmapDescriptor personalizado para los marcadores de ruta
 */
private fun createCustomMarker(context: android.content.Context): BitmapDescriptor {
    // Cargar el drawable vectorial
    val drawable = androidx.core.content.ContextCompat.getDrawable(
        context,
        R.drawable.ic_route_marker_green
    ) ?: return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    
    // Convertir a Bitmap
    val bitmap = android.graphics.Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
```

**REEMPLAZAR** las líneas 146-163 (marcadores de inicio y fin):

```kotlin
// CÓDIGO ANTIGUO (ELIMINAR)
// Marcador de inicio (punto verde)
googleMap.addMarker(
    MarkerOptions()
        .position(routePoints.first())
        .title("Inicio de la ruta")
        .snippet("Punto de partida")
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
)

// Marcador de final (bandera a cuadros - rojo)
if (routePoints.size > 1) {
    googleMap.addMarker(
        MarkerOptions()
            .position(routePoints.last())
            .title("Final de la ruta")
            .snippet("Meta")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
    )
}
```

**POR:**

```kotlin
// CÓDIGO NUEVO - Marcadores personalizados
val customMarker = createCustomMarker(context)

// Marcador de inicio (círculo verde personalizado)
googleMap.addMarker(
    MarkerOptions()
        .position(routePoints.first())
        .title("Inicio de la ruta")
        .snippet("Punto de partida")
        .icon(customMarker)
        .anchor(0.5f, 0.5f) // Centrar el icono en la coordenada
)

// Marcador de final (círculo verde personalizado)
if (routePoints.size > 1) {
    googleMap.addMarker(
        MarkerOptions()
            .position(routePoints.last())
            .title("Final de la ruta")
            .snippet("Meta")
            .icon(customMarker)
            .anchor(0.5f, 0.5f) // Centrar el icono en la coordenada
    )
}
```

### 📦 Importaciones Necesarias
Agregar al inicio del archivo si no están ya:

```kotlin
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
```

### 📋 Resultado Esperado
- La línea de la ruta será más gruesa (15px) y de color verde lima brillante
- Las esquinas y extremos serán redondeados para un aspecto más suave
- Los marcadores de inicio y fin serán círculos verdes personalizados
- Los marcadores estarán perfectamente centrados en las coordenadas

---

## **TAREA 3: Rediseñar la Tarjeta de Estadísticas**

### 📍 Ubicación del Código
Archivo: `app/src/main/java/com/zipstats/app/ui/routes/RouteDetailDialog.kt`

### 🔧 Modificación 3.1: Crear Layout XML para la Tarjeta Flotante

**CREAR** un nuevo archivo XML:
📁 **Ubicación:** `app/src/main/res/layout/share_route_stats_card.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="16dp"
    app:cardBackgroundColor="#D92C2C2C"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="20dp">

        <!-- Icono del vehículo -->
        <ImageView
            android:id="@+id/vehicleIcon"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:src="@drawable/ic_scooter"
            android:contentDescription="@string/vehicle_icon"
            app:tint="#39FF14"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent" />

        <!-- Contenedor de estadísticas -->
        <LinearLayout
            android:id="@+id/statsContainer"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:orientation="horizontal"
            app:layout_constraintStart_toEndOf="@id/vehicleIcon"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toTopOf="@id/vehicleIcon"
            app:layout_constraintBottom_toBottomOf="@id/vehicleIcon">

            <!-- Distancia -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:gravity="center">

                <ImageView
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:src="@drawable/ic_distance"
                    app:tint="#FFFFFF"
                    android:contentDescription="@string/distance_icon" />

                <TextView
                    android:id="@+id/distanceValue"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textSize="24sp"
                    android:textStyle="bold"
                    android:textColor="#FFFFFF"
                    tools:text="1.61" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/km"
                    android:textSize="14sp"
                    android:textColor="#AAAAAA" />

            </LinearLayout>

            <!-- Tiempo -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:gravity="center">

                <ImageView
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:src="@drawable/ic_timer"
                    app:tint="#FFFFFF"
                    android:contentDescription="@string/time_icon" />

                <TextView
                    android:id="@+id/timeValue"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textSize="24sp"
                    android:textStyle="bold"
                    android:textColor="#FFFFFF"
                    tools:text="23" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/min"
                    android:textSize="14sp"
                    android:textColor="#AAAAAA" />

            </LinearLayout>

            <!-- Velocidad -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:gravity="center">

                <ImageView
                    android:layout_width="20dp"
                    android:layout_height="20dp"
                    android:src="@drawable/ic_speed"
                    app:tint="#FFFFFF"
                    android:contentDescription="@string/speed_icon" />

                <TextView
                    android:id="@+id/speedValue"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textSize="24sp"
                    android:textStyle="bold"
                    android:textColor="#FFFFFF"
                    tools:text="16.5" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/km_h_avg"
                    android:textSize="14sp"
                    android:textColor="#AAAAAA" />

            </LinearLayout>

        </LinearLayout>

        <!-- Nombre del vehículo e información secundaria -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginTop="16dp"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintTop_toBottomOf="@id/vehicleIcon">

            <TextView
                android:id="@+id/vehicleName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="16sp"
                android:textStyle="bold"
                android:textColor="#FFFFFF"
                tools:text="Xiaomi 4 Ultra" />

            <TextView
                android:id="@+id/routeDate"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textSize="12sp"
                android:textColor="#888888"
                tools:text="21 de octubre de 2025" />

        </LinearLayout>

    </androidx.constraintlayout.widget.ConstraintLayout>

</androidx.cardview.widget.CardView>
```

### 🔧 Modificación 3.2: Agregar Strings al resources

**AGREGAR** al archivo `app/src/main/res/values/strings.xml`:

```xml
<!-- Strings para compartir rutas -->
<string name="vehicle_icon">Icono del vehículo</string>
<string name="distance_icon">Icono de distancia</string>
<string name="time_icon">Icono de tiempo</string>
<string name="speed_icon">Icono de velocidad</string>
<string name="km">km</string>
<string name="min">min</string>
<string name="km_h_avg">km/h media</string>
```

### 🔧 Modificación 3.3: Actualizar la Función `createFinalRouteImage`

**REEMPLAZAR** la función `createFinalRouteImage` en `RouteDetailDialog.kt` (líneas 421-466) por la siguiente versión mejorada:

```kotlin
/**
 * Crea la imagen final combinando el mapa con las estadísticas
 */
private fun createFinalRouteImage(route: Route, mapBitmap: android.graphics.Bitmap): android.graphics.Bitmap {
    val width = 1080
    val height = 1920
    val mapHeight = (height * 0.85f).toInt() // 85% para el mapa
    val bottomPadding = height - mapHeight

    val finalBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(finalBitmap)
    
    // Fondo limpio
    canvas.drawColor(android.graphics.Color.rgb(245, 245, 245))
    
    // Dibujar el mapa escalado
    val mapWidth = mapBitmap.width
    val mapHeightActual = mapBitmap.height
    val scaleX = width.toFloat() / mapWidth
    val scaleY = mapHeight.toFloat() / mapHeightActual
    val scale = kotlin.math.min(scaleX, scaleY)
    
    val scaledWidth = (mapWidth * scale).toInt()
    val scaledHeight = (mapHeightActual * scale).toInt()
    val offsetX = (width - scaledWidth) / 2
    val offsetY = (mapHeight - scaledHeight) / 2
    
    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(mapBitmap, scaledWidth, scaledHeight, true)
    canvas.drawBitmap(scaledBitmap, offsetX.toFloat(), offsetY.toFloat(), null)
    
    // Inflar el layout de la tarjeta flotante
    val inflater = android.view.LayoutInflater.from(context)
    val cardView = inflater.inflate(R.layout.share_route_stats_card, null) as androidx.cardview.widget.CardView
    
    // Configurar datos en la tarjeta
    cardView.findViewById<android.widget.TextView>(R.id.distanceValue).text = 
        String.format("%.1f", route.totalDistance)
    
    val durationMinutes = route.totalDuration / 60000
    cardView.findViewById<android.widget.TextView>(R.id.timeValue).text = durationMinutes.toString()
    
    cardView.findViewById<android.widget.TextView>(R.id.speedValue).text = 
        String.format("%.1f", route.averageSpeed)
    
    cardView.findViewById<android.widget.TextView>(R.id.vehicleName).text = route.scooterName
    
    // Formatear fecha
    val dateFormat = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", java.util.Locale("es", "ES"))
    } else {
        java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", java.util.Locale("es", "ES"))
    }
    
    val dateText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val instant = java.time.Instant.ofEpochMilli(route.startTime)
        val date = java.time.LocalDate.ofInstant(instant, java.time.ZoneId.systemDefault())
        dateFormat.format(date)
    } else {
        dateFormat.format(java.util.Date(route.startTime))
    }
    
    cardView.findViewById<android.widget.TextView>(R.id.routeDate).text = dateText
    
    // Medir y renderizar la tarjeta
    val cardWidth = width - 64 // Márgenes de 32dp a cada lado
    val measureSpec = android.view.View.MeasureSpec.makeMeasureSpec(cardWidth, android.view.View.MeasureSpec.EXACTLY)
    cardView.measure(measureSpec, android.view.View.MeasureSpec.UNSPECIFIED)
    
    val cardHeight = cardView.measuredHeight
    val cardX = 32
    val cardY = mapHeight - cardHeight - 32 // Posicionar sobre el mapa, cerca del borde inferior
    
    cardView.layout(0, 0, cardView.measuredWidth, cardHeight)
    
    // Dibujar la tarjeta en el canvas
    canvas.save()
    canvas.translate(cardX.toFloat(), cardY.toFloat())
    cardView.draw(canvas)
    canvas.restore()
    
    // Watermark en la parte inferior
    val watermarkPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.rgb(150, 150, 150)
        textSize = 28f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("ZipStats - Tu compañero de tracking", width / 2f, height - 40f, watermarkPaint)
    
    return finalBitmap
}
```

### 📋 Resultado Esperado
- Tarjeta flotante semitransparente con esquinas redondeadas
- Iconos modernos junto a cada métrica
- Tipografía clara con jerarquía visual
- Fecha formateada en español
- Tarjeta posicionada sobre el mapa como overlay
- Diseño moderno y profesional

---

## **TAREA 4: Aplicar los Cambios a Otros Componentes**

### 📍 Archivos Adicionales a Modificar

Los mismos cambios del estilo de mapa y marcadores deben aplicarse a:

1. **RouteMapView.kt** - Repetir TAREA 1 y TAREA 2
2. **BasicMapView.kt** - Repetir TAREA 1 y TAREA 2
3. **RoutesViewModel.kt** - Actualizar función `createFinalRouteImage` (similar a TAREA 3)

### 🔧 Instrucciones
Para cada archivo, buscar las secciones equivalentes donde se:
- Inicializa el GoogleMap (aplicar estilo)
- Dibuja la polyline (aplicar nuevo estilo)
- Crea marcadores (usar marcadores personalizados)

---

## 📊 Checklist de Verificación

Antes de considerar completa la implementación, verificar:

- [ ] El archivo `map_style_light.json` existe en `res/raw/`
- [ ] Los 5 iconos SVG existen en `res/drawable/`
- [ ] El layout `share_route_stats_card.xml` existe en `res/layout/`
- [ ] Los strings se agregaron a `strings.xml`
- [ ] El estilo de mapa se aplica en `CapturableMapView.kt`
- [ ] La polyline usa color verde lima (#39FF14) y grosor 15f
- [ ] Los marcadores son círculos verdes personalizados
- [ ] La función `createFinalRouteImage` usa el nuevo layout de tarjeta
- [ ] La fecha se formatea correctamente en español
- [ ] Los cambios se aplicaron a `RouteMapView.kt`
- [ ] Los cambios se aplicaron a `BasicMapView.kt`
- [ ] Los cambios se aplicaron a `RoutesViewModel.kt`

---

## 🧪 Pruebas Recomendadas

1. **Prueba de Visualización:**
   - Abrir una ruta existente
   - Verificar que el mapa tenga el estilo limpio
   - Verificar que la ruta sea verde lima y prominente
   - Verificar que los marcadores sean círculos verdes

2. **Prueba de Compartir:**
   - Compartir una ruta
   - Verificar que la imagen generada tenga:
     - Mapa con estilo limpio
     - Tarjeta flotante semitransparente
     - Iconos visibles
     - Estadísticas correctas
     - Fecha formateada en español

3. **Prueba de Rendimiento:**
   - Compartir rutas con diferentes cantidades de puntos GPS
   - Verificar que no haya problemas de memoria
   - Verificar que la generación sea rápida (< 3 segundos)

---

## 🎨 Colores de Referencia

Para mantener consistencia visual:

| Elemento | Color | Código |
|----------|-------|--------|
| Ruta (Polyline) | Verde Lima | `#39FF14` |
| Marcadores | Verde Lima | `#39FF14` |
| Fondo Tarjeta | Gris Oscuro Semi-transparente | `#D92C2C2C` |
| Texto Principal | Blanco | `#FFFFFF` |
| Texto Secundario | Gris Claro | `#AAAAAA` |
| Iconos | Verde Lima / Blanco | `#39FF14` / `#FFFFFF` |

---

## 📞 Soporte

Si encuentras algún problema durante la implementación:

1. Verificar que todos los recursos estén en las ubicaciones correctas
2. Limpiar y reconstruir el proyecto (`./gradlew clean build`)
3. Verificar los logs de Android para mensajes de error
4. Revisar que todas las importaciones estén presentes

---

## 🎯 Resultado Final Esperado

La imagen compartida debe tener:

✅ **Mapa limpio y minimalista** con estilo personalizado  
✅ **Ruta destacada** en verde lima brillante con línea gruesa  
✅ **Marcadores circulares** verdes en inicio y fin  
✅ **Tarjeta flotante moderna** con fondo semitransparente  
✅ **Iconografía clara** para cada métrica  
✅ **Tipografía jerárquica** con números grandes y etiquetas pequeñas  
✅ **Fecha legible** en formato español  
✅ **Diseño profesional** listo para redes sociales  

---

**¡Buena suerte con la implementación! 🚀**

