# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [3.2] - 2025-01-06

### ✨ Nuevas Características

- **Sistema de Permisos Centralizado**
  - ✅ Diálogo de permisos al inicio de la app con explicaciones
  - ✅ Sección de permisos en Ajustes con switches informativos
  - ✅ Gestión centralizada de todos los permisos de la app

- **Sincronización de Logros con Firebase**
  - ✅ Logros asociados al usuario en Firebase
  - ✅ Sincronización automática al iniciar sesión
  - ✅ Prevención de notificaciones duplicadas al cambiar de dispositivo

- **Notificaciones de Logros**
  - ✅ Reemplazo de Snackbars por notificaciones del sistema
  - ✅ Notificaciones anidadas para múltiples logros desbloqueados
  - ✅ Canal de notificaciones dedicado para logros

- **Mejoras en Estadísticas**
  - ✅ Selección de período (mes específico o año completo)
  - ✅ Visualización de estadísticas por mes o año seleccionado
  - ✅ Actualización automática de pestañas según selección
  - ✅ Eliminación de métrica "Máximo" en pestañas "Este Mes" y "Este Año"

### 🎨 Mejoras de Interfaz

- **Unificación de Botones**
  - ✅ Todos los botones de diálogos y formularios ahora son `Button` (no `TextButton`)
  - ✅ Botón principal con estilo sombreado/elevated
  - ✅ Botón secundario con estilo más sutil (`surfaceVariant`)
  - ✅ Consistencia visual en toda la app

- **Mejoras de Legibilidad**
  - ✅ Filas alternadas (striping) en tablas de rutas y registros
  - ✅ Mayor padding vertical en filas de tablas
  - ✅ Encabezados de columnas más distinguibles con mayor tamaño y contraste

- **Formularios de Vehículos**
  - ✅ Eliminación de placeholders específicos de patinetes
  - ✅ Estilo unificado: formularios centrados como diálogos
  - ✅ Conversión de bottom sheet a diálogo en edición de vehículos

- **Onboarding**
  - ✅ Reducción a 2 botones: "Registrar vehículo ahora" y "Más tarde"
  - ✅ Eliminación del botón "Ir a perfil"

- **Iconos y Navegación**
  - ✅ Corrección de alineación del icono en Bottom Navigation
  - ✅ Cambio de icono de navegación en tarjeta de Mantenimiento (flecha derecha en lugar de izquierda)

- **Textos de Ayuda**
  - ✅ Texto explicativo en campo de kilometraje de reparaciones
  - ✅ Información sobre comportamiento automático cuando el campo está vacío

### 🔧 Mejoras y Correcciones

- **Workflows de CI/CD**
  - ✅ Corrección del workflow de CodeQL para análisis de seguridad
  - ✅ Mejora del workflow de tests para ejecución correcta
  - ✅ Corrección del auto-labeling de Pull Requests
  - ✅ Configuración mejorada de GitHub Actions
  - ✅ Desactivación de publicación automática de releases (publicación manual)

- **Seguridad**
  - ✅ Configuración de CodeQL para escaneo de código
  - ✅ Mejoras en la gestión de archivos sensibles en workflows

- **Infraestructura**
  - ✅ Actualización de dependencias de GitHub Actions
  - ✅ Mejoras en la configuración de Gradle wrapper
  - ✅ Optimización de procesos de build
  - ✅ APK disponible como artifact en lugar de publicación automática

### 📝 Notas

- Esta versión incluye mejoras significativas en la experiencia de usuario
- Sistema de permisos más transparente y fácil de gestionar
- Logros sincronizados entre dispositivos
- Interfaz más consistente y profesional

---

## [3.0] - 2024-XX-XX

### ✨ Nuevas Características

- **Autenticación**
  - ✅ Inicio de sesión con cuenta de Google
  - ✅ Fusión automática de cuentas (email/password + Google)
  - ✅ Eliminación de verificación de email (siempre va a spam)

- **Interfaz de Usuario**
  - ✅ Diseño completamente responsive que se adapta a todos los tamaños de pantalla
  - ✅ Mejoras en la visualización de tablas en pantallas pequeñas
  - ✅ Texto adaptativo según el tamaño de la pantalla

- **Tracking**
  - ✅ Opción para mantener la pantalla encendida durante la grabación de rutas
  - ✅ Configuración en pantalla de ajustes

- **Onboarding**
  - ✅ Diálogo de bienvenida mejorado
  - ✅ Aparece solo si no hay vehículos registrados
  - ✅ Se muestra una vez por sesión si se descarta

### 🚀 Actualizaciones

- **Dependencias**
  - ✅ `com.google.gms.google-services`: 4.4.2 → 4.4.4
  - ✅ `com.google.android.gms:play-services-base`: 18.2.0 → 18.9.0
  - ✅ `com.google.dagger:hilt-compiler`: 2.55 → 2.57.2
  - ✅ `androidx.test.espresso:espresso-core`: 3.5.1 → 3.7.0
  - ✅ `androidx.navigation:navigation-compose`: 2.7.6 → 2.9.5
  - ✅ `compileSdk`: 34 → 35

### 🎨 Mejoras de UI

- ✅ Diseño responsive en todas las pantallas
- ✅ Adaptación automática de texto y espaciado según tamaño de pantalla
- ✅ Mejoras en la visualización de tablas en dispositivos pequeños

### 🔧 Refactorización

- ✅ Consolidación de carpetas de utilidades (`util`, `utils`, `ui/utils` → `utils`)
- ✅ Mejora en la organización del código

### 🐛 Correcciones

- ✅ Corrección del refresco de pantalla al eliminar vehículos
- ✅ Mejora en el manejo de excepciones durante la eliminación
- ✅ Corrección de problemas de cancelación de coroutines

---

## [2.9] - Versión anterior

### Características principales

- Tracking GPS en tiempo real
- Gestión de múltiples vehículos
- Estadísticas y registros detallados
- Exportación a Excel
- Visualización de rutas en Google Maps

