# 🚦 Sistema de Filtros de Velocidad - ZipStats

## 📋 Resumen

El sistema de ZipStats aplica **múltiples capas de filtrado** para garantizar que las velocidades mostradas y guardadas sean precisas y razonables para cada tipo de vehículo. Los filtros se aplican en **3 momentos diferentes** del ciclo de vida de una ruta.

---

## 🎯 Límites de Velocidad por Tipo de Vehículo

| Tipo | Velocidad Máxima | Velocidad Mínima | Umbral de Pausa |
|------|------------------|------------------|------------------|
| 🛴 **Patinete** | 35 km/h | 4 km/h | 4 km/h |
| 🚲 **Bicicleta** | 40 km/h | 3 km/h | 3 km/h |
| 🚴 **E-Bike** | 45 km/h | 4 km/h | 4 km/h |
| 🛞 **Monociclo** | 35 km/h | 4 km/h | 4 km/h |

> **Nota**: Monociclo está igualado a Patinete desde la última actualización.

---

## 🔄 Flujo de Filtrado en Tiempo Real

### **Fase 1: SpeedCalculator (Durante el Tracking)**

**Ubicación**: `app/src/main/java/com/zipstats/app/tracking/SpeedCalculator.kt`

**Cuándo se aplica**: En tiempo real, cada vez que llega una nueva ubicación GPS durante el tracking activo.

**Proceso paso a paso**:

#### **FILTRO 1: Precisión GPS** ✅
```kotlin
if (location.accuracy > MAX_ACCURACY) { // MAX_ACCURACY = 20 metros
    // Rechaza si la precisión es > 20m
}
```
- **Propósito**: Eliminar lecturas GPS con baja precisión (edificios, túneles, etc.)
- **Tolerancia**: 20 metros de precisión máxima
- **Comportamiento**: Si se rechazan 5 lecturas consecutivas, devuelve `null` (se detiene el tracking)

#### **FILTRO 2: Frecuencia de Actualizaciones** ⏱️
```kotlin
if (currentTime - lastUpdateTime < MIN_TIME_DELTA) { // MIN_TIME_DELTA = 100ms
    // Ignora actualizaciones muy frecuentes
}
```
- **Propósito**: Evitar procesar actualizaciones GPS demasiado rápidas (ruido)
- **Tolerancia**: Mínimo 100ms entre actualizaciones
- **Comportamiento**: Mantiene la velocidad actual si llega muy rápido

#### **FILTRO 3: Rango de Velocidad** 🚦
```kotlin
if (gpsSpeed > vehicleType.maxSpeed * MAX_SPEED_MULTIPLIER) { 
    // MAX_SPEED_MULTIPLIER = 1.2 (20% de tolerancia)
    // Rechaza si excede maxSpeed * 1.2
}
```
- **Propósito**: Eliminar velocidades imposibles para el tipo de vehículo
- **Tolerancia**: `maxSpeed * 1.2` (ej: Patinete = 35 * 1.2 = **42 km/h máximo**)
- **Ejemplo**: 
  - Patinete: Rechaza velocidades > 42 km/h
  - Bicicleta: Rechaza velocidades > 48 km/h
  - E-Bike: Rechaza velocidades > 54 km/h
  - Monociclo: Rechaza velocidades > 42 km/h

#### **FILTRO 4: Validación por Distancia** 📏
```kotlin
val calculatedSpeed = (distance / timeDelta) * 3.6f // km/h
if (abs(gpsSpeed - calculatedSpeed) > MAX_ACCELERATION && 
    calculatedSpeed < vehicleType.maxSpeed) {
    // Rechaza si hay gran diferencia entre GPS y cálculo por distancia
}
```
- **Propósito**: Detectar saltos GPS comparando velocidad GPS vs velocidad calculada
- **Tolerancia**: `MAX_ACCELERATION = 30 km/h/s` de diferencia máxima
- **Excepción**: No se aplica al arrancar desde parado (permite aceleración inicial)

#### **FILTRO 5: Aceleración Razonable** ⚡
```kotlin
val acceleration = abs(gpsSpeed - currentDisplaySpeed) / timeDelta
if (acceleration > maxAccel) { // MAX_ACCELERATION = 30 km/h/s
    // Rechaza si la aceleración es imposible
}
```
- **Propósito**: Eliminar cambios de velocidad físicamente imposibles
- **Tolerancia**: 
  - Normal: 30 km/h/s
  - Al arrancar/frenar: 60 km/h/s (doble tolerancia)
- **Ejemplo**: Si vas a 20 km/h y en 1 segundo pasas a 60 km/h → Rechazado (aceleración = 40 km/h/s)

#### **FILTRO 6: Umbral de Pausa** 🛑
```kotlin
val displaySpeed = if (smoothedSpeed < vehicleType.pauseSpeedThreshold) {
    0f  // Muestra 0 si está por debajo del umbral
} else {
    smoothedSpeed
}
```
- **Propósito**: Mostrar velocidad 0 cuando estás prácticamente parado
- **Umbral**: 
  - Patinete/Monociclo: 4 km/h
  - Bicicleta: 3 km/h
  - E-Bike: 4 km/h

#### **Suavizado EMA (Exponential Moving Average)** 📈
```kotlin
val smoothedSpeed = emaFilter.updateSpeed(gpsSpeed.toDouble()).toFloat()
```
- **Propósito**: Suavizar fluctuaciones del GPS para mostrar velocidad más estable
- **Método**: Media móvil exponencial adaptativa

---

## 🔍 Filtrado Post-Ruta (Análisis)

### **Fase 2: OutlierFilter (Al Finalizar la Ruta)**

**Ubicación**: `app/src/main/java/com/zipstats/app/analysis/OutlierFilter.kt`

**Cuándo se aplica**: Después de finalizar la ruta, antes de guardar los puntos en Firebase.

**Proceso paso a paso**:

#### **CRITERIO 1: Precisión GPS** ✅
```kotlin
if (current.accuracy > MAX_ACCURACY) { // MAX_ACCURACY = 25 metros
    return false // Elimina el punto
}
```
- **Tolerancia**: 25 metros (más permisivo que en tiempo real)

#### **CRITERIO 2: Velocidad Máxima** 🚦
```kotlin
if (speed > vehicleType.maxSpeed * 1.5f) { // 1.5 = 50% de tolerancia
    return false // Elimina el punto
}
```
- **Tolerancia**: `maxSpeed * 1.5` (más permisivo que en tiempo real)
- **Ejemplo**: 
  - Patinete: Elimina velocidades > 52.5 km/h
  - Bicicleta: Elimina velocidades > 60 km/h
  - E-Bike: Elimina velocidades > 67.5 km/h
  - Monociclo: Elimina velocidades > 52.5 km/h

#### **CRITERIO 3: Distancia Razonable** 📏
```kotlin
if (distanceFromPrev > MAX_REASONABLE_DISTANCE) { // 200 metros
    return false // Elimina el punto
}
```
- **Propósito**: Eliminar saltos GPS grandes (ej: de 0 a 500m en 1 segundo)
- **Tolerancia**: 200 metros máximo entre puntos consecutivos

#### **CRITERIO 4: Aceleración Razonable** ⚡
```kotlin
val acceleration = abs(speedOut - speedIn)
if (acceleration > MAX_ACCELERATION) { // 30 km/h/s
    return false // Elimina el punto
}
```
- **Propósito**: Eliminar cambios de velocidad imposibles entre puntos
- **Tolerancia**: 30 km/h/s máximo

#### **CRITERIO 5: Intervalo Temporal** ⏱️
```kotlin
if (timeDelta > MAX_TIME_GAP || timeDelta < MIN_TIME_GAP) {
    return false // Elimina el punto
}
```
- **Propósito**: Eliminar puntos con saltos temporales anómalos
- **Tolerancias**: 
  - Mínimo: 100ms entre puntos
  - Máximo: 30 segundos entre puntos

#### **CRITERIO 6: Velocidad Mínima (Drift GPS)** 🐌
```kotlin
if (speed < vehicleType.minSpeed * 0.5f) {
    // Solo rechaza si hay movimiento muy lento consistente
    if (nextSpeed < vehicleType.minSpeed * 0.5f) {
        return false // Elimina el punto
    }
}
```
- **Propósito**: Eliminar "drift GPS" (movimiento falso cuando estás parado)
- **Tolerancia**: Velocidad < `minSpeed * 0.5` en dos puntos consecutivos
- **Ejemplo**: Patinete elimina si velocidad < 2 km/h en dos puntos seguidos

---

## 📊 Análisis de Segmentos (Post-Ruta)

### **Fase 3: RouteAnalyzer (Cálculo de Estadísticas)**

**Ubicación**: `app/src/main/java/com/zipstats/app/utils/RouteAnalyzer.kt`

**Cuándo se aplica**: Al calcular estadísticas finales (distancia, velocidad media, etc.)

**Proceso**:

#### **Detección de Movimiento Real** 🚶
```kotlin
val isMoving = speed >= vehicleType.minSpeed && 
              speed <= vehicleType.maxSpeed &&
              distance > 3f && // mínimo 3 metros
              current.accuracy < 15f
```
- **Propósito**: Separar segmentos en movimiento de pausas
- **Criterios**:
  1. Velocidad entre `minSpeed` y `maxSpeed`
  2. Distancia mínima de 3 metros
  3. Precisión GPS < 15 metros

#### **Cálculo de Velocidad Máxima** 🏁
```kotlin
if (speed > maxSpeed && speed < 100f) {
    maxSpeed = speed
}
```
- **Propósito**: Calcular velocidad máxima real (excluyendo outliers extremos)
- **Límite superior**: 100 km/h (protección contra errores)

---

## 📈 Resumen de Tolerancias

| Filtro | Tiempo Real | Post-Ruta | Análisis |
|--------|-------------|-----------|----------|
| **Precisión GPS** | 20m | 25m | 15m |
| **Velocidad Máx. (Patinete)** | 42 km/h (1.2x) | 52.5 km/h (1.5x) | 35 km/h (1.0x) |
| **Aceleración Máx.** | 30 km/h/s | 30 km/h/s | - |
| **Distancia Máx.** | - | 200m | 3m mínimo |
| **Intervalo Temporal** | 100ms mínimo | 100ms - 30s | - |

---

## 🎯 Ejemplos Prácticos

### **Ejemplo 1: Patinete a 50 km/h**
1. **SpeedCalculator (Tiempo Real)**: ❌ Rechazado (50 > 42 km/h)
2. **OutlierFilter (Post-Ruta)**: ❌ Eliminado (50 > 52.5 km/h... espera, no, 50 < 52.5)
   - **Corrección**: Si pasa el filtro de tiempo real, podría llegar a post-ruta, pero se eliminaría si excede 52.5 km/h
3. **RouteAnalyzer**: ❌ No se cuenta en estadísticas (50 > 35 km/h)

### **Ejemplo 2: Bicicleta a 45 km/h**
1. **SpeedCalculator**: ✅ Aceptado (45 < 48 km/h = 40 * 1.2)
2. **OutlierFilter**: ✅ Aceptado (45 < 60 km/h = 40 * 1.5)
3. **RouteAnalyzer**: ❌ No se cuenta en segmentos en movimiento (45 > 40 km/h)

### **Ejemplo 3: Salto GPS (0 → 500m en 1s)**
1. **SpeedCalculator**: ❌ Rechazado (velocidad calculada = 1800 km/h, imposible)
2. **OutlierFilter**: ❌ Eliminado (distancia > 200m)
3. **RouteAnalyzer**: ❌ No se procesa (punto eliminado)

---

## 🔧 Configuración Actual

### **Constantes en SpeedCalculator**
- `MAX_ACCURACY = 20f` metros
- `MIN_TIME_DELTA = 100L` milisegundos
- `MAX_SPEED_MULTIPLIER = 1.2f` (20% de tolerancia)
- `MAX_ACCELERATION = 30f` km/h/s
- `maxConsecutiveRejections = 5` (detiene tracking si 5 rechazos seguidos)

### **Constantes en OutlierFilter**
- `MAX_ACCURACY = 25f` metros
- `MAX_REASONABLE_DISTANCE = 200f` metros
- `MAX_ACCELERATION = 30f` km/h/s
- `MAX_TIME_GAP = 30000L` milisegundos (30 segundos)
- `MIN_TIME_GAP = 100L` milisegundos

---

## 💡 Notas Importantes

1. **Los filtros son acumulativos**: Un punto debe pasar TODOS los filtros para ser aceptado
2. **Tolerancias diferentes**: Post-ruta es más permisivo que tiempo real (para no perder datos válidos)
3. **Protección contra drift**: El sistema elimina movimiento falso cuando estás parado
4. **Suavizado adaptativo**: La velocidad mostrada es suavizada, no la instantánea del GPS
5. **Velocidad 0 inteligente**: Se muestra 0 cuando estás por debajo del umbral de pausa

---

## 🚀 Resultado Final

Gracias a estos filtros, ZipStats garantiza:
- ✅ Velocidades precisas y razonables
- ✅ Eliminación de ruido GPS
- ✅ Detección correcta de pausas
- ✅ Estadísticas confiables
- ✅ Experiencia de usuario fluida (sin saltos bruscos)

