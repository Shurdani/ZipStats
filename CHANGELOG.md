# Changelog

## [Versión Actual] - 2024

### 🔧 Mejoras y Correcciones (Última Actualización)

#### 🌍 Formato de Números en Español
- **Formato Unificado**: Todos los números de la aplicación ahora usan formato español:
  - Separador de miles: punto (.) → Ejemplo: 23.525
  - Separador decimal: coma (,) → Ejemplo: 23.525,25
- **Función Centralizada**: Nueva función `formatNumberSpanish()` en `LocationUtils.kt` para formateo consistente
- **Archivos Actualizados**:
  - Pantallas: RouteDetailDialog, RoutesScreen, StatisticsScreen, RecordsHistoryScreen, TrackingScreen, AchievementsScreen, RepairsScreen
  - Componentes: RouteSummaryCard, ProfileScreen
  - ViewModels: TrackingViewModel, RoutesViewModel
  - Utilidades: ShareUtils, LocationUtils
  - Perfil: ScooterDetailScreen, ScootersManagementScreen

#### 🌧️ Mejoras en Diálogo de Clima
- **Descripción de Lluvia**: Corregido para mostrar la descripción guardada en Firebase ("Lluvia") en lugar de calcular automáticamente
- **Lógica de Precipitación**: Mejorada para mostrar probabilidad de lluvia cuando no hay precipitación medida pero hay lluvia detectada por condiciones
- **Dirección del Viento**: Eliminada de la tarjeta del clima para evitar cortes de texto (solo se muestra velocidad)

#### 📱 Mejoras de UI/UX
- **Diálogo de Logros**: Título y descripción ahora permiten múltiples líneas para evitar cortes
- **Tarjeta Comparativa**: Texto de comparación ahora se adapta y no se corta, mostrando siempre el período completo
- **Tarjeta de Distancia Total**: Números ahora usan autoResize para ajustarse automáticamente sin cortarse

#### 🐛 Correcciones de Tipos
- Corregido error de tipo: `route.movingPercentage` (Float) convertido a Double para formateo

---

## [Versión Anterior] - 2024

### ✨ Nuevas Funcionalidades

#### 🎯 Sistema de Insights Dinámicos Aleatorios
- **Tarjetas de Comparación Inteligentes**: Implementado un sistema que muestra métricas aleatorias en cada carga de la pantalla de estadísticas
- **7 Métricas Disponibles**:
  - 📏 **Distancia**: Comparación de kilómetros recorridos
  - 🌱 **CO₂ Ahorrado**: Impacto ambiental en kilogramos de CO₂
  - 🌳 **Árboles**: Equivalente en árboles salvados
  - ⛽ **Gasolina**: Litros de combustible ahorrados
  - 💧 **Rutas con Lluvia**: Kilómetros recorridos bajo condiciones de lluvia
  - 🌊 **Calzada Mojada**: Kilómetros con calzada mojada (sin lluvia activa)
  - 🌡️ **Clima Extremo**: Kilómetros en condiciones climáticas extremas

#### 🎨 Rediseño de Tarjeta Comparativa
- **Diseño Visual Mejorado**: Nueva tarjeta comparativa con:
  - Icono circular con color temático según la métrica
  - Comparación lado a lado (Actual vs Anterior)
  - Barra visual de diferencia proporcional
  - Badge de porcentaje con icono de tendencia
  - Colores adaptados a Material 3 (tema claro/oscuro)

#### 🏆 Mensajes Motivacionales
- Mensajes personalizados para métricas aventureras: "¡Espíritu aventurero!"
- Filtrado inteligente: Solo muestra métricas con datos válidos (>0.1 km)

### 🔧 Mejoras y Correcciones

#### 📊 Gestión de Filtros por Pestañas
- **Lógica Mejorada**: Los filtros se ajustan automáticamente al cambiar de pestaña:
  - Al cambiar a "Este Mes": Se limpia el filtro de solo año
  - Al cambiar a "Este Año": Se mantiene el año pero se elimina el mes específico
  - Al cambiar a "Todo": Se limpian todos los filtros

#### 🎯 Cálculo de Métricas Climáticas
- Integración con `RouteRepository` para acceder a datos de rutas
- Cálculo automático de distancias con condiciones climáticas según período seleccionado
- Detección inteligente de calzada mojada (considera día/noche, humedad, precipitación)

### 🛠️ Cambios Técnicos

#### ViewModel (`StatisticsViewModel.kt`)
- Añadido `RouteRepository` como dependencia
- Nuevo enum `InsightMetric` con 7 métricas configuradas
- Nueva función `generateRandomInsight()` con lógica de filtrado inteligente
- Funciones auxiliares:
  - `calculateWeatherDistances()`: Calcula distancias con condiciones climáticas
  - `checkWetRoadConditions()`: Verifica condiciones de calzada mojada
- Nuevo StateFlow `weatherDistances` para almacenar métricas climáticas
- Actualizado `loadStatistics()` para incluir cálculo de métricas climáticas

#### UI (`StatisticsScreen.kt`)
- Nuevo componente `SmartInsightCard`: Tarjeta visual para insights dinámicos
- Actualizado `ComparisonCard`: Rediseño completo con mejor UX
- Integración de `LaunchedEffect` para generar insights al cambiar período
- Observación de `weatherDistances` StateFlow

#### Modelos de Datos
- Nuevo `RandomInsightData`: Estructura de datos para insights
- Extendido `ComparisonData`: Añadidos campos para tipo de métrica, título, unidad e icono
- Nuevo `Quintuple` helper class: Para retornar múltiples valores

### 🎨 Mejoras de UI/UX

- **Colores Temáticos**: Cada métrica tiene su color distintivo:
  - Distancia: Azul (#2979FF)
  - CO₂: Verde (#4CAF50)
  - Árboles: Verde claro (#8BC34A)
  - Gasolina: Naranja (#FFA726)
  - Lluvia: Azul cian (#00B0FF)
  - Calzada Mojada: Naranja/Ámbar (#FF9100)
  - Clima Extremo: Rojo (#D50000)

- **Adaptación a Material 3**: Todos los colores se adaptan automáticamente a temas claro/oscuro
- **Iconografía Mejorada**: Uso de iconos Material Icons para cada métrica

### 📝 Notas

- Las métricas climáticas solo se muestran si hay datos válidos (>0.1 km)
- El sistema selecciona aleatoriamente entre métricas válidas disponibles
- Los cálculos de comparación histórica son aproximados para métricas climáticas (basados en tendencia general)

---

**Desarrollado con ❤️ para ZipStats**

