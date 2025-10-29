# 🚀 ZipStats v2.8 - Release Notes

## 📅 Fecha de lanzamiento
Noviembre de 2024

## 🎯 Resumen
Esta versión introduce un **sistema completo de personalización de temas** con 6 paletas de colores profesionales, mejoras significativas en el sistema de GPS con posicionamiento previo, optimizaciones en la captura de clima, y refinamientos en la experiencia de usuario para una aplicación más pulida y profesional.

---

## ✨ Nuevas Funcionalidades

### 🎨 Sistema de Temas y Personalización
- **6 paletas de colores profesionales**:
  - 🟦 **Ride Blue**: Moderna y tecnológica (por defecto)
  - 🟢 **Eco Green**: Natural y sostenible
  - 🔴 **Energy Red**: Deportiva y potente
  - 🟣 **Urban Purple**: Moderna y elegante
  - ⚫ **Steel Gray**: Minimalista y profesional
  - 🟠 **Solar Flare**: Cálida y energética
  

- **Configuración persistente**:
  - Las preferencias se guardan automáticamente usando DataStore
  - Los cambios se aplican inmediatamente sin reiniciar la app

### 📍 Posicionamiento GPS Previo
- **Inicio inteligente de GPS**:
  - El GPS se activa automáticamente al entrar en la pantalla "Listo para grabar"
  - Obtiene una posición precisa **antes** de iniciar el tracking
  - Elimina los primeros metros perdidos al iniciar una ruta

- **Feedback visual en tiempo real**:
  - 🟢 **Verde**: Listo (precisión ≤6m) - "Listo para iniciar"
  - 🟡 **Amarillo**: Buena señal (precisión ≤10m) - "Señal encontrada: Precisión X m"
  - 🔴 **Rojo**: Buscando - "Buscando señal GPS..."

- **Icono dinámico**:
  - El icono GPS cambia de color según la calidad de la señal
  - Indicador visual claro del estado del GPS

- **Control de inicio**:
  - El botón "Iniciar seguimiento" solo se activa cuando hay señal GPS válida (≤10m)
  - Muestra mensaje "Esperando señal GPS..." cuando no hay señal suficiente
  - Confirmación visual: "¡Tracking iniciado al 100%!" al iniciar

- **Gestión eficiente de recursos**:
  - Se detiene automáticamente cuando se sale de la pantalla
  - No consume batería innecesariamente
  - Optimizado para ahorro energético

### 🌤️ Mejoras en Sistema de Clima
- **Captura mejorada al inicio**:
  - El clima se captura automáticamente al **inicio** de la ruta, no al finalizar
  - Reintentos automáticos con delays progresivos (3s, 6s, 10s)
  - Espera inteligente hasta obtener el primer punto GPS

- **Manejo robusto de errores**:
  - Reintentos automáticos ante fallos de red
  - Estados claros: Cargando, Éxito, Error, No disponible
  - No bloquea el inicio del tracking si falla la API

- **Feedback visual mejorado**:
  - Indicador de estado en tiempo real durante el tracking
  - Mensajes claros sobre el estado de la captura del clima
  - Iconos y emojis para mejor comprensión

### 🔔 Navegación desde Notificación
- **Navegación directa a Tracking**:
  - Al hacer clic en la notificación durante el tracking, abre directamente la pantalla de Tracking
  - Limpia el back stack correctamente para evitar navegación confusa
  - Funciona tanto con la app abierta como en segundo plano

- **Experiencia mejorada**:
  - Siempre lleva a la pantalla correcta, sin importar dónde estabas antes
  - Al presionar "Atrás" desde Tracking, vuelve a la pantalla principal
  - Comportamiento consistente y predecible

### 💾 Diálogo de Finalizar Ruta
- **Checkbox desmarcado por defecto**:
  - El checkbox "Añadir X km a registros" ahora viene **desmarcado** por defecto
  - Mayor control sobre cuándo añadir distancias a los registros
  - Comportamiento más intuitivo y menos intrusivo

---

## 🐛 Correcciones de Errores

### Navegación
- Corregida la navegación desde notificaciones que llevaba a pantallas incorrectas
- Mejorado el manejo del back stack al usar la notificación durante el tracking
- Arreglado comportamiento inconsistente al hacer clic en la notificación

### Clima
- Corregido timing de captura de clima (ahora al inicio, no al final)
- Mejorado manejo de timeouts y errores de red
- Añadida espera inteligente para el primer punto GPS antes de consultar clima

### GPS
- Eliminada la pérdida de los primeros metros al iniciar una ruta
- Mejorado el tiempo de inicialización del GPS
- Optimizada la gestión de recursos del GPS previo

---

## 📦 Archivos Nuevos

### Tema y Personalización
- `ColorTheme.kt` - Sistema de paletas de colores
- `Theme.kt` - Sistema de temas mejorado con soporte Material You
- `ThemeMode.kt` - Modos de tema (Sistema/Claro/Oscuro)
- `Type.kt` - Tipografía mejorada
- `Shape.kt` - Formas y componentes visuales

### GPS y Tracking
- Mejoras en `TrackingViewModel.kt` - Posicionamiento GPS previo
- Mejoras en `TrackingScreen.kt` - Feedback visual del GPS
- Optimizaciones en `LocationTrackingService.kt`

---

## 🔧 Cambios Técnicos

### Sistema de Temas
- Implementación completa de Material Design 3
- Integración con Material You (Android 12+)
- Sistema de persistencia con DataStore
- Soporte para modo Pure Black OLED
- 6 paletas de colores profesionales con variantes claro/oscuro

### GPS Previo
- Nueva funcionalidad `startPreLocationTracking()` en TrackingViewModel
- Estado `GpsPreLocationState` para manejo de estados GPS
- Callback de ubicación optimizado para ahorro de batería
- Limpieza automática de recursos al salir de la pantalla

### Clima
- Mejoras en `captureStartWeather()` - Captura al inicio
- Reintentos automáticos con delays progresivos
- Mejor manejo de estados y errores
- Integración no bloqueante con el tracking

### Navegación
- Mejoras en `MainActivity.kt` para manejo de intents de notificación
- Limpieza correcta del back stack al navegar desde notificación
- Delay inteligente para asegurar que NavController esté listo

---

## 📱 Compatibilidad

- **Android mínimo**: API 31 (Android 12)
- **Android objetivo**: API 34 (Android 14)
- **Versión del código**: 7
- **Versión del nombre**: 2.8

---

## 🔐 Configuración Requerida

### OpenWeather API (Opcional)
Para usar la funcionalidad de clima, necesitas configurar una API key:

1. Obtén una clave gratuita en [OpenWeather](https://openweathermap.org/api)
2. Crea/edita el archivo `local.properties`
3. Añade la línea: `openweather.api.key=TU_API_KEY_AQUI`

Ver `CONFIGURACION_OPENWEATHER.md` para más detalles.

**Nota**: La app funciona perfectamente sin la API key, simplemente no mostrará datos de clima.

---

## 🎨 Personalización

### Cómo cambiar el tema:
1. Ve a **Perfil** → **Ajustes de Cuenta**
2. En la sección **Personalización**:
   - Selecciona tu **Paleta de Colores** favorita
   - Elige el **Modo de Tema** (Sistema/Claro/Oscuro)
   - Activa **Material You** si está disponible (Android 12+)
   - Activa **Pure Black OLED** para ahorrar batería en pantallas OLED

### Paletas recomendadas:
- **Uso diario**: Ride Blue o Urban Purple
- **Día soleado**: Solar Flare
- **Noche/OLED**: Steel Gray con Pure Black activado
- **Deportivo**: Energy Red
- **Ecológico**: Eco Green

---

## 📝 Notas de Actualización

### Para Usuarios Nuevos
- Instala la APK y disfruta de todas las funcionalidades
- Explora las 6 paletas de colores disponibles
- Personaliza la app según tus preferencias

### Para Usuarios Existentes
- **Actualización automática**: Todas tus rutas y datos se mantienen intactos
- **Tema por defecto**: Se mantiene Ride Blue (tu tema anterior)
- **GPS mejorado**: Notarás que las rutas empiezan más precisas desde el primer momento
- **Clima**: Se guarda automáticamente en todas las rutas nuevas
- **Notificaciones**: Funcionan mejor al hacer clic durante el tracking

---

## 🐞 Problemas Conocidos

Ninguno en este momento.

---

## 🔮 Próximas Mejoras

- Gráficos de velocidad durante la ruta
- Exportación de rutas en formato GPX
- Comparación entre rutas
- Estadísticas por tipo de vehículo
- Más paletas de colores según feedback
- Temas personalizados por el usuario

---

## 🙏 Agradecimientos

Gracias a todos los usuarios que han proporcionado feedback para mejorar ZipStats. Vuestra participación es esencial para hacer de esta la mejor app de tracking de movilidad personal.

---

## 📞 Soporte

¿Encontraste un bug o tienes una sugerencia?
- Abre un issue en [GitHub](https://github.com/Shurdani/ZipStats/issues)

---

**¡Disfruta de ZipStats v2.8!** 🎉

---

## 📊 Resumen de Cambios vs v2.7

| Categoría | v2.7 | v2.8 |
|-----------|------|------|
| Paletas de colores | 1 | 6 |
| Modos de tema | Sistema | Sistema + Claro + Oscuro |
| Material You | ❌ | ✅ |
| Modo OLED | ❌ | ✅ |
| GPS previo | ❌ | ✅ |
| Feedback GPS visual | ❌ | ✅ |
| Clima al inicio | ❌ | ✅ |
| Navegación desde notificación | ⚠️ | ✅ |
| Checkbox por defecto | ✅ Marcado | ✅ Desmarcado |

