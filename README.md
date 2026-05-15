# 🛴 ZipStats

Aplicación Android para registrar rutas GPS de patinetes, bicicletas y otros vehículos personales, con métricas de conducción, mapas y contexto meteorológico.

[![Version](https://img.shields.io/badge/Version-7.3.1-brightgreen.svg)](https://github.com/shurdani/ZipStats/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2031%2B-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2025.01.00-blue.svg)](https://developer.android.com/jetpack/compose)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-green.svg)](https://gradle.org)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Características

- Tracking GPS en tiempo real con servicio en foreground.
- Cálculo de distancia, velocidad y duración de ruta.
- Visualización de rutas sobre Mapbox.
- Gestión de vehículos (kilometraje, imagen y estadísticas por vehículo).
- Historial de rutas y exportación a Excel.
- Sistema meteorológico con Google Weather API (alertas y detección de condiciones adversas).
- Sistema de logros y progreso de objetivos.
- Personalización visual (tema, colores dinámicos, negro puro y ajustes de interfaz).

---

## Capturas de pantalla

> Capturas completas en [`.github/assets/screenshots`](https://github.com/Shurdani/ZipStats/tree/main/.github/assets/screenshots).

### Vistas principales

| Registros | Estadísticas | Vehículo |
| --- | --- | --- |
| ![Tus Viajes](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot1.png) | ![Tus Datos](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot2.png) | ![Tu Vehículo](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot3.png) |

| Logros | Rutas | Tracking |
| --- | --- | --- |
| ![Tus Logros](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot4.png) | ![Tus Rutas](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot5.png) | ![En Ruta](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot6.png) |

| Personalización | Perfil | Clima detallado |
| --- | --- | --- |
| ![Personalizable](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot7.png) | ![Tu Perfil](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot8.png) | ![Clima de tus rutas detallado](https://raw.githubusercontent.com/Shurdani/ZipStats/main/.github/assets/screenshots/screenshot9.png) |

---

## Stack técnico

### Core
- Kotlin `2.0.21`
- Jetpack Compose BOM `2025.01.00`
- Arquitectura MVVM + separación por capas
- Hilt `2.57.2`
- KSP `2.0.0-1.0.22`
- Gradle Wrapper `8.13`

### Android
- `compileSdk 35`
- `targetSdk 34`
- `minSdk 33` (Android 14+)
- Java/Kotlin JVM target `17`

### Servicios y librerías
- Mapbox Maps SDK `11.8.0` + SDK Services `7.9.0`
- Firebase BoM `33.6.0` (Auth, Firestore, Storage, Analytics)
- Google Play Services (Location/Auth/Base)
- Google Weather API
- Cloudinary
- Retrofit `2.11.0`
- Coroutines `1.8.1`
- DataStore `1.1.2`
- Apache POI `5.2.3`
- Coil `2.7.0`

> Nota: las versiones vigentes se mantienen en `gradle/libs.versions.toml`.

---

## Requisitos

- Android 14 (API 33) o superior.
- GPS habilitado.
- Google Play Services.
- Conexión a Internet para mapas/sincronización/clima.

---

## Configuración local

### 1) Clonar repositorio

```bash
git clone https://github.com/shurdani/zipstats.git
cd zipstats
```

### 2) Preparar archivos de configuración

1. Copia `local.properties.example` a `local.properties`.
2. Copia `app/google-services.json.example` a `app/google-services.json`.
3. (Opcional para release firmado) copia `keystore.properties.example` a `keystore.properties`.

### 3) Credenciales necesarias

En `local.properties`:

- `mapbox.access.token`
- `MAPBOX_DOWNLOADS_TOKEN` (scope `DOWNLOADS:READ`)
- `cloudinary.cloud_name`
- `cloudinary.api_key`
- `cloudinary.api_secret`
- `google.weather.api.key` (usada por el repositorio de clima actual)
- `openweather.api.key` (compatibilidad/uso alternativo en partes del proyecto)

### 4) Compilar

Linux/macOS:

```bash
./gradlew clean assembleDebug
./gradlew installDebug
```

Windows (PowerShell/CMD):

```powershell
.\gradlew.bat clean assembleDebug
.\gradlew.bat installDebug
```

Build release:

```bash
./gradlew assembleRelease
```

---

## Estructura principal

```text
app/src/main/java/com/zipstats/app/
├── analysis/      # Análisis de datos GPS
├── di/            # Inyección de dependencias (Hilt)
├── model/         # Modelos de dominio/datos
├── navigation/    # Navegación
├── repository/    # Acceso a datos y APIs
├── service/       # Servicios en segundo plano
├── tracking/      # Lógica de tracking
├── ui/            # Pantallas y componentes Compose
└── utils/         # Utilidades
```

---

## Seguridad y credenciales

- No subir `local.properties`, `app/google-services.json` ni `keystore.properties`.
- El repositorio Mapbox privado usa `MAPBOX_DOWNLOADS_TOKEN` desde `local.properties`.
- Restringe claves API por paquete y huellas SHA cuando aplique.

---

## Contribuir

1. Haz fork del proyecto.
2. Crea tu rama (`git checkout -b feature/mi-feature`).
3. Haz commit de los cambios.
4. Publica la rama.
5. Abre un Pull Request.

---

## Licencia

GPLv3. Consulta [LICENSE](LICENSE) para más detalles.  
El nombre "ZipStats", logotipo y elementos visuales forman parte de la identidad del autor.

---

## Contacto

- Email: `zipstatsapp@gmail.com`
- Issues: [GitHub Issues](https://github.com/shurdani/zipstats/issues)

---

¡Feliz tracking! 🛴
