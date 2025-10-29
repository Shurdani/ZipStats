# 🚀 ZipStats v2.7 - Release Notes

## 📅 Fecha de lanzamiento
27 de Octubre de 2025

## 🎯 Resumen
Esta versión trae mejoras significativas en el análisis de rutas, cálculo preciso de velocidad real, integración de clima en tiempo real con OpenWeather API, y un rediseño completo de la pantalla de detalles de ruta para una mejor experiencia de usuario.

---

## ✨ Nuevas Funcionalidades

### 🧮 Análisis de Rutas Mejorado
- **Cálculo de velocidad real**: Ahora se calcula la velocidad solo durante el movimiento, excluyendo pausas y paradas
- **Detección automática de pausas**: El sistema identifica automáticamente cuándo el vehículo está detenido
- **Tiempo en movimiento**: Se diferencia entre tiempo total y tiempo en movimiento
- **Filtrado de outliers**: Eliminación de lecturas GPS erróneas para mayor precisión
- **Análisis post-ruta**: Procesamiento completo de cada ruta con métricas avanzadas

### 🌤️ Integración de OpenWeather API
- **Clima en tiempo real**: Cada ruta guarda las condiciones climáticas del momento
- **Temperatura y condiciones**: Visualización de temperatura y emoji del clima
- **Histórico de clima**: Las rutas antiguas muestran el clima del día que se realizaron
- **API key segura**: Configuración mediante `local.properties` (nunca expuesta en el código)

### 🎨 Rediseño Pantalla de Detalles de Ruta
- **Encabezado compacto**: Muestra el modelo del vehículo en lugar del nombre completo
- **Mapa expandible**: Toca el mapa para verlo en pantalla completa
- **Chips informativos**: Distancia, duración y clima en tarjetas visuales
- **Detalles avanzados colapsables**:
  - Velocidad real (destacada)
  - Velocidad máxima
  - Velocidad media
  - Tiempo en movimiento con porcentaje
  - Hora de inicio y fin de la ruta
- **Botones flotantes**: Acciones de eliminar y compartir más accesibles
- **Imagen compartida mejorada**: Incluye clima y todas las métricas actualizadas

### 📊 Otras Mejoras
- **Exportación Excel mejorada**: Mayor precisión en los datos exportados
- **Iconos vectoriales**: Uso de iconos XML escalables para cada tipo de vehículo
- **Formato de duración**: Ahora se muestran las unidades (h, min, s) de forma clara
- **Consistencia tipográfica**: Mejoras visuales en toda la aplicación

---

## 🐛 Correcciones de Errores

### Android 10+ (API 29+)
- Corregidos permisos de almacenamiento para Android 10 y superior
- Implementado Scoped Storage correctamente
- Mejorado manejo de archivos temporales

### Deprecated Components
- Reemplazado `Divider` por `HorizontalDivider` (Material3)
- Corregido uso de `StateFlow.value` en componentes Compose
- Actualizado manejo de permisos deprecated

### Estabilidad
- Mejorado manejo de errores en rutas guardadas
- Correcciones en el cálculo de velocidad en tiempo real
- Mejor gestión de memoria en capturas de mapa

---

## 📦 Archivos Nuevos

### Core
- `WeatherRepository.kt` - Repositorio para OpenWeather API
- `RouteAnalyzer.kt` - Análisis post-ruta completo
- `ExcelExporter.kt` - Exportación mejorada a Excel

### Análisis
- `OutlierFilter.kt` - Filtrado de outliers GPS
- `PauseDetector.kt` - Detección de pausas automática
- `SpeedCalculator.kt` - Cálculos precisos de velocidad
- `LocationTracker.kt` - Tracking mejorado de ubicación

### Documentación
- `CONFIGURACION_OPENWEATHER.md` - Guía de configuración de API
- `DEBUG_OPENWEATHER.md` - Debugging de clima
- `RESUMEN_MEJORAS_COMPLETAS_v2.7.md` - Documentación completa de mejoras

---

## 🔧 Cambios Técnicos

### Modelo de Datos
- Añadidos campos de clima a `Route`:
  - `weatherTemperature: Double?`
  - `weatherEmoji: String?`
  - `weatherDescription: String?`

### Repositorios
- Nuevo método `createRouteWithWeather()` en `RouteRepository`
- Integración automática de clima al finalizar rutas

### UI Components
- Rediseñado `RouteDetailDialog` completamente
- Nuevo componente `FullscreenMapDialog`
- Mejoras en `CapturableMapView` para compartir

---

## 📱 Compatibilidad

- **Android mínimo**: API 31 (Android 12)
- **Android objetivo**: API 34 (Android 14)
- **Versión del código**: 6
- **Versión del nombre**: 2.7

---

## 🔐 Configuración Requerida

### OpenWeather API
Para usar la funcionalidad de clima, necesitas configurar una API key:

1. Obtén una clave gratuita en [OpenWeather](https://openweathermap.org/api)
2. Crea/edita el archivo `local.properties`
3. Añade la línea: `openweather.api.key=TU_API_KEY_AQUI`

Ver `CONFIGURACION_OPENWEATHER.md` para más detalles.

---

## 🙏 Agradecimientos

Gracias a todos los usuarios que han proporcionado feedback para mejorar ZipStats.

---

## 📝 Notas de Actualización

### Para Usuarios Nuevos
- Instala la APK y disfruta de todas las funcionalidades

### Para Usuarios Existentes
- **Rutas antiguas**: Seguirán funcionando normalmente
- **Clima en rutas antiguas**: Se mostrará el clima actual como referencia
- **Rutas nuevas**: Incluirán automáticamente el clima del momento

---

## 🐞 Problemas Conocidos

Ninguno en este momento.

---

## 🔮 Próximas Mejoras

- Gráficos de velocidad durante la ruta
- Exportación de rutas en formato GPX
- Comparación entre rutas
- Estadísticas por tipo de vehículo

---

## 📞 Soporte

¿Encontraste un bug o tienes una sugerencia?
- Abre un issue en [GitHub](https://github.com/Shurdani/ZipStats/issues)

---

**¡Disfruta de ZipStats v2.7!** 🎉

