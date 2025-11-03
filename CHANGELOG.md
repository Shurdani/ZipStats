# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [3.0.0] - 2024-12-XX

### 🎉 Versión Mayor - Mejoras Significativas

#### ✨ Nuevas Características

- **Pantalla siempre encendida durante tracking**: Nueva opción en ajustes para mantener la pantalla encendida automáticamente durante la grabación de rutas. Esto mejora la experiencia de uso al evitar que la pantalla se apague durante el seguimiento GPS.
- **Sistema de onboarding mejorado**: 
  - Dialog de bienvenida que aparece automáticamente cuando no hay vehículos registrados
  - Gestión inteligente de sesión: el diálogo solo aparece una vez por sesión si el usuario lo descarta
  - Opciones directas para registrar vehículo o ir a perfil desde el diálogo

#### 🚀 Actualizaciones de Dependencias

- **Google Services**: Actualizado de `4.4.2` a `4.4.4`
- **Google Play Services Base**: Actualizado de `18.2.0` a `18.9.0`
- **Hilt (Dagger)**: Actualizado de `2.55` a `2.57.2`
- **Navigation Compose**: Actualizado de `2.7.6` a `2.9.5`
- **Espresso Core**: Actualizado de `3.5.1` a `3.7.0`
- **compileSdk**: Actualizado de `34` a `35` para soportar las nuevas dependencias

#### 🎨 Mejoras de Interfaz de Usuario

- **Diseño responsive mejorado**: 
  - Adaptación automática de textos, tamaños de fuente y espaciados según el tamaño de pantalla
  - Textos abreviados en pantallas pequeñas ("Patinete" → "Pat.", "Distancia" → "Dist.")
  - Prevención de solapamiento de encabezados en tablas
  - Ajustes dinámicos de padding y márgenes

- **Mejoras visuales**:
  - Icono actualizado para vehículo tipo "Monociclo" (de nave espacial a icono de rueda)

#### 🔧 Refactorización y Organización

- **Consolidación de carpetas de utilidades**:
  - Unificación de `util/` y `utils/` en una sola carpeta `utils/`
  - Movido `ScreenUtils` de `ui/utils/` a `utils/` para mejor organización
  - Todos los archivos de utilidades ahora están centralizados

#### 🐛 Correcciones de Bugs

- **Eliminación de vehículos**: Corregido el problema donde la pantalla no se refrescaba después de eliminar un vehículo
  - Mejora en el manejo de excepciones de cancelación
  - Implementación de espera activa para confirmar eliminación en Firestore antes de actualizar UI

- **Onboarding**: 
  - Corregido el problema donde el diálogo aparecía incluso cuando ya había vehículos registrados
  - Mejorada la lógica de detección de estado de carga

- **Google Sign-In**: Mejoras en el manejo de fusión de cuentas cuando se usa el mismo email con diferentes métodos de autenticación

#### 📝 Mejoras Internas

- **Gestión de estado mejorada**: 
  - Mejor manejo de `CancellationException` en coroutines
  - Optimización de actualizaciones de UI después de operaciones asíncronas

- **Código más limpio**: 
  - Eliminación de código duplicado
  - Mejor organización de utilidades
  - Actualización de APIs deprecadas

### 🔄 Migración desde v2.9

Esta versión es compatible con datos de versiones anteriores. No se requieren acciones especiales de migración.

Los usuarios simplemente necesitan actualizar la aplicación normalmente. Todas las preferencias y datos existentes se mantendrán intactos.

---

## [2.9.0] - Versión Anterior

Versión base con las siguientes características principales:
- Tracking GPS en tiempo real
- Gestión de múltiples vehículos
- Estadísticas y visualización de rutas
- Exportación a Excel
- Autenticación con Firebase
- Interfaz Material Design 3

---

## Tipos de Cambios

- **✨ Añadido**: Para nuevas características
- **🔄 Cambiado**: Para cambios en funcionalidades existentes
- **⚠️ Deprecado**: Para funcionalidades que serán removidas en futuras versiones
- **🗑️ Eliminado**: Para funcionalidades removidas
- **🐛 Corregido**: Para correcciones de bugs
- **🔒 Seguridad**: Para vulnerabilidades de seguridad

