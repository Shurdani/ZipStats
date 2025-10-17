# ZipStats 🛴

<!-- Banner - Coloca tu banner en .github/assets/banner.png -->
![ZipStats Banner](.github/assets/banner.png)

ZipStats es una aplicación diseñada para llevar el control total de tus desplazamientos en patinete eléctrico. Registra tus kilómetros, analiza estadísticas detalladas y descubre tu impacto ecológico de forma sencilla y visual.
Ideal para quienes usan el patinete a diario y quieren convertir sus trayectos en datos útiles y motivadores.

## 📸 Capturas de Pantalla

<!-- Capturas - Coloca tus capturas en .github/assets/screenshots/ -->
<p align="center">
  <img src=".github/assets/screenshots/screenshot1.png" width="200" alt="Captura 1"/>
  <img src=".github/assets/screenshots/screenshot2.png" width="200" alt="Captura 2"/>
  <img src=".github/assets/screenshots/screenshot3.png" width="200" alt="Captura 3"/>
  <img src=".github/assets/screenshots/screenshot4.png" width="200" alt="Captura 4"/>
</p>

## Características

- 📝 **Historial de viajes**: Guarda, analiza tus trayectos y consulta los kilómetros recorridos por fecha y vehículo.
- 🏆 **Sistema de logros**: Desbloquea logros según tu actividad.
- 🔧 **Gestión de reparaciones**: Mantén un historial de mantenimiento y reparaciones.
- 📈 **Estadísticas**: Revisa tus promedios, máximos y evolución a lo largo del tiempo.
- 🌿 **Impacto ecológico**: Calcula CO₂ ahorrado, árboles equivalentes y gasolina no consumida.
- 🛠️ **Multivehículo**: Lleva el seguimiento de varios patinetes de forma independiente.
- 🎨 **Tema personalizable**: Modo claro, oscuro y OLED.
- 🎨 **Colores dinámicos**: Adaptación a tu wallpaper (Android 12+).
- ☁️ **Sincronización con Firebase**: tus datos en la nube puedes cambiar de dispositivo, sin miedo a perder datos.

## Tecnologías

- **Kotlin** - Lenguaje de programación
- **Jetpack Compose** - UI moderna declarativa
- **Firebase** - Backend (Auth, Firestore, Storage)
- **Hilt** - Inyección de dependencias
- **Material 3** - Design system
- **Google Maps** - Mapas y ubicación
- **Cloudinary** - Gestión de imágenes

## Requisitos

- Android Studio Hedgehog o superior
- JDK 17
- Android SDK 31 o superior
- Cuenta de Firebase
- Cuenta de Cloudinary (para imágenes)

## Configuración

### 1. Clonar el repositorio

```bash
git clone https://github.com/Shurdani/ZipStats.git
cd ZipStats
```

### 2. Configurar Firebase

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com/)
2. Añade una app Android con el package name: `com.example.patineta`
3. Descarga el archivo `google-services.json`
4. Copia `google-services.json` a la carpeta `app/`
   - Puedes usar `app/google-services.json.example` como referencia

### 3. Configurar local.properties

Copia el archivo de ejemplo y configura tus credenciales:

```bash
cp local.properties.example local.properties
```

Edita `local.properties` con:

```properties
sdk.dir=RUTA_A_TU_ANDROID_SDK

# Cloudinary Configuration (obtén tus credenciales en https://cloudinary.com/console)
cloudinary.cloud_name=TU_CLOUD_NAME
cloudinary.api_key=TU_API_KEY
cloudinary.api_secret=TU_API_SECRET
```

### 4. Sincronizar y compilar

```bash
./gradlew clean build
```

## Estructura del Proyecto

```
app/src/main/java/com/example/patineta/
├── di/                 # Inyección de dependencias
├── model/             # Modelos de datos
├── navigation/        # Navegación de la app
├── repository/        # Capa de datos
├── service/          # Servicios (Cloudinary, Network, etc)
├── ui/               # Componentes UI
│   ├── achievements/ # Pantalla de logros
│   ├── auth/        # Autenticación
│   ├── components/  # Componentes reutilizables
│   ├── profile/     # Perfil de usuario
│   ├── records/     # Registros de viajes
│   ├── repairs/     # Reparaciones
│   ├── settings/    # Configuración
│   ├── statistics/  # Estadísticas
│   └── theme/       # Tema y estilos
└── utils/           # Utilidades
```

## Firebase Rules

Configura las reglas de seguridad de Firebase:

### Firestore Rules
Copia el contenido de `firestore.rules` a Firebase Console

### Storage Rules
Copia el contenido de `storage.rules` a Firebase Console

## Compilar APK

Para generar el APK de producción:

```bash
./gradlew assembleRelease
```

El APK se generará en: `app/build/outputs/apk/release/`

## Contribuir

1. Fork el proyecto
2. Crea tu rama de feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## Licencia

El código de ZipStats está disponible bajo la [Licencia Pública General de GNU versión 3 (GPL v3)](LICENSE), esto excluye:

- **Iconos, logotipos, mascotas, materiales de marketing y assets de ZipStats.**
- **Animaciones y videos de ZipStats.**
- **Documentación de ZipStats.**
- **Capturas de pantalla de Google Play.**
- **Textos y descripciones de Google Play.**

Ver el archivo [LICENSE](LICENSE) para más detalles sobre los términos de la licencia.

## Autor

Shurdani - [@Shurdani](https://github.com/Shurdani)

## Agradecimientos

- [Firebase](https://firebase.google.com/)
- [Cloudinary](https://cloudinary.com/)
- [Material Design](https://m3.material.io/)

