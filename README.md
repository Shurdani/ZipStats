# 🛴 ZipStats

**Aplicación Android para tracking GPS de patinetes, bicicletas y otros vehículos personales.**

[![Version](https://img.shields.io/badge/Version-6.3.3-brightgreen.svg)](https://github.com/shurdani/ZipStats/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2031%2B-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2025.01.00-blue.svg)](https://developer.android.com/jetpack/compose)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-green.svg)](https://gradle.org)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## 📱 **Características Principales**

### 🗺️ **Tracking GPS en Tiempo Real**
- ✅ Seguimiento preciso de rutas con GPS en tiempo real
- ✅ Cálculo automático de distancia, velocidad y duración
- ✅ Visualización interactiva de rutas en Mapbox con estilo personalizado
- ✅ Marcadores personalizados de inicio y final con orientación inteligente
- ✅ Filtrado de ruido GPS para mayor precisión (precisión < 50m)
- ✅ Velocidad = 0 cuando estás parado (filtro inteligente)
- ✅ Servicio en foreground que continúa funcionando en segundo plano
- ✅ Pausa y reanudación del tracking
- ✅ Integración con OpenMeteo API para datos meteorológicos precisos
- ✅ Captura automática del clima al inicio de la ruta
- ✅ Monitoreo continuo del clima cada 10 minutos durante la ruta
- ✅ Preavisos inteligentes de lluvia y condiciones extremas antes de iniciar
- ✅ Detección automática de lluvia durante la ruta con actualización en tiempo real
- ✅ Sistema de calzada húmeda con detección probabilística
- ✅ Badges de seguridad en rutas finalizadas (lluvia, calzada húmeda, condiciones extremas)
- ✅ Compartir rutas con imágenes de mapas profesionales

### 📊 **Gestión de Vehículos**
- ✅ Registro de múltiples vehículos (patinete, bicicleta, etc.)
- ✅ Seguimiento de kilometraje por vehículo
- ✅ Estadísticas individuales por vehículo
- ✅ Imágenes personalizadas con Cloudinary

### 📈 **Estadísticas y Registros**
- ✅ Historial completo de rutas con visualización en mapa
- ✅ Estadísticas detalladas (distancia total, velocidad promedio/máxima, tiempo en movimiento)
- ✅ Información meteorológica completa por ruta (temperatura, viento, humedad, UV, precipitación)
- ✅ Badges de seguridad que reflejan condiciones adversas durante la ruta
- ✅ Filtrado por fecha y vehículo con índices optimizados de Firebase
- ✅ Análisis post-ruta (porcentaje de tiempo en movimiento, pausas detectadas)
- ✅ Exportación de datos a Excel
- ✅ Recarga automática de componentes cuando es necesario

### 🌦️ **Sistema Meteorológico Inteligente**
- ✅ Preavisos preventivos antes de iniciar la ruta
  - Detección de lluvia activa con aviso azul/rosa
  - Alerta de calzada húmeda con aviso amarillo/naranja
  - Advertencia de condiciones extremas (viento fuerte, temperatura extrema, UV alto, tormentas)
- ✅ Monitoreo continuo durante la ruta
  - Chequeo automático cada 10 minutos
  - Actualización en tiempo real del clima cuando se detecta lluvia
  - Confirmación de lluvia con doble verificación para evitar falsos positivos
- ✅ Detección inteligente de lluvia con múltiples reglas:
  - Códigos meteorológicos oficiales
  - Precipitación medida directamente
  - Análisis de humedad y probabilidad de lluvia
  - Detección de diluvios urbanos mediterráneos
- ✅ Badges de seguridad en rutas finalizadas:
  - 🔵 Ruta realizada con lluvia
  - 🟡 Precaución: calzada húmeda
  - ⚠️ Condiciones extremas
- ✅ Detección de condiciones extremas:
  - Viento fuerte (>40 km/h) y ráfagas (>60 km/h)
  - Temperaturas extremas (<0°C o >35°C)
  - Índice UV muy alto (>8)
  - Tormentas y fenómenos meteorológicos adversos

### 🎨 **Interfaz Moderna**
- ✅ Diseño Material Design 3
- ✅ Jetpack Compose 100%
- ✅ Navegación intuitiva con Bottom Navigation
- ✅ Tema adaptable con soporte para colores dinámicos
- ✅ Modo oscuro optimizado con mejor contraste para OLED
- ✅ Velocímetro con Media Móvil Exponencial para respuesta instantánea
- ✅ Iconografía unificada y consistente
- ✅ Accesibilidad mejorada con contraste optimizado en todos los componentes

---

## 🚀 **Tecnologías Utilizadas**

### **Core**
- **Lenguaje:** Kotlin `2.0.21`
- **UI Framework:** Jetpack Compose `2025.01.00` (BOM)
- **Arquitectura:** MVVM + Clean Architecture
- **Inyección de Dependencias:** Hilt `2.57.2` (Dagger)
- **Gradle:** `8.13`
- **Android Gradle Plugin:** `8.8.0`
- **Compile SDK:** `35`
- **Target SDK:** `34`
- **Min SDK:** `31` (Android 12+)

### **APIs y Servicios**
- **Mapbox Maps SDK:** `11.8.0` - Visualización de mapas y rutas
- **Mapbox SDK Services:** `7.9.0` - Servicios de geocodificación y direcciones
- **Google Weather API:** Datos meteorológicos en tiempo real y pronósticos
- **Firebase BoM:** `33.6.0`
  - Authentication (Email/Password/Google)
  - Firestore Database (Almacenamiento de datos)
  - Storage (Imágenes de perfil)
  - Analytics
- **Cloudinary:** Gestión de imágenes de vehículos
- **Google Play Services:**
  - Location `21.3.0` - GPS tracking en tiempo real
  - Auth `21.3.0` - Autenticación con Google
  - Base `18.5.0`

### **Librerías Principales**
- **Navigation Compose:** `2.9.0` - Navegación entre pantallas
- **Lifecycle:** `2.8.1` - ViewModel y Runtime Compose
- **Coil:** `2.7.0` - Carga de imágenes
- **Retrofit:** `2.11.0` - Cliente HTTP para APIs
- **Kotlinx Coroutines:** `1.7.3` - Programación asíncrona
- **DataStore:** `1.1.2` - Preferencias locales
- **Apache POI:** `5.2.3` - Exportación a Excel
- **Material Design:** `1.12.0` - Componentes Material
- **HBRecorder:** `3.0.9` - Grabación de pantalla

### **Herramientas de Desarrollo**
- **KSP:** `2.0.0-1.0.22` - Kotlin Symbol Processing (reemplaza kapt)
- **Gradle Version Catalog:** Gestión centralizada de dependencias en `libs.versions.toml`
- **Android Gradle Plugin:** `8.8.0` - Build system
- **Gradle Wrapper:** `8.13` - Build tool

---

## 📋 **Requisitos**

- Android 12 (API 31) o superior
- GPS habilitado
- Google Play Services
- Conexión a Internet (para mapas y sincronización)

---

## 🔧 **Configuración del Proyecto**

### **1. Clonar el Repositorio**

```bash
git clone https://github.com/shurdani/zipstats.git
cd zipstats
```

### **2. Configurar Credenciales**

Configura las siguientes credenciales en `local.properties`:

- ✅ **Mapbox Access Token** - Token de acceso para Mapbox Maps SDK
- ✅ **Mapbox Downloads Token** - Token de descarga para Mapbox (requerido para descargar dependencias)
- ✅ **Firebase** - Copia `google-services.json` a `app/` (ver `google-services.json.example`)
- ✅ **Cloudinary Credentials** - Cloud name, API Key y API Secret
- ✅ **OpenWeather API Key** (opcional) - Para datos meteorológicos alternativos
- ✅ **Google Weather API Key** (opcional) - Para datos meteorológicos de Google

**⚠️ IMPORTANTE:** 
- Copia `local.properties.example` a `local.properties` y configura tus credenciales
- Copia `google-services.json.example` a `google-services.json` en `app/`
- El `MAPBOX_DOWNLOADS_TOKEN` debe tener scope `DOWNLOADS:READ` en tu cuenta de Mapbox

### **3. Compilar e Instalar**

```bash
# Limpiar y compilar
./gradlew clean assembleDebug

# Instalar en dispositivo conectado
./gradlew installDebug

# O compilar release
./gradlew assembleRelease
```

### **4. Estructura de Archivos de Configuración**

```
ZipStats/
├── local.properties              # Credenciales locales (NO versionar)
├── local.properties.example      # Plantilla de credenciales
├── google-services.json          # Firebase config (NO versionar)
├── google-services.json.example  # Plantilla de Firebase
├── keystore.properties           # Configuración de firma (NO versionar)
└── keystore.properties.example  # Plantilla de keystore
```

**⚠️ IMPORTANTE:** Todos los archivos con credenciales reales están en `.gitignore` y no deben ser versionados.

---

## 🏗️ **Arquitectura del Proyecto**

```
app/src/main/java/com/zipstats/app/
├── analysis/               # Análisis de datos GPS (filtrado de outliers, detección de pausas)
├── di/                     # Módulos de inyección de dependencias (Hilt)
├── map/                    # Animación y visualización de rutas
├── model/                  # Modelos de datos (Route, Scooter, RoutePoint, User, etc.)
├── navigation/             # Navegación entre pantallas
├── network/                # APIs de red (Cloudinary)
├── permission/             # Gestión de permisos
├── repository/             # Repositorios (capa de datos)
│   └── WeatherRepository   # Integración con OpenMeteo API
├── service/                # Servicios en segundo plano
│   ├── LocationTrackingService  # Servicio de tracking GPS
│   ├── AchievementsService      # Gestión de logros
│   └── NetworkMonitor           # Monitoreo de red
├── tracking/               # Lógica de tracking GPS (LocationTracker, SpeedCalculator)
├── ui/                     # Interfaz de usuario (Jetpack Compose)
│   ├── achievements/       # Pantalla de logros
│   ├── auth/              # Autenticación (login, registro, verificación)
│   ├── components/        # Componentes reutilizables
│   │   └── CapturableMapView  # Componente de mapas Mapbox
│   ├── onboarding/        # Pantalla de bienvenida
│   ├── permissions/       # Diálogos de permisos
│   ├── profile/           # Perfil y gestión de vehículos
│   ├── records/           # Historial de registros
│   ├── repairs/           # Gestión de reparaciones
│   ├── routes/            # Pantalla de rutas y detalles
│   ├── shared/            # Componentes compartidos (overlays, estados)
│   ├── splash/           # Pantalla de inicio
│   ├── statistics/       # Estadísticas y análisis
│   ├── theme/            # Sistema de temas Material Design 3
│   └── tracking/         # Pantalla de tracking GPS en tiempo real
└── utils/                 # Utilidades (formateo, exportación, análisis)
```

---

## 🎯 **Características Técnicas**

### **🗺️ GPS Tracking Optimizado**
- Filtrado de ruido GPS (precisión < 50m)
- Distancia mínima entre puntos (5m) para evitar saltos
- Velocidad filtrada (< 1.5 km/h = 0 km/h)
- Servicio en foreground con notificación persistente
- Cálculo preciso con fórmula Haversine
- Actualización GPS cada 2 segundos
- Media Móvil Exponencial (EMA) para respuesta instantánea del velocímetro

### **🌦️ Sistema Meteorológico Avanzado**
- Integración con OpenMeteo API para datos precisos (gratuita y open-source)
- Captura automática del clima al inicio de cada ruta
- Monitoreo continuo cada 10 minutos durante la ruta
- Detección inteligente de lluvia con 4 reglas diferentes:
  - Códigos meteorológicos oficiales
  - Precipitación medida directamente
  - Análisis probabilístico (humedad + probabilidad)
  - Detección de diluvios urbanos mediterráneos
- Sistema de calzada húmeda con consideración día/noche
- Detección de condiciones extremas (viento, temperatura, UV, tormentas)
- Preavisos preventivos antes de iniciar la ruta
- Actualización en tiempo real del clima cuando se detecta lluvia
- Badges de seguridad que reflejan el estado más adverso durante la ruta

### **⚡ Rendimiento**
- Carga lazy de imágenes con Coil
- Reactive data streams con Flow
- Optimización de memoria con LazyColumn
- Índices de Firebase optimizados para consultas rápidas
- Gestión de estado reactiva con recarga automática
- Version Catalog (libs.versions.toml) para gestión centralizada de dependencias
- Compilación optimizada con KSP (Kotlin Symbol Processing)

### **🎨 Accesibilidad y UX**
- Contraste optimizado para modo oscuro y OLED
- Colores dinámicos con Material You
- Texto legible en todos los estados de botones (habilitado/deshabilitado)
- Soporte para modo oscuro puro (negro puro) para pantallas OLED
- Componentes accesibles con descripciones apropiadas

### **🔒 Seguridad**
- Autenticación Firebase
- Reglas de seguridad Firestore
- API Keys protegidas (no hardcodeadas en el código)
- Credenciales sensibles en `local.properties` (no versionado)
- Restricciones de API Key por package name y SHA-1
- Mapbox Downloads Token almacenado de forma segura

---

## 🔄 **Mejoras Recientes**

### **Versión 5.4.8**
- ✅ **Accesibilidad mejorada:** Contraste optimizado para modo oscuro y pantallas OLED
- ✅ **Botones corregidos:** Texto visible en todos los estados (habilitado/deshabilitado)
- ✅ **Gestión de dependencias:** Migración a Version Catalog (libs.versions.toml)
- ✅ **Eliminación de Accompanist:** Migrado a APIs oficiales de Navigation Compose
- ✅ **Actualización de dependencias:** Todas las librerías actualizadas a versiones estables
- ✅ **Seguridad mejorada:** Credenciales sensibles movidas a `local.properties`
- ✅ **Colores dinámicos:** Soporte completo para Material You
- ✅ **Modo OLED:** Soporte para modo oscuro puro (negro puro) para pantallas OLED

### **Mejoras de Accesibilidad**
- Contraste mejorado en todos los componentes de UI
- Texto legible en botones primarios (negro sobre naranja en modo oscuro)
- Estados deshabilitados con colores visibles
- Soporte para colores dinámicos del sistema
- Modo oscuro optimizado para diferentes tipos de pantalla

---

## 📱 **Capturas de Pantalla**

<p align="center">
  <img src=".github/assets/screenshots/screenshot1.png" width="200" alt="Pantalla 1"/>
  <img src=".github/assets/screenshots/screenshot2.png" width="200" alt="Pantalla 2"/>
  <img src=".github/assets/screenshots/screenshot3.png" width="200" alt="Pantalla 3"/>
</p>

<p align="center">
  <img src=".github/assets/screenshots/screenshot4.png" width="200" alt="Pantalla 4"/>
  <img src=".github/assets/screenshots/screenshot5.png" width="200" alt="Pantalla 5"/>
  <img src=".github/assets/screenshots/screenshot6.png" width="200" alt="Pantalla 6"/>
</p>

<p align="center">
  <img src=".github/assets/screenshots/screenshot7.png" width="200" alt="Pantalla 7"/>
  <img src=".github/assets/screenshots/screenshot8.png" width="200" alt="Pantalla 8"/>
  <img src=".github/assets/screenshots/screenshot9.png" width="200" alt="Pantalla 9"/>
</p>

---

## 🤝 **Contribuir**

¡Las contribuciones son bienvenidas! Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

**⚠️ IMPORTANTE:** Asegúrate de que tu código sigue las mejores prácticas de seguridad antes de hacer commit.

---

## 📄 **Licencia**

Este proyecto está bajo la Licencia Pública General GNU v3 (GPLv3). Consulta el archivo [LICENSE](LICENSE) para más detalles.  
El nombre "ZipStats", su logotipo y elementos visuales están protegidos como identidad del autor. No se permite su uso en aplicaciones derivadas sin autorización expresa.

---

## 👥 **Autores**

- **[Shurdani]** - *Desarrollo inicial* - [Shurdani](https://github.com/Shurdani)

---

## 🙏 **Agradecimientos**

- Mapbox por la plataforma de mapas
- OpenMeteo por los datos meteorológicos precisos y gratuitos
- Firebase por los servicios backend
- Cloudinary por la gestión de imágenes
- La comunidad de Android y Jetpack Compose

---

## 📞 **Contacto**

¿Preguntas? ¿Sugerencias? ¿Encontraste un bug?

- 📧 Email: zipstatsapp@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/shurdani/Patinetatrack/issues)

---

## 🔮 **Próximas Características**

- [ ] Modo offline para tracking sin conexión
- [ ] Exportación de rutas en formato GPX
- [ ] Compartir rutas con otros usuarios
- [ ] Estadísticas avanzadas con gráficos
- [ ] Integración con wearables (smartwatch)
- [ ] Modo oscuro automático según hora del día
- [ ] Notificaciones de recordatorios de mantenimiento
- [ ] Historial meteorológico detallado por ruta
- [ ] Alertas de condiciones meteorológicas adversas en tiempo real

---

**¡Feliz Tracking! 🚀🛴**
