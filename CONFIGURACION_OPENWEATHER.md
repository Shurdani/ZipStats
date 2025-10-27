# Configuración de OpenWeather API

Esta aplicación usa la API de OpenWeather para mostrar datos meteorológicos en los detalles de las rutas.

## 🔑 Obtener API Key de OpenWeather

### 1. Crear Cuenta
1. Ve a [OpenWeatherMap.org](https://openweathermap.org/)
2. Click en "Sign In" → "Create an Account"
3. Completa el registro (email, contraseña, etc.)
4. Verifica tu email

### 2. Generar API Key
1. Inicia sesión en [OpenWeatherMap](https://openweathermap.org/)
2. Ve a tu perfil → "My API keys"
3. La API key por defecto ya estará creada (o crea una nueva)
4. Copia tu API key

### 3. Configurar en el Proyecto

#### Opción A: Archivo local.properties (Recomendado)
1. Abre o crea el archivo `local.properties` en la raíz del proyecto
2. Añade la siguiente línea:
```properties
openweather.api.key=TU_API_KEY_AQUI
```

#### Opción B: Usar el archivo de ejemplo
1. Copia `local.properties.example` a `local.properties`
2. Reemplaza `YOUR_OPENWEATHER_API_KEY` con tu API key real

**⚠️ IMPORTANTE**: 
- Nunca subas el archivo `local.properties` a Git
- El archivo ya está en `.gitignore`
- Solo el archivo `.example` debe estar en el repositorio

## 📊 Plan Gratuito

El plan gratuito de OpenWeather incluye:
- ✅ 1,000 llamadas por día
- ✅ 60 llamadas por minuto
- ✅ Clima actual
- ✅ Pronóstico de 5 días
- ❌ Datos históricos (requiere suscripción)

### Limitaciones en la App
Dado que los datos históricos requieren suscripción de pago, la aplicación actualmente:
- Obtiene el clima **actual** de la ubicación de inicio de la ruta
- Lo usa como aproximación del clima durante la ruta
- En el futuro se puede implementar datos históricos con suscripción

## 🧪 Probar sin API Key

Si no configuras la API key:
- La app seguirá funcionando normalmente
- El chip de clima mostrará valores por defecto: `☁️ --°C`
- No se lanzarán errores, solo mensajes de log

## 🔧 Verificar Configuración

Para verificar que tu API key está configurada correctamente:

1. Compila el proyecto:
```bash
./gradlew assembleDebug
```

2. Ejecuta la app y abre los detalles de una ruta
3. El chip de clima debería mostrar:
   - Un emoji de clima (☀️, ☁️, 🌧️, etc.)
   - La temperatura actual (ej: "18°C")

4. Si ves `--°C`, revisa:
   - Que la API key esté correctamente en `local.properties`
   - Los logs de Android Studio (busca "WeatherRepository" o "StatsChips")

## 📱 Uso de la API en la App

### Cuándo se Llama a la API
- Al abrir los detalles de una ruta
- Una vez por ruta (resultado se cachea en el estado del componente)
- Solo si la ruta tiene puntos GPS

### Datos Mostrados
- **Emoji del clima**: ☀️ ☁️ 🌧️ ⛈️ ❄️ 🌫️
- **Temperatura**: Redondeada al entero más cercano en °C
- **Ubicación**: Primera coordenada de la ruta

### Mapeo de Iconos a Emojis
```kotlin
01d → ☀️  (Despejado día)
02d → 🌤️  (Pocas nubes)
04d → ☁️  (Nubes)
09d → 🌧️  (Lluvia)
10d → 🌦️  (Lluvia y sol)
11d → ⛈️  (Tormenta)
13d → ❄️  (Nieve)
50d → 🌫️  (Niebla)
```

## 🔒 Seguridad

### Archivo Build Config
La API key se expone de forma segura usando `BuildConfig`:
```kotlin
BuildConfig.OPENWEATHER_API_KEY
```

### ProGuard
En builds de release, la API key está ofuscada por ProGuard.

### Buenas Prácticas
✅ API key solo en `local.properties`  
✅ `local.properties` en `.gitignore`  
✅ Validación de API key antes de hacer llamadas  
✅ Manejo de errores graceful  
✅ Logs descriptivos para debugging  

## 🚀 Próximas Mejoras

- [ ] Implementar caché de respuestas de clima
- [ ] Agregar datos históricos con suscripción premium
- [ ] Mostrar descripción completa del clima al hacer tap
- [ ] Guardar datos de clima junto con la ruta en Firebase
- [ ] Añadir indicador de calidad del aire (AQI)

## 📚 Documentación Oficial

- [OpenWeather API Docs](https://openweathermap.org/api)
- [Current Weather Data](https://openweathermap.org/current)
- [Weather Icons](https://openweathermap.org/weather-conditions)
- [API Pricing](https://openweathermap.org/price)

## ❓ Troubleshooting

### Error: "API key not configured"
**Solución**: Añade tu API key en `local.properties`

### Error: 401 Unauthorized
**Solución**: Verifica que la API key sea correcta y esté activada

### Error: 429 Too Many Requests
**Solución**: Has excedido el límite de 60 llamadas/minuto o 1000/día

### El clima no se actualiza
**Solución**: 
1. Limpia el proyecto: `./gradlew clean`
2. Reconstruye: `./gradlew assembleDebug`
3. Desinstala la app y reinstala

### Clima muestra "--°C"
**Posibles causas**:
- API key no configurada
- Ruta sin puntos GPS
- Error de red
- Revisa logs de Android Studio

---

📝 **Nota**: Esta implementación sigue el mismo patrón de seguridad usado para Google Maps y Cloudinary en la aplicación.

