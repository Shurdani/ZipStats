# 🔍 Guía de Debugging - OpenWeather API

## Problema Común: "No se muestra el clima, sigue con icono estático"

### ✅ Cambios Realizados

1. ✅ **Permiso INTERNET añadido** en `AndroidManifest.xml`
2. ✅ **Logs mejorados** para debugging
3. ✅ **Validación de API key** con mensajes claros

### 📋 Checklist de Verificación

#### 1. Verificar que la API key esté configurada

Abre `local.properties` y verifica que existe esta línea:
```properties
openweather.api.key=tu_api_key_real_aqui
```

**⚠️ Importante**: 
- No debe decir `YOUR_OPENWEATHER_API_KEY`
- Debe ser una cadena de 32 caracteres hexadecimales
- Ejemplo de formato válido: `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6`

#### 2. Verificar la API key en OpenWeather

1. Ve a https://home.openweathermap.org/api_keys
2. Verifica que tu API key esté **Active** (Estado: activo)
3. Las API keys nuevas pueden tardar **10-15 minutos** en activarse

#### 3. Limpiar y reconstruir el proyecto

```bash
# Limpiar
./gradlew clean

# Reconstruir
./gradlew assembleDebug

# O desde Android Studio
Build > Clean Project
Build > Rebuild Project
```

#### 4. Desinstalar y reinstalar la app

```bash
# Desinstalar
adb uninstall com.zipstats.app

# Instalar fresh
./gradlew installDebug
```

**⚠️ Importante**: El `BuildConfig` se genera en tiempo de compilación, necesitas rebuild completo.

### 🔎 Cómo Ver los Logs

#### Opción 1: Android Studio Logcat

1. Abre Android Studio
2. Ve a **View > Tool Windows > Logcat**
3. En el filtro, escribe: `WeatherRepository|StatsChips`
4. Ejecuta la app y abre los detalles de una ruta
5. Observa los logs

#### Opción 2: Terminal (adb)

```bash
adb logcat -s WeatherRepository:D StatsChips:D
```

### 📊 Logs Esperados

#### ✅ Logs de ÉXITO (todo funciona):

```
D/StatsChips: Iniciando carga de clima para ruta abc123
D/StatsChips: Obteniendo clima para lat=41.3851, lon=2.1734
D/WeatherRepository: === Iniciando llamada a OpenWeather API ===
D/WeatherRepository: API Key length: 32 chars
D/WeatherRepository: URL (sin API key): https://api.openweathermap.org/data/2.5/weather?lat=41.3851&lon=2.1734&units=metric&lang=es
D/WeatherRepository: Realizando petición HTTP...
D/WeatherRepository: Código de respuesta: 200
D/WeatherRepository: Respuesta recibida: {"coord":{"lon":2.1734,"lat":41.3851}...
D/WeatherRepository: ✅ Clima parseado correctamente: 18.5°C, cielo claro
D/StatsChips: Clima obtenido: 18.5°C, emoji=☀️
D/StatsChips: Carga de clima finalizada
```

**Resultado**: Deberías ver `☀️ 19°C` en el chip de clima

#### ❌ Logs de ERROR - API Key no configurada:

```
D/StatsChips: Iniciando carga de clima para ruta abc123
D/WeatherRepository: === Iniciando llamada a OpenWeather API ===
D/WeatherRepository: API Key length: 26 chars
E/WeatherRepository: ❌ API key de OpenWeather no configurada correctamente
E/WeatherRepository: Por favor, añade tu API key en local.properties:
E/WeatherRepository: openweather.api.key=TU_API_KEY_AQUI
E/StatsChips: Error obteniendo clima: API key de OpenWeather no configurada
```

**Solución**: Configura tu API key en `local.properties`

#### ❌ Logs de ERROR - API Key inválida (401):

```
D/WeatherRepository: Código de respuesta: 401
E/WeatherRepository: ❌ Error HTTP 401
E/WeatherRepository: API key inválida. Verifica tu key en local.properties
```

**Solución**: 
- Verifica que copiaste la API key correctamente
- Espera 10-15 min si es nueva
- Genera una nueva en OpenWeather

#### ❌ Logs de ERROR - Sin conexión a internet:

```
E/WeatherRepository: ❌ Excepción al obtener clima: UnknownHostException
```

**Solución**: Verifica conexión del emulador/dispositivo

#### ❌ Logs de ERROR - Límite excedido (429):

```
D/WeatherRepository: Código de respuesta: 429
E/WeatherRepository: ❌ Error HTTP 429
E/WeatherRepository: Límite de llamadas excedido (60/min o 1000/día)
```

**Solución**: Espera un minuto o verifica tu cuota

### 🐛 Debugging Paso a Paso

#### Paso 1: Verificar que se llama a la API

Busca en Logcat:
```
D/StatsChips: Iniciando carga de clima
```

- ✅ **Si lo ves**: La función se está ejecutando
- ❌ **Si NO lo ves**: 
  - La ruta no tiene puntos GPS
  - El dialog no se está abriendo correctamente
  - Verifica que la ruta tenga coordenadas

#### Paso 2: Verificar la API key

Busca:
```
D/WeatherRepository: API Key length: X chars
```

- ✅ **Si ves "32 chars"**: API key configurada correctamente
- ❌ **Si ves otro número**: 
  - API key incorrecta en `local.properties`
  - Haz rebuild del proyecto

#### Paso 3: Verificar la respuesta HTTP

Busca:
```
D/WeatherRepository: Código de respuesta: XXX
```

- ✅ **200**: Éxito
- ❌ **401**: API key inválida
- ❌ **429**: Límite excedido
- ❌ **Otro**: Ver mensaje de error

#### Paso 4: Verificar el parsing

Busca:
```
D/StatsChips: Clima obtenido: X°C, emoji=Y
```

- ✅ **Si lo ves**: Todo funciona, el chip debería actualizarse
- ❌ **Si NO lo ves**: Error en el parsing (reportar bug)

### 🔧 Soluciones Comunes

#### Problema: "API key no configurada" pero SÍ la configuré

**Solución**:
```bash
# 1. Verifica el archivo
cat local.properties | grep openweather

# 2. Debe mostrar:
openweather.api.key=tu_key_real

# 3. Si NO lo muestra, añádela:
echo "openweather.api.key=TU_KEY_REAL" >> local.properties

# 4. Rebuild completo
./gradlew clean assembleDebug
```

#### Problema: Emulador sin internet

**Solución**:
```bash
# Verificar conectividad del emulador
adb shell ping -c 3 8.8.8.8

# Si falla, reinicia el emulador
adb reboot
```

#### Problema: API key tarda en activarse

**Solución**: Las API keys nuevas de OpenWeather tardan 10-15 minutos en propagarse.

1. Espera 15 minutos
2. Verifica en https://home.openweathermap.org/api_keys que esté **Active**
3. Intenta de nuevo

### 📱 Probar en el Emulador

1. **Asegúrate de que el emulador tenga internet**:
   - Abre Chrome en el emulador
   - Navega a google.com
   - Si no carga, reinicia el emulador

2. **Ejecuta la app**:
   - Ve a la lista de rutas
   - Abre los detalles de una ruta que tenga puntos GPS
   - Espera 1-2 segundos
   - El chip de clima debería actualizarse

3. **Observa Logcat mientras lo haces**

### ✅ Señales de Éxito

Cuando funcione correctamente verás:

1. En Logcat: `✅ Clima parseado correctamente: XX°C`
2. En la UI: El chip cambia de `☁️ --°C` a algo como `☀️ 19°C`
3. El emoji cambia según el clima real

### 🆘 Si Nada Funciona

Si después de seguir todos los pasos sigue sin funcionar:

1. **Comparte los logs completos**:
```bash
adb logcat -d > logcat_openweather.txt
```

2. **Verifica tu `local.properties`**:
```bash
cat local.properties
```

3. **Verifica la API key en OpenWeather**:
   - Crea una NUEVA API key
   - Espera 15 minutos
   - Prueba con la nueva

4. **Prueba la API manualmente**:
```bash
# Reemplaza TU_API_KEY y las coordenadas
curl "https://api.openweathermap.org/data/2.5/weather?lat=41.3851&lon=2.1734&appid=TU_API_KEY&units=metric&lang=es"
```

Si esto funciona pero la app no, es un problema de configuración del proyecto.

---

## 🎯 Checklist Final

Antes de reportar un bug, verifica:

- [ ] Permiso INTERNET en AndroidManifest.xml
- [ ] API key en `local.properties`
- [ ] API key activa en OpenWeather (15 min de espera)
- [ ] Proyecto limpiado y reconstruido (`clean + assembleDebug`)
- [ ] App desinstalada y reinstalada
- [ ] Emulador con conexión a internet
- [ ] Logs revisados en Logcat
- [ ] Ruta tiene puntos GPS
- [ ] API probada manualmente con curl

Si todo está OK y sigue sin funcionar, hay un bug en el código.

