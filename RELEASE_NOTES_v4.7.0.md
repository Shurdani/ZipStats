# 🚀 Release v4.7.0 - Mejoras en Mapas y Sistema de Preavisos Meteorológicos

## 🎯 Resumen

Esta release incluye mejoras significativas en la visualización de mapas, un sistema unificado de preavisos meteorológicos más intuitivo, y correcciones importantes en el formato de temperatura y visualización de badges.

---

## ✨ Nuevas Características

### 🗺️ Rotación Automática de Rutas Verticales
- Las rutas que van de Norte a Sur ahora se rotan automáticamente 90° para aprovechar mejor el espacio de la pantalla
- Ajuste inteligente de zoom y padding para evitar desbordamientos

### 🌦️ Centro de Notificaciones Unificado
- Todos los preavisos meteorológicos ahora aparecen en una sola tarjeta inteligente
- Mensajes dinámicos que indican específicamente qué condición activó la alerta (ej: "Viento fuerte: 45 km/h")
- Colores diferenciados según la gravedad (azul para lluvia, naranja para calzada mojada, rojo para extremas)

### 🏆 Badges Múltiples en Resumen
- Si una ruta tuvo lluvia + condiciones extremas, ahora se muestran ambos badges
- Resaltado visual de los parámetros que activaron las alertas extremas en los detalles

---

## 🔧 Mejoras

- ✅ **Migración completa a Mapbox SDK v11**: Eliminados todos los warnings de deprecación
- ✅ **Inicialización robusta del mapa**: Corregido el problema de "Europa entera" en la primera carga
- ✅ **Formato de temperatura corregido**: La temperatura de 0°C ya no muestra el signo menos
- ✅ **Umbrales consistentes**: Preavisos y badges usan exactamente los mismos criterios de detección

---

## 🐛 Correcciones

- 🐛 Mapa mostraba vista completa de Europa en la primera carga
- 🐛 Rutas verticales rotadas se desbordaban fuera de pantalla
- 🐛 Temperatura de 0°C se mostraba como "-0°C"
- 🐛 Preavisos aparecían durante el tracking (ahora solo antes de iniciar)
- 🐛 Solo se mostraba un badge cuando había múltiples condiciones adversas

---

## 📱 Compatibilidad

- **Android**: API 31+ (Android 12+)
- **Mapbox SDK**: v11.8.0
- **Kotlin**: 2.0.0

---

## 📥 Descarga

Descarga la nueva versión desde [Releases](https://github.com/shurdani/Patinetatrack/releases/tag/v4.7.0)

---

## 🙏 Agradecimientos

Gracias a todos los usuarios que reportaron bugs y sugirieron mejoras. ¡Seguimos mejorando ZipStats para ti!

