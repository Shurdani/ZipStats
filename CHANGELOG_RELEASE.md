# Changelog - Release

## Versión Actual - 2024

### ✨ Nuevas Funcionalidades

**Sistema de Insights Dinámicos Aleatorios**: Tarjetas de comparación inteligentes que muestran métricas aleatorias (distancia, CO₂, árboles, gasolina, lluvia, calzada mojada, clima extremo) en cada carga de estadísticas con mensajes motivacionales personalizados.

**Rediseño de Tarjeta Comparativa**: Nueva tarjeta visual con iconos temáticos, comparación lado a lado, barras de diferencia proporcional y badges de porcentaje adaptados a Material 3.

### 🔧 Mejoras y Correcciones

**Formato de Números en Español**: Migración completa a formato español (punto para miles, coma para decimales) en toda la aplicación. Nueva función centralizada `formatNumberSpanish()` para consistencia.

**Mejoras en Diálogo de Clima**: Descripción de lluvia ahora muestra la guardada en Firebase, lógica mejorada de precipitación/probabilidad, y eliminada dirección del viento para evitar cortes de texto.

**Mejoras de UI/UX**: Diálogos de logros y tarjetas comparativas ahora permiten múltiples líneas para evitar cortes. Tarjeta de distancia total con autoResize. Gestión mejorada de filtros por pestañas que se ajustan automáticamente.

**Cálculo de Métricas Climáticas**: Integración con RouteRepository, cálculo automático de distancias con condiciones climáticas, y detección inteligente de calzada mojada considerando día/noche, humedad y precipitación.

**Correcciones Técnicas**: Corregido error de tipo con `route.movingPercentage`. Nuevos componentes UI (`SmartInsightCard`), modelos de datos extendidos (`RandomInsightData`, `ComparisonData`), y mejoras en ViewModels.

### 🎨 Mejoras Visuales

Colores temáticos para cada métrica (azul, verde, naranja, rojo) que se adaptan automáticamente a Material 3 (tema claro/oscuro). Iconografía mejorada con Material Icons.

---

**Desarrollado con ❤️ para ZipStats**

